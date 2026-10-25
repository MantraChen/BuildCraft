/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team.
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 */
package buildcraft.lib.fluid;

import net.minecraft.block.*;
import net.minecraft.entity.Entity;
import net.minecraft.fluid.FluidState;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/** Fluid levels retain all legacy quanta instead of truncating them to vanilla's eight. */
public class BCFluidBlock extends FluidBlock {
    public static final BooleanProperty VERTICAL_FLOW = BooleanProperty.of("vertical_flow");
    private boolean sticky;
    public BCFluidBlock(BCFluid fluid, AbstractBlock.Settings settings) {
        super(fluid, settings);
        setDefaultState(getDefaultState().with(LEVEL, 0).with(VERTICAL_FLOW, false));
    }
    public void setSticky(boolean value) { sticky = value; }
    public boolean isSticky() { return sticky; }
    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder);
        builder.add(VERTICAL_FLOW);
    }
    @Override public FluidState getFluidState(BlockState state) {
        BCFluid bc = (BCFluid) fluid;
        int level = state.get(LEVEL);
        return level == 0 ? bc.getStill(false)
            : bc.getFlowing(Math.max(1, bc.getQuantaPerBlock() - level), state.get(VERTICAL_FLOW));
    }
    @Override public int getOpacity(BlockState state, BlockView world, BlockPos pos) {
        return ((BCFluid) fluid).getLightOpacity();
    }
    @Override public void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
        if (sticky) entity.slowMovement(state, new Vec3d(0.25, 0.05, 0.25));
        super.onEntityCollision(state, world, pos, entity);
    }
}
