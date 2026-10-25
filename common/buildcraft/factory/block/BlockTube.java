/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.factory.block;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import buildcraft.factory.tile.TileMiner;
import buildcraft.lib.block.BlockBCBase_Neptune;

public class BlockTube extends BlockBCBase_Neptune {
    private static final VoxelShape SHAPE = VoxelShapes.cuboid(.25, 0, .25, .75, 1, .75);
    public BlockTube(AbstractBlock.Settings settings, String id) { super(settings.strength(-1, 3_600_000).nonOpaque(), id); }
    @Override public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) { return SHAPE; }
    @Override public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) { return SHAPE; }
    /** A connected tube belongs to its miner, including during creative-mode breaking. */
    public boolean isConnectedToMiner(World world, BlockPos pos) {
        BlockPos current = pos.up();
        while (world.isInBuildLimit(current) && world.getBlockState(current).isOf(this)) current = current.up();
        return world.isInBuildLimit(current) && world.getBlockEntity(current) instanceof TileMiner;
    }
}
