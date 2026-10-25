/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 *
 * Ported to Fabric 1.20.1 by R.Chen (https://github.com/MantraChen).
 */
package buildcraft.energy;

import net.fabricmc.api.ModInitializer;

import buildcraft.BuildCraftFabric;
import buildcraft.lib.BCLibInitializer;

/**
 * Fabric ModInitializer for the BuildCraft energy module.
 * Replaces the Forge {@code @Mod}-annotated {@link BCEnergy} entry point.
 *
 * Lifecycle mapping (Forge BCEnergy → Fabric):
 *   FMLPreInitializationEvent  → {@link #onInitialize()} (config, registry, proxy)
 *   FMLInitializationEvent     → {@link #onInitialize()} (recipes, world gen — folded)
 *   FMLPostInitializationEvent → {@link #onInitialize()} (biome validation — folded)
 *   NetworkRegistry GUI handler → Fabric ExtendedScreenHandlerType
 *   MinecraftForge.EVENT_BUS    → individual Fabric Event callbacks (deferred)
 *
 * Blocks, fluids, buckets, items and tile factories register here. World generation remains unfinished.
 */
public class BCEnergyInitializer implements ModInitializer {

    public static final String MODID = "buildcraftenergy";

    @Override
    public void onInitialize() {
        BuildCraftFabric.LOGGER.info("[BCEnergy] Initializing buildcraft-energy (Fabric 1.20.1)");

        BuildCraftFabric.LOGGER.info(
            "[BCEnergy] Depends on {} (lib entrypoint already built)", BCLibInitializer.MODID
        );

        // --- Forge preInit (BCEnergy.preInit) ------------------------------------
        BCEnergyConfig.preInit();
        BCEnergyTags.register();
        BCEnergyFluids.register(BCEnergyConfig.oilIsSticky, BCEnergyConfig.enableOilBurn);
        BCEnergyBlocks.preInit();
        BCEnergyItems.preInit();
        BCEnergyScreens.register();
        buildcraft.energy.tile.TileEngineRF.RF_UPGRADE.put(buildcraft.core.BCCoreItems.gearIron, 2 * buildcraft.api.mj.MjAPI.MJ);
        buildcraft.energy.tile.TileEngineRF.RF_UPGRADE.put(buildcraft.core.BCCoreItems.gearGold, 3 * buildcraft.api.mj.MjAPI.MJ);
        if (BCEnergyConfig.enableRfEngine) team.reborn.energy.api.EnergyStorage.SIDED.registerForBlockEntity(
            (tile, side) -> tile.rfEnergyStorage,
            buildcraft.lib.registry.BlockEntityRegistry.type(buildcraft.energy.tile.TileEngineRF.class));
        if (BCEnergyBlocks.mjDynamo != null) team.reborn.energy.api.EnergyStorage.SIDED.registerForBlockEntity(
            (tile, side) -> side == tile.facing() ? tile.energyStorage : null,
            buildcraft.lib.registry.BlockEntityRegistry.type(buildcraft.energy.tile.TileDynamoMJ.class));
        // STUB(R.Chen): BCEnergyEntities.preInit() — no-op in 1.12.2; kept for parity.
        // Client model/sprite/render registration is owned by BCLibClientInitializer.

        // --- Forge init (BCEnergy.init) ------------------------------------------
        BCEnergyRecipes.init();
        // STUB(R.Chen): BCEnergyWorldGen.init() — oil biome/structure generation deferred.

        // --- Forge postInit (BCEnergy.postInit) ----------------------------------
        // STUB(R.Chen): BCEnergyConfig.validateBiomeNames() — deferred with world gen.
        // STUB(R.Chen): MigrationManager block migrations — deferred until fluids land.
    }
}
