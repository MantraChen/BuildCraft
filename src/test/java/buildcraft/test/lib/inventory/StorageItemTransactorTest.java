/*
 * Copyright (c) 2026 FelixMantra
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * https://mozilla.org/MPL/2.0/
 */
package buildcraft.test.lib.inventory;

import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.item.InventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.CombinedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleVariantStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.util.math.Direction;
import net.minecraft.util.collection.DefaultedList;

import buildcraft.lib.inventory.StorageItemTransactor;
import buildcraft.lib.inventory.InventoryWrapper;

import static org.junit.Assert.*;

public class StorageItemTransactorTest {
    @BeforeClass
    public static void bootstrap() {
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
    }

    private static class Store extends SingleVariantStorage<ItemVariant> {
        @Override protected ItemVariant getBlankVariant() { return ItemVariant.blank(); }
        @Override protected long getCapacity(ItemVariant variant) { return 64; }
    }

    @Test
    public void insufficientSpaceRollsBackAllOrNoneInsertion() {
        Store store = new Store();
        StorageItemTransactor transactor = new StorageItemTransactor(store);
        transactor.insert(new ItemStack(Items.STONE, 60), false, false);
        ItemStack incoming = new ItemStack(Items.STONE, 10);
        assertEquals(10, transactor.insert(incoming, true, false).getCount());
        assertEquals(60, store.amount);
        assertEquals(10, incoming.getCount());
    }

    @Test
    public void partialInsertReturnsExactRemainder() {
        Store store = new Store();
        StorageItemTransactor transactor = new StorageItemTransactor(store);
        transactor.insert(new ItemStack(Items.STONE, 60), false, false);
        assertEquals(6, transactor.insert(new ItemStack(Items.STONE, 10), false, false).getCount());
        assertEquals(64, store.amount);
    }

    @Test
    public void simulatedOperationsDoNotChangeStorage() {
        Store store = new Store();
        StorageItemTransactor transactor = new StorageItemTransactor(store);
        assertTrue(transactor.insert(new ItemStack(Items.STONE, 5), true, true).isEmpty());
        assertEquals(0, store.amount);
        transactor.insert(new ItemStack(Items.STONE, 5), true, false);
        assertEquals(3, transactor.extract(null, 1, 3, true).getCount());
        assertEquals(5, store.amount);
    }

    @Test
    public void minimumExtractionCanCombineMatchingSlots() {
        Store first = new Store();
        Store second = new Store();
        new StorageItemTransactor(first).insert(new ItemStack(Items.STONE, 3), false, false);
        new StorageItemTransactor(second).insert(new ItemStack(Items.STONE, 4), false, false);
        StorageItemTransactor transactor = new StorageItemTransactor(new CombinedStorage<>(List.of(first, second)));
        assertEquals(6, transactor.extract(null, 6, 6, false).getCount());
        assertEquals(1, first.amount + second.amount);
    }

    @Test
    public void insufficientExtractionLeavesEverySlotUntouched() {
        Store first = new Store();
        Store second = new Store();
        new StorageItemTransactor(first).insert(new ItemStack(Items.STONE, 3), false, false);
        new StorageItemTransactor(second).insert(new ItemStack(Items.STONE, 4), false, false);
        StorageItemTransactor transactor = new StorageItemTransactor(new CombinedStorage<>(List.of(first, second)));
        assertTrue(transactor.extract(null, 8, 8, false).isEmpty());
        assertEquals(3, first.amount);
        assertEquals(4, second.amount);
    }

    @Test
    public void differentNbtVariantsAreNotCombined() {
        Store first = new Store();
        Store second = new Store();
        ItemStack named = new ItemStack(Items.STONE, 4);
        named.getOrCreateNbt().putString("test", "different");
        new StorageItemTransactor(first).insert(new ItemStack(Items.STONE, 3), false, false);
        new StorageItemTransactor(second).insert(named, false, false);
        StorageItemTransactor transactor = new StorageItemTransactor(new CombinedStorage<>(List.of(first, second)));
        assertTrue(transactor.extract(null, 6, 6, false).isEmpty());
        assertEquals(7, first.amount + second.amount);
    }

    @Test
    public void simulatedBatchSharesCapacityAndRollsBack() {
        Store store = new Store();
        StorageItemTransactor transactor = new StorageItemTransactor(store);
        DefaultedList<ItemStack> batch = DefaultedList.of();
        batch.add(new ItemStack(Items.STONE, 40));
        batch.add(new ItemStack(Items.STONE, 40));
        DefaultedList<ItemStack> remainder = transactor.insert(batch, true);
        assertEquals(1, remainder.size());
        assertEquals(16, remainder.get(0).getCount());
        assertEquals(0, store.amount);
        assertEquals(40, batch.get(0).getCount());
    }

    @Test
    public void callerTransactionCanRollBackCommittedOperations() {
        Store store = new Store();
        StorageItemTransactor transactor = new StorageItemTransactor(store);
        try (Transaction outer = Transaction.openOuter()) {
            transactor.insert(new ItemStack(Items.STONE, 5), true, false);
            assertEquals(5, store.amount);
        }
        assertEquals(0, store.amount);
    }

    @Test
    public void extractionHonorsFilterAndMaximum() {
        Store first = new Store();
        Store second = new Store();
        new StorageItemTransactor(first).insert(new ItemStack(Items.STONE, 10), false, false);
        new StorageItemTransactor(second).insert(new ItemStack(Items.DIRT, 10), false, false);
        StorageItemTransactor transactor = new StorageItemTransactor(new CombinedStorage<>(List.of(first, second)));
        ItemStack result = transactor.extract(stack -> stack.isOf(Items.DIRT), 1, 4, false);
        assertEquals(Items.DIRT, result.getItem());
        assertEquals(4, result.getCount());
        assertEquals(10, first.amount);
        assertEquals(6, second.amount);
    }

    @Test
    public void vanillaInventoryAdapterPreservesAtomicity() {
        SimpleInventory inventory = new SimpleInventory(2);
        inventory.setStack(0, new ItemStack(Items.STONE, 60));
        inventory.setStack(1, new ItemStack(Items.DIRT, 64));
        InventoryWrapper wrapper = new InventoryWrapper(inventory);
        assertEquals(10, wrapper.insert(new ItemStack(Items.STONE, 10), true, false).getCount());
        assertEquals(60, inventory.getStack(0).getCount());
        assertEquals(6, wrapper.insert(new ItemStack(Items.STONE, 10), false, false).getCount());
        assertEquals(64, inventory.getStack(0).getCount());
    }

    private static class SidedStore extends SimpleInventory implements SidedInventory {
        SidedStore() { super(1); }
        @Override public int[] getAvailableSlots(Direction side) { return new int[] {0}; }
        @Override public boolean canInsert(int slot, ItemStack stack, Direction side) { return side == Direction.UP; }
        @Override public boolean canExtract(int slot, ItemStack stack, Direction side) { return side == Direction.DOWN; }
    }

    @Test
    public void sidedInventoryRestrictionsArePreserved() {
        SidedStore inventory = new SidedStore();
        StorageItemTransactor top = new StorageItemTransactor(InventoryStorage.of(inventory, Direction.UP));
        StorageItemTransactor bottom = new StorageItemTransactor(InventoryStorage.of(inventory, Direction.DOWN));
        assertEquals(5, bottom.insert(new ItemStack(Items.STONE, 5), false, false).getCount());
        assertTrue(top.insert(new ItemStack(Items.STONE, 5), true, false).isEmpty());
        assertTrue(top.extract(null, 1, 5, false).isEmpty());
        assertEquals(5, bottom.extract(null, 1, 5, false).getCount());
        assertTrue(inventory.getStack(0).isEmpty());
    }
}
