/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0
 * Native Fabric stripes pipe. Remake by FelixMantra.
 */
package buildcraft.transport.pipe.behaviour;

import java.io.IOException;
import java.util.*;
import buildcraft.api.core.EnumPipePart;
import buildcraft.api.mj.*;
import buildcraft.api.tools.IToolWrench;
import buildcraft.api.transport.IStripesActivator;
import buildcraft.api.transport.pipe.*;
import buildcraft.lib.misc.*;
import buildcraft.lib.mj.TransactionalMjReceiver;
import buildcraft.transport.BCTransportStatements;
import buildcraft.transport.statements.ActionPipeDirection;
import buildcraft.transport.stripes.StripesInteraction;
import buildcraft.transport.tile.TilePipeHolder;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.transfer.v1.transaction.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;

public class PipeBehaviourStripes extends PipeBehaviour implements IStripesActivator, IMjRedstoneReceiver, TransactionalMjReceiver {
    private static final long CAPACITY=256*MjAPI.MJ,PER_TICK=10*MjAPI.MJ;
    public Direction direction;
    private long stored,progress;
    private BlockPos workPos;
    private BlockState workState;
    private int retry;
    private final int crackId=System.identityHashCode(this);
    private final SnapshotParticipant<Long> energySnapshots=new SnapshotParticipant<>(){
        @Override protected Long createSnapshot(){return stored;}
        @Override protected void readSnapshot(Long state){stored=state;}
        @Override protected void onFinalCommit(){dirty();}
    };
    public PipeBehaviourStripes(IPipe pipe){super(pipe);}
    public PipeBehaviourStripes(IPipe pipe,NbtCompound nbt){
        super(pipe,nbt);stored=Math.max(0,Math.min(CAPACITY,nbt.getCompound("battery").getLong("stored")));
        direction=NBTUtilBC.readEnum(nbt.get("direction"),Direction.class);
        if(nbt.contains("workPos") && nbt.contains("workState")){
            workPos=BlockPos.fromLong(nbt.getLong("workPos"));workState=NbtHelper.toBlockState(Registries.BLOCK.getReadOnlyWrapper(),nbt.getCompound("workState"));progress=Math.max(0,nbt.getLong("progress"));
        }
    }
    public long getStoredMicroMj(){return stored;}
    public long getProgressMicroMj(){return progress;}
    public boolean isLive(){return pipe.getHolder() instanceof TilePipeHolder holder && holder.isStorageAvailable() && holder.getPipe()==pipe && !holder.getWorld().isClient;}
    private void dirty(){var tile=pipe.getHolder().getPipeTile();if(tile!=null)tile.markDirty();}
    private boolean outlet(Direction side){if(side==null || pipe.isConnected(side))return false;var plug=pipe.getHolder().getPluggable(side);return plug==null || !plug.isBlocking();}
    public void setDirection(Direction side){
        if(direction==side)return;clearWork();direction=side;dirty();
        var world=pipe.getHolder().getPipeWorld();if(world!=null && !world.isClient)pipe.getHolder().scheduleNetworkUpdate(IPipeHolder.PipeMessageReceiver.BEHAVIOUR);
    }
    private void clearCracks(){var world=pipe.getHolder().getPipeWorld();if(world instanceof ServerWorld && workPos!=null)world.setBlockBreakingInfo(crackId,workPos,-1);}
    private void clearWork(){clearCracks();boolean changed=workPos!=null || progress!=0;progress=0;workPos=null;workState=null;retry=0;if(changed)dirty();}
    @PipeEventHandler public void onInvalidate(PipeEventTileState.Invalidate event){clearCracks();}
    @PipeEventHandler public void onUnload(PipeEventTileState.ChunkUnload event){clearCracks();}
    @Override public boolean canConnect(Direction face,PipeBehaviour other){return !(other instanceof PipeBehaviourStripes);}
    @Override public IMjConnector getMjConnector(Direction side){return this;}
    @Override public boolean canConnect(IMjConnector other){return true;}
    @Override public boolean canReceive(){return isLive();}
    @Override public long getPowerRequested(){return isLive()?CAPACITY-stored:0;}
    @Override public long receivePower(long power,boolean simulate){
        if(power<=0 || !isLive())return power;long accepted=Math.min(power,CAPACITY-stored);
        if(!simulate && accepted>0){var tx=Transaction.getCurrentUnsafe();if(tx!=null)energySnapshots.updateSnapshots(tx);stored+=accepted;if(tx==null)dirty();}
        return power-accepted;
    }
    @Override public long receivePower(long power,TransactionContext transaction){
        if(power<=0 || !isLive())return power;long accepted=Math.min(power,CAPACITY-stored);
        if(accepted>0){energySnapshots.updateSnapshots(transaction);stored+=accepted;}return power-accepted;
    }
    @Override public NbtCompound writeToNbt(){
        var nbt=super.writeToNbt();var battery=new NbtCompound();battery.putLong("stored",stored);nbt.put("battery",battery);nbt.put("direction",NBTUtilBC.writeEnum(direction));
        if(workPos!=null && workState!=null && progress>0){nbt.putLong("workPos",workPos.asLong());nbt.put("workState",NbtHelper.fromBlockState(workState));nbt.putLong("progress",progress);}return nbt;
    }
    @Override public void writePayload(PacketByteBuf buffer,EnvType side){if(side==EnvType.SERVER)buffer.writeByte(direction==null?6:direction.ordinal());}
    @Override public void readPayload(PacketByteBuf buffer,EnvType side,Object ctx) throws IOException {
        if(side!=EnvType.CLIENT)return;int value=buffer.readUnsignedByte();if(value>6)throw new IOException("Invalid stripes direction");direction=value==6?null:Direction.values()[value];
    }
    @PipeEventHandler public void addActions(PipeEventStatement.AddActionInternal event){for(var face:Direction.values())if(outlet(face))event.actions.add(BCTransportStatements.ACTION_PIPE_DIRECTION[face.ordinal()]);}
    @PipeEventHandler public void onAction(PipeEventActionActivate event){if(isLive() && event.action instanceof ActionPipeDirection action && outlet(action.direction))setDirection(action.direction);}
    @Override public boolean onPipeActivate(PlayerEntity player,HitResult trace,float x,float y,float z,EnumPipePart part){
        var hand=EntityUtil.getWrenchHand(player);if(hand==null || !pipe.getHolder().canPlayerInteract(player))return false;var stack=player.getStackInHand(hand);var wrench=(IToolWrench)stack.getItem();
        if(!wrench.canWrench(player,hand,stack,trace))return false;
        if(isLive()){int start=direction==null?-1:direction.ordinal();for(int i=1;i<=6;i++){var next=Direction.values()[(start+i)%6];if(outlet(next)){setDirection(next);wrench.wrenchUsed(player,hand,stack,trace);break;}}}return true;
    }
    @Override public void onTick(){
        if(!isLive() || Transaction.getCurrentUnsafe()!=null)return;
        if(!outlet(direction)){
            Direction next=null;int count=0;for(var face:Direction.values())if(pipe.isConnected(face)){next=face.getOpposite();count++;}
            setDirection(count==1 && outlet(next)?next:null);
        }
        var world=(ServerWorld)pipe.getHolder().getPipeWorld();var origin=pipe.getHolder().getPipePos();
        if(direction==null){clearWork();return;}var target=origin.offset(direction);
        if(!StripesInteraction.loaded(world,target)){clearWork();return;}
        var state=world.getBlockState(target);long cost=BlockUtil.computeBlockBreakPower(world,target);
        if(cost<=0 || cost==Long.MAX_VALUE){clearWork();return;}
        if(!target.equals(workPos) || state!=workState){clearWork();workPos=target;workState=state;}
        if(progress>cost){progress=cost;dirty();}
        if(progress<cost){long used=Math.min(stored,Math.min(PER_TICK,cost-progress));stored-=used;progress+=used;if(used>0){dirty();world.setBlockBreakingInfo(crackId,target,(int)(9.0*progress/cost));}return;}
        if(retry>0){retry--;return;}
        var drops=BlockUtil.breakBlockAndGetDrops(world,target,new ItemStack(Items.DIAMOND_PICKAXE),pipe.getHolder().getOwner(),false);
        if(drops.isPresent()){clearWork();dirty();for(var stack:drops.get())if(!sendItem(stack,direction))dropItem(stack,direction);}
        else retry=20; // Retain paid work while native protection refuses the break.
    }
    @PipeEventHandler public void onDrop(PipeEventItem.Drop event){
        if(!isLive() || !outlet(direction) || event.getStack().isEmpty())return;
        var world=(ServerWorld)pipe.getHolder().getPipeWorld();var pos=pipe.getHolder().getPipePos();var face=direction;
        var player=FakePlayerProvider.INSTANCE.getFakePlayer(world,pipe.getHolder().getOwner(),pos);
        var inventory=player.getInventory();var previous=new ArrayList<ItemStack>();for(int i=0;i<inventory.size();i++)previous.add(inventory.getStack(i).copy());int selected=inventory.selectedSlot;
        float yaw=player.getYaw(),pitch=player.getPitch();var oldPos=player.getPos();boolean handled=false;
        try{
            inventory.clear();inventory.selectedSlot=0;player.setStackInHand(Hand.MAIN_HAND,event.getStack().copy());
            player.setYaw(face.asRotation());player.setPitch(face==Direction.UP?-90:face==Direction.DOWN?90:0);
            try{
                if(StripesInteraction.allow(player,pos.offset(face),face.getOpposite()).isAccepted())handled=true;
                else if(isLive())handled=PipeApi.stripeRegistry.handleItem(world,pos,face,player.getMainHandStack(),player,this);
            }catch(StripesInteraction.Denied denied){handled=true;}
            if(handled){
                event.setStack(ItemStack.EMPTY);
                for(int i=0;i<inventory.size();i++){var stack=inventory.removeStack(i);if(!stack.isEmpty() && !sendItem(stack,face))dropItem(stack,face);}
            }
        }finally{
            inventory.clear();for(int i=0;i<previous.size();i++)inventory.setStack(i,previous.get(i));inventory.selectedSlot=selected;player.refreshPositionAndAngles(oldPos.x,oldPos.y,oldPos.z,yaw,pitch);
        }
    }
    @Override public boolean sendItem(ItemStack stack,Direction from){
        if(stack.isEmpty())return true;if(!isLive() || !(pipe.getFlow() instanceof IFlowItems flow))return false;flow.insertItemsForce(stack.copy(),from,null,.02);dirty();return true;
    }
    @Override public void dropItem(ItemStack stack,Direction from){InventoryUtil.drop(pipe.getHolder().getPipeWorld(),pipe.getHolder().getPipePos(),stack.copy());}
}
