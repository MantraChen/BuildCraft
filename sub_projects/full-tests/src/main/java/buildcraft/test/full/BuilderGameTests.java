/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;

import java.util.*;
import buildcraft.api.enums.EnumSnapshotType;
import buildcraft.api.mj.*;
import buildcraft.builders.*;
import buildcraft.builders.container.ContainerBuilder;
import buildcraft.builders.snapshot.*;
import buildcraft.builders.tile.TileBuilder;
import buildcraft.core.BCCoreBlocks;
import buildcraft.core.marker.*;
import buildcraft.core.tile.TileMarkerPath;
import buildcraft.lib.mj.TransactionalMjReceiver;
import buildcraft.lib.net.PacketBufferBC;
import buildcraft.lib.tile.TileBC_Neptune;
import net.fabricmc.fabric.api.event.player.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.item.*;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;

public final class BuilderGameTests implements FabricGameTest {
    private static final BlockPos POS=new BlockPos(2,80,2);
    private static final Map<ServerWorld,Set<BlockPos>> USE_DENIED=new WeakHashMap<>(),BREAK_DENIED=new WeakHashMap<>();
    private static UUID observedOwner;
    static {
        UseBlockCallback.EVENT.register((player,world,hand,hit)->{if(USE_DENIED.getOrDefault(world,Set.of()).contains(hit.getBlockPos())){observedOwner=player.getUuid();return ActionResult.FAIL;}return ActionResult.PASS;});
        PlayerBlockBreakEvents.BEFORE.register((world,player,pos,state,tile)->!BREAK_DENIED.getOrDefault(world,Set.of()).contains(pos));
    }
    private static TileBuilder place(TestContext c){c.setBlockState(POS,Blocks.AIR);c.setBlockState(POS,BCBuildersBlocks.builder.getDefaultState().with(BCBuildersBlocks.builder.getFacingProperty(),Direction.NORTH));var tile=(TileBuilder)c.getBlockEntity(POS);tile.onPlacedBy(c.createMockCreativeServerPlayerInWorld(),new ItemStack(BCBuildersBlocks.builder));return tile;}
    private static Template template(int length,boolean filled){var s=new Template();s.size=new BlockPos(length,1,1);s.offset=BlockPos.ORIGIN;s.facing=Direction.NORTH;s.data=new BitSet();if(filled)s.data.set(0,length);s.computeKey();return s;}
    private static ItemStack snapshot(TileBuilder tile,Snapshot s){if(!GlobalSavedDataSnapshots.get(tile.getWorld()).addSnapshot(s))throw new IllegalStateException("Cannot save test plan");return BCBuildersItems.snapshot.getUsed(s.getType(),new Snapshot.Header(s.key,tile.getOwner().getId(),new Date(0),"Builder native test"));}
    private static void install(TileBuilder tile,Snapshot s){tile.invSnapshot.setStackInSlot(0,snapshot(tile,s));tile.tick();}
    private static Blueprint blueprint(TileBuilder tile,BlockState... states){var s=new Blueprint();s.size=new BlockPos(states.length,1,1);s.offset=BlockPos.ORIGIN;s.facing=Direction.NORTH;s.data=new int[states.length];var world=tile.getWorld();for(int i=0;i<states.length;i++){var p=tile.getPos().south().east(i);world.setBlockState(p,states[i]);s.palette.add(SchematicBlockManager.getSchematicBlock(new buildcraft.api.schematics.SchematicBlockContext(world,tile.getPos().south(),p,states[i],states[i].getBlock())));s.data[i]=i;world.setBlockState(p,Blocks.AIR.getDefaultState());}s.computeKey();return s;}
    private static void ticks(TileBuilder tile,int n){for(int i=0;i<n;i++)tile.tick();}
    private static IMjReceiver input(TileBuilder tile){return (IMjReceiver)MjAPI.CONNECTOR.find(tile.getWorld(),tile.getPos(),Direction.UP);}
    private static void power(TileBuilder tile,long mj){input(tile).receivePower(mj*MjAPI.MJ,false);}
    private static int count(TileBuilder tile,Item item){int n=0;for(int i=0;i<27;i++)if(tile.invResources.getStackInSlot(i).isOf(item))n+=tile.invResources.getStackInSlot(i).getCount();return n;}
    private static TileBuilder reload(TileBuilder tile){var w=tile.getWorld();var p=tile.getPos();var state=tile.getCachedState();var n=tile.createNbtWithIdentifyingData();w.removeBlockEntity(p);var next=(TileBuilder)BlockEntity.createFromNbt(p,state,n);w.addBlockEntity(next);next.onLoad();return next;}
    private static long paid(TileBuilder tile){long n=0;if(tile.getBuilder()!=null)for(var tag:tile.getBuilder().serializeNBT().getList("work",10))n+=((NbtCompound)tag).getLong("power");return n;}
    private static List<BlockPos> markers(TestContext c){var near=c.getAbsolutePos(POS.south());var points=List.of(near,near.east(3),near.east(3).south(2));
        // These elevated fixtures are outside the empty template's automatic cleanup volume.
        c.getWorld().setBlockState(near,Blocks.AIR.getDefaultState());
        for(int i=1;i<points.size();i++)for(var p:buildcraft.lib.misc.PositionUtil.getAllOnPath(points.get(i-1),points.get(i)))c.getWorld().setBlockState(p,Blocks.AIR.getDefaultState());
        for(var p:points){c.getWorld().setBlockState(p.down(),Blocks.STONE.getDefaultState());c.getWorld().setBlockState(p,BCCoreBlocks.markerPath.getDefaultState().with(BCCoreBlocks.markerPath.getFacingProperty(),Direction.UP));((TileMarkerPath)c.getWorld().getBlockEntity(p)).onLoad();}var cache=PathCache.INSTANCE.getSubCache(c.getWorld());c.assertTrue(cache.tryConnect(points.get(0),points.get(1)) && cache.tryConnect(points.get(1),points.get(2)),"Path markers did not connect");return points;}
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void oversizedSavedPathsCannotBecomeAnUnrelatedLocalBuild(TestContext c){
        var tile=place(c);install(tile,template(1,true));tile.invResources.setStackInSlot(0,new ItemStack(Items.STONE));power(tile,100);var saved=tile.createNbt();
        for(var points:List.of(new long[257],new long[]{tile.getPos().south().asLong(),tile.getPos().south(70000).asLong()})){
            saved.putLongArray("pathPositions",points);tile.readNbt(saved);tile.onLoad();ticks(tile,3);
            c.assertTrue(tile.getPathCount()==0 && !tile.getBox().isInitialized() && count(tile,Items.STONE)==1 && tile.getBattery().getStored()==100*MjAPI.MJ,"Invalid path silently built behind machine");
            tile=reload(tile);ticks(tile,3);c.assertTrue(tile.getPathCount()==0 && !tile.getBox().isInitialized() && count(tile,Items.STONE)==1 && tile.getBattery().getStored()==100*MjAPI.MJ,"Re-saving invalid path enabled an unrelated build");
        }c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void boundedMjSimulationNestedRollbackAndStaleReceiver(TestContext c){
        var tile=place(c);var r=input(tile);c.assertTrue(r.receivePower(20_000*MjAPI.MJ,true)==4_000*MjAPI.MJ && tile.getBattery().getStored()==0,"MJ simulation changed builder");
        try(var outer=Transaction.openOuter()){((TransactionalMjReceiver)r).receivePower(10_000*MjAPI.MJ,outer);try(var nested=outer.openNested()){r.receivePower(10_000*MjAPI.MJ,false);nested.commit();}c.assertTrue(tile.getBattery().getStored()==16_000*MjAPI.MJ,"Builder exceeded input capacity");}
        c.assertTrue(tile.getBattery().getStored()==0,"Nested MJ abort leaked");power(tile,12);var fresh=reload(tile);c.assertTrue(r.getPowerRequested()==0 && r.receivePower(MjAPI.MJ,false)==MjAPI.MJ && fresh.getBattery().getStored()==12*MjAPI.MJ,"Reload left stale MJ handle");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void cancelledHardnessCreditSurvivesReloadAboveCapacity(TestContext c){
        var tile=place(c);c.getWorld().setBlockState(tile.getPos().south(),Blocks.OBSIDIAN.getDefaultState());install(tile,template(1,false));power(tile,16_000);tile.tick();c.assertTrue(paid(tile)==256*MjAPI.MJ,"Hardness work did not retain paid credit");power(tile,16_000);tile.invSnapshot.setStackInSlot(0,ItemStack.EMPTY);
        c.assertTrue(tile.getBattery().getStored()==16_256*MjAPI.MJ,"Removing snapshot discarded credit");tile=reload(tile);ticks(tile,20);c.assertTrue(tile.getBattery().getStored()==16_256*MjAPI.MJ && input(tile).getPowerRequested()==0,"Refund dissipated after reload");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void paidBreakRestartsWithExactLootAndCost(TestContext c){
        var tile=place(c);c.getWorld().setBlockState(tile.getPos().south(),Blocks.OBSIDIAN.getDefaultState());install(tile,template(1,false));power(tile,2000);tile.tick();tile=reload(tile);c.assertTrue(paid(tile)==256*MjAPI.MJ,"Reload clipped paid hardness");ticks(tile,12);c.assertTrue(tile.isFinished() && count(tile,Items.OBSIDIAN)==1 && tile.getBattery().getStored()==368*MjAPI.MJ,"Reloaded builder lost/duplicated work or loot");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativeSnapshotAndMaterialAutomationHonorAbortAndRemoval(TestContext c){
        c.setBlockState(POS.south(),Blocks.AIR);
        var tile=place(c);var stack=snapshot(tile,template(1,true));var storage=ItemStorage.SIDED.find(c.getWorld(),tile.getPos(),Direction.UP);power(tile,100);
        try(var tx=Transaction.openOuter()){c.assertTrue(storage.insert(ItemVariant.of(stack),1,tx)==1 && storage.insert(ItemVariant.of(Items.STONE),2,tx)==2,"Automation rejected input");tile.tick();c.assertTrue(c.getWorld().isAir(tile.getPos().south()),"Builder ran inside a transfer transaction");}
        c.assertTrue(tile.invSnapshot.getStackInSlot(0).isEmpty() && count(tile,Items.STONE)==0,"Aborted inputs leaked");
        try(var tx=Transaction.openOuter()){storage.insert(ItemVariant.of(stack),1,tx);storage.insert(ItemVariant.of(Items.STONE),2,tx);tx.commit();}ticks(tile,4);
        c.assertTrue(tile.isFinished() && count(tile,Items.STONE)==1,"Automated input lost/duplicated materials");try(var tx=Transaction.openOuter()){c.assertTrue(storage.extract(ItemVariant.of(stack),1,tx)==1 && storage.extract(ItemVariant.of(Items.STONE),1,tx)==1,"Native output extraction missing");tx.commit();}
        c.setBlockState(POS,Blocks.AIR);try(var tx=Transaction.openOuter()){c.assertTrue(storage.insert(ItemVariant.of(Items.STONE),1,tx)==0,"Stale item handle accepted items");}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void blueprintPlacementProtectionRefundsWithOwnerAndResumes(TestContext c){
        var tile=place(c);install(tile,blueprint(tile,Blocks.STONE.getDefaultState()));tile.invResources.setStackInSlot(0,new ItemStack(Items.STONE));power(tile,100);var target=tile.getPos().south();USE_DENIED.computeIfAbsent(c.getWorld(),w->new HashSet<>()).add(target);
        try{ticks(tile,4);c.assertTrue(c.getWorld().isAir(target) && count(tile,Items.STONE)==1 && tile.getBattery().getStored()==100*MjAPI.MJ && tile.getOwner().getId().equals(observedOwner),"Denied blueprint placement changed world/resources or used wrong owner");}finally{USE_DENIED.get(c.getWorld()).remove(target);}
        ticks(tile,4);c.assertTrue(tile.isFinished() && count(tile,Items.STONE)==0 && tile.getBattery().getStored()==90*MjAPI.MJ,"Blueprint did not resume after protection release");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativeFluidBlueprintWaitsForCommittedTankInput(TestContext c){
        var tile=place(c);install(tile,blueprint(tile,Blocks.WATER.getDefaultState()));power(tile,100);ticks(tile,3);c.assertTrue(!tile.isFinished() && tile.getBattery().getStored()==100*MjAPI.MJ,"Missing fluid consumed energy");var storage=FluidStorage.SIDED.find(c.getWorld(),tile.getPos(),Direction.UP);
        try(var tx=Transaction.openOuter()){c.assertTrue(storage.insert(FluidVariant.of(Fluids.WATER),FluidConstants.BUCKET,tx)==FluidConstants.BUCKET,"Native tank input missing");tile.tick();c.assertTrue(c.getWorld().isAir(tile.getPos().south()),"Fluid placement ran inside transaction");}ticks(tile,2);c.assertTrue(c.getWorld().isAir(tile.getPos().south()),"Aborted fluid transfer placed water");
        try(var tx=Transaction.openOuter()){storage.insert(FluidVariant.of(Fluids.WATER),FluidConstants.BUCKET,tx);tx.commit();}ticks(tile,4);c.assertTrue(tile.isFinished() && c.getWorld().getFluidState(tile.getPos().south()).isOf(Fluids.WATER) && tile.getTankManager().getTanks().stream().allMatch(t->t.getStorage().getAmount()==0),"Fluid construction lost/duplicated water");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativePathAdoptionTraversesBendWithExactMaterials(TestContext c){
        var path=markers(c);var tile=place(c);c.assertTrue(tile.path.equals(path) && tile.getPathCount()==6,"Builder did not adopt full ordered path");for(var p:path)c.assertTrue(c.getWorld().isAir(p),"Adopted path marker remained");install(tile,template(1,true));tile.invResources.setStackInSlot(0,new ItemStack(Items.STONE,7));power(tile,1000);ticks(tile,20);
        c.assertTrue(tile.isFinished() && tile.getPathIndex()==5 && count(tile,Items.STONE)==1,"Path traversal lost steps/materials");List<BlockPos> expected=new ArrayList<>();expected.add(path.get(0));for(int i=1;i<path.size();i++)expected.addAll(buildcraft.lib.misc.PositionUtil.getAllOnPath(path.get(i-1),path.get(i)));long cost=0;for(var p:expected){c.assertTrue(c.getWorld().getBlockState(p).isOf(Blocks.STONE),"Path cell skipped");cost+=(long)(Math.sqrt(p.getSquaredDistance(tile.getPos()))*10*MjAPI.MJ);}c.assertTrue(tile.getBattery().getStored()==1000*MjAPI.MJ-cost,"Path energy cost wrong");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void partialPathReloadPreservesIndexAndPaidNextCell(TestContext c){
        var path=markers(c);var tile=place(c);install(tile,template(1,true));tile.invResources.setStackInSlot(0,new ItemStack(Items.STONE,6));power(tile,15);tile.tick();tile.tick();c.assertTrue(tile.getPathIndex()==1 && paid(tile)==5*MjAPI.MJ,"Expected partially funded second path cell");tile=reload(tile);c.assertTrue(tile.getPathIndex()==1 && paid(tile)==5*MjAPI.MJ && count(tile,Items.STONE)==5,"Path reload reset index/credit");power(tile,1000);ticks(tile,20);c.assertTrue(tile.isFinished() && count(tile,Items.STONE)==0,"Reloaded path duplicated first segment");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void protectedPathMarkersStayConnectedAndUnconsumed(TestContext c){
        var points=markers(c);BREAK_DENIED.computeIfAbsent(c.getWorld(),w->new HashSet<>()).add(points.get(1));try{var tile=place(c);c.assertTrue(tile.path.isEmpty(),"Protected path was adopted");for(var p:points)c.assertTrue(c.getWorld().getBlockEntity(p) instanceof TileMarkerPath marker && marker.getCurrentConnection()!=null,"Protected connection partially removed");}finally{BREAK_DENIED.get(c.getWorld()).remove(points.get(1));for(var p:points)c.getWorld().breakBlock(p,false);}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void reversedAndLoopedNativeConnectionsRetainOrder(TestContext c){
        var points=markers(c);var marker=(TileMarkerPath)c.getWorld().getBlockEntity(points.get(0));marker.reverseDirection();c.assertTrue(marker.getPath().equals(List.of(points.get(2),points.get(1),points.get(0))),"Native direction reversal lost order");var connection=marker.getCurrentConnection();c.assertTrue(connection.canAddMarker(points.get(0),points.get(2)) && connection.addMarker(points.get(0),points.get(2)),"Native loop closure failed");var expected=List.of(points.get(2),points.get(1),points.get(0),points.get(2));c.assertTrue(marker.getPath().equals(expected),"Loop closure not published");var tile=place(c);c.assertTrue(tile.path.equals(expected),"Builder reordered loop");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void connectingNewMarkerToExistingEndpointUsesCorrectDirection(TestContext c){
        var points=markers(c);var extra=points.get(0).west();c.getWorld().setBlockState(extra.down(),Blocks.STONE.getDefaultState());c.getWorld().setBlockState(extra,BCCoreBlocks.markerPath.getDefaultState().with(BCCoreBlocks.markerPath.getFacingProperty(),Direction.UP));((TileMarkerPath)c.getWorld().getBlockEntity(extra)).onLoad();var cache=PathCache.INSTANCE.getSubCache(c.getWorld());c.assertTrue(cache.canConnect(extra,points.get(0)) && cache.tryConnect(extra,points.get(0)),"Reverse connection request failed");c.assertTrue(cache.getConnection(extra).getMarkerPositions().equals(List.of(extra,points.get(0),points.get(1),points.get(2))),"Reverse connection inserted wrong point");for(var p:List.of(extra,points.get(0),points.get(1),points.get(2)))c.getWorld().breakBlock(p,false);c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativeMarkerPersistentStateWritesConnectionsAndReadsMalformedLists(TestContext c)throws Exception{
        var points=markers(c);var saved=c.getWorld().getPersistentStateManager().get(data->{var value=new PathSavedData();value.readFromNBT(data);return value;},PathSavedData.NAME);var nbt=saved.writeToNBT(new NbtCompound());c.assertTrue(nbt.getList("connections",9).size()>0,"Path connection missing from native state");c.getWorld().getPersistentStateManager().save();
        var file=c.getWorld().getServer().getSavePath(net.minecraft.util.WorldSavePath.ROOT).resolve("data").resolve(PathSavedData.NAME+".dat");c.assertTrue(java.nio.file.Files.isRegularFile(file),"Path state did not reach world data file");
        var malformed=new NbtCompound();malformed.putString("positions","bad");malformed.putInt("connections",123);new PathSavedData().readFromNBT(malformed);for(var p:points)c.getWorld().breakBlock(p,false);c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void workPacketsTrackPaidTemplateAndBlueprintPlacementAndClear(TestContext c)throws Exception{
        var tile=place(c);install(tile,blueprint(tile,Blocks.STONE.getDefaultState()));tile.invResources.setStackInSlot(0,new ItemStack(Items.STONE));power(tile,5);tile.tick();var packet=PacketBufferBC.write(b->tile.writePayload(TileBuilder.NET_WORK,b,TileBC_Neptune.NetSide.SERVER));try{tile.readPayload(TileBuilder.NET_WORK,packet,TileBC_Neptune.NetSide.CLIENT,null);}finally{packet.release();}var work=tile.blueprintBuilder.clientWork();c.assertTrue(work.size()==1 && work.get(0).progress()==.5f && work.get(0).item().isOf(Items.STONE),"Blueprint material work preview wrong");tile.invSnapshot.setStackInSlot(0,ItemStack.EMPTY);tile.tick();packet=PacketBufferBC.write(b->tile.writePayload(TileBuilder.NET_WORK,b,TileBC_Neptune.NetSide.SERVER));try{tile.readPayload(TileBuilder.NET_WORK,packet,TileBC_Neptune.NetSide.CLIENT,null);}finally{packet.release();}c.assertTrue(tile.blueprintBuilder.clientWork().isEmpty(),"Removed blueprint retained work animation");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void malformedStatusAndWorkRejectWithoutPartialState(TestContext c)throws Exception{
        var tile=place(c);install(tile,template(1,true));var before=tile.getBox().min();
        for(int bad=0;bad<2;bad++){var packet=PacketBufferBC.write(b->tile.writePayload(TileBC_Neptune.NET_RENDER_DATA,b,TileBC_Neptune.NetSide.SERVER));if(bad==0)packet.writerIndex(packet.writerIndex()-1);else packet.writeByte(1);boolean rejected=false;try{tile.readPayload(TileBC_Neptune.NET_RENDER_DATA,packet,TileBC_Neptune.NetSide.CLIENT,null);}catch(java.io.IOException|IndexOutOfBoundsException e){rejected=true;}finally{packet.release();}c.assertTrue(rejected && tile.snapshotType==EnumSnapshotType.TEMPLATE && tile.getBox().min().equals(before),"Invalid status partially applied");}
        var packet=PacketBufferBC.write(b->{b.writeVarInt(1);b.writeBlockPos(before);b.writeBoolean(false);b.writeFloat(Float.NaN);b.writeItemStack(new ItemStack(Items.STONE));});boolean rejected=false;try{tile.readPayload(TileBuilder.NET_WORK,packet,TileBC_Neptune.NetSide.CLIENT,null);}catch(java.io.IOException e){rejected=true;}finally{packet.release();}c.assertTrue(rejected && tile.templateBuilder.clientWork().isEmpty(),"NaN work accepted");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativeScreenQuickMoveAndGuiEnvelopeRemainConsistent(TestContext c)throws Exception{
        var tile=place(c);var player=c.createMockCreativeServerPlayerInWorld();player.refreshPositionAndAngles(tile.getPos().getX(),tile.getPos().getY(),tile.getPos().getZ()-2,0,0);var handler=new ContainerBuilder(player,7,tile);player.currentScreenHandler=handler;player.getInventory().setStack(9,snapshot(tile,template(1,true)));handler.quickMove(player,0);tile.tick();c.assertTrue(tile.getTemplateBuildingInfo()!=null,"Native snapshot quick move rejected plan");
        var expectedOwner=tile.getOwner().getId();var packet=PacketBufferBC.write(b->tile.writePayload(TileBC_Neptune.NET_GUI_DATA,b,TileBC_Neptune.NetSide.SERVER));try{tile.readPayload(TileBC_Neptune.NET_GUI_DATA,packet,TileBC_Neptune.NetSide.CLIENT,null);c.assertTrue(!packet.isReadable() && tile.getOwner().getId().equals(expectedOwner),"Builder GUI lost owner/delta envelope");}finally{packet.release();}
        var fresh=reload(tile);c.assertTrue(!handler.canUse(player) && fresh.getTemplateBuildingInfo()!=null,"Stale native screen remained usable");c.complete();
    }
}
