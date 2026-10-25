package buildcraft.lib.net;

import java.util.Objects;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.minecraft.entity.player.PlayerEntity;

/** Receiving player and reply channel supplied by Fabric on the game thread. */
public record NetworkContext(PlayerEntity player, Consumer<FabricPacket> reply) {
    public NetworkContext {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(reply, "reply");
    }

    public boolean isClient() { return player.getWorld().isClient; }

    public void sendReply(FabricPacket packet) {
        if (packet != null) reply.accept(packet);
    }
}
