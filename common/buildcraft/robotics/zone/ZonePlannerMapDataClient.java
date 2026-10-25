/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.robotics.zone;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.world.World;

import buildcraft.lib.net.MessageManager;

public class ZonePlannerMapDataClient extends ZonePlannerMapData {
    public static final ZonePlannerMapDataClient INSTANCE = new ZonePlannerMapDataClient();

    private final Map<ZonePlannerMapChunkKey, Long> pending = new HashMap<>();

    @Override
    public ZonePlannerMapChunk loadChunk(World world, ZonePlannerMapChunkKey key) {
        if (world == null || !world.getRegistryKey().getValue().equals(key.dimension)) return null;
        long now = System.nanoTime();
        pending.entrySet().removeIf(entry -> now - entry.getValue() > 10_000_000_000L);
        if (pending.size() < 64 && !pending.containsKey(key)) {
            pending.put(key, now);
            MessageManager.sendToServer(new MessageZoneMapRequest(key));
        }
        return null;
    }


    public void onChunkReceived(ZonePlannerMapChunkKey key, ZonePlannerMapChunk zonePlannerMapChunk) {
        if (pending.remove(key) != null) data.put(key, zonePlannerMapChunk);
    }

    @Override public void clear() { super.clear(); pending.clear(); }
}
