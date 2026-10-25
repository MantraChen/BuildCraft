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
 * Blocks, items, schematic factories, builder/filler screens and reloadable rules register here.
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
        BCBuildersRegistries.preInit();
        BCBuildersStatements.preInit();
        BCBuildersRegistries.init();
        BCBuildersSchematics.preInit();
        BCBuildersItems.fmlPreInit();
        BCBuildersBlocks.fmlPreInit();
        BCBuildersBlocks.fmlInit();
        buildcraft.lib.chunkload.ChunkLoaderManager.register();
        net.fabricmc.fabric.api.transfer.v1.item.ItemStorage.SIDED.registerForBlockEntity(
            (tile,face)->net.fabricmc.fabric.api.transfer.v1.storage.Storage.empty(),
            buildcraft.lib.registry.BlockEntityRegistry.type(buildcraft.builders.tile.TileQuarry.class));
        BCBuildersScreens.register();
        buildcraft.builders.snapshot.RulesLoader.loadAll();
        buildcraft.builders.snapshot.RulesLoader.registerReloadListener();
        // STUB(R.Chen): NetworkRegistry GUI handler → ExtendedScreenHandlerType for:
        //               ARCHITECT_TABLE, LIBRARY, REPLACER, QUARRY.

        // --- Forge init (BCBuilders.init) ----------------------------------------
        // STUB(R.Chen): BCBuildersRecipes.init() — architect/library/snapshot recipes deferred.

        // --- Forge postInit (BCBuilders.postInit) --------------------------------
        buildcraft.core.BCCoreConfig.postInit();
    }
}
