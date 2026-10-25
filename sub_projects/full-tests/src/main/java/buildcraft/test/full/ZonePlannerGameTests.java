/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;
import java.util.*;
import buildcraft.core.*;
import buildcraft.core.item.*;
import buildcraft.robotics.*;
import buildcraft.robotics.container.ContainerZonePlanner;
import buildcraft.robotics.tile.TileZonePlanner;
import buildcraft.robotics.zone.*;
import buildcraft.lib.net.*;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.recipe.*;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.*;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import io.netty.buffer.Unpooled;
public final class ZonePlannerGameTests implements FabricGameTest {
    private static final Map<ServerWorld,Set<BlockPos>> DENIED=new WeakHashMap<>();
    static {UseBlockCallback.EVENT.register((p,w,h,hit)->DENIED.getOrDefault(w,Set.of()).contains(hit.getBlockPos())?ActionResult.FAIL:ActionResult.PASS);}
    private static BlockPos base(TestContext c){return c.getAbsolutePos(new BlockPos(2,150,2));}
    private static ServerPlayerEntity player(TestContext c,BlockPos at){var p=c.createMockCreativeServerPlayerInWorld();p.getInventory().clear();p.refreshPositionAndAngles(at.getX()+.5,at.getY()+1,at.getZ()+.5,0,0);return p;}
    private static TileZonePlanner tile(TestContext c){var at=base(c);c.getWorld().setBlockState(at,Blocks.AIR.getDefaultState());c.getWorld().setBlockState(at,BCRoboticsBlocks.zonePlanner.getDefaultState());var t=(TileZonePlanner)c.getWorld().getBlockEntity(at);t.onPlacedBy(player(c,at),new ItemStack(BCRoboticsBlocks.zonePlanner));return t;}
    private static ItemStack brush(DyeColor colour){return ItemPaintbrush_BC8.coloured(new ItemStack(BCCoreItems.paintbrush),colour);}
    private static ItemStack map(){return new ItemStack(BCCoreItems.mapLocation);}
    private static void ticks(TileZonePlanner t,int n){for(int i=0;i<n;i++)t.tick();}
    private static void check(TestContext c,boolean ok,String s){c.assertTrue(ok,s);}
    private static TileZonePlanner reload(TileZonePlanner t){var w=t.getWorld();var p=t.getPos();var state=t.getCachedState();var n=t.createNbtWithIdentifyingData();w.removeBlockEntity(p);var copy=(TileZonePlanner)BlockEntity.createFromNbt(p,state,n);w.addBlockEntity(copy);copy.onLoad();return copy;}
    private static ContainerZonePlanner screen(TestContext c,TileZonePlanner t){var p=player(c,t.getPos());BCRoboticsScreens.open(p,t.getPos());check(c,p.currentScreenHandler instanceof ContainerZonePlanner,"Native planner screen missing");return (ContainerZonePlanner)p.currentScreenHandler;}
    private static ActionResult use(ServerPlayerEntity p,BlockPos pos){return p.interactionManager.interactBlock(p,p.getWorld(),p.getMainHandStack(),Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(pos),Direction.UP,pos,false));}
    private static void output(TileZonePlanner t,DyeColor colour){t.invOutputPaintbrush.setStackInSlot(0,brush(colour));t.invOutputMapLocation.setStackInSlot(0,map());}
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativePaintingUsesExactlySixtyFourChargesAndPreservesNames(TestContext c){
        var at=base(c);var p=player(c,at);var s=brush(DyeColor.RED);s.setCustomName(Text.literal("Felix brush"));p.setStackInHand(Hand.MAIN_HAND,s);
        for(int i=0;i<64;i++){c.getWorld().setBlockState(at,Blocks.WHITE_WOOL.getDefaultState());check(c,use(p,at).isAccepted()&&c.getWorld().getBlockState(at).isOf(Blocks.RED_WOOL),"Native wool painting failed");}
        check(c,ItemPaintbrush_BC8.getColour(p.getMainHandStack())==null&&p.getMainHandStack().getCount()==1&&p.getMainHandStack().hasCustomName(),"Exhausted brush lost item/name or retained paint");c.getWorld().setBlockState(at,Blocks.WHITE_WOOL.getDefaultState());check(c,use(p,at)==ActionResult.FAIL&&c.getWorld().getBlockState(at).isOf(Blocks.WHITE_WOOL),"Clean brush recolored wool");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void allDyeColoursCraftFromCleanBrushWithExactData(TestContext c){
        var r=(CraftingRecipe)c.getWorld().getRecipeManager().get(new Identifier("buildcraftcore:paintbrush_dye")).orElseThrow();
        for(var colour:DyeColor.values()){var grid=new buildcraft.lib.tile.craft.WorkbenchCrafting.Grid(2,2);var original=new ItemStack(BCCoreItems.paintbrush);original.setCustomName(Text.literal("Named brush"));grid.setStack(0,original);grid.setStack(3,new ItemStack(DyeItem.byColor(colour)));check(c,r.matches(grid,c.getWorld()),"Colour recipe missing");var result=r.craft(grid,c.getWorld().getRegistryManager());check(c,ItemPaintbrush_BC8.getColour(result)==colour&&ItemPaintbrush_BC8.usesLeft(result)==64&&result.hasCustomName()&&ItemPaintbrush_BC8.getColour(original)==null,"Dye craft mutated input or lost data");grid.setStack(0,result);check(c,!r.matches(grid,c.getWorld()),"Full colored brush accepted clean-only dye recipe");}
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void paintingProtectsBlocksTransactionsAndUnchangedColours(TestContext c){
        var at=base(c);var p=player(c,at);p.setStackInHand(Hand.MAIN_HAND,brush(DyeColor.BLUE));c.getWorld().setBlockState(at,Blocks.BLUE_WOOL.getDefaultState());check(c,use(p,at)==ActionResult.FAIL&&ItemPaintbrush_BC8.usesLeft(p.getMainHandStack())==64,"Same-color paint consumed charge");c.getWorld().setBlockState(at,Blocks.WHITE_WOOL.getDefaultState());DENIED.computeIfAbsent(c.getWorld(),w->new HashSet<>()).add(at);
        try{check(c,use(p,at)==ActionResult.FAIL&&c.getWorld().getBlockState(at).isOf(Blocks.WHITE_WOOL),"Protection bypassed");}finally{DENIED.get(c.getWorld()).remove(at);}
        try(var tx=Transaction.openOuter()){check(c,use(p,at)==ActionResult.FAIL&&ItemPaintbrush_BC8.usesLeft(p.getMainHandStack())==64,"Painting mutated a foreign transaction");}
        c.getWorld().setBlockState(at,Blocks.WHITE_STAINED_GLASS_PANE.getDefaultState().with(net.minecraft.state.property.Properties.NORTH,true).with(net.minecraft.state.property.Properties.WATERLOGGED,true));check(c,use(p,at).isAccepted(),"Native pane paint failed");var state=c.getWorld().getBlockState(at);check(c,state.isOf(Blocks.BLUE_STAINED_GLASS_PANE)&&state.get(net.minecraft.state.property.Properties.WATERLOGGED),"Painting lost native pane state");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativePipePaintAndCleanBrushKeepPipeContents(TestContext c){
        var at=base(c);var p=player(c,at);c.getWorld().setBlockState(at,Blocks.AIR.getDefaultState());var item=Registries.ITEM.get(new Identifier("buildcrafttransport:pipe_stone_item"));p.setStackInHand(Hand.MAIN_HAND,new ItemStack(item));c.getWorld().setBlockState(at.down(),Blocks.STONE.getDefaultState());p.interactionManager.interactBlock(p,c.getWorld(),p.getMainHandStack(),Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(at.down()),Direction.UP,at.down(),false));var holder=buildcraft.transport.block.BlockPipeHolder.getPipe(c.getWorld(),at);check(c,holder!=null&&holder.getPipe()!=null,"Pipe fixture failed");var pipe=holder.getPipe();var cargo=new ItemStack(Items.DIAMOND,3);cargo.setCustomName(Text.literal("Preserved cargo"));((buildcraft.transport.pipe.flow.PipeFlowItems)pipe.flow).insertItemsForce(cargo.copy(),Direction.WEST,null,.08);p.setStackInHand(Hand.MAIN_HAND,brush(DyeColor.CYAN));check(c,use(p,at).isAccepted()&&pipe.getColour()==DyeColor.CYAN&&ItemPaintbrush_BC8.usesLeft(p.getMainHandStack())==63,"Native pipe paint failed");p.setStackInHand(Hand.MAIN_HAND,new ItemStack(BCCoreItems.paintbrush));check(c,use(p,at).isAccepted()&&holder.getPipe()==pipe&&pipe.getColour()==null,"Clean brush replaced pipe or failed to erase");var drops=net.minecraft.util.collection.DefaultedList.<ItemStack>of();pipe.flow.addDrops(drops,0);check(c,drops.size()==1&&ItemStack.areEqual(drops.get(0),cargo),"Painting changed travelling cargo");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void exportPaysOneMapAfterTwoHundredTicksAndKeepsBrush(TestContext c){
        var t=tile(c);t.layers[14].set(-2,3,true);output(t,DyeColor.RED);var input=t.invOutputMapLocation.getStackInSlot(0);input.setCustomName(Text.literal("Keep map name"));ticks(t,199);check(c,t.progress(true)==199&&t.invOutputResult.getStackInSlot(0).isEmpty()&&!t.invOutputMapLocation.getStackInSlot(0).isEmpty(),"Output completed early");t.tick();var result=t.invOutputResult.getStackInSlot(0);var plan=BCCoreItems.mapLocation.getZone(result);check(c,plan!=null&&plan.contains(new Vec3d(t.getPos().getX()-1.5,0,t.getPos().getZ()+3.5))&&result.hasCustomName()&&t.invOutputMapLocation.getStackInSlot(0).isEmpty()&&ItemPaintbrush_BC8.usesLeft(t.invOutputPaintbrush.getStackInSlot(0))==64,"Export lost map/brush/data");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void importRelocatesAbsoluteZoneAndReturnsBlankOnlyOnCompletion(TestContext c){
        var t=tile(c);var zone=new ZonePlan();zone.set(t.getPos().getX()+4,t.getPos().getZ()-7,true);var input=map();input.setCustomName(Text.literal("Preserve"));ItemMapLocation.setZone(input,zone);input.getOrCreateNbt().putString("dimension",c.getWorld().getRegistryKey().getValue().toString());t.invInputMapLocation.setStackInSlot(0,input);t.invInputPaintbrush.setStackInSlot(0,brush(DyeColor.BLUE));ticks(t,199);check(c,!t.invInputMapLocation.getStackInSlot(0).isEmpty()&&t.layers[11].getAll().isEmpty(),"Import consumed map early");t.tick();var result=t.invInputResult.getStackInSlot(0);check(c,t.layers[11].get(4,-7)&&!ItemMapLocation.isRecorded(result)&&result.hasCustomName()&&t.invInputMapLocation.getStackInSlot(0).isEmpty(),"Import lost zone or blank/name");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void exportProgressAndAllLayersSurviveNativeReload(TestContext c){
        var t=tile(c);for(int i=0;i<16;i++)t.layers[i].set(i,-i,true);output(t,DyeColor.GREEN);ticks(t,123);t=reload(t);check(c,t.progress(true)==123,"Reload lost partial progress");for(int i=0;i<16;i++)check(c,t.layers[i].get(i,-i),"Reload lost layer");ticks(t,76);check(c,t.invOutputResult.getStackInSlot(0).isEmpty(),"Reload duplicated progress");t.tick();check(c,BCCoreItems.mapLocation.getZone(t.invOutputResult.getStackInSlot(0))!=null,"Reloaded export failed");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void blockedResultsAndChangedInputsKeepAllItems(TestContext c){
        var t=tile(c);output(t,DyeColor.YELLOW);t.invOutputResult.setStackInSlot(0,map());ticks(t,220);check(c,!t.invOutputMapLocation.getStackInSlot(0).isEmpty()&&t.progress(true)==0,"Blocked output consumed map");t.invOutputResult.setStackInSlot(0,ItemStack.EMPTY);ticks(t,100);t.invOutputPaintbrush.setStackInSlot(0,brush(DyeColor.RED));check(c,t.progress(true)==0,"Changed brush retained old progress");try(var tx=Transaction.openOuter()){ticks(t,220);check(c,t.progress(true)==0&&!t.invOutputMapLocation.getStackInSlot(0).isEmpty(),"Transfer ran within foreign transaction");}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void dimensionMismatchAndMalformedLayersPreserveSourceData(TestContext c){
        var t=tile(c);var zone=new ZonePlan();zone.set(1,2,true);var input=map();ItemMapLocation.setZone(input,zone);input.getOrCreateNbt().putString("dimension","minecraft:the_nether");t.invInputMapLocation.setStackInSlot(0,input);t.invInputPaintbrush.setStackInSlot(0,brush(DyeColor.RED));ticks(t,220);check(c,t.status()==1&&!t.invInputMapLocation.getStackInSlot(0).isEmpty(),"Foreign dimension map was consumed");var n=t.createNbt();var bad=new NbtCompound();bad.putString("chunkMapping","broken original");n.put("layer_14",bad);t.readNbt(n);output(t,DyeColor.RED);ticks(t,220);check(c,t.hasUnreadableLayer(14)&&t.createNbt().getCompound("layer_14").equals(bad)&&!t.invOutputMapLocation.getStackInSlot(0).isEmpty()&&t.invOutputResult.getStackInSlot(0).isEmpty(),"Malformed saved layer erased or exported");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativeScreenTransfersExactlyOneItemAndRejectsStaleAccess(TestContext c){
        var t=tile(c);var p=player(c,t.getPos());p.setStackInHand(Hand.MAIN_HAND,brush(DyeColor.RED));check(c,use(p,t.getPos()).isAccepted()&&p.currentScreenHandler instanceof ContainerZonePlanner&&ItemPaintbrush_BC8.usesLeft(p.getMainHandStack())==64,"Held brush blocked native planner screen or consumed paint");var h=(ContainerZonePlanner)p.currentScreenHandler;check(c,h.getType()==BCRoboticsScreens.ZONE&&h.slots.size()==58,"Native slots missing");h.player.getInventory().setStack(9,new ItemStack(BCCoreItems.mapLocation,3));h.quickMove(h.player,22);check(c,t.invOutputMapLocation.getStackInSlot(0).getCount()==1&&h.player.getInventory().getStack(9).getCount()==2,"Shift transfer ignored one-item limit");check(c,!h.slots.get(18).canInsert(map())&&!h.slots.get(21).canInsert(map()),"Output slot accepts inserts");c.getWorld().removeBlockEntity(t.getPos());check(c,!h.canUse(h.player)&&h.quickMove(h.player,20).isEmpty(),"Stale planner screen mutated inventory");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void rectangleEditsUseCursorColourAndPreserveOtherLayers(TestContext c){
        var t=tile(c);var h=screen(c,t);var p=t.getPos();h.setCursorStack(brush(DyeColor.RED));
        // A headless TestServer has view distance zero; use its one authorized chunk.
        int x=p.getX()&~15,z=p.getZ()&~15,dx=x-p.getX(),dz=z-p.getZ();
        check(c,h.edit(14,x,z,x+15,z+15,true),"Valid rectangle rejected, viewDistance="+c.getWorld().getServer().getPlayerManager().getViewDistance());
        check(c,t.layers[14].getAll().size()==256,"Rectangle lost cells");
        check(c,h.edit(14,x,z,x,z,false)&&t.layers[14].getAll().size()==255&&t.layers[14].get(dx+15,dz+15),"Erase lost last cell");
        check(c,!h.edit(11,x,z,x,z,true)&&t.layers[11].getAll().isEmpty(),"Cursor colour mismatch accepted");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void invalidEditPacketsAndOversizedRectanglesLeaveLayersIntact(TestContext c)throws Exception{
        var t=tile(c);var h=screen(c,t);var p=t.getPos();h.setCursorStack(brush(DyeColor.RED));t.layers[14].set(0,0,true);check(c,!h.edit(14,p.getX(),p.getZ(),Integer.MAX_VALUE,Integer.MAX_VALUE,true),"Unbounded rectangle accepted");var b=PacketBufferBC.write(buf->{buf.writeByte(14);buf.writeInt(p.getX());buf.writeInt(p.getZ());buf.writeInt(p.getX());buf.writeInt(p.getZ());buf.writeBoolean(false);buf.writeByte(1);});boolean rejected=false;try{h.readMessage(ContainerZonePlanner.EDIT,b,false,null);}catch(java.io.IOException expected){rejected=true;}finally{b.release();}check(c,rejected&&t.layers[14].get(0,0),"Trailing packet changed layer");((ServerPlayerEntity)h.player).closeHandledScreen();check(c,!h.edit(14,p.getX(),p.getZ(),p.getX(),p.getZ(),false),"Closed screen retained editing");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void independentViewerEditsDoNotReplaceEntireLayers(TestContext c){
        var t=tile(c);var first=screen(c,t);var second=screen(c,t);var p=t.getPos();first.setCursorStack(brush(DyeColor.BLUE));second.setCursorStack(brush(DyeColor.BLUE));check(c,first.edit(11,p.getX(),p.getZ(),p.getX(),p.getZ(),true)&&second.edit(11,p.getX()+1,p.getZ(),p.getX()+1,p.getZ(),true),"Two authorized viewers could not edit");check(c,t.layers[11].get(0,0)&&t.layers[11].get(1,0),"Second viewer erased first viewer's region");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void mapRequestsRequireOpenScreenDimensionAndBoundedRate(TestContext c){
        var t=tile(c);var h=screen(c,t);var p=(ServerPlayerEntity)h.player;var key=new ZonePlannerMapChunkKey(p.getChunkPos(),c.getWorld().getRegistryKey().getValue(),10);for(int i=0;i<4;i++)check(c,new MessageZoneMapRequest(key).response(p)!=null,"Allowed map request rejected");check(c,new MessageZoneMapRequest(key).response(p)==null,"Map request rate unbounded");p.closeHandledScreen();check(c,new MessageZoneMapRequest(key).response(p)==null,"Closed screen received map data");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void largeLayerSerializationAndSmallEditPacketsStayWithinNativeBounds(TestContext c){
        var plan=new ZonePlan();for(int i=0;i<ZonePlan.MAX_CHUNKS;i++)plan.set(i*16+15,15,true);var b=PacketBufferBC.write(plan::writeToByteBuf);try{check(c,b.readableBytes()<1024*1024,"One layer exceeds native client payload");var copy=new ZonePlan().readFromByteBuf(b);check(c,copy.getAll().size()==4096&&copy.get(4095*16+15,15),"Large layer truncated");}finally{b.release();}var t=tile(c);var h=screen(c,t);h.setCursorStack(brush(DyeColor.RED));try(var tx=Transaction.openOuter()){check(c,!h.edit(14,t.getPos().getX(),t.getPos().getZ(),t.getPos().getX(),t.getPos().getZ(),true),"Foreign transaction accepted edit");}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativePlannerAndCleanBrushRecipesMatchOriginalPlannedMaterials(TestContext c){
        var brush=(CraftingRecipe)c.getWorld().getRecipeManager().get(new Identifier("buildcraftcore:paintbrush")).orElseThrow();var g=new buildcraft.lib.tile.craft.WorkbenchCrafting.Grid(3,3);g.setStack(1,new ItemStack(Items.STRING));g.setStack(2,new ItemStack(Items.WHITE_WOOL));g.setStack(4,new ItemStack(BCCoreItems.gearWood));g.setStack(5,new ItemStack(Items.STRING));g.setStack(6,new ItemStack(Items.STICK));check(c,brush.matches(g,c.getWorld())&&brush.craft(g,c.getWorld().getRegistryManager()).isOf(BCCoreItems.paintbrush),"Original brush recipe unavailable");var r=(CraftingRecipe)c.getWorld().getRecipeManager().get(new Identifier("buildcraftrobotics:zone_planner")).orElseThrow();g.clear();for(int i:new int[]{0,2,3,5,6,8})g.setStack(i,new ItemStack(Items.OAK_PLANKS));g.setStack(1,new ItemStack(Registries.ITEM.get(new Identifier("buildcrafttransport:pipe_diamond_item"))));g.setStack(4,new ItemStack(Items.CHEST));g.setStack(7,new ItemStack(Items.PISTON));check(c,r.matches(g,c.getWorld())&&r.craft(g,c.getWorld().getRegistryManager()).isOf(BCRoboticsBlocks.zonePlanner.asItem()),"Planned planner recipe unavailable");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void survivalBreakKeepsInventoryAndNativeMetalProperties(TestContext c){
        var t=tile(c);var p=player(c,t.getPos());p.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);p.setStackInHand(Hand.MAIN_HAND,new ItemStack(Items.IRON_PICKAXE));
        check(c,p.canHarvest(t.getCachedState())&&t.getCachedState().getHardness(c.getWorld(),t.getPos())==5f,"Native planner tool/hardness wrong");
        t.invPaintbrushes.setStackInSlot(0,brush(DyeColor.BLUE));output(t,DyeColor.RED);t.invInputResult.setStackInSlot(0,map());
        var recorded=map();var zone=new ZonePlan();zone.set(-7,11,true);ItemMapLocation.setZone(recorded,zone);t.invInputMapLocation.setStackInSlot(0,recorded);
        check(c,p.interactionManager.tryBreakBlock(t.getPos()),"Native survival break failed");int maps=0,brushes=0,planners=0,records=0;
        for(var entity:c.getWorld().getEntitiesByClass(net.minecraft.entity.ItemEntity.class,new Box(t.getPos()).expand(2),e->true)){
            var stack=entity.getStack();if(stack.isOf(BCCoreItems.mapLocation)){maps+=stack.getCount();if(ItemMapLocation.isRecorded(stack)){records++;check(c,((ZonePlan)BCCoreItems.mapLocation.getZone(stack)).get(-7,11),"Breaking lost recorded map data");}}
            if(stack.isOf(BCCoreItems.paintbrush))brushes+=stack.getCount();if(stack.isOf(BCRoboticsBlocks.zonePlanner.asItem()))planners+=stack.getCount();entity.discard();
        }
        check(c,maps==3&&brushes==2&&planners==1&&records==1,"Breaking lost or duplicated inventory");c.complete();
    }

}
