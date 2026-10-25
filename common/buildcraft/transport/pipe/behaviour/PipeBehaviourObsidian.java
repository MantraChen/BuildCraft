/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0
 * Native Fabric entity collection. Remake by FelixMantra.
 */
package buildcraft.transport.pipe.behaviour;

import java.util.*;
import buildcraft.api.mj.*;
import buildcraft.api.transport.pipe.*;
import buildcraft.lib.inventory.ItemTransactorHelper;
import buildcraft.lib.misc.InventoryUtil;
import buildcraft.lib.mj.TransactionalMjReceiver;
import buildcraft.transport.tile.TilePipeHolder;
import net.fabricmc.fabric.api.transfer.v1.transaction.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.entity.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.*;

/** A single connected output collects collisions for free, or scans four forward planes using MJ. */
public class PipeBehaviourObsidian extends PipeBehaviour implements IMjRedstoneReceiver, TransactionalMjReceiver {
    private static final long POWER_PER_ITEM=MjAPI.MJ/2,POWER_PER_METRE=MjAPI.MJ/4;
    private static final int DROP_GAP=20;
    private static final double INSERT_SPEED=.04;
    private final WeakHashMap<ItemEntity,Long> dropped=new WeakHashMap<>();
    private int reloadDelay;
    private record Pending(ItemStack stack,Direction from) {}
    private final List<Pending> pending=new ArrayList<>();
    private final SnapshotParticipant<List<Pending>> snapshots=new SnapshotParticipant<>(){
        @Override protected List<Pending> createSnapshot(){return List.copyOf(pending);}
        @Override protected void readSnapshot(List<Pending> state){pending.clear();pending.addAll(state);}
        @Override protected void onFinalCommit(){
            var committed=List.copyOf(pending);pending.clear();
            for(var entry:committed){
                if(live() && pipe.getFlow() instanceof IFlowItems flow)flow.insertItemsForce(entry.stack(),entry.from(),null,INSERT_SPEED);
                else InventoryUtil.drop(pipe.getHolder().getPipeWorld(),pipe.getHolder().getPipePos(),entry.stack());
            }
            pipe.getHolder().getPipeTile().markDirty();
        }
    };
    public PipeBehaviourObsidian(IPipe pipe){super(pipe);}
    public PipeBehaviourObsidian(IPipe pipe,NbtCompound nbt){super(pipe,nbt);reloadDelay=DROP_GAP;}
    private boolean live(){
        return pipe.getHolder() instanceof TilePipeHolder holder && holder.isStorageAvailable() && holder.getPipe()==pipe
            && holder.getWorld()!=null && !holder.getWorld().isClient;
    }
    @Override public void onTick(){if(live() && reloadDelay>0)reloadDelay--;}
    @Override public boolean canConnect(Direction face,PipeBehaviour other){return !(other instanceof PipeBehaviourObsidian);}
    @Override public IMjConnector getMjConnector(Direction facing){return this;}
    @Override public boolean canConnect(IMjConnector other){return true;}
    @Override public boolean canReceive(){return live();}
    private Direction openFace(){
        Direction open=null;
        for(var face:Direction.values())if(pipe.isConnected(face)){
            if(open!=null)return null;open=face.getOpposite();
        }
        if(open!=null){var plug=pipe.getHolder().getPluggable(open);if(plug!=null && plug.isBlocking())return null;}
        return open;
    }
    protected Box getSuckingBox(Direction face,int distance){
        var center=Vec3d.ofCenter(pipe.getHolder().getPipePos());
        var box=new Box(center.x-.4,center.y-.4,center.z-.4,center.x+.4,center.y+.4,center.z+.4)
            .offset(face.getOffsetX()*distance,face.getOffsetY()*distance,face.getOffsetZ()*distance);
        return switch(face.getAxis()){
            case X -> box.expand(.5,distance,distance);
            case Y -> box.expand(distance,.5,distance);
            case Z -> box.expand(distance,distance,.5);
        };
    }
    @Override public void onEntityCollide(Entity entity){
        if(!live() || reloadDelay>0)return;
        var face=openFace();if(face==null)return;
        try(var transaction=Transaction.openNested(Transaction.getCurrentUnsafe())){
            collect(entity,face,0,true,transaction);transaction.commit();
        }
    }
    private long collect(Entity entity,Direction face,long power,boolean free,TransactionContext transaction){
        var world=pipe.getHolder().getPipeWorld();
        if(entity.isRemoved() || entity instanceof LivingEntity || entity.getWorld()!=world || !world.isChunkLoaded(entity.getBlockPos())
            || !(pipe.getFlow() instanceof IFlowItems))return power;
        if(entity instanceof ItemEntity item && dropped.getOrDefault(item,Long.MIN_VALUE)>world.getTime())return power;
        double distance=entity.getPos().distanceTo(Vec3d.ofCenter(pipe.getHolder().getPipePos()));
        if(!Double.isFinite(distance))return power;
        long cost=free?0:(long)(Math.max(1,distance)*POWER_PER_METRE+POWER_PER_ITEM);
        int max=free?Integer.MAX_VALUE:(int)Math.min(Integer.MAX_VALUE,power/cost);
        if(max<=0)return power;
        var transactor=ItemTransactorHelper.getTransactorForEntity(entity,face.getOpposite());
        var extracted=transactor.extract(null,1,max,false);
        if(extracted.isEmpty())return power;
        snapshots.updateSnapshots(transaction);pending.add(new Pending(extracted.copy(),face));
        return power-cost*extracted.getCount();
    }
    @PipeEventHandler public void onPipeDrop(PipeEventItem.Drop event){dropped.put(event.getEntity(),pipe.getHolder().getPipeWorld().getTime()+DROP_GAP);}
    private long scan(long power,TransactionContext transaction){
        if(power<=0 || !live() || reloadDelay>0)return power;
        var face=openFace();if(face==null)return power;
        for(int distance=1;distance<=4;distance++){
            var entities=pipe.getHolder().getPipeWorld().getOtherEntities(null,getSuckingBox(face,distance));
            for(var entity:entities){
                long left=collect(entity,face,power,false,transaction);
                if(left<power)return left;
            }
        }
        // Preserve the original empty-scan cost without accepting more energy than was offered.
        return power-Math.min(power,MjAPI.MJ);
    }
    @Override public long getPowerRequested(){long offered=512*MjAPI.MJ;return offered-receivePower(offered,true);}
    @Override public long receivePower(long power,boolean simulate){
        try(var transaction=Transaction.openNested(Transaction.getCurrentUnsafe())){
            long left=scan(power,transaction);if(!simulate)transaction.commit();return left;
        }
    }
    @Override public long receivePower(long power,TransactionContext transaction){
        try(var nested=transaction.openNested()){long left=scan(power,nested);nested.commit();return left;}
    }
}
