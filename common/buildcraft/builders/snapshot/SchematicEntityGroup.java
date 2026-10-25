/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0
 * Remake by FelixMantra
 */
package buildcraft.builders.snapshot;

import java.util.*;
import java.util.function.*;
import buildcraft.api.core.InvalidInputDataException;
import buildcraft.api.schematics.*;
import buildcraft.lib.compat.FluidStackBC;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.AbstractDecorationEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** A bounded, ordered riding tree. Materials, identity and placement belong to the whole tree. */
public final class SchematicEntityGroup implements ISchematicEntity {
    public static final int MAX_DEPTH=64;
    private List<SchematicEntityDefault> members=List.of();
    private int[] parents=new int[0];
    private record Tree(List<Entity> members,int[] parents){}

    // Validate before writing entity NBT, whose vanilla passenger serialization is recursive.
    private static Tree tree(Entity root){
        if(root.hasVehicle())return null;
        var entities=new ArrayList<Entity>();var indices=new ArrayList<Integer>();var depths=new ArrayList<Integer>();
        var seen=Collections.newSetFromMap(new IdentityHashMap<Entity,Boolean>());
        entities.add(root);indices.add(-1);depths.add(0);seen.add(root);
        for(int i=0;i<entities.size();i++){
            var parent=entities.get(i);if(!parent.isAlive())return null;
            for(var child:parent.getPassengerList()){
                if(child.getVehicle()!=parent || !seen.add(child) || entities.size()>=Blueprint.MAX_ENTITIES || depths.get(i)+1>MAX_DEPTH)return null;
                entities.add(child);indices.add(i);depths.add(depths.get(i)+1);
            }
        }
        return new Tree(List.copyOf(entities),indices.stream().mapToInt(Integer::intValue).toArray());
    }
    public static List<Entity> entities(Entity root){var tree=tree(root);return tree==null?List.of():tree.members;}
    public static boolean predicate(SchematicEntityContext context){
        var tree=tree(context.entity);return tree!=null && tree.members.size()>1 && tree.members.stream().allMatch(SchematicEntityDefault::eligible);
    }
    @Override public void init(SchematicEntityContext context){
        var tree=tree(context.entity);
        if(tree==null || tree.members.size()<2)throw new IllegalArgumentException("Invalid riding group");
        var captured=new ArrayList<SchematicEntityDefault>();
        for(var entity:tree.members){var member=new SchematicEntityDefault();member.initMember(new SchematicEntityContext(context.world,context.basePos,entity));captured.add(member);}
        members=List.copyOf(captured);parents=tree.parents;
    }
    public int size(){return members.size();}
    public List<Vec3d> positions(){return members.stream().map(SchematicEntityDefault::getPos).toList();}
    @Override public Vec3d getPos(){return members.get(0).getPos();}
    @Override public List<ItemStack> computeRequiredItems(){return members.stream().flatMap(m->m.computeRequiredItems().stream()).toList();}
    @Override public List<FluidStackBC> computeRequiredFluids(){return members.stream().flatMap(m->m.computeRequiredFluids().stream()).toList();}
    @Override public SchematicEntityGroup getRotated(BlockRotation rotation){
        var copy=new SchematicEntityGroup();copy.members=members.stream().map(m->m.getRotated(rotation)).toList();copy.parents=parents.clone();return copy;
    }
    private List<Entity> preview(World world,BlockPos base){
        var result=new ArrayList<Entity>();
        for(var member:members){var entity=member.preview(world,base);if(entity==null)return List.of();result.add(entity);}
        for(int i=1;i<result.size();i++)if(!result.get(i).startRiding(result.get(parents[i]),true))return List.of();
        // Native seats depend on the complete passenger list and the rotated parent's yaw.
        for(int i=1;i<result.size();i++)result.get(parents[i]).updatePassengerPosition(result.get(i));
        return result;
    }
    private boolean sameTree(List<Entity> expected){
        if(expected.isEmpty())return false;var actual=tree(expected.get(0));
        return actual!=null && actual.members.equals(expected) && Arrays.equals(actual.parents,parents);
    }
    public boolean matches(Entity root,BlockPos base){
        var actual=tree(root);if(actual==null || !Arrays.equals(actual.parents,parents))return false;
        var expected=preview(root.getWorld(),base);if(expected.size()!=members.size())return false;
        for(int i=0;i<expected.size();i++)if(!SchematicEntityDefault.matchesMember(expected.get(i),actual.members.get(i)))return false;
        return true;
    }
    public Entity buildChecked(World world,BlockPos base,BiPredicate<SchematicEntityDefault,Entity> authorize,BooleanSupplier live){
        return place(world,base,authorize,live,true);
    }
    private Entity place(World world,BlockPos base,BiPredicate<SchematicEntityDefault,Entity> authorize,BooleanSupplier live,boolean checkSpace){
        if(!(world instanceof ServerWorld server))return null;
        var created=preview(world,base);boolean complete=false;
        try{
            if(created.size()!=members.size() || !sameTree(created) || !live.getAsBoolean())return null;
            for(int i=0;i<created.size();i++)if(!authorize.test(members.get(i),created.get(i)))return null;
            if(!live.getAsBoolean() || !sameTree(created))return null;
            for(var entity:created)if(checkSpace && !SchematicEntityDefault.canPlace(world,entity))return null;
            for(var entity:created)if(!live.getAsBoolean() || !world.spawnEntity(entity) || entity.isRemoved())return null;
            if(!live.getAsBoolean() || !sameTree(created) || created.stream().anyMatch(e->server.getEntity(e.getUuid())!=e))return null;
            for(var entity:created)if(entity instanceof AbstractDecorationEntity decoration)decoration.onPlace();
            complete=true;return created.get(0);
        }finally{
            // The material transaction aborts on failure. Undo every entity we created before returning.
            if(!complete)for(int i=created.size()-1;i>=0;i--){var entity=created.get(i);entity.stopRiding();entity.discard();}
        }
    }
    @Override public Entity build(World world,BlockPos base){return buildChecked(world,base,(m,e)->true,()->true);}
    @Override public Entity buildWithoutChecks(World world,BlockPos base){return place(world,base,(m,e)->true,()->true,false);}
    @Override public NbtCompound serializeNBT(){
        var nbt=new NbtCompound();var list=new NbtList();for(var member:members)list.add(member.serializeNBT());nbt.put("members",list);nbt.putIntArray("parents",parents.clone());return nbt;
    }
    @Override public void deserializeNBT(NbtCompound nbt)throws InvalidInputDataException{
        if(!(nbt.get("members") instanceof NbtList list) || list.getHeldType()!=NbtElement.COMPOUND_TYPE || list.size()<2 || list.size()>Blueprint.MAX_ENTITIES)
            throw new InvalidInputDataException("Invalid riding group size/type");
        var links=nbt.getIntArray("parents");if(links.length!=list.size() || links[0]!=-1)throw new InvalidInputDataException("Invalid riding group root");
        var depth=new int[links.length];
        for(int i=1;i<links.length;i++)if(links[i]<0 || links[i]>=i || (depth[i]=depth[links[i]]+1)>MAX_DEPTH || (i>1 && links[i]<links[i-1]))
            throw new InvalidInputDataException("Invalid riding group order/depth");
        var loaded=new ArrayList<SchematicEntityDefault>();
        for(int i=0;i<list.size();i++){var member=new SchematicEntityDefault();member.deserializeNBT(list.getCompound(i));loaded.add(member);}
        members=List.copyOf(loaded);parents=links.clone();
    }
}
