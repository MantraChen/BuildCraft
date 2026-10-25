/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0
 * Remake by FelixMantra */
package buildcraft.core;
import buildcraft.core.marker.volume.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.minecraft.server.network.ServerPlayerEntity;

public final class BCCoreEventDist {
    private BCCoreEventDist(){}
    public static void register(){
        ServerTickEvents.END_WORLD_TICK.register(world->WorldSavedDataVolumeBoxes.get(world).tick());
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->joined(handler.player));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player,origin,destination)->joined(player));
    }
    private static void joined(ServerPlayerEntity player){
        var data=WorldSavedDataVolumeBoxes.get(player.getWorld());
        boolean resumed=false;
        for(var volume:data.volumeBoxes)if(volume.isPausedEditingBy(player)){volume.resumeEditing();resumed=true;}
        if(resumed)data.markDirty();
        // Wait for the client to install its new world before applying that dimension's collection.
        buildcraft.lib.misc.MessageUtil.doDelayedServer(player.getServer(),()->{
            if(player.isDisconnected())return;
            MessageVolumeBoxes.send(player.getServerWorld(),java.util.List.of(player));
        });
    }
}
