/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 *
 * Ported to Fabric 1.20.1 by R.Chen (https://github.com/MantraChen).
 */
package buildcraft.silicon;

import buildcraft.silicon.block.BlockLaser;
import buildcraft.silicon.block.BlockLaserTable;
import buildcraft.api.enums.EnumLaserTableType;
import buildcraft.lib.BCLib;
import buildcraft.lib.registry.RegistrationHelper;
import buildcraft.silicon.tile.*;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.MapColor;

public class BCSiliconBlocks {
    private static final RegistrationHelper HELPER = new RegistrationHelper();

    public static BlockLaser laser;
    public static BlockLaserTable assemblyTable;
    public static BlockLaserTable advancedCraftingTable;
    public static BlockLaserTable integrationTable;
    public static BlockLaserTable chargingTable;
    public static BlockLaserTable programmingTable;

    public static void preInit() {
        laser = HELPER.addBlockAndItem("block.laser", () -> new BlockLaser(settings(), "block.laser"));
        assemblyTable = table(EnumLaserTableType.ASSEMBLY_TABLE, "block.assembly_table");
        advancedCraftingTable = table(EnumLaserTableType.ADVANCED_CRAFTING_TABLE, "block.advanced_crafting_table");
        integrationTable = table(EnumLaserTableType.INTEGRATION_TABLE, "block.integration_table");
        if (BCLib.DEV) {
            chargingTable = table(EnumLaserTableType.CHARGING_TABLE, "block.charging_table");
            programmingTable = table(EnumLaserTableType.PROGRAMMING_TABLE, "block.programming_table");
        }
        HELPER.registerTile(TileLaser.class, "tile.laser", TileLaser::new, laser);
        HELPER.registerTile(TileAssemblyTable.class, "tile.assembly_table", TileAssemblyTable::new, assemblyTable);
        HELPER.registerTile(TileAdvancedCraftingTable.class, "tile.advanced_crafting_table", TileAdvancedCraftingTable::new, advancedCraftingTable);
        HELPER.registerTile(TileIntegrationTable.class, "tile.integration_table", TileIntegrationTable::new, integrationTable);
        HELPER.registerTile(TileChargingTable.class, "tile.charging_table", TileChargingTable::new, chargingTable);
        HELPER.registerTile(TileProgrammingTable_Neptune.class, "tile.programming_table", TileProgrammingTable_Neptune::new, programmingTable);
    }

    private static AbstractBlock.Settings settings() {
        return AbstractBlock.Settings.create().mapColor(MapColor.IRON_GRAY).strength(5.0F, 10.0F).nonOpaque();
    }

    private static BlockLaserTable table(EnumLaserTableType type, String id) {
        return HELPER.addBlockAndItem(id, () -> new BlockLaserTable(type, settings(), id));
    }
}
