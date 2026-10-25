/*
 * Copyright (c) 2026 FelixMantra
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.inventory;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;

import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;

import buildcraft.api.core.IStackFilter;
import buildcraft.api.inventory.IItemTransactor;

/** Adapts Fabric inventories to BuildCraft's simulation and all-or-none contract. */
public class StorageItemTransactor implements IItemTransactor {
    private final Storage<ItemVariant> storage;

    public StorageItemTransactor(Storage<ItemVariant> storage) {
        this.storage = Objects.requireNonNull(storage);
    }

    @Override
    public ItemStack insert(ItemStack stack, boolean allOrNone, boolean simulate) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        try (Transaction transaction = Transaction.openNested(Transaction.getCurrentUnsafe())) {
            long inserted = storage.insert(ItemVariant.of(stack), stack.getCount(), transaction);
            if (allOrNone && inserted != stack.getCount()) return stack.copy();
            if (!simulate) transaction.commit();
            if (inserted == stack.getCount()) return ItemStack.EMPTY;
            ItemStack remainder = stack.copy();
            remainder.decrement((int) inserted);
            return remainder;
        }
    }

    @Override
    public DefaultedList<ItemStack> insert(DefaultedList<ItemStack> stacks, boolean simulate) {
        DefaultedList<ItemStack> remaining = DefaultedList.of();
        // Simulated batches share capacity accounting, then roll back together.
        try (Transaction transaction = Transaction.openNested(Transaction.getCurrentUnsafe())) {
            for (ItemStack stack : stacks) {
                ItemStack remainder = insert(stack, false, false);
                if (!remainder.isEmpty()) remaining.add(remainder);
            }
            if (!simulate) transaction.commit();
        }
        return remaining;
    }

    @Override
    public ItemStack extract(IStackFilter filter, int min, int max, boolean simulate) {
        int minimum = Math.max(1, min);
        if (max < minimum) return ItemStack.EMPTY;
        Set<ItemVariant> candidates = new LinkedHashSet<>();
        for (StorageView<ItemVariant> view : storage.nonEmptyViews()) {
            ItemVariant variant = view.getResource();
            if (!variant.isBlank() && (filter == null
                || filter.matches(variant.toStack((int) Math.min(view.getAmount(), Integer.MAX_VALUE))))) {
                candidates.add(variant);
            }
        }
        for (ItemVariant variant : candidates) {
            try (Transaction transaction = Transaction.openNested(Transaction.getCurrentUnsafe())) {
                long extracted = storage.extract(variant, max, transaction);
                if (extracted < minimum) continue;
                if (!simulate) transaction.commit();
                return variant.toStack((int) extracted);
            }
        }
        return ItemStack.EMPTY;
    }
}
