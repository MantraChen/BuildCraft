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

import buildcraft.api.enums.EnumLaserTableType;
import buildcraft.api.mj.ILaserTargetBlock;

import buildcraft.lib.block.BlockBCTile_Neptune;

import buildcraft.silicon.tile.TileAdvancedCraftingTable;
import buildcraft.silicon.tile.TileAssemblyTable;
import buildcraft.silicon.tile.TileChargingTable;
import buildcraft.silicon.tile.TileIntegrationTable;
import buildcraft.silicon.tile.TileProgrammingTable_Neptune;

/** Original nine-pixel table profile; each tile handles its native interaction. */
public class BlockLaserTable extends BlockBCTile_Neptune implements ILaserTargetBlock {
    private final EnumLaserTableType type;

    public BlockLaserTable(EnumLaserTableType type, Settings settings, String id) {
        super(settings, id);
        this.type = type;
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        switch (type) {
            case ASSEMBLY_TABLE:
                return buildcraft.lib.registry.BlockEntityRegistry.create(TileAssemblyTable.class, pos, state);
            case ADVANCED_CRAFTING_TABLE:
                return buildcraft.lib.registry.BlockEntityRegistry.create(TileAdvancedCraftingTable.class, pos, state);
            case INTEGRATION_TABLE:
                return buildcraft.lib.registry.BlockEntityRegistry.create(TileIntegrationTable.class, pos, state);
            case CHARGING_TABLE:
                return buildcraft.lib.registry.BlockEntityRegistry.create(TileChargingTable.class, pos, state);
            case PROGRAMMING_TABLE:
                return buildcraft.lib.registry.BlockEntityRegistry.create(TileProgrammingTable_Neptune.class, pos, state);
            default:
                return null;
        }
    }

    @Override public net.minecraft.util.shape.VoxelShape getOutlineShape(BlockState state,net.minecraft.world.BlockView world,BlockPos pos,net.minecraft.block.ShapeContext context) {
        return net.minecraft.block.Block.createCuboidShape(0,0,0,16,9,16);
    }
}
