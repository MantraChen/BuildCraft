package buildcraft.lib.net;

import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;

/** Immutable payload bytes, independent of Netty's connection-buffer lifetime. */
final class PacketPayload {
    private final byte[] bytes;

    private PacketPayload(byte[] bytes) {
        this.bytes = bytes;
    }

    static PacketPayload copyOf(PacketBufferBC buffer, int maximum) {
        checkSize(buffer.readableBytes(), maximum);
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.getBytes(buffer.readerIndex(), bytes);
        return new PacketPayload(bytes);
    }

    static PacketPayload create(IPayloadWriter writer, int maximum) {
        PacketBufferBC buffer = new PacketBufferBC(Unpooled.buffer());
        try {
            writer.write(buffer);
            return copyOf(buffer, maximum);
        } finally {
            buffer.release();
        }
    }

    static PacketPayload read(PacketByteBuf buffer, int length, int maximum) {
        checkSize(length, maximum);
        if (length != buffer.readableBytes()) {
            throw new IllegalArgumentException("Payload length does not match packet: " + length);
        }
        byte[] bytes = new byte[length];
        buffer.readBytes(bytes);
        return new PacketPayload(bytes);
    }

    private static void checkSize(int size, int maximum) {
        if (size < 0 || size > maximum) throw new IllegalArgumentException("Invalid payload size: " + size);
    }

    int size() { return bytes.length; }

    void write(PacketByteBuf buffer) { buffer.writeBytes(bytes); }

    /** Each reader owns and must release its view; reads cannot change a broadcast packet. */
    PacketBufferBC open() {
        return new PacketBufferBC(Unpooled.wrappedBuffer(bytes).asReadOnly());
    }
}
