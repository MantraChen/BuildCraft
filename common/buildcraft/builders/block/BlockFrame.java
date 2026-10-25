/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.block;

import java.util.List;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.block.AbstractBlock;
import net.minecraft.state.property.Property;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;

import buildcraft.api.properties.BuildCraftProperties;

import buildcraft.lib.block.BlockBCBase_Neptune;

public class BlockFrame extends BlockBCBase_Neptune {
    public static final Map<Direction, Property<Boolean>> CONNECTED_MAP = BuildCraftProperties.CONNECTED_MAP;

    public static final Box BASE_AABB = new Box(4 / 16D, 4 / 16D, 4 / 16D, 12 / 16D, 12 / 16D, 12 / 16D);
    public static final Box CONNECTION_AABB = new Box(4 / 16D, 0 / 16D, 4 / 16D, 12 / 16D, 4 / 16D, 12 / 16D);
    private static final VoxelShape[] SHAPES = createShapes();

    public BlockFrame(AbstractBlock.Settings material, String id) {
        super(material, id);
    }

    @Override
    protected void addProperties(List<Property<?>> properties) {
        super.addProperties(properties);
        properties.addAll(CONNECTED_MAP.values());
    }

    private BlockState withConnections(BlockState state, BlockView world, BlockPos pos) {
        for (Direction side : CONNECTED_MAP.keySet()) {
            Block block = world.getBlockState(pos.offset(side)).getBlock();
            state = state.with(CONNECTED_MAP.get(side), block instanceof BlockFrame || block instanceof BlockQuarry);
        }
        return state;
    }

    @Override public BlockState getPlacementState(ItemPlacementContext context) {
        return withConnections(super.getPlacementState(context), context.getWorld(), context.getBlockPos());
    }

    @Override public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
        super.onBlockAdded(state, world, pos, oldState, notify);
        if (!world.isClient) world.setBlockState(pos, withConnections(state, world, pos), Block.NOTIFY_LISTENERS);
    }

    @Override public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighbor,
            WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        return withConnections(state, world, pos);
    }

    @Override public BlockState rotate(BlockState state, BlockRotation rotation) {
        BlockState rotated = state;
        for (Direction side : Direction.values()) rotated = rotated.with(CONNECTED_MAP.get(rotation.rotate(side)), state.get(CONNECTED_MAP.get(side)));
        return rotated;
    }

    @Override public BlockState mirror(BlockState state, BlockMirror mirror) {
        BlockState mirrored = state;
        for (Direction side : Direction.values()) mirrored = mirrored.with(CONNECTED_MAP.get(mirror.apply(side)), state.get(CONNECTED_MAP.get(side)));
        return mirrored;
    }

    @Override public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        int mask = 0;
        for (Direction side : Direction.values()) if (state.get(CONNECTED_MAP.get(side))) mask |= 1 << side.ordinal();
        return SHAPES[mask];
    }

    private static VoxelShape[] createShapes() {
        VoxelShape[] arms = {
            Block.createCuboidShape(4, 0, 4, 12, 4, 12), Block.createCuboidShape(4, 12, 4, 12, 16, 12),
            Block.createCuboidShape(4, 4, 0, 12, 12, 4), Block.createCuboidShape(4, 4, 12, 12, 12, 16),
            Block.createCuboidShape(0, 4, 4, 4, 12, 12), Block.createCuboidShape(12, 4, 4, 16, 12, 12)
        };
        VoxelShape[] shapes = new VoxelShape[64];
        for (int mask = 0; mask < shapes.length; mask++) {
            VoxelShape shape = VoxelShapes.cuboid(BASE_AABB);
            for (Direction side : Direction.values()) if ((mask & (1 << side.ordinal())) != 0)
                shape = VoxelShapes.union(shape, arms[side.ordinal()]);
            shapes[mask] = shape;
        }
        return shapes;
    }
}
