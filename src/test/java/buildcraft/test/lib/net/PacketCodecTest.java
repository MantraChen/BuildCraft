package buildcraft.test.lib.net;

import buildcraft.lib.net.*;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.nio.ReadOnlyBufferException;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.BlockPos;
import org.junit.Test;
import static org.junit.Assert.*;

public class PacketCodecTest {
    @Test public void tilePacketCopiesOnlyReadableBytesAndKeepsPosition() {
        PacketBufferBC source = new PacketBufferBC(Unpooled.buffer());
        BlockPos.Mutable pos = new BlockPos.Mutable(4, -2, 7);
        source.writeInt(123);
        source.writeInt(456);
        source.readInt();
        MessageUpdateTile packet = new MessageUpdateTile(pos, source);
        assertEquals(4, source.readerIndex());
        source.setInt(4, 789);
        source.release();
        pos.set(0, 0, 0);
        assertEquals(new BlockPos(4, -2, 7), packet.getPos());
        PacketBufferBC read = packet.getPayload();
        try { assertEquals(456, read.readInt()); } finally { read.release(); }
    }

    @Test public void broadcastCanBeEncodedAndReadRepeatedly() {
        MessageUpdateTile message = MessageUpdateTile.of(BlockPos.ORIGIN, b -> b.writeInt(42));
        for (int i = 0; i < 3; i++) {
            PacketByteBuf wire = new PacketByteBuf(Unpooled.buffer());
            try {
                message.write(wire);
                MessageUpdateTile decoded = MessageUpdateTile.read(wire);
                assertFalse(wire.isReadable());
                PacketBufferBC read = decoded.getPayload();
                try { assertEquals(42, read.readInt()); } finally { read.release(); }
            } finally { wire.release(); }
        }
        assertEquals(4, message.getPayloadSize());
    }

    @Test public void independentViewsCannotMutateThePayload() {
        MessageContainer packet = MessageContainer.of(7, 65535, b -> b.writeInt(3));
        PacketBufferBC first = packet.getPayload(), second = packet.getPayload();
        try {
            assertThrows(ReadOnlyBufferException.class, () -> first.setInt(0, 4));
            first.readInt();
            assertEquals(3, second.readInt());
        } finally { first.release(); second.release(); }
    }

    @Test public void containerWireKeepsSyncIdMessageIdAndPackedFlags() {
        MessageContainer message = MessageContainer.of(12, 65234, b -> {
            b.writeBoolean(true); b.writeInt(56); b.writeBoolean(false); b.writeBoolean(true);
        });
        PacketByteBuf wire = new PacketByteBuf(Unpooled.buffer());
        try {
            message.write(wire);
            MessageContainer decoded = MessageContainer.read(wire);
            assertEquals(12, decoded.getWindowId());
            assertEquals(65234, decoded.getMessageId());
            PacketBufferBC read = decoded.getPayload();
            try {
                assertTrue(read.readBoolean()); assertEquals(56, read.readInt());
                assertFalse(read.readBoolean()); assertTrue(read.readBoolean());
                assertFalse(read.isReadable());
            } finally { read.release(); }
        } finally { wire.release(); }
    }

    @Test public void framingRejectsTruncationTrailingDataAndOversize() {
        for (int claimed : new int[] {0, 2, MessageUpdateTile.MAX_PAYLOAD + 1}) {
            PacketByteBuf wire = new PacketByteBuf(Unpooled.buffer());
            try {
                wire.writeBlockPos(BlockPos.ORIGIN); wire.writeMedium(claimed); wire.writeByte(1);
                assertThrows(IllegalArgumentException.class, () -> MessageUpdateTile.read(wire));
            } finally { wire.release(); }
        }
        PacketByteBuf wire = new PacketByteBuf(Unpooled.buffer());
        try {
            wire.writeInt(1); wire.writeShort(2); wire.writeShort(20); wire.writeByte(1);
            assertThrows(IllegalArgumentException.class, () -> MessageContainer.read(wire));
        } finally { wire.release(); }
        assertThrows(IllegalArgumentException.class, () -> MessageContainer.of(1, 2, b -> b.writeZero(65536)));
        assertThrows(IllegalArgumentException.class, () -> MessageContainer.of(1, 65536, b -> {}));
        assertThrows(IllegalArgumentException.class, () -> MessageContainer.of(-1, 0, b -> {}));
    }

    @Test public void writerBuffersAreReleasedOnSuccessAndFailure() {
        PacketBufferBC[] captured = new PacketBufferBC[1];
        MessageUpdateTile.of(BlockPos.ORIGIN, b -> { captured[0] = b; b.writeInt(1); });
        assertEquals(0, captured[0].refCnt());
        assertThrows(IllegalStateException.class, () -> MessageContainer.of(1, 2, b -> {
            captured[0] = b; throw new IllegalStateException("writer failed");
        }));
        assertEquals(0, captured[0].refCnt());
    }

    @Test public void serverboundLimitIncludesTheHeader() {
        NetworkRouting.validateServerbound(MessageUpdateTile.of(BlockPos.ORIGIN,
            b -> b.writeZero(32767 - MessageUpdateTile.HEADER_SIZE)));
        assertThrows(IllegalArgumentException.class, () -> NetworkRouting.validateServerbound(
            MessageUpdateTile.of(BlockPos.ORIGIN, b -> b.writeZero(32768 - MessageUpdateTile.HEADER_SIZE))));
        assertThrows(IllegalArgumentException.class, () -> NetworkRouting.validateServerbound(
            MessageContainer.of(1, 2, b -> b.writeZero(32768 - MessageContainer.HEADER_SIZE))));
    }

    @Test public void stringsUseBoundedMinecraftFraming() {
        PacketBufferBC buffer = new PacketBufferBC(Unpooled.buffer());
        try {
            buffer.writeString("Remake by FelixMantra · 中文");
            assertEquals("Remake by FelixMantra · 中文", buffer.readString());
            buffer.clear(); buffer.writeVarInt(Integer.MAX_VALUE);
            assertThrows(DecoderException.class, buffer::readString);
            buffer.clear(); buffer.writeVarInt(-1);
            assertThrows(DecoderException.class, buffer::readString);
        } finally { buffer.release(); }
    }
}
