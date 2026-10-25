/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.core;

import buildcraft.lib.compat.MaterialBC;

import buildcraft.api.enums.EnumEngineType;

import buildcraft.lib.BCLib;
import buildcraft.lib.registry.RegistrationHelper;

import buildcraft.core.block.BlockDecoration;
import buildcraft.core.block.BlockEngine_BC8;
import buildcraft.core.block.BlockMarkerPath;
import buildcraft.core.block.BlockMarkerVolume;
import buildcraft.core.block.BlockPowerConsumerTester;
import buildcraft.core.block.BlockSpring;
import buildcraft.core.item.ItemBlockDecorated;
import buildcraft.core.item.ItemBlockSpring;
import buildcraft.core.item.ItemEngine_BC8;
import buildcraft.core.tile.TileEngineCreative;
import buildcraft.core.tile.TileEngineRedstone_BC8;
import buildcraft.core.tile.TileMarkerPath;
import buildcraft.core.tile.TileMarkerVolume;
import buildcraft.core.tile.TilePowerConsumerTester;

public class BCCoreBlocks {

    private static final RegistrationHelper HELPER = new RegistrationHelper();

    public static BlockEngine_BC8 engine;
    public static BlockSpring spring;
    public static BlockDecoration decorated;
    public static BlockMarkerVolume markerVolume;
    public static BlockMarkerPath markerPath;
    public static BlockPowerConsumerTester powerTester;

    public static void preInit() {
        spring = HELPER.addBlockAndItem("block.spring", () -> new BlockSpring("block.spring"), ItemBlockSpring::new);
        decorated = HELPER.addBlockAndItem("block.decorated", () -> new BlockDecoration("block.decorated"), ItemBlockDecorated::new);
        markerVolume = HELPER.addBlockAndItem("block.marker.volume", () -> new BlockMarkerVolume(markerSettings(), "block.marker.volume"));
        markerPath = HELPER.addBlockAndItem("block.marker.path", () -> new BlockMarkerPath(markerSettings(), "block.marker.path"));
        if (BCLib.DEV) {
            powerTester = HELPER.addBlockAndItem("block.power_tester", () -> new BlockPowerConsumerTester(MaterialBC.IRON, "block.power_tester"));
        }

        engine = HELPER.addBlockAndItem("block.engine.bc", () -> new BlockEngine_BC8(
            net.minecraft.block.AbstractBlock.Settings.create().mapColor(net.minecraft.block.MapColor.IRON_GRAY)
                .nonOpaque(), "block.engine.bc"), ItemEngine_BC8::new);
        if (engine != null) {
            engine.registerEngine(EnumEngineType.WOOD, TileEngineRedstone_BC8.class);
            engine.registerEngine(EnumEngineType.CREATIVE, TileEngineCreative.class);
        }

        HELPER.registerTile(TileMarkerVolume.class, "tile.marker.volume", TileMarkerVolume::new, markerVolume);
        HELPER.registerTile(TileMarkerPath.class, "tile.marker.path", TileMarkerPath::new, markerPath);
        HELPER.registerTile(TileEngineRedstone_BC8.class, "tile.engine.wood", TileEngineRedstone_BC8::new, engine);
        HELPER.registerTile(TileEngineCreative.class, "tile.engine.creative", TileEngineCreative::new, engine);
        if (BCLib.DEV) {
            HELPER.registerTile(TilePowerConsumerTester.class, "tile.power_tester", TilePowerConsumerTester::new, powerTester);
        }
    }

    private static net.minecraft.block.AbstractBlock.Settings markerSettings() {
        return net.minecraft.block.AbstractBlock.Settings.create().nonOpaque().noCollision()
            .sounds(net.minecraft.sound.BlockSoundGroup.WOOD).luminance(state -> 7);
    }
}
