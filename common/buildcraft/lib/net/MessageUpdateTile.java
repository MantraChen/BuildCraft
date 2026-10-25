/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 */
package buildcraft.lib.net;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** Block position followed by the original unsigned-medium-length binary tile payload. */
public final class MessageUpdateTile implements FabricPacket {
    public static final int HEADER_SIZE = Long.BYTES + 3;
    public static final int MAX_PAYLOAD = 1_048_576 - HEADER_SIZE;
    public static final PacketType<MessageUpdateTile> TYPE = PacketType.create(
        new Identifier("buildcraft", "update_tile_bc"), MessageUpdateTile::read);

    private final BlockPos pos;
    private final PacketPayload payload;

    /** Copies readable bytes; the caller retains ownership of the source buffer. */
    public MessageUpdateTile(BlockPos pos, PacketBufferBC payload) {
        this(pos, PacketPayload.copyOf(payload, MAX_PAYLOAD));
    }

    private MessageUpdateTile(BlockPos pos, PacketPayload payload) {
        this.pos = pos.toImmutable();
        this.payload = payload;
    }

    public BlockPos getPos() { return pos; }
    /** The caller must release this independent read-only view. */
    public PacketBufferBC getPayload() { return payload.open(); }
    public int getPayloadSize() { return payload.size(); }

    public static MessageUpdateTile read(PacketByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        return new MessageUpdateTile(pos, PacketPayload.read(buffer, buffer.readUnsignedMedium(), MAX_PAYLOAD));
    }

    @Override
    public void write(PacketByteBuf buffer) {
        buffer.writeBlockPos(pos);
        buffer.writeMedium(payload.size());
        payload.write(buffer);
    }

    @Override
    public PacketType<?> getType() { return TYPE; }

    public static MessageUpdateTile of(BlockPos pos, IPayloadWriter writer) {
        return new MessageUpdateTile(pos, PacketPayload.create(writer, MAX_PAYLOAD));
    }
}
