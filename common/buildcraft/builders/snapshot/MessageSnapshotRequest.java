/* Copyright (c) 2017 SpaceToad and the BuildCraft team. MPL-2.0. */
package buildcraft.builders.snapshot;

import java.util.Arrays;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import buildcraft.builders.item.ItemSnapshot;
import buildcraft.lib.misc.HashUtil;

public final class MessageSnapshotRequest implements FabricPacket {
    public static final PacketType<MessageSnapshotRequest> TYPE = PacketType.create(
        new Identifier("buildcraftbuilders", "snapshot_request"), MessageSnapshotRequest::new);
    public final Snapshot.Key key;
    public MessageSnapshotRequest(Snapshot.Key key) {
        if (key.hash.length != HashUtil.DIGEST_LENGTH) throw new IllegalArgumentException("Invalid snapshot hash");
        this.key = new Snapshot.Key(new Snapshot.Key(), key.hash.clone());
    }
    private MessageSnapshotRequest(PacketByteBuf buffer) {
        this(new Snapshot.Key(new Snapshot.Key(), buffer.readByteArray(HashUtil.DIGEST_LENGTH)));
        if (buffer.isReadable()) throw new IllegalArgumentException("Trailing snapshot request data");
    }
    @Override public PacketType<MessageSnapshotRequest> getType() { return TYPE; }
    @Override public void write(PacketByteBuf buffer) { buffer.writeByteArray(key.hash); }
    private boolean matches(ItemStack stack) {
        var header = stack.getItem() instanceof ItemSnapshot item ? item.getHeader(stack) : null;
        return header != null && Arrays.equals(header.key.hash, key.hash);
    }
    public MessageSnapshotResponse response(ServerPlayerEntity player) {
        // Only expose blueprints the player actually holds or is viewing in an accessible screen.
        boolean authorized = false;
        for (int i = 0; i < player.getInventory().size(); i++) authorized |= matches(player.getInventory().getStack(i));
        if (!authorized && player.currentScreenHandler.canUse(player))
            authorized = player.currentScreenHandler.slots.stream().anyMatch(slot -> matches(slot.getStack()));
        return MessageSnapshotResponse.of(key, authorized
            ? GlobalSavedDataSnapshots.get(player.getWorld()).getSnapshot(key) : null);
    }
}
