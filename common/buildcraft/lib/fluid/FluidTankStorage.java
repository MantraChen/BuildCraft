/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.lib.fluid;

import java.util.Objects;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.nbt.NbtCompound;
import buildcraft.lib.compat.FluidStackBC;

/** One variant, exact droplet amounts, and a filter lock that participates in rollback. */
public final class FluidTankStorage extends SnapshotParticipant<FluidTankStorage.State>
        implements SingleSlotStorage<FluidVariant> {
    record State(FluidVariant variant, long amount, FluidVariant accepted) {}
    private FluidVariant variant = FluidVariant.blank(), accepted = FluidVariant.blank();
    private long amount;
    private final long capacity;
    private final Runnable changed;
    private Predicate<FluidVariant> filter = resource -> true;
    private boolean lockFirst, canInsert = true, canExtract = true;

    public FluidTankStorage(long capacity, Runnable changed) {
        StoragePreconditions.notNegative(capacity);
        this.capacity = capacity;
        this.changed = Objects.requireNonNull(changed);
    }
    public void setFilter(Predicate<FluidVariant> filter) { this.filter = Objects.requireNonNull(filter); }
    public void setLockFirst(boolean lockFirst) { this.lockFirst = lockFirst; }
    public FluidVariant getAcceptedVariant() { return accepted; }
    public void setAcceptedVariant(FluidVariant accepted) {
        this.accepted = Objects.requireNonNull(accepted);
        changed.run();
    }
    public void setCanInsert(boolean enabled) { canInsert = enabled; }
    public void setCanExtract(boolean enabled) { canExtract = enabled; }
    @Override public boolean supportsInsertion() { return canInsert; }
    @Override public boolean supportsExtraction() { return canExtract; }
    public boolean accepts(FluidVariant resource) {
        return canInsert && !resource.isBlank() && filter.test(resource)
            && (accepted.isBlank() || accepted.equals(resource));
    }
    @Override public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
        StoragePreconditions.notBlankNotNegative(resource, maxAmount);
        if (!accepts(resource) || (!variant.isBlank() && !variant.equals(resource))) return 0;
        long inserted = Math.min(maxAmount, capacity - amount);
        if (inserted > 0) {
            updateSnapshots(transaction);
            variant = resource;
            amount += inserted;
            if (lockFirst && accepted.isBlank()) accepted = resource;
        }
        return inserted;
    }
    @Override public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
        StoragePreconditions.notBlankNotNegative(resource, maxAmount);
        if (!canExtract || !variant.equals(resource)) return 0;
        long extracted = Math.min(maxAmount, amount);
        if (extracted > 0) {
            updateSnapshots(transaction);
            amount -= extracted;
            if (amount == 0) variant = FluidVariant.blank();
        }
        return extracted;
    }
    @Override public boolean isResourceBlank() { return variant.isBlank(); }
    @Override public FluidVariant getResource() { return variant; }
    @Override public long getAmount() { return amount; }
    @Override public long getCapacity() { return capacity; }
    @Override protected State createSnapshot() { return new State(variant, amount, accepted); }
    @Override protected void readSnapshot(State state) {
        variant = state.variant; amount = state.amount; accepted = state.accepted;
    }
    @Override protected void onFinalCommit() { changed.run(); }
    public NbtCompound writeNbt(NbtCompound tag) {
        tag.putInt("storageVersion", 1);
        tag.put("variant", variant.toNbt());
        tag.putLong("amount", amount);
        tag.put("acceptedVariant", accepted.toNbt());
        return tag;
    }
    /** Capacity belongs to the machine definition, never to a saved Capacity field. */
    public void readNbt(NbtCompound tag) {
        if (tag.contains("storageVersion")) {
            variant = FluidVariant.fromNbt(tag.getCompound("variant"));
            amount = tag.getLong("amount");
            accepted = FluidVariant.fromNbt(tag.getCompound("acceptedVariant"));
        } else {
            NbtCompound oldFluid = tag.contains("Fluid") ? tag.getCompound("Fluid") : tag;
            FluidStackBC fluid = FluidStackBC.fromNbt(oldFluid);
            variant = fluid.getFluidVariant();
            amount = fluid.getAmount();
            accepted = FluidVariant.blank();
        }
        amount = variant.isBlank() ? 0 : Math.max(0, Math.min(amount, capacity));
        if (amount == 0) variant = FluidVariant.blank();
        if (lockFirst && accepted.isBlank() && amount > 0) accepted = variant;
    }
}
