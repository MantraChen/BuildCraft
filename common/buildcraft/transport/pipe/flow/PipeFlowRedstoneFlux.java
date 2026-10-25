/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0
 * Native transactional RF transport remake by FelixMantra. */
package buildcraft.transport.pipe.flow;

import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import javax.annotation.Nullable;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import team.reborn.energy.api.EnergyStorage;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.tiles.IDebuggable;
import buildcraft.api.transport.pipe.*;
import buildcraft.lib.misc.VecUtil;
import buildcraft.lib.misc.data.AverageInt;
import buildcraft.lib.net.PacketBufferBC;

/** Six delayed RF sections. Every native transfer includes buffers and per-tick budgets in rollback. */
public class PipeFlowRedstoneFlux extends PipeFlow implements IFlowRedstoneFlux, IDebuggable {
    public static final int NET_POWER_AMOUNTS = 2;
    private static final int MAX_RATE = Integer.MAX_VALUE / 32;
    private final EnumMap<Direction, Section> sections = new EnumMap<>(Direction.class);
    private int maxPower = -1, capacity;
    private boolean disabled, isReceiver;
    private long currentWorldTime = Long.MIN_VALUE, lastTick = Long.MIN_VALUE;
    public Vec3d clientDisplayFlowCentre = VecUtil.VEC_HALF, clientDisplayFlowCentreLast = VecUtil.VEC_HALF;
    public long clientLastDisplayTime;

    private record State(long tick, int[] sections) {}
    private final SnapshotParticipant<State> snapshots = new SnapshotParticipant<>() {
        @Override protected State createSnapshot() {
            int[] state = new int[36]; int index = 0;
            for (var s : sections.values()) {
                state[index++]=s.internalPower; state[index++]=s.internalNextPower;
                state[index++]=s.received; state[index++]=s.sent;
                state[index++]=s.powerQuery; state[index++]=s.nextPowerQuery;
            }
            return new State(currentWorldTime,state);
        }
        @Override protected void readSnapshot(State state) {
            currentWorldTime=state.tick; int index=0;
            for (var s : sections.values()) {
                s.internalPower=state.sections[index++]; s.internalNextPower=state.sections[index++];
                s.received=state.sections[index++]; s.sent=state.sections[index++];
                s.powerQuery=state.sections[index++]; s.nextPowerQuery=state.sections[index++];
            }
        }
        @Override protected void onFinalCommit() { pipe.getHolder().getPipeTile().markDirty(); }
    };

    public PipeFlowRedstoneFlux(IPipe pipe) {
        super(pipe);
        for (var side : Direction.values()) sections.put(side,new Section(side));
    }
    public PipeFlowRedstoneFlux(IPipe pipe,NbtCompound nbt) {
        this(pipe);
        int limit=rate(PipeApi.getRfTransferInfo(pipe.getDefinition()).transferPerTick);
        for(var s:sections.values()) {
            var saved=nbt.getCompound(s.side.getName());
            s.internalPower=(int)clamp(saved.getLong("stored"),0,limit);
            s.internalNextPower=(int)clamp(saved.getLong("incoming"),0,limit-s.internalPower);
        }
    }
    @Override public NbtCompound writeToNbt() {
        var nbt=super.writeToNbt(); nbt.putInt("rf_format",1);
        for(var s:sections.values()) {
            var saved=new NbtCompound(); saved.putInt("stored",s.internalPower);saved.putInt("incoming",s.internalNextPower);
            nbt.put(s.side.getName(),saved);
        }
        return nbt;
    }
    @Override public void reconfigure() {
        var info=PipeApi.getRfTransferInfo(pipe.getDefinition());
        var event=new PipeEventRedstoneFlux.Configure(pipe.getHolder(),this);
        event.setReceiver(info.isReceiver);event.setMaxPower(info.transferPerTick);
        pipe.getHolder().fireEvent(event);
        isReceiver=event.isReceiver();disabled=event.isTransferDisabled();
        maxPower=rate(event.getMaxPower());capacity=rate(info.transferPerTick);
    }
    private void configure() { if(maxPower<0)reconfigure(); }
    private static int rate(int value) { return (int)clamp(value,1,MAX_RATE); }
    private static long clamp(long value,long min,long max) { return Math.max(min,Math.min(max,value)); }
    private static void nonNegative(long amount) { if(amount<0)throw new IllegalArgumentException("Negative RF transfer"); }
    private boolean live() {
        var holder=pipe.getHolder();var world=holder.getPipeWorld();
        return world!=null && !holder.getPipeTile().isRemoved() && world.isChunkLoaded(holder.getPipePos())
            && world.getBlockEntity(holder.getPipePos())==holder.getPipeTile() && holder.getPipe()==pipe;
    }
    private boolean server() { return live() && !pipe.getHolder().getPipeWorld().isClient; }
    private boolean open(Direction side) {
        var plug=pipe.getHolder().getPluggable(side);return plug==null || !plug.isBlocking();
    }
    private EnergyStorage neighbour(Direction side,boolean connected) {
        if(side==null || !live() || !open(side) || connected && !pipe.isConnected(side))return null;
        var world=pipe.getHolder().getPipeWorld();var pos=pipe.getHolder().getPipePos().offset(side);
        return world.isChunkLoaded(pos)?EnergyStorage.SIDED.find(world,pos,side.getOpposite()):null;
    }
    @Override public boolean canConnect(Direction side,PipeFlow other) { return other instanceof PipeFlowRedstoneFlux; }
    @Override public boolean canConnect(Direction side,BlockEntity tile) { return neighbour(side,false)!=null; }
    public EnergyStorage getStorage(@Nullable Direction side) { configure();return side==null?null:sections.get(side); }
    public Section getSection(Direction side) { return sections.get(side); }
    public long getStoredPower() { long total=0;for(var s:sections.values())total+=s.internalPower+s.internalNextPower;return total; }
    public int getPowerRequested(@Nullable Direction side) {
        configure();if(disabled)return 0;
        long total=0;for(var s:sections.values())if(s.side!=side && pipe.isConnected(s.side))total+=s.powerQuery;
        return (int)Math.min(total,maxPower);
    }
    public double getMaxTransferForRender(float partialTicks) { configure();return maxPower; }

    /** Explicit passive extraction; source and pipe join the same Fabric transaction. */
    @Override public int tryExtractPower(int maximum,Direction from) {
        configure();
        if(maximum<=0 || from==null || !server() || !isReceiver || disabled)return 0;
        var source=neighbour(from,true);
        if(source==null || !source.supportsExtraction())return 0;
        try(var tx=Transaction.openOuter()) {
            int limit=Math.min(maximum,sections.get(from).room());
            long extracted=source.extract(limit,tx);
            if(extracted<0 || extracted>limit)throw new IllegalStateException("Invalid native energy extraction");
            int accepted=sections.get(from).insertInternal((int)extracted,tx);
            if(accepted!=extracted)return 0;
            tx.commit();return accepted;
        }
    }
    private void step() {
        long now=pipe.getHolder().getPipeWorld().getTime();
        if(currentWorldTime==now)return;
        currentWorldTime=now;
        for(var s:sections.values()) {
            s.powerQuery=s.nextPowerQuery;s.nextPowerQuery=0;
            s.internalPower+=s.internalNextPower;s.internalNextPower=0;s.received=s.sent=0;
        }
    }
    private void requestPower(Direction side,int amount) {
        configure();
        if(amount<=0 || disabled || !server() || !open(side) || !pipe.isConnected(side))return;
        step();
        if(pipe.getBehaviour() instanceof IPipeTransportRfHook hook)amount=hook.requestPower(side,amount);
        var s=sections.get(side);s.nextPowerQuery+=(int)clamp(amount,0,maxPower-s.nextPowerQuery);
    }
    private int move(Section source,Section exit,int offered) {
        if(offered<=0)return 0;
        try(var tx=Transaction.openOuter()) {
            long accepted=0;
            var other=pipe.getConnectedPipe(exit.side);
            if(other!=null && other.isConnected(exit.side.getOpposite()) && other.getFlow() instanceof PipeFlowRedstoneFlux flow) {
                accepted=flow.sections.get(exit.side.getOpposite()).insertInternal(offered,tx);
            } else {
                var target=neighbour(exit.side,true);
                if(target!=null && target.supportsInsertion())accepted=target.insert(offered,tx);
            }
            if(accepted<0 || accepted>offered)throw new IllegalStateException("Invalid native energy acceptance");
            if(accepted==0 || accepted>source.internalPower)return 0;
            snapshots.updateSnapshots(tx);
            source.internalPower-=(int)accepted;exit.sent+=(int)accepted;
            tx.commit();return (int)accepted;
        }
    }
    @Override public void onTick() {
        configure();var world=pipe.getHolder().getPipeWorld();if(world==null)return;
        if(world.isClient) {
            clientDisplayFlowCentreLast=clientDisplayFlowCentre;
            for(var s:sections.values()) {
                s.clientDisplayFlowLast=s.clientDisplayFlow;
                double delta=s.displayFlow.value*2.4*s.side.getDirection().offset();
                s.clientDisplayFlow=(s.clientDisplayFlow+16+delta)%16;
                double center=(VecUtil.getValue(clientDisplayFlowCentre,s.side.getAxis())+16+delta/2)%16;
                clientDisplayFlowCentre=VecUtil.replaceValue(clientDisplayFlowCentre,s.side.getAxis(),center);
            }
            return;
        }
        if(!server() || lastTick==world.getTime())return;
        lastTick=world.getTime();step();
        var oldFlows=new EnumFlow[6];int[] oldPower=new int[6];
        for(var s:sections.values()) {oldFlows[s.side.ordinal()]=s.displayFlow;oldPower[s.side.ordinal()]=s.displayPower;s.displayFlow=EnumFlow.STATIONARY;}
        if(!disabled) {
            for(var source:sections.values()) {
                if(source.internalPower<=0)continue;
                int total=0;for(var target:sections.values())if(target!=source && pipe.isConnected(target.side))total+=target.powerQuery;
                boolean returning=total==0 && pipe.isConnected(source.side) && source.powerQuery>0;
                if(returning)total=source.powerQuery;
                int remainingQuery=total;
                for(var target:sections.values()) {
                    if((returning?target!=source:target==source) || target.powerQuery<=0 || !pipe.isConnected(target.side))continue;
                    int offered=(int)(source.internalPower*(long)target.powerQuery/remainingQuery);
                    remainingQuery-=target.powerQuery;offered=Math.min(offered,Math.max(0,maxPower-target.sent));
                    int used=move(source,target,offered);
                    if(used>0) {
                        source.powerAverage.push(used);target.powerAverage.push(used);
                        source.displayFlow=EnumFlow.OUT;target.displayFlow=EnumFlow.IN;
                    }
                }
            }
            for(var side:Direction.values()) if(pipe.getConnectedType(side)==IPipe.ConnectedType.TILE) {
                var target=neighbour(side,true);
                if(target!=null && target.supportsInsertion()) {
                    // Ask the actual insertion policy, including sided filters and tick limits. Always roll back.
                    try(var tx=Transaction.openOuter()) {
                        long requested=target.insert(maxPower,tx);
                        if(requested<0 || requested>maxPower)throw new IllegalStateException("Invalid native energy demand");
                        requestPower(side,(int)requested);
                    }
                }
            }
            for(var side:Direction.values()) {
                var other=pipe.getConnectedPipe(side);
                if(other!=null && other.isConnected(side.getOpposite()) && other.getFlow() instanceof PipeFlowRedstoneFlux flow)
                    flow.requestPower(side.getOpposite(),Math.min(getPowerRequested(side),sections.get(side).room()));
            }
        }
        boolean changed=false;
        for(var s:sections.values()) {
            s.powerAverage.tick();s.displayPower=(int)(Math.sqrt(Math.min(1,s.powerAverage.getAverage()/maxPower))*MjAPI.MJ);
            changed|=s.displayPower!=oldPower[s.side.ordinal()] || s.displayFlow!=oldFlows[s.side.ordinal()];
        }
        if(changed)sendPayload(NET_POWER_AMOUNTS);
    }
    @Override public void writePayload(int id,PacketByteBuf buffer,EnvType side) {
        super.writePayload(id,buffer,side);
        if(side==EnvType.SERVER && (id==NET_POWER_AMOUNTS || id==NET_ID_FULL_STATE)) {
            var packet=PacketBufferBC.asPacketBufferBc(buffer);
            for(var s:sections.values()){packet.writeInt(s.displayPower);packet.writeEnumValue(s.displayFlow);}
        }
    }
    @Override public void readPayload(int id,PacketByteBuf buffer,EnvType side) throws IOException {
        super.readPayload(id,buffer,side);
        if(side!=EnvType.CLIENT || id!=NET_POWER_AMOUNTS && id!=NET_ID_FULL_STATE)return;
        var packet=PacketBufferBC.asPacketBufferBc(buffer);int[] amounts=new int[6];var flows=new EnumFlow[6];
        for(int i=0;i<6;i++) {
            amounts[i]=packet.readInt();int direction=packet.readFixedBits(2);
            if(amounts[i]<0 || amounts[i]>MjAPI.MJ || direction>=EnumFlow.values().length)throw new IOException("Invalid RF pipe snapshot");
            flows[i]=EnumFlow.values()[direction];
        }
        for(var s:sections.values()){s.displayPower=amounts[s.side.ordinal()];s.displayFlow=flows[s.side.ordinal()];}
    }
    @Override public void getDebugInfo(List<String> left,List<String> right,Direction side) {
        configure();left.add("RF: "+getStoredPower()+", limit: "+maxPower+"/t");
        left.add("Receiver: "+isReceiver+", disabled: "+disabled);
        for(var s:sections.values())left.add(s.side+": "+s.getEnergyStored()+" RF, demand "+s.powerQuery);
    }
    public final class Section implements EnergyStorage {
        public final Direction side;
        public final AverageInt clientDisplayAverage=new AverageInt(10),powerAverage=new AverageInt(1);
        public double clientDisplayFlow,clientDisplayFlowLast;
        public int displayPower;public EnumFlow displayFlow=EnumFlow.STATIONARY;
        private int powerQuery,nextPowerQuery,internalPower,internalNextPower,received,sent;
        private Section(Direction side){this.side=side;clientDisplayFlow=(side.getDirection()==Direction.AxisDirection.POSITIVE?7:1)/8.0;}
        private int room() {
            configure();int used=currentWorldTime==pipe.getHolder().getPipeWorld().getTime()?received:0;
            return Math.max(0,Math.min(capacity-internalPower-internalNextPower,maxPower-used));
        }
        private int insertInternal(int amount,TransactionContext tx) {
            configure();if(amount<=0 || disabled || !server() || !open(side))return 0;
            int accepted=Math.min(amount,room());
            if(accepted>0) {snapshots.updateSnapshots(tx);step();internalNextPower+=accepted;received+=accepted;}
            return accepted;
        }
        @Override public boolean supportsInsertion(){configure();return isReceiver && !disabled && live() && open(side);}
        @Override public boolean supportsExtraction(){return false;}
        @Override public long insert(long maximum,TransactionContext tx) {
            nonNegative(maximum);return supportsInsertion()?insertInternal((int)Math.min(maximum,MAX_RATE),tx):0;
        }
        @Override public long extract(long maximum,TransactionContext tx){nonNegative(maximum);return 0;}
        @Override public long getAmount(){return live() && open(side)?internalPower+internalNextPower:0;}
        @Override public long getCapacity(){configure();return live() && open(side)?capacity:0;}
        public boolean canReceive(){return supportsInsertion();}
        public int getEnergyStored(){return internalPower+internalNextPower;}
        public int getMaxEnergyStored(){configure();return capacity;}
    }
    public enum EnumFlow { IN(-1),OUT(1),STATIONARY(0);public final int value;EnumFlow(int value){this.value=value;} }
}
