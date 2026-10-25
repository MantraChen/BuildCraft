/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.factory;

import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import buildcraft.factory.tile.TileMiningWell;

/** Index by exact x/z column, so unrelated world updates do not scan every mining well. */
public final class MiningWellUpdates {
    private static final Map<World,Map<Long,Map<BlockPos,WeakReference<TileMiningWell>>>> WORLDS=
        Collections.synchronizedMap(new WeakHashMap<>());
    private static long column(BlockPos pos) {return ChunkPos.toLong(pos.getX(),pos.getZ());}
    public static void register(TileMiningWell tile) {
        var world=tile.getWorld();if(world==null || world.isClient)return;
        WORLDS.computeIfAbsent(world,w->new HashMap<>()).computeIfAbsent(column(tile.getPos()),p->new HashMap<>())
            .put(tile.getPos(),new WeakReference<>(tile));
    }
    public static void unregister(TileMiningWell tile) {
        var columns=WORLDS.get(tile.getWorld());if(columns==null)return;
        var miners=columns.get(column(tile.getPos()));if(miners==null)return;
        var ref=miners.get(tile.getPos());if(ref!=null && ref.get()==tile)miners.remove(tile.getPos());
        if(miners.isEmpty())columns.remove(column(tile.getPos()));
    }
    public static void changed(World world,BlockPos pos) {
        if(world.isClient)return;
        var columns=WORLDS.get(world);if(columns==null)return;
        var miners=columns.get(column(pos));if(miners==null)return;
        for(var ref:List.copyOf(miners.values())) {
            var tile=ref.get();if(tile!=null && tile.isStorageAvailable())tile.onColumnChanged(pos);
        }
    }
}
