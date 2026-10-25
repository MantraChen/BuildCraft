/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.transport.wire;

import java.util.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;
import buildcraft.api.transport.IWireEmitter;
import buildcraft.api.transport.pipe.IPipeHolder;

/** Derived networks for loaded pipes. The authoritative wire parts live in tile NBT.
 * Remake by FelixMantra: rebuild topology at world-tick boundaries and sync per watched tile. */
public class WorldSavedDataWireSystems extends PersistentState {
    public static final String DATA_NAME = "buildcraft_wire_systems";
    private final World world;
    public final Map<WireSystem, Boolean> wireSystems = new HashMap<>();
    private final Map<WireSystem.WireElement, List<WireSystem>> index = new HashMap<>();
    private final Map<BlockPos, IPipeHolder> holders = new HashMap<>();
    public boolean gatesChanged = true;
    private boolean structureChanged = true;

    public WorldSavedDataWireSystems(World world) { this.world = world; }
    public World getWorld() { return world; }
    public void markStructureChanged() { structureChanged = true; gatesChanged = true; }

    public void register(IPipeHolder holder) {
        if (holders.put(holder.getPipePos().toImmutable(), holder) != holder) markStructureChanged();
    }
    public void unregister(IPipeHolder holder) {
        if (holders.remove(holder.getPipePos(), holder)) markStructureChanged();
    }
    public IPipeHolder getLoadedHolder(BlockPos pos) {
        var holder = holders.get(pos);
        return holder != null && !holder.getPipeTile().isRemoved() && world.isChunkLoaded(pos)
            && world.getBlockEntity(pos) == holder.getPipeTile() ? holder : null;
    }
    public List<WireSystem> getWireSystemsWithElement(WireSystem.WireElement element) {
        return new ArrayList<>(getWireSystemsWithElementAsReadOnlyList(element));
    }
    public List<WireSystem> getWireSystemsWithElementAsReadOnlyList(WireSystem.WireElement element) {
        return index.getOrDefault(element, List.of());
    }
    public void rebuildWireSystemsAround(IPipeHolder holder) { markStructureChanged(); }

    public boolean isEmitterEmitting(WireSystem.WireElement element, DyeColor color) {
        var holder = getLoadedHolder(element.blockPos);
        return holder != null && holder.getPluggable(element.emitterSide) instanceof IWireEmitter emitter
            && emitter.isEmitting(color);
    }

    public void tick() {
        if (holders.entrySet().removeIf(e -> getLoadedHolder(e.getKey()) == null)) markStructureChanged();
        if (structureChanged) {
            wireSystems.clear(); index.clear();
            for (var holder : holders.values()) {
                var manager = (WireManager) holder.getWireManager();
                for (var part : manager.parts.keySet()) {
                    var element = new WireSystem.WireElement(holder.getPipePos(), part);
                    if (index.containsKey(element)) continue;
                    var system = new WireSystem(this, element);
                    if (system.isEmpty()) continue;
                    wireSystems.put(system, false);
                    for (var member : system.elements) index.computeIfAbsent(member, ignored -> new ArrayList<>()).add(system);
                }
                manager.updateBetweens(true);
            }
            structureChanged = false;
            gatesChanged = true;
        }
        if (gatesChanged) {
            wireSystems.replaceAll((system, old) -> system.update(this));
            gatesChanged = false;
            for (var holder : holders.values()) ((WireManager)holder.getWireManager()).syncPower();
        }
    }

    // Old world-level graph snapshots may contain unloaded or removed emitters. Never revive them.
    @Override public NbtCompound writeNbt(NbtCompound nbt) { return nbt; }
    public static WorldSavedDataWireSystems fromNbt(NbtCompound nbt, World world) { return new WorldSavedDataWireSystems(world); }
    public static WorldSavedDataWireSystems get(World world) {
        if (!(world instanceof ServerWorld server)) throw new IllegalArgumentException("Wire systems require a server world");
        return server.getPersistentStateManager().getOrCreate(nbt -> fromNbt(nbt, world), () -> new WorldSavedDataWireSystems(world), DATA_NAME);
    }
}
