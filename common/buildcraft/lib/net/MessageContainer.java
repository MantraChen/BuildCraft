/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 */
package buildcraft.lib.net;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

/** Screen sync ID, message ID and the original unsigned-short-length widget payload. */
public final class MessageContainer implements FabricPacket {
    public static final int HEADER_SIZE = Integer.BYTES + 2 * Short.BYTES;
    public static final int MAX_PAYLOAD = 0xffff;
    public static final PacketType<MessageContainer> TYPE = PacketType.create(
        new Identifier("buildcraft", "container"), MessageContainer::read);

    private final int windowId;
    private final int msgId;
    private final PacketPayload payload;

    /** Copies readable bytes; the caller retains ownership of the source buffer. */
    public MessageContainer(int windowId, int msgId, PacketBufferBC payload) {
        this(windowId, msgId, PacketPayload.copyOf(payload, MAX_PAYLOAD));
    }

    private MessageContainer(int windowId, int msgId, PacketPayload payload) {
        if (windowId < 0 || msgId < 0 || msgId > 0xffff) {
            throw new IllegalArgumentException("Invalid screen/message ID: " + windowId + "/" + msgId);
        }
        this.windowId = windowId;
        this.msgId = msgId;
        this.payload = payload;
    }

    public int getWindowId() { return windowId; }
    public int getMessageId() { return msgId; }
    public int getPayloadSize() { return payload.size(); }
    /** The caller must release this independent read-only view. */
    public PacketBufferBC getPayload() { return payload.open(); }

    public static MessageContainer read(PacketByteBuf buffer) {
        int windowId = buffer.readInt();
        int msgId = buffer.readUnsignedShort();
        return new MessageContainer(windowId, msgId,
            PacketPayload.read(buffer, buffer.readUnsignedShort(), MAX_PAYLOAD));
    }

    @Override
    public void write(PacketByteBuf buffer) {
        buffer.writeInt(windowId);
        buffer.writeShort(msgId);
        buffer.writeShort(payload.size());
        payload.write(buffer);
    }

    @Override
    public PacketType<?> getType() { return TYPE; }

    public static MessageContainer of(int windowId, int msgId, IPayloadWriter writer) {
        return new MessageContainer(windowId, msgId, PacketPayload.create(writer, MAX_PAYLOAD));
    }
}
