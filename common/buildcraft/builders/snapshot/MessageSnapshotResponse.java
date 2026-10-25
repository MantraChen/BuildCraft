/* Copyright (c) 2017 SpaceToad and the BuildCraft team. MPL-2.0. */
package buildcraft.builders.snapshot;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtTagSizeTracker;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import buildcraft.lib.misc.HashUtil;

/** Bounded native NBT; unavailable or oversized previews return an empty response. */
public final class MessageSnapshotResponse implements FabricPacket {
    public static final int MAX_BYTES = 1_000_000;
    public static final PacketType<MessageSnapshotResponse> TYPE = PacketType.create(
        new Identifier("buildcraftbuilders", "snapshot_response"), MessageSnapshotResponse::new);
    public final Snapshot.Key key;
    private final NbtCompound nbt;
    private MessageSnapshotResponse(Snapshot.Key key, NbtCompound nbt) { this.key = key; this.nbt = nbt; }
    private MessageSnapshotResponse(PacketByteBuf buffer) {
        if (buffer.readableBytes() > MAX_BYTES) throw new IllegalArgumentException("Snapshot response too large");
        byte[] hash = buffer.readByteArray(HashUtil.DIGEST_LENGTH);
        if (hash.length != HashUtil.DIGEST_LENGTH) throw new IllegalArgumentException("Invalid snapshot hash");
        key = new Snapshot.Key(new Snapshot.Key(), hash);
        nbt = buffer.readNbt(new NbtTagSizeTracker(2_097_152));
        if (buffer.isReadable()) throw new IllegalArgumentException("Trailing snapshot response data");
    }
    public static MessageSnapshotResponse of(Snapshot.Key key, Snapshot snapshot) {
        NbtCompound tag = snapshot == null ? null : Snapshot.writeToNBT(snapshot);
        PacketByteBuf buffer = new PacketByteBuf(Unpooled.buffer());
        try {
            // Read with the same allocation budget as the receiver before choosing to send it.
            buffer.writeNbt(tag);
            if (buffer.readableBytes() + 64 > MAX_BYTES) tag = null;
            else buffer.readNbt(new NbtTagSizeTracker(2_097_152));
        } catch (RuntimeException tooLarge) { tag = null; }
        finally { buffer.release(); }
        return new MessageSnapshotResponse(key, tag);
    }
    public Snapshot snapshot() throws java.io.IOException {
        if (nbt == null) return null;
        Snapshot snapshot = Snapshot.readFromNBT(nbt);
        Snapshot.Key stored = snapshot.key;
        snapshot.computeKey();
        if (!java.util.Arrays.equals(key.hash, snapshot.key.hash)
            || !java.util.Arrays.equals(stored.hash, snapshot.key.hash))
            throw new IllegalArgumentException("Snapshot hash does not match its content");
        return snapshot;
    }
    @Override public PacketType<MessageSnapshotResponse> getType() { return TYPE; }
    @Override public void write(PacketByteBuf buffer) { buffer.writeByteArray(key.hash); buffer.writeNbt(nbt); }
}
