/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;

import java.util.*;
import java.util.function.Consumer;
import buildcraft.api.enums.EnumSnapshotType;
import buildcraft.api.mj.*;
import buildcraft.api.schematics.*;
import buildcraft.builders.*;
import buildcraft.builders.snapshot.*;
import buildcraft.builders.tile.*;
import buildcraft.lib.misc.NBTUtilBC;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.*;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.vehicle.*;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.*;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.math.*;

public final class RidingGroupGameTests implements FabricGameTest {
    private static final BlockPos POS=new BlockPos(2,170,2);
    private static final Map<ServerWorld,Consumer<Entity>> SPAWN=new WeakHashMap<>();
    private static final Set<ServerWorld> DENY_USE=new HashSet<>(),DENY_ATTACK=new HashSet<>();
    @Override public void invokeTestMethod(TestContext context,java.lang.reflect.Method method){
        // The fixture extends beyond the tiny empty structure. Wait for native entity sections,
        // not just synchronous block chunks, before manually advancing machines in one tick.
        var b=base(context);var world=context.getWorld();
        for(int x=(b.getX()-6)>>4;x<=(b.getX()+6)>>4;x++)for(int z=(b.getZ()-6)>>4;z<=(b.getZ()+6)>>4;z++)world.getChunk(x,z);
        boolean[] invoked={false};context.runAtEveryTick(()->{
            if(invoked[0])return;
            for(int x=(b.getX()-6)>>4;x<=(b.getX()+6)>>4;x++)for(int z=(b.getZ()-6)>>4;z<=(b.getZ()+6)>>4;z++)if(!world.isChunkLoaded(ChunkPos.toLong(x,z)))return;
            invoked[0]=true;FabricGameTest.super.invokeTestMethod(context,method);
        });
    }
    static {
        ServerEntityEvents.ENTITY_LOAD.register((entity,world)->{var action=SPAWN.get(world);if(action!=null)action.accept(entity);});
        UseBlockCallback.EVENT.register((p,w,h,hit)->DENY_USE.contains(w)&&p.getMainHandStack().isOf(Items.ARMOR_STAND)?ActionResult.FAIL:ActionResult.PASS);
        AttackEntityCallback.EVENT.register((p,w,h,e,hit)->DENY_ATTACK.contains(w)&&e instanceof ArmorStandEntity?ActionResult.FAIL:ActionResult.PASS);
    }
    private static BlockPos base(TestContext c){return c.getAbsolutePos(POS.south());}
    private static void clear(TestContext c){var b=base(c);for(var e:c.getWorld().getEntitiesByClass(Entity.class,new Box(b).expand(6),e->!(e instanceof net.minecraft.entity.player.PlayerEntity)))e.discard();for(var p:BlockPos.iterate(b.add(-3,-1,-3),b.add(5,6,5)))c.getWorld().setBlockState(p,Blocks.AIR.getDefaultState());}
    private static ItemStack helmet(){var s=new ItemStack(Items.DIAMOND_HELMET);s.setCustomName(Text.literal("Remake by FelixMantra"));return s;}
    private static BoatEntity source(TestContext c,int riders,boolean chest){
        var b=base(c);BoatEntity boat=chest?new ChestBoatEntity(c.getWorld(),b.getX()+1.5,b.getY()+.3,b.getZ()+1.5):new BoatEntity(c.getWorld(),b.getX()+1.5,b.getY()+.3,b.getZ()+1.5);
        boat.setNoGravity(true);boat.setYaw(30);boat.setVariant(BoatEntity.Type.BAMBOO);
        if(chest)((ChestBoatEntity)boat).setStack(0,new ItemStack(Items.EMERALD,5));
        for(int i=0;i<riders;i++){var rider=new ArmorStandEntity(c.getWorld(),0,0,0);rider.setNoGravity(true);rider.setCustomName(Text.literal("Rider "+i));if(i==0)rider.equipStack(EquipmentSlot.HEAD,helmet());rider.startRiding(boat,true);}
        for(var rider:boat.getPassengerList())boat.updatePassengerPosition(rider);return boat;
    }
    private static SchematicEntityGroup capture(TestContext c,Entity root){var s=SchematicEntityManager.getSchematicEntity(new SchematicEntityContext(c.getWorld(),base(c),root));c.assertTrue(s instanceof SchematicEntityGroup,"Whole group capture missing");return (SchematicEntityGroup)s;}
    private static Blueprint plan(ISchematicEntity...entities){var b=new Blueprint();b.size=new BlockPos(4,5,4);b.offset=BlockPos.ORIGIN;b.facing=Direction.NORTH;b.data=new int[80];b.palette.add(new SchematicBlockAir());b.entities.addAll(List.of(entities));b.computeKey();return b;}
    private static TileBuilder machine(TestContext c,Blueprint blueprint){c.setBlockState(POS,Blocks.AIR);c.setBlockState(POS,BCBuildersBlocks.builder.getDefaultState().with(BCBuildersBlocks.builder.getFacingProperty(),Direction.NORTH));var t=(TileBuilder)c.getBlockEntity(POS);t.onPlacedBy(c.createMockCreativeServerPlayerInWorld(),new ItemStack(BCBuildersBlocks.builder));c.assertTrue(GlobalSavedDataSnapshots.get(c.getWorld()).addSnapshot(blueprint),"Group plan save failed");t.invSnapshot.setStackInSlot(0,BCBuildersItems.snapshot.getUsed(EnumSnapshotType.BLUEPRINT,new Snapshot.Header(blueprint.key,t.getOwner().getId(),new Date(0),"Riding group")));t.tick();((IMjReceiver)t.getMjConnector(Direction.UP)).receivePower(16_000*MjAPI.MJ,false);return t;}
    private static void supply(TileBuilder t,SchematicEntityGroup s){var items=s.computeRequiredItems();for(int i=0;i<items.size();i++)t.invResources.setStackInSlot(i,items.get(i).copy());}
    private static void tick(TileBuilder t){for(int i=0;i<8;i++)t.tick();}
    private static List<Entity> existing(TestContext c){return c.getWorld().getEntitiesByClass(Entity.class,new Box(base(c)).expand(6),e->e instanceof BoatEntity||e instanceof ArmorStandEntity);}
    private static int items(TileBuilder t){int n=0;for(int i=0;i<27;i++)n+=t.invResources.getStackInSlot(i).getCount();return n;}
    private static void discard(Entity root){var group=SchematicEntityGroup.entities(root);for(int i=group.size()-1;i>=0;i--){group.get(i).stopRiding();group.get(i).discard();}}
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void capturesEveryPassengerAndExactCargoWithoutDuplicateRiders(TestContext c){
        var root=source(c,2,true);var s=capture(c,root);c.assertTrue(s.size()==3,"Group size wrong");var cost=s.computeRequiredItems();
        c.assertTrue(cost.stream().filter(i->i.isOf(Items.ARMOR_STAND)).mapToInt(ItemStack::getCount).sum()==2&&cost.stream().anyMatch(i->ItemStack.areEqual(i,helmet()))&&cost.stream().anyMatch(i->i.isOf(Items.BAMBOO_CHEST_RAFT))&&cost.stream().anyMatch(i->i.isOf(Items.EMERALD)&&i.getCount()==5),"Group material or cargo free");
        for(var child:root.getPassengerList())c.assertTrue(SchematicEntityManager.getSchematicEntity(new SchematicEntityContext(c.getWorld(),base(c),child))==null,"Rider captured twice");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void anyUnsupportedPassengerRejectsWholeGroup(TestContext c){
        for(var excluded:List.of(EntityType.COW.create(c.getWorld()),c.createMockCreativeServerPlayerInWorld())){var root=source(c,1,false);excluded.startRiding(root,true);c.assertTrue(SchematicEntityManager.getSchematicEntity(new SchematicEntityContext(c.getWorld(),base(c),root))==null,"Unsupported member partially captured");excluded.stopRiding();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void orderedNestedGroupRoundTripsAndRotatesWithFreshUuids(TestContext c)throws Exception{
        clear(c);var root=source(c,2,false);var child=new ArmorStandEntity(c.getWorld(),0,0,0);child.setNoGravity(true);child.startRiding(root.getFirstPassenger(),true);root.getFirstPassenger().updatePassengerPosition(child);
        var s=(SchematicEntityGroup)SchematicEntityManager.readFromNBT(SchematicEntityManager.writeToNBT(capture(c,root)));var old=SchematicEntityGroup.entities(root).stream().map(Entity::getUuid).toList();
        for(var rotation:BlockRotation.values()){var rotated=s.getRotated(rotation);var built=rotated.build(c.getWorld(),base(c));c.assertTrue(built!=null&&rotated.matches(built,base(c)),"Group rotation/seat mismatch "+rotation);var all=SchematicEntityGroup.entities(built);c.assertTrue(all.size()==4&&all.stream().noneMatch(e->old.contains(e.getUuid()))&&all.stream().map(Entity::getUuid).distinct().count()==4&&all.get(3).getVehicle()==all.get(1),"Fresh identity or nested relationship lost");discard(built);}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void missingOneNamedItemPreventsEveryGroupMember(TestContext c){
        clear(c);var s=capture(c,source(c,2,false));var t=machine(c,plan(s));supply(t,s);for(int i=0;i<27;i++)if(t.invResources.getStackInSlot(i).isOf(Items.DIAMOND_HELMET))t.invResources.setStackInSlot(i,new ItemStack(Items.DIAMOND_HELMET));int before=items(t);tick(t);c.assertTrue(existing(c).isEmpty()&&items(t)==before&&t.getPendingEntities()==3&&!t.isFinished(),"Missing member material created partial group");
        for(int i=0;i<27;i++)if(t.invResources.getStackInSlot(i).isOf(Items.DIAMOND_HELMET))t.invResources.setStackInSlot(i,helmet());tick(t);c.assertTrue(existing(c).size()==3&&items(t)==0&&t.isFinished(),"Paid whole group did not complete");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void passengerUseProtectionRefundsEntireGroup(TestContext c){
        clear(c);var s=capture(c,source(c,2,false));var t=machine(c,plan(s));supply(t,s);int before=items(t);DENY_USE.add(c.getWorld());try{tick(t);c.assertTrue(existing(c).isEmpty()&&items(t)==before&&!t.isFinished(),"Passenger permission bypass or material loss");}finally{DENY_USE.remove(c.getWorld());}tick(t);c.assertTrue(t.isFinished()&&existing(c).size()==3,"Protected group could not resume");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void secondSpawnFailureRemovesFirstEntityAndRefundsMaterials(TestContext c){
        clear(c);var s=capture(c,source(c,2,false));var t=machine(c,plan(s));supply(t,s);int before=items(t);int[] spawned={0};SPAWN.put(c.getWorld(),e->{if(e instanceof BoatEntity||e instanceof ArmorStandEntity){spawned[0]++;if(e instanceof ArmorStandEntity)e.discard();}});
        try{tick(t);c.assertTrue(spawned[0]>=2&&existing(c).isEmpty()&&items(t)==before&&!t.isFinished(),"Actual partial spawn was not rolled back");}finally{SPAWN.remove(c.getWorld());}tick(t);c.assertTrue(t.isFinished()&&existing(c).size()==3&&items(t)==0,"Failed group left stale riding/UUID state");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void changingPlanDuringSpawnRollsBackWholeGroup(TestContext c){
        clear(c);var s=capture(c,source(c,1,false));var t=machine(c,plan(s));supply(t,s);int before=items(t);SPAWN.put(c.getWorld(),e->{if(e instanceof ArmorStandEntity)t.invSnapshot.setStackInSlot(0,ItemStack.EMPTY);});
        try{tick(t);c.assertTrue(existing(c).isEmpty()&&items(t)==before,"Changed plan left entities or spent materials");}finally{SPAWN.remove(c.getWorld());}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void blockedPassengerHeadPreventsVehicleSpawn(TestContext c){
        clear(c);var s=capture(c,source(c,1,false));c.getWorld().setBlockState(base(c).add(1,1,1),Blocks.STONE.getDefaultState());c.assertTrue(s.build(c.getWorld(),base(c))==null&&existing(c).isEmpty(),"Blocked passenger was placed through solid block");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativeGroundSeatsAndRailSeatsRemainPlaceable(TestContext c){
        clear(c);var b=base(c);for(var p:BlockPos.iterate(b.add(-1,-1,-1),b.add(4,-1,4)))c.getWorld().setBlockState(p,Blocks.STONE.getDefaultState());
        for(var type:BoatEntity.Type.values()){
            var root=source(c,1,false);root.setVariant(type);root.setNoGravity(false);root.setPosition(b.getX()+1.5,b.getY(),b.getZ()+1.5);root.updatePassengerPosition(root.getFirstPassenger());
            var s=capture(c,root);var built=s.build(c.getWorld(),b);c.assertTrue(built!=null&&s.matches(built,b),"Ground-level native boat seat blocked: "+type);discard(built);
        }
        c.getWorld().setBlockState(b.add(1,0,1),Blocks.RAIL.getDefaultState());var cart=new ChestMinecartEntity(c.getWorld(),b.getX()+1.5,b.getY()+.0625,b.getZ()+1.5);var rider=new ArmorStandEntity(c.getWorld(),0,0,0);rider.startRiding(cart,true);cart.updatePassengerPosition(rider);var s=capture(c,cart);var built=s.build(c.getWorld(),b);c.assertTrue(built!=null&&s.matches(built,b),"Native rail riding group blocked");discard(built);c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void existingGroupAndBuilderReloadKeepEveryUuidWithoutMaterials(TestContext c){
        clear(c);var root=source(c,2,true);var s=capture(c,root);var built=s.build(c.getWorld(),base(c));c.assertTrue(built!=null,"Existing group setup failed");var ids=existing(c).stream().map(Entity::getUuid).collect(java.util.stream.Collectors.toSet());var t=machine(c,plan(s));tick(t);c.assertTrue(t.isFinished()&&items(t)==0,"Matching group required materials");
        var w=t.getWorld();var p=t.getPos();var state=t.getCachedState();var n=t.createNbtWithIdentifyingData();w.removeBlockEntity(p);var fresh=(TileBuilder)BlockEntity.createFromNbt(p,state,n);w.addBlockEntity(fresh);fresh.onLoad();tick(fresh);c.assertTrue(fresh.isFinished()&&ids.equals(existing(c).stream().map(Entity::getUuid).collect(java.util.stream.Collectors.toSet())),"Reload duplicated or removed group members");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void protectedPassengerPreventsAnyExcavationAndAllCostsAreRecovered(TestContext c){
        clear(c);var s=capture(c,source(c,2,true));var built=s.build(c.getWorld(),base(c));c.assertTrue(built!=null,"Excavation setup failed");var t=machine(c,plan());DENY_ATTACK.add(c.getWorld());try{tick(t);c.assertTrue(existing(c).size()==3&&items(t)==0&&!t.isFinished(),"Protected group partially removed");}finally{DENY_ATTACK.remove(c.getWorld());}tick(t);c.assertTrue(existing(c).isEmpty()&&t.isFinished()&&items(t)==s.computeRequiredItems().stream().mapToInt(ItemStack::getCount).sum(),"Whole group recovery lost or duplicated cargo");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void standaloneDoesNotMatchVehicleWithUnpaidPassengers(TestContext c){
        var root=source(c,0,false);var single=new SchematicEntityDefault();single.init(new SchematicEntityContext(c.getWorld(),base(c),root));var rider=new ArmorStandEntity(c.getWorld(),0,0,0);rider.startRiding(root,true);c.assertTrue(!single.matches(root,base(c)),"Standalone schematic claimed unpaid riding group");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void malformedTreesAndOutsidePassengerAreRejected(TestContext c)throws Exception{
        var s=capture(c,source(c,2,false));
        var saved=s.serializeNBT();var changed=s.serializeNBT();changed.getIntArray("parents")[1]=2;
        c.assertTrue(saved.equals(s.serializeNBT()),"Editing exported NBT changed the live riding tree");
        var loaded=new SchematicEntityGroup();loaded.deserializeNBT(saved);saved.getIntArray("parents")[2]=2;
        c.assertTrue(loaded.serializeNBT().getIntArray("parents")[2]==0,"Input NBT still aliases the loaded riding tree");
        for(int mode=0;mode<6;mode++){var n=s.serializeNBT();if(mode==0)n.putIntArray("parents",new int[]{-1,2,1});if(mode==1)n.putIntArray("parents",new int[]{0,0,0});if(mode==2)n.putIntArray("parents",new int[]{-1,0});if(mode==3)n.putString("members","bad");if(mode==4)n.getList("members",10).getCompound(1).getCompound("entityNbt").putString("id","minecraft:cow");if(mode==5)n.getList("members",10).getCompound(1).put("pos",NBTUtilBC.writeVec3d(new Vec3d(Double.NaN,0,0)));boolean rejected=false;try{new SchematicEntityGroup().deserializeNBT(n);}catch(buildcraft.api.core.InvalidInputDataException expected){rejected=true;}c.assertTrue(rejected,"Malformed riding tree accepted "+mode);}
        var n=plan(s).serializeNBT();n.getList("entities",10).getCompound(0).getCompound("data").getList("members",10).getCompound(1).put("pos",NBTUtilBC.writeVec3d(new Vec3d(10,0,0)));boolean rejected=false;try{new Blueprint().deserializeNBT(n);}catch(buildcraft.api.core.InvalidInputDataException expected){rejected=true;}c.assertTrue(rejected,"Outside passenger accepted via inside root");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void blueprintLimitCountsEveryMemberAndDepthIsBounded(TestContext c)throws Exception{
        var s=capture(c,source(c,2,false));var n=plan(s).serializeNBT();var list=n.getList("entities",10);var first=list.getCompound(0);for(int i=1;i<1366;i++)list.add(first.copy());boolean rejected=false;try{new Blueprint().deserializeNBT(n);}catch(buildcraft.api.core.InvalidInputDataException expected){rejected=true;}c.assertTrue(rejected,"4098 members bypassed blueprint limit");
        var group=s.serializeNBT();var members=new NbtList();var parents=new int[66];for(int i=0;i<66;i++){members.add(group.getList("members",10).getCompound(1).copy());parents[i]=i-1;}group.put("members",members);group.putIntArray("parents",parents);rejected=false;try{new SchematicEntityGroup().deserializeNBT(group);}catch(buildcraft.api.core.InvalidInputDataException expected){rejected=true;}c.assertTrue(rejected,"Unbounded riding depth accepted");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void architectCapturesOneWholeGroupAndExcludesBoundaryCrossingGroup(TestContext c){
        clear(c);c.setBlockState(POS,BCBuildersBlocks.architect.getDefaultState().with(BCBuildersBlocks.architect.getFacingProperty(),Direction.NORTH));var t=(TileArchitectTable)c.getBlockEntity(POS);t.onPlacedBy(c.createMockCreativeServerPlayerInWorld(),new ItemStack(BCBuildersBlocks.architect));var n=t.createNbt();var box=new buildcraft.lib.misc.data.Box();box.setMin(base(c));box.setMax(base(c).add(3,4,3));n.put("box",box.writeToNBT());n.putBoolean("isValid",true);t.readNbt(n);
        var root=source(c,2,false);var group=SchematicEntityGroup.entities(root);group.forEach(c.getWorld()::spawnEntity);
        for(int pass=0;pass<2;pass++){if(pass==1)group.get(1).setPosition(Vec3d.of(base(c).east(8)));t.invSnapshotOut.setStackInSlot(0,ItemStack.EMPTY);t.invSnapshotIn.setStackInSlot(0,BCBuildersItems.snapshot.getClean(EnumSnapshotType.BLUEPRINT));tickArchitect(t);var h=BCBuildersItems.snapshot.getHeader(t.invSnapshotOut.getStackInSlot(0));c.assertTrue(h!=null,"Architect group output missing");var b=(Blueprint)GlobalSavedDataSnapshots.get(c.getWorld()).getSnapshot(h.key);c.assertTrue(b.entities.size()==(pass==0?1:0)&&(pass!=0||SchematicEntityManager.count(b.entities.get(0))==3),"Architect split group or duplicated passengers");}discard(root);c.complete();
    }
    private static void tickArchitect(TileArchitectTable t){for(int i=0;i<12;i++)t.tick();}
}
