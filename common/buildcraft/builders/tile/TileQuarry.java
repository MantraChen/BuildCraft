/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 *
 * Ported to Fabric 1.20.1 by R.Chen (https://github.com/MantraChen).
 */

package buildcraft.builders.tile;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Direction.Axis;
import net.minecraft.util.math.Vec3d;

import buildcraft.api.core.BCDebugging;
import buildcraft.api.core.IAreaProvider;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.mj.MjBattery;
import buildcraft.api.mj.MjCapabilityHelper;
import buildcraft.api.tiles.IDebuggable;

import buildcraft.lib.block.BlockBCBase_Neptune;
import buildcraft.lib.chunkload.ChunkLoaderManager;
import buildcraft.lib.chunkload.IChunkLoadingTile;
import buildcraft.lib.client.render.DetachedRenderer;
import buildcraft.lib.misc.AdvancementUtil;
import buildcraft.lib.misc.BlockUtil;
import buildcraft.lib.misc.InventoryUtil;
import buildcraft.lib.misc.LocaleUtil;
import buildcraft.lib.misc.MathUtil;
import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.lib.misc.VecUtil;
import buildcraft.lib.misc.data.AxisOrder;
import buildcraft.lib.misc.data.Box;
import buildcraft.lib.misc.data.BoxIterator;
import buildcraft.lib.misc.data.EnumAxisOrder;
import buildcraft.lib.mj.MjBatteryReceiver;
import buildcraft.lib.net.PacketBufferBC;
import buildcraft.lib.tile.TileBC_Neptune;
import buildcraft.lib.tile.TileBC_Neptune.NetSide;

/** Native powered excavation, frame repair and persistence. Remake by FelixMantra. */
public class TileQuarry extends TileBC_Neptune implements buildcraft.lib.tile.TickingBlockEntity, IDebuggable, IChunkLoadingTile {
    public static final boolean DEBUG_QUARRY = BCDebugging.shouldDebugLog("builders.quarry");
    private static final long MAX_POWER_PER_TICK = 512 * MjAPI.MJ;
    private static final Identifier ADVANCEMENT_COMPLETE
        = new Identifier("buildcraftbuilders:diggy_diggy_hole");

    private final MjBattery battery = new MjBattery(24000 * MjAPI.MJ);
    public final Box frameBox = new Box();
    private final Box miningBox = new Box();
    private BoxIterator boxIterator;
    public final List<BlockPos> framePoses = new ArrayList<>();
    private int frameBoxPosesCount = 0;
    private BoxIterator frameIterator;
    private boolean miningFinished, performingWork;
    private int scanBudget;
    private static final int MAX_PENDING_FRAME_WORK = 1024;
    private boolean firstChecked = false;
    private final Set<BlockPos> frameBreakBlockPoses = new TreeSet<>(
        BlockUtil.uniqueBlockPosComparator(Comparator.comparingDouble(p -> getPos().getSquaredDistance(p)))
    );
    private final Set<BlockPos> framePlaceFramePoses = new HashSet<>();
    public Task currentTask = null;
    public Vec3d drillPos;
    public Vec3d clientDrillPos;
    public Vec3d prevClientDrillPos;
    private long debugPowerRate = 0;
    private double blockPercentSoFar;
    private double moveDistanceSoFar;
    private boolean firstServerTick = true;
    private boolean legacyMiningBounds;

    public TileQuarry(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        caps.addProvider(new MjCapabilityHelper(new MjBatteryReceiver(battery) {
            @Override public boolean canReceive() { return isStorageAvailable(); }
            @Override public long getPowerRequested() { return canReceive()?super.getPowerRequested():0; }
            @Override public long receivePower(long amount,boolean simulate) {
                if(amount<0 || !canReceive() || world.isClient)return amount;
                long remainder=super.receivePower(amount,simulate);
                if(!simulate && remainder!=amount)markDirty();
                return remainder;
            }
        }));
    }

    @Nonnull
    private BoxIterator createBoxIterator() {
        long x = getPos().getX();
        long y = getPos().getY();
        long z = getPos().getZ();
        long seed = ((x & 0xFFFF) << 0) | ((y & 0xFFFF) << 16) | ((z & 0xFFFF) << 32);

        Random rand = new Random(seed);
        EnumAxisOrder axisOrder = rand.nextBoolean() ? EnumAxisOrder.XZY : EnumAxisOrder.ZXY;
        AxisOrder.Inversion inv = AxisOrder.Inversion.getFor(rand.nextBoolean(), rand.nextBoolean(), false);
        return new BoxIterator(miningBox, AxisOrder.getFor(axisOrder, inv), true);
    }

    /** Gets the current positions where frame blocks should be placed, in order.
     * <p>
     * Assumes that {@link #frameBox} is correct for the current position. Does not take into account the current facing
     * of the quarry, as that is assumed to be involved in the {@link #frameBox} itself.
     *
     * @return An ordered list of the positions that the frame should be placed in. The list is in placement order.
     * @throws IllegalStateException if something went wrong during iteration, or the current {@link #frameBox} was
     *             incorrect compared to {@link #getPos()} */
    private List<BlockPos> getFramePositions() {
        Set<BlockPos> visitedSet = new HashSet<>();
        List<BlockPos> framePositions = new ArrayList<>();

        List<BlockPos> openSet = new ArrayList<>();
        List<BlockPos> nextOpenSet = new ArrayList<>();

        openSet.add(getPos());

        Direction[] order = Direction.values();
        List<Direction> orderAsList = Arrays.asList(order);

        int maxIterationCount = frameBox.getBlocksOnEdgeCount();
        int iterationCount = 0;
        do {
            for (BlockPos p : openSet) {
                Collections.shuffle(orderAsList);
                for (Direction face : order) {
                    BlockPos next = p.offset(face);
                    if (frameBox.isOnEdge(next) && visitedSet.add(next)) {
                        nextOpenSet.add(next);
                        framePositions.add(next);
                    }
                }
            }
            openSet.clear();
            List<BlockPos> t = openSet;
            openSet = nextOpenSet;
            nextOpenSet = t;

            Collections.shuffle(openSet);

            if (openSet.size() > 8 * 3) {
                String msg = "OpenSet got too big!";
                msg += "\n  Position = " + pos;
                msg += "\n  Frame Box = " + frameBox;
                msg += "\n  Iteration Count = " + iterationCount;
                msg += "\n  OpenSet = " + openSet.stream().map(Object::toString).collect(
                    Collectors.joining("\n  ", "[", "]")
                );
                throw new IllegalStateException(msg);
            }

            iterationCount++;
            if (iterationCount >= maxIterationCount) {
                String msg = "Failed to generate a correct list of frame positions! Was the frame box wrong?";
                msg += "\n  Position = " + pos;
                msg += "\n  Frame Box = " + frameBox;
                msg += "\n  Iteration Count = " + iterationCount;
                msg += "\n  OpenSet = " + openSet.stream().map(Object::toString).collect(
                    Collectors.joining("\n  ", "[", "]")
                );
                throw new IllegalStateException(msg);
            }
        } while (!openSet.isEmpty());

        if (framePositions.isEmpty()) {
            String msg = "Failed to generate a correct list of frame positions! Was the frame box wrong?";
            msg += "\n  Position = " + pos;
            msg += "\n  Frame Box = " + frameBox;
            throw new IllegalStateException(msg);
        }

        return framePositions;
    }

    private boolean shouldBeFrame(BlockPos p) {
        return frameBox.isOnEdge(p);
    }

    @Override
    public void onPlacedBy(LivingEntity placer, ItemStack stack) {
        super.onPlacedBy(placer, stack);
        if (world == null || world.isClient) return;
        Direction back = getCachedState().get(BlockBCBase_Neptune.PROP_FACING).getOpposite();
        BlockPos areaPos = pos.offset(back);
        IAreaProvider chosen = null;
        if (world.isChunkLoaded(areaPos) && world.getBlockEntity(areaPos) instanceof IAreaProvider provider
                && usableArea(provider.min(), provider.max(), areaPos)) chosen = provider;
        if (chosen == null) {
            var cache = buildcraft.core.marker.VolumeCache.INSTANCE.getSubCache(world);
            for (BlockPos markerPos : List.copyOf(cache.getAllMarkers())) {
                var marker = cache.getMarker(markerPos);
                if (marker instanceof buildcraft.core.tile.TileMarkerVolume volume
                        && volume.getCurrentConnection() != null && usableArea(volume.min(), volume.max(), areaPos)) {
                    chosen = volume; break;
                }
            }
        }
        BlockPos min, max;
        if (chosen != null) {
            min = chosen.min(); max = chosen.max();
        } else if (back == Direction.EAST || back == Direction.WEST) {
            min = pos.add(back == Direction.EAST ? 1 : -11, 0, -5);
            max = pos.add(back == Direction.EAST ? 11 : -1, 4, 5);
        } else {
            min = pos.add(-5, 0, back == Direction.SOUTH ? 1 : -11);
            max = pos.add(5, 4, back == Direction.SOUTH ? 11 : -1);
        }
        int height = Math.min(world.getHeight()-1, Math.max(max.getY()-min.getY(),
            buildcraft.builders.BCBuildersConfig.quarryFrameMinHeight));
        int y = Math.max(world.getBottomY(), Math.min(min.getY(), world.getTopY()-1-height));
        frameBox.reset(); frameBox.setMin(new BlockPos(min.getX(),y,min.getZ()));
        frameBox.setMax(new BlockPos(max.getX(),y+height,max.getZ()));
        miningBox.reset();
        int bottom = Math.max(world.getBottomY(), frameBox.max().getY()-1-
            Math.max(1,buildcraft.core.BCCoreConfig.miningMaxDepth));
        miningBox.setMin(new BlockPos(min.getX()+1,bottom,min.getZ()+1));
        miningBox.setMax(new BlockPos(max.getX()-1,frameBox.max().getY()-1,max.getZ()-1));
        if (!validBounds()) { frameBox.reset(); miningBox.reset(); return; }
        if (chosen != null) chosen.removeFromWorld();
        boxIterator=null; currentTask=null; drillPos=null; miningFinished=false;
        updatePoses(); markDirty(); sendNetworkUpdate(NET_RENDER_DATA);
    }

    private boolean usableArea(BlockPos min, BlockPos max, BlockPos adjacent) {
        if (min==null || max==null || max.getX()-min.getX()<3 || max.getZ()-min.getZ()<3
                || max.getX()-min.getX()>256 || max.getZ()-min.getZ()>256 || min.getY()!=pos.getY()) return false;
        Box box = new Box(min,max);
        return !box.contains(pos) && box.contains(adjacent)
            && (adjacent.getX()==min.getX() || adjacent.getX()==max.getX()
                || adjacent.getZ()==min.getZ() || adjacent.getZ()==max.getZ());
    }

    private boolean loaded(BlockPos target) {
        return target!=null && world!=null && world.isInBuildLimit(target)
            && world.getWorldBorder().contains(target) && world.isChunkLoaded(target);
    }

    private boolean canMine(BlockPos target) {
        if (!loaded(target)) return false;
        var state=world.getBlockState(target);
        return !state.isAir() && !(state.getBlock() instanceof net.minecraft.block.FluidBlock)
            && state.getHardness(world,target)>=0;
    }

    private boolean canMoveThrough(BlockPos target) {
        if (!loaded(target)) return false;
        var state=world.getBlockState(target);
        if (state.isAir()) return true;
        if (!(state.getBlock() instanceof net.minecraft.block.FluidBlock)) return false;
        return net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes.getViscosity(
            net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant.of(state.getFluidState().getFluid()),world)<=1000;
    }

    private boolean canMoveDownTo(BlockPos target) {
        for (int y=miningBox.max().getY();y>target.getY();y--)
            if (!canMoveThrough(new BlockPos(target.getX(),y,target.getZ()))) return false;
        return true;
    }

    private boolean canIgnoreInFrameBox(BlockPos target) {
        var state=world.getBlockState(target);
        return !state.isAir() && !(state.getBlock() instanceof net.minecraft.block.FluidBlock);
    }

    private void check(BlockPos target) {
        if (target==null || !frameBox.contains(target) || !loaded(target)) return;
        frameBreakBlockPoses.remove(target); framePlaceFramePoses.remove(target);
        if (frameBreakBlockPoses.size()+framePlaceFramePoses.size()>=MAX_PENDING_FRAME_WORK) return;
        if (shouldBeFrame(target)) {
            if (!world.getBlockState(target).isOf(buildcraft.builders.BCBuildersBlocks.frame)) {
                if (canIgnoreInFrameBox(target)) frameBreakBlockPoses.add(target.toImmutable());
                else framePlaceFramePoses.add(target.toImmutable());
            }
        } else if (canIgnoreInFrameBox(target)) frameBreakBlockPoses.add(target.toImmutable());
    }

    private void scanFrame() {
        if (frameIterator==null) return;
        for (int i=0;i<(firstChecked?10:500);i++) {
            if (frameBreakBlockPoses.size()+framePlaceFramePoses.size()>=MAX_PENDING_FRAME_WORK) return;
            BlockPos target=frameIterator.getCurrent();
            if (!loaded(target)) return;
            check(target);
            if (frameIterator.advance()==null) {
                firstChecked=true;
                frameIterator=new BoxIterator(frameBox,AxisOrder.getFor(EnumAxisOrder.XZY,AxisOrder.Inversion.NNN),false);
                break;
            }
        }
    }

    /** Native block changes revisit already-scanned mining positions and repair missing frames. */
    public void onWorldBlockChanged(BlockPos changed) {
        if (world==null || world.isClient || isRemoved() || performingWork) return;
        if (frameBox.isInitialized() && frameBox.contains(changed)) check(changed);
        if (miningBox.isInitialized() && miningBox.contains(changed) && boxIterator!=null
                && (miningFinished || boxIterator.hasVisited(changed))) {
            boxIterator.moveTo(changed); boxIterator.advance(); miningFinished=false;
        }
    }

    public boolean isMiningFinished() { return miningFinished; }
    public Box getMiningBox() { return miningBox; }
    public long getStoredPower() { return battery.getStored(); }

    @Override
    public void onLoad() {
        super.onLoad();
        buildcraft.builders.BCBuildersEventDist.INSTANCE.validateQuarry(this);
        if (!world.isClient) {
            updatePoses();
        }
    }

    @Override
    public void cancelRemoval() {
        super.cancelRemoval();
        buildcraft.builders.BCBuildersEventDist.INSTANCE.validateQuarry(this);
    }

    @Override
    public void markRemoved() {
        super.markRemoved();
        buildcraft.builders.BCBuildersEventDist.INSTANCE.invalidateQuarry(this);
        if (world != null && !world.isClient) {
            ChunkLoaderManager.releaseChunksFor(this);
        }
    }

    @Override public void onRemove() {
        if(world==null || world.isClient || !isStorageAvailable())return;
        ChunkLoaderManager.forget(this);
        buildcraft.builders.BCBuildersEventDist.INSTANCE.invalidateQuarry(this);
        super.onRemove();
        if(currentTask instanceof TaskBreakBlock task)world.setBlockBreakingInfo(task.breakPos.hashCode(),task.breakPos,-1);
        for(var frame:List.copyOf(framePoses))if(world.isChunkLoaded(frame)
            && world.getBlockState(frame).isOf(buildcraft.builders.BCBuildersBlocks.frame))world.removeBlock(frame,false);
    }

    @Nullable
    @Override
    public LoadType getLoadType() {
        return validBounds() ? LoadType.HARD : null;
    }

    @Nullable
    @Override
    public Set<ChunkPos> getChunksToLoad() {
        if (!miningBox.isInitialized()) {
            return null;
        }
        Set<ChunkPos> chunkPoses = new HashSet<>();
        ChunkPos minChunkPos = new ChunkPos(frameBox.min());
        ChunkPos maxChunkPos = new ChunkPos(frameBox.max());
        for (int x = minChunkPos.x; x <= maxChunkPos.x; x++) {
            for (int z = minChunkPos.z; z <= maxChunkPos.z; z++) {
                chunkPoses.add(new ChunkPos(x, z));
            }
        }
        return chunkPoses;
    }

    private void updatePoses() {
        framePoses.clear();
        frameBoxPosesCount = 0;
        frameIterator = null;
        firstChecked = false;
        frameBreakBlockPoses.clear();
        framePlaceFramePoses.clear();
        if (world!=null && frameBox.isInitialized() && miningBox.isInitialized() && validBounds()
                && getCachedState().isOf(buildcraft.builders.BCBuildersBlocks.quarry)) {
            var size=frameBox.size(); frameBoxPosesCount=size.getX()*size.getY()*size.getZ();
            frameIterator=new BoxIterator(frameBox,AxisOrder.getFor(EnumAxisOrder.XZY,AxisOrder.Inversion.NNN),false);
            framePoses.addAll(getFramePositions());
            ChunkLoaderManager.loadChunksForTile(this);
        }
    }

    // ITickable.update() → BlockEntityTicker. The owning Block must return this ticker from getTicker(...).
    public static <T extends TileQuarry> BlockEntityTicker<T> ticker() {
        return (world, pos, state, be) -> be.tick();
    }

    public void tick() {
        if (world==null || isRemoved()) return;

        if (world.isClient) {
            prevClientDrillPos = clientDrillPos;
            clientDrillPos = drillPos;
            if (currentTask != null) {
                currentTask.clientTick();
            }
            return;
        }

        if (firstServerTick) {
            firstServerTick = false;
            if (legacyMiningBounds && miningBox.isInitialized() && miningBox.min().getY()==0) {
                int bottom=Math.max(world.getBottomY(),miningBox.max().getY()-Math.max(1,buildcraft.core.BCCoreConfig.miningMaxDepth));
                if (bottom<0) {
                    miningBox.setMin(miningBox.min().withY(bottom));
                    boxIterator=null; miningFinished=false;
                }
            }
            legacyMiningBounds=false;
            if (!validBounds()) {
                frameBox.reset(); miningBox.reset(); currentTask=null; drillPos=null;
                ChunkLoaderManager.forget(this);
            }
            updatePoses();
        }

        if (!frameBox.isInitialized() || !miningBox.isInitialized()) {
            return;
        }

        scanFrame();
        scanBudget=256;

        long max;
        if (battery.getStored() > battery.getCapacity() / 2) {
            max = MAX_POWER_PER_TICK;
        } else {
            long roundedUp = battery.getStored() + MjAPI.MJ / 2;
            if (roundedUp > Long.MAX_VALUE / MAX_POWER_PER_TICK) {
                // The multiplication would overflow, so we'll have to use BigInteger for this bit
                max = BigInteger.valueOf(roundedUp).multiply(BigInteger.valueOf(MAX_POWER_PER_TICK))
                    .divide(BigInteger.valueOf(battery.getCapacity() / 2)).longValue();
            } else {
                max = MAX_POWER_PER_TICK * roundedUp / (battery.getCapacity() / 2);
            }
            max = MathUtil.clamp(max, 0, MAX_POWER_PER_TICK);
        }
        debugPowerRate = max;
        blockPercentSoFar = 0;
        moveDistanceSoFar = 0;

        int maxTasks = Math.max(1, (int) (max * buildcraft.builders.BCBuildersConfig.quarryMaxTasksPerTick / MAX_POWER_PER_TICK));
        boolean sendUpdate = false;
        power_loop: for (int i = 0; i < maxTasks; i++) {

            if (currentTask != null) {
                if(currentTask instanceof TaskBreakBlock task && !canMine(task.breakPos)) {
                    if(!loaded(task.breakPos))break;
                    battery.addPower(currentTask.power,false);
                    world.setBlockBreakingInfo(task.breakPos.hashCode(),task.breakPos,-1);
                    currentTask=null;check(task.breakPos);sendUpdate=true;continue;
                }
                long needed = currentTask.getRequiredPowerThisTick();
                long added;
                final int mult = buildcraft.builders.BCBuildersConfig.quarryTaskPowerDivisor;
                if (mult > 0) {
                    long nNeeded = (needed * (mult + i) + mult - 1) / mult;
                    long power = battery.extractPower(0, Math.min(max, nNeeded));
                    max -= power;
                    added = power * mult / (mult + i);

                } else {
                    added = battery.extractPower(0, Math.min(max, needed));
                    max -= added;
                }
                if (currentTask.addPower(added)) {
                    currentTask = null;
                } else {
                    sendUpdate = true;
                    break;
                }
            }

            if (!frameBreakBlockPoses.isEmpty()) {
                BlockPos blockPos = frameBreakBlockPoses.iterator().next();
                if (canMine(blockPos)) {
                    drillPos = null;
                    currentTask = new TaskBreakBlock(blockPos);
                    sendUpdate = true;
                }
                check(blockPos);
                continue power_loop;
            }

            if (!framePlaceFramePoses.isEmpty()) {
                for (BlockPos blockPos : framePoses) {
                    if (!framePlaceFramePoses.contains(blockPos)) {
                        continue;
                    }
                    check(blockPos);
                    if (!framePlaceFramePoses.contains(blockPos)) {
                        continue;
                    }
                    drillPos = null;
                    currentTask = new TaskAddFrame(blockPos);
                    sendUpdate = true;
                    continue power_loop;
                }
            }

            if (!firstChecked || miningFinished) break;
            if (boxIterator==null || drillPos==null) {
                boxIterator=createBoxIterator();
                drillPos=Vec3d.of(miningBox.closestInsideTo(pos));
            }
            while (boxIterator.hasNext() && scanBudget>0) {
                BlockPos target=boxIterator.getCurrent();
                if (!loaded(target)) break power_loop;
                scanBudget--;
                if (canMine(target) && canMoveDownTo(target)) break;
                boxIterator.advance();
            }
            if (!boxIterator.hasNext()) {
                miningFinished=true; sendUpdate=true;
                if (miningBox.size().getX()==63 && miningBox.size().getZ()==63 && getOwner()!=null)
                    AdvancementUtil.unlockAdvancement(((ServerWorld)world).getServer().getPlayerManager().getPlayer(getOwner().getId()),ADVANCEMENT_COMPLETE);
                break;
            }
            if (scanBudget<=0) break;
            BlockPos target=boxIterator.getCurrent();
            currentTask=drillPos.squaredDistanceTo(Vec3d.of(target))>=1
                ?new TaskMoveDrill(drillPos,Vec3d.of(target)):new TaskBreakBlock(target);
            sendUpdate=true;
        }
        debugPowerRate -= max;
        if (sendUpdate) {
            sendNetworkUpdate(NET_RENDER_DATA);
        }
        markChunkDirty();
    }

    /** Physical rails and drill support use the same world coordinates as the renderer. */
    public List<net.minecraft.util.math.Box> getCollisionBoxes() {
        if(drillPos==null || !validDrill(drillPos) || !frameBox.isInitialized())return List.of();
        double x=drillPos.x+.5,z=drillPos.z+.5,y=frameBox.max().getY()+.5,r=.125;
        return List.of(
            new net.minecraft.util.math.Box(x-r,y-r,frameBox.min().getZ()+.5-r,x+r,y+r,frameBox.max().getZ()+.5+r),
            new net.minecraft.util.math.Box(frameBox.min().getX()+.5-r,y-r,z-r,frameBox.max().getX()+.5+r,y+r,z+r),
            new net.minecraft.util.math.Box(x-r,drillPos.y,z-r,x+r,y+r,z+r));
    }

    @Override
    public void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putInt("quarry_format",2);
        nbt.put("box", miningBox.writeToNBT());
        nbt.put("frame", frameBox.writeToNBT());
        if (boxIterator != null) {
            nbt.put("boxIterator", boxIterator.writeToNbt());
        }
        nbt.put("battery", battery.writeToNbt());
        if (currentTask != null) {
            nbt.putByte(
                "currentTaskId", (byte) Arrays.stream(EnumTaskType.values()).filter(
                    type -> type.clazz == currentTask.getClass()
                ).findFirst().orElseThrow(IllegalStateException::new).ordinal()
            );
            nbt.put("currentTaskData", currentTask.createNbt());
        }
        if (drillPos != null) {
            nbt.put("drillPos", NBTUtilBC.writeVec3d(drillPos));
        }
        nbt.putBoolean("firstChecked", firstChecked);
        nbt.putBoolean("miningFinished", miningFinished);
    }

    @Override
    public void readFromNBT(NbtCompound nbt) {
        super.readFromNBT(nbt);
        legacyMiningBounds=nbt.getInt("quarry_format")<2;
        miningBox.initialize(nbt.getCompound("box"));
        frameBox.initialize(nbt.getCompound("frame"));
        boxIterator = BoxIterator.readFromNbt(nbt.getCompound("boxIterator"));
        battery.readFromNbt(nbt.getCompound("battery"));
        int taskId=nbt.getByte("currentTaskId");
        if (nbt.contains("currentTaskId") && taskId>=0 && taskId<EnumTaskType.values().length) {
            currentTask = EnumTaskType.values()[taskId].supplier.apply(this);
            currentTask.readFromNBT(nbt.getCompound("currentTaskData"));
        } else {
            currentTask = null;
        }
        drillPos = NBTUtilBC.readVec3d(nbt.get("drillPos"));
        firstChecked = false;
        miningFinished=nbt.getBoolean("miningFinished");
        if (drillPos != null && !validDrill(drillPos)) {
            drillPos = null;
        }

        // Validation
        boolean isValid = false;
        if (frameBox.isInitialized() && miningBox.isInitialized()) {
            isValid = true;
            Direction validFace = null;
            for (Direction face : Direction.values()) {
                if (face.getAxis() == Axis.Y) continue;
                // We can't read the blockstate yet so instead we'll have to try all possible faces
                if (frameBox.isOnEdge(getPos().offset(face))) {
                    validFace = face;
                    break;
                }
            }
            if (validFace == null) {
                isValid = false;
            } else {

                int fx0 = frameBox.min().getX();
                int fy0 = frameBox.min().getY();
                int fz0 = frameBox.min().getZ();

                int fx1 = frameBox.max().getX();
                int fy1 = frameBox.max().getY();
                int fz1 = frameBox.max().getZ();

                int mx0 = miningBox.min().getX();
                int my0 = miningBox.min().getY();
                int mz0 = miningBox.min().getZ();

                int mx1 = miningBox.max().getX();
                int my1 = miningBox.max().getY();
                int mz1 = miningBox.max().getZ();

                isValid = true //
                    && fx0 + 1 == mx0//
                    && fx1 - 1 == mx1//
                    && fz0 + 1 == mz0//
                    && fz1 - 1 == mz1//
                    && fy0 >= my0//
                    && fy1 - 1 == my1//
                ;
            }
        }
        isValid &= validBounds();
        if (boxIterator!=null && (!boxIterator.getMin().equals(miningBox.min()) || !boxIterator.getMax().equals(miningBox.max()))) boxIterator=null;
        if (!validTask(currentTask)) currentTask=null;
        if (!isValid) {
            frameBox.reset();
            miningBox.reset();
            drillPos = null;
            currentTask=null; boxIterator=null; miningFinished=false;
            if(world instanceof ServerWorld)ChunkLoaderManager.forget(this);
        }
    }

    private boolean validBounds() {
        return validBounds(frameBox,miningBox);
    }

    private boolean validBounds(Box frameBox,Box miningBox) {
        if (!frameBox.isInitialized() || !miningBox.isInitialized()) return false;
        if (world!=null && (frameBox.min().getY()<world.getBottomY() || frameBox.max().getY()>=world.getTopY()
                || miningBox.min().getY()<world.getBottomY() || !world.getWorldBorder().contains(frameBox.min())
                || !world.getWorldBorder().contains(frameBox.max()))) return false;
        var size=frameBox.size();
        if (size.getX()<4 || size.getZ()<4 || size.getY()<2 || size.getX()>257 || size.getZ()>257 || size.getY()>4096
                || frameBox.contains(pos) || Math.abs((long)frameBox.min().getX())>30_000_000L
                || Math.abs((long)frameBox.max().getX())>30_000_000L || Math.abs((long)frameBox.min().getZ())>30_000_000L
                || Math.abs((long)frameBox.max().getZ())>30_000_000L || Math.abs((long)miningBox.min().getY())>4096
                || Math.abs((long)miningBox.max().getY())>4096) return false;
        return frameBox.min().getX()+1==miningBox.min().getX() && frameBox.max().getX()-1==miningBox.max().getX()
            && frameBox.min().getZ()+1==miningBox.min().getZ() && frameBox.max().getZ()-1==miningBox.max().getZ()
            && frameBox.min().getY()>=miningBox.min().getY() && frameBox.max().getY()-1==miningBox.max().getY()
            && Direction.Type.HORIZONTAL.stream().anyMatch(side->frameBox.isOnEdge(pos.offset(side)));
    }

    private boolean validDrill(Vec3d value) {
        return validDrill(value,miningBox);
    }

    private boolean validDrill(Vec3d value,Box miningBox) {
        return value!=null && Double.isFinite(value.x+value.y+value.z) && miningBox.isInitialized()
            && value.x>=miningBox.min().getX() && value.x<=miningBox.max().getX()
            && value.y>=miningBox.min().getY() && value.y<=miningBox.max().getY()
            && value.z>=miningBox.min().getZ() && value.z<=miningBox.max().getZ();
    }

    private boolean validTask(Task task) {
        return validTask(task,frameBox,miningBox,drillPos);
    }

    private boolean validTask(Task task,Box frameBox,Box miningBox,Vec3d drillPos) {
        if (task==null) return true;
        if (task.power<0 || task.power>1_000_000L*MjAPI.MJ) return false;
        if (task instanceof TaskAddFrame add) return add.framePos!=null && frameBox.isInitialized() && frameBox.isOnEdge(add.framePos);
        if (task instanceof TaskBreakBlock mine) return mine.breakPos!=null && (drillPos==null
            ?frameBox.isInitialized() && frameBox.contains(mine.breakPos):miningBox.isInitialized() && miningBox.contains(mine.breakPos));
        if (task instanceof TaskMoveDrill move) return validDrill(move.from,miningBox) && validDrill(move.to,miningBox);
        return false;
    }

    @Override
    public void writePayload(int id, PacketBufferBC buffer, NetSide side) {
        super.writePayload(id, buffer, side);
        if (side == NetSide.SERVER) {
            if (id == NET_RENDER_DATA) {
                frameBox.writeData(buffer);
                miningBox.writeData(buffer);
                buffer.writeBoolean(drillPos != null);
                if (drillPos != null) {
                    writeVec3d(buffer, drillPos);
                }
                buffer.writeBoolean(currentTask != null);
                if (currentTask != null) {
                    buffer.writeByte(
                        (byte) Arrays.stream(EnumTaskType.values()).filter(type -> type.clazz == currentTask.getClass())
                            .findFirst().orElseThrow(IllegalStateException::new).ordinal()
                    );
                    for (int i = 0; i < 2; i++) {
                        currentTask.toBytes(buffer);
                    }
                }
            }
        }
    }

    @Override
    public void readPayload(int id, PacketBufferBC buffer, NetSide side, Object ctx) throws IOException {
        super.readPayload(id, buffer, side, ctx);
        if (side == NetSide.CLIENT) {
            if (id == NET_RENDER_DATA) {
                Box nextFrame=new Box(),nextMining=new Box();
                nextFrame.readData(buffer);
                nextMining.readData(buffer);
                Vec3d nextDrill=buffer.readBoolean()?readVec3d(buffer):null;
                Task task=null,update=null;
                if (buffer.readBoolean()) {
                    int taskId = buffer.readUnsignedByte();
                    if (taskId>=EnumTaskType.values().length) throw new IOException("Invalid quarry task");
                    task = EnumTaskType.values()[taskId].supplier.apply(this);
                    task.fromBytes(buffer);
                    update=EnumTaskType.values()[taskId].supplier.apply(this);
                    update.fromBytes(buffer);
                    if (!task.equals(update)) throw new IOException("Inconsistent quarry task");
                }
                boolean empty=!nextFrame.isInitialized() && !nextMining.isInitialized() && nextDrill==null && task==null;
                if (!empty && (!validBounds(nextFrame,nextMining) || nextDrill!=null && !validDrill(nextDrill,nextMining)
                        || !validTask(task,nextFrame,nextMining,nextDrill) || !validTask(update,nextFrame,nextMining,nextDrill)))
                    throw new IOException("Invalid quarry render state");
                if(empty) {frameBox.reset();miningBox.reset();}
                else {frameBox.initialize(nextFrame);miningBox.initialize(nextMining);}
                drillPos=nextDrill;
                if (task!=null && currentTask!=null && currentTask.equals(task)) currentTask.power=update.power;
                else currentTask=task;
            }
        }
    }

    private static void writeVec3d(PacketBufferBC buffer, Vec3d vec) {
        buffer.writeDouble(vec.x);
        buffer.writeDouble(vec.y);
        buffer.writeDouble(vec.z);
    }

    private static Vec3d readVec3d(PacketBufferBC buffer) {
        return new Vec3d(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }

    @Override
    public void getDebugInfo(List<String> left, List<String> right, Direction side) {
        left.add("battery = " + battery.getDebugString());
        left.add("rate = " + LocaleUtil.localizeMjFlow(debugPowerRate));
        left.add("frameBox");
        left.add(" - min = " + frameBox.min());
        left.add(" - max = " + frameBox.max());
        left.add("miningBox:");
        left.add(" - min = " + miningBox.min());
        left.add(" - max = " + miningBox.max());

        left.add("frameScan = " + (frameIterator==null?null:frameIterator.getCurrent()));
        left.add("frameBoxPosesCount = " + frameBoxPosesCount);
        left.add("firstChecked = " + firstChecked);

        BoxIterator iter = boxIterator;
        left.add("current = " + (iter == null ? "null" : iter.getCurrent()));

        Task task = currentTask;
        if (task != null) {
            left.add("task:");
            left.add(" - class = " + task.getClass().getName());
            left.add(" - power = " + LocaleUtil.localizeMj(task.power));
            left.add(" - target = " + LocaleUtil.localizeMj(task.getTarget()));
        } else {
            left.add("task = null");
        }
        left.add("drill = " + drillPos);
    }

    @Environment(EnvType.CLIENT)
    @Override
    public DetachedRenderer.IDetachedRenderer getDebugRenderer() {
        // STUB(R.Chen): AdvDebuggerQuarry (client debug overlay) deferred to the client render pass.
        return null;
    }

    private enum EnumTaskType {
        BREAK_BLOCK(TaskBreakBlock.class, quarry -> quarry.new TaskBreakBlock()),
        ADD_FRAME(TaskAddFrame.class, quarry -> quarry.new TaskAddFrame()),
        MOVE_DRILL(TaskMoveDrill.class, quarry -> quarry.new TaskMoveDrill());

        public final Class<? extends Task> clazz;
        public final Function<TileQuarry, Task> supplier;

        EnumTaskType(Class<? extends Task> clazz, Function<TileQuarry, Task> supplier) {
            this.clazz = clazz;
            this.supplier = supplier;
        }
    }

    private abstract class Task {
        public long power;
        public long clientPower;
        public long prevClientPower;

        NbtCompound createNbt() { return serializeNBT(); }
        NbtCompound serializeNBT() {
            NbtCompound nbt = new NbtCompound();
            nbt.putLong("power", power);
            return nbt;
        }

        void readFromNBT(NbtCompound nbt) {
            power = nbt.getLong("power");
            if (power < 0) {
                power = 0;
            }
        }

        void toBytes(PacketBufferBC buffer) {
            buffer.writeLong(power);
        }

        void fromBytes(PacketBufferBC buffer) throws IOException {
            power = buffer.readLong();
            if (power<0 || power>1_000_000L*MjAPI.MJ) throw new IOException("Invalid quarry task power");
        }

        void clientTick() {
            prevClientPower = clientPower;
            clientPower = power;
        }

        public abstract long getTarget();

        public long getRequiredPowerThisTick() {
            return Math.max(0, getTarget() - power);
        }

        /** @param target TODO
         * @return {@code true} if this task has been completed, or cancelled. */
        protected abstract boolean onReceivePower(long added, long target);

        protected abstract boolean finish(long added, long target);

        /** @return {@code true} if this task has been completed, or cancelled. */
        final boolean addPower(long microJoules) {
            power += Math.max(0,microJoules);
            long target = getTarget();
            if (power >= target) {
                if (!finish(microJoules, target)) {
                    battery.addPower(Math.min(power, battery.getCapacity() - battery.getStored()), false);
                }
                return true;
            } else {
                return onReceivePower(microJoules, target);
            }
        }
    }

    public class TaskBreakBlock extends Task {
        public BlockPos breakPos = BlockPos.ORIGIN;

        TaskBreakBlock() {}

        TaskBreakBlock(BlockPos pos) {
            this.breakPos = pos;
        }

        @Override
        NbtCompound serializeNBT() {
            NbtCompound nbt = super.serializeNBT();
            nbt.put("breakPos", NBTUtilBC.writeBlockPos(breakPos));
            return nbt;
        }

        @Override
        void readFromNBT(NbtCompound nbt) {
            super.readFromNBT(nbt);
            breakPos = NBTUtilBC.readBlockPos(nbt.get("breakPos"));
            if (breakPos == null) {
                // We failed to read, abort
                currentTask = null;
            }
        }

        @Override
        void toBytes(PacketBufferBC buffer) {
            super.toBytes(buffer);
            buffer.writeBlockPos(breakPos);
        }

        @Override
        void fromBytes(PacketBufferBC buffer) throws IOException {
            super.fromBytes(buffer);
            breakPos = buffer.readBlockPos();
        }

        @Override
        public long getTarget() {
            return BlockUtil.computeBlockBreakPower(world, breakPos,buildcraft.core.BCCoreConfig.miningMultiplier);
        }

        @Override
        public long getRequiredPowerThisTick() {
            long target = getTarget();
            long req = Math.max(0, target - power);
            double rate = buildcraft.builders.BCBuildersConfig.quarryMaxBlockMineRate;
            if (rate < 0.1) {
                return req;
            }
            rate /= 20; // seconds -> ticks
            rate -= blockPercentSoFar;
            if (rate <= 0) {
                return 0;
            }
            return Math.min(req, (long) (target * rate));
        }

        @Override
        protected boolean onReceivePower(long added, long target) {
            blockPercentSoFar += added / (double) target;
            if (!world.isAir(breakPos)) {
                world.setBlockBreakingInfo(breakPos.hashCode(), breakPos, (int) (power * 9 / getTarget()));
                return false;
            } else {
                return true;
            }
        }

        @Override
        protected boolean finish(long added, long target) {
            blockPercentSoFar += added / (double) target;
            if (!canMine(breakPos)) {
                return true;
            }
            world.setBlockBreakingInfo(breakPos.hashCode(), breakPos, -1);
            Optional<List<ItemStack>> stacks;
            performingWork=true;
            try { stacks=BlockUtil.breakBlockAndGetDrops((ServerWorld)world,breakPos,new ItemStack(Items.DIAMOND_PICKAXE),getOwner(),true); }
            finally { performingWork=false; }
            if (stacks.isPresent()) {
                // Frame clearing intentionally destroys its debris, as in upstream.
                if (drillPos != null) {
                    stacks.get().forEach(stack -> InventoryUtil.addToBestAcceptor(world,pos,null,stack));
                }
            }
            check(breakPos);
            return stacks.isPresent();
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if (o == null) return false;
            if (getClass() != o.getClass()) {
                return false;
            }
            return breakPos.equals(((TaskBreakBlock) o).breakPos);
        }
    }

    public class TaskAddFrame extends Task {
        public BlockPos framePos = BlockPos.ORIGIN;

        TaskAddFrame() {}

        TaskAddFrame(BlockPos framePos) {
            this.framePos = framePos;
        }

        @Override
        NbtCompound serializeNBT() {
            NbtCompound nbt = super.serializeNBT();
            nbt.put("framePos", NBTUtilBC.writeBlockPos(framePos));
            return nbt;
        }

        @Override
        void readFromNBT(NbtCompound nbt) {
            super.readFromNBT(nbt);
            framePos = NBTUtilBC.readBlockPos(nbt.get("framePos"));
            if (framePos == null) {
                // We failed to read, abort
                currentTask = null;
            }
        }

        @Override
        void toBytes(PacketBufferBC buffer) {
            super.toBytes(buffer);
            buffer.writeBlockPos(framePos);
        }

        @Override
        void fromBytes(PacketBufferBC buffer) throws IOException {
            super.fromBytes(buffer);
            framePos = buffer.readBlockPos();
        }

        @Override
        public long getTarget() {
            return 24 * MjAPI.MJ;
        }

        @Override
        protected boolean onReceivePower(long added, long target) {
            return canIgnoreInFrameBox(framePos);
        }

        @Override
        protected boolean finish(long added, long target) {
            if (canIgnoreInFrameBox(framePos)) {
                return false;
            }
            if (!loaded(framePos) || !frameBox.isOnEdge(framePos)) return false;
            var player=buildcraft.lib.misc.FakePlayerProvider.INSTANCE.getFakePlayer((ServerWorld)world,getOwner(),framePos);
            if (!world.canPlayerModifyAt(player,framePos)) return false;
            var previous=player.getMainHandStack();
            var frameStack=new ItemStack(buildcraft.builders.BCBuildersBlocks.frame);
            player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,frameStack);
            performingWork=true;
            try {
                player.interactionManager.interactBlock(player,world,frameStack,net.minecraft.util.Hand.MAIN_HAND,
                    new net.minecraft.util.hit.BlockHitResult(Vec3d.ofCenter(framePos),Direction.UP,framePos,false));
            } finally { player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,previous); performingWork=false; }
            check(framePos);
            return world.getBlockState(framePos).isOf(buildcraft.builders.BCBuildersBlocks.frame);
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if (o == null) return false;
            if (getClass() != o.getClass()) {
                return false;
            }
            return framePos.equals(((TaskAddFrame) o).framePos);
        }
    }

    private class TaskMoveDrill extends Task {
        public Vec3d from = Vec3d.ZERO;
        public Vec3d to = Vec3d.ZERO;

        TaskMoveDrill() {}

        TaskMoveDrill(Vec3d from, Vec3d to) {
            this.from = from;
            this.to = to;
        }

        @Override
        NbtCompound serializeNBT() {
            NbtCompound nbt = super.serializeNBT();
            nbt.put("from", NBTUtilBC.writeVec3d(from));
            nbt.put("to", NBTUtilBC.writeVec3d(to));
            return nbt;
        }

        @Override
        void readFromNBT(NbtCompound nbt) {
            super.readFromNBT(nbt);
            from = NBTUtilBC.readVec3d(nbt.get("from"));
            to = NBTUtilBC.readVec3d(nbt.get("to"));
            if (from == null || to == null) {
                // We failed to read. Abort.
                currentTask = null;
            }
        }

        @Override
        void toBytes(PacketBufferBC buffer) {
            super.toBytes(buffer);
            writeVec3d(buffer, from);
            writeVec3d(buffer, to);
        }

        @Override
        void fromBytes(PacketBufferBC buffer) throws IOException {
            super.fromBytes(buffer);
            from = readVec3d(buffer);
            to = readVec3d(buffer);
        }

        @Override
        public long getTarget() {
            return (long) (from.distanceTo(to) * 20 * MjAPI.MJ);
        }

        @Override
        public long getRequiredPowerThisTick() {
            long req = Math.max(0, getTarget() - power);

            double max = buildcraft.builders.BCBuildersConfig.quarryMaxFrameMoveSpeed;
            if (max < 0.1) {
                return req;
            }
            max /= 20;
            max -= moveDistanceSoFar;
            if (max <= 0) {
                return 0;
            }
            return Math.min(req, (long) (max * 20 * MjAPI.MJ));
        }

        @Override
        protected boolean onReceivePower(long added, long target) {
            moveDistanceSoFar += added / (double) MjAPI.MJ;
            drillPos = from.multiply(1 - power / (double) target).add(to.multiply(power / (double) target));
            return false;
        }

        @Override
        protected boolean finish(long added, long target) {
            moveDistanceSoFar += added / (double) MjAPI.MJ;
            drillPos = to;
            return true;
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if (o == null) return false;
            if (getClass() != o.getClass()) {
                return false;
            }
            TaskMoveDrill other = (TaskMoveDrill) o;
            return from.equals(other.from) && to.equals(other.to);
        }
    }

    // @Override removed (R.Chen): no longer overrides — Phase 10
    public void render(float partialTicks) { /* STUB */ }
}
