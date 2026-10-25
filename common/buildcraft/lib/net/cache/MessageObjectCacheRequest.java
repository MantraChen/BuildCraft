/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 */
package buildcraft.lib.net.cache;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import buildcraft.lib.net.PacketBufferBC;

/** Client requests a small batch of known server object IDs. */
public final class MessageObjectCacheRequest implements FabricPacket {
    public static final int MAX_IDS = 16;
    public static final PacketType<MessageObjectCacheRequest> TYPE = PacketType.create(
        new Identifier("buildcraft", "cache_request"), MessageObjectCacheRequest::read);
    private final int cacheId;
    private final int[] ids;

    MessageObjectCacheRequest(NetworkedObjectCache<?> cache, int[] ids) {
        this(BuildCraftObjectCaches.CACHES.indexOf(cache), ids);
    }

    private MessageObjectCacheRequest(int cacheId, int[] ids) {
        BuildCraftObjectCaches.getCache(cacheId);
        if (ids.length < 1 || ids.length > MAX_IDS) throw new IllegalArgumentException("Invalid cache request count");
        for (int id : ids) if (id < 0) throw new IllegalArgumentException("Invalid object ID");
        this.cacheId = cacheId;
        this.ids = ids.clone();
    }

    public static MessageObjectCacheRequest read(PacketByteBuf buffer) {
        int cache = buffer.readUnsignedByte();
        int count = buffer.readUnsignedShort();
        if (count < 1 || count > MAX_IDS || buffer.readableBytes() != count * Integer.BYTES) {
            throw new IllegalArgumentException("Invalid cache request length");
        }
        int[] ids = new int[count];
        for (int i = 0; i < count; i++) ids[i] = buffer.readInt();
        return new MessageObjectCacheRequest(cache, ids);
    }

    @Override public void write(PacketByteBuf buffer) {
        buffer.writeByte(cacheId);
        buffer.writeShort(ids.length);
        for (int id : ids) buffer.writeInt(id);
    }

    @Override public PacketType<?> getType() { return TYPE; }

    public MessageObjectCacheResponse createResponse() {
        NetworkedObjectCache<?> cache = BuildCraftObjectCaches.getCache(cacheId);
        byte[][] values = new byte[ids.length][];
        PacketBufferBC buffer = new PacketBufferBC(Unpooled.buffer());
        try {
            for (int i = 0; i < ids.length; i++) {
                cache.writeObjectServer(ids[i], buffer);
                if (buffer.readableBytes() > MessageObjectCacheResponse.MAX_OBJECT_BYTES) {
                    throw new IllegalArgumentException("Cached object exceeds packet limit");
                }
                values[i] = new byte[buffer.readableBytes()];
                buffer.readBytes(values[i]);
                buffer.clear();
            }
            return new MessageObjectCacheResponse(cacheId, ids, values);
        } finally { buffer.release(); }
    }
}
