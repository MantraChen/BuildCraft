/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;

import java.util.*;
import buildcraft.api.enums.EnumSnapshotType;
import buildcraft.api.mj.*;
import buildcraft.api.schematics.*;
import buildcraft.builders.*;
import buildcraft.builders.snapshot.*;
import buildcraft.builders.tile.*;
import buildcraft.lib.net.PacketBufferBC;
import buildcraft.lib.tile.TileBC_Neptune;
import net.fabricmc.fabric.api.event.player.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.*;
import net.minecraft.entity.decoration.*;
import net.minecraft.entity.decoration.painting.*;
import net.minecraft.entity.vehicle.*;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.registry.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.*;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.math.*;

public final class EntityBlueprintGameTests implements FabricGameTest {
    private static final BlockPos POS=new BlockPos(2,140,2);
    private static final Map<ServerWorld,Set<BlockPos>> USE_DENIED=new WeakHashMap<>(),ATTACK_DENIED=new WeakHashMap<>();
    private static UUID observedOwner;
    static {
        UseBlockCallback.EVENT.register((p,w,h,hit)->{if(USE_DENIED.getOrDefault(w,Set.of()).contains(hit.getBlockPos())){observedOwner=p.getUuid();return ActionResult.FAIL;}return ActionResult.PASS;});
        AttackEntityCallback.EVENT.register((p,w,h,e,hit)->{if(ATTACK_DENIED.getOrDefault(w,Set.of()).contains(e.getBlockPos())){observedOwner=p.getUuid();return ActionResult.FAIL;}return ActionResult.PASS;});
    }
    private static BlockPos base(TestContext c){return c.getAbsolutePos(POS.south());}
    private static void clear(TestContext c,BlockPos origin){
        c.getWorld().getEntitiesByClass(Entity.class,new Box(origin).expand(5),e->!(e instanceof net.minecraft.entity.player.PlayerEntity)).forEach(Entity::discard);
        for(var p:BlockPos.iterate(origin.add(-3,-1,-3),origin.add(4,4,4)))c.getWorld().setBlockState(p,Blocks.AIR.getDefaultState());
    }
    private static ArmorStandEntity stand(TestContext c,BlockPos base){var e=new ArmorStandEntity(c.getWorld(),base.getX()+.5,base.getY(),base.getZ()+.5);e.setNoGravity(true);e.setYaw(30);return e;}
    private static SchematicEntityDefault capture(TestContext c,BlockPos origin,Entity entity){var s=SchematicEntityManager.getSchematicEntity(new SchematicEntityContext(c.getWorld(),origin,entity));c.assertTrue(s instanceof SchematicEntityDefault,"Entity capture unavailable: "+entity.getType());return (SchematicEntityDefault)s;}
    private static Blueprint plan(ISchematicEntity...entities){var b=new Blueprint();b.size=new BlockPos(3,3,3);b.offset=BlockPos.ORIGIN;b.facing=Direction.NORTH;b.data=new int[27];b.palette.add(new SchematicBlockAir());b.entities.addAll(List.of(entities));b.computeKey();return b;}
    private static TileBuilder machine(TestContext c,Blueprint blueprint){c.setBlockState(POS,Blocks.AIR);c.setBlockState(POS,BCBuildersBlocks.builder.getDefaultState().with(BCBuildersBlocks.builder.getFacingProperty(),Direction.NORTH));var t=(TileBuilder)c.getBlockEntity(POS);t.onPlacedBy(c.createMockCreativeServerPlayerInWorld(),new ItemStack(BCBuildersBlocks.builder));c.assertTrue(GlobalSavedDataSnapshots.get(c.getWorld()).addSnapshot(blueprint),"Entity plan save failed");t.invSnapshot.setStackInSlot(0,BCBuildersItems.snapshot.getUsed(EnumSnapshotType.BLUEPRINT,new Snapshot.Header(blueprint.key,t.getOwner().getId(),new Date(0),"Entity test")));t.tick();return t;}
    private static void charge(TileBuilder t){((IMjReceiver)t.getMjConnector(Direction.UP)).receivePower(16_000*MjAPI.MJ,false);}
    private static void tick(TileBuilder t,int n){for(int i=0;i<n;i++)t.tick();}
    private static TileBuilder reload(TileBuilder t){var w=t.getWorld();var p=t.getPos();var s=t.getCachedState();var n=t.createNbtWithIdentifyingData();w.removeBlockEntity(p);var fresh=(TileBuilder)BlockEntity.createFromNbt(p,s,n);w.addBlockEntity(fresh);fresh.onLoad();return fresh;}
    private static int count(TileBuilder t,Item item){int n=0;for(int i=0;i<27;i++)if(t.invResources.getStackInSlot(i).isOf(item))n+=t.invResources.getStackInSlot(i).getCount();return n;}
    private static List<ArmorStandEntity> stands(TestContext c){return c.getWorld().getEntitiesByClass(ArmorStandEntity.class,new Box(base(c)).expand(3),e->true);}
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativeEntityWhitelistExcludesPlayersDropsMobsAndRiders(TestContext c){
        var b=base(c);var entities=List.of(stand(c,b),new ItemFrameEntity(c.getWorld(),b,Direction.NORTH),new GlowItemFrameEntity(c.getWorld(),b,Direction.NORTH),new BoatEntity(c.getWorld(),b.getX()+.5,b.getY(),b.getZ()+.5),new EndCrystalEntity(c.getWorld(),b.getX()+.5,b.getY(),b.getZ()+.5));
        for(var entity:entities)c.assertTrue(SchematicEntityDefault.predicate(new SchematicEntityContext(c.getWorld(),b,entity)),"Native rule missed "+entity.getType());
        for(var entity:List.of(c.createMockCreativeServerPlayerInWorld(),new ItemEntity(c.getWorld(),b.getX(),b.getY(),b.getZ(),new ItemStack(Items.DIAMOND)),EntityType.COW.create(c.getWorld())))c.assertTrue(!SchematicEntityDefault.predicate(new SchematicEntityContext(c.getWorld(),b,entity)),"Unsupported entity captured");
        var boat=(BoatEntity)entities.get(3);var rider=stand(c,b);rider.startRiding(boat,true);c.assertTrue(!SchematicEntityDefault.predicate(new SchematicEntityContext(c.getWorld(),b,boat))&&!SchematicEntityDefault.predicate(new SchematicEntityContext(c.getWorld(),b,rider)),"Vehicle/passenger graph captured without costs");rider.stopRiding();c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void armorStandRoundTripAllRotationsKeepsEquipmentAndFreshIdentity(TestContext c)throws Exception{
        var b=base(c);clear(c,b);var original=stand(c,b);var helmet=new ItemStack(Items.DIAMOND_HELMET);helmet.setCustomName(Text.literal("Remake by FelixMantra"));original.equipStack(EquipmentSlot.HEAD,helmet);original.setHeadRotation(new EulerAngle(10,20,30));var schematic=capture(c,b,original);var saved=SchematicEntityManager.writeToNBT(schematic);var restored=(SchematicEntityDefault)SchematicEntityManager.readFromNBT(saved);
        c.assertTrue(restored.computeRequiredItems().stream().anyMatch(s->ItemStack.areEqual(s,helmet)),"Equipment NBT missing from cost");
        for(var rotation:BlockRotation.values()){var rotated=restored.getRotated(rotation);var expected=rotated.getPos().add(Vec3d.of(b));var built=(ArmorStandEntity)rotated.build(c.getWorld(),b);c.assertTrue(built!=null && built.getPos().squaredDistanceTo(expected)<1e-8 && !built.getUuid().equals(original.getUuid()) && ItemStack.areEqual(built.getEquippedStack(EquipmentSlot.HEAD),helmet) && built.getHeadRotation().equals(original.getHeadRotation()) && rotated.matches(built,b),"Rotated armor stand failed "+rotation);built.discard();}
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void framesAttachOnAllSixFacesAfterAllRotations(TestContext c)throws Exception{
        var b=base(c);clear(c,b);var held=new ItemStack(Items.DIAMOND);held.setCustomName(Text.literal("Exact frame item"));
        for(var facing:Direction.values())for(var rotation:BlockRotation.values()){
            var original=new ItemFrameEntity(c.getWorld(),b,facing);original.setHeldItemStack(held);original.setRotation(3);var schematic=capture(c,b,original).getRotated(rotation);var side=rotation.rotate(facing);var support=b.offset(side.getOpposite());c.getWorld().setBlockState(support,Blocks.STONE.getDefaultState());var built=(ItemFrameEntity)schematic.build(c.getWorld(),b);
            c.assertTrue(built!=null && built.getDecorationBlockPos().equals(b) && ItemStack.areEqual(built.getHeldItemStack(),held) && built.getRotation()==3 && schematic.matches(built,b),"Rotated attachment failed: "+facing+" / "+rotation);built.discard();c.getWorld().setBlockState(support,Blocks.AIR.getDefaultState());
        }c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void paintingsRetainVariantAndRequireNativeSupport(TestContext c){
        var b=base(c);clear(c,b);var variant=Registries.PAINTING_VARIANT.entryOf(PaintingVariants.KEBAB);var original=new PaintingEntity(c.getWorld(),b,Direction.NORTH,variant);var capture=capture(c,b,original);
        c.assertTrue(capture.build(c.getWorld(),b)==null,"Unsupported painting spawned");
        for(var rotation:BlockRotation.values()){var side=rotation.rotate(Direction.NORTH);c.getWorld().setBlockState(b.offset(side.getOpposite()),Blocks.STONE.getDefaultState());var schematic=capture.getRotated(rotation);var painting=(PaintingEntity)schematic.build(c.getWorld(),b);c.assertTrue(painting!=null && painting.getVariant().equals(variant) && schematic.matches(painting,b),"Painting variant/facing lost");painting.discard();c.getWorld().setBlockState(b.offset(side.getOpposite()),Blocks.AIR.getDefaultState());}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativeBoatAndChestBoatTypesChargeMatchingItemsAndCargo(TestContext c){
        var b=base(c);for(var type:BoatEntity.Type.values())for(boolean chest:List.of(false,true)){BoatEntity boat=chest?new ChestBoatEntity(c.getWorld(),b.getX()+.5,b.getY(),b.getZ()+.5):new BoatEntity(c.getWorld(),b.getX()+.5,b.getY(),b.getZ()+.5);boat.setVariant(type);if(chest)((ChestBoatEntity)boat).setStack(0,new ItemStack(Items.EMERALD,5));var schematic=capture(c,b,boat);var cost=schematic.computeRequiredItems();c.assertTrue(cost.stream().anyMatch(s->s.isOf(boat.asItem())&&s.getCount()==1),"Wrong native boat cost "+type+" chest="+chest);c.assertTrue(!chest || cost.stream().anyMatch(s->s.isOf(Items.EMERALD)&&s.getCount()==5),"Chest boat cargo free");}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void minecartCargoAndCrystalPlacementUseNativeRules(TestContext c){
        var b=base(c);clear(c,b);var cart=new ChestMinecartEntity(c.getWorld(),b.getX()+.5,b.getY(),b.getZ()+.5);cart.setStack(0,new ItemStack(Items.DIAMOND,4));var schematic=capture(c,b,cart);c.assertTrue(schematic.computeRequiredItems().stream().anyMatch(s->s.isOf(Items.DIAMOND)&&s.getCount()==4),"Minecart cargo uncharged");c.assertTrue(schematic.build(c.getWorld(),b)==null,"Minecart built without rails");c.getWorld().setBlockState(b.down(),Blocks.STONE.getDefaultState());c.getWorld().setBlockState(b,Blocks.RAIL.getDefaultState());var built=(ChestMinecartEntity)schematic.build(c.getWorld(),b);c.assertTrue(built!=null && built.getStack(0).getCount()==4,"Native minecart contents missing");built.discard();c.getWorld().setBlockState(b,Blocks.AIR.getDefaultState());var crystal=capture(c,b,new EndCrystalEntity(c.getWorld(),b.getX()+.5,b.getY(),b.getZ()+.5));c.assertTrue(crystal.computeRequiredItems().get(0).isOf(Items.END_CRYSTAL) && crystal.build(c.getWorld(),b)==null,"Crystal native rule wrong");c.getWorld().setBlockState(b.down(),Blocks.OBSIDIAN.getDefaultState());var end=crystal.build(c.getWorld(),b);c.assertTrue(end!=null,"Supported crystal rejected");end.discard();c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void missingExactEquipmentRollsBackAndFullBatteryWaits(TestContext c){
        var b=base(c);clear(c,b);var original=stand(c,b);var helmet=new ItemStack(Items.DIAMOND_HELMET);helmet.setCustomName(Text.literal("Required"));original.equipStack(EquipmentSlot.HEAD,helmet);var t=machine(c,plan(capture(c,b,original)));t.invResources.setStackInSlot(0,new ItemStack(Items.ARMOR_STAND));t.invResources.setStackInSlot(1,new ItemStack(Items.DIAMOND_HELMET));charge(t);tick(t,5);c.assertTrue(stands(c).isEmpty() && count(t,Items.ARMOR_STAND)==1 && !t.isFinished() && t.getPendingEntities()==1,"Short exact equipment spawned or consumed");t.invResources.setStackInSlot(1,helmet.copy());t.getBattery().extractPower(1,1);tick(t,3);c.assertTrue(stands(c).isEmpty(),"Entity ignored full-battery requirement");charge(t);tick(t,3);c.assertTrue(t.isFinished() && stands(c).size()==1 && count(t,Items.ARMOR_STAND)==0 && count(t,Items.DIAMOND_HELMET)==0 && t.getBattery().getStored()==16_000*MjAPI.MJ,"Entity did not atomically pay native equipment");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void alreadyBuiltEntitiesAndReloadNeverDuplicateOrConsumeAgain(TestContext c){
        var b=base(c);clear(c,b);var original=stand(c,b);var s=capture(c,b,original);c.getWorld().spawnEntity(original);var t=machine(c,plan(s));tick(t,3);c.assertTrue(t.isFinished() && stands(c).size()==1,"Existing matching entity not recognized");var n=t.createNbt();n.putBoolean("finished",false);t.readNbt(n);t.onLoad();charge(t);t.invResources.setStackInSlot(0,new ItemStack(Items.ARMOR_STAND,2));tick(t,4);t=reload(t);tick(t,3);c.assertTrue(stands(c).size()==1 && count(t,Items.ARMOR_STAND)==2 && t.getPendingEntities()==0 && t.getRemainingMaterials().isEmpty(),"Reload duplicated/repaid entity or retained finished materials");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void blockedEntityPlacementUsesOwnerAndReturnsAllMaterials(TestContext c){
        var b=base(c);clear(c,b);var t=machine(c,plan(capture(c,b,stand(c,b))));t.invResources.setStackInSlot(0,new ItemStack(Items.ARMOR_STAND));charge(t);USE_DENIED.computeIfAbsent(c.getWorld(),w->new HashSet<>()).add(b);try{tick(t,4);c.assertTrue(stands(c).isEmpty() && count(t,Items.ARMOR_STAND)==1 && t.getOwner().getId().equals(observedOwner),"Native owner protection failed");}finally{USE_DENIED.get(c.getWorld()).remove(b);}tick(t,4);c.assertTrue(t.isFinished()&&stands(c).size()==1,"Unblocked entity did not resume");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void protectedExtrasStayThenReturnMaterialsForReplacement(TestContext c){
        var b=base(c);clear(c,b);var original=stand(c,b);original.setCustomName(Text.literal("wanted"));var desired=capture(c,b,original);var extra=stand(c,b);extra.setCustomName(Text.literal("different"));c.getWorld().spawnEntity(extra);var t=machine(c,plan(desired));charge(t);ATTACK_DENIED.computeIfAbsent(c.getWorld(),w->new HashSet<>()).add(b);
        try{tick(t,4);c.assertTrue(!extra.isRemoved() && !t.isFinished() && t.getOwner().getId().equals(observedOwner),"Protected extra removed");}finally{ATTACK_DENIED.get(c.getWorld()).remove(b);}tick(t,5);c.assertTrue(extra.isRemoved() && t.isFinished() && stands(c).size()==1 && stands(c).get(0).getCustomName().getString().equals("wanted") && count(t,Items.ARMOR_STAND)==0,"Replacement did not recover/reuse exact entity material");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void excavationDisabledPreservesUnwantedEntities(TestContext c)throws Exception{
        var b=base(c);clear(c,b);var extra=stand(c,b);c.getWorld().spawnEntity(extra);var t=machine(c,plan());var packet=PacketBufferBC.write(out->out.writeBoolean(false));try{t.readPayload(TileBuilder.NET_CAN_EXCAVATE,packet,TileBC_Neptune.NetSide.SERVER,null);}finally{packet.release();}charge(t);tick(t,4);c.assertTrue(!extra.isRemoved() && t.isFinished(),"Dig-off removed extra entity or stalled");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void malformedEntityDataCannotSilentlyDropOrMoveOutsidePlan(TestContext c)throws Exception{
        var b=base(c);var s=capture(c,b,stand(c,b));var invalid=s.serializeNBT();invalid.getCompound("entityNbt").putString("id","minecraft:cow");boolean rejected=false;try{new SchematicEntityDefault().deserializeNBT(invalid);}catch(buildcraft.api.core.InvalidInputDataException expected){rejected=true;}c.assertTrue(rejected,"Disallowed entity NBT accepted");
        for(int kind=0;kind<3;kind++){var blueprint=plan(s).serializeNBT();if(kind==0)blueprint.putString("entities","bad");else if(kind==1)blueprint.getList("entities",10).getCompound(0).getCompound("data").put("pos",buildcraft.lib.misc.NBTUtilBC.writeVec3d(new Vec3d(Double.NaN,0,0)));else blueprint.getList("entities",10).getCompound(0).getCompound("data").put("pos",buildcraft.lib.misc.NBTUtilBC.writeVec3d(new Vec3d(4,0,0)));rejected=false;try{new Blueprint().deserializeNBT(blueprint);}catch(buildcraft.api.core.InvalidInputDataException expected){rejected=true;}c.assertTrue(rejected,"Malformed entity plan accepted "+kind);}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void architectCapturesNativeEntitiesAndGrantsActualAdvancement(TestContext c){
        var b=base(c);clear(c,b);c.setBlockState(POS,BCBuildersBlocks.architect.getDefaultState().with(BCBuildersBlocks.architect.getFacingProperty(),Direction.NORTH));var t=(TileArchitectTable)c.getBlockEntity(POS);var player=c.createMockCreativeServerPlayerInWorld();t.onPlacedBy(player,new ItemStack(BCBuildersBlocks.architect));var n=t.createNbt();var box=new buildcraft.lib.misc.data.Box();box.setMin(b);box.setMax(b.add(2,2,2));n.put("box",box.writeToNBT());n.putBoolean("isValid",true);t.readNbt(n);var stand=stand(c,b);c.getWorld().spawnEntity(stand);c.getWorld().spawnEntity(new ItemEntity(c.getWorld(),b.getX()+1,b.getY(),b.getZ()+1,new ItemStack(Items.DIAMOND)));t.invSnapshotIn.setStackInSlot(0,BCBuildersItems.snapshot.getClean(EnumSnapshotType.BLUEPRINT));for(int i=0;i<8;i++)t.tick();var header=BCBuildersItems.snapshot.getHeader(t.invSnapshotOut.getStackInSlot(0));c.assertTrue(header!=null,"Entity architect produced no blueprint");var blueprint=(Blueprint)GlobalSavedDataSnapshots.get(c.getWorld()).getSnapshot(header.key);c.assertTrue(blueprint.entities.size()==1 && player.getAdvancementTracker().getProgress(c.getWorld().getServer().getAdvancementLoader().get(new Identifier("buildcraftbuilders:architect"))).isDone(),"Architect lost entities or real advancement");stand.discard();c.complete();
    }
}