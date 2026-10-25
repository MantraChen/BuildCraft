/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.energy;

import java.util.function.LongSupplier;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import team.reborn.energy.api.EnergyStorage;
import buildcraft.api.mj.MjBattery;

/** Exact integer conversion; aborted transfers restore both MJ and the tick's output allowance. */
public final class DynamoEnergyStorage extends SnapshotParticipant<DynamoEnergyStorage.State> implements EnergyStorage {
    private final MjBattery battery;
    private final LongSupplier ratio, limit, clock;
    private final Runnable changed;
    private final java.util.function.BooleanSupplier active;
    private long tick = Long.MIN_VALUE, used;
    public DynamoEnergyStorage(MjBattery battery, LongSupplier ratio, LongSupplier limit, LongSupplier clock, Runnable changed) {
        this(battery, ratio, limit, clock, changed, () -> true);
    }
    public DynamoEnergyStorage(MjBattery battery, LongSupplier ratio, LongSupplier limit, LongSupplier clock, Runnable changed,
            java.util.function.BooleanSupplier active) {
        this.battery = battery; this.ratio = ratio; this.limit = limit; this.clock = clock; this.changed = changed;
        this.active = active;
    }
    private long ratio() {
        long value = ratio.getAsLong();
        if (value <= 0) throw new IllegalStateException("Invalid MJ conversion ratio");
        return value;
    }
    private void refresh() { long now = clock.getAsLong(); if (now != tick) { tick = now; used = 0; } }
    public long usedThisTick() { refresh(); return used; }
    @Override public boolean supportsInsertion() { return false; }
    @Override public boolean supportsExtraction() { return active.getAsBoolean(); }
    @Override public long insert(long amount, TransactionContext transaction) {
        if (amount < 0) throw new IllegalArgumentException("Negative energy insertion");
        return 0;
    }
    @Override public long extract(long amount, TransactionContext transaction) {
        if (amount < 0) throw new IllegalArgumentException("Negative energy extraction");
        if (!active.getAsBoolean()) return 0;
        refresh();
        long ratio = ratio();
        long available = Math.min(battery.getStored(), Math.max(0, limit.getAsLong() - used));
        long extracted = Math.min(amount, available / ratio);
        if (extracted > 0) {
            updateSnapshots(transaction);
            long paid = extracted * ratio; // bounded by available, so cannot overflow
            battery.extractPower(paid);
            used += paid;
        }
        return extracted;
    }
    @Override public long getAmount() { return active.getAsBoolean() ? battery.getStored() / ratio() : 0; }
    @Override public long getCapacity() { return active.getAsBoolean() ? battery.getCapacity() / ratio() : 0; }
    @Override protected State createSnapshot() { return new State(battery.getStored(), tick, used); }
    @Override protected void readSnapshot(State state) {
        battery.extractAll(); battery.addPower(state.stored, false); tick = state.tick; used = state.used;
    }
    @Override protected void onFinalCommit() { changed.run(); }
    record State(long stored, long tick, long used) {}
}
