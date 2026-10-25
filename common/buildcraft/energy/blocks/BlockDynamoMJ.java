/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.energy.blocks;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import buildcraft.api.blocks.ICustomRotationHandler;

import buildcraft.lib.block.BlockBCTile_Neptune;

import buildcraft.energy.tile.TileDynamoMJ;

public class BlockDynamoMJ extends BlockBCTile_Neptune implements buildcraft.lib.block.IBlockWithFacing {

    public BlockDynamoMJ(AbstractBlock.Settings settings, String id) {
        super(settings, id);
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return buildcraft.lib.registry.BlockEntityRegistry.create(TileDynamoMJ.class, pos, state);
    }

    @Override public boolean canFaceVertically() { return true; }

    @Override
    public int getOpacity(BlockState state, net.minecraft.world.BlockView world, BlockPos pos) {
        return 0;
    }
}
