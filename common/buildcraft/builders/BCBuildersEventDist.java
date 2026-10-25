/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.builders;

import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.world.World;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.*;
import buildcraft.builders.tile.TileQuarry;

/** World-scoped quarry updates and moving-arm collisions. Remake by FelixMantra. */
public enum BCBuildersEventDist {
    INSTANCE;
    private final Map<World,Map<BlockPos,WeakReference<TileQuarry>>> quarries=Collections.synchronizedMap(new WeakHashMap<>());
    public void validateQuarry(TileQuarry tile) {
        if(tile.getWorld()!=null)quarries.computeIfAbsent(tile.getWorld(),w->new HashMap<>()).put(tile.getPos(),new WeakReference<>(tile));
    }
    public void invalidateQuarry(TileQuarry tile) {
        var map=quarries.get(tile.getWorld());if(map==null)return;
        var current=map.get(tile.getPos());if(current!=null && current.get()==tile)map.remove(tile.getPos());
    }
    public void blockChanged(World world,BlockPos pos) {
        if(world.isClient)return;
        var map=quarries.get(world);if(map==null || map.isEmpty())return;
        for(var ref:List.copyOf(map.values())) {
            var tile=ref.get();if(tile!=null && tile.isStorageAvailable())tile.onWorldBlockChanged(pos);
        }
    }
    public List<VoxelShape> collisions(World world,net.minecraft.util.math.Box query) {
        var map=quarries.get(world);if(map==null || map.isEmpty())return List.of();
        var result=new ArrayList<VoxelShape>();
        for(var ref:List.copyOf(map.values())) {
            var tile=ref.get();if(tile==null || !tile.isStorageAvailable())continue;
            for(var box:tile.getCollisionBoxes())if(box.intersects(query))result.add(VoxelShapes.cuboid(box));
        }
        return result;
    }
    public void onClientTick() {}
}
