/* Copyright (c) 2016 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.factory.tile;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.util.math.BlockPos;

public class TileAutoWorkbenchItems extends TileAutoWorkbenchBase {
    public TileAutoWorkbenchItems(BlockEntityType<?> type,BlockPos pos,BlockState state) {
        super(type,pos,state,3,3);
    }
}
