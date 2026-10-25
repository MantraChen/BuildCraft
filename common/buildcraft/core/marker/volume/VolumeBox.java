/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0
 * Remake by FelixMantra */
package buildcraft.core.marker.volume;

import java.io.IOException;
import java.util.*;
import java.util.stream.Stream;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.lib.misc.data.Box;
import buildcraft.lib.net.PacketBufferBC;

public class VolumeBox {
    public static final int MAX_VOLUME=1_048_576;
    public final World world;
    public UUID id;
    public Box box;
    private UUID player,oldPlayer;
    private BlockPos held,oldMin,oldMax;
    private double dist;
    public final Map<EnumAddonSlot,Addon> addons=new EnumMap<>(EnumAddonSlot.class);
    public final List<Lock> locks=new ArrayList<>();
    public VolumeBox(World world,BlockPos at){this.world=Objects.requireNonNull(world);id=UUID.randomUUID();box=new Box(at,at);}
    public VolumeBox(World world,NbtCompound nbt){
        this.world=Objects.requireNonNull(world);id=nbt.getUuid("id");box=new Box();box.initialize(nbt.getCompound("box"));
        if(!validBounds(world,box))throw new IllegalArgumentException("Invalid volume bounds");
        player=nbt.containsUuid("player")?nbt.getUuid("player"):null;
        oldPlayer=nbt.containsUuid("oldPlayer")?nbt.getUuid("oldPlayer"):null;
        held=nbt.contains("held",NbtElement.COMPOUND_TYPE)?NbtHelper.toBlockPos(nbt.getCompound("held")):null;
        oldMin=nbt.contains("oldMin",NbtElement.COMPOUND_TYPE)?NbtHelper.toBlockPos(nbt.getCompound("oldMin")):null;
        oldMax=nbt.contains("oldMax",NbtElement.COMPOUND_TYPE)?NbtHelper.toBlockPos(nbt.getCompound("oldMax")):null;
        dist=nbt.getDouble("dist");
        if((player!=null||oldPlayer!=null)&&(held==null||oldMin==null||oldMax==null||!Double.isFinite(dist)||dist<1.5||dist>8||!validBounds(world,new Box(oldMin,oldMax))))
            throw new IllegalArgumentException("Invalid volume editing state");
        var entries=nbt.getList("addons",NbtElement.COMPOUND_TYPE);
        if(entries.size()>8)throw new IllegalArgumentException("Too many volume addons");
        for(int i=0;i<entries.size();i++){
            var tag=entries.getCompound(i);var name=Identifier.tryParse(tag.getString("addonClass"));
            var type=name==null?null:AddonsRegistry.INSTANCE.getClassByName(name);
            var slot=NBTUtilBC.readEnum(tag.get("slot"),EnumAddonSlot.class);
            if(type==null||slot==null||addons.containsKey(slot))throw new IllegalArgumentException("Invalid volume addon");
            try{
                var addon=type.getDeclaredConstructor().newInstance();addon.volumeBox=this;
                addon.readFromNBT(tag.getCompound("addonData"));addons.put(slot,addon);addon.postReadFromNbt();
            }catch(ReflectiveOperationException invalid){throw new IllegalArgumentException("Invalid volume addon",invalid);}
        }
        var savedLocks=nbt.getList("locks",NbtElement.COMPOUND_TYPE);
        if(savedLocks.size()>256)throw new IllegalArgumentException("Too many volume locks");
        for(int i=0;i<savedLocks.size();i++){var lock=new Lock();lock.readFromNBT(savedLocks.getCompound(i));locks.add(lock);}
    }
    public VolumeBox(World world,PacketBufferBC buf)throws IOException{this.world=Objects.requireNonNull(world);fromBytes(buf);}
    public static boolean validBounds(World world,Box bounds){
        if(!bounds.isInitialized())return false;
        var min=bounds.min();var max=bounds.max();
        long x=(long)max.getX()-min.getX()+1,y=(long)max.getY()-min.getY()+1,z=(long)max.getZ()-min.getZ()+1;
        return x>0&&y>0&&z>0&&x<=MAX_VOLUME&&y<=MAX_VOLUME&&z<=MAX_VOLUME&&x*y<=MAX_VOLUME&&x*y*z<=MAX_VOLUME
            &&world.isInBuildLimit(min)&&world.isInBuildLimit(max)&&world.getWorldBorder().contains(min)&&world.getWorldBorder().contains(max);
    }
    public boolean canEdit(PlayerEntity user,BlockPos target){
        return user!=null&&user.getWorld()==world&&!user.isSpectator()&&(!isEditing()||isEditingBy(user))
            &&world.isChunkLoaded(target)&&world.canPlayerModifyAt(user,target)
            &&user.squaredDistanceTo(target.getX()+.5,target.getY()+.5,target.getZ()+.5)<=64;
    }
    public boolean isEditing(){return player!=null;}
    private void resetEditing(){oldMin=oldMax=held=null;dist=0;oldPlayer=null;}
    public void cancelEditing(){
        if(oldMin!=null&&oldMax!=null)box=new Box(oldMin,oldMax);
        player=null;resetEditing();addons.values().forEach(Addon::onVolumeBoxSizeChange);
    }
    public void confirmEditing(){player=null;resetEditing();addons.values().forEach(Addon::onVolumeBoxSizeChange);}
    public void pauseEditing(){oldPlayer=player;player=null;}
    public void resumeEditing(){if(player==null){player=oldPlayer;oldPlayer=null;}}
    public void setPlayer(PlayerEntity user){player=user.getUuid();oldPlayer=null;}
    public boolean isEditingBy(PlayerEntity user){return user!=null&&Objects.equals(player,user.getUuid());}
    public boolean isPausedEditingBy(PlayerEntity user){return user!=null&&Objects.equals(oldPlayer,user.getUuid());}
    public PlayerEntity getPlayer(World world){return player==null?null:world.getPlayerByUuid(player);}
    public void setHeldDistOldMinOldMax(BlockPos held,double dist,BlockPos min,BlockPos max){this.held=held.toImmutable();this.dist=dist;oldMin=min.toImmutable();oldMax=max.toImmutable();}
    public BlockPos getHeld(){return held;}
    public double getDist(){return dist;}
    public Stream<Lock.Target> getLockTargetsStream(){return locks.stream().flatMap(lock->lock.targets.stream());}
    public boolean isAddonLocked(EnumAddonSlot slot){return getLockTargetsStream().anyMatch(target->target instanceof Lock.Target.TargetAddon addon&&addon.slot==slot);}
    public NbtCompound writeToNBT(){
        var nbt=new NbtCompound();nbt.putUuid("id",id);nbt.put("box",box.writeToNBT());
        if(player!=null)nbt.putUuid("player",player);if(oldPlayer!=null)nbt.putUuid("oldPlayer",oldPlayer);
        if(held!=null)nbt.put("held",NbtHelper.fromBlockPos(held));if(oldMin!=null)nbt.put("oldMin",NbtHelper.fromBlockPos(oldMin));if(oldMax!=null)nbt.put("oldMax",NbtHelper.fromBlockPos(oldMax));nbt.putDouble("dist",dist);
        nbt.put("addons",NBTUtilBC.writeCompoundList(addons.entrySet().stream().map(entry->{
            var tag=new NbtCompound();tag.put("slot",NBTUtilBC.writeEnum(entry.getKey()));
            tag.putString("addonClass",AddonsRegistry.INSTANCE.getNameByClass(entry.getValue().getClass()).toString());
            tag.put("addonData",entry.getValue().writeToNBT(new NbtCompound()));return tag;
        })));
        nbt.put("locks",NBTUtilBC.writeCompoundList(locks.stream().map(Lock::writeToNBT)));return nbt;
    }
    /** Keep open screens attached to the same addon while atomically replacing validated state. */
    public void apply(VolumeBox source){
        if(source.world!=world||id!=null&&!id.equals(source.id))throw new IllegalArgumentException("Volume identity changed");
        id=source.id;box=source.box;player=source.player;oldPlayer=source.oldPlayer;held=source.held;oldMin=source.oldMin;oldMax=source.oldMax;dist=source.dist;
        addons.keySet().removeIf(slot->!source.addons.containsKey(slot));
        source.addons.forEach((slot,incoming)->{
            var existing=addons.get(slot);
            if(existing==null||existing.getClass()!=incoming.getClass()){incoming.volumeBox=this;addons.put(slot,incoming);incoming.postReadFromNbt();}
            else{existing.readFromNBT(incoming.writeToNBT(new NbtCompound()));existing.postReadFromNbt();}
        });
        locks.clear();locks.addAll(source.locks);
    }
    public void toBytes(PacketBufferBC buffer){buffer.writeNbt(writeToNBT());}
    public void fromBytes(PacketBufferBC buffer)throws IOException{
        try{var tag=buffer.readNbt();if(tag==null)throw new IOException("Missing volume data");var incoming=new VolumeBox(world,tag);if(buffer.isReadable())throw new IOException("Trailing volume data");apply(incoming);}
        catch(RuntimeException invalid){throw new IOException("Invalid volume data",invalid);}
    }
    @Override public boolean equals(Object other){return this==other||other instanceof VolumeBox volume&&id.equals(volume.id);}
    @Override public int hashCode(){return id.hashCode();}
}
