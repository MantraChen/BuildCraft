/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.block;

import java.util.Arrays;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.sound.BlockSoundGroup;
import buildcraft.lib.compat.MaterialBC;
import net.minecraft.block.AbstractBlock;
import net.minecraft.state.property.Property;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.Direction;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

import buildcraft.api.properties.BuildCraftProperties;

import buildcraft.lib.block.BlockBCTile_Neptune;
import buildcraft.lib.block.IBlockWithFacing;
import buildcraft.lib.misc.AdvancementUtil;
import buildcraft.lib.tile.TileBC_Neptune;

import buildcraft.builders.BCBuildersBlocks;
import buildcraft.builders.tile.TileQuarry;

public class BlockQuarry extends BlockBCTile_Neptune implements IBlockWithFacing {
    private static final Identifier ADVANCEMENT = new Identifier("buildcraftbuilders:shaping_the_world");

    public BlockQuarry(AbstractBlock.Settings material, String id) {
        super(material, id);
    }

    @Override
    protected void addProperties(List<Property<?>> properties) {
        super.addProperties(properties);
        properties.addAll(BuildCraftProperties.CONNECTED_MAP.values());
    }

    private boolean isConnected(BlockView world, BlockPos pos, BlockState state, Direction side) {
        Direction facing = side;
        if (facing.getAxis().isHorizontal()) {
            facing = Direction.fromHorizontal(
                side.getHorizontal() + 2 + state.get(getFacingProperty()).getHorizontal());
        }
        BlockPos neighbor = pos.offset(facing);
        return world instanceof World level && level.isChunkLoaded(neighbor)
            && net.fabricmc.fabric.api.transfer.v1.item.ItemStorage.SIDED.find(level, neighbor, facing.getOpposite()) != null;
    }

    private BlockState withConnections(BlockState state, BlockView world, BlockPos pos) {
        for (Direction face : Direction.values()) {
            state =
                state.with(BuildCraftProperties.CONNECTED_MAP.get(face), isConnected(world, pos, state, face));
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
        // Inventory lookups are authoritative on the server; clients consume the synced state.
        return world.isClient() ? state : withConnections(state, world, pos);
    }

    @Override
    public TileBC_Neptune createBlockEntity(net.minecraft.util.math.BlockPos pos, BlockState state) {
        return buildcraft.lib.registry.BlockEntityRegistry.create(TileQuarry.class, pos, state);
    }

    @Override
    public boolean canBeRotated(World world, BlockPos pos, BlockState state) {
        return false;
    }

    @Override
    public void breakBlock(World world, BlockPos pos, BlockState state) {
        super.breakBlock(world, pos, state);
    }

    // @Override -- removed: method does not exist in Fabric 1.20.1
    public BlockSoundGroup getSoundType(BlockState state, World world, BlockPos pos, @Nullable Entity entity) {
        return BlockSoundGroup.ANVIL;
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, BlockState state, LivingEntity placer,
        ItemStack stack) {
        super.onBlockPlacedBy(world, pos, state, placer, stack);
        if (placer instanceof PlayerEntity) {
            AdvancementUtil.unlockAdvancement((PlayerEntity) placer, ADVANCEMENT);
        }
    }
}
