/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0
 * Remake by FelixMantra */
package buildcraft.core.marker.volume;

import java.util.*;
import net.fabricmc.api.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import buildcraft.lib.net.*;

/** Dimension-scoped, immutable batches; native packet limits do not depend on the number of boxes. */
public final class MessageVolumeBoxes implements FabricPacket {
    public static final int MAX_BOX_BYTES=65536,MAX_PACKET_BYTES=256*1024,MAX_BOXES=4096;
    public static final PacketType<MessageVolumeBoxes> TYPE=PacketType.create(new Identifier("buildcraft","volume_boxes"),MessageVolumeBoxes::read);
    final Identifier dimension;final UUID generation;final int index;final boolean last;final List<byte[]> boxes;
    private MessageVolumeBoxes(Identifier dimension,UUID generation,int index,boolean last,List<byte[]> boxes){
        this.dimension=dimension;this.generation=generation;this.index=index;this.last=last;this.boxes=List.copyOf(boxes);
    }
    public static List<MessageVolumeBoxes> batches(ServerWorld world,List<VolumeBox> volumes){
        if(volumes.size()>MAX_BOXES)throw new IllegalArgumentException("Too many volume boxes");
        List<List<byte[]>> groups=new ArrayList<>();List<byte[]> current=new ArrayList<>();int size=0;
        for(var volume:volumes){
            var buffer=PacketBufferBC.write(volume::toBytes);byte[] bytes;
            try{bytes=new byte[buffer.readableBytes()];buffer.getBytes(buffer.readerIndex(),bytes);}finally{buffer.release();}
            if(bytes.length>MAX_BOX_BYTES)throw new IllegalArgumentException("Volume data too large");
            if(!current.isEmpty()&&size+bytes.length+5>MAX_PACKET_BYTES){groups.add(current);current=new ArrayList<>();size=0;}
            current.add(bytes);size+=bytes.length+5;
        }
        groups.add(current);var generation=UUID.randomUUID();List<MessageVolumeBoxes> result=new ArrayList<>();
        for(int i=0;i<groups.size();i++)result.add(new MessageVolumeBoxes(world.getRegistryKey().getValue(),generation,i,i==groups.size()-1,groups.get(i)));
        return List.copyOf(result);
    }
    public static void send(ServerWorld world,Collection<ServerPlayerEntity> players){
        if(players.isEmpty())return;
        for(var packet:batches(world,WorldSavedDataVolumeBoxes.get(world).volumeBoxes))
            for(var player:players)if(player.getWorld()==world)BCNetworkManager.sendTo(packet,player);
    }
    public static MessageVolumeBoxes read(PacketByteBuf buffer){
        Identifier dimension=buffer.readIdentifier();UUID generation=buffer.readUuid();int index=buffer.readVarInt();boolean last=buffer.readBoolean();int count=buffer.readVarInt();
        if(index<0||index>MAX_BOXES||count<0||count>MAX_BOXES)throw new IllegalArgumentException("Invalid volume batch");
        List<byte[]> boxes=new ArrayList<>();int size=0;
        for(int i=0;i<count;i++){byte[] bytes=buffer.readByteArray(MAX_BOX_BYTES);size+=bytes.length+5;if(size>MAX_PACKET_BYTES)throw new IllegalArgumentException("Volume batch too large");boxes.add(bytes);}
        if(buffer.isReadable())throw new IllegalArgumentException("Trailing volume batch data");
        return new MessageVolumeBoxes(dimension,generation,index,last,boxes);
    }
    @Override public void write(PacketByteBuf buffer){
        buffer.writeIdentifier(dimension);buffer.writeUuid(generation);buffer.writeVarInt(index);buffer.writeBoolean(last);buffer.writeVarInt(boxes.size());
        for(var box:boxes)buffer.writeByteArray(box);
    }
    @Override public PacketType<?> getType(){return TYPE;}
    @Environment(EnvType.CLIENT) public static void registerClientHandler(){
        BCNetworkManager.registerClientReceiver(TYPE,(packet,player,sender)->ClientVolumeBoxes.INSTANCE.accept(player.getWorld(),packet));
    }
}
