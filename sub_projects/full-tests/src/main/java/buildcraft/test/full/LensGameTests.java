/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;

import java.util.*;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.transport.pipe.*;
import buildcraft.silicon.*;
import buildcraft.silicon.item.ItemPluggableLens;
import buildcraft.silicon.plug.PluggableLens;
import buildcraft.transport.*;
import buildcraft.transport.pipe.flow.PipeFlowItems;
import buildcraft.transport.tile.TilePipeHolder;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.item.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.test.*;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;

public final class LensGameTests implements FabricGameTest {
    private static final BlockPos POS=new BlockPos(3,3,3);
    private static TilePipeHolder pipe(TestContext c,BlockPos pos,Item item){
        c.setBlockState(pos,BCTransportBlocks.pipeHolder);var tile=(TilePipeHolder)c.getBlockEntity(pos);tile.onPlacedBy(null,new ItemStack(item));return tile;
    }
    private static TilePipeHolder pipe(TestContext c){return pipe(c,POS,BCTransportItems.pipeItemStone);}
    private static PipeFlowItems flow(TilePipeHolder tile){return (PipeFlowItems)tile.getPipe().getFlow();}
    private static PluggableLens lens(TilePipeHolder tile,Direction side,DyeColor colour,boolean filter){
        var plug=new PluggableLens(BCSiliconPlugs.lens,tile,side,colour,filter);tile.replacePluggable(side,plug);return plug;
    }
    private static NbtList cargo(TilePipeHolder tile){return flow(tile).writeToNbt().getList("items",NbtElement.COMPOUND_TYPE);}
    private static int count(TestContext c,BlockPos pos,Item item){
        var barrel=(BarrelBlockEntity)c.getBlockEntity(pos);int count=0;for(int i=0;i<barrel.size();i++)if(barrel.getStack(i).isOf(item))count+=barrel.getStack(i).getCount();return count;
    }
    private static TilePipeHolder reload(TestContext c,TilePipeHolder tile){
        var copy=BCTransportBlocks.pipeHolderTile.instantiate(tile.getPos(),tile.getCachedState());copy.readNbt(tile.createNbt());c.getWorld().removeBlockEntity(tile.getPos());c.getWorld().addBlockEntity(copy);copy.tick();return copy;
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void flushLensWinsConnectionHitAndNativeBreakingPreservesPipe(TestContext c){
        var tile=pipe(c);var player=c.createMockCreativeServerPlayerInWorld();player.getAbilities().creativeMode=false;
        for(var side:Direction.values()){pipe(c,POS.offset(side),BCTransportItems.pipeItemStone);lens(tile,side,DyeColor.BLUE,side.ordinal()%2==0);}tile.tick();
        for(var side:Direction.values()){
            var normal=Vec3d.of(side.getVector());var eye=Vec3d.ofCenter(tile.getPos()).add(normal.multiply(3));var look=normal.multiply(-1);
            player.refreshPositionAndAngles(eye.x,eye.y-player.getEyeHeight(player.getPose()),eye.z,(float)Math.toDegrees(Math.atan2(-look.x,look.z)),(float)-Math.toDegrees(Math.asin(look.y)));player.setHeadYaw(player.getYaw());
            var trace=BCTransportBlocks.pipeHolder.rayTrace(c.getWorld(),tile.getPos(),player);
            c.assertTrue(trace!=null && trace.subHit==7+side.ordinal(),"Flush lens lost to connection hit "+side);
            player.interactionManager.tryBreakBlock(tile.getPos());
            c.assertTrue(c.getWorld().getBlockEntity(tile.getPos())==tile && tile.getPluggable(side)==null && tile.getPipe().isConnected(side),"Native lens break removed pipe or connection "+side);
        }
        var drops=c.getWorld().getEntitiesByClass(net.minecraft.entity.ItemEntity.class,new Box(tile.getPos()).expand(1),e->e.getStack().isOf(BCSiliconItems.plugLens));
        c.assertTrue(drops.stream().mapToInt(e->e.getStack().getCount()).sum()==6,"Native lens breaking lost/doubled drops");
        drops.forEach(net.minecraft.entity.Entity::discard);c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void allThirtyFourVariantsKeepNativeAndLegacyColourIdentity(TestContext c){
        c.assertTrue(ItemPluggableLens.variants().size()==34 && new HashSet<>(ItemPluggableLens.variants()).size()==34,"Wrong lens variant count");
        for(var data:ItemPluggableLens.variants()){
            var stack=BCSiliconItems.plugLens.getStack(data.colour(),data.isFilter());
            c.assertTrue(ItemPluggableLens.getData(ItemStack.fromNbt(stack.writeNbt(new NbtCompound()))).equals(data),"Native lens NBT lost variant");
            int damage=data.colour()==null?(data.isFilter()?33:32):15-data.colour().getId()+(data.isFilter()?16:0);
            var legacy=new ItemStack(BCSiliconItems.plugLens);legacy.getOrCreateNbt().putInt("Damage",damage);
            c.assertTrue(ItemPluggableLens.getData(legacy).equals(data),"Legacy dye damage reversed "+damage);
            stack.getOrCreateNbt().putInt("Damage",0);c.assertTrue(ItemPluggableLens.getData(stack).equals(data),"Legacy fallback overrode native NBT");
        }c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void allVariantsPlaceOnSixFacesWithoutBlockingConnections(TestContext c){
        var tile=pipe(c);var player=c.createMockCreativeServerPlayerInWorld();player.getAbilities().creativeMode=false;
        for(var data:ItemPluggableLens.variants())for(var side:Direction.values()){
            var look=Vec3d.of(side.getVector()).multiply(-1);var eye=Vec3d.ofCenter(tile.getPos()).subtract(look.multiply(3));
            player.refreshPositionAndAngles(eye.x,eye.y-player.getEyeHeight(player.getPose()),eye.z,(float)Math.toDegrees(Math.atan2(-look.x,look.z)),(float)-Math.toDegrees(Math.asin(look.y)));player.setHeadYaw(player.getYaw());
            var stack=BCSiliconItems.plugLens.getStack(data.colour(),data.isFilter());stack.setCount(2);player.setStackInHand(Hand.MAIN_HAND,stack);
            var result=BCTransportBlocks.pipeHolder.onUse(tile.getCachedState(),c.getWorld(),tile.getPos(),player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(tile.getPos()),side,tile.getPos(),false));
            var plug=(PluggableLens)tile.getPluggable(side);
            c.assertTrue(result.isAccepted() && stack.getCount()==1 && plug!=null && !plug.isBlocking() && ItemPluggableLens.getData(plug.getPickStack()).equals(data),"Native variant placement/pick failed "+data+" "+side);
            c.assertTrue(!plug.getBoundingBox().contains(.5,.5,.5),"Lens hitbox overlaps centre");
            c.assertTrue(BCSiliconItems.plugLens.onPlace(stack,tile,side,player,Hand.MAIN_HAND)==null,"Overwrote occupied attachment");tile.replacePluggable(side,null);
        }c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void placementRejectsFluidPowerAndDistantPlayers(TestContext c){
        var player=c.createMockCreativeServerPlayerInWorld();var stack=BCSiliconItems.plugLens.getStack(DyeColor.BLUE,false);
        for(var item:List.of(BCTransportItems.pipeFluidStone,BCTransportItems.pipePowerStone,BCTransportItems.pipeRfStone,BCTransportItems.pipeStructure)){
            var tile=pipe(c,POS,item);player.refreshPositionAndAngles(tile.getPos(),0,0);
            c.assertTrue(BCSiliconItems.plugLens.onPlace(stack,tile,Direction.WEST,player,Hand.MAIN_HAND)==null,"Lens accepted non-item flow");c.getWorld().removeBlock(tile.getPos(),false);
        }
        var tile=pipe(c);player.refreshPositionAndAngles(tile.getPos().add(100,0,0),0,0);
        c.assertTrue(BCSiliconItems.plugLens.onPlace(stack,tile,Direction.WEST,player,Hand.MAIN_HAND)==null,"Distant lens placement accepted");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void filterSimulationRejectsOnlyOtherColoursOnItsOwnFace(TestContext c){
        var tile=pipe(c);c.setBlockState(POS.west(),Blocks.BARREL);c.setBlockState(POS.east(),Blocks.BARREL);tile.tick();
        for(var data:ItemPluggableLens.variants())if(data.isFilter()){
            lens(tile,Direction.WEST,data.colour(),true);var before=tile.createNbt();
            for(var incoming:ItemPluggableLens.variants())if(!incoming.isFilter()){
                var stack=new ItemStack(Items.DIAMOND,7);
                boolean accept=incoming.colour()==null || incoming.colour()==data.colour();
                var event=new PipeEventItem.TryInsert(tile,flow(tile),incoming.colour(),Direction.WEST,stack);
                tile.fireEvent(event);c.assertTrue(event.isCanceled()!=accept,"Original filter cancellation contract changed");
                c.assertTrue(flow(tile).injectItem(stack,false,Direction.WEST,incoming.colour(),.08).isEmpty()==accept,"Filter acceptance wrong "+data+" "+incoming);
                c.assertTrue(flow(tile).injectItem(stack,false,Direction.EAST,incoming.colour(),.08).isEmpty(),"Filter affected another face");
                c.assertTrue(stack.getCount()==7 && before.equals(tile.createNbt()),"Simulation changed cargo or filter");
            }
        }c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void filterPrioritiesPreferMatchesAndUseUncolouredFallback(TestContext c){
        var tile=pipe(c);lens(tile,Direction.EAST,DyeColor.RED,true);lens(tile,Direction.NORTH,DyeColor.BLUE,true);lens(tile,Direction.SOUTH,null,true);
        for(var dye:Arrays.asList(DyeColor.RED,DyeColor.BLUE,null,DyeColor.GREEN)){
            var event=new PipeEventItem.SideCheck(tile,flow(tile),dye,Direction.WEST,new ItemStack(Items.DIAMOND));event.disallow(Direction.WEST);tile.fireEvent(event);
            if(dye==DyeColor.GREEN)c.assertTrue(!event.isAllowed(Direction.EAST) && !event.isAllowed(Direction.NORTH) && !event.isAllowed(Direction.SOUTH),"Mismatched colour leaked through filter");
            else {
                Direction target=dye==null?Direction.SOUTH:dye==DyeColor.RED?Direction.EAST:Direction.NORTH;
                c.assertTrue(event.getOrder().get(0).equals(EnumSet.of(target)),"Matching filter was not first choice");
                if(dye==null)c.assertTrue(event.getOrder().get(event.getOrder().size()-1).equals(EnumSet.of(Direction.EAST,Direction.NORTH)),"Coloured filters not lowest uncoloured priority");
            }
        }
        var blocked=new PipeEventItem.SideCheck(tile,flow(tile),DyeColor.RED,Direction.WEST,new ItemStack(Items.DIAMOND));blocked.disallow(Direction.EAST);tile.fireEvent(blocked);c.assertTrue(!blocked.isAllowed(Direction.EAST),"Filter reopened disconnected face");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void crossingInputLensPaintsEveryColourAndClearRemovesPaint(TestContext c){
        var tile=pipe(c);c.setBlockState(POS.west(),Blocks.BARREL);tile.tick();
        for(var data:ItemPluggableLens.variants()){
            lens(tile,Direction.WEST,data.colour(),data.isFilter());
            // Uncoloured cargo passes all filters, and only a regular lens paints it.
            c.assertTrue(flow(tile).injectItem(new ItemStack(Items.DIAMOND),true,Direction.WEST,null,.08).isEmpty(),"Lens refused input");
            var added=cargo(tile);c.assertTrue(added.size()==1 && added.getCompound(0).getByte("colour")==(!data.isFilter() && data.colour()!=null?data.colour().getId()+1:0),"Inserted cargo paint wrong "+data);
            c.getWorld().removeBlock(tile.getPos(),false);tile=pipe(c);tile.tick();
        }
        lens(tile,Direction.WEST,null,false);flow(tile).injectItem(new ItemStack(Items.EMERALD),true,Direction.WEST,DyeColor.RED,.08);
        c.assertTrue(cargo(tile).getCompound(0).getByte("colour")==0,"Clear lens retained incoming paint");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void outputLensOnlyPaintsItsCrossedFaceAndFiltersNeverPaint(TestContext c){
        var tile=pipe(c);
        for(var side:Direction.values())for(boolean filter:new boolean[]{false,true}){
            var plug=lens(tile,side,DyeColor.BLUE,filter);
            for(var destination:Direction.values()){
                var event=new PipeEventItem.ReachEnd(tile,flow(tile),DyeColor.RED,new ItemStack(Items.DIAMOND),destination);tile.fireEvent(event);
                c.assertTrue(event.colour==(!filter && destination==side?DyeColor.BLUE:DyeColor.RED),"Output paint leaked to other side/filter");
            }
            tile.replacePluggable(side,null);
        }c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativeItemInsertionPaintsOnlyAfterFinalTransactionCommit(TestContext c){
        var tile=pipe(c);c.setBlockState(POS.west(),Blocks.BARREL);tile.tick();lens(tile,Direction.WEST,DyeColor.MAGENTA,false);
        var storage=ItemStorage.SIDED.find(c.getWorld(),tile.getPos(),Direction.WEST);c.assertTrue(storage!=null,"Lens hid native item input");
        try(var outer=Transaction.openOuter()){storage.insert(ItemVariant.of(Items.DIAMOND),3,outer);try(var inner=outer.openNested()){storage.insert(ItemVariant.of(Items.EMERALD),2,inner);inner.commit();}}
        c.assertTrue(cargo(tile).isEmpty(),"Aborted native insertion left painted items");
        try(var tx=Transaction.openOuter()){c.assertTrue(storage.insert(ItemVariant.of(Items.DIAMOND),3,tx)==3 && cargo(tile).isEmpty(),"Native insertion committed early");tx.commit();}
        c.assertTrue(cargo(tile).size()==1 && cargo(tile).getCompound(0).getByte("colour")==DyeColor.MAGENTA.getId()+1 && ItemStack.fromNbt(cargo(tile).getCompound(0).getCompound("stack")).getCount()==3,"Native insertion bypassed lens or duplicated cargo");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void staleLensCannotPaintOrBlockAfterRemovalOrTileReplacement(TestContext c){
        var tile=pipe(c);var paint=lens(tile,Direction.WEST,DyeColor.BLUE,false);tile.replacePluggable(Direction.WEST,null);
        var on=new PipeEventItem.OnInsert(tile,flow(tile),DyeColor.RED,new ItemStack(Items.DIAMOND),Direction.WEST);paint.onInsert(on);c.assertTrue(on.colour==DyeColor.RED,"Removed lens still paints");
        var filter=lens(tile,Direction.WEST,DyeColor.BLUE,true);reload(c,tile);
        var attempt=new PipeEventItem.TryInsert(tile,flow(tile),DyeColor.RED,Direction.WEST,new ItemStack(Items.DIAMOND));filter.tryInsert(attempt);c.assertTrue(attempt.accepted==1,"Stale filter blocked input");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void allVariantsPersistAndSynchronizeWithoutLosingDirection(TestContext c) throws Exception{
        var tile=pipe(c);
        for(var data:ItemPluggableLens.variants())for(var side:Direction.values()){
            var plug=lens(tile,side,data.colour(),data.isFilter());var copy=BCTransportBlocks.pipeHolderTile.instantiate(tile.getPos(),tile.getCachedState());copy.readNbt(tile.createNbt());
            c.assertTrue(ItemPluggableLens.getData(copy.getPluggable(side).getPickStack()).equals(data),"Saved lens variant lost");
            var buffer=new PacketByteBuf(io.netty.buffer.Unpooled.buffer());
            try {plug.writeCreationPayload(buffer);var synced=BCSiliconPlugs.lens.loadFromBuffer(tile,side,buffer);
                c.assertTrue(buffer.readableBytes()==0 && synced.side==side && ItemPluggableLens.getData(synced.getPickStack()).equals(data) && synced.getBoundingBox().equals(plug.getBoundingBox()),"Lens creation packet corrupted");}
            finally{buffer.release();}tile.replacePluggable(side,null);
        }c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void legacyBlockColourAndMalformedPacketsStayBounded(TestContext c){
        var tile=pipe(c);var nbt=new NbtCompound();nbt.putBoolean("f",true);
        for(int i=0;i<16;i++){nbt.putByte("c",(byte)i);var plug=new PluggableLens(BCSiliconPlugs.lens,tile,Direction.WEST,nbt);c.assertTrue(plug.colour==DyeColor.byId(i) && plug.isFilter,"Legacy block colour reversed");}
        for(var colour:DyeColor.values()){
            nbt.putString("colour",colour==DyeColor.LIGHT_GRAY?"SILVER":colour.name());
            c.assertTrue(new PluggableLens(BCSiliconPlugs.lens,tile,Direction.WEST,nbt).colour==colour,"Legacy named colour lost "+colour);
        }
        nbt.putString("colour","_NULL");c.assertTrue(new PluggableLens(BCSiliconPlugs.lens,tile,Direction.WEST,nbt).colour==null,"Saved clear lens became coloured");
        for(int invalid:new int[]{17,255}){
            var buffer=new PacketByteBuf(io.netty.buffer.Unpooled.buffer());boolean rejected=false;
            try{buffer.writeByte(invalid).writeBoolean(false);new PluggableLens(BCSiliconPlugs.lens,tile,Direction.WEST,buffer);}catch(IllegalArgumentException expected){rejected=true;}finally{buffer.release();}
            c.assertTrue(rejected,"Invalid lens packet accepted");
        }
        var buffer=new PacketByteBuf(io.netty.buffer.Unpooled.buffer());boolean truncated=false;try{buffer.writeByte(1);new PluggableLens(BCSiliconPlugs.lens,tile,Direction.WEST,buffer);}catch(IndexOutOfBoundsException expected){truncated=true;}finally{buffer.release();}
        c.assertTrue(truncated,"Truncated lens packet accepted");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=100)
    public void realThreeWayFilterRoutingKeepsExactColouredCargo(TestContext c){
        var tile=pipe(c);for(var side:List.of(Direction.WEST,Direction.EAST,Direction.NORTH,Direction.SOUTH))c.setBlockState(POS.offset(side),Blocks.BARREL);
        lens(tile,Direction.EAST,DyeColor.RED,true);lens(tile,Direction.NORTH,DyeColor.BLUE,true);lens(tile,Direction.SOUTH,null,true);tile.tick();
        flow(tile).injectItem(new ItemStack(Items.DIAMOND,4),true,Direction.WEST,DyeColor.RED,.08);
        flow(tile).injectItem(new ItemStack(Items.EMERALD,3),true,Direction.WEST,DyeColor.BLUE,.08);
        flow(tile).injectItem(new ItemStack(Items.GOLD_INGOT,2),true,Direction.WEST,null,.08);
        reload(c,tile);
        c.waitAndRun(65,()->{c.assertTrue(count(c,POS.east(),Items.DIAMOND)==4 && count(c,POS.north(),Items.EMERALD)==3 && count(c,POS.south(),Items.GOLD_INGOT)==2,"Live filter routing lost/misrouted cargo after reload");c.complete();});
    }
    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=100)
    public void clearOutputLensLetsPaintedCargoEnterNeighbouringClearFilter(TestContext c){
        var first=pipe(c);var second=pipe(c,POS.east(),BCTransportItems.pipeItemStone);c.setBlockState(POS.west(),Blocks.BARREL);c.setBlockState(POS.east(2),Blocks.BARREL);
        lens(first,Direction.EAST,null,false);lens(second,Direction.WEST,null,true);lens(second,Direction.EAST,null,true);first.tick();second.tick();
        c.assertTrue(first.getPipe().isConnected(Direction.EAST) && second.getPipe().isConnected(Direction.WEST),"Lens broke pipe connection");
        flow(first).injectItem(new ItemStack(Items.DIAMOND,5),true,Direction.WEST,DyeColor.RED,.08);
        c.waitAndRun(70,()->{c.assertTrue(count(c,POS.east(2),Items.DIAMOND)==5 && cargo(first).isEmpty() && cargo(second).isEmpty(),"Output clear lens did not clear before next filter");c.complete();});
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void pipeBreakingDropsEachColouredAttachmentExactlyOnce(TestContext c){
        var tile=pipe(c);var expected=new HashSet<ItemPluggableLens.LensData>();
        for(var side:Direction.values()){var data=new ItemPluggableLens.LensData(DyeColor.byId(side.ordinal()),side.ordinal()%2==0);expected.add(data);lens(tile,side,data.colour(),data.isFilter());}
        c.getWorld().breakBlock(tile.getPos(),true);
        var drops=c.getWorld().getEntitiesByClass(net.minecraft.entity.ItemEntity.class,new Box(tile.getPos()).expand(1),e->e.getStack().isOf(BCSiliconItems.plugLens));
        c.assertTrue(drops.size()==6 && drops.stream().allMatch(e->e.getStack().getCount()==1) && new HashSet<>(drops.stream().map(e->ItemPluggableLens.getData(e.getStack())).toList()).equals(expected),"Lens drops lost or duplicated variant");
        c.getWorld().getEntitiesByClass(net.minecraft.entity.ItemEntity.class,new Box(tile.getPos()).expand(1),e->true).forEach(net.minecraft.entity.Entity::discard);c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void assemblyRecipesKeepOriginalGlassBarsVariantAndMjCost(TestContext c){
        for(var data:ItemPluggableLens.variants()){
            String colour=data.colour()==null?"clear":data.colour().getName();
            var id=new Identifier("buildcraftsilicon","assembly/lens_"+(data.isFilter()?"filter":"regular")+"_"+colour);
            var recipe=(buildcraft.silicon.recipe.NativeAssemblyRecipe)c.getWorld().getRecipeManager().get(id).orElseThrow();
            c.assertTrue(recipe.cost()==500*MjAPI.MJ && recipe.output().getCount()==1 && ItemPluggableLens.getData(recipe.output()).equals(data),"Wrong lens recipe output/cost "+id);
            var glass=net.minecraft.registry.Registries.ITEM.get(new Identifier("minecraft",data.colour()==null?"glass":colour+"_stained_glass"));
            c.assertTrue(recipe.inputs().size()==(data.isFilter()?2:1) && recipe.inputs().get(0).test(new ItemStack(glass)) && (!data.isFilter() || recipe.inputs().get(1).test(new ItemStack(Items.IRON_BARS))),"Wrong glass/bars ingredients");
        }c.complete();
    }
}
