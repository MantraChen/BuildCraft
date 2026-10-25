/* Copyright (c) 2017 SpaceToad and the BuildCraft team. MPL-2.0. */
package buildcraft.lib.net;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import buildcraft.BuildCraftFabric;
import static buildcraft.lib.net.BCNetworkManager.handle;

/** Client packet lambdas live here so the dedicated server never resolves ClientPlayerEntity. */
@Environment(EnvType.CLIENT)
public final class BCClientNetworkManager {
    private BCClientNetworkManager() {}
    /** Call from a {@code ClientModInitializer.onInitializeClient()}. */
    @Environment(EnvType.CLIENT)
    public static void registerClient() {
        BuildCraftFabric.LOGGER.info("[BCNetwork] Registering client packet receivers");
        ClientPlayNetworking.registerGlobalReceiver(buildcraft.builders.snapshot.MessageSnapshotResponse.TYPE,
            (packet, player, sender) -> handle(() -> buildcraft.builders.snapshot.ClientSnapshots.INSTANCE.accept(packet),
                player.networkHandler.getConnection()::disconnect));
        ClientPlayNetworking.registerGlobalReceiver(buildcraft.robotics.zone.MessageZoneMapResponse.TYPE,
            (packet, player, sender) -> {
                if (player.getWorld().getRegistryKey().getValue().equals(packet.key().dimension))
                    buildcraft.robotics.zone.ZonePlannerMapDataClient.INSTANCE.onChunkReceived(packet.key(), packet.data());
            });
        ClientPlayNetworking.registerGlobalReceiver(MessageDebugResponse.TYPE, (packet, player, sender) ->
            buildcraft.lib.debug.ClientDebuggables.accept(packet));

        ClientPlayNetworking.registerGlobalReceiver(MessageUpdateTile.TYPE, (packet, player, sender) ->
            handle(() -> NetworkRouting.receiveTile(packet, new NetworkContext(player, sender::sendPacket)),
                player.networkHandler.getConnection()::disconnect));
        ClientPlayNetworking.registerGlobalReceiver(MessageContainer.TYPE, (packet, player, sender) ->
            handle(() -> NetworkRouting.receiveContainer(packet, new NetworkContext(player, sender::sendPacket)),
                player.networkHandler.getConnection()::disconnect));
        ClientPlayNetworking.registerGlobalReceiver(buildcraft.lib.net.cache.MessageObjectCacheResponse.TYPE,
            (packet, player, sender) -> handle(packet::apply, player.networkHandler.getConnection()::disconnect));
        ClientPlayNetworking.registerGlobalReceiver(MessageMarker.TYPE, (packet, player, sender) -> handle(() -> {
            if (!player.getWorld().getRegistryKey().getValue().equals(packet.dimension)) return;
            if (packet.cacheId >= buildcraft.lib.marker.MarkerCache.CACHES.size()) {
                throw new IllegalArgumentException("Unknown marker cache: " + packet.cacheId);
            }
            buildcraft.lib.marker.MarkerCache.CACHES.get(packet.cacheId).getSubCache(player.getWorld()).handleMessageMain(packet);
        }, player.networkHandler.getConnection()::disconnect));
    }

}
