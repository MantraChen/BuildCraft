/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0
 * Native pulse extraction, gate controls and animation. Remake by FelixMantra.
 */
package buildcraft.silicon.plug;

import java.io.IOException;
import net.fabricmc.api.EnvType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Direction;
import buildcraft.api.mj.*;
import buildcraft.api.transport.pipe.*;
import buildcraft.api.transport.pluggable.PluggableDefinition;
import buildcraft.silicon.*;
import buildcraft.transport.BCTransportConfig;

/** One 20-tick pulse; single extraction requests survive unavailable inputs/capacity. */
public final class PluggablePulsar extends PluggableSidedPlate {
    public static final int PULSE_STAGE=20,MAX_PENDING=1024;
    private boolean manuallyEnabled,pulsingClient,autoClient;
    private int pulseStage,gateEnabledTicks,gateSinglePulses,lastFlags=-1;
    public PluggablePulsar(PluggableDefinition definition,IPipeHolder holder,Direction side){super(definition,holder,side);}
    public PluggablePulsar(PluggableDefinition definition,IPipeHolder holder,Direction side,NbtCompound nbt) {
        this(definition,holder,side);manuallyEnabled=nbt.getBoolean("manuallyEnabled");
        pulseStage=Math.max(0,Math.min(PULSE_STAGE-1,nbt.getInt("pulseStage")));
        gateEnabledTicks=Math.max(0,Math.min(10,nbt.getInt("gateEnabledTicks")));
        gateSinglePulses=Math.max(0,Math.min(MAX_PENDING,nbt.getInt("gateSinglePulses")));
    }
    public PluggablePulsar(PluggableDefinition definition,IPipeHolder holder,Direction side,PacketByteBuf buffer) {
        this(definition,holder,side);readData(buffer);
    }
    @Override public NbtCompound writeToNbt() {
        var nbt=super.writeToNbt();nbt.putBoolean("manuallyEnabled",manuallyEnabled);nbt.putInt("pulseStage",pulseStage);
        nbt.putInt("gateEnabledTicks",gateEnabledTicks);nbt.putInt("gateSinglePulses",gateSinglePulses);return nbt;
    }
    @Override public ItemStack getPickStack(){return new ItemStack(BCSiliconItems.plugPulsar);}
    public boolean isPulsing(){return holder.getPipeWorld().isClient?pulsingClient:manuallyEnabled || gateEnabledTicks>0 || gateSinglePulses>0;}
    public boolean isAutomatic(){return holder.getPipeWorld().isClient?autoClient:gateEnabledTicks>0 || gateSinglePulses>0;}
    public boolean isManual(){return manuallyEnabled;}
    public int getPulseStage(){return pulseStage;}
    public int getPendingPulses(){return gateSinglePulses;}
    public void enablePulsar(){if(liveServer() && gateEnabledTicks!=10){gateEnabledTicks=10;holder.getPipeTile().markDirty();}}
    public void addSinglePulse(){if(liveServer() && gateSinglePulses<MAX_PENDING){gateSinglePulses++;holder.getPipeTile().markDirty();}}
    @Override public void onTick() {
        if(!isAttached())return;
        if(holder.getPipeWorld().isClient){pulseStage=isPulsing()?(pulseStage+1)%PULSE_STAGE:0;return;}
        boolean on=isPulsing();int oldStage=pulseStage;
        pulseStage=on?pulseStage+1:0;
        if(gateEnabledTicks>0)gateEnabledTicks--;
        if(pulseStage>=PULSE_STAGE) {
            pulseStage=0;
            if(holder.getPipe()!=null && holder.getPipe().getBehaviour() instanceof IMjRedstoneReceiver receiver) {
                if(gateSinglePulses>0) {
                    long amount=holder.getPipe().getFlow() instanceof IFlowFluid?BCTransportConfig.millibucketExtractionCost()*1000
                        :holder.getPipe().getFlow() instanceof IFlowItems?BCTransportConfig.itemExtractionCost():MjAPI.MJ;
                    if(amount>0 && receiver.receivePower(amount,true)==0 && receiver.receivePower(amount,false)==0)gateSinglePulses--;
                } else receiver.receivePower(MjAPI.MJ,false);
            }
        }
        int flags=(isPulsing()?1:0)|(isAutomatic()?2:0)|(manuallyEnabled?4:0);
        if(flags!=lastFlags){lastFlags=flags;scheduleNetworkUpdate();}
        if(on || oldStage!=pulseStage)holder.getPipeTile().markDirty();
    }
    @PipeEventHandler public void addActions(PipeEventStatement.AddActionInternalSided event) {
        if(event.side==side && isAttached()){event.actions.add(BCSiliconStatements.ACTION_PULSAR_CONSTANT);event.actions.add(BCSiliconStatements.ACTION_PULSAR_SINGLE);}
    }
    @Override public boolean onPluggableActivate(PlayerEntity player,HitResult hit,float x,float y,float z) {
        if(!isAttached() || !holder.canPlayerInteract(player))return false;
        if(!holder.getPipeWorld().isClient) {
            manuallyEnabled=!manuallyEnabled;holder.getPipeTile().markDirty();scheduleNetworkUpdate();
            holder.getPipeWorld().playSound(null,holder.getPipePos(),net.minecraft.sound.SoundEvents.BLOCK_LEVER_CLICK,net.minecraft.sound.SoundCategory.BLOCKS,.3f,manuallyEnabled?.6f:.5f);
        }
        return true;
    }
    private void writeData(PacketByteBuf buffer) {
        buffer.writeBoolean(isPulsing());buffer.writeBoolean(isAutomatic());buffer.writeBoolean(manuallyEnabled);buffer.writeByte(pulseStage);
    }
    private void readData(PacketByteBuf buffer) {
        boolean on=buffer.readBoolean(),automatic=buffer.readBoolean(),manual=buffer.readBoolean();int stage=buffer.readUnsignedByte();
        if(stage>=PULSE_STAGE || on!=(automatic || manual))throw new IllegalArgumentException("Invalid pulsar state");
        pulsingClient=on;autoClient=automatic;manuallyEnabled=manual;pulseStage=stage;
    }
    @Override public void writeCreationPayload(PacketByteBuf buffer){writeData(buffer);}
    @Override public void writePayload(PacketByteBuf buffer,EnvType side){if(side==EnvType.SERVER)writeData(buffer);}
    @Override public void readPayload(PacketByteBuf buffer,EnvType side,Object context) throws IOException {
        if(side==EnvType.CLIENT)try{readData(buffer);}catch(IllegalArgumentException error){throw new IOException("Invalid pulsar update",error);}
    }
}
