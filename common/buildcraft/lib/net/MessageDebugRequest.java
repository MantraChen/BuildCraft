package buildcraft.lib.net;

import java.util.Objects;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;

/** A single nearby block query, tied to the world in which the player aimed it. */
public record MessageDebugRequest(Identifier dimension, BlockPos pos, Direction side) implements FabricPacket {
    public static final PacketType<MessageDebugRequest> TYPE = PacketType.create(new Identifier("buildcraft", "debug_request"), MessageDebugRequest::read);
    public MessageDebugRequest {
        Objects.requireNonNull(dimension); pos = pos.toImmutable(); Objects.requireNonNull(side);
    }
    public static MessageDebugRequest read(PacketByteBuf buffer) {
        var result = new MessageDebugRequest(buffer.readIdentifier(), buffer.readBlockPos(), buffer.readEnumConstant(Direction.class));
        if (buffer.isReadable()) throw new IllegalArgumentException("Trailing debug query data");
        return result;
    }
    public void write(PacketByteBuf buffer) { buffer.writeIdentifier(dimension); buffer.writeBlockPos(pos); buffer.writeEnumConstant(side); }
    public PacketType<?> getType() { return TYPE; }
}
