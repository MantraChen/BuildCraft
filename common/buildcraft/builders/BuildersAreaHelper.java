/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.builders;

import java.util.*;
import buildcraft.api.core.IAreaProvider;
import buildcraft.lib.tile.TileBC_Neptune;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/** Validate every connected marker before a machine consumes the selection. */
public final class BuildersAreaHelper {
    private BuildersAreaHelper() {}
    public static boolean canConsumeMarkers(TileBC_Neptune machine, IAreaProvider provider) {
        if (!(provider instanceof buildcraft.core.tile.TileMarkerVolume marker)) return true;
        var world = machine.getWorld();
        var connection = marker.getCurrentConnection();
        if (connection == null || !(world instanceof ServerWorld server)) return false;
        var player = buildcraft.lib.misc.FakePlayerProvider.INSTANCE.getFakePlayer(server, machine.getOwner(), machine.getPos());
        var positions = List.copyOf(connection.getMarkerPositions());
        var states = new HashMap<BlockPos, BlockState>();
        var entities = new HashMap<BlockPos, BlockEntity>();
        for (var p : positions) {
            if (!world.isChunkLoaded(p) || !world.getWorldBorder().contains(p) || !world.canPlayerModifyAt(player,p)) return false;
            states.put(p,world.getBlockState(p)); entities.put(p,world.getBlockEntity(p));
        }
        for (var p : positions) if (!net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.invoker()
            .beforeBlockBreak(world,player,p,states.get(p),entities.get(p))) return false;
        return machine.isStorageAvailable() && marker.getCurrentConnection() == connection
            && positions.equals(List.copyOf(connection.getMarkerPositions()))
            && positions.stream().allMatch(p -> world.isChunkLoaded(p) && world.getBlockState(p)==states.get(p)
                && world.getBlockEntity(p)==entities.get(p) && world.canPlayerModifyAt(player,p));
    }
}
