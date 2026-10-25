/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0
 * Native Fabric pump. Remake by FelixMantra.
 */
package buildcraft.factory.tile;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import it.unimi.dsi.fastutil.longs.*;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.storage.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.*;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.GameRules;
import buildcraft.api.mj.*;
import buildcraft.core.BCCoreConfig;
import buildcraft.core.tile.ITileOilSpring;
import buildcraft.lib.fluid.*;
import buildcraft.lib.misc.FakePlayerProvider;
import buildcraft.lib.misc.AdvancementUtil;
import buildcraft.lib.mj.MjBatteryReceiver;
import buildcraft.lib.net.PacketBufferBC;

/** Searches connected sources incrementally and pays exactly 10 MJ per successfully collected bucket. */
public class TilePump extends TileMiner {
    private static final long COST=10*MjAPI.MJ;
    private static final int SEARCH_BUDGET=2048;
    private final Tank tank;
    private final Storage<FluidVariant> output;
    private final Long2LongOpenHashMap parents=new Long2LongOpenHashMap();
    private final LongArrayFIFOQueue frontier=new LongArrayFIFOQueue();
    private final LongArrayList sources=new LongArrayList();
    private BlockPos connection, oilSpringPos;
    private FluidVariant fluid=FluidVariant.blank(), paidFluid=FluidVariant.blank();
    private FluidVariant clientFluid=FluidVariant.blank();
    private long clientAmount;
    private boolean initialized, searching, contentsDirty;
    private int retryTicks, lastRadius=-1, lastDepth=-1;

    public TilePump(BlockEntityType<?> type,BlockPos pos,BlockState state) {
        super(type,pos,state);
        tank=tankManager.add(new Tank("tank",16000,this) {
            @Override protected void onContentsChanged() {
                super.onContentsChanged();contentsDirty=true;
            }
        });
        output=new FluidStorageAccess(tank,false,true);
    }

    public long getFluidAmount() { return world!=null && world.isClient?clientAmount:tank.getStorage().getAmount(); }
    public FluidVariant getFluidVariant() { return world!=null && world.isClient?clientFluid:tank.getStorage().getResource(); }
    @Override public Storage<FluidVariant> getFluidStorage(Direction side) { return output; }
    @Override protected long getBatteryCapacity() { return 50*MjAPI.MJ; }
    @Override public boolean isComplete() {
        return world!=null && world.isClient?isComplete:initialized && !searching && currentPos==null;
    }
    @Override protected BlockPos getTargetPos() {
        return searching || currentPos!=null?connection:null;
    }

    @Override public void tick() {
        super.tick();
        if (!isStorageAvailable() || world.isClient) return;
        // Moving buffered fluid does not spend MJ and remains possible while redstone pauses collection.
        for (Direction side:Direction.values()) {
            if (tank.isEmpty()) break;
            BlockPos neighbor=pos.offset(side);
            if (!world.isChunkLoaded(neighbor)) continue;
            Storage<FluidVariant> destination=FluidStorage.SIDED.find(world,neighbor,side.getOpposite());
            if (destination!=null) StorageUtil.move(output,destination,variant->true,FluidConstants.BUCKET,null);
        }
        if (contentsDirty) {contentsDirty=false;sendNetworkUpdate(NET_LED_STATUS);}
    }

    private int radius() { return Math.max(1,Math.min(128,BCCoreConfig.pumpMaxDistance)); }
    private boolean loaded(BlockPos cell) {
        return world.isInBuildLimit(cell) && world.getWorldBorder().contains(cell) && world.isChunkLoaded(cell);
    }
    private boolean matches(BlockPos cell) {
        return loaded(cell) && !fluid.isBlank() && fluid.getFluid().matchesType(world.getFluidState(cell).getFluid());
    }
    private boolean source(BlockPos cell) {
        return matches(cell) && world.getFluidState(cell).isStill()
            && world.getBlockState(cell).getBlock() instanceof FluidDrainable;
    }
    private boolean connected(BlockPos from,BlockPos to,Direction direction) {
        return !VoxelShapes.adjacentSidesCoverSquare(world.getBlockState(from).getCollisionShape(world,from),
            world.getBlockState(to).getCollisionShape(world,to),direction);
    }

    private void startSearch() {
        parents.clear();frontier.clear();sources.clear();
        searching=false;initialized=true;connection=null;fluid=FluidVariant.blank();
        lastRadius=radius();lastDepth=miningDepth();
        for (int depth=1;depth<=lastDepth;depth++) {
            BlockPos cell=pos.down(depth);
            if (!loaded(cell)) break;
            BlockState state=world.getBlockState(cell);
            FluidState liquid=state.getFluidState();
            if (!liquid.isEmpty()) {
                connection=cell;
                Fluid type=liquid.getFluid();
                fluid=FluidVariant.of(type instanceof FlowableFluid flowing?flowing.getStill():type);
                parents.put(cell.asLong(),cell.asLong());frontier.enqueue(cell.asLong());
                searching=true;
                break;
            }
            if (!state.isAir() && !isTubeBlock(world,cell)) break;
        }
        if (!searching) {setCurrent(null);updateLength();retryTicks=30;}
    }

    private void searchStep() {
        boolean gas=FluidVariantAttributes.isLighterThanAir(fluid);
        Direction vertical=gas?Direction.DOWN:Direction.UP;
        Direction[] directions={vertical,Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST};
        for (int budget=SEARCH_BUDGET;budget>0 && !frontier.isEmpty();budget--) {
            BlockPos cell=BlockPos.fromLong(frontier.dequeueLong());
            if (!matches(cell)) continue;
            if (source(cell)) sources.add(cell.asLong());
            for (Direction direction:directions) {
                BlockPos next=cell.offset(direction);
                if (next.equals(pos) || next.getSquaredDistance(connection)>(long)lastRadius*lastRadius
                        || parents.containsKey(next.asLong()) || !matches(next) || !connected(cell,next,direction)) continue;
                parents.put(next.asLong(),cell.asLong());frontier.enqueue(next.asLong());
            }
        }
        if (!frontier.isEmpty()) return;
        searching=false;
        // A saved partially paid source must survive a fresh search before its payment may be resumed.
        if (currentPos!=null && paidFluid.equals(fluid) && sources.rem(currentPos.asLong()) && pathValid(currentPos)) {
            progress=Math.min(COST,progress);
        } else {setCurrent(null);nextSource();}
        if (!updateLength()) {retryTicks=20;}
    }

    private void setCurrent(BlockPos next) {
        if (!Objects.equals(currentPos,next) || !paidFluid.equals(fluid)) progress=0;
        currentPos=next;
        paidFluid=next==null?FluidVariant.blank():fluid;
        markChunkDirty();
    }

    private void nextSource() {
        while (!sources.isEmpty()) {
            BlockPos next=BlockPos.fromLong(sources.removeLong(sources.size()-1));
            if (source(next)) {setCurrent(next);return;}
        }
        setCurrent(null);retryTicks=30;
    }

    private boolean pathValid(BlockPos cell) {
        if (!source(cell) || connection==null) return false;
        long key=cell.asLong();
        // Parents form a tree established by the bounded-per-tick flood search.
        for (int remaining=parents.size();remaining>0;remaining--) {
            if (!parents.containsKey(key)) return false;
            BlockPos here=BlockPos.fromLong(key);
            if (!matches(here)) return false;
            long parent=parents.get(key);
            if (parent==key) return here.equals(connection);
            BlockPos next=BlockPos.fromLong(parent);
            Direction side=Direction.fromVector(next.getX()-here.getX(),next.getY()-here.getY(),next.getZ()-here.getZ());
            if (!matches(next) || side==null || !connected(here,next,side)) return false;
            key=parent;
        }
        return false;
    }

    /** Mirrors water regeneration: two actual source neighbors and solid/source support, with the native game rule. */
    private boolean renewableWater(BlockPos cell) {
        if (BCCoreConfig.pumpsConsumeWater || !fluid.isOf(Fluids.WATER)
                || !world.getGameRules().getBoolean(GameRules.WATER_SOURCE_CONVERSION)
                || !(world.getBlockState(cell).getBlock() instanceof FluidBlock)) return false;
        int count=0;
        for (Direction side:Direction.Type.HORIZONTAL) {
            BlockPos next=cell.offset(side);
            if (loaded(next) && world.getFluidState(next).isOf(Fluids.WATER)
                    && world.getFluidState(next).isStill() && connected(cell,next,side)) count++;
        }
        BlockPos below=cell.down();
        if (count<2 || !loaded(below)) return false;
        var state=world.getBlockState(below);
        return state.isSolid() || state.getFluidState().isOf(Fluids.WATER) && state.getFluidState().isStill();
    }

    @Override protected void mine() {
        if (world.isReceivingRedstonePower(pos)) return;
        if (lastRadius!=radius() || lastDepth!=miningDepth()) initialized=false;
        if (retryTicks>0) {retryTicks--;return;}
        if (!initialized || currentPos==null && !searching) startSearch();
        if (searching) {searchStep();if(searching)return;}
        if (currentPos==null) return;
        if (!pathValid(currentPos)) {initialized=false;retryTicks=30;return;}
        // A newly placed obstruction must not let the pump draw fluid through a blocked shaft.
        if (!updateLength()) {retryTicks=20;return;}
        var storage=tank.getStorage();
        if (!storage.isResourceBlank() && !storage.getResource().equals(fluid)
                || storage.getCapacity()-storage.getAmount()<FluidConstants.BUCKET) return;
        long added=battery.extractPower(0,Math.max(0,COST-progress));
        progress+=added;
        if (added>0) markChunkDirty();
        if (progress<COST) return;

        BlockPos cell=currentPos;
        BlockState before=world.getBlockState(cell);
        var player=FakePlayerProvider.INSTANCE.getFakePlayer((ServerWorld)world,getOwner(),cell);
        if (!world.canPlayerModifyAt(player,cell)) {retryTicks=20;return;}
        ItemStack previous=player.getMainHandStack();
        player.setStackInHand(Hand.MAIN_HAND,new ItemStack(Items.BUCKET));
        boolean renewable;
        try {
            var hit=new BlockHitResult(Vec3d.ofCenter(cell),Direction.UP,cell,false);
            if (UseBlockCallback.EVENT.invoker().interact(player,world,Hand.MAIN_HAND,hit)!=ActionResult.PASS
                    || world.getBlockState(cell)!=before || !pathValid(cell)) {retryTicks=20;return;}
            renewable=renewableWater(cell);
            // Reserve the complete bucket before touching the world. A failed/vetoed drain rolls this back.
            try (Transaction transaction=Transaction.openOuter()) {
                if (storage.insert(fluid,FluidConstants.BUCKET,transaction)!=FluidConstants.BUCKET) return;
                if (!renewable && ((FluidDrainable)before.getBlock()).tryDrainFluid(world,cell,before).isEmpty()) {
                    retryTicks=20;return;
                }
                transaction.commit();
            }
        } finally {player.setStackInHand(Hand.MAIN_HAND,previous);}
        progress=0;
        if (getOwner()!=null) {
            AdvancementUtil.unlockAdvancement((ServerWorld)world,getOwner().getId(),new Identifier("buildcraftfactory:draining_the_world"));
            if (fluid.getFluid()==buildcraft.energy.BCEnergyFluids.crudeOil[0]) {
                AdvancementUtil.unlockAdvancement((ServerWorld)world,getOwner().getId(),new Identifier("buildcraftfactory:oil_platform"));
                recordOil(cell);
            }
        }
        if (!renewable) {nextSource();updateLength();}
        markChunkDirty();
    }

    private void recordOil(BlockPos cell) {
        if (oilSpringPos==null || !loaded(oilSpringPos) || !(world.getBlockEntity(oilSpringPos) instanceof ITileOilSpring)) {
            oilSpringPos=null;
            // World generation places the reservoir marker on the bottom layer; retain support for legacy Y=0.
            for (int y:new int[]{world.getBottomY(),0}) {
                for (int dx=-10;dx<=10;dx++) for (int dz=-10;dz<=10;dz++) {
                    BlockPos candidate=new BlockPos(pos.getX()+dx,y,pos.getZ()+dz);
                    if (loaded(candidate) && world.getBlockEntity(candidate) instanceof ITileOilSpring
                            && (oilSpringPos==null || candidate.getSquaredDistance(pos)<oilSpringPos.getSquaredDistance(pos))) oilSpringPos=candidate;
                }
            }
        }
        if (oilSpringPos!=null && loaded(oilSpringPos) && world.getBlockEntity(oilSpringPos) instanceof ITileOilSpring spring)
            spring.onPumpOil(getOwner(),cell);
    }

    @Override public boolean onActivated(PlayerEntity player,Hand hand,Direction face,float x,float y,float z) {
        if (!canInteractWith(player)) return false;
        var context=ContainerItemContext.forPlayerInteraction(player,hand);
        return world.isClient?context.find(FluidStorage.ITEM)!=null:FluidTransfers.interact(output,context,player);
    }

    @Override protected IMjReceiver createMjReceiver() {
        return new MjBatteryReceiver(battery) {
            @Override public boolean canReceive() { return isStorageAvailable() && !world.isClient; }
            @Override public long getPowerRequested() { return canReceive()?super.getPowerRequested():0; }
            @Override public long receivePower(long amount,boolean simulate) {
                if (amount<0 || !canReceive()) return amount;
                long left=super.receivePower(amount,simulate);
                if (!simulate && left!=amount) markChunkDirty();
                return left;
            }
        };
    }

    @Override public void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        if (connection!=null) nbt.put("pumpConnection",NbtHelper.fromBlockPos(connection));
        if (oilSpringPos!=null) nbt.put("oilSpringPos",NbtHelper.fromBlockPos(oilSpringPos));
        nbt.put("pumpPaidFluid",paidFluid.toNbt());
    }
    @Override public void readFromNBT(NbtCompound nbt) {
        super.readFromNBT(nbt);
        oilSpringPos=nbt.contains("oilSpringPos")?NbtHelper.toBlockPos(nbt.getCompound("oilSpringPos")):null;
        connection=nbt.contains("pumpConnection")?NbtHelper.toBlockPos(nbt.getCompound("pumpConnection")):null;
        paidFluid=FluidVariant.fromNbt(nbt.getCompound("pumpPaidFluid"));
        if (paidFluid.isBlank()) progress=0;
        progress=Math.min(progress,COST);
        parents.clear();frontier.clear();sources.clear();
        initialized=searching=false;contentsDirty=true;retryTicks=0;lastRadius=lastDepth=-1;
    }
    @Override protected void writeMinerState(PacketBufferBC buffer) {
        tank.getStorage().getResource().toPacket(buffer);
        buffer.writeLong(tank.getStorage().getAmount());
    }
    @Override protected Runnable readMinerState(PacketBufferBC buffer) throws IOException {
        FluidVariant next=FluidVariant.fromPacket(buffer);
        long amount=buffer.readLong();
        if (amount<0 || amount>tank.getStorage().getCapacity() || next.isBlank()!=(amount==0))
            throw new IOException("Invalid pump tank state");
        return ()->{clientFluid=next;clientAmount=amount;};
    }
    @Override public void getDebugInfo(List<String> left,List<String> right,Direction side) {
        super.getDebugInfo(left,right,side);
        left.add("fluid = "+getFluidAmount()+" droplets of "+getFluidVariant());
        left.add("search = "+parents.size()+" visited, "+sources.size()+" sources, "+searching);
        left.add("oilSpring = "+oilSpringPos);
    }
}
