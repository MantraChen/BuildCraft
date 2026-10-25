/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import java.util.Comparator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.List;
import java.util.Optional;

import com.mojang.authlib.GameProfile;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.ItemEntity;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

// STUB(R.Chen): the full Forge BlockUtil (block breaking, BreakEvent firing, fluid drain/fill via the
// Forge fluid API, comparator/double-chest helpers, CompatManager ghost-loading) is deferred. Only the
// block-state / block-entity accessors used by TileBC_Neptune are migrated; the `force` flag — which
// drove CompatManager's chunk-ghost-loading — is now a no-op since Yarn's World loads as needed.
public class BlockUtil {

    public static BlockEntity getTileEntity(World world, BlockPos pos) {
        return getTileEntity(world, pos, false);
    }

    public static BlockEntity getTileEntity(World world, BlockPos pos, boolean force) {
        return world.getBlockEntity(pos);
    }

    public static BlockState getBlockState(World world, BlockPos pos) {
        return getBlockState(world, pos, false);
    }

    public static BlockState getBlockState(World world, BlockPos pos, boolean force) {
        return world.getBlockState(pos);
    }

    /** Pure-logic comparator helper (no Forge deps): orders by the supplied comparator, breaking ties on the
     * (x, y, z) coordinates so that the resulting {@link java.util.TreeSet} never collapses distinct positions. */
    public static Comparator<BlockPos> uniqueBlockPosComparator(Comparator<BlockPos> base) {
        return base
            .thenComparingInt(BlockPos::getX)
            .thenComparingInt(BlockPos::getY)
            .thenComparingInt(BlockPos::getZ);
    }

    /** Includes flowing fluids and waterlogged blocks, normalized to the still-fluid identity. */
    public static Fluid getFluidWithFlowing(World world, BlockPos pos) {
        FluidState state = world.getFluidState(pos);
        if (state.isEmpty()) return null;
        Fluid fluid = state.getFluid();
        return fluid instanceof net.minecraft.fluid.FlowableFluid flowing ? flowing.getStill() : fluid;
    }

    // STUB(R.Chen): Forge block-hardness → MJ break-power curve deferred. Returns a flat placeholder cost so
    // the quarry's task scheduler still advances; real per-block hardness scaling restored with the mining pass.
    public static long computeBlockBreakPower(World world, BlockPos pos) {
        return 16 * buildcraft.api.mj.MjAPI.MJ;
    }

    /** Harvests with native player break callbacks, loot tables and block-entity drops.
     * @param grabAll whether to also collect items already lying next to the block
     * @return empty when the break is refused; otherwise the collected drops, removed from the world exactly once
     */
    public static Optional<List<ItemStack>> breakBlockAndGetDrops(ServerWorld world, BlockPos pos, ItemStack tool,
        GameProfile owner, boolean grabAll) {
        BlockState state = world.getBlockState(pos);
        if (state.isAir() || state.getHardness(world, pos) < 0 || !world.getWorldBorder().contains(pos)
            || !world.isInBuildLimit(pos)) {
            return Optional.empty();
        }
        FakePlayer player = FakePlayerProvider.INSTANCE.getFakePlayer(world, owner, pos);
        ItemStack previous = player.getMainHandStack();
        player.setStackInHand(Hand.MAIN_HAND, tool.copy());
        try {
            if (!world.canPlayerModifyAt(player, pos) || (state.isToolRequired() && !player.canHarvest(state))) {
                return Optional.empty();
            }
            Box area = new Box(pos).expand(1);
            Set<ItemEntity> existing = Collections.newSetFromMap(new IdentityHashMap<>());
            if (!grabAll) existing.addAll(world.getEntitiesByClass(ItemEntity.class, area, entity -> true));
            if (!player.interactionManager.tryBreakBlock(pos)) return Optional.empty();
            List<ItemStack> drops = new ArrayList<>();
            for (ItemEntity entity : world.getEntitiesByClass(ItemEntity.class, area, entity -> !entity.isRemoved())) {
                if (!existing.contains(entity) && !entity.getStack().isEmpty()) {
                    drops.add(entity.getStack().copy());
                    entity.discard();
                }
            }
            return Optional.of(drops);
        } finally {
            player.setStackInHand(Hand.MAIN_HAND, previous);
        }
    }

    // STUB(R.Chen): missing getFluidWithoutFlowing / getFluid helpers.
    public static Fluid getFluidWithoutFlowing(BlockState state) {
        FluidState fs = state.getFluidState();
        if (fs.isEmpty()) return net.minecraft.fluid.Fluids.EMPTY;
        return fs.getFluid() instanceof net.minecraft.fluid.FlowableFluid flowing ? flowing.getStill() : fs.getFluid();
    }
    public static Fluid getFluid(World world, BlockPos pos) {
        return getFluidWithFlowing(world, pos);
    }

    /** BlockUtil.getBlockEntity with explicit force flag (no-op, delegates to World). */
    public static BlockEntity getBlockEntity(World world, BlockPos pos, boolean force) {
        return getTileEntity(world, pos, force);
    }

    /** STUB(R.Chen): getFluidWithFlowing(Block) — checks if block is a fluid block. */
    public static Fluid getFluidWithFlowing(net.minecraft.block.Block block) {
        if (block instanceof net.minecraft.block.FluidBlock) {
            return ((net.minecraft.block.FluidBlock) block).getFluidState(block.getDefaultState()).getFluid();
        }
        return null;
    }

    // STUB(R.Chen): direction arrays from Forge BlockUtil — static convenience fields.
    public static final Direction[] HORIZONTALS = Direction.Type.HORIZONTAL.stream().toArray(Direction[]::new);
    public static final int BUCKET_VOLUME = 1000;

    // STUB(R.Chen): Forge useItemOnBlock → Fabric ItemUsageContext migration deferred. Returns false.
    public static boolean useItemOnBlock(World world, net.minecraft.entity.player.PlayerEntity player,
            ItemStack stack, BlockPos pos, Direction side) {
        return false;
    }
}
