/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 *
 * Ported to Fabric 1.20.1 by R.Chen (https://github.com/MantraChen).
 */
package buildcraft.builders;

import net.fabricmc.api.ModInitializer;

import buildcraft.BuildCraftFabric;
import buildcraft.lib.BCLibInitializer;

/**
 * Fabric ModInitializer for the BuildCraft builders module.
 * Replaces the Forge {@code @Mod}-annotated {@link BCBuilders} entry point.
 *
 * Lifecycle mapping (Forge {@link BCBuilders} → Fabric):
 *   FMLPreInitializationEvent  → {@link #onInitialize()} (config, registry, proxy)
 *   FMLInitializationEvent     → {@link #onInitialize()} (recipes — folded)
 *   FMLPostInitializationEvent → {@link #onInitialize()} (folded; no-op at this stage)
 *   NetworkRegistry GUI handler → Fabric {@code ExtendedScreenHandlerType} (deferred)
 *   MinecraftForge.EVENT_BUS    → individual Fabric {@code Event} callbacks (deferred)
 *
 * Blocks, items and tile factories register here; schematic and screen integration remains.
 */
public class BCBuildersInitializer implements ModInitializer {

    public static final String MODID = "buildcraftbuilders";

    @Override
    public void onInitialize() {
        BuildCraftFabric.LOGGER.info("[BCBuilders] Initializing buildcraft-builders (Fabric 1.20.1)");

        BuildCraftFabric.LOGGER.info(
            "[BCBuilders] Depends on {} (lib entrypoint already built)", BCLibInitializer.MODID
        );

        // --- Forge preInit (BCBuilders.preInit) ----------------------------------
        BCBuildersConfig.preInit();
        BCBuildersTags.register();
        BCBuildersItems.fmlPreInit();
        BCBuildersBlocks.fmlPreInit();
        BCBuildersBlocks.fmlInit();
        // STUB(R.Chen): NetworkRegistry GUI handler → ExtendedScreenHandlerType for:
        //               ARCHITECT_TABLE, BUILDER, FILLER, LIBRARY, REPLACER, QUARRY.
        // STUB(R.Chen): MinecraftForge.EVENT_BUS.register(BCBuildersEventDist.INSTANCE) — port the quarry
        //               validate/invalidate world-event-listener bookkeeping to Fabric callbacks.

        // --- Forge init (BCBuilders.init) ----------------------------------------
        // STUB(R.Chen): BCBuildersRecipes.init() — architect/library/snapshot recipes deferred.
        // STUB(R.Chen): BCBuildersSchematics.init() — schematic factory registration deferred until the
        //               snapshot/schematic serialization layer is ported.

        // --- Forge postInit (BCBuilders.postInit) --------------------------------
        // STUB(R.Chen): BCBuildersStatements.init() / action providers deferred.
        buildcraft.core.BCCoreConfig.postInit();
    }
}
