package buildcraft.lib.net;

import java.util.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** Immutable, bounded response; position and dimension prevent stale text on another target. */
public record MessageDebugResponse(Identifier dimension, BlockPos pos, List<String> left, List<String> right) implements FabricPacket {
    public static final int MAX_LINES = 64, MAX_LINE_LENGTH = 512;
    public static final PacketType<MessageDebugResponse> TYPE = PacketType.create(new Identifier("buildcraft", "debug_response"), MessageDebugResponse::read);
    public MessageDebugResponse {
        Objects.requireNonNull(dimension); pos = pos.toImmutable(); left = List.copyOf(left); right = List.copyOf(right);
        for (var lines : List.of(left, right)) {
            if (lines.size() > MAX_LINES || lines.stream().anyMatch(line -> line.length() > MAX_LINE_LENGTH))
                throw new IllegalArgumentException("Debug text exceeds packet limits");
        }
    }
    public static List<String> boundedLines(List<String> lines) {
        return lines.stream().filter(Objects::nonNull).limit(MAX_LINES)
            .map(line -> line.substring(0, Math.min(line.length(), MAX_LINE_LENGTH))).toList();
    }
    private static List<String> readLines(PacketByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_LINES) throw new IllegalArgumentException("Invalid debug line count");
        List<String> lines = new ArrayList<>(count);
        for (int i = 0; i < count; i++) lines.add(buffer.readString(MAX_LINE_LENGTH));
        return lines;
    }
    public static MessageDebugResponse read(PacketByteBuf buffer) {
        var result = new MessageDebugResponse(buffer.readIdentifier(), buffer.readBlockPos(), readLines(buffer), readLines(buffer));
        if (buffer.isReadable()) throw new IllegalArgumentException("Trailing debug response data");
        return result;
    }
    public void write(PacketByteBuf buffer) {
        buffer.writeIdentifier(dimension); buffer.writeBlockPos(pos);
        for (var lines : List.of(left, right)) { buffer.writeVarInt(lines.size()); for (String line : lines) buffer.writeString(line, MAX_LINE_LENGTH); }
    }
    public PacketType<?> getType() { return TYPE; }
}
