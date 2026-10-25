/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.lib.block;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import buildcraft.api.blocks.ICustomRotationHandler;

public abstract class BlockMarkerBase extends BlockBCTile_Neptune implements IBlockWithFacing {

    public BlockMarkerBase(AbstractBlock.Settings settings, String id) {
        super(settings, id);
        setDefaultState(getDefaultState().with(BLOCK_FACING_6, Direction.UP));
    }

    @Override public boolean canFaceVertically() { return true; }

    @Override
    public BlockState getPlacementState(net.minecraft.item.ItemPlacementContext context) {
        BlockState state = getDefaultState().with(BLOCK_FACING_6, context.getSide());
        return state.canPlaceAt(context.getWorld(), context.getBlockPos()) ? state : null;
    }

    @Override
    public boolean canPlaceAt(BlockState state, net.minecraft.world.WorldView world, BlockPos pos) {
        Direction facing = state.get(BLOCK_FACING_6);
        BlockPos support = pos.offset(facing.getOpposite());
        return world.getBlockState(support).isSideSolidFullSquare(world, support, facing);
    }

    @Override
    public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighbor,
            net.minecraft.world.WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        return !state.canPlaceAt(world, pos) ? net.minecraft.block.Blocks.AIR.getDefaultState()
            : super.getStateForNeighborUpdate(state, direction, neighbor, world, pos, neighborPos);
    }

    @Override
    public net.minecraft.util.shape.VoxelShape getOutlineShape(BlockState state, net.minecraft.world.BlockView world,
            BlockPos pos, net.minecraft.block.ShapeContext context) {
        return switch (state.get(BLOCK_FACING_6)) {
            case UP -> net.minecraft.block.Block.createCuboidShape(7, 0, 7, 9, 9, 9);
            case DOWN -> net.minecraft.block.Block.createCuboidShape(7, 7, 7, 9, 16, 9);
            case NORTH -> net.minecraft.block.Block.createCuboidShape(7, 7, 7, 9, 9, 16);
            case SOUTH -> net.minecraft.block.Block.createCuboidShape(7, 7, 0, 9, 9, 9);
            case WEST -> net.minecraft.block.Block.createCuboidShape(7, 7, 7, 16, 9, 9);
            case EAST -> net.minecraft.block.Block.createCuboidShape(0, 7, 7, 9, 9, 9);
        };
    }

    @Override
    public ActionResult attemptRotation(World world, BlockPos pos, BlockState state, Direction sideWrenched) {
        BlockState rotated = state.with(BLOCK_FACING_6, sideWrenched);
        if (rotated.equals(state) || !rotated.canPlaceAt(world, pos)) return ActionResult.PASS;
        if (!world.isClient) world.setBlockState(pos, rotated);
        return ActionResult.SUCCESS;
    }
}
