/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.factory;

import buildcraft.lib.compat.MaterialBC;
import net.minecraft.block.AbstractBlock;

import buildcraft.lib.registry.RegistrationHelper;

import buildcraft.factory.block.BlockAutoWorkbenchItems;
import buildcraft.factory.block.BlockChute;
import buildcraft.factory.block.BlockDistiller;
import buildcraft.factory.block.BlockFloodGate;
import buildcraft.factory.block.BlockHeatExchange;
import buildcraft.factory.block.BlockMiningWell;
import buildcraft.factory.block.BlockPump;
import buildcraft.factory.block.BlockTank;
import buildcraft.factory.block.BlockTube;
import buildcraft.factory.block.BlockWaterGel;
import buildcraft.factory.tile.TileAutoWorkbenchItems;
import buildcraft.factory.tile.TileChute;
import buildcraft.factory.tile.TileDistiller_BC8;
import buildcraft.factory.tile.TileFloodGate;
import buildcraft.factory.tile.TileHeatExchange;
import buildcraft.factory.tile.TileMiningWell;
import buildcraft.factory.tile.TilePump;
import buildcraft.factory.tile.TileTank;

public class BCFactoryBlocks {

    private static final RegistrationHelper HELPER = new RegistrationHelper();

    public static BlockAutoWorkbenchItems autoWorkbenchItems;
    public static BlockMiningWell miningWell;
    public static BlockPump pump;
    public static BlockTube tube;
    public static BlockFloodGate floodGate;
    public static BlockTank tank;
    public static BlockChute chute;
    public static BlockDistiller distiller;
    public static BlockHeatExchange heatExchange;

    // public static BlockAutoWorkbenchFluids autoWorkbenchFluids;
    public static BlockWaterGel waterGel;

    public static void fmlPreInit() {
        autoWorkbenchItems = HELPER.addBlockAndItem("block.autoworkbench.item", () -> new BlockAutoWorkbenchItems(MaterialBC.ROCK, "block.autoworkbench.item"));
        miningWell = HELPER.addBlockAndItem("block.mining_well", () -> new BlockMiningWell(MaterialBC.IRON, "block.mining_well"));
        pump = HELPER.addBlockAndItem("block.pump", () -> new BlockPump(MaterialBC.IRON, "block.pump"));
        tube = HELPER.addBlock("block.tube", () -> new BlockTube(MaterialBC.IRON, "block.tube"));
        floodGate = HELPER.addBlockAndItem("block.flood_gate", () -> new BlockFloodGate(MaterialBC.IRON, "block.flood_gate"));
        tank = HELPER.addBlockAndItem("block.tank", () -> new BlockTank(
            AbstractBlock.Settings.create().mapColor(net.minecraft.block.MapColor.IRON_GRAY)
                .strength(0.5f).sounds(net.minecraft.sound.BlockSoundGroup.GLASS), "block.tank"));
        chute = HELPER.addBlockAndItem("block.chute", () -> new BlockChute(AbstractBlock.Settings.create()
            .mapColor(net.minecraft.block.MapColor.IRON_GRAY).nonOpaque().strength(5.0f, 10.0f)
            .sounds(net.minecraft.sound.BlockSoundGroup.METAL), "block.chute"));
        distiller = HELPER.addBlockAndItem("block.distiller", () -> new BlockDistiller(MaterialBC.IRON, "block.distiller"));
        heatExchange = HELPER.addBlockAndItem("block.heat_exchange", () -> new BlockHeatExchange(MaterialBC.IRON, "block.heat_exchange"));
        waterGel = HELPER.addBlock("block.water_gel", () -> new BlockWaterGel(MaterialBC.CLAY, "block.water_gel"));

        HELPER.registerTile(TileAutoWorkbenchItems.class, "tile.autoworkbench.item", TileAutoWorkbenchItems::new, autoWorkbenchItems);
        HELPER.registerTile(TileMiningWell.class, "tile.mining_well", TileMiningWell::new, miningWell);
        HELPER.registerTile(TilePump.class, "tile.pump", TilePump::new, pump);
        HELPER.registerTile(TileFloodGate.class, "tile.flood_gate", TileFloodGate::new, floodGate);
        HELPER.registerTile(TileTank.class, "tile.tank", TileTank::new, tank);
        HELPER.registerTile(TileChute.class, "tile.chute", TileChute::new, chute);
        HELPER.registerTile(TileDistiller_BC8.class, "tile.distiller", TileDistiller_BC8::new, distiller);
        HELPER.registerTile(TileHeatExchange.class, "tile.heat_exchange", TileHeatExchange::new, heatExchange);
    }
}
