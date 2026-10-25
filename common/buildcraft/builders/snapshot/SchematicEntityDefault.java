/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0
 * Remake by FelixMantra
 */
package buildcraft.builders.snapshot;

import java.util.*;
import net.minecraft.block.Blocks;
import net.minecraft.entity.*;
import net.minecraft.entity.decoration.*;
import net.minecraft.entity.decoration.painting.PaintingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import buildcraft.api.core.InvalidInputDataException;
import buildcraft.api.schematics.*;
import buildcraft.lib.compat.FluidStackBC;
import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.lib.misc.RotationUtil;

/** Rule-selected entities, with native attachment coordinates and fresh identities. */
public class SchematicEntityDefault implements ISchematicEntity {
    private NbtCompound entityNbt;
    private Vec3d pos;
    private BlockPos hangingPos=BlockPos.ORIGIN;
    private Direction hangingFacing=Direction.NORTH;
    private BlockRotation entityRotation=BlockRotation.NONE;
    private boolean hanging;
    private static final List<String> TRANSIENT=List.of("UUID","UUIDMost","UUIDLeast","Passengers","Leash",
        "Pos","Motion","Rotation","FallDistance","Fire","Air","OnGround","PortalCooldown",
        "HurtTime","HurtByTimestamp","DeathTime","FallFlying","TicksFrozen");

    private static NbtCompound data(Entity entity) {
        var nbt=entity.writeNbt(new NbtCompound());
        nbt.putString("id",Registries.ENTITY_TYPE.getId(entity.getType()).toString());
        nbt.remove("Passengers");nbt.remove("Leash");nbt.remove("LootTable");nbt.remove("LootTableSeed");
        return nbt;
    }
    private static boolean allowed(NbtCompound nbt) {
        var id=Identifier.tryParse(nbt.getString("id"));
        return id!=null && Registries.ENTITY_TYPE.containsId(id) && !id.equals(new Identifier("minecraft:player"))
            && RulesLoader.READ_DOMAINS.contains(id.getNamespace()) && RulesLoader.getRules(id,nbt).stream().anyMatch(rule->rule.capture);
    }
    public static boolean predicate(SchematicEntityContext context) {
        return !(context.entity instanceof PlayerEntity) && context.entity.isAlive()
            && !context.entity.hasPassengers() && !context.entity.hasVehicle() && allowed(data(context.entity));
    }
    @Override public void init(SchematicEntityContext context) {
        if(!predicate(context))throw new IllegalArgumentException("Entity is not eligible for schematic capture");
        entityNbt=data(context.entity);pos=context.entity.getPos().subtract(Vec3d.of(context.basePos));
        hanging=context.entity instanceof AbstractDecorationEntity;
        if(hanging){var decoration=(AbstractDecorationEntity)context.entity;hangingPos=decoration.getDecorationBlockPos().subtract(context.basePos);
            hangingFacing=decoration instanceof ItemFrameEntity?Direction.byId(entityNbt.getByte("Facing")):decoration.getHorizontalFacing();}
        else {hangingPos=BlockPos.ofFloored(pos);hangingFacing=Direction.NORTH;}
        entityRotation=BlockRotation.NONE;
    }
    @Override public Vec3d getPos(){return pos;}
    private List<RequiredExtractor> extractors(){
        if(!allowed(entityNbt))return List.of();
        return RulesLoader.getRules(new Identifier(entityNbt.getString("id")),entityNbt).stream()
            .filter(rule->rule.requiredExtractors!=null).flatMap(rule->rule.requiredExtractors.stream()).toList();
    }
    @Override public List<ItemStack> computeRequiredItems(){
        return extractors().stream().flatMap(e->e.extractItemsFromEntity(entityNbt).stream()).filter(s->s!=null&&!s.isEmpty()).map(ItemStack::copy).toList();
    }
    @Override public List<FluidStackBC> computeRequiredFluids(){
        return extractors().stream().flatMap(e->e.extractFluidsFromEntity(entityNbt).stream()).filter(Objects::nonNull).map(FluidStackBC::copy).toList();
    }
    @Override public SchematicEntityDefault getRotated(BlockRotation rotation){
        SchematicEntityDefault copy=SchematicEntityManager.createCleanCopy(this);
        copy.entityNbt=entityNbt.copy();copy.pos=RotationUtil.rotateVec3d(pos,rotation);copy.hanging=hanging;
        copy.hangingPos=hangingPos.rotate(rotation);copy.hangingFacing=rotation.rotate(hangingFacing);copy.entityRotation=entityRotation.rotate(rotation);return copy;
    }
    private NbtCompound placementNbt(BlockPos base){
        if(entityNbt==null || !allowed(entityNbt))return null;
        var nbt=entityNbt.copy();
        for(var rule:RulesLoader.getRules(new Identifier(entityNbt.getString("id")),entityNbt))if(rule.replaceNbt!=null)nbt=(NbtCompound)NBTUtilBC.merge(nbt,rule.replaceNbt);
        if(!nbt.getString("id").equals(entityNbt.getString("id")))return null;
        nbt.remove("Passengers");nbt.remove("Leash");nbt.remove("LootTable");nbt.remove("LootTableSeed");
        nbt.putUuid("UUID",UUID.randomUUID());nbt.remove("UUIDMost");nbt.remove("UUIDLeast");
        nbt.put("Pos",NBTUtilBC.writeVec3d(Vec3d.of(base).add(pos)));nbt.put("Motion",NBTUtilBC.writeVec3d(Vec3d.ZERO));
        nbt.putFloat("FallDistance",0);nbt.putShort("Fire",(short)-1);nbt.putShort("PortalCooldown",(short)0);
        if(hanging){var at=base.add(hangingPos);nbt.putInt("TileX",at.getX());nbt.putInt("TileY",at.getY());nbt.putInt("TileZ",at.getZ());
            if(nbt.getString("id").equals("minecraft:painting"))nbt.putByte("facing",(byte)hangingFacing.getHorizontal());
            else nbt.putByte("Facing",(byte)hangingFacing.getId());
        }else{
            var angles=nbt.getList("Rotation",NbtElement.FLOAT_TYPE);float yaw=angles.getFloat(0);
            yaw+=switch(entityRotation){case NONE->0;case CLOCKWISE_90->90;case CLOCKWISE_180->180;case COUNTERCLOCKWISE_90->-90;};
            var rotated=new NbtList();rotated.add(NbtFloat.of(MathHelper.wrapDegrees(yaw)));rotated.add(NbtFloat.of(angles.getFloat(1)));nbt.put("Rotation",rotated);
        }
        return nbt;
    }
    public Entity preview(World world,BlockPos base){var nbt=placementNbt(base);return nbt==null?null:EntityType.getEntityFromNbt(nbt,world).orElse(null);}
    public boolean matches(Entity entity,BlockPos base){
        if(entity.isRemoved() || !Registries.ENTITY_TYPE.getId(entity.getType()).toString().equals(entityNbt.getString("id"))
            || entity.getPos().squaredDistanceTo(Vec3d.of(base).add(pos))>.01)return false;
        var expected=preview(entity.getWorld(),base);if(expected==null)return false;
        if(Math.abs(MathHelper.wrapDegrees(expected.getYaw()-entity.getYaw()))>1 || Math.abs(expected.getPitch()-entity.getPitch())>1)return false;
        var a=data(expected);var b=data(entity);TRANSIENT.forEach(key->{a.remove(key);b.remove(key);});return a.equals(b);
    }
    public static boolean accessible(World world,Entity entity){
        var box=entity.getBoundingBox();
        if(!world.isInBuildLimit(BlockPos.ofFloored(box.minX,box.minY,box.minZ)) || !world.isInBuildLimit(BlockPos.ofFloored(box.maxX,box.maxY-1e-6,box.maxZ))
            || !world.getWorldBorder().contains(box))return false;
        for(int x=MathHelper.floor(box.minX)>>4;x<=MathHelper.floor(box.maxX)>>4;x++)for(int z=MathHelper.floor(box.minZ)>>4;z<=MathHelper.floor(box.maxZ)>>4;z++)if(!world.isChunkLoaded(x,z) || world instanceof net.minecraft.server.world.ServerWorld server && !server.isChunkLoaded(ChunkPos.toLong(x,z)))return false;
        return true;
    }
    private boolean canPlace(World world,Entity entity){
        if(!accessible(world,entity))return false;
        if(entity instanceof AbstractDecorationEntity decoration)return decoration.canStayAttached();
        if(entity instanceof EndCrystalEntity){var floor=world.getBlockState(entity.getBlockPos().down());if(!floor.isOf(Blocks.OBSIDIAN)&&!floor.isOf(Blocks.BEDROCK))return false;}
        if(entity instanceof AbstractMinecartEntity && !world.getBlockState(entity.getBlockPos()).isIn(net.minecraft.registry.tag.BlockTags.RAILS))return false;
        return world.isSpaceEmpty(entity,entity.getBoundingBox());
    }
    @Override public Entity build(World world,BlockPos base){
        if(world.isClient)return null;var entity=preview(world,base);
        if(entity==null || !entity.isAlive() || entity.getPos().squaredDistanceTo(Vec3d.of(base).add(pos))>.01 || !canPlace(world,entity) || !world.spawnEntity(entity) || entity.isRemoved())return null;
        if(entity instanceof AbstractDecorationEntity decoration)decoration.onPlace();return entity;
    }
    @Override public Entity buildWithoutChecks(World world,BlockPos base){var entity=preview(world,base);return entity!=null && world.spawnEntity(entity)?entity:null;}
    @Override public NbtCompound serializeNBT(){
        var nbt=new NbtCompound();nbt.put("entityNbt",entityNbt.copy());nbt.put("pos",NBTUtilBC.writeVec3d(pos));nbt.put("hangingPos",NbtHelper.fromBlockPos(hangingPos));
        nbt.putBoolean("hanging",hanging);nbt.put("hangingFacing",NBTUtilBC.writeEnum(hangingFacing));nbt.put("entityRotation",NBTUtilBC.writeEnum(entityRotation));return nbt;
    }
    @Override public void deserializeNBT(NbtCompound nbt)throws InvalidInputDataException{
        var data=nbt.getCompound("entityNbt").copy();var coordinates=nbt.getList("pos",NbtElement.DOUBLE_TYPE);
        if(coordinates.size()!=3 || !allowed(data))throw new InvalidInputDataException("Invalid or disallowed schematic entity");
        var position=NBTUtilBC.readVec3d(coordinates);
        if(!Double.isFinite(position.x)||!Double.isFinite(position.y)||!Double.isFinite(position.z)||Math.abs(position.x)>BuildingExecutor.MAX_VOLUME||Math.abs(position.y)>BuildingExecutor.MAX_VOLUME||Math.abs(position.z)>BuildingExecutor.MAX_VOLUME)
            throw new InvalidInputDataException("Invalid schematic entity position");
        var facing=NBTUtilBC.readEnum(nbt.get("hangingFacing"),Direction.class);var rotation=NBTUtilBC.readEnum(nbt.get("entityRotation"),BlockRotation.class);
        if(facing==null || rotation==null)throw new InvalidInputDataException("Invalid schematic entity rotation");
        var angles=data.getList("Rotation",NbtElement.FLOAT_TYPE);
        if(angles.size()!=2 || !Float.isFinite(angles.getFloat(0)) || !Float.isFinite(angles.getFloat(1)))throw new InvalidInputDataException("Invalid schematic entity angles");
        entityNbt=data;pos=position;hangingPos=NbtHelper.toBlockPos(nbt.getCompound("hangingPos"));hangingFacing=facing;entityRotation=rotation;
        hanging=nbt.getBoolean("hanging") || data.contains("TileX");
        if(hanging && !data.contains("TileX"))throw new InvalidInputDataException("Missing attachment position");
        if(hanging && hangingPos.getSquaredDistance(position)>1024)throw new InvalidInputDataException("Attachment outside entity position");
    }
}
