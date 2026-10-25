/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0
 * Native MJ/RF attachment remake by FelixMantra.
 */
package buildcraft.transport.plug;

import buildcraft.api.mj.*;
import buildcraft.api.transport.pipe.IPipeHolder;
import buildcraft.api.transport.pluggable.*;
import buildcraft.lib.BCLibConfig;
import buildcraft.lib.mj.TransactionalMjReceiver;
import buildcraft.transport.BCTransportItems;
import net.fabricmc.fabric.api.transfer.v1.transaction.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.*;
import team.reborn.energy.api.EnergyStorage;

/** Blocks item/fluid passage while exposing the pipe behavior's native power input. */
public final class PluggablePowerAdaptor extends PipePluggable {
    private long storedMJ;
    private final Input input=new Input();
    private final RfInput rfInput=new RfInput();
    public PluggablePowerAdaptor(PluggableDefinition definition,IPipeHolder holder,Direction side){super(definition,holder,side);}
    public PluggablePowerAdaptor(PluggableDefinition definition,IPipeHolder holder,Direction side,NbtCompound nbt){
        this(definition,holder,side);
        storedMJ=Math.max(0,Math.min(MjRfConversion.MAX_MJ_PER_RF-1,nbt.getLong("storedMJ")));
    }
    @Override public NbtCompound writeToNbt(){var nbt=super.writeToNbt();nbt.putLong("storedMJ",storedMJ);return nbt;}
    public long getStoredMicroMj(){return storedMJ;}
    @Override public boolean isBlocking(){return true;}
    @Override public ItemStack getPickStack(){return new ItemStack(BCTransportItems.plugPowerAdaptor);}
    @Override public Box getBoundingBox(){
        double lo=3/16.0,hi=13/16.0;
        return switch(side) {
            case DOWN->new Box(lo,0,lo,hi,.25,hi);case UP->new Box(lo,.75,lo,hi,1,hi);
            case NORTH->new Box(lo,lo,0,hi,hi,.25);case SOUTH->new Box(lo,lo,.75,hi,hi,1);
            case WEST->new Box(0,lo,lo,.25,hi,hi);case EAST->new Box(.75,lo,lo,1,hi,hi);
        };
    }
    private boolean live(){
        var world=holder.getPipeWorld();var tile=holder.getPipeTile();
        return world!=null && !world.isClient && tile!=null && !tile.isRemoved()
            && world.isChunkLoaded(holder.getPipePos()) && world.getBlockEntity(holder.getPipePos())==tile
            && holder.getPluggable(side)==this && holder.getPipe()!=null;
    }
    private IMjRedstoneReceiver receiver(){
        return live() && holder.getPipe().getBehaviour().getMjConnector(side) instanceof IMjRedstoneReceiver receiver?receiver:null;
    }
    @Override public IMjConnector getMjConnector(){return receiver()==null?null:input;}
    public EnergyStorage getEnergyStorage(){return rfInput.available()?rfInput:null;}
    private final class Input implements IMjRedstoneReceiver {
        @Override public boolean canConnect(IMjConnector other){var receiver=receiver();return receiver!=null && receiver.canConnect(other);}
        @Override public boolean canReceive(){var receiver=receiver();return receiver!=null && receiver.canReceive();}
        @Override public long getPowerRequested(){var receiver=receiver();return receiver==null?0:Math.max(0,receiver.getPowerRequested());}
        @Override public long receivePower(long power,boolean simulate){
            if(power<=0)return power;
            var receiver=receiver();if(receiver==null || !receiver.canReceive())return power;
            long excess=receiver.receivePower(power,simulate);
            if(!simulate && excess<power)holder.getPipeTile().markDirty();
            return excess;
        }
    }
    private final class RfInput extends SnapshotParticipant<Long> implements EnergyStorage {
        boolean available(){return BCLibConfig.powerMode!=BCLibConfig.PowerMode.MJ_ONLY && receiver() instanceof TransactionalMjReceiver;}
        @Override public boolean supportsInsertion(){return available();}
        @Override public boolean supportsExtraction(){return false;}
        @Override public long insert(long maxAmount,TransactionContext transaction){
            if(maxAmount<0)throw new IllegalArgumentException("Negative RF input");
            if(maxAmount==0 || !available() || !(receiver() instanceof TransactionalMjReceiver receiver) || !receiver.canReceive())return 0;
            long rate=BCLibConfig.mjRfConversion.mjPerRf;
            long offeredRf=Math.min(maxAmount,(Long.MAX_VALUE-storedMJ)/rate);
            long offeredMj=offeredRf*rate+storedMJ;
            try(var nested=transaction.openNested()){
                updateSnapshots(nested);
                long excess=receiver.receivePower(offeredMj,nested);
                if(excess<0 || excess>offeredMj)throw new IllegalStateException("Invalid MJ acceptance");
                long used=offeredMj-excess;
                if(used==0)return 0;
                long chargedMj=Math.max(0,used-storedMJ);
                long acceptedRf=chargedMj/rate+(chargedMj%rate==0?0:1);
                storedMJ=storedMJ+acceptedRf*rate-used;
                nested.commit();
                return acceptedRf;
            }
        }
        @Override public long extract(long maxAmount,TransactionContext transaction){
            if(maxAmount<0)throw new IllegalArgumentException("Negative RF extraction");return 0;
        }
        // This is a work adapter, not an RF battery. Only sub-RF MJ credit is retained.
        @Override public long getAmount(){return 0;}
        @Override public long getCapacity(){return 0;}
        @Override protected Long createSnapshot(){return storedMJ;}
        @Override protected void readSnapshot(Long snapshot){storedMJ=snapshot;}
        @Override protected void onFinalCommit(){holder.getPipeTile().markDirty();}
    }
}
