/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 */
package buildcraft.lib.net.cache;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.item.ItemStack;
import buildcraft.lib.compat.FluidStackBC;

/** Shared cache registration; values are reset at connection/server lifecycle boundaries. */
public final class BuildCraftObjectCaches {
    private static final List<NetworkedObjectCache<?>> REGISTRY = new ArrayList<>();
    public static final List<NetworkedObjectCache<?>> CACHES = Collections.unmodifiableList(REGISTRY);
    public static final NetworkedItemStackCache CACHE_ITEMS = registerCache(new NetworkedItemStackCache());
    public static final NetworkedFluidStackCache CACHE_FLUIDS = registerCache(new NetworkedFluidStackCache());
    private static boolean frozen;

    private BuildCraftObjectCaches() {}

    public static <C extends NetworkedObjectCache<?>> C registerCache(C cache) {
        if (frozen) throw new IllegalStateException("Cannot register a cache after a connection starts");
        if (REGISTRY.contains(cache)) throw new IllegalArgumentException("Cache already registered");
        if (REGISTRY.size() >= 256) throw new IllegalStateException("Too many network caches");
        REGISTRY.add(java.util.Objects.requireNonNull(cache));
        return cache;
    }

    static NetworkedObjectCache<?> getCache(int id) {
        if (id < 0 || id >= REGISTRY.size()) throw new IllegalArgumentException("Unknown cache: " + id);
        return REGISTRY.get(id);
    }

    public static int storeItemStack(ItemStack stack) { return CACHE_ITEMS.server().store(stack); }
    public static int getItemStackId(ItemStack stack) { return CACHE_ITEMS.server().getId(stack); }
    public static Supplier<ItemStack> retrieveItemStack(int id) { return CACHE_ITEMS.client().retrieve(id); }
    public static int storeFluid(FluidVariant fluid) { return CACHE_FLUIDS.server().store(FluidStackBC.of(fluid, 1)); }
    public static int getFluidId(FluidVariant fluid) { return CACHE_FLUIDS.server().getId(FluidStackBC.of(fluid, 1)); }
    public static Supplier<FluidVariant> retrieveFluid(int id) {
        NetworkedFluidStackCache.Link link = CACHE_FLUIDS.client().retrieve(id);
        return () -> link.get().getFluidVariant();
    }

    public static void onClientJoinServer() {
        frozen = true;
        CACHES.forEach(NetworkedObjectCache::onClientJoinServer);
    }

    public static void onClientTick(Consumer<MessageObjectCacheRequest> sender) {
        CACHES.forEach(cache -> cache.onClientWorldTick(sender));
    }

    public static void onServerStarting() {
        frozen = true;
        onServerStopped();
    }

    public static void onServerStopped() { CACHES.forEach(NetworkedObjectCache::onServerStopped); }
}
