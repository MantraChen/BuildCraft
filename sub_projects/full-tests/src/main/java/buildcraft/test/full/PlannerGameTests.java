/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;

import java.util.*;
import buildcraft.api.enums.EnumSnapshotType;
import buildcraft.api.mj.MjAPI;
import buildcraft.builders.*;
import buildcraft.builders.addon.AddonFillerPlanner;
import buildcraft.builders.container.*;
import buildcraft.builders.tile.TileFiller;
import buildcraft.core.*;
import buildcraft.core.marker.volume.*;
import buildcraft.lib.net.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.*;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.*;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;

public final class PlannerGameTests implements FabricGameTest {
    private static final BlockPos BASE=new BlockPos(2,155,2);
    private static BlockPos base(TestContext c){return c.getAbsolutePos(BASE);}
    private static ServerPlayerEntity player(TestContext c,Vec3d target){
        var p=c.createMockCreativeServerPlayerInWorld();p.getInventory().clear();p.getAbilities().flying=true;aim(p,target,target.add(0,0,-3));return p;
    }
    private static void aim(ServerPlayerEntity p,Vec3d target,Vec3d eye){
        var look=target.subtract(eye).normalize();p.refreshPositionAndAngles(eye.x,eye.y-p.getEyeHeight(p.getPose()),eye.z,(float)Math.toDegrees(Math.atan2(-look.x,look.z)),(float)-Math.toDegrees(Math.asin(look.y)));p.setHeadYaw(p.getYaw());
    }
    private static VolumeBox volume(TestContext c){
        var data=WorldSavedDataVolumeBoxes.get(c.getWorld());var pos=base(c);data.volumeBoxes.removeIf(v->v.box.contains(pos));
        var volume=new VolumeBox(c.getWorld(),pos);data.volumeBoxes.add(volume);return volume;
    }
    private static AddonFillerPlanner addon(VolumeBox v){
        var a=new AddonFillerPlanner();a.volumeBox=v;v.addons.put(EnumAddonSlot.WEST_DOWN_NORTH,a);a.onAdded();return a;
    }
    private static Vec3d corner(VolumeBox v){return EnumAddonSlot.WEST_DOWN_NORTH.getBoundingBox(v).getCenter();}
    private static ActionResult useBlock(ServerPlayerEntity p,BlockPos pos){return p.interactionManager.interactBlock(p,p.getWorld(),p.getMainHandStack(),Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(pos).add(0,.5,0),Direction.UP,pos,false));}
    private static ActionResult connector(ServerPlayerEntity p){p.setStackInHand(Hand.MAIN_HAND,new ItemStack(BCCoreItems.markerConnector));return BCCoreItems.markerConnector.use(p.getWorld(),p,Hand.MAIN_HAND).getResult();}
    private static void settings(ContainerFillerPlanner h,buildcraft.builders.snapshot.pattern.Pattern pattern,boolean inverted)throws Exception{
        var b=PacketBufferBC.write(buffer->{var statement=new buildcraft.lib.statement.FullStatement<buildcraft.api.filler.IFillerPattern>(buildcraft.builders.filler.FillerType.INSTANCE,4,null);statement.set(pattern);statement.writeToBuffer(buffer);buffer.writeBoolean(inverted);});
        try{h.readMessage(buildcraft.lib.gui.ContainerBC_Neptune.NET_DATA,b,false,null);}finally{b.release();}
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativeVolumeAndPlannerToolsAreReusableAndRespectSlots(TestContext c){
        var pos=base(c);var data=WorldSavedDataVolumeBoxes.get(c.getWorld());data.volumeBoxes.removeIf(v->v.box.contains(pos));c.getWorld().setBlockState(pos.down(),Blocks.STONE.getDefaultState());var p=player(c,Vec3d.ofCenter(pos));p.setStackInHand(Hand.MAIN_HAND,new ItemStack(BCCoreItems.volumeBox));
        c.assertTrue(useBlock(p,pos.down()).isAccepted()&&p.getMainHandStack().getCount()==1,"Reusable volume tool failed");var v=data.getVolumeBoxAt(pos);c.assertTrue(v!=null&&v.box.size().equals(new BlockPos(1,1,1)),"Native volume creation missing");
        aim(p,corner(v),corner(v).add(-2,1,-2));p.setStackInHand(Hand.MAIN_HAND,new ItemStack(BCBuildersItems.addonFillerPlanner));
        c.assertTrue(BCBuildersItems.addonFillerPlanner.use(c.getWorld(),p,Hand.MAIN_HAND).getResult().isAccepted()&&p.getMainHandStack().getCount()==1&&v.addons.size()==1,"Reusable planner attachment failed");
        var other=EnumAddonSlot.EAST_UP_SOUTH.getBoundingBox(v).getCenter();aim(p,other,other.add(2,1,2));BCBuildersItems.addonFillerPlanner.use(c.getWorld(),p,Hand.MAIN_HAND);c.assertTrue(v.addons.size()==1,"Single planner duplicated into another slot");data.volumeBoxes.remove(v);c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=100)
    public void nativeWorldTickDragsVolumeAndConfirmUpdatesTemplate(TestContext c){
        var v=volume(c);var p=player(c,Vec3d.ofCenter(v.box.min()));var data=WorldSavedDataVolumeBoxes.get(c.getWorld());c.assertTrue(connector(p).isAccepted()&&v.isEditingBy(p),"Connector did not begin native resize");
        var target=v.box.min().add(3,2,4);var center=Vec3d.ofCenter(target);aim(p,center,center.add(0,0,-v.getDist()));
        c.waitAndRun(3,()->{
            c.assertTrue(v.box.max().equals(target),"Native END_WORLD_TICK did not update volume");var a=addon(v);aim(p,corner(v),corner(v).add(-2,1,-2));
            c.assertTrue(connector(p).isAccepted()&&!v.isEditing()&&!(p.currentScreenHandler instanceof ContainerFillerPlanner),"Clicking addon during drag did not confirm resize");a.patternStatement.set(BCBuildersStatements.PATTERN_FILL);a.updateBuildingInfo();
            c.assertTrue(a.buildingInfo.getSnapshot().data.cardinality()==60,"Confirmed plan size wrong");data.volumeBoxes.remove(v);c.complete();
        });
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void cancelResizeRestoresBoundsAndAnotherPlayerCannotTakeActiveEdit(TestContext c){
        var v=volume(c);var start=v.box.min();var p=player(c,Vec3d.ofCenter(start));var other=player(c,Vec3d.ofCenter(start));connector(p);
        c.assertTrue(!connector(other).isAccepted()&&v.isEditingBy(p),"Second player stole edit");var target=start.add(2,1,2);aim(p,Vec3d.ofCenter(target),Vec3d.ofCenter(target).add(0,0,-v.getDist()));WorldSavedDataVolumeBoxes.get(c.getWorld()).tick();
        var a=addon(v);aim(p,corner(v),corner(v).add(-2,1,-2));p.setSneaking(true);connector(p);
        c.assertTrue(!v.isEditing()&&v.box.min().equals(start)&&v.box.max().equals(start)&&v.addons.containsValue(a),"Cancel at addon lost old bounds or removed planner");WorldSavedDataVolumeBoxes.get(c.getWorld()).volumeBoxes.remove(v);c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void plannerScreenUsesExplicitIdentityAndSynchronizedSettings(TestContext c)throws Exception{
        var v=volume(c);v.box.extendToEncompass(v.box.min().add(2,2,2));var a=addon(v);var p=player(c,corner(v));aim(p,corner(v),corner(v).add(-2,1,-2));
        c.assertTrue(connector(p).isAccepted()&&p.currentScreenHandler instanceof ContainerFillerPlanner,"Connector failed to open native planner");var h=(ContainerFillerPlanner)p.currentScreenHandler;
        c.assertTrue(h.getType()==BCBuildersScreens.PLANNER&&h.addon==a,"Planner screen identity wrong");settings(h,BCBuildersStatements.PATTERN_FILL,false);
        c.assertTrue(a.patternStatement.get()==BCBuildersStatements.PATTERN_FILL&&a.buildingInfo.getSnapshot().data.cardinality()==27,"Native planner setting not applied");settings(h,BCBuildersStatements.PATTERN_FILL,true);
        c.assertTrue(a.inverted&&a.buildingInfo.getSnapshot().data.isEmpty(),"Inverted planner wrong");p.closeHandledScreen();WorldSavedDataVolumeBoxes.get(c.getWorld()).volumeBoxes.remove(v);c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void lockedOrRemovedPlannerCannotBeChangedByStaleScreen(TestContext c)throws Exception{
        var v=volume(c);var a=addon(v);var p=player(c,corner(v));BCBuildersScreens.openPlanner(p,a);var h=(ContainerFillerPlanner)p.currentScreenHandler;var machine=base(c).west(2);c.getWorld().setBlockState(machine,Blocks.STONE.getDefaultState());
        v.locks.add(new Lock(new Lock.Cause.CauseBlock(machine,Blocks.STONE),new Lock.Target.TargetAddon(a.getSlot()),new Lock.Target.TargetRemove(),new Lock.Target.TargetResize()));
        settings(h,BCBuildersStatements.PATTERN_FILL,true);c.assertTrue(a.patternStatement.get()==BCBuildersStatements.PATTERN_NONE&&!a.inverted,"Locked planner changed");
        WorldSavedDataVolumeBoxes.get(c.getWorld()).volumeBoxes.remove(v);settings(h,BCBuildersStatements.PATTERN_FILL,false);c.assertTrue(!h.canUse(p)&&a.patternStatement.get()==BCBuildersStatements.PATTERN_NONE,"Removed volume accepted stale screen");p.closeHandledScreen();c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void persistedAddonAndEditingStateReloadWithoutChangingIdentity(TestContext c){
        var v=volume(c);var a=addon(v);a.patternStatement.set(BCBuildersStatements.PATTERN_SPHERE);a.inverted=true;a.updateBuildingInfo();var p=player(c,Vec3d.ofCenter(v.box.min()));v.setPlayer(p);v.setHeldDistOldMinOldMax(v.box.min(),3,v.box.min(),v.box.max());v.pauseEditing();
        var tag=v.writeToNBT();var loaded=new VolumeBox(c.getWorld(),tag);c.assertTrue(loaded.id.equals(v.id)&&loaded.isPausedEditingBy(p)&&loaded.addons.get(a.getSlot()) instanceof AddonFillerPlanner b&&b.inverted&&b.patternStatement.get()==BCBuildersStatements.PATTERN_SPHERE,"Saved planner/edit state lost");
        loaded.resumeEditing();c.assertTrue(loaded.isEditingBy(p),"Paused edit did not resume");var existing=a;v.apply(loaded);c.assertTrue(v.addons.get(EnumAddonSlot.WEST_DOWN_NORTH)==existing,"State update detached open addon");WorldSavedDataVolumeBoxes.get(c.getWorld()).volumeBoxes.remove(v);c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void invalidBoundsAndLegacyEntriesStayOutOfRuntimeWithoutDataLoss(TestContext c){
        var v=volume(c);var tag=v.writeToNBT();tag.getCompound("box").putInt("xMax",Integer.MAX_VALUE);
        // Use an invalid UUID independent of the box's legacy encoding.
        tag.remove("id");var list=new NbtList();list.add(tag);var state=new NbtCompound();state.put("volumeBoxes",list);
        var saved=WorldSavedDataVolumeBoxes.fromNbt(state,c.getWorld());c.assertTrue(saved.volumeBoxes.isEmpty()&&saved.writeNbt(new NbtCompound()).getList("volumeBoxes",10).getCompound(0).equals(tag),"Unreadable old volume was accepted or erased");
        var huge=new buildcraft.lib.misc.data.Box(base(c),base(c).add(2000,0,2000));c.assertTrue(!VolumeBox.validBounds(c.getWorld(),huge),"Unbounded planner volume accepted");WorldSavedDataVolumeBoxes.get(c.getWorld()).volumeBoxes.remove(v);c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void volumePacketsBatchAndRoundTripWithinNativeLimits(TestContext c)throws Exception{
        List<VolumeBox> volumes=new ArrayList<>();for(int i=0;i<2000;i++){var v=new VolumeBox(c.getWorld(),base(c));addon(v);volumes.add(v);}
        var packets=MessageVolumeBoxes.batches(c.getWorld(),volumes);c.assertTrue(packets.size()>1,"Fixture did not exercise batched volume synchronization");
        for(var packet:packets){
            var first=new net.minecraft.network.PacketByteBuf(io.netty.buffer.Unpooled.buffer());var second=new net.minecraft.network.PacketByteBuf(io.netty.buffer.Unpooled.buffer());
            try{packet.write(first);c.assertTrue(first.readableBytes()<1024*1024,"Volume packet exceeds native client payload");byte[] before=new byte[first.readableBytes()];first.getBytes(0,before);var decoded=MessageVolumeBoxes.read(first);decoded.write(second);byte[] after=new byte[second.readableBytes()];second.readBytes(after);c.assertTrue(Arrays.equals(before,after),"Immutable volume packet round trip changed bytes");}finally{first.release();second.release();}
        }c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void malformedVolumePacketDoesNotPartiallyReplaceExistingAddon(TestContext c)throws Exception{
        var v=volume(c);var a=addon(v);var old=v.writeToNBT();var b=PacketBufferBC.write(v::toBytes);b.writerIndex(b.writerIndex()-1);boolean failed=false;
        try{v.fromBytes(b);}catch(Exception expected){failed=true;}finally{b.release();}
        c.assertTrue(failed&&v.writeToNBT().equals(old)&&v.addons.get(EnumAddonSlot.WEST_DOWN_NORTH)==a,"Truncated volume changed active state");WorldSavedDataVolumeBoxes.get(c.getWorld()).volumeBoxes.remove(v);c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void detachedAndForeignTransactionsCannotCreateOrAttachTools(TestContext c){
        var v=volume(c);var p=player(c,corner(v));aim(p,corner(v),corner(v).add(-2,1,-2));p.setStackInHand(Hand.MAIN_HAND,new ItemStack(BCBuildersItems.addonFillerPlanner));
        try(var tx=Transaction.openOuter()){c.assertTrue(!BCBuildersItems.addonFillerPlanner.use(c.getWorld(),p,Hand.MAIN_HAND).getResult().isAccepted()&&v.addons.isEmpty(),"Planner attached inside foreign transaction");}
        var spectator=new ServerPlayerEntity(c.getWorld().getServer(),c.getWorld(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"planner-spectator"));
        spectator.networkHandler=p.networkHandler;spectator.changeGameMode(net.minecraft.world.GameMode.SPECTATOR);spectator.refreshPositionAfterTeleport(p.getPos());
        c.assertTrue(spectator.isSpectator()&&!v.canEdit(spectator,base(c)),"Spectator can edit volume");WorldSavedDataVolumeBoxes.get(c.getWorld()).volumeBoxes.remove(v);c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=180)
    public void nativeFillerAdoptsConfirmedPlannerAndUnlocksAfterRemoval(TestContext c){
        var v=volume(c);var a=addon(v);v.box.extendToEncompass(v.box.min().east(2));for(var pos:BlockPos.iterate(v.box.min(),v.box.max()))c.getWorld().setBlockState(pos,Blocks.AIR.getDefaultState());a.patternStatement.set(BCBuildersStatements.PATTERN_FILL);a.updateBuildingInfo();var at=v.box.min().north();c.getWorld().setBlockState(at,Blocks.AIR.getDefaultState());c.getWorld().setBlockState(at,BCBuildersBlocks.filler.getDefaultState().with(buildcraft.lib.block.BlockBCBase_Neptune.PROP_FACING,Direction.NORTH));var t=(TileFiller)c.getWorld().getBlockEntity(at);t.onPlacedBy(player(c,Vec3d.ofCenter(at)),new ItemStack(BCBuildersBlocks.filler));t.invResources.setStackInSlot(0,new ItemStack(Items.STONE,3));t.getBattery().addPower(1000*MjAPI.MJ,false);
        c.assertTrue(t.addon==a&&v.isAddonLocked(a.getSlot()),"Filler did not adopt/lock planner");
        c.waitAndRun(60,()->{c.assertTrue(t.isFinished()&&t.invResources.getStackInSlot(0).isEmpty(),"Planner filler did not finish/pay");for(int i=0;i<3;i++)c.assertTrue(c.getWorld().getBlockState(v.box.min().east(i)).isOf(Blocks.STONE),"Planner filler shape mismatch");c.getWorld().setBlockState(at,Blocks.AIR.getDefaultState());
            c.waitAndRun(3,()->{c.assertTrue(v.locks.isEmpty(),"Native world tick left broken filler locked");WorldSavedDataVolumeBoxes.get(c.getWorld()).volumeBoxes.remove(v);c.complete();});
        });
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativePlannerAndVolumeToolRecipesAreAvailable(TestContext c){
        for(var id:List.of("buildcraftbuilders:filler_planner","buildcraftcore:volume_box","buildcraftcore:marker_connector"))
            c.assertTrue(c.getWorld().getRecipeManager().get(new Identifier(id)).isPresent(),"Missing survival tool recipe "+id);c.complete();
    }
}
