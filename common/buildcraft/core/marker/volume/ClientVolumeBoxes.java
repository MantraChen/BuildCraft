/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.core.marker.volume;
import java.util.*;
import net.fabricmc.api.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;
import buildcraft.lib.net.PacketBufferBC;
@Environment(EnvType.CLIENT)
public enum ClientVolumeBoxes {
    INSTANCE;
    public final List<VolumeBox> volumeBoxes=new ArrayList<>();
    private World world;private UUID generation;private int next;
    private final Map<UUID,VolumeBox> pending=new LinkedHashMap<>();
    public void worldChanged(World nextWorld){if(world!=nextWorld){world=nextWorld;volumeBoxes.clear();pending.clear();generation=null;next=0;}}
    public void clear(){world=null;volumeBoxes.clear();pending.clear();generation=null;next=0;}
    public VolumeBox upsert(World world,NbtCompound tag){worldChanged(world);return upsert(new VolumeBox(world,tag));}
    private VolumeBox upsert(VolumeBox incoming){
        for(var existing:volumeBoxes)if(existing.id.equals(incoming.id)){existing.apply(incoming);return existing;}
        volumeBoxes.add(incoming);return incoming;
    }
    public void accept(World current,MessageVolumeBoxes packet){
        worldChanged(current);if(!current.getRegistryKey().getValue().equals(packet.dimension))return;
        if(packet.index==0){pending.clear();generation=packet.generation;next=0;}
        if(!Objects.equals(generation,packet.generation)||packet.index!=next)throw new IllegalArgumentException("Out-of-order volume batch");
        try{
            for(byte[] bytes:packet.boxes){
                var buffer=new PacketBufferBC(io.netty.buffer.Unpooled.wrappedBuffer(bytes));
                try{
                    var incoming=new VolumeBox(current,buffer);
                    if(pending.putIfAbsent(incoming.id,incoming)!=null||pending.size()>MessageVolumeBoxes.MAX_BOXES)throw new IllegalArgumentException("Duplicate or oversized volume collection");
                }finally{buffer.release();}
            }
        }catch(java.io.IOException invalid){pending.clear();throw new IllegalArgumentException("Invalid volume batch",invalid);}
        next++;
        if(packet.last){
            volumeBoxes.removeIf(existing->!pending.containsKey(existing.id));
            pending.values().forEach(this::upsert);pending.clear();generation=null;next=0;
        }
    }
}
