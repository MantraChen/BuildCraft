/* Copyright (c) 2016 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0
 * Remake by FelixMantra */
package buildcraft.robotics.container;
import java.io.IOException;
import java.util.*;
import buildcraft.core.item.ItemPaintbrush_BC8;
import buildcraft.lib.gui.*;
import buildcraft.lib.gui.slot.*;
import buildcraft.lib.misc.data.IdAllocator;
import buildcraft.lib.net.PacketBufferBC;
import buildcraft.robotics.tile.TileZonePlanner;
import buildcraft.robotics.zone.ZonePlan;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.*;
public class ContainerZonePlanner extends ContainerBCTile<TileZonePlanner> {
    private static final IdAllocator IDS=ContainerBC_Neptune.IDS.makeChild("zone_planner");
    public static final int EDIT=IDS.allocId("EDIT"),LAYER=IDS.allocId("LAYER");
    private long mapRequestTick=Long.MIN_VALUE,editTick=Long.MIN_VALUE;private int mapRequests,edits,nextLayer;
    private final long[] sent=new long[16];public final boolean[] ready=new boolean[16];
    public boolean allowMapRequest(long tick){if(tick!=mapRequestTick){mapRequestTick=tick;mapRequests=0;}return ++mapRequests<=4;}
    public ContainerZonePlanner(PlayerEntity player,TileZonePlanner tile){this(player,0,tile);}
    public ContainerZonePlanner(PlayerEntity player,int syncId,TileZonePlanner tile){
        super(buildcraft.robotics.BCRoboticsScreens.ZONE,player,syncId,tile);Arrays.fill(sent,Long.MIN_VALUE);
        for(int x=0;x<4;x++)for(int y=0;y<4;y++)addSlot(new SlotBase(tile.invPaintbrushes,x*4+y,8+x*18,146+y*18));
        addSlot(new SlotBase(tile.invInputPaintbrush,0,8,125));addSlot(new SlotBase(tile.invInputMapLocation,0,26,125));addSlot(new SlotOutput(tile.invInputResult,0,74,125));
        addSlot(new SlotBase(tile.invOutputPaintbrush,0,233,9));addSlot(new SlotBase(tile.invOutputMapLocation,0,233,27));addSlot(new SlotOutput(tile.invOutputResult,0,233,75));
        addFullPlayerInventory(88,146);addProperties(tile.properties);
    }
    @Override public IdAllocator getIdAllocator(){return IDS;}
    @Override public void sendContentUpdates(){
        super.sendContentUpdates();if(player.getWorld().isClient||player.currentScreenHandler!=this||!canUse(player))return;
        for(int offset=0;offset<16;offset++){int index=(nextLayer+offset)&15;if(sent[index]!=tile.versions[index]){sendMessage(LAYER,b->{b.writeByte(index);b.writeBoolean(tile.hasUnreadableLayer(index));tile.layers[index].writeToByteBuf(b);});sent[index]=tile.versions[index];nextLayer=(index+1)&15;break;}}
    }
    @Override public void readMessage(int id,PacketBufferBC b,boolean client,Object context)throws IOException{
        if(id==LAYER){if(!client)throw new IOException("Client cannot replace a planner layer");int index=b.readUnsignedByte();boolean invalid=b.readBoolean();if(index>=16)throw new IOException("Invalid zone layer");var layer=new ZonePlan().readFromByteBuf(b);if(b.isReadable())throw new IOException("Trailing zone layer data");tile.layers[index]=layer;ready[index]=!invalid;}
        else if(id==EDIT){if(client)throw new IOException("Server cannot send editor input");int colour=b.readUnsignedByte();int x1=b.readInt(),z1=b.readInt(),x2=b.readInt(),z2=b.readInt();boolean value=b.readBoolean();if(b.isReadable())throw new IOException("Trailing zone edit data");edit(colour,x1,z1,x2,z2,value);}
        else super.readMessage(id,b,client,context);
    }
    public void paint(int colour,int x1,int z1,int x2,int z2,boolean value){sendMessage(EDIT,b->{b.writeByte(colour);b.writeInt(x1);b.writeInt(z1);b.writeInt(x2);b.writeInt(z2);b.writeBoolean(value);});}
    public boolean edit(int colour,int x1,int z1,int x2,int z2,boolean value){
        if(player.getWorld().isClient||player.currentScreenHandler!=this||!canUse(player)||player.isSpectator()||Transaction.getCurrentUnsafe()!=null||colour<0||colour>=16||tile.hasUnreadableLayer(colour))return false;
        var brush=ItemPaintbrush_BC8.getColour(getCursorStack());if(brush==null||brush.getId()!=colour)return false;
        long now=player.getWorld().getTime();if(now!=editTick){editTick=now;edits=0;}if(++edits>4)return false;
        int minX=Math.min(x1,x2),maxX=Math.max(x1,x2),minZ=Math.min(z1,z2),maxZ=Math.max(z1,z2);
        if((long)maxX-minX>255||(long)maxZ-minZ>255)return false;
        int radius=Math.min(32,((net.minecraft.server.network.ServerPlayerEntity)player).server.getPlayerManager().getViewDistance());var center=player.getChunkPos();
        for(int cx=minX>>4;cx<=maxX>>4;cx++)for(int cz=minZ>>4;cz<=maxZ>>4;cz++)if(Math.abs((long)cx-center.x)>radius||Math.abs((long)cz-center.z)>radius||!player.getWorld().isChunkLoaded(cx,cz))return false;
        if(!player.getWorld().getWorldBorder().contains(new BlockPos(minX,tile.getPos().getY(),minZ))||!player.getWorld().getWorldBorder().contains(new BlockPos(maxX,tile.getPos().getY(),maxZ)))return false;
        var copy=new ZonePlan(tile.layers[colour]);try{for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)copy.set(Math.subtractExact(x,tile.getPos().getX()),Math.subtractExact(z,tile.getPos().getZ()),value);}catch(IllegalArgumentException|ArithmeticException invalid){return false;}
        tile.replaceLayer(colour,copy);return true;
    }
}
