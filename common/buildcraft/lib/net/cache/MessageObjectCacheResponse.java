/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 */
package buildcraft.lib.net.cache;

import java.io.IOException;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import buildcraft.lib.net.PacketBufferBC;

/** Immutable cached values; the worst-case batch fits Minecraft's 1 MiB S2C limit. */
public final class MessageObjectCacheResponse implements FabricPacket {
    public static final int MAX_OBJECT_BYTES = 65520;
    public static final PacketType<MessageObjectCacheResponse> TYPE = PacketType.create(
        new Identifier("buildcraft", "cache_response"), MessageObjectCacheResponse::read);
    private final int cacheId;
    private final int[] ids;
    private final byte[][] values;

    MessageObjectCacheResponse(int cacheId, int[] ids, byte[][] values) {
        BuildCraftObjectCaches.getCache(cacheId);
        if (ids.length < 1 || ids.length > MessageObjectCacheRequest.MAX_IDS || values.length != ids.length) {
            throw new IllegalArgumentException("Invalid cache response count");
        }
        this.cacheId = cacheId;
        this.ids = ids.clone();
        this.values = new byte[values.length][];
        for (int i = 0; i < ids.length; i++) {
            if (ids[i] < 0 || values[i].length > MAX_OBJECT_BYTES) throw new IllegalArgumentException("Invalid cache value");
            this.values[i] = values[i].clone();
        }
    }

    public static MessageObjectCacheResponse read(PacketByteBuf buffer) {
        int cacheId = buffer.readUnsignedByte();
        int count = buffer.readUnsignedShort();
        if (count < 1 || count > MessageObjectCacheRequest.MAX_IDS || count * 6 > buffer.readableBytes()) {
            throw new IllegalArgumentException("Invalid cache response count");
        }
        int[] ids = new int[count];
        byte[][] values = new byte[count][];
        for (int i = 0; i < count; i++) {
            ids[i] = buffer.readInt();
            int length = buffer.readUnsignedShort();
            if (length > MAX_OBJECT_BYTES || length > buffer.readableBytes()) throw new IllegalArgumentException("Invalid cached value length");
            values[i] = new byte[length];
            buffer.readBytes(values[i]);
        }
        if (buffer.isReadable()) throw new IllegalArgumentException("Trailing cache response data");
        return new MessageObjectCacheResponse(cacheId, ids, values);
    }

    @Override public void write(PacketByteBuf buffer) {
        buffer.writeByte(cacheId);
        buffer.writeShort(ids.length);
        for (int i = 0; i < ids.length; i++) {
            buffer.writeInt(ids[i]);
            buffer.writeShort(values[i].length);
            buffer.writeBytes(values[i]);
        }
    }

    @Override public PacketType<?> getType() { return TYPE; }

    public void apply() throws IOException {
        NetworkedObjectCache<?> cache = BuildCraftObjectCaches.getCache(cacheId);
        for (int i = 0; i < ids.length; i++) {
            PacketBufferBC buffer = new PacketBufferBC(Unpooled.wrappedBuffer(values[i]).asReadOnly());
            try { cache.readObjectClient(ids[i], buffer); } finally { buffer.release(); }
        }
    }
}
