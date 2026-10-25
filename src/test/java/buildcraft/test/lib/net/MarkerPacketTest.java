package buildcraft.test.lib.net;

import buildcraft.lib.net.MessageMarker;
import buildcraft.lib.net.PacketBufferBC;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.junit.Test;
import static org.junit.Assert.*;

public class MarkerPacketTest {
    private final Identifier dimension = new Identifier("example", "moon");

    @Test public void singleMarkerRetainsDimensionAndRemovalFlags() {
        MessageMarker message = new MessageMarker(dimension, 1, false, false, List.of(new BlockPos(9, -64, -7)));
        MessageMarker decoded = roundTrip(message);
        assertEquals(dimension, decoded.dimension);
        assertEquals(1, decoded.cacheId);
        assertFalse(decoded.add); assertFalse(decoded.connection);
        assertEquals(message.positions, decoded.positions);
    }

    @Test public void connectionsPreservePositionOrderAndCanBroadcastTwice() {
        MessageMarker message = new MessageMarker(dimension, 0, true, true,
            List.of(new BlockPos(1, 2, 3), new BlockPos(4, 5, 6), new BlockPos(-7, 8, -9)));
        for (int i = 0; i < 2; i++) {
            MessageMarker decoded = roundTrip(message);
            assertTrue(decoded.add); assertTrue(decoded.connection);
            assertEquals(message.positions, decoded.positions);
        }
    }

    @Test public void sourcePositionsAndCollectionsAreCopied() {
        BlockPos.Mutable mutable = new BlockPos.Mutable(1, 2, 3);
        List<BlockPos> source = new ArrayList<>(); source.add(mutable);
        MessageMarker message = new MessageMarker(dimension, 0, true, false, source);
        mutable.set(9, 9, 9); source.clear();
        assertEquals(List.of(new BlockPos(1, 2, 3)), message.positions);
        assertThrows(UnsupportedOperationException.class, () -> message.positions.clear());
    }

    @Test public void invalidLengthsAndCachesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new MessageMarker(dimension, -1, true, false, List.of(BlockPos.ORIGIN)));
        assertThrows(IllegalArgumentException.class, () -> new MessageMarker(dimension, 0, true, false, List.of()));
        for (int count : new int[] {0, 2, 65535}) {
            PacketBufferBC wire = new PacketBufferBC(Unpooled.buffer());
            try {
                wire.writeIdentifier(dimension);
                wire.writeBoolean(true); wire.writeBoolean(true); wire.writeBoolean(false);
                wire.writeShort(0); wire.writeShort(count); wire.writeBlockPos(BlockPos.ORIGIN);
                assertThrows(IllegalArgumentException.class, () -> MessageMarker.read(wire));
            } finally { wire.release(); }
        }
    }

    private MessageMarker roundTrip(MessageMarker message) {
        PacketByteBuf wire = new PacketByteBuf(Unpooled.buffer());
        try {
            message.write(wire);
            MessageMarker decoded = MessageMarker.read(wire);
            assertFalse(wire.isReadable());
            return decoded;
        } finally { wire.release(); }
    }
}
