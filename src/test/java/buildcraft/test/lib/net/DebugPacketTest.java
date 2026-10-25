package buildcraft.test.lib.net;

import java.util.*;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import org.junit.Test;
import static org.junit.Assert.*;
import buildcraft.lib.net.*;

public class DebugPacketTest {
    private static final Identifier WORLD = new Identifier("minecraft", "overworld");
    @Test public void queryCopiesPositionAndRoundTripsWorldAndFace() {
        BlockPos.Mutable mutable = new BlockPos.Mutable(4, -3, 9);
        var packet = new MessageDebugRequest(WORLD, mutable, Direction.WEST);
        mutable.set(0, 0, 0);
        PacketByteBuf wire = new PacketByteBuf(Unpooled.buffer());
        try { packet.write(wire); assertEquals(packet, MessageDebugRequest.read(wire)); }
        finally { wire.release(); }
        assertEquals(new BlockPos(4, -3, 9), packet.pos());
    }
    @Test public void responseSnapshotsInputAndCanBeEncodedRepeatedly() {
        var left = new ArrayList<>(List.of("heat = 100", "冷却"));
        var packet = new MessageDebugResponse(WORLD, BlockPos.ORIGIN, left, List.of("right")); left.clear();
        assertThrows(UnsupportedOperationException.class, () -> packet.left().clear());
        for (int i = 0; i < 3; i++) {
            PacketByteBuf wire = new PacketByteBuf(Unpooled.buffer());
            try { packet.write(wire); assertEquals(packet, MessageDebugResponse.read(wire)); }
            finally { wire.release(); }
        }
    }
    @Test public void malformedCountsDirectionsAndTrailingBytesAreRejected() {
        for (int count : new int[] {-1, 65, Integer.MAX_VALUE}) {
            PacketByteBuf wire = new PacketByteBuf(Unpooled.buffer());
            try {
                wire.writeIdentifier(WORLD); wire.writeBlockPos(BlockPos.ORIGIN); wire.writeVarInt(count);
                assertThrows(IllegalArgumentException.class, () -> MessageDebugResponse.read(wire));
            } finally { wire.release(); }
        }
        for (int face : new int[] {-1, 6}) {
            PacketByteBuf wire = new PacketByteBuf(Unpooled.buffer());
            try {
                wire.writeIdentifier(WORLD); wire.writeBlockPos(BlockPos.ORIGIN); wire.writeVarInt(face);
                assertThrows(RuntimeException.class, () -> MessageDebugRequest.read(wire));
            } finally { wire.release(); }
        }
        PacketByteBuf wire = new PacketByteBuf(Unpooled.buffer());
        try {
            new MessageDebugResponse(WORLD, BlockPos.ORIGIN, List.of(), List.of()).write(wire); wire.writeByte(0);
            assertThrows(IllegalArgumentException.class, () -> MessageDebugResponse.read(wire));
        } finally { wire.release(); }
    }
    @Test public void legitimateLongDebugOutputIsBoundedBeforeSending() {
        var lines = Collections.nCopies(100, "x".repeat(1000));
        var bounded = MessageDebugResponse.boundedLines(lines);
        assertEquals(64, bounded.size()); assertEquals(512, bounded.get(0).length());
        assertThrows(IllegalArgumentException.class, () -> new MessageDebugResponse(WORLD, BlockPos.ORIGIN, lines, List.of()));
    }
}
