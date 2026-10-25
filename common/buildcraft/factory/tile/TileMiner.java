/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 *
 * Ported to Fabric 1.20.1 by R.Chen (https://github.com/MantraChen).
 */
package buildcraft.factory.tile;

import java.io.IOException;
import java.util.List;

import javax.annotation.Nonnull;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import buildcraft.api.core.EnumPipePart;
import buildcraft.api.mj.IMjReceiver;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.mj.MjBattery;
import buildcraft.api.mj.MjCapabilityHelper;
import buildcraft.api.tiles.IDebuggable;

import buildcraft.lib.migrate.BCVersion;
import buildcraft.lib.misc.LocaleUtil;
import buildcraft.lib.misc.data.IdAllocator;
import buildcraft.lib.net.PacketBufferBC;
import buildcraft.lib.tile.TileBC_Neptune;
import buildcraft.lib.tile.TileBC_Neptune.NetSide;

/** Shared native shaft, energy, persistence and render synchronization. Remake by FelixMantra. */
public abstract class TileMiner extends TileBC_Neptune implements buildcraft.lib.tile.TickingBlockEntity, IDebuggable, buildcraft.api.tiles.IHasWork {
    public static final IdAllocator IDS = TileBC_Neptune.IDS.makeChild("miner");
    public static final int NET_LED_STATUS = IDS.allocId("LED_STATUS");
    public static final int NET_WANTED_Y = IDS.allocId("WANTED_Y");

    protected long progress = 0;
    protected BlockPos currentPos = null;

    private int wantedLength = 0;
    private int tubeReach;
    private double currentLength = 0;
    private double lastLength = 0;
    private int offset;

    protected boolean isComplete = false;
    protected final MjBattery battery = new MjBattery(getBatteryCapacity());

    public TileMiner(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        caps.addProvider(new MjCapabilityHelper(createMjReceiver()));
    }

    @Override
    public boolean hasWork() { return isStorageAvailable() && !isComplete(); }

    public long getStoredPower() { return battery.getStored(); }
    public long getPaidWork() { return progress; }
    public BlockPos getCurrentPos() { return currentPos; }
    public int getWantedLength() { return wantedLength; }

    protected abstract void mine();

    protected abstract IMjReceiver createMjReceiver();

    /** BlockEntityTicker wired by the owning Block.getTicker(). Replaces ITickable.update(). */
    @SuppressWarnings("unchecked")
    public static <T extends TileMiner> BlockEntityTicker<T> ticker() {
        return (world, pos, state, be) -> be.tick();
    }

    /** Ticking logic. Replaces {@code ITickable.update()}. */
    public void tick() {
        if (!isStorageAvailable()) return;
        if (world.isClient) {
            lastLength = currentLength;
            if (Math.abs(wantedLength - currentLength) <= 0.01) {
                currentLength = wantedLength;
            } else {
                currentLength = currentLength + (wantedLength - currentLength) / 7D;
            }
            return;
        }

        long before=battery.getStored();
        battery.tick(getWorld(), getPos());
        if (before!=battery.getStored()) markChunkDirty();

        if (world.getTime() % 10 == offset) {
            sendNetworkUpdate(NET_LED_STATUS);
        }

        mine();
    }

    @Override
    public IdAllocator getIdAllocator() {
        return IDS;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if(world!=null)offset = world.random.nextInt(10);
    }

    @Override
    public void onRemove() {
        if (!isStorageAvailable() || world.isClient) return;
        super.onRemove();
        retractTubes(0);
    }

    protected int maxShaftLength() {
        return world==null ? 4096 : Math.max(0,Math.min(4096,pos.getY()-world.getBottomY()));
    }

    protected int miningDepth() {
        return Math.min(maxShaftLength(),Math.max(0,buildcraft.core.BCCoreConfig.miningMaxDepth));
    }

    /** Repair only the shaft's replaceable cells; never erase an intervening solid block. */
    protected boolean updateLength() {
        if (world==null || world.isClient) return false;
        BlockPos target=getTargetPos();
        int length=target==null?0:pos.getY()-target.getY();
        if (length<0 || length>maxShaftLength()) length=0;
        boolean success=true;
        int reach=Math.max(0,length-1);
        for (int depth=1;depth<=reach;depth++) {
            BlockPos cell=pos.down(depth);
            if (!isTubeBlock(world,cell) && !placeTube(cell)) {success=false;length=0;reach=0;break;}
            tubeReach=Math.max(tubeReach,depth);
        }
        retractTubes(reach);
        if (wantedLength!=length) {
            currentLength=wantedLength=length;
            sendNetworkUpdate(NET_WANTED_Y);
        }
        markChunkDirty();
        return success;
    }

    private boolean placeTube(BlockPos cell) {
        if (!world.isInBuildLimit(cell) || !world.getWorldBorder().contains(cell) || !world.isChunkLoaded(cell)) return false;
        var state=world.getBlockState(cell);
        boolean water=state.getBlock() instanceof net.minecraft.block.FluidBlock
            && net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes.getViscosity(
                net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant.of(state.getFluidState().getFluid()),world)<=1000;
        if (!state.isAir() && !water) return false;
        var player=buildcraft.lib.misc.FakePlayerProvider.INSTANCE.getFakePlayer((net.minecraft.server.world.ServerWorld)world,getOwner(),cell);
        if (!world.canPlayerModifyAt(player,cell)) return false;
        var previous=player.getMainHandStack();
        player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,net.minecraft.item.ItemStack.EMPTY);
        try {
            var hit=new net.minecraft.util.hit.BlockHitResult(net.minecraft.util.math.Vec3d.ofCenter(cell),Direction.UP,cell,false);
            if (net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.invoker().interact(
                    player,world,net.minecraft.util.Hand.MAIN_HAND,hit)!=net.minecraft.util.ActionResult.PASS) return false;
            return world.setBlockState(cell,buildcraft.factory.BCFactoryBlocks.tube.getDefaultState());
        } finally {player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,previous);}
    }

    private void retractTubes(int retain) {
        if(world==null || world.isClient || !world.isChunkLoaded(pos))return;
        for(int depth=retain+1;depth<=Math.min(tubeReach,maxShaftLength());depth++) {
            BlockPos cell=pos.down(depth);
            if(world.getBlockEntity(cell) instanceof TileMiner)break;
            if(isTubeBlock(world,cell))world.removeBlock(cell,false);
        }
        tubeReach=retain;
    }

    protected boolean isTubeBlock(net.minecraft.world.World level,BlockPos cell) {
        return level.getBlockState(cell).isOf(buildcraft.factory.BCFactoryBlocks.tube);
    }

    protected boolean validWorkPosition(BlockPos cell) {
        return cell!=null && Math.abs((long)cell.getX())<=30_000_000L && Math.abs((long)cell.getZ())<=30_000_000L
            && (world==null ? Math.abs((long)cell.getY())<=4096 : world.isInBuildLimit(cell) && world.getWorldBorder().contains(cell));
    }

    protected BlockPos getTargetPos() {
        return currentPos;
    }

    public double getLength(float partialTicks) {
        if (partialTicks <= 0) {
            return lastLength;
        } else if (partialTicks >= 1) {
            return currentLength;
        } else {
            return lastLength * (1 - partialTicks) + currentLength * partialTicks;
        }
    }

    public boolean isComplete() {
        return world!=null && world.isClient ? isComplete : currentPos == null;
    }

    @Override
    protected void migrateOldNBT(int version, NbtCompound nbt) {
        super.migrateOldNBT(version, nbt);
        if (version == BCVersion.BEFORE_RECORDS.dataVersion || version == BCVersion.v7_2_0_pre_12.dataVersion) {
            NbtCompound oldBattery = nbt.getCompound("battery");
            int energy = oldBattery.getInt("energy");
            battery.extractPower(0, Integer.MAX_VALUE);
            battery.addPower(energy * 100, false);
        }
    }

    @Override
    public void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        if (currentPos != null) {
            nbt.put("currentPos", NbtHelper.fromBlockPos(currentPos));
        }
        nbt.putInt("wantedLength", wantedLength);
        nbt.putLong("progress", progress);
        nbt.putInt("tubeReach",tubeReach);
        nbt.put("battery", battery.writeToNbt());
    }

    @Override
    public void readFromNBT(NbtCompound nbt) {
        super.readFromNBT(nbt);
        currentPos=nbt.contains("currentPos")?NbtHelper.toBlockPos(nbt.getCompound("currentPos")):null;
        if (!validWorkPosition(currentPos))currentPos=null;
        wantedLength=Math.max(0,Math.min(maxShaftLength(),nbt.getInt("wantedLength")));
        currentLength=lastLength=wantedLength;
        tubeReach=Math.max(0,Math.min(maxShaftLength(),nbt.contains("tubeReach")?nbt.getInt("tubeReach"):wantedLength-1));
        progress=currentPos==null?0:Math.max(0,nbt.getLong("progress"));
        // TODO(R.Chen): remove legacy mj_battery migration once all worlds are updated
        if (nbt.contains("mj_battery")) {
            nbt.put("battery", nbt.get("mj_battery"));
        }
        battery.readFromNbt(nbt.getCompound("battery"));
    }

    // Networking

    @Override
    public void writePayload(int id, PacketBufferBC buffer, NetSide side) {
        super.writePayload(id, buffer, side);
        if (side == NetSide.SERVER) {
            if (id == NET_RENDER_DATA) {
                writePayload(NET_LED_STATUS, buffer, side);
                buffer.writeInt(wantedLength);
            } else if (id == NET_LED_STATUS) {
                buffer.writeBoolean(isComplete());
                battery.writeToBuffer(buffer);
            } else if (id == NET_WANTED_Y) {
                buffer.writeInt(wantedLength);
            }
        }
    }

    @Override
    public void readPayload(int id, PacketBufferBC buffer, NetSide side, /* STUB(R.Chen): MessageContext */ Object ctx)
        throws IOException {
        super.readPayload(id, buffer, side, ctx);
        if (side==NetSide.CLIENT) {
            if (id==NET_RENDER_DATA || id==NET_LED_STATUS) {
                boolean complete=buffer.readBoolean();
                long energy=buffer.readLong();
                int length=id==NET_RENDER_DATA?buffer.readInt():wantedLength;
                if (energy<0 || length<0 || length>maxShaftLength())throw new IOException("Invalid miner render state");
                isComplete=complete;
                var tag=new NbtCompound();tag.putLong("stored",energy);battery.readFromNbt(tag);
                if(id==NET_RENDER_DATA)currentLength=lastLength=wantedLength=length;
            } else if (id==NET_WANTED_Y) {
                int length=buffer.readInt();
                if(length<0 || length>maxShaftLength())throw new IOException("Invalid miner shaft length");
                wantedLength=length;
            }
        }
    }

    @Override
    public void getDebugInfo(List<String> left, List<String> right, Direction side) {
        left.add("battery = " + battery.getDebugString());
        left.add("current = " + currentPos);
        left.add("wantedLength = " + wantedLength);
        left.add("currentLength = " + currentLength);
        left.add("lastLength = " + lastLength);
        left.add("isComplete = " + isComplete());
        left.add("progress = " + LocaleUtil.localizeMj(progress));
    }

    // Rendering — FRAPI/BER-based; the Forge TESR helpers (getRenderBoundingBox,
    // getMaxRenderDistanceSquared, hasFastRenderer) have no direct Fabric equivalent.

    @Environment(EnvType.CLIENT)
    public float getPercentFilledForRender() {
        float val = battery.getStored() / (float) battery.getCapacity();
        return val < 0 ? 0 : val > 1 ? 1 : val;
    }

    protected long getBatteryCapacity() {
        return 500 * MjAPI.MJ;
    }
}
