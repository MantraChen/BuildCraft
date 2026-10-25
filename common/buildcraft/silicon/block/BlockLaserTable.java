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

/**
 * Screen opening and table shape still require native client integration.
 * BCSiliconGuis.openGui deferred until ExtendedScreenHandlerType is registered.
 * isOpaqueCube/isFullCube/getBlockLayer do not exist in 1.20.1 — removed.
 */
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

    // STUB(R.Chen): onUse (was onBlockActivated) deferred — BCSiliconGuis.openGui needs
    // ExtendedScreenHandlerType registration first.
    // STUB(R.Chen): getBoundingBox/VoxelShape (was Box) deferred to lib.block phase.

    // @Override removed (R.Chen): no longer overrides — Phase 10
    public boolean canUse(net.minecraft.entity.player.PlayerEntity player) { return true; }
}
