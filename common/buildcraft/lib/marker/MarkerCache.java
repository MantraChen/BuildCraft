/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 */
package buildcraft.lib.marker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import buildcraft.api.core.BCDebugging;
import buildcraft.api.core.BCLog;

public abstract class MarkerCache<S extends MarkerSubCache<?>> {
    public static final boolean DEBUG = BCDebugging.shouldDebugLog("lib.markers");
    private static final List<MarkerCache<?>> REGISTRY = new ArrayList<>();
    public static final List<MarkerCache<?>> CACHES = Collections.unmodifiableList(REGISTRY);
    private static volatile boolean frozen;
    public final String name;
    // Separate game threads own these maps; actual world identities cannot collide like identity hash codes.
    private final Map<World, S> cacheClient = new IdentityHashMap<>();
    private final Map<World, S> cacheServer = new IdentityHashMap<>();

    protected MarkerCache(String name) { this.name = name; }

    public static void registerCache(MarkerCache<?> cache) {
        if (frozen) throw new IllegalStateException("Cannot register marker caches after a world loads");
        if (REGISTRY.contains(cache) || REGISTRY.stream().anyMatch(existing -> existing.name.equals(cache.name))) {
            throw new IllegalArgumentException("Duplicate marker cache: " + cache.name);
        }
        if (REGISTRY.size() > 32767) throw new IllegalStateException("Too many marker caches");
        REGISTRY.add(cache);
    }

    public static void postInit() {
        if (DEBUG) for (int i = 0; i < CACHES.size(); i++) BCLog.logger.info("[lib.markers] {} = {}", i, CACHES.get(i).name);
    }

    public static void onPlayerJoinWorld(ServerPlayerEntity player) {
        for (MarkerCache<?> cache : CACHES) cache.getSubCache(player.getWorld()).onPlayerJoinWorld(player);
    }

    public static void onWorldUnload(World world) {
        for (MarkerCache<?> cache : CACHES) (world.isClient ? cache.cacheClient : cache.cacheServer).remove(world);
    }

    public static void clearClient() { CACHES.forEach(cache -> cache.cacheClient.clear()); }
    public static void clearServer() { CACHES.forEach(cache -> cache.cacheServer.clear()); }
    protected abstract S createSubCache(World world);

    public S getSubCache(World world) {
        frozen = true;
        return (world.isClient ? cacheClient : cacheServer).computeIfAbsent(world, this::createSubCache);
    }
}
