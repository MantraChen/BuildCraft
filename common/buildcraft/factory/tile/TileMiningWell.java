/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0
 * Native Fabric excavation. Remake by FelixMantra.
 */
package buildcraft.factory.tile;

import net.minecraft.block.BlockState;
import net.minecraft.block.FluidBlock;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import buildcraft.api.core.SafeTimeTracker;
import buildcraft.api.mj.IMjReceiver;
import buildcraft.core.BCCoreConfig;
import buildcraft.factory.MiningWellUpdates;
import buildcraft.lib.misc.BlockUtil;
import buildcraft.lib.misc.InventoryUtil;
import buildcraft.lib.mj.MjBatteryReceiver;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes;

/** Mines one vertical column, preserving paid work while power or permission is unavailable. */
public class TileMiningWell extends TileMiner {
    private boolean shouldCheck=true, changingColumn;
    private final SafeTimeTracker tracker=new SafeTimeTracker(256);
    private BlockState miningState;
    private int retryTicks, lastDepth=-1;

    public TileMiningWell(BlockEntityType<?> type,BlockPos pos,BlockState state) {
        super(type,pos,state);
    }

    @Override protected void mine() {
        if (world.isReceivingRedstonePower(pos)) return;
        if (lastDepth!=miningDepth()) {lastDepth=miningDepth();shouldCheck=true;}
        if (currentPos!=null && (!validWorkPosition(currentPos) || world.getBlockState(currentPos)!=miningState))
            shouldCheck=true;
        if (shouldCheck || currentPos==null && tracker.markTimeIfDelay(world)) {
            shouldCheck=false;
            nextPos();
        }
        if (currentPos==null) return;
        if (retryTicks>0) {retryTicks--;return;}
        long cost=BlockUtil.computeBlockBreakPower(world,currentPos,BCCoreConfig.miningMultiplier);
        progress=Math.min(progress,cost);
        long added=battery.extractPower(0,cost-progress);
        progress+=added;
        if (added>0) {
            markChunkDirty();
            world.setBlockBreakingInfo(pos.hashCode(),currentPos,(int)(9D*progress/cost));
        }
        if (progress<cost) return;

        var target=currentPos;
        changingColumn=true;
        try {
            var drops=BlockUtil.breakBlockAndGetDrops((ServerWorld)world,target,new ItemStack(Items.DIAMOND_PICKAXE),getOwner(),false);
            if(drops.isEmpty()) {retryTicks=20;return;}
            clearCracks();
            progress=0;miningState=null;
            drops.get().forEach(stack->InventoryUtil.addToBestAcceptor(world,pos,null,stack));
            nextPos();
            markChunkDirty();
        } finally {changingColumn=false;}
    }

    private boolean passable(BlockPos cell) {
        var state=world.getBlockState(cell);
        return state.isAir() || isTubeBlock(world,cell) || state.getBlock() instanceof FluidBlock
            && FluidVariantAttributes.getViscosity(FluidVariant.of(state.getFluidState().getFluid()),world)<=1000;
    }

    private void nextPos() {
        BlockPos target=null;
        for(int depth=1;depth<=miningDepth();depth++) {
            BlockPos cell=pos.down(depth);
            if(!world.isChunkLoaded(cell) || !world.getWorldBorder().contains(cell))break;
            if(passable(cell))continue;
            var state=world.getBlockState(cell);
            if(!(state.getBlock() instanceof FluidBlock) && state.getHardness(world,cell)>=0)target=cell;
            break;
        }
        BlockState nextState=target==null?null:world.getBlockState(target);
        if(!java.util.Objects.equals(currentPos,target) || miningState!=null && miningState!=nextState) {
            clearCracks();progress=0;retryTicks=0;
        }
        currentPos=target;miningState=nextState;
        boolean wasChanging=changingColumn;
        changingColumn=true;
        try {
            if(!updateLength()) {
                clearCracks();currentPos=null;miningState=null;progress=0;
            }
        } finally {changingColumn=wasChanging;}
        markChunkDirty();
    }

    @Override protected boolean validWorkPosition(BlockPos cell) {
        return super.validWorkPosition(cell) && cell.getX()==pos.getX() && cell.getZ()==pos.getZ()
            && cell.getY()<pos.getY() && (long)pos.getY()-cell.getY()<=miningDepth();
    }

    public void onColumnChanged(BlockPos cell) {
        if(!changingColumn && cell.getX()==pos.getX() && cell.getZ()==pos.getZ() && cell.getY()<pos.getY()) {
            shouldCheck=true;retryTicks=0;
        }
    }

    private void clearCracks() {
        if(currentPos!=null && world!=null && !world.isClient)world.setBlockBreakingInfo(pos.hashCode(),currentPos,-1);
    }

    @Override public void onLoad() {super.onLoad();MiningWellUpdates.register(this);shouldCheck=true;}
    @Override public void cancelRemoval() {super.cancelRemoval();MiningWellUpdates.register(this);}
    @Override public void markRemoved() {clearCracks();MiningWellUpdates.unregister(this);super.markRemoved();}
    @Override public void onRemove() {
        if(!isStorageAvailable() || world.isClient)return;
        clearCracks();MiningWellUpdates.unregister(this);super.onRemove();
    }

    @Override public void readFromNBT(NbtCompound nbt) {
        super.readFromNBT(nbt);
        miningState=nbt.contains("miningState")?NbtHelper.toBlockState(Registries.BLOCK.getReadOnlyWrapper(),nbt.getCompound("miningState")):null;
        shouldCheck=true;retryTicks=0;
    }

    @Override public void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        if(miningState!=null)nbt.put("miningState",NbtHelper.fromBlockState(miningState));
    }

    @Override protected IMjReceiver createMjReceiver() {
        return new MjBatteryReceiver(battery) {
            @Override public boolean canReceive() {return isStorageAvailable() && !world.isClient;}
            @Override public long getPowerRequested() {return canReceive()?super.getPowerRequested():0;}
            @Override public long receivePower(long amount,boolean simulate) {
                if(amount<0 || !canReceive())return amount;
                long left=super.receivePower(amount,simulate);
                if(!simulate && left!=amount)markChunkDirty();
                return left;
            }
        };
    }
}
