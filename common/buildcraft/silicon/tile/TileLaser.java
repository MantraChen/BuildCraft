/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0
 * Native directional laser transport. Remake by FelixMantra.
 */
package buildcraft.silicon.tile;

import java.io.IOException;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.*;
import buildcraft.api.mj.*;
import buildcraft.api.properties.BuildCraftProperties;
import buildcraft.api.tiles.IDebuggable;
import buildcraft.lib.misc.PositionUtil;
import buildcraft.lib.misc.data.AverageLong;
import buildcraft.lib.misc.data.IdAllocator;
import buildcraft.lib.net.PacketBufferBC;
import buildcraft.lib.tile.*;

public final class TileLaser extends TileBC_Neptune implements TickingBlockEntity,IDebuggable {
    public static final IdAllocator IDS=TileBC_Neptune.IDS.makeChild("laser");
    public static final int TARGETING_RANGE=6;
    public static final long CAPACITY=1024*MjAPI.MJ,MAX_PER_TICK=4*MjAPI.MJ;
    private final List<BlockPos> targets=new ArrayList<>();
    private final AverageLong average=new AverageLong(100);
    private final Connector connector=new Connector();
    private long stored,lastTransfer,clientAverage,nextScan,nextChoose;
    private BlockPos targetPos;
    public Vec3d laserPos;
    private boolean scanNeeded=true;

    public TileLaser(BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);}
    @Override public IdAllocator getIdAllocator(){return IDS;}
    public long getStoredPower(){return stored;}
    public long getMaxPowerPerTick(){return MAX_PER_TICK;}
    public long getAverageClient(){return clientAverage;}
    public long getLastTransfer(){return lastTransfer;}
    public BlockPos getTargetPos(){return targetPos;}
    @Override public IMjConnector getMjConnector(Direction side){return isStorageAvailable()?connector:null;}
    @Override public void onLoad(){super.onLoad();scanNeeded=true;}
    @Override public void onNeighbourBlockChanged(Block block,BlockPos neighbor){super.onNeighbourBlockChanged(block,neighbor);scanNeeded=true;}
    private Direction facing(){return getCachedState().get(BuildCraftProperties.BLOCK_FACING_6);}
    private boolean inCone(BlockPos at) {
        var d=at.subtract(pos);var face=facing();
        int forward=d.getX()*face.getOffsetX()+d.getY()*face.getOffsetY()+d.getZ()*face.getOffsetZ();
        if(forward<1 || forward>TARGETING_RANGE)return false;
        return switch(face.getAxis()) {
            case X -> Math.abs(d.getY())<forward && Math.abs(d.getZ())<forward;
            case Y -> Math.abs(d.getX())<forward && Math.abs(d.getZ())<forward;
            case Z -> Math.abs(d.getX())<forward && Math.abs(d.getY())<forward;
        };
    }
    private ILaserTarget target(BlockPos at) {
        if(at==null || !inCone(at) || !world.isChunkLoaded(at) || !(world.getBlockState(at).getBlock() instanceof ILaserTargetBlock)
            || !(world.getBlockEntity(at) instanceof ILaserTarget target) || target.isInvalidTarget())return null;
        for(var between:PositionUtil.getAllOnPath(pos,at)) {
            if(between.equals(pos) || between.equals(at))continue;
            if(!world.isChunkLoaded(between) || !world.isAir(between))return null;
        }
        return target;
    }
    private void scan() {
        targets.clear();
        for(int x=-TARGETING_RANGE;x<=TARGETING_RANGE;x++)for(int y=-TARGETING_RANGE;y<=TARGETING_RANGE;y++)for(int z=-TARGETING_RANGE;z<=TARGETING_RANGE;z++) {
            BlockPos at=pos.add(x,y,z);
            if(inCone(at) && target(at)!=null)targets.add(at);
        }
        nextScan=world.getTime()+20;scanNeeded=false;
    }
    private void choose() {
        var candidates=new ArrayList<BlockPos>();
        for(var at:targets){var target=target(at);if(target!=null && target.getRequiredLaserPower()>0)candidates.add(at);}
        targetPos=candidates.isEmpty()?null:candidates.get(world.random.nextInt(candidates.size()));
        nextChoose=world.getTime()+10+world.random.nextInt(21);
    }
    @Override public void tick() {
        if(world==null || !isStorageAvailable())return;
        if(world.isClient) {
            if(targetPos==null || lastTransfer<=0)laserPos=null;
            else if(laserPos==null || world.getTime()%5==0)
                laserPos=Vec3d.of(targetPos).add((5.5+world.random.nextInt(6))/16,.5625,(5.5+world.random.nextInt(6))/16);
            return;
        }
        average.tick();BlockPos before=targetPos;boolean wasFiring=lastTransfer>0;lastTransfer=0;
        if(scanNeeded || world.getTime()>=nextScan)scan();
        var target=target(targetPos);
        if(target==null || target.getRequiredLaserPower()<=0 || world.getTime()>=nextChoose){choose();target=target(targetPos);}
        if(target!=null && stored>0) {
            long ramp=MAX_PER_TICK*(stored+MAX_PER_TICK)/(CAPACITY/2);
            long sent=Math.min(stored,Math.min(MAX_PER_TICK,Math.min(ramp,Math.max(0,target.getRequiredLaserPower()))));
            if(sent>0) {
                long excess=target.receiveLaserPower(sent);
                if(excess<0 || excess>sent)throw new IllegalStateException("Invalid laser target energy remainder");
                lastTransfer=sent-excess;stored-=lastTransfer;
                if(lastTransfer>0){average.push(lastTransfer);markDirty();}
            }
        }
        if(!Objects.equals(before,targetPos) || wasFiring!=(lastTransfer>0) || world.getTime()%5==0)
            sendNetworkUpdate(NET_RENDER_DATA);
    }
    private final class Connector implements IMjReceiver,IMjReadable {
        @Override public boolean canConnect(IMjConnector other){return isStorageAvailable() && other!=null;}
        @Override public boolean canReceive(){return isStorageAvailable() && world!=null && !world.isClient;}
        @Override public long getPowerRequested(){return canReceive()?CAPACITY-stored:0;}
        @Override public long receivePower(long amount,boolean simulate) {
            if(amount<0)throw new IllegalArgumentException("Negative laser energy");
            long accepted=Math.min(amount,getPowerRequested());
            if(!simulate && accepted>0){stored+=accepted;markDirty();}
            return amount-accepted;
        }
        @Override public long getStored(){return isStorageAvailable()?stored:0;}
        @Override public long getCapacity(){return isStorageAvailable()?CAPACITY:0;}
    }
    @Override public void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);var battery=new NbtCompound();battery.putLong("stored",stored);nbt.put("battery",battery);
    }
    @Override public void readFromNBT(NbtCompound nbt) {
        super.readFromNBT(nbt);
        stored=Math.max(0,Math.min(CAPACITY,nbt.getCompound(nbt.contains("battery")?"battery":"mj_battery").getLong("stored")));
        targetPos=null;lastTransfer=0;laserPos=null;scanNeeded=true;
    }
    @Override public void writePayload(int id,PacketBufferBC buffer,NetSide side) {
        super.writePayload(id,buffer,side);
        if(side==NetSide.SERVER && id==NET_RENDER_DATA) {
            buffer.writeLong(stored);buffer.writeBoolean(targetPos!=null);
            if(targetPos!=null)buffer.writeBlockPos(targetPos);
            buffer.writeLong(lastTransfer);buffer.writeLong(Math.max(0,Math.min(MAX_PER_TICK,Math.round(average.getAverage()))));
        }
    }
    @Override public void readPayload(int id,PacketBufferBC buffer,NetSide side,Object context) throws IOException {
        super.readPayload(id,buffer,side,context);
        if(side==NetSide.CLIENT && id==NET_RENDER_DATA) {
            long energy=buffer.readLong();BlockPos at=buffer.readBoolean()?buffer.readBlockPos():null;
            long sent=buffer.readLong(),avg=buffer.readLong();
            if(energy<0 || energy>CAPACITY || sent<0 || sent>MAX_PER_TICK || avg<0 || avg>MAX_PER_TICK
                || at!=null && !inCone(at) || at==null && sent!=0)throw new IOException("Invalid laser state");
            stored=energy;targetPos=at;lastTransfer=sent;clientAverage=avg;
            if(at==null || sent==0)laserPos=null;
        }
    }
    @Override public void getDebugInfo(List<String> left,List<String> right,Direction side) {
        left.add("MJ = "+stored+"/"+CAPACITY);left.add("laser target = "+targetPos);left.add("sent = "+lastTransfer);
    }
}
