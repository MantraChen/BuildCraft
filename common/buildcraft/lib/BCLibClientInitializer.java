/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 *
 * Ported to Fabric 1.20.1 by R.Chen (https://github.com/MantraChen).
 */
package buildcraft.lib;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import buildcraft.lib.client.render.DetachedRenderer;

import buildcraft.BuildCraftFabric;

/**
 * Fabric ClientModInitializer for the BuildCraft lib module.
 * Replaces the client-only @SubscribeEvent methods in {@link BCLibEventDist} and
 * the {@code @SidedProxy} client proxy in {@link BCLibProxy}.
 *
 * Event mapping (Forge → Fabric):
 *   TextureStitchEvent.Pre/Post → STUB (use ClientSpriteRegistryCallback or ModelLoadingPlugin)
 *   ModelBakeEvent              → STUB (ModelLoadingPlugin#onInitializeModelLoader)
 *   RenderWorldLastEvent        → {@link WorldRenderEvents#LAST}
 *   ClientTickEvent (END)       → {@link ClientTickEvents#END_CLIENT_TICK}
 *   ClientConnectedToServer     → {@link ClientPlayConnectionEvents#JOIN}
 */
@Environment(EnvType.CLIENT)
public class BCLibClientInitializer implements ClientModInitializer {
    private net.minecraft.client.world.ClientWorld previousMarkerWorld;

    @Override
    public void onInitializeClient() {
        BuildCraftFabric.LOGGER.info("[BCLib] Initializing buildcraft-lib client");
        buildcraft.lib.net.BCNetworkManager.registerClient();
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientBlockEntityEvents.BLOCK_ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof buildcraft.lib.tile.TileBC_Neptune tile) {
                buildcraft.lib.misc.MessageUtil.doDelayedClient(1, () -> {
                    if (net.minecraft.client.MinecraftClient.getInstance().world == world
                        && !tile.isRemoved() && world.isChunkLoaded(tile.getPos())
                        && world.getBlockEntity(tile.getPos()) == tile) tile.onLoad();
                });
            }
        });
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register((entity, world) -> {
            if (entity instanceof buildcraft.lib.tile.TileBC_Neptune tile) tile.onChunkUnload();
        });

        buildcraft.api.registry.BuildCraftRegistryManager.managerResourcePacks = buildcraft.lib.script.ReloadableRegistryManager.RESOURCE_PACKS;
        net.fabricmc.fabric.api.resource.ResourceManagerHelper.get(net.minecraft.resource.ResourceType.CLIENT_RESOURCES)
            .registerReloadListener(buildcraft.lib.script.ReloadableRegistryManager.RESOURCE_PACKS);

        ClientPlayConnectionEvents.INIT.register((handler, client) -> {
            buildcraft.lib.net.cache.BuildCraftObjectCaches.onClientJoinServer();
            buildcraft.lib.marker.MarkerCache.clearClient();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            buildcraft.lib.net.cache.BuildCraftObjectCaches.onClientJoinServer();
            buildcraft.lib.marker.MarkerCache.clearClient();
        });

        // Forge: ClientTickEvent (Phase.END)
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (previousMarkerWorld != client.world) {
                if (previousMarkerWorld != null) buildcraft.lib.marker.MarkerCache.onWorldUnload(previousMarkerWorld);
                previousMarkerWorld = client.world;
            }
            buildcraft.lib.misc.MessageUtil.postClientTick();
            if (client.player != null && client.getNetworkHandler() != null) {
                buildcraft.lib.net.cache.BuildCraftObjectCaches.onClientTick(buildcraft.lib.net.BCNetworkManager::sendToServer);
            }
            // STUB(R.Chen): ItemDebugger hover-debug request → use MinecraftClient#crosshairTarget.
        });

        // Forge: RenderWorldLastEvent → DetachedRenderer event bus
        DetachedRenderer.registerEvents();

        // STUB(R.Chen): TextureStitchEvent → ModelLoadingPlugin / ClientSpriteRegistryCallback.
        // STUB(R.Chen): ModelBakeEvent → ModelLoadingPlugin#onInitializeModelLoader.
        // STUB(R.Chen): EventBuildCraftReload.FinishLoad → wire via custom ResourceReloader.

        BuildCraftFabric.LOGGER.info("[BCLib] Client initialization complete");
    }
}
