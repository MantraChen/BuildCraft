/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 *
 * Ported to Fabric 1.20.1 by R.Chen (https://github.com/MantraChen).
 */
package buildcraft.core;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import buildcraft.lib.compat.forge_stubs.OreDictionaryStub;

import buildcraft.BuildCraftFabric;
import buildcraft.lib.BCLibInitializer;

/**
 * Fabric ModInitializer for the BuildCraft core module.
 * Replaces the Forge {@code @Mod}-annotated {@link BCCore} entry point.
 *
 * Lifecycle mapping (Forge {@link BCCore} → Fabric):
 *   FMLPreInitializationEvent  → {@link #onInitialize()} (config, registry, statements, recipes)
 *   FMLInitializationEvent     → {@link #onInitialize()} (folded; marker-cache registration)
 *   FMLPostInitializationEvent → {@link #onInitialize()} (folded; config save)
 *   NetworkRegistry GUI handler → Fabric {@code ExtendedScreenHandlerType} (deferred)
 *   MinecraftForge.EVENT_BUS    → individual Fabric {@code Event} callbacks
 *   WorldEvent.Unload           → {@link ServerWorldEvents#UNLOAD}
 *
 * Native registration uses shared metadata without executing the old Forge lifecycle.
 */
public class BCCoreInitializer implements ModInitializer {

    public static final String MODID = "buildcraftcore";

    @Override
    public void onInitialize() {
        BuildCraftFabric.LOGGER.info("[BCCore] Initializing buildcraft-core (Fabric 1.20.1)");

        // buildcraftcore depends on buildcraftlib; Fabric load order is declared in
        // fabric.mod.json ("depends"). Touch the lib MODID so the wiring is explicit.
        BuildCraftFabric.LOGGER.info("[BCCore] Depends on {} (lib entrypoint already built)", BCLibInitializer.MODID);

        // --- Forge preInit (BCCore.preInit) -----------------------------------
        BCCoreConfig.preInit(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("buildcraft").toFile());
        BCCoreTags.register();
        BCCoreBlocks.preInit();
        BCCoreItems.preInit();
        buildcraft.lib.BCLibItems.enableGuide();
        buildcraft.lib.BCLibItems.enableDebugger();
        buildcraft.lib.BCLibItems.fmlPreInit();
        // STUB(R.Chen): CreativeTabManager.createTab("buildcraft.main") — port to
        //               FabricItemGroup once lib.registry is migrated.
        // TODO: wire statements and native recipes.
        // STUB(R.Chen): NetworkRegistry GUI handler — port to ExtendedScreenHandlerType.
        // STUB(R.Chen): OreDictionaryStub.registerOre("craftingTableWood", ...) — port to
        //               Fabric item tags (c:crafting_tables / data-driven tag json).

        // --- Forge init (BCCore.init) -----------------------------------------
        buildcraft.lib.marker.MarkerCache.registerCache(buildcraft.core.marker.VolumeCache.INSTANCE);
        buildcraft.lib.marker.MarkerCache.registerCache(buildcraft.core.marker.PathCache.INSTANCE);
        buildcraft.lib.marker.MarkerCache.postInit();

        // --- Forge postInit (BCCore.postInit) ---------------------------------
        // STUB(R.Chen): BCCoreConfig.saveConfigs() / postInit().

        registerLifecycleEvents();

        BuildCraftFabric.LOGGER.info("[BCCore] Initialization complete");
    }

    private static void registerLifecycleEvents() {
        // Forge: FMLServerStartingEvent
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            // STUB(R.Chen): no core-specific server-start hook yet.
        });

        // Forge: MinecraftForge.EVENT_BUS.register(BCCoreEventDist.INSTANCE)
        // STUB(R.Chen): BCCoreEventDist subscribes to volume-box world load/save and
        //               player events — port each to its Fabric Event once
        //               core.marker.volume is migrated.

        // Forge: WorldEvent.Unload (server side)
        ServerWorldEvents.UNLOAD.register((server, world) -> {
            // STUB(R.Chen): VolumeCache / PathCache per-world cleanup.
        });
    }
}
