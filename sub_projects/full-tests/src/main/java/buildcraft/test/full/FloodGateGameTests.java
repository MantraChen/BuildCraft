/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;

import java.util.*;
import buildcraft.api.mj.*;
import buildcraft.core.*;
import buildcraft.core.item.ItemFragileFluidContainer;
import buildcraft.energy.BCEnergyFluids;
import buildcraft.factory.*;
import buildcraft.factory.block.BlockFloodGate;
import buildcraft.factory.tile.*;
import buildcraft.lib.misc.FakePlayerProvider;
import buildcraft.lib.net.PacketBufferBC;
import buildcraft.lib.registry.BlockEntityRegistry;
import buildcraft.lib.tile.TileBC_Neptune;
import buildcraft.transport.*;
import buildcraft.transport.pipe.flow.PipeFlowFluids;
import buildcraft.transport.tile.TilePipeHolder;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.storage.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.*;
import net.minecraft.entity.ItemEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.*;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;

public final class FloodGateGameTests implements FabricGameTest {
    private static final BlockPos POS=new BlockPos(3,4,3);
    private static final long BUCKET=FluidConstants.BUCKET;
    private static final FluidVariant WATER=FluidVariant.of(Fluids.WATER);
    private static final Map<ServerWorld,Map<BlockPos,java.util.function.Consumer<net.minecraft.entity.player.PlayerEntity>>> DENIED=new WeakHashMap<>();
    static{net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player,world,hand,hit)->{
        var callback=DENIED.getOrDefault(world,Map.of()).get(hit.getBlockPos());if(callback==null)return ActionResult.PASS;callback.accept(player);return ActionResult.FAIL;
    });}
    private static TileFloodGate gate(ServerWorld world,BlockPos pos){
        world.setBlockState(pos,Blocks.AIR.getDefaultState());world.setBlockState(pos,BCFactoryBlocks.floodGate.getDefaultState());
        return (TileFloodGate)world.getBlockEntity(pos);
    }
    private static TileFloodGate gate(TestContext c){return gate(c.getWorld(),c.getAbsolutePos(POS));}
    private static void chamber(ServerWorld world,BlockPos gate,List<BlockPos> cells){
        var empty=new HashSet<>(cells);
        for(var face:Direction.values())if(!empty.contains(gate.offset(face)))world.setBlockState(gate.offset(face),Blocks.STONE.getDefaultState());
        for(var cell:cells)for(var face:Direction.values()){
            var wall=cell.offset(face);if(!wall.equals(gate) && !empty.contains(wall))world.setBlockState(wall,Blocks.STONE.getDefaultState());
        }
        for(var cell:cells)world.setBlockState(cell,Blocks.AIR.getDefaultState());
    }
    private static TileFloodGate corridor(TestContext c,int length){
        var tile=gate(c);var cells=new ArrayList<BlockPos>();for(int i=1;i<=length;i++)cells.add(tile.getPos().east(i));chamber(c.getWorld(),tile.getPos(),cells);
        tile.openSides.clear();tile.openSides.add(Direction.EAST);return tile;
    }
    private static void ticks(TileFloodGate tile,int count){for(int i=0;i<count;i++)tile.tick();}
    private static void fill(TestContext c,Storage<FluidVariant> tank,FluidVariant fluid,long amount){try(var tx=Transaction.openOuter()){c.assertTrue(tank.insert(fluid,amount,tx)==amount,"Fixture insertion failed");tx.commit();}}
    private static void fill(TestContext c,TileFloodGate tile,FluidVariant fluid,long amount){fill(c,tile.tank,fluid,amount);}
    private static void source(TestContext c,BlockPos absolute,net.minecraft.fluid.Fluid fluid){c.assertTrue(fluid.matchesType(c.getWorld().getFluidState(absolute).getFluid()) && c.getWorld().getFluidState(absolute).isStill(),"Missing source at "+absolute);}

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void allFluidFacesKeepTwoBucketCapacityAndTransactionalDroplets(TestContext c){
        var tile=gate(c);var tag=new NbtCompound();tag.putString("batch","Remake by FelixMantra");var fluid=FluidVariant.of(Fluids.WATER,tag);
        for(var side:Direction.values()){
            var storage=FluidStorage.SIDED.find(c.getWorld(),tile.getPos(),side);c.assertTrue(storage!=null,"Missing sided fluid endpoint "+side);
            try(var outer=Transaction.openOuter()){
                c.assertTrue(storage.insert(fluid,3*BUCKET,outer)==2*BUCKET,"Wrong flood tank capacity");
                try(var nested=outer.openNested()){c.assertTrue(storage.extract(fluid,37,nested)==37,"Sub-mB extraction rounded");nested.commit();}
            }
            c.assertTrue(tile.getFluidAmount()==0,"Aborted transfer changed tank");
        }
        fill(c,tile,fluid,BUCKET-1);ticks(tile,80);c.assertTrue(tile.getFluidAmount()==BUCKET-1 && tile.getFluidVariant().equals(fluid),"Partial bucket was spent or lost NBT");
        var saved=tile.createNbt();tile.readNbt(saved);c.assertTrue(tile.getFluidAmount()==BUCKET-1 && tile.getFluidVariant().equals(fluid),"Saved fractional bucket lost NBT");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void eachSourceCostsOneBucketAndAtLeastSixteenTicksWithoutPower(TestContext c){
        var tile=corridor(c,2);fill(c,tile,WATER,2*BUCKET);ticks(tile,31);
        c.assertTrue(tile.getFluidAmount()==2*BUCKET && c.getWorld().isAir(tile.getPos().east()),"Placed before initial search/cadence");
        tile.tick();source(c,tile.getPos().east(),Fluids.WATER);c.assertTrue(tile.getFluidAmount()==BUCKET,"First bucket debit wrong");
        ticks(tile,15);c.assertTrue(c.getWorld().isAir(tile.getPos().east(2)),"Placement exceeded one bucket per sixteen ticks");
        tile.tick();source(c,tile.getPos().east(2),Fluids.WATER);c.assertTrue(tile.getFluidAmount()==0 && MjAPI.CONNECTOR.find(c.getWorld(),tile.getPos(),Direction.UP)==null,"Flooding needed/invented MJ or lost fluid");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void existingSourcesAreFreeAndFlowingCellsBecomeSources(TestContext c){
        var tile=corridor(c,3);c.getWorld().setBlockState(tile.getPos().east(),Blocks.WATER.getDefaultState());
        c.getWorld().setBlockState(tile.getPos().east(2),Blocks.WATER.getDefaultState().with(FluidBlock.LEVEL,3));fill(c,tile,WATER,2*BUCKET);ticks(tile,60);
        for(int i=1;i<=3;i++)source(c,tile.getPos().east(i),Fluids.WATER);
        c.assertTrue(tile.getFluidAmount()==0,"Existing source was charged or flow/air was skipped");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void liquidsStayAtOrBelowOutletWhileGasesRise(TestContext c){
        var tile=gate(c);var cells=List.of(tile.getPos().east(),tile.getPos().east().up(),tile.getPos().east().up(2));chamber(c.getWorld(),tile.getPos(),cells);
        tile.openSides.clear();tile.openSides.add(Direction.EAST);fill(c,tile,WATER,2*BUCKET);ticks(tile,160);
        source(c,cells.get(0),Fluids.WATER);c.assertTrue(c.getWorld().isAir(cells.get(1)) && tile.getFluidAmount()==BUCKET,"Liquid searched upward");
        tile.tank.getStorage().readNbt(new NbtCompound());chamber(c.getWorld(),tile.getPos(),cells);var gas=BCEnergyFluids.fuelGaseous[0];fill(c,tile,FluidVariant.of(gas),2*BUCKET);ticks(tile,60);
        source(c,cells.get(0),gas);source(c,cells.get(1),gas);fill(c,tile,FluidVariant.of(gas),BUCKET);ticks(tile,60);source(c,cells.get(2),gas);
        c.assertTrue(tile.getFluidAmount()==0,"Gas placement lost or duplicated a bucket");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void foreignFluidsPlantsAndWaterloggedSolidsDoNotBecomePaths(TestContext c){
        var tile=corridor(c,2);fill(c,tile,WATER,2*BUCKET);
        for(var obstruction:List.of(Blocks.LAVA.getDefaultState(),Blocks.DANDELION.getDefaultState(),Blocks.OAK_SLAB.getDefaultState().with(net.minecraft.state.property.Properties.WATERLOGGED,true))){
            c.getWorld().setBlockState(tile.getPos().east(),obstruction,Block.NOTIFY_LISTENERS|Block.FORCE_STATE);ticks(tile,300);
            c.assertTrue(tile.getFluidAmount()==2*BUCKET && c.getWorld().isAir(tile.getPos().east(2)),"Flood gate traversed/replaced foreign fluid, plant or waterlogged solid");
        }c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void blockedOrClosedRoutesCannotPlaceAtDistantQueuedCells(TestContext c){
        var tile=corridor(c,3);fill(c,tile,WATER,2*BUCKET);ticks(tile,32);source(c,tile.getPos().east(),Fluids.WATER);
        c.getWorld().setBlockState(tile.getPos().east(),Blocks.STONE.getDefaultState());ticks(tile,80);
        c.assertTrue(tile.getFluidAmount()==BUCKET && c.getWorld().isAir(tile.getPos().east(2)),"Queued target ignored changed path");
        c.getWorld().setBlockState(tile.getPos().east(),Blocks.WATER.getDefaultState());tile.openSides.clear();ticks(tile,80);
        c.assertTrue(tile.getFluidAmount()==BUCKET && c.getWorld().isAir(tile.getPos().east(2)),"Closed outlet continued flooding");
        tile.openSides.add(Direction.EAST);ticks(tile,60);source(c,tile.getPos().east(2),Fluids.WATER);c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativeWrenchTogglesFivePortsInBothHandsAndNeverOpensTop(TestContext c){
        var tile=gate(c);var player=c.createMockCreativeServerPlayerInWorld();player.refreshPositionAndAngles(Vec3d.ofCenter(tile.getPos()).add(0,0,-2).x,tile.getPos().getY(),tile.getPos().getZ()-2,0,0);
        for(var hand:Hand.values())for(var side:Direction.values()){
            player.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);player.setStackInHand(Hand.OFF_HAND,ItemStack.EMPTY);player.setStackInHand(hand,new ItemStack(BCCoreItems.wrench));
            var before=EnumSet.copyOf(tile.openSides);var hit=new BlockHitResult(Vec3d.ofCenter(tile.getPos()).add(Vec3d.of(side.getVector()).multiply(.5)),side,tile.getPos(),false);
            player.interactionManager.interactBlock(player,c.getWorld(),player.getStackInHand(hand),hand,hit);
            if(side==Direction.UP)c.assertTrue(before.equals(tile.openSides) && !tile.openSides.contains(Direction.UP),"Wrench opened top outlet");
            else{c.assertTrue(tile.openSides.contains(side)!=before.contains(side) && tile.getCachedState().get(BlockFloodGate.CONNECTED_MAP.get(side))==tile.openSides.contains(side),"Wrench/state mismatch "+side+" "+hand);}
        }
        c.assertTrue(tile.openSides.size()==5,"Two toggles did not restore ports");
        player.refreshPositionAndAngles(tile.getPos().getX()+20,tile.getPos().getY(),tile.getPos().getZ(),0,0);c.assertTrue(!tile.toggleSide(player,Direction.EAST),"Distant wrench accepted");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativeProtectionUsesOwnerAndDoesNotSpendOrPlace(TestContext c){
        var tile=corridor(c,2);var owner=c.createMockCreativeServerPlayerInWorld();tile.onPlacedBy(owner,new ItemStack(BCFactoryBlocks.floodGate));fill(c,tile,WATER,2*BUCKET);
        var checked=new boolean[]{false};var target=tile.getPos().east();
        DENIED.computeIfAbsent(c.getWorld(),w->new HashMap<>()).put(target,player->{checked[0]=true;c.assertTrue(player.getUuid().equals(owner.getUuid()) && player.getMainHandStack().isOf(Items.WATER_BUCKET),"Flood permission did not use owner/bucket");});
        try{ticks(tile,100);c.assertTrue(checked[0] && tile.getFluidAmount()==2*BUCKET && c.getWorld().isAir(target) && c.getWorld().isAir(target.east()),"Denied placement spent fluid or bypassed protected path");}
        finally{DENIED.get(c.getWorld()).remove(target);}
        // Idle scans back off up to 256 ticks; lifting protection is discovered on the next scan.
        ticks(tile,400);source(c,target,Fluids.WATER);source(c,target.east(),Fluids.WATER);
        var advancement=c.getWorld().getServer().getAdvancementLoader().get(new Identifier("buildcraftfactory:flooding_the_world"));
        c.assertTrue(advancement!=null && owner.getAdvancementTracker().getProgress(advancement).isDone(),"Owner flooding advancement absent");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void removingMachineDuringPermissionCallbackCannotDrainOrPlace(TestContext c){
        var tile=corridor(c,1);fill(c,tile,WATER,BUCKET);var target=tile.getPos().east();
        DENIED.computeIfAbsent(c.getWorld(),w->new HashMap<>()).put(target,player->tile.markRemoved());
        try{ticks(tile,40);c.assertTrue(c.getWorld().isAir(target) && tile.tank.getStorage().getAmount()==BUCKET,"Removed machine placed or lost fluid");}
        finally{DENIED.get(c.getWorld()).remove(target);tile.cancelRemoval();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void savedTankAndLegacySideMasksRebuildPathsWithoutDuplicatingSources(TestContext c){
        var tile=corridor(c,3);fill(c,tile,WATER,2*BUCKET);ticks(tile,32);var saved=tile.createNbt();var old=FluidStorage.SIDED.find(c.getWorld(),tile.getPos(),Direction.UP);
        var restored=BlockEntityRegistry.type(TileFloodGate.class).instantiate(tile.getPos(),tile.getCachedState());restored.readNbt(saved);c.getWorld().removeBlockEntity(tile.getPos());c.getWorld().addBlockEntity(restored);restored.onLoad();
        c.assertTrue(restored.queue.isEmpty() && restored.getFluidAmount()==BUCKET,"Reload retained derived queue or lost buffered bucket");ticks(restored,70);
        source(c,restored.getPos().east(2),Fluids.WATER);c.assertTrue(c.getWorld().isAir(restored.getPos().east(3)) && restored.getFluidAmount()==0,"Reload duplicated output");
        try(var tx=Transaction.openOuter()){c.assertTrue(old.insert(WATER,1,tx)==0,"Replaced storage stayed live");}
        var legacy=restored.createNbt();legacy.putByteArray("openSides",new byte[]{(byte)((1<<Direction.NORTH.ordinal())|(1<<Direction.UP.ordinal()))});restored.readNbt(legacy);restored.onLoad();
        c.assertTrue(restored.openSides.equals(EnumSet.of(Direction.NORTH)) && restored.getCachedState().get(BlockFloodGate.CONNECTED_MAP.get(Direction.NORTH)),"Legacy bitmap failed or enabled top");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void reachIncludesSixtyFourBlocksAndExcludesSixtyFive(TestContext c){
        var world=c.getWorld();var pos=new BlockPos(30000,100,30000);var tile=gate(world,pos);var cells=new ArrayList<BlockPos>();for(int i=1;i<=65;i++)cells.add(pos.east(i));chamber(world,pos,cells);
        for(int i=1;i<64;i++)world.setBlockState(pos.east(i),Blocks.WATER.getDefaultState(),Block.NOTIFY_LISTENERS|Block.FORCE_STATE);
        tile.openSides.clear();tile.openSides.add(Direction.EAST);fill(c,tile,WATER,2*BUCKET);ticks(tile,90);
        source(c,pos.east(64),Fluids.WATER);c.assertTrue(world.isAir(pos.east(65)) && tile.getFluidAmount()==BUCKET,"Flood range did not retain original 64-block limit");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void searchYieldsAndKeepsOriginalFourThousandTargetLimit(TestContext c){
        var tile=gate(c);fill(c,tile,WATER,BUCKET);ticks(tile,17);
        c.assertTrue(tile.isSearching() && tile.queue.size()<=1024 && tile.getFluidAmount()==BUCKET,"Search did not yield after bounded tick work");
        for(int i=0;i<20 && tile.isSearching();i++)tile.tick();
        c.assertTrue(!tile.isSearching() && tile.queue.size()==4096 && tile.getFluidAmount()==BUCKET,"Search lost original 4096 candidate capacity or placed early");
        for(var target:tile.queue)c.assertTrue(target.getSquaredDistance(tile.getPos())<=64*64 && c.getWorld().isChunkLoaded(target),"Search crossed radius/unloaded chunk");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void unloadedOutletDoesNotForceNeighborChunk(TestContext c){
        var world=c.getWorld();var pos=new BlockPos(48015,120,48008);world.getChunk(pos);
        world.setBlockState(pos,Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS|Block.FORCE_STATE);
        world.setBlockState(pos,BCFactoryBlocks.floodGate.getDefaultState(),Block.NOTIFY_LISTENERS|Block.FORCE_STATE);
        var tile=(TileFloodGate)world.getBlockEntity(pos);tile.openSides.clear();tile.openSides.add(Direction.EAST);var next=pos.east();
        c.assertTrue(!world.isChunkLoaded(next),"Fixture neighbor chunk already loaded");fill(c,tile,WATER,BUCKET);ticks(tile,600);
        c.assertTrue(!world.isChunkLoaded(next) && tile.getFluidAmount()==BUCKET && tile.queue.isEmpty(),"Search loaded neighboring chunk or consumed fluid: loaded="+world.isChunkLoaded(next)+", amount="+tile.getFluidAmount()+", queued="+tile.queue.size());c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativeBucketExchangeWorksWithAllOutletsClosed(TestContext c){
        var tile=gate(c);tile.openSides.clear();var player=FakePlayerProvider.INSTANCE.getFakePlayer(c.getWorld(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"FloodTester"),tile.getPos());
        player.setStackInHand(Hand.MAIN_HAND,new ItemStack(Items.WATER_BUCKET));var hit=new BlockHitResult(Vec3d.ofCenter(tile.getPos()),Direction.UP,tile.getPos(),false);
        c.assertTrue(player.interactionManager.interactBlock(player,c.getWorld(),player.getMainHandStack(),Hand.MAIN_HAND,hit).isAccepted() && player.getMainHandStack().isOf(Items.BUCKET) && tile.getFluidAmount()==BUCKET,"Native bucket insertion failed");ticks(tile,100);
        c.assertTrue(tile.getFluidAmount()==BUCKET,"Closed gate spent bucket");
        c.assertTrue(player.interactionManager.interactBlock(player,c.getWorld(),player.getMainHandStack(),Hand.MAIN_HAND,hit).isAccepted() && player.getMainHandStack().isOf(Items.WATER_BUCKET) && tile.getFluidAmount()==0,"Native bucket extraction lost fluid");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void removalRecoversTaggedRemainderAndInvalidatesCachedViews(TestContext c){
        var tile=corridor(c,1);var tag=new NbtCompound();tag.putString("batch","retain me");var fluid=FluidVariant.of(BCEnergyFluids.crudeOil[0],tag);fill(c,tile,fluid,BUCKET+37);
        var storage=FluidStorage.SIDED.find(c.getWorld(),tile.getPos(),Direction.NORTH);var view=storage.iterator().next();var box=new Box(tile.getPos()).expand(1);
        c.getWorld().getEntitiesByClass(ItemEntity.class,box,e->true).forEach(ItemEntity::discard);c.getWorld().breakBlock(tile.getPos(),true);tile.onRemove();ticks(tile,100);
        long amount=c.getWorld().getEntitiesByClass(ItemEntity.class,box,e->true).stream().map(e->ItemFragileFluidContainer.getFluid(e.getStack())).filter(f->f.getFluidVariant().equals(fluid)).mapToLong(f->f.getAmount()).sum();
        c.assertTrue(amount==BUCKET+37 && c.getWorld().isAir(tile.getPos().east()),"Removal lost/duplicated tagged droplets or continued flooding");
        try(var tx=Transaction.openOuter()){c.assertTrue(storage.insert(fluid,1,tx)==0 && view.extract(fluid,1,tx)==0,"Removed flood gate retained fluid access");}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void malformedAndServerDirectedSnapshotsCannotChangeOutlets(TestContext c) throws Exception {
        var tile=gate(c);fill(c,tile,WATER,BUCKET);var before=tile.createNbt();
        for(int kind=0;kind<3;kind++){
            var packet=PacketBufferBC.write(b->tile.writePayload(TileBC_Neptune.NET_RENDER_DATA,b,TileBC_Neptune.NetSide.SERVER));
            try{
                if(kind==0)packet.writerIndex(packet.writerIndex()-1);
                if(kind==1)packet.setLong(packet.writerIndex()-8,2*BUCKET+1);
                boolean rejected=false;
                try{tile.readPayload(TileBC_Neptune.NET_RENDER_DATA,packet,kind==2?TileBC_Neptune.NetSide.SERVER:TileBC_Neptune.NetSide.CLIENT,null);}catch(IndexOutOfBoundsException|java.io.IOException expected){rejected=true;}
                c.assertTrue((kind==2 || rejected) && before.equals(tile.createNbt()),"Invalid snapshot changed persisted tank/sides");
            }finally{packet.release();}
        }c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void ultrawarmWorldUsesNativeWaterEvaporationWithoutCreatingSource(TestContext c){
        var world=c.getWorld().getServer().getWorld(net.minecraft.world.World.NETHER);var pos=new BlockPos(30000,120,30000);var tile=gate(world,pos);chamber(world,pos,List.of(pos.east()));
        tile.openSides.clear();tile.openSides.add(Direction.EAST);fill(c,tile,WATER,BUCKET);ticks(tile,40);
        c.assertTrue(world.getDimension().ultrawarm() && world.isAir(pos.east()) && tile.getFluidAmount()==0,"Water placement ignored native ultrawarm evaporation");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void originalSurvivalRecipeConsumesExactIngredients(TestContext c){
        var player=c.createMockCreativeServerPlayerInWorld();var handler=new net.minecraft.screen.CraftingScreenHandler(1,player.getInventory(),net.minecraft.screen.ScreenHandlerContext.create(c.getWorld(),c.getAbsolutePos(POS)));
        var gear=net.minecraft.registry.Registries.ITEM.get(new Identifier("buildcraftcore:gear_iron"));Item[] ingredients={Items.IRON_INGOT,gear,Items.IRON_INGOT,Items.IRON_BARS,BCFactoryBlocks.tank.asItem(),Items.IRON_BARS,Items.IRON_INGOT,Items.IRON_BARS,Items.IRON_INGOT};
        for(int i=0;i<9;i++)handler.getSlot(i+1).setStack(new ItemStack(ingredients[i]));handler.onContentChanged(handler.getSlot(1).inventory);
        c.assertTrue(handler.getSlot(0).getStack().isOf(BCFactoryBlocks.floodGate.asItem()) && handler.getSlot(0).getStack().getCount()==1,"Flood gate recipe absent/wrong count");handler.onSlotClick(0,0,net.minecraft.screen.slot.SlotActionType.PICKUP,player);
        for(int i=1;i<=9;i++)c.assertTrue(handler.getSlot(i).getStack().isEmpty(),"Flood recipe retained an ingredient");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=230)
    public void nativeFluidPipeFeedsClosedGateAndPlacesOnlyWholeBuckets(TestContext c){
        var tile=corridor(c,1);tile.openSides.clear();var pipePos=tile.getPos().west();var world=c.getWorld();world.setBlockState(pipePos,BCTransportBlocks.pipeHolder.getDefaultState());
        world.setBlockState(pipePos.west(),BCFactoryBlocks.tank.getDefaultState());
        var pipe=(TilePipeHolder)world.getBlockEntity(pipePos);pipe.onPlacedBy(null,new ItemStack(BCTransportItems.pipeFluidWood));pipe.tick();
        var fluid=FluidVariant.of(BCEnergyFluids.crudeOil[0]);var source=FluidStorage.SIDED.find(world,pipePos.west(),Direction.EAST);fill(c,source,fluid,BUCKET+37);
        var receiver=(IMjReceiver)MjAPI.CONNECTOR.find(world,pipePos,Direction.UP);long cost=((BUCKET+37)*1000+80)/81;
        c.assertTrue(receiver!=null && receiver.receivePower(2*MjAPI.MJ,false)==2*MjAPI.MJ-cost,"Native extraction cost or endpoint incorrect");
        c.runAtTick(160,()->{
            c.assertTrue(tile.getFluidAmount()==BUCKET+37 && ((PipeFlowFluids)pipe.getPipe().flow).getAmount()==0 && world.isAir(tile.getPos().east()),"Native pipe lost droplets or closed outlet leaked");tile.openSides.add(Direction.EAST);
        });
        c.runAtTick(215,()->{source(c,tile.getPos().east(),BCEnergyFluids.crudeOil[0]);c.assertTrue(tile.getFluidAmount()==37 && tile.getFluidVariant().equals(fluid),"Flood gate rounded/placed partial pipe delivery");c.complete();});
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativeTankInsertionUnlocksFloodingParentAdvancement(TestContext c){
        c.setBlockState(POS,BCFactoryBlocks.tank);var tank=(TileTank)c.getBlockEntity(POS);var player=c.createMockCreativeServerPlayerInWorld();player.refreshPositionAndAngles(tank.getPos().getX(),tank.getPos().getY(),tank.getPos().getZ()-2,0,0);
        player.setStackInHand(Hand.MAIN_HAND,new ItemStack(Items.WATER_BUCKET));
        c.assertTrue(tank.onActivated(player,Hand.MAIN_HAND,Direction.NORTH,.5f,.5f,0),"Manual tank insertion rejected");
        var advancement=c.getWorld().getServer().getAdvancementLoader().get(new Identifier("buildcraftfactory:fluid_storage"));
        c.assertTrue(advancement!=null && player.getAdvancementTracker().getProgress(advancement).isDone(),"Flooding parent advancement absent/unreachable");c.complete();
    }
}
