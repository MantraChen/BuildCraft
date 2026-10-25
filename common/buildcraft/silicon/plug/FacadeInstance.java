/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.silicon.plug;

import java.util.*;
import buildcraft.api.facades.*;
import net.minecraft.nbt.*;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.DyeColor;

public final class FacadeInstance implements IFacade {
    private final FacadePhasedState[] phases;
    public final boolean isHollow;
    public FacadeInstance(FacadePhasedState[] states,boolean hollow){
        if(states.length<1 || states.length>17)throw new IllegalArgumentException("Facade requires 1 to 17 states");
        var colours=new HashSet<DyeColor>();
        for(var phase:states)if(phase==null || !colours.add(phase.activeColour))throw new IllegalArgumentException("Duplicate facade phase");
        phases=states.clone();isHollow=hollow;
    }
    public static FacadeInstance createSingle(FacadeBlockStateInfo state,boolean hollow){return new FacadeInstance(new FacadePhasedState[]{state.createPhased(null)},hollow);}
    @Override public FacadeType getType(){return phases.length==1?FacadeType.Basic:FacadeType.Phased;}
    @Override public boolean isHollow(){return isHollow;}
    @Override public FacadePhasedState[] getPhasedStates(){return phases.clone();}
    public int size(){return phases.length;}
    public FacadePhasedState state(int index){return phases[index];}
    public FacadeInstance withSwappedIsHollow(){return new FacadeInstance(phases,!isHollow);}
    public boolean canAddColour(DyeColor colour){for(var phase:phases)if(phase.activeColour==colour)return false;return phases.length<17;}
    public FacadeInstance withState(FacadePhasedState state){
        if(!canAddColour(state.activeColour))return null;var copy=Arrays.copyOf(phases,phases.length+1);copy[phases.length]=state;return new FacadeInstance(copy,isHollow);
    }
    public FacadePhasedState getCurrentStateForStack(){return phases[(int)((System.currentTimeMillis()/500)%phases.length)];}
    public int activeIndex(java.util.function.Predicate<DyeColor> powered){
        int fallback=0;
        for(int i=0;i<phases.length;i++)if(phases[i].activeColour==null)fallback=i;
        for(int i=0;i<phases.length;i++)if(phases[i].activeColour!=null && powered.test(phases[i].activeColour))return i;
        return fallback;
    }
    public NbtCompound writeToNbt(){
        var nbt=new NbtCompound();var list=new NbtList();for(var phase:phases)list.add(phase.writeToNbt());
        nbt.put("states",list);nbt.putBoolean("isHollow",isHollow);return nbt;
    }
    public static FacadeInstance readFromNbt(NbtCompound nbt){
        var list=nbt.getList("states",NbtElement.COMPOUND_TYPE);
        if(list.size()>=1 && list.size()<=17)try{
            var phases=new FacadePhasedState[list.size()];for(int i=0;i<phases.length;i++)phases[i]=FacadePhasedState.readFromNbt(list.getCompound(i));
            return new FacadeInstance(phases,nbt.getBoolean("isHollow"));
        }catch(IllegalArgumentException ignored){}
        return createSingle(FacadeStateManager.INSTANCE.fallback(),false);
    }
    /** Supports the pre-wrapper item and placed-facade save layout as well. */
    public static FacadeInstance fromContainer(NbtCompound nbt){
        if(nbt==null)return readFromNbt(new NbtCompound());
        if(nbt.contains("facade",NbtElement.COMPOUND_TYPE))return readFromNbt(nbt.getCompound("facade"));
        var legacy=nbt.copy();var list=legacy.getList("states",NbtElement.COMPOUND_TYPE);
        if(!legacy.contains("isHollow") && !list.isEmpty())legacy.putBoolean("isHollow",list.getCompound(0).getBoolean("isHollow"));
        return readFromNbt(legacy);
    }
    public void writeToBuffer(PacketByteBuf buffer){buffer.writeBoolean(isHollow);buffer.writeByte(phases.length);for(var phase:phases)phase.writeToBuffer(buffer);}
    public static FacadeInstance readFromBuffer(PacketByteBuf buffer){
        boolean hollow=buffer.readBoolean();int count=buffer.readUnsignedByte();
        if(count<1 || count>17)throw new IllegalArgumentException("Invalid facade phase count");
        var phases=new FacadePhasedState[count];for(int i=0;i<count;i++)phases[i]=FacadePhasedState.readFromBuffer(buffer);
        return new FacadeInstance(phases,hollow);
    }
}