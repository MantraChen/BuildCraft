/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 */
package buildcraft.lib.net;

import java.util.Collection;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** Immutable marker membership/connection update, scoped to its source dimension. */
public final class MessageMarker implements FabricPacket {
    public static final int MAX_POSITIONS = 32767;
    public static final PacketType<MessageMarker> TYPE = PacketType.create(
        new Identifier("buildcraft", "markers"), MessageMarker::read);
    public final Identifier dimension;
    public final int cacheId;
    public final boolean add, connection;
    public final List<BlockPos> positions;

    public MessageMarker(Identifier dimension, int cacheId, boolean add, boolean connection, Collection<BlockPos> positions) {
        if (cacheId < 0 || cacheId > 32767 || positions.isEmpty() || positions.size() > MAX_POSITIONS) {
            throw new IllegalArgumentException("Invalid marker cache or position count");
        }
        this.dimension = java.util.Objects.requireNonNull(dimension);
        this.cacheId = cacheId;
        this.add = add;
        this.connection = connection;
        this.positions = positions.stream().map(BlockPos::toImmutable).toList();
    }

    public static MessageMarker read(PacketByteBuf wire) {
        Identifier dimension = wire.readIdentifier();
        PacketBufferBC buffer = new PacketBufferBC(wire);
        boolean add = buffer.readBoolean(), multiple = buffer.readBoolean(), connection = buffer.readBoolean();
        int cacheId = buffer.readUnsignedShort();
        int count = multiple ? buffer.readUnsignedShort() : 1;
        if (count < 1 || count > MAX_POSITIONS || buffer.readableBytes() != count * Long.BYTES) {
            throw new IllegalArgumentException("Invalid marker payload length");
        }
        java.util.ArrayList<BlockPos> positions = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) positions.add(buffer.readBlockPos());
        return new MessageMarker(dimension, cacheId, add, connection, positions);
    }

    @Override public void write(PacketByteBuf wire) {
        wire.writeIdentifier(dimension);
        PacketBufferBC buffer = new PacketBufferBC(wire);
        buffer.writeBoolean(add);
        buffer.writeBoolean(positions.size() != 1);
        buffer.writeBoolean(connection);
        buffer.writeShort(cacheId);
        if (positions.size() != 1) buffer.writeShort(positions.size());
        for (BlockPos pos : positions) buffer.writeBlockPos(pos);
    }

    @Override public PacketType<?> getType() { return TYPE; }
}
