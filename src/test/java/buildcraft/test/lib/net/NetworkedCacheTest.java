package buildcraft.test.lib.net;

import buildcraft.lib.compat.FluidStackBC;
import buildcraft.lib.net.cache.*;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class NetworkedCacheTest {
    @Before public void reset() {
        BuildCraftObjectCaches.onServerStarting();
        BuildCraftObjectCaches.onClientJoinServer();
    }

    private List<MessageObjectCacheRequest> requests() {
        List<MessageObjectCacheRequest> packets = new ArrayList<>();
        BuildCraftObjectCaches.onClientTick(packets::add);
        return packets;
    }

    private void exchange(MessageObjectCacheRequest request) throws Exception {
        PacketByteBuf wire = new PacketByteBuf(Unpooled.buffer());
        try {
            request.write(wire);
            MessageObjectCacheRequest decoded = MessageObjectCacheRequest.read(wire);
            MessageObjectCacheResponse response = decoded.createResponse();
            wire.clear(); response.write(wire);
            MessageObjectCacheResponse received = MessageObjectCacheResponse.read(wire);
            assertFalse(wire.isReadable());
            received.apply();
        } finally { wire.release(); }
    }

    @Test public void itemIdsIgnoreCountButPreserveNbtAndInputMutation() throws Exception {
        ItemStack stack = new ItemStack(Items.DIAMOND, 32);
        stack.getOrCreateNbt().putString("origin", "old");
        ItemStack original = stack.copy();
        int id = BuildCraftObjectCaches.storeItemStack(stack);
        assertEquals(id, BuildCraftObjectCaches.storeItemStack(stack.copyWithCount(1)));
        stack.getOrCreateNbt().putString("origin", "new");
        assertNotEquals(id, BuildCraftObjectCaches.storeItemStack(stack));
        assertEquals(id, BuildCraftObjectCaches.getItemStackId(original));
        Supplier<ItemStack> link = BuildCraftObjectCaches.retrieveItemStack(id);
        assertTrue(link.get().isEmpty());
        exchange(requests().get(0));
        assertEquals("old", link.get().getNbt().getString("origin"));
        assertEquals(1, link.get().getCount());
        ItemStack returned = link.get(); returned.getNbt().putString("origin", "mutated"); returned.setCount(50);
        assertEquals("old", link.get().getNbt().getString("origin"));
        assertEquals(1, link.get().getCount());
    }

    @Test public void repeatedRetrievalSharesOneRequestAndBroadcastIsReusable() throws Exception {
        int id = BuildCraftObjectCaches.storeItemStack(new ItemStack(Items.GOLD_INGOT));
        Supplier<ItemStack> first = BuildCraftObjectCaches.retrieveItemStack(id);
        assertSame(first, BuildCraftObjectCaches.retrieveItemStack(id));
        List<MessageObjectCacheRequest> requests = requests();
        assertEquals(1, requests.size());
        assertTrue(requests().isEmpty());
        exchange(requests.get(0)); exchange(requests.get(0));
        assertTrue(first.get().isOf(Items.GOLD_INGOT));
    }

    @Test public void fluidCachePreservesVariantAndDoesNotInventWaterWhileLoading() throws Exception {
        NbtCompound nbt = new NbtCompound(); nbt.putString("grade", "refined");
        FluidVariant fluid = FluidVariant.of(Fluids.LAVA, nbt);
        int id = BuildCraftObjectCaches.storeFluid(fluid);
        assertEquals(id, BuildCraftObjectCaches.CACHE_FLUIDS.server().store(FluidStackBC.of(fluid, 81000)));
        assertNotEquals(id, BuildCraftObjectCaches.storeFluid(FluidVariant.of(Fluids.LAVA)));
        Supplier<FluidVariant> link = BuildCraftObjectCaches.retrieveFluid(id);
        assertTrue(link.get().isBlank());
        for (MessageObjectCacheRequest request : requests()) exchange(request);
        assertEquals(fluid, link.get());
    }

    @Test public void batchesStayBoundedAndEventuallyResolveAllLinks() throws Exception {
        List<Supplier<ItemStack>> links = new ArrayList<>();
        for (int i = 0; i < 35; i++) {
            ItemStack stack = new ItemStack(Items.PAPER);
            stack.getOrCreateNbt().putInt("page", i);
            links.add(BuildCraftObjectCaches.retrieveItemStack(BuildCraftObjectCaches.storeItemStack(stack)));
        }
        for (int pass = 0; pass < 3; pass++) {
            List<MessageObjectCacheRequest> batch = requests();
            assertEquals(1, batch.size());
            PacketByteBuf wire = new PacketByteBuf(Unpooled.buffer());
            try { batch.get(0).write(wire); assertTrue(wire.readableBytes() <= 3 + 16 * 4); }
            finally { wire.release(); }
            exchange(batch.get(0));
        }
        assertTrue(requests().isEmpty());
        for (int i = 0; i < links.size(); i++) assertEquals(i, links.get(i).get().getNbt().getInt("page"));
    }

    @Test public void disconnectInvalidatesOldLinksAndServerResetReusesIdsSafely() throws Exception {
        int oldId = BuildCraftObjectCaches.storeItemStack(new ItemStack(Items.DIAMOND));
        Supplier<ItemStack> old = BuildCraftObjectCaches.retrieveItemStack(oldId);
        exchange(requests().get(0)); assertTrue(old.get().isOf(Items.DIAMOND));
        BuildCraftObjectCaches.onClientJoinServer();
        assertTrue(old.get().isEmpty());
        BuildCraftObjectCaches.onServerStopped();
        assertEquals(-1, BuildCraftObjectCaches.getItemStackId(new ItemStack(Items.DIAMOND)));
        int newId = BuildCraftObjectCaches.storeItemStack(new ItemStack(Items.EMERALD));
        assertEquals(oldId, newId);
        Supplier<ItemStack> current = BuildCraftObjectCaches.retrieveItemStack(newId);
        exchange(requests().get(0));
        assertTrue(current.get().isOf(Items.EMERALD)); assertTrue(old.get().isEmpty());
    }

    @Test public void invalidRequestsAreRejectedBeforeAccessingObjects() {
        for (int count : new int[] {0, 17, 65535}) {
            PacketByteBuf wire = new PacketByteBuf(Unpooled.buffer());
            try {
                wire.writeByte(0); wire.writeShort(count);
                assertThrows(IllegalArgumentException.class, () -> MessageObjectCacheRequest.read(wire));
            } finally { wire.release(); }
        }
        PacketByteBuf wire = new PacketByteBuf(Unpooled.buffer());
        try {
            wire.writeByte(255); wire.writeShort(1); wire.writeInt(0);
            assertThrows(IllegalArgumentException.class, () -> MessageObjectCacheRequest.read(wire));
            wire.clear(); wire.writeByte(0); wire.writeShort(1); wire.writeInt(-1);
            assertThrows(IllegalArgumentException.class, () -> MessageObjectCacheRequest.read(wire));
            wire.clear(); wire.writeByte(0); wire.writeShort(1); wire.writeInt(999);
            MessageObjectCacheRequest request = MessageObjectCacheRequest.read(wire);
            assertThrows(IllegalArgumentException.class, request::createResponse);
        } finally { wire.release(); }
    }

    @Test public void malformedResponseLengthsAndTrailingDataAreRejected() {
        PacketByteBuf wire = new PacketByteBuf(Unpooled.buffer());
        try {
            wire.writeByte(0); wire.writeShort(1); wire.writeInt(0); wire.writeShort(9); wire.writeByte(1);
            assertThrows(IllegalArgumentException.class, () -> MessageObjectCacheResponse.read(wire));
            wire.clear(); wire.writeByte(0); wire.writeShort(1); wire.writeInt(0); wire.writeShort(0); wire.writeByte(1);
            assertThrows(IllegalArgumentException.class, () -> MessageObjectCacheResponse.read(wire));
        } finally { wire.release(); }
    }

    @Test public void failedSendingKeepsThePendingRequest() {
        int id = BuildCraftObjectCaches.storeItemStack(new ItemStack(Items.COAL));
        BuildCraftObjectCaches.retrieveItemStack(id);
        assertThrows(IllegalStateException.class, () -> BuildCraftObjectCaches.onClientTick(p -> {
            throw new IllegalStateException("Disconnected");
        }));
        assertEquals(1, requests().size());
    }

    @Test public void emptySentinelDoesNotRequestAnInvalidServerId() {
        assertTrue(BuildCraftObjectCaches.retrieveItemStack(-1).get().isEmpty());
        assertTrue(BuildCraftObjectCaches.retrieveFluid(-1).get().isBlank());
        assertTrue(requests().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> BuildCraftObjectCaches.retrieveItemStack(-2));
    }
}
