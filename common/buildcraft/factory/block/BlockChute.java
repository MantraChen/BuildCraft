/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.factory.block;

import java.util.List;
import java.util.Map;

import buildcraft.lib.compat.MaterialBC;
import net.minecraft.block.AbstractBlock;
import net.minecraft.state.property.Property;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

import buildcraft.api.properties.BuildCraftProperties;

import buildcraft.lib.block.BlockBCTile_Neptune;
import buildcraft.lib.block.IBlockWithFacing;
import buildcraft.lib.tile.TileBC_Neptune;

import buildcraft.factory.BCFactoryGuis;
import buildcraft.factory.tile.TileChute;

public class BlockChute extends BlockBCTile_Neptune implements IBlockWithFacing {
    public static final Map<Direction, Property<Boolean>> CONNECTED_MAP = BuildCraftProperties.CONNECTED_MAP;

    public BlockChute(AbstractBlock.Settings material, String id) {
        super(material, id);
    }

    @Override
    public TileBC_Neptune createBlockEntity(net.minecraft.util.math.BlockPos pos, BlockState state) {
        return buildcraft.lib.registry.BlockEntityRegistry.create(TileChute.class, pos, state);
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, BlockState state, PlayerEntity player, Hand hand,
        Direction side, float hitX, float hitY, float hitZ) {
        if (!world.isClient) {
            BCFactoryGuis.CHUTE.openGUI(player, pos);
        }
        return true;
    }

    // @Override -- removed: method does not exist in Fabric 1.20.1
    public boolean isOpaqueCube(BlockState state) {
        return false;
    }

    // @Override -- removed: method does not exist in Fabric 1.20.1
    public boolean isFullCube(BlockState state) {
        return false;
    }

    @Override
    protected void addProperties(List<Property<?>> properties) {
        super.addProperties(properties);
        properties.addAll(CONNECTED_MAP.values());
    }

    private BlockState withConnections(BlockState state, BlockView world, BlockPos pos) {
        for (Direction side : Direction.values()) {
            state = state.with(CONNECTED_MAP.get(side), side != state.get(getFacingProperty())
                && TileChute.hasInventoryAtPosition(world, pos.offset(side), side));
        }
        return state;
    }

    @Override public BlockState getPlacementState(net.minecraft.item.ItemPlacementContext context) {
        return withConnections(super.getPlacementState(context), context.getWorld(), context.getBlockPos());
    }

    @Override public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
        super.onBlockAdded(state, world, pos, oldState, notify);
        if (!world.isClient) world.setBlockState(pos, withConnections(state, world, pos), net.minecraft.block.Block.NOTIFY_LISTENERS);
    }

    @Override public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighbor,
            net.minecraft.world.WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        return world.isClient() ? state : withConnections(state, world, pos);
    }

    @Override public net.minecraft.util.ActionResult attemptRotation(World world, BlockPos pos, BlockState state, Direction side) {
        if (!world.isClient) {
            Direction facing = buildcraft.lib.misc.RotationUtil.rotateAll(state.get(getFacingProperty()));
            world.setBlockState(pos, withConnections(state.with(getFacingProperty(), facing), world, pos));
        }
        return net.minecraft.util.ActionResult.SUCCESS;
    }

    @Override public BlockState rotate(BlockState state, net.minecraft.util.BlockRotation rotation) {
        BlockState rotated = super.rotate(state, rotation);
        for (Direction side : Direction.values()) rotated = rotated.with(CONNECTED_MAP.get(rotation.rotate(side)), state.get(CONNECTED_MAP.get(side)));
        return rotated;
    }

    @Override public BlockState mirror(BlockState state, net.minecraft.util.BlockMirror mirror) {
        BlockState mirrored = super.mirror(state, mirror);
        for (Direction side : Direction.values()) mirrored = mirrored.with(CONNECTED_MAP.get(mirror.apply(side)), state.get(CONNECTED_MAP.get(side)));
        return mirrored;
    }

    // IBlockWithFacing

    @Override
    public boolean canFaceVertically() {
        return true;
    }
}
