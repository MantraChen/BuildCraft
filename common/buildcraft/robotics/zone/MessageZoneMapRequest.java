/* Copyright (c) 2017 SpaceToad and the BuildCraft team. MPL-2.0. */
package buildcraft.robotics.zone;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import buildcraft.robotics.container.ContainerZonePlanner;

public record MessageZoneMapRequest(ZonePlannerMapChunkKey key) implements FabricPacket {
    public static final PacketType<MessageZoneMapRequest> TYPE = PacketType.create(
        new Identifier("buildcraftrobotics", "zone_map_request"), buffer -> {
            var message = new MessageZoneMapRequest(new ZonePlannerMapChunkKey(buffer));
            if (buffer.isReadable()) throw new IllegalArgumentException("Trailing map request data");
            return message;
        });
    @Override public PacketType<MessageZoneMapRequest> getType() { return TYPE; }
    @Override public void write(PacketByteBuf buffer) { key.write(buffer); }
    public MessageZoneMapResponse response(ServerPlayerEntity player) {
        var world = player.getServerWorld();
        if (!(player.currentScreenHandler instanceof ContainerZonePlanner screen) || !screen.canUse(player)
            || !world.getRegistryKey().getValue().equals(key.dimension)) return null;
        int radius = Math.min(32, player.server.getPlayerManager().getViewDistance());
        var center = player.getChunkPos();
        if (Math.abs((long) key.chunkPos.x - center.x) > radius || Math.abs((long) key.chunkPos.z - center.z) > radius
            || key.level < Math.floorDiv(world.getBottomY(), 32) || key.level > Math.floorDiv(world.getTopY() - 1, 32) + 1
            || !world.isChunkLoaded(key.chunkPos.x, key.chunkPos.z) || !screen.allowMapRequest(world.getTime())) return null;
        return new MessageZoneMapResponse(key, ZonePlannerMapDataServer.INSTANCE.getChunk(world, key));
    }
}
