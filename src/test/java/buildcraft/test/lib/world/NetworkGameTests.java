package buildcraft.test.lib.world;

import buildcraft.lib.net.*;
import com.mojang.authlib.GameProfile;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.*;
import net.minecraft.block.entity.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;

public class NetworkGameTests implements FabricGameTest {
    private static final Block BLOCK = Registry.register(Registries.BLOCK,
        new Identifier("buildcraft_port_tests", "network_receiver"), new ReceiverBlock());
    private static final BlockEntityType<ReceiverTile> TYPE = Registry.register(Registries.BLOCK_ENTITY_TYPE,
        new Identifier("buildcraft_port_tests", "network_receiver"),
        FabricBlockEntityTypeBuilder.create(ReceiverTile::new, BLOCK).build());
    private static final BlockPos POS = new BlockPos(2, 2, 2);

    /** Called before registry freeze by the fixture's initializer. */
    public static void initialize() {}

    private ReceiverTile place(TestContext test) {
        test.setBlockState(POS, BLOCK);
        return (ReceiverTile) test.getBlockEntity(POS);
    }

    private FakePlayer player(TestContext test) {
        FakePlayer player = FakePlayer.get(test.getWorld(), new GameProfile(UUID.randomUUID(), "NetworkTest"));
        BlockPos pos = test.getAbsolutePos(POS);
        player.setPosition(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        player.changeGameMode(GameMode.SURVIVAL);
        return player;
    }

    private NetworkContext context(PlayerEntity player) { return new NetworkContext(player, packet -> {}); }
    private MessageUpdateTile packet(ReceiverTile tile, int value) {
        return MessageUpdateTile.of(tile.getPos(), buffer -> buffer.writeInt(value));
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void tileReceivesPlayerContextAndReturnsReply(TestContext test) throws IOException {
        ReceiverTile tile = place(test);
        FakePlayer player = player(test);
        tile.reply = true;
        List<FabricPacket> replies = new ArrayList<>();
        NetworkRouting.receiveTile(packet(tile, 42), new NetworkContext(player, replies::add));
        test.assertTrue(tile.value == 42 && tile.sender == player, "Tile must receive payload and actual sending player");
        test.assertTrue(tile.lastBuffer.refCnt() == 0 && replies.size() == 1, "Payload must be released and reply sent once");
        test.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void tileRejectsDeniedDistantAndSpectatingPlayers(TestContext test) throws IOException {
        ReceiverTile tile = place(test);
        FakePlayer player = player(test);
        tile.allowed = false;
        NetworkRouting.receiveTile(packet(tile, 1), context(player));
        tile.allowed = true;
        player.setPosition(player.getX() + 9, player.getY(), player.getZ());
        NetworkRouting.receiveTile(packet(tile, 2), context(player));
        player.setPosition(player.getX() - 9, player.getY(), player.getZ());
        player.changeGameMode(GameMode.SPECTATOR);
        NetworkRouting.receiveTile(packet(tile, 3), context(player));
        test.assertTrue(tile.lastBuffer == null && tile.value == 0, "Unauthorized requests must never reach the tile");
        test.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void unloadedChunkRequestsDoNotLoadChunks(TestContext test) throws IOException {
        FakePlayer player = player(test);
        BlockPos far = new BlockPos(1000000, 100, 1000000);
        player.setPosition(far.getX(), far.getY(), far.getZ());
        test.assertTrue(!test.getWorld().isChunkLoaded(far), "Fixture target must start unloaded");
        NetworkRouting.receiveTile(MessageUpdateTile.of(far, b -> b.writeInt(1)), context(player));
        test.assertTrue(!test.getWorld().isChunkLoaded(far), "A packet must not generate or load a chunk");
        test.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void failedTileDecodeStillReleasesItsBuffer(TestContext test) throws IOException {
        ReceiverTile tile = place(test);
        tile.fail = true;
        try {
            NetworkRouting.receiveTile(packet(tile, 4), context(player(test)));
            throw new AssertionError("Expected decode failure");
        } catch (IOException expected) {
            test.assertTrue(tile.lastBuffer.refCnt() == 0, "Failed decode must release the payload");
        }
        test.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void screenMessagesRequireCurrentUsableSyncId(TestContext test) throws IOException {
        FakePlayer player = player(test);
        ReceiverScreen screen = new ReceiverScreen(7);
        ScreenHandler previous = player.currentScreenHandler;
        player.currentScreenHandler = screen;
        try {
            NetworkRouting.receiveContainer(MessageContainer.of(6, 12, b -> b.writeInt(10)), context(player));
            test.assertTrue(screen.calls == 0, "Stale screen ID must be ignored");
            screen.allowed = false;
            NetworkRouting.receiveContainer(MessageContainer.of(7, 12, b -> b.writeInt(20)), context(player));
            test.assertTrue(screen.calls == 0, "Unusable screen must be ignored");
            screen.allowed = true;
            NetworkRouting.receiveContainer(MessageContainer.of(7, 12, b -> b.writeInt(30)), context(player));
            test.assertTrue(screen.calls == 1 && screen.value == 30 && screen.messageId == 12,
                "Current screen must receive the selected message exactly once");
            test.assertTrue(screen.lastBuffer.refCnt() == 0, "Screen payload must be released");
        } finally { player.currentScreenHandler = previous; }
        test.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void trailingScreenPayloadIsRejectedAndReleased(TestContext test) throws IOException {
        FakePlayer player = player(test);
        ReceiverScreen screen = new ReceiverScreen(8);
        ScreenHandler previous = player.currentScreenHandler;
        player.currentScreenHandler = screen;
        try {
            try {
                NetworkRouting.receiveContainer(MessageContainer.of(8, 12, b -> {
                    b.writeInt(1); b.writeByte(2);
                }), context(player));
                throw new AssertionError("Expected unread payload rejection");
            } catch (IOException expected) {
                test.assertTrue(screen.lastBuffer.refCnt() == 0, "Rejected screen payload must be released");
            }
        } finally { player.currentScreenHandler = previous; }
        test.complete();
    }

    private static final class ReceiverBlock extends BlockWithEntity {
        ReceiverBlock() { super(AbstractBlock.Settings.create()); }
        @Override public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return new ReceiverTile(pos, state); }
    }

    private static final class ReceiverTile extends BlockEntity implements IPayloadReceiver {
        boolean allowed = true, fail, reply;
        int value;
        PlayerEntity sender;
        PacketBufferBC lastBuffer;
        ReceiverTile(BlockPos pos, BlockState state) { super(TYPE, pos, state); }
        @Override public boolean canReceivePayload(PlayerEntity player) { return allowed; }
        @Override public MessageUpdateTile receivePayload(NetworkContext context, PacketBufferBC payload) throws IOException {
            lastBuffer = payload;
            sender = context.player();
            if (fail) throw new IOException("Test decode failure");
            value = payload.readInt();
            return reply ? MessageUpdateTile.of(pos, b -> b.writeInt(value)) : null;
        }
    }

    private static final class ReceiverScreen extends ScreenHandler implements IContainerPayloadReceiver {
        boolean allowed = true;
        int calls, value, messageId;
        PacketBufferBC lastBuffer;
        ReceiverScreen(int id) { super(null, id); }
        @Override public boolean canUse(PlayerEntity player) { return allowed; }
        @Override public ItemStack quickMove(PlayerEntity player, int slot) { return ItemStack.EMPTY; }
        @Override public void readMessage(int id, PacketBufferBC buffer, boolean isClient, Object context) {
            messageId = id; lastBuffer = buffer; value = buffer.readInt(); calls++;
        }
    }
}
