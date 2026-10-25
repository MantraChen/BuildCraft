/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.silicon.plug;

import java.util.Objects;
import buildcraft.api.facades.IFacadePhasedState;
import net.minecraft.block.Block;
import net.minecraft.nbt.*;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.util.DyeColor;

public final class FacadePhasedState implements IFacadePhasedState {
    public final FacadeBlockStateInfo stateInfo;
    public final DyeColor activeColour;
    public FacadePhasedState(FacadeBlockStateInfo info,DyeColor colour){stateInfo=Objects.requireNonNull(info);activeColour=colour;}
    public FacadePhasedState withColour(DyeColor colour){return new FacadePhasedState(stateInfo,colour);}
    @Override public FacadeBlockStateInfo getState(){return stateInfo;}
    @Override public DyeColor getActiveColor(){return activeColour;}
    public NbtCompound writeToNbt(){
        var nbt=new NbtCompound();nbt.put("state",NbtHelper.fromBlockState(stateInfo.state));
        if(activeColour!=null)nbt.putString("activeColour",activeColour.name());return nbt;
    }
    public static FacadePhasedState readFromNbt(NbtCompound nbt){
        var state=NbtHelper.toBlockState(Registries.BLOCK.getReadOnlyWrapper(),nbt.getCompound("state"));
        var info=FacadeStateManager.INSTANCE.find(state);if(info==null)info=FacadeStateManager.INSTANCE.fallback();
        DyeColor colour=null;
        if(nbt.contains("activeColour",NbtElement.STRING_TYPE)){
            String name=nbt.getString("activeColour");if(name.equals("SILVER"))name="LIGHT_GRAY";
            if(!name.equals("_NULL"))colour=DyeColor.valueOf(name.toUpperCase(java.util.Locale.ROOT));
        }
        return new FacadePhasedState(info,colour);
    }
    public void writeToBuffer(PacketByteBuf buffer){buffer.writeVarInt(Block.getRawIdFromState(stateInfo.state));buffer.writeByte(activeColour==null?0:activeColour.getId()+1);}
    public static FacadePhasedState readFromBuffer(PacketByteBuf buffer){
        int raw=buffer.readVarInt(),colour=buffer.readUnsignedByte();
        if(raw<0 || raw>=Block.STATE_IDS.size() || colour>16)throw new IllegalArgumentException("Invalid facade state/colour");
        var info=FacadeStateManager.INSTANCE.find(Block.getStateFromRawId(raw));
        // A previously saved facade may have been disabled by a compatibility hook.
        // Use the same recovery state as NBT instead of disconnecting the client.
        if(info==null)info=FacadeStateManager.INSTANCE.fallback();
        return new FacadePhasedState(info,colour==0?null:DyeColor.byId(colour-1));
    }
}
