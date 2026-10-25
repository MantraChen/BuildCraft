/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 *
 * Ported to Fabric 1.20.1 by R.Chen (https://github.com/MantraChen).
 */
package buildcraft.lib.net;

import java.util.Collection;
import java.util.function.Consumer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import buildcraft.BuildCraftFabric;

/**
 * Fabric 1.20.1 replacement for the original {@code MessageManager}.
 *
 * Responsibilities:
 *   - Register all BuildCraft packet types on both server and client.
 *   - Provide static {@code sendTo*} helpers whose signatures track the legacy
 *     {@code MessageManager} API closely, so call sites can be ported with
 *     minimal churn.
 *
 * Original (Forge) → Fabric mapping:
 *   SimpleNetworkWrapper              → ServerPlayNetworking / ClientPlayNetworking
 *   IMessage                          → FabricPacket (typed) implementations
 *   IMessageHandler<REQ, REP>         → PlayChannelHandler / PlayPacketHandler
 *   wrapper.sendToServer(msg)         → ClientPlayNetworking.send(packet)
 *   wrapper.sendTo(msg, playerMP)     → ServerPlayNetworking.send(player, packet)
 *   wrapper.sendToAll(msg)            → PlayerLookup.all(server).forEach(send)
 *   wrapper.sendToAllAround(msg, tp)  → PlayerLookup.tracking(world, pos).forEach(send)
 *
 * Typed Fabric receivers run on the game thread and use NetworkRouting for access checks.
 */
public final class BCNetworkManager {

    private BCNetworkManager() {}

    /** Call from a {@code ModInitializer.onInitialize()} (common side). */
    public static void registerCommon() {
        BuildCraftFabric.LOGGER.info("[BCNetwork] Registering common packet receivers");

        ServerPlayNetworking.registerGlobalReceiver(MessageUpdateTile.TYPE, (packet, player, sender) ->
            handle(() -> NetworkRouting.receiveTile(packet, new NetworkContext(player, sender::sendPacket)),
                player.networkHandler::disconnect));
        ServerPlayNetworking.registerGlobalReceiver(MessageContainer.TYPE, (packet, player, sender) ->
            handle(() -> NetworkRouting.receiveContainer(packet, new NetworkContext(player, sender::sendPacket)),
                player.networkHandler::disconnect));
        ServerPlayNetworking.registerGlobalReceiver(buildcraft.lib.net.cache.MessageObjectCacheRequest.TYPE,
            (packet, player, sender) -> handle(() -> sender.sendPacket(packet.createResponse()),
                player.networkHandler::disconnect));
    }

    /** Call from a {@code ClientModInitializer.onInitializeClient()}. */
    @Environment(EnvType.CLIENT)
    public static void registerClient() {
        BuildCraftFabric.LOGGER.info("[BCNetwork] Registering client packet receivers");

        ClientPlayNetworking.registerGlobalReceiver(MessageUpdateTile.TYPE, (packet, player, sender) ->
            handle(() -> NetworkRouting.receiveTile(packet, new NetworkContext(player, sender::sendPacket)),
                player.networkHandler.getConnection()::disconnect));
        ClientPlayNetworking.registerGlobalReceiver(MessageContainer.TYPE, (packet, player, sender) ->
            handle(() -> NetworkRouting.receiveContainer(packet, new NetworkContext(player, sender::sendPacket)),
                player.networkHandler.getConnection()::disconnect));
        ClientPlayNetworking.registerGlobalReceiver(buildcraft.lib.net.cache.MessageObjectCacheResponse.TYPE,
            (packet, player, sender) -> handle(packet::apply, player.networkHandler.getConnection()::disconnect));
    }

    private static void handle(PacketAction action, Consumer<net.minecraft.text.Text> disconnect) {
        try {
            action.run();
        } catch (java.io.IOException | RuntimeException exception) {
            BuildCraftFabric.LOGGER.warn("[BCNetwork] Invalid BuildCraft packet", exception);
            disconnect.accept(net.minecraft.text.Text.literal("Invalid BuildCraft packet; see the log for details"));
        }
    }

    @FunctionalInterface
    private interface PacketAction { void run() throws java.io.IOException; }

    // -----------------------------------------------------------------------
    // Send helpers (kept close to legacy MessageManager signatures)
    // -----------------------------------------------------------------------

    /** Legacy: {@code MessageManager.sendToServer(msg)} — client-only. */
    @Environment(EnvType.CLIENT)
    public static <T extends FabricPacket> void sendToServer(T packet) {
        NetworkRouting.validateServerbound(packet);
        ClientPlayNetworking.send(packet);
    }

    /** Legacy: {@code MessageManager.sendTo(msg, playerMP)}. */
    public static <T extends FabricPacket> void sendTo(T packet, ServerPlayerEntity player) {
        ServerPlayNetworking.send(player, packet);
    }

    /** Legacy: {@code MessageManager.sendToAll(msg)}. */
    public static <T extends FabricPacket> void sendToAll(T packet, net.minecraft.server.MinecraftServer server) {
        Collection<ServerPlayerEntity> players = PlayerLookup.all(server);
        sendToEach(packet, players);
    }

    /**
     * Legacy: {@code MessageManager.sendToAllAround(msg, NetworkRegistry.TargetPoint)}.
     * Uses Fabric's chunk-tracking lookup: only sends to players that have the
     * chunk containing {@code pos} loaded on their client.
     */
    public static <T extends FabricPacket> void sendToAllTracking(T packet, ServerWorld world, BlockPos pos) {
        sendToEach(packet, PlayerLookup.tracking(world, pos));
    }

    /** Convenience: broadcast to every player tracking the given world's chunk. */
    public static <T extends FabricPacket> void sendToAllInWorld(T packet, World world, BlockPos pos) {
        if (world instanceof ServerWorld serverWorld) {
            sendToAllTracking(packet, serverWorld, pos);
        } else {
            BuildCraftFabric.LOGGER.warn(
                "[BCNetwork] sendToAllInWorld called with non-server world {} — dropped", world
            );
        }
    }

    private static <T extends FabricPacket> void sendToEach(T packet, Iterable<ServerPlayerEntity> players) {
        for (ServerPlayerEntity player : players) {
            ServerPlayNetworking.send(player, packet);
        }
    }

    // -----------------------------------------------------------------------
    // Hook for additional packet registration from other BC modules
    // -----------------------------------------------------------------------

    /**
     * STUB(R.Chen): until module initializers exist, other BC modules can register
     * their packet types by passing a callback here from their own onInitialize().
     * Example:
     *   BCNetworkManager.registerExtra(() -> {
     *       ServerPlayNetworking.registerGlobalReceiver(MyPacket.TYPE, MyHandler::handle);
     *   });
     */
    public static void registerExtra(Runnable r) {
        r.run();
    }

    /** Same as {@link #registerExtra(Runnable)} but lets callers see the active common-side hook. */
    public static <T extends FabricPacket> void registerServerReceiver(
        PacketType<T> type,
        ServerPlayNetworking.PlayPacketHandler<T> handler
    ) {
        ServerPlayNetworking.registerGlobalReceiver(type, handler);
    }

    @Environment(EnvType.CLIENT)
    public static <T extends FabricPacket> void registerClientReceiver(
        PacketType<T> type,
        ClientPlayNetworking.PlayPacketHandler<T> handler
    ) {
        ClientPlayNetworking.registerGlobalReceiver(type, handler);
    }

    /** STUB(R.Chen): retained for source-compat with legacy call sites that took a Consumer. */
    public static <T extends FabricPacket> void forEachTrackingPlayer(
        ServerWorld world, BlockPos pos, Consumer<ServerPlayerEntity> consumer
    ) {
        PlayerLookup.tracking(world, pos).forEach(consumer);
    }
}
