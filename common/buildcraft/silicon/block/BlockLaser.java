/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 *
 * Ported to Fabric 1.20.1 by R.Chen (https://github.com/MantraChen).
 */
package buildcraft.silicon.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;

import buildcraft.lib.block.BlockBCTile_Neptune;
import buildcraft.lib.block.IBlockWithFacing;

import buildcraft.silicon.tile.TileLaser;

public class BlockLaser extends BlockBCTile_Neptune implements IBlockWithFacing {

    public BlockLaser(Settings settings, String id) {
        super(settings, id);
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return buildcraft.lib.registry.BlockEntityRegistry.create(TileLaser.class, pos, state);
    }

    @Override
    public boolean canFaceVertically() {
        return true;
    }
}
