/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.energy;

import buildcraft.lib.compat.MaterialBC;
import net.minecraft.block.AbstractBlock;

import buildcraft.api.enums.EnumEngineType;
import buildcraft.api.enums.EnumSpring;

import buildcraft.lib.item.ItemBlockBC_Neptune;
import buildcraft.lib.registry.RegistrationHelper;

import buildcraft.core.BCCoreBlocks;
import buildcraft.energy.blocks.BlockDynamoMJ;
import buildcraft.energy.tile.TileDynamoMJ;
import buildcraft.energy.tile.TileEngineIron_BC8;
import buildcraft.energy.tile.TileEngineRF;
import buildcraft.energy.tile.TileEngineStone_BC8;
import buildcraft.energy.tile.TileSpringOil;

public class BCEnergyBlocks {

    private static final RegistrationHelper HELPER = new RegistrationHelper();

    public static BlockDynamoMJ mjDynamo;

    public static void preInit() {

        if (BCCoreBlocks.engine != null) {
            BCCoreBlocks.engine.registerEngine(EnumEngineType.STONE, TileEngineStone_BC8.class);
            BCCoreBlocks.engine.registerEngine(EnumEngineType.IRON, TileEngineIron_BC8.class);
            if (BCEnergyConfig.enableRfEngine) {
                BCCoreBlocks.engine.registerEngine(EnumEngineType.RF, TileEngineRF.class);
            }
        }

        if (BCEnergyConfig.enableMjDynamo) {
            mjDynamo = HELPER.addForcedBlock(new BlockDynamoMJ(
                AbstractBlock.Settings.create().mapColor(net.minecraft.block.MapColor.IRON_GRAY)
                    .nonOpaque(), "block.mj_dynamo"));
            HELPER.addForcedItem(new ItemBlockBC_Neptune(mjDynamo));
            HELPER.registerTile(TileDynamoMJ.class, "tile.mj_dynamo", TileDynamoMJ::new, mjDynamo);
        }

        EnumSpring.OIL.liquidBlock = BCEnergyFluids.crudeOil[0].getBlock().getDefaultState();
        EnumSpring.OIL.tileConstructor = (pos, state) -> buildcraft.lib.registry.BlockEntityRegistry.create(TileSpringOil.class, pos, state);

        HELPER.registerTile(TileSpringOil.class, "tile.spring.oil", TileSpringOil::new, BCCoreBlocks.spring);
        HELPER.registerTile(TileEngineStone_BC8.class, "tile.engine.stone", TileEngineStone_BC8::new, BCCoreBlocks.engine);
        HELPER.registerTile(TileEngineIron_BC8.class, "tile.engine.iron", TileEngineIron_BC8::new, BCCoreBlocks.engine);
        HELPER.registerTile(TileEngineRF.class, "tile.engine.rf", TileEngineRF::new, BCCoreBlocks.engine);
    }
}
