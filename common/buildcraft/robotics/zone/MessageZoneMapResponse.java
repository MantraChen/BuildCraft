/* Copyright (c) 2017 SpaceToad and the BuildCraft team. MPL-2.0. */
package buildcraft.robotics.zone;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public record MessageZoneMapResponse(ZonePlannerMapChunkKey key, ZonePlannerMapChunk data) implements FabricPacket {
    public static final PacketType<MessageZoneMapResponse> TYPE = PacketType.create(
        new Identifier("buildcraftrobotics", "zone_map_response"), buffer -> {
            var message = new MessageZoneMapResponse(new ZonePlannerMapChunkKey(buffer), new ZonePlannerMapChunk(buffer));
            if (buffer.isReadable()) throw new IllegalArgumentException("Trailing map response data");
            return message;
        });
    @Override public PacketType<MessageZoneMapResponse> getType() { return TYPE; }
    @Override public void write(PacketByteBuf buffer) { key.write(buffer); data.write(buffer); }
}
