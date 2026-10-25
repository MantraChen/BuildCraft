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
        // STUB(R.Chen): BCEnergyEntities.preInit() — no-op in 1.12.2; kept for parity.
        // Remaining native screens: ENGINE_RF and DYNAMO_MJ.
        // Client model/sprite/render registration is owned by BCLibClientInitializer.
        // STUB(R.Chen): EnergyStorage.SIDED.registerForBlockEntityType(
        //               (be, side) -> be.rfEnergyStorage, ENGINE_RF_TYPE, null)
        //               — Team Reborn registration deferred until ENGINE_RF_TYPE is registered.

        // --- Forge init (BCEnergy.init) ------------------------------------------
        BCEnergyRecipes.init();
        // STUB(R.Chen): BCEnergyWorldGen.init() — oil biome/structure generation deferred.

        // --- Forge postInit (BCEnergy.postInit) ----------------------------------
        // STUB(R.Chen): BCEnergyConfig.validateBiomeNames() — deferred with world gen.
        // STUB(R.Chen): MigrationManager block migrations — deferred until fluids land.
    }
}
