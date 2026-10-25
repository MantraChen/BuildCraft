/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 *
 * Ported to Fabric 1.20.1 by R.Chen (https://github.com/MantraChen).
 */
package buildcraft.lib;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;

import buildcraft.BuildCraftFabric;

/**
 * Fabric ModInitializer for the BuildCraft lib module.
 * Replaces the Forge {@code @Mod}-annotated {@link BCLib} entry point.
 *
 * Lifecycle mapping (Forge → Fabric):
 *   FMLPreInitializationEvent  → {@link #onInitialize()} (common-side init)
 *   FMLInitializationEvent     → {@link #onInitialize()} (folded; Fabric has no equivalent split)
 *   FMLPostInitializationEvent → {@link #onInitialize()} (folded; defer via Loom hooks if needed)
 *   FMLServerStartingEvent     → {@link ServerLifecycleEvents#SERVER_STARTING}
 *   ServerTickEvent (END)      → {@link ServerTickEvents#END_SERVER_TICK}
 *   WorldEvent.Unload          → {@link ServerWorldEvents#UNLOAD}
 */
public class BCLibInitializer implements ModInitializer {

    public static final String MODID = "buildcraftlib";

    @Override
    public void onInitialize() {
        BuildCraftFabric.LOGGER.info("[BCLib] Initializing buildcraft-lib (Fabric 1.20.1)");
        buildcraft.api.core.BuildCraftAPI.fakePlayerProvider = buildcraft.lib.misc.FakePlayerProvider.INSTANCE;

        net.fabricmc.fabric.api.transfer.v1.item.ItemStorage.SIDED.registerFallback(
            (world, pos, state, blockEntity, side) -> blockEntity instanceof buildcraft.lib.tile.TileBC_Neptune tile
                ? tile.getItemStorage(side) : null);
        buildcraft.api.mj.MjAPI.CONNECTOR.registerFallback(
            (world, pos, state, blockEntity, side) -> blockEntity instanceof buildcraft.lib.tile.TileBC_Neptune tile
                ? tile.getMjConnector(side) : null);
        buildcraft.api.registry.BuildCraftRegistryManager.managerDataPacks = buildcraft.lib.script.ReloadableRegistryManager.DATA_PACKS;
        net.fabricmc.fabric.api.resource.ResourceManagerHelper.get(net.minecraft.resource.ResourceType.SERVER_DATA)
            .registerReloadListener(buildcraft.lib.script.ReloadableRegistryManager.DATA_PACKS);

        // STUB(R.Chen): registry hookups — wire up once BCLibBlocks/BCLibItems are migrated.
        // Registry.register(Registries.BLOCK, ...);
        // Registry.register(Registries.ITEM, ...);
        // Registry.register(Registries.BLOCK_ENTITY_TYPE, ...);

        buildcraft.lib.net.BCNetworkManager.registerCommon();

        registerLifecycleEvents();

        BuildCraftFabric.LOGGER.info("[BCLib] Initialization complete");
    }

    private static void registerLifecycleEvents() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof buildcraft.lib.tile.TileBC_Neptune tile) {
                // Fabric may notify before NBT is applied; initialize on the next game tick.
                buildcraft.lib.misc.MessageUtil.doDelayedServer(world.getServer(), () -> {
                    if (!tile.isRemoved() && world.isChunkLoaded(tile.getPos())
                        && world.getBlockEntity(tile.getPos()) == tile) tile.onLoad();
                });
            }
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register((entity, world) -> {
            if (entity instanceof buildcraft.lib.tile.TileBC_Neptune tile) tile.onChunkUnload();
        });
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
            buildcraft.lib.misc.MessageUtil.doDelayedServer(server, () -> {
                if (!handler.isConnectionOpen()) return;
                buildcraft.lib.marker.MarkerCache.onPlayerJoinWorld(handler.player);
            }));
        net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, from, to) ->
            buildcraft.lib.misc.MessageUtil.doDelayedServer(to.getServer(), () -> {
                if (player.networkHandler.isConnectionOpen()) buildcraft.lib.marker.MarkerCache.onPlayerJoinWorld(player);
            }));
        // Forge: FMLServerStartingEvent → register command
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            buildcraft.lib.net.cache.BuildCraftObjectCaches.onServerStarting();
            // STUB(R.Chen): port CommandBuildCraft to Fabric's CommandRegistrationCallback.
        });

        // Forge: ServerTickEvent (Phase.END)
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            buildcraft.lib.misc.MessageUtil.postServerTick(server);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(buildcraft.lib.misc.MessageUtil::stopServer);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> buildcraft.lib.net.cache.BuildCraftObjectCaches.onServerStopped());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> buildcraft.lib.marker.MarkerCache.clearServer());

        // Forge: WorldEvent.Unload (server side)
        ServerWorldEvents.UNLOAD.register((server, world) -> {
            buildcraft.lib.marker.MarkerCache.onWorldUnload(world);
        });
    }
}
