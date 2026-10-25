/* Copyright (c) 2017 SpaceToad and the BuildCraft team. MPL-2.0. */
package buildcraft.robotics.zone;

import java.util.Objects;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;

/** A level is an absolute upper Y slice, including negative levels in modern worlds. */
public final class ZonePlannerMapChunkKey {
    public static final int LEVEL_HEIGHT = 32;
    public final ChunkPos chunkPos;
    public final Identifier dimension;
    public final int level;
    public ZonePlannerMapChunkKey(ChunkPos chunkPos, Identifier dimension, int level) {
        if (Math.abs((long) chunkPos.x) > 1_875_000 || Math.abs((long) chunkPos.z) > 1_875_000
            || level < -64 || level > 64) throw new IllegalArgumentException("Map key out of range");
        this.chunkPos = Objects.requireNonNull(chunkPos);
        this.dimension = Objects.requireNonNull(dimension);
        this.level = level;
    }
    public ZonePlannerMapChunkKey(PacketByteBuf buf) {
        this(buf.readChunkPos(), buf.readIdentifier(), buf.readVarInt());
    }
    public void write(PacketByteBuf buf) {
        buf.writeChunkPos(chunkPos);
        buf.writeIdentifier(dimension);
        buf.writeVarInt(level);
    }
    @Override public boolean equals(Object obj) {
        return obj instanceof ZonePlannerMapChunkKey other && chunkPos.equals(other.chunkPos)
            && dimension.equals(other.dimension) && level == other.level;
    }
    @Override public int hashCode() { return Objects.hash(chunkPos, dimension, level); }
}
