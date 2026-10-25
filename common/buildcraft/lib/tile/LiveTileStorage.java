/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.lib.tile;

import java.util.Iterator;
import java.util.function.BooleanSupplier;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.TransferVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;

/** A native automation reference and its retained views share the block entity's lifetime. */
public final class LiveTileStorage<T extends TransferVariant<?>> implements Storage<T> {
    private final Storage<T> delegate;
    private final T blank;
    private final BooleanSupplier available;

    public static <T extends TransferVariant<?>> Storage<T> of(Storage<T> delegate, T blank, BooleanSupplier available) {
        return delegate == null ? null : new LiveTileStorage<>(delegate, blank, available);
    }
    private LiveTileStorage(Storage<T> delegate, T blank, BooleanSupplier available) {
        this.delegate = delegate;
        this.blank = blank;
        this.available = available;
    }
    @Override public boolean supportsInsertion() { return available.getAsBoolean() && delegate.supportsInsertion(); }
    @Override public boolean supportsExtraction() { return available.getAsBoolean() && delegate.supportsExtraction(); }
    @Override public long insert(T resource, long amount, TransactionContext transaction) {
        StoragePreconditions.notBlankNotNegative(resource, amount);
        return available.getAsBoolean() ? delegate.insert(resource, amount, transaction) : 0;
    }
    @Override public long extract(T resource, long amount, TransactionContext transaction) {
        StoragePreconditions.notBlankNotNegative(resource, amount);
        return available.getAsBoolean() ? delegate.extract(resource, amount, transaction) : 0;
    }
    @Override public Iterator<StorageView<T>> iterator() {
        if (!available.getAsBoolean()) return java.util.Collections.emptyIterator();
        var iterator = delegate.iterator();
        return new Iterator<>() {
            @Override public boolean hasNext() { return available.getAsBoolean() && iterator.hasNext(); }
            @Override public StorageView<T> next() {
                if (!hasNext()) throw new java.util.NoSuchElementException();
                var view = iterator.next();
                return new StorageView<>() {
                    @Override public boolean isResourceBlank() { return !available.getAsBoolean() || view.isResourceBlank(); }
                    @Override public T getResource() { return available.getAsBoolean() ? view.getResource() : blank; }
                    @Override public long getAmount() { return available.getAsBoolean() ? view.getAmount() : 0; }
                    @Override public long getCapacity() { return available.getAsBoolean() ? view.getCapacity() : 0; }
                    @Override public long extract(T resource, long amount, TransactionContext transaction) {
                        StoragePreconditions.notBlankNotNegative(resource, amount);
                        return available.getAsBoolean() ? view.extract(resource, amount, transaction) : 0;
                    }
                };
            }
        };
    }
}
