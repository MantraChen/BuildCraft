/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team.
 * SPDX-License-Identifier: MPL-2.0
 * Remake by FelixMantra.
 */
package buildcraft.silicon.plug;

import buildcraft.api.transport.pipe.*;
import buildcraft.api.transport.pluggable.*;
import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.silicon.BCSiliconItems;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.*;

/** Paints items crossing this face, or filters their existing colour without painting. */
public final class PluggableLens extends PipePluggable {
    public final DyeColor colour;
    public final boolean isFilter;
    public PluggableLens(PluggableDefinition def,IPipeHolder holder,Direction side) {this(def,holder,side,null,false);}
    public PluggableLens(PluggableDefinition def,IPipeHolder holder,Direction side,DyeColor colour,boolean filter) {
        super(def,holder,side);this.colour=colour;this.isFilter=filter;
    }
    public PluggableLens(PluggableDefinition def,IPipeHolder holder,Direction side,NbtCompound nbt) {
        this(def,holder,side,nbt.contains("colour")?readSavedColour(nbt.get("colour")):
            nbt.contains("c",NbtElement.NUMBER_TYPE) && nbt.getInt("c")>=0 && nbt.getInt("c")<16?DyeColor.byId(nbt.getInt("c")):null,nbt.getBoolean("f"));
    }
    public PluggableLens(PluggableDefinition def,IPipeHolder holder,Direction side,PacketByteBuf buffer) {
        this(def,holder,side,readColour(buffer),buffer.readBoolean());
    }
    private static DyeColor readSavedColour(NbtElement value){
        // 1.12 named the light-gray enum SILVER; preserve that saved world colour.
        return value instanceof NbtString && value.asString().equals("SILVER")
            ? DyeColor.LIGHT_GRAY : NBTUtilBC.readEnum(value,DyeColor.class);
    }
    private static DyeColor readColour(PacketByteBuf buffer) {
        int id=buffer.readUnsignedByte();
        if(id>16)throw new IllegalArgumentException("Invalid lens colour "+id);
        return id==0?null:DyeColor.byId(id-1);
    }
    @Override public NbtCompound writeToNbt(){
        var nbt=super.writeToNbt();nbt.put("colour",NBTUtilBC.writeEnum(colour));nbt.putBoolean("f",isFilter);return nbt;
    }
    @Override public void writeCreationPayload(PacketByteBuf buffer){buffer.writeByte(colour==null?0:colour.getId()+1);buffer.writeBoolean(isFilter);}
    @Override public ItemStack getPickStack(){return BCSiliconItems.plugLens.getStack(colour,isFilter);}
    @Override public Box getBoundingBox() {
        return switch(side) {
            case DOWN->new Box(3/16.0,0,3/16.0,13/16.0,2/16.0,13/16.0);
            case UP->new Box(3/16.0,14/16.0,3/16.0,13/16.0,1,13/16.0);
            case NORTH->new Box(3/16.0,3/16.0,0,13/16.0,13/16.0,2/16.0);
            case SOUTH->new Box(3/16.0,3/16.0,14/16.0,13/16.0,13/16.0,1);
            case WEST->new Box(0,3/16.0,3/16.0,2/16.0,13/16.0,13/16.0);
            case EAST->new Box(14/16.0,3/16.0,3/16.0,1,13/16.0,13/16.0);
        };
    }
    private boolean liveServer(){
        var world=holder.getPipeWorld();var tile=holder.getPipeTile();
        return world!=null && !world.isClient && tile!=null && !tile.isRemoved()
            && world.isChunkLoaded(holder.getPipePos()) && world.getBlockEntity(holder.getPipePos())==tile
            && holder.getPluggable(side)==this;
    }
    @PipeEventHandler public void tryInsert(PipeEventItem.TryInsert event){
        if(liveServer() && isFilter && event.from==side && event.colour!=null && event.colour!=colour)event.cancel();
    }
    @PipeEventHandler public void sideCheck(PipeEventItem.SideCheck event){
        if(!liveServer() || !isFilter)return;
        if(event.colour==colour)event.increasePriority(side);
        else if(event.colour!=null)event.disallow(side);
        else event.decreasePriority(side);
    }
    @PipeEventHandler public void onInsert(PipeEventItem.OnInsert event){
        if(liveServer() && !isFilter && event.from==side)event.colour=colour;
    }
    @PipeEventHandler public void reachEnd(PipeEventItem.ReachEnd event){
        if(liveServer() && !isFilter && event.to==side)event.colour=colour;
    }
}
