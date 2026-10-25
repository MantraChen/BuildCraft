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
 *   TextureStitchEvent.Pre/Post → atlas source JSON + resource reload listener
 *   ModelBakeEvent              → ModelHolderRegistry after Minecraft's model reload
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
        BCLibProxy.setClientWorldSupplier(() -> net.minecraft.client.MinecraftClient.getInstance().world);
        BCLibProxy.setResourceOpener(id -> net.minecraft.client.MinecraftClient.getInstance().getResourceManager()
            .getResource(id).orElseThrow(() -> new java.io.FileNotFoundException(id.toString())).getInputStream());
        net.fabricmc.fabric.api.resource.ResourceManagerHelper.get(net.minecraft.resource.ResourceType.CLIENT_RESOURCES)
            .registerReloadListener(new net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener() {
                public net.minecraft.util.Identifier getFabricId() { return new net.minecraft.util.Identifier("buildcraft", "config_overrides"); }
                public void reload(net.minecraft.resource.ResourceManager manager) {
                    buildcraft.lib.config.RoamingConfigManager.clearCaches();
                    buildcraft.lib.config.DetailedConfigOption.reloadAll();
                }
            });
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, context, lines) ->
            buildcraft.core.list.ListTooltipHandler.INSTANCE.append(net.minecraft.client.MinecraftClient.getInstance().player, stack, lines));
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(
            client -> buildcraft.lib.client.guide.font.FontManager.INSTANCE.clearTextures());
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
        buildcraft.lib.script.ReloadableRegistryManager.RESOURCE_PACKS.registerRegistry(
            buildcraft.lib.client.guide.GuidePageRegistry.INSTANCE);
        net.fabricmc.fabric.api.resource.ResourceManagerHelper.get(net.minecraft.resource.ResourceType.CLIENT_RESOURCES)
            .registerReloadListener(buildcraft.lib.script.ReloadableRegistryManager.RESOURCE_PACKS);
        net.fabricmc.fabric.api.resource.ResourceManagerHelper.get(net.minecraft.resource.ResourceType.CLIENT_RESOURCES)
            .registerReloadListener(buildcraft.lib.client.guide.GuideManager.INSTANCE);
        net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player, world, hand) -> {
            var stack = player.getStackInHand(hand);
            if (world.isClient && stack.getItem() instanceof buildcraft.lib.item.ItemGuide) {
                net.minecraft.client.MinecraftClient.getInstance().setScreen(new buildcraft.lib.client.guide.GuiGuide(
                    buildcraft.lib.item.ItemGuide.getBookName(stack)));
                return net.minecraft.util.TypedActionResult.success(stack);
            }
            return net.minecraft.util.TypedActionResult.pass(stack);
        });

        ClientPlayConnectionEvents.INIT.register((handler, client) -> {
            buildcraft.lib.debug.ClientDebuggables.clear();
            buildcraft.lib.net.cache.BuildCraftObjectCaches.onClientJoinServer();
            buildcraft.lib.marker.MarkerCache.clearClient();
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            buildcraft.lib.client.guide.GuideManager.INSTANCE.reload();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            buildcraft.lib.debug.ClientDebuggables.clear();
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
            buildcraft.lib.debug.ClientDebuggables.tick(client);
        });

        // Forge: RenderWorldLastEvent → DetachedRenderer event bus
        DetachedRenderer.registerEvents();

        buildcraft.core.BCCoreModels.fmlPreInit();
        buildcraft.energy.BCEnergyModels.fmlPreInit();
        buildcraft.energy.BCEnergySprites.fmlPreInit();
        buildcraft.core.BCCoreModels.fmlInit();
        buildcraft.energy.BCEnergyModels.fmlInit();
        buildcraft.energy.client.gui.BCEnergyClientScreens.register();
        buildcraft.factory.client.BCFactoryClient.register();
        buildcraft.builders.client.BCBuildersClient.register();
        buildcraft.lib.client.model.ModelHolderRegistry.registerReloadListener();
        // STUB(R.Chen): EventBuildCraftReload.FinishLoad → wire via custom ResourceReloader.

        BuildCraftFabric.LOGGER.info("[BCLib] Client initialization complete");
    }
}
