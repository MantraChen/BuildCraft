package buildcraft.test.lib.inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import buildcraft.api.core.EnumPipePart;
import buildcraft.lib.tile.item.ItemHandlerManager;
import buildcraft.lib.tile.item.ItemHandlerManager.EnumAccess;
import buildcraft.lib.tile.item.ItemHandlerSimple;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.Direction;
import org.junit.Test;

import static org.junit.Assert.*;

public class ItemHandlerManagerTest {
    private static final ItemVariant STONE = ItemVariant.of(Items.STONE);

    @Test public void namedGroupsAndPhantomFiltersSurviveNbtRoundTrip() {
        ItemHandlerManager source = new ItemHandlerManager(null);
        ItemHandlerSimple real = source.addInvHandler("real", 3, EnumAccess.BOTH);
        ItemHandlerSimple filter = source.addInvHandler("filter", 1, EnumAccess.PHANTOM);
        ItemStack tagged = new ItemStack(Items.DIAMOND, 12);
        tagged.getOrCreateNbt().putString("origin", "FelixMantra");
        real.setStackInSlot(2, tagged);
        filter.setStackInSlot(0, new ItemStack(Items.GOLD_INGOT));
        NbtCompound saved = source.serializeNBT();

        ItemHandlerManager restored = new ItemHandlerManager(null);
        ItemHandlerSimple restoredFilter = restored.addInvHandler("filter", 1, EnumAccess.PHANTOM);
        ItemHandlerSimple restoredReal = restored.addInvHandler("real", 3, EnumAccess.BOTH);
        restored.deserializeNBT(saved);
        assertTrue(ItemStack.areEqual(tagged, restoredReal.getStackInSlot(2)));
        assertTrue(restoredReal.getStackInSlot(0).isEmpty());
        assertEquals(Items.GOLD_INGOT, restoredFilter.getStackInSlot(0).getItem());
    }

    @Test public void dropsIncludePrivateInventoryButExcludePhantomAndAreCopies() {
        ItemHandlerManager manager = new ItemHandlerManager(null);
        ItemHandlerSimple real = manager.addInvHandler("private", 1, EnumAccess.NONE);
        real.setStackInSlot(0, new ItemStack(Items.STONE, 10));
        manager.addInvHandler("ghost", 1, EnumAccess.PHANTOM).setStackInSlot(0, new ItemStack(Items.DIAMOND, 64));
        DefaultedList<ItemStack> drops = DefaultedList.of();
        manager.addDrops(drops);
        assertEquals(1, drops.size());
        assertEquals(10, drops.get(0).getCount());
        drops.get(0).decrement(4);
        assertEquals(10, real.getStackInSlot(0).getCount());
        assertNull(manager.getStorage(Direction.UP));
        assertNull(manager.getStorage(null));
    }

    @Test public void insertionAndExtractionFacesCannotBypassRestrictionsViaViews() {
        ItemHandlerManager manager = new ItemHandlerManager(null);
        ItemHandlerSimple input = manager.addInvHandler("input", 1, EnumAccess.INSERT, EnumPipePart.UP);
        ItemHandlerSimple output = manager.addInvHandler("output", 1, EnumAccess.EXTRACT, EnumPipePart.DOWN);
        output.setStackInSlot(0, new ItemStack(Items.STONE, 7));
        Storage<ItemVariant> up = manager.getStorage(Direction.UP);
        Storage<ItemVariant> down = manager.getStorage(Direction.DOWN);
        try (Transaction tx = Transaction.openOuter()) {
            assertEquals(5, up.insert(STONE, 5, tx));
            assertEquals(0, up.extract(STONE, 5, tx));
            for (StorageView<ItemVariant> view : up) assertEquals(0, view.extract(STONE, 5, tx));
            assertEquals(0, down.insert(STONE, 5, tx));
            assertEquals(3, down.extract(STONE, 3, tx));
            tx.commit();
        }
        assertEquals(5, input.getStackInSlot(0).getCount());
        assertEquals(4, output.getStackInSlot(0).getCount());
        assertNull(manager.getStorage(Direction.NORTH));
    }

    @Test public void abortedTransfersAcrossFacesRestoreDataAndNeverNotify() {
        AtomicInteger changes = new AtomicInteger();
        ItemHandlerManager manager = new ItemHandlerManager((h, s, before, after) -> changes.incrementAndGet());
        ItemHandlerSimple handler = manager.addInvHandler("main", 1, EnumAccess.BOTH, EnumPipePart.VALUES);
        try (Transaction outer = Transaction.openOuter()) {
            manager.getStorage(Direction.UP).insert(STONE, 20, outer);
            try (Transaction nested = outer.openNested()) {
                manager.getStorage(Direction.DOWN).extract(STONE, 5, nested);
                nested.commit();
            }
            assertEquals(15, handler.getStackInSlot(0).getCount());
            assertEquals(0, changes.get());
        }
        assertTrue(handler.getStackInSlot(0).isEmpty());
        assertEquals(0, changes.get());
    }

    @Test public void nestedAbortThenCommitReportsOneFinalChange() {
        List<Integer> beforeCounts = new ArrayList<>(), afterCounts = new ArrayList<>();
        ItemHandlerManager manager = new ItemHandlerManager((h, s, before, after) -> {
            beforeCounts.add(before.getCount());
            afterCounts.add(after.getCount());
        });
        manager.addInvHandler("main", 1, EnumAccess.BOTH, EnumPipePart.VALUES);
        Storage<ItemVariant> storage = manager.getStorage(null);
        try (Transaction outer = Transaction.openOuter()) {
            try (Transaction nested = outer.openNested()) { storage.insert(STONE, 40, nested); }
            storage.insert(STONE, 12, outer);
            storage.extract(STONE, 2, outer);
            assertTrue(beforeCounts.isEmpty());
            outer.commit();
        }
        assertEquals(List.of(0), beforeCounts);
        assertEquals(List.of(10), afterCounts);
        try (Transaction second = Transaction.openOuter()) {
            storage.insert(STONE, 1, second);
            second.commit();
        }
        assertEquals(List.of(0, 10), beforeCounts);
        assertEquals(List.of(10, 11), afterCounts);
    }

    @Test public void slotFiltersAndLimitedCapacityAreRespectedByTransfers() {
        ItemHandlerManager manager = new ItemHandlerManager(null);
        ItemHandlerSimple handler = new ItemHandlerSimple(1, 3);
        handler.setChecker((slot, stack) -> stack.isOf(Items.STONE));
        manager.addInvHandler("limited", handler, EnumAccess.BOTH, EnumPipePart.VALUES);
        Storage<ItemVariant> storage = manager.getStorage(Direction.UP);
        assertEquals(3, storage.iterator().next().getCapacity());
        try (Transaction tx = Transaction.openOuter()) {
            assertEquals(0, storage.insert(ItemVariant.of(Items.DIRT), 12, tx));
            assertEquals(3, storage.insert(STONE, Long.MAX_VALUE, tx));
            assertEquals(0, storage.insert(STONE, Long.MAX_VALUE, tx));
            tx.commit();
        }
        assertEquals(3, handler.getStackInSlot(0).getCount());
    }

    @Test public void oversizedExistingStackIsNotShrunkOrDuplicatedByInsertion() {
        ItemHandlerSimple handler = new ItemHandlerSimple(1, 3);
        handler.setStackInSlot(0, new ItemStack(Items.STONE, 8));
        ItemStack remainder = handler.insertItem(0, new ItemStack(Items.STONE, Integer.MAX_VALUE), false);
        assertEquals(Integer.MAX_VALUE, remainder.getCount());
        assertEquals(8, handler.getStackInSlot(0).getCount());
        assertTrue(handler.extractItem(0, -5, false).isEmpty());
        assertEquals(8, handler.getStackInSlot(0).getCount());
    }

    @Test public void missingSavedGroupClearsStaleContents() {
        ItemHandlerManager manager = new ItemHandlerManager(null);
        ItemHandlerSimple handler = manager.addInvHandler("main", 2, EnumAccess.NONE);
        handler.setStackInSlot(1, new ItemStack(Items.DIAMOND));
        manager.deserializeNBT(new NbtCompound());
        assertTrue(handler.getStackInSlot(1).isEmpty());
    }

    @Test(expected = IllegalArgumentException.class)
    public void duplicateNamesCannotSilentlyReplaceSavedInventory() {
        ItemHandlerManager manager = new ItemHandlerManager(null);
        manager.addInvHandler("main", 1, EnumAccess.NONE);
        manager.addInvHandler("main", 1, EnumAccess.NONE);
    }

    @Test(expected = IllegalArgumentException.class)
    public void phantomSlotsCannotBeExposedToAutomation() {
        new ItemHandlerManager(null).addInvHandler("ghost", 1, EnumAccess.PHANTOM, EnumPipePart.UP);
    }
}
