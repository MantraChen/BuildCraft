/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.silicon.plug;

import buildcraft.api.facades.*;
import buildcraft.api.transport.pipe.IPipeHolder;
import buildcraft.api.transport.pluggable.*;
import buildcraft.silicon.BCSiliconItems;
import net.fabricmc.api.EnvType;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.*;

public final class PluggableFacade extends PipePluggable implements IFacade {
    public final FacadeInstance states;
    private int activeState;
    public PluggableFacade(PluggableDefinition def,IPipeHolder holder,Direction side){this(def,holder,side,FacadeInstance.createSingle(FacadeStateManager.INSTANCE.fallback(),false));}
    public PluggableFacade(PluggableDefinition def,IPipeHolder holder,Direction side,FacadeInstance states){super(def,holder,side);this.states=states;}
    public PluggableFacade(PluggableDefinition def,IPipeHolder holder,Direction side,NbtCompound nbt){
        this(def,holder,side,FacadeInstance.fromContainer(nbt));activeState=MathHelper.clamp(nbt.getInt("activeState"),0,states.size()-1);
    }
    public PluggableFacade(PluggableDefinition def,IPipeHolder holder,Direction side,PacketByteBuf buffer){
        this(def,holder,side,FacadeInstance.readFromBuffer(buffer));activeState=readIndex(buffer);
    }
    private int readIndex(PacketByteBuf buffer){int value=buffer.readUnsignedByte();if(value>=states.size())throw new IllegalArgumentException("Invalid active facade phase");return value;}
    public FacadeBlockStateInfo active(){return states.state(activeState).stateInfo;}
    public int activeIndex(){return activeState;}
    @Override public void onTick(){
        var world=holder.getPipeWorld();var tile=holder.getPipeTile();
        if(world==null || world.isClient || tile==null || tile.isRemoved() || !world.isChunkLoaded(holder.getPipePos())
            || world.getBlockEntity(holder.getPipePos())!=tile || holder.getPluggable(side)!=this)return;
        int next=states.activeIndex(colour->holder.getWireManager().isAnyPowered(colour));
        if(next!=activeState){activeState=next;tile.markDirty();scheduleNetworkUpdate();}
    }
    @Override public NbtCompound writeToNbt(){var nbt=super.writeToNbt();nbt.put("facade",states.writeToNbt());nbt.putInt("activeState",activeState);return nbt;}
    @Override public void writeCreationPayload(PacketByteBuf buffer){states.writeToBuffer(buffer);buffer.writeByte(activeState);}
    @Override public void writePayload(PacketByteBuf buffer,EnvType side){if(side==EnvType.SERVER)buffer.writeByte(activeState);}
    @Override public void readPayload(PacketByteBuf buffer,EnvType side,Object context){if(side==EnvType.CLIENT)activeState=readIndex(buffer);}
    @Override public ItemStack getPickStack(){return BCSiliconItems.plugFacade.createFacadeStack(states);}
    @Override public FacadeType getType(){return states.getType();}
    @Override public boolean isHollow(){return states.isHollow;}
    @Override public FacadePhasedState[] getPhasedStates(){return states.getPhasedStates();}
    @Override public boolean isBlocking(){return !isHollow();}
    @Override public boolean canBeConnected(){return !isHollow();}
    @Override public boolean isSideSolid(){
        if(isHollow())return false;
        for(var phase:states.getPhasedStates())if(!phase.stateInfo.isSideSolid(side))return false;return true;
    }
    @Override public float getExplosionResistance(net.minecraft.entity.Entity exploder,net.minecraft.world.explosion.Explosion explosion){return active().state.getBlock().getBlastResistance();}
    @Override public Box getBoundingBox(){
        return switch(side){
            case DOWN->new Box(0,0,0,1,.125,1);case UP->new Box(0,.875,0,1,1,1);
            case NORTH->new Box(0,0,0,1,1,.125);case SOUTH->new Box(0,0,.875,1,1,1);
            case WEST->new Box(0,0,0,.125,1,1);case EAST->new Box(.875,0,0,1,1,1);
        };
    }
}