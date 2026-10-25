/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.transport.pipe.flow;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.util.math.Direction;
import buildcraft.transport.tile.TilePipeHolder;

/** Insert-only native automation endpoint. Travelling items become visible only on final commit. */
public final class PipeItemStorage extends SnapshotParticipant<List<PipeItemStorage.Pending>> {
    record Pending(ItemVariant resource, int count, Direction face) {}
    private final TilePipeHolder holder;
    private final PipeFlowItems flow;
    private final List<Pending> pending = new ArrayList<>();
    private final List<Storage<ItemVariant>> faces = new ArrayList<>();

    public PipeItemStorage(TilePipeHolder holder, PipeFlowItems flow) {
        this.holder = holder;
        this.flow = flow;
        for (var face : Direction.values()) faces.add(new Face(face));
    }
    public Storage<ItemVariant> side(Direction face) { return face == null ? null : faces.get(face.ordinal()); }
    @Override protected List<Pending> createSnapshot() { return List.copyOf(pending); }
    @Override protected void readSnapshot(List<Pending> snapshot) { pending.clear(); pending.addAll(snapshot); }
    @Override protected void onFinalCommit() {
        var committed = List.copyOf(pending);
        pending.clear();
        for (var item : committed) flow.commitAutomationInsert(item.resource.toStack(item.count), item.face);
        holder.markChunkDirty();
    }

    private final class Face implements Storage<ItemVariant> {
        private final Direction face;
        Face(Direction face) { this.face = face; }
        @Override public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, maxAmount);
            if (maxAmount == 0 || holder.isRemoved() || holder.getWorld() == null || holder.getWorld().isClient
                    || holder.getPipe() == null || holder.getPipe().flow != flow) return 0;
            var plug = holder.getPluggable(face);
            if (plug != null && plug.isBlocking()) return 0;
            int offered = (int) Math.min(maxAmount, resource.getItem().getMaxCount());
            int accepted = offered - flow.injectItem(resource.toStack(offered), false, face, null, 0.08).getCount();
            if (accepted <= 0) return 0;
            updateSnapshots(transaction);
            pending.add(new Pending(resource, accepted, face));
            return accepted;
        }
        @Override public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, maxAmount);
            return 0;
        }
        @Override public boolean supportsExtraction() { return false; }
        @Override public Iterator<StorageView<ItemVariant>> iterator() { return java.util.Collections.emptyIterator(); }
    }
}
