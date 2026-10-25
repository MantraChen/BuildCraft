package buildcraft.lib.net;

import java.io.IOException;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Shared client/server routing. Fabric's typed receivers invoke this on the game thread. */
public final class NetworkRouting {
    private NetworkRouting() {}

    /** Minecraft 1.20.1 limits custom client-to-server payloads to 32767 bytes. */
    public static void validateServerbound(net.fabricmc.fabric.api.networking.v1.FabricPacket packet) {
        net.minecraft.network.PacketByteBuf buffer = new net.minecraft.network.PacketByteBuf(
            io.netty.buffer.Unpooled.buffer());
        try {
            packet.write(buffer);
            if (buffer.readableBytes() > 32767) throw new IllegalArgumentException("Serverbound packet exceeds 32767 bytes");
        } finally {
            buffer.release();
        }
    }

    public static void receiveTile(MessageUpdateTile message, NetworkContext context) throws IOException {
        PlayerEntity player = context.player();
        World world = player.getWorld();
        BlockPos pos = message.getPos();
        // Check before looking up the block entity: a packet must never load a chunk.
        if (!world.isInBuildLimit(pos) || !world.getWorldBorder().contains(pos)
            || !world.isChunkLoaded(pos)) return;
        if (!context.isClient() && (player.isSpectator()
            || player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64
            || !world.canPlayerModifyAt(player, pos))) return;
        BlockEntity tile = world.getBlockEntity(pos);
        if (!(tile instanceof IPayloadReceiver receiver) || tile.isRemoved()) return;
        if (!context.isClient() && !receiver.canReceivePayload(player)) return;
        PacketBufferBC payload = message.getPayload();
        try {
            MessageUpdateTile reply = receiver.receivePayload(context, payload);
            requireEmpty(payload);
            context.sendReply(reply);
        } finally {
            payload.release();
        }
    }

    public static void receiveContainer(MessageContainer message, NetworkContext context) throws IOException {
        PlayerEntity player = context.player();
        ScreenHandler screen = player.currentScreenHandler;
        if (screen.syncId != message.getWindowId() || !(screen instanceof IContainerPayloadReceiver receiver)) return;
        if (!context.isClient() && (player.isSpectator() || !screen.canUse(player))) return;
        PacketBufferBC payload = message.getPayload();
        try {
            receiver.readMessage(message.getMessageId(), payload, context.isClient(), context);
            requireEmpty(payload);
        } finally {
            payload.release();
        }
    }

    private static void requireEmpty(PacketBufferBC buffer) throws IOException {
        if (buffer.isReadable()) throw new IOException("Unread BuildCraft payload bytes: " + buffer.readableBytes());
    }
}
