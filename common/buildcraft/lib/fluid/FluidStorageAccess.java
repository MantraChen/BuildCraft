package buildcraft.lib.fluid;

import java.util.Iterator;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;

/** Restricts automation access without restricting the machine's internal processing or manual GUI. */
public record FluidStorageAccess(Storage<FluidVariant> delegate, boolean insertion, boolean extraction)
        implements Storage<FluidVariant> {
    @Override public long insert(FluidVariant variant, long max, TransactionContext tx) {
        StoragePreconditions.notBlankNotNegative(variant, max);
        return insertion ? delegate.insert(variant, max, tx) : 0;
    }
    @Override public long extract(FluidVariant variant, long max, TransactionContext tx) {
        StoragePreconditions.notBlankNotNegative(variant, max);
        return extraction ? delegate.extract(variant, max, tx) : 0;
    }
    @Override public boolean supportsInsertion() { return insertion && delegate.supportsInsertion(); }
    @Override public boolean supportsExtraction() { return extraction && delegate.supportsExtraction(); }
    @Override public long getVersion() { return delegate.getVersion(); }
    @Override public Iterator<StorageView<FluidVariant>> iterator() {
        Iterator<StorageView<FluidVariant>> iterator = delegate.iterator();
        return new Iterator<>() {
            public boolean hasNext() { return iterator.hasNext(); }
            public StorageView<FluidVariant> next() {
                StorageView<FluidVariant> view = iterator.next();
                return new StorageView<>() {
                    public long extract(FluidVariant resource, long max, TransactionContext tx) {
                        StoragePreconditions.notBlankNotNegative(resource, max);
                        return extraction ? view.extract(resource, max, tx) : 0;
                    }
                    public boolean isResourceBlank() { return view.isResourceBlank(); }
                    public FluidVariant getResource() { return view.getResource(); }
                    public long getAmount() { return view.getAmount(); }
                    public long getCapacity() { return view.getCapacity(); }
                };
            }
        };
    }
}
