/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.lib.mj;

import buildcraft.api.mj.*;
import buildcraft.lib.tile.TileBC_Neptune;
import net.fabricmc.fabric.api.transfer.v1.transaction.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.nbt.NbtCompound;

/** Capacity-bounded input; refunds already owned by work may exceed that capacity. */
public final class BoundedTileMjReceiver extends SnapshotParticipant<NbtCompound>
        implements IMjReceiver, IMjReadable, TransactionalMjReceiver {
    private final TileBC_Neptune tile;
    private final MjBattery battery;
    public BoundedTileMjReceiver(TileBC_Neptune tile, MjBattery battery) { this.tile=tile; this.battery=battery; }
    private boolean live() { return tile.isStorageAvailable() && !tile.getWorld().isClient; }
    @Override public boolean canConnect(IMjConnector other) { return live(); }
    @Override public long getStored() { return battery.getStored(); }
    @Override public long getCapacity() { return battery.getCapacity(); }
    @Override public long getPowerRequested() { return live()?Math.max(0,getCapacity()-getStored()):0; }
    @Override public long receivePower(long power,boolean simulate) {
        if (power<=0 || !live()) return power;
        long accepted=Math.min(power,getPowerRequested());
        if (!simulate && accepted>0) {
            var transaction=Transaction.getCurrentUnsafe();
            if (transaction!=null) updateSnapshots(transaction);
            battery.addPower(accepted,false);
            if (transaction==null) tile.markDirty();
        }
        return power-accepted;
    }
    @Override public long receivePower(long power,TransactionContext transaction) {
        if (power<=0 || !live()) return power;
        long accepted=Math.min(power,getPowerRequested());
        if (accepted>0) { updateSnapshots(transaction); battery.addPower(accepted,false); }
        return power-accepted;
    }
    @Override protected NbtCompound createSnapshot() { return battery.writeToNbt(); }
    @Override protected void readSnapshot(NbtCompound value) { battery.readFromNbt(value); }
    @Override protected void onFinalCommit() { tile.markDirty(); }
}
