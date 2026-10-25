/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 *
 * Ported to Fabric 1.20.1 by R.Chen (https://github.com/MantraChen).
 */
package buildcraft.lib.tile.item;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.ArrayList;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.base.CombinedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.collection.DefaultedList;

import buildcraft.api.core.IStackFilter;

import buildcraft.lib.tile.item.StackInsertionFunction.InsertionResult;

// TODO(R.Chen): Forge AbstractInvItemTransactor (IItemTransactor) dropped — re-add Transfer-API batch
// insert/extract when the full item-handler layer is migrated.
public class ItemHandlerSimple implements IItemHandlerAdv {

    private StackInsertionChecker checker;
    private StackInsertionFunction inserter;

    @Nullable
    private StackChangeCallback callback;

    public final DefaultedList<ItemStack> stacks;

    private int firstUsed = Integer.MAX_VALUE;
    private int slotLimit = 64;
    private SlotState[] transferSlots;

    public ItemHandlerSimple(int size) {
        this(size, (slot, stack) -> true, StackInsertionFunction.getDefaultInserter(), null);
    }

    public ItemHandlerSimple(int size, int maxStackSize) {
        this(size);
        setLimitedInsertor(maxStackSize);
    }

    public ItemHandlerSimple(int size, @Nullable StackChangeCallback callback) {
        this(size, (slot, stack) -> true, StackInsertionFunction.getDefaultInserter(), callback);
    }

    public ItemHandlerSimple(int size, StackInsertionChecker checker, StackInsertionFunction insertionFunction,
        @Nullable StackChangeCallback callback) {
        stacks = DefaultedList.ofSize(size, ItemStack.EMPTY);
        this.checker = checker;
        this.inserter = insertionFunction;
        this.callback = callback;
    }

    public void setChecker(StackInsertionChecker checker) {
        this.checker = checker;
    }

    public void setInsertor(StackInsertionFunction insertor) {
        this.inserter = insertor;
    }

    public void setLimitedInsertor(int maxStackSize) {
        setInsertor(StackInsertionFunction.getInsertionFunction(maxStackSize));
        slotLimit = Math.min(64, maxStackSize);
    }

    public void setCallback(StackChangeCallback callback) {
        this.callback = callback;
    }

    public NbtCompound createNbt() { return serializeNBT(); }
    public NbtCompound serializeNBT() {
        NbtCompound nbt = new NbtCompound();
        NbtList list = new NbtList();
        for (ItemStack stack : stacks) {
            NbtCompound itemNbt = new NbtCompound();
            stack.writeNbt(itemNbt);
            list.add(itemNbt);
        }
        nbt.put("items", list);
        return nbt;
    }

    public void deserializeNBT(NbtCompound nbt) {
        NbtList list = nbt.getList("items", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size() && i < getSlots(); i++) {
            setStackInternal(i, ItemStack.fromNbt(list.getCompound(i)));
        }
        for (int i = list.size(); i < getSlots(); i++) {
            setStackInternal(i, ItemStack.EMPTY);
        }
    }

    @Override
    public int getSlots() {
        return stacks.size();
    }

    private boolean badSlotIndex(int slot) {
        return slot < 0 || slot >= stacks.size();
    }

    @Override
    @Nonnull
    public ItemStack getStackInSlot(int slot) {
        if (badSlotIndex(slot)) return ItemStack.EMPTY;
        return stacks.get(slot);
    }

    @Override
    @Nonnull
    public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
        if (badSlotIndex(slot)) return stack;
        if (canSet(slot, stack)) {
            ItemStack current = stacks.get(slot);
            if (!canSet(slot, current)) {
                return stack;
            }
            InsertionResult result = inserter.modifyForInsertion(slot, current.copy(), stack.copy());
            if (!canSet(slot, result.toSet)) {
                // TODO(R.Chen): replace with Yarn CrashReport when crash API is verified.
                throw new IllegalStateException(
                    "Conflicting Insertion! checker=" + checker.getClass() + " inserter=" + inserter.getClass()
                        + " slot=" + slot + " existing=" + current + " inserting=" + stack
                        + " toSet=" + result.toSet + " toReturn=" + result.toReturn);
            } else if (!simulate) {
                setStackInternal(slot, result.toSet);
                if (callback != null) {
                    callback.onStackChange(this, slot, current, result.toSet);
                }
            }
            return result.toReturn;
        } else {
            return stack;
        }
    }

    @Override
    @Nonnull
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (badSlotIndex(slot) || amount <= 0) return ItemStack.EMPTY;
        ItemStack current = stacks.get(slot);
        if (current.isEmpty()) return ItemStack.EMPTY;
        if (current.getCount() < amount) {
            if (simulate) return current.copy();
            setStackInternal(slot, ItemStack.EMPTY);
            if (callback != null) callback.onStackChange(this, slot, current, ItemStack.EMPTY);
            return current;
        } else {
            ItemStack before = current;
            current = current.copy();
            ItemStack split = current.split(amount);
            if (!simulate) {
                if (current.getCount() <= 0) current = ItemStack.EMPTY;
                setStackInternal(slot, current);
                if (callback != null) callback.onStackChange(this, slot, before, current);
            }
            return split;
        }
    }

    // TODO(R.Chen): IStackFilter-based extract — migrate when Transfer-API item layer lands.
    @Nonnull
    protected ItemStack extract(int slot, IStackFilter filter, int min, int max, boolean simulate) {
        if (badSlotIndex(slot)) return ItemStack.EMPTY;
        if (min <= 0) min = 1;
        if (max < min) return ItemStack.EMPTY;
        ItemStack current = stacks.get(slot);
        ItemStack before = current.copy();
        if (current.getCount() < min) return ItemStack.EMPTY;
        if (filter.matches(current)) {
            if (simulate) {
                return current.copy().split(max);
            }
            ItemStack split = current.split(max);
            if (current.getCount() <= 0) {
                stacks.set(slot, ItemStack.EMPTY);
            }
            if (callback != null) callback.onStackChange(this, slot, before, stacks.get(slot));
            return split;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void setStackInSlot(int slot, @Nonnull ItemStack stack) {
        if (badSlotIndex(slot)) throw new IndexOutOfBoundsException("Slot index out of range: " + slot);
        ItemStack before = stacks.get(slot);
        setStackInternal(slot, stack);
        if (callback != null) callback.onStackChange(this, slot, before, stack);
    }

    @Override
    public final boolean canSet(int slot, @Nonnull ItemStack stack) {
        if (stack.isEmpty()) return true;
        return checker.canSet(slot, stack);
    }

    private void setStackInternal(int slot, @Nonnull ItemStack stack) {
        stacks.set(slot, stack.isEmpty() ? ItemStack.EMPTY : stack);
        if (stack.isEmpty() && firstUsed == slot) {
            for (int s = firstUsed; s < getSlots(); s++) {
                if (!stacks.get(s).isEmpty()) {
                    firstUsed = s;
                    break;
                }
            }
            if (firstUsed == slot) firstUsed = Integer.MAX_VALUE;
        } else if (!stack.isEmpty() && firstUsed > slot) {
            firstUsed = slot;
        }
    }

    @Override
    public int getSlotLimit(int slot) {
        return slotLimit;
    }

    /** All side views share the same snapshots, so nested transfers can roll back across faces. */
    public Storage<ItemVariant> asStorage(boolean insert, boolean extract) {
        if (transferSlots == null) {
            transferSlots = new SlotState[getSlots()];
            for (int slot = 0; slot < getSlots(); slot++) transferSlots[slot] = new SlotState(slot);
        }
        List<SingleSlotStorage<ItemVariant>> views = new ArrayList<>();
        for (SlotState slot : transferSlots) views.add(new TransferSlot(slot, insert, extract));
        return new CombinedStorage<>(views);
    }

    private final class SlotState extends SnapshotParticipant<ItemStack> {
        final int slot;
        TransactionContext pendingRoot;

        SlotState(int slot) { this.slot = slot; }

        @Override protected ItemStack createSnapshot() { return stacks.get(slot).copy(); }
        @Override protected void readSnapshot(ItemStack snapshot) { setStackInternal(slot, snapshot); }

        void change(ItemStack stack, TransactionContext transaction) {
            updateSnapshots(transaction);
            TransactionContext root = transaction.getOpenTransaction(0);
            if (pendingRoot != root) {
                pendingRoot = root;
                ItemStack before = stacks.get(slot).copy();
                transaction.addOuterCloseCallback(result -> {
                    pendingRoot = null;
                    ItemStack after = stacks.get(slot);
                    if (result.wasCommitted() && callback != null && !ItemStack.areEqual(before, after)) {
                        callback.onStackChange(ItemHandlerSimple.this, slot, before, after);
                    }
                });
            }
            setStackInternal(slot, stack);
        }
    }

    private final class TransferSlot implements SingleSlotStorage<ItemVariant> {
        final SlotState state;
        final boolean insert, extract;

        TransferSlot(SlotState state, boolean insert, boolean extract) {
            this.state = state;
            this.insert = insert;
            this.extract = extract;
        }

        @Override public boolean supportsInsertion() { return insert; }
        @Override public boolean supportsExtraction() { return extract; }

        @Override
        public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, maxAmount);
            if (!insert || maxAmount == 0) return 0;
            ItemStack input = resource.toStack((int) Math.min(maxAmount, resource.getItem().getMaxCount()));
            ItemStack current = stacks.get(state.slot);
            if (!canSet(state.slot, input) || !canSet(state.slot, current)) return 0;
            InsertionResult result = inserter.modifyForInsertion(state.slot, current.copy(), input.copy());
            if (!canSet(state.slot, result.toSet)) throw new IllegalStateException("Conflicting insertion rules");
            long accepted = input.getCount() - result.toReturn.getCount();
            if (accepted > 0) state.change(result.toSet, transaction);
            return accepted;
        }

        @Override
        public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, maxAmount);
            if (!extract || maxAmount == 0 || !resource.equals(getResource())) return 0;
            ItemStack current = stacks.get(state.slot);
            int taken = (int) Math.min(maxAmount, current.getCount());
            if (taken > 0) {
                ItemStack remaining = current.copy();
                remaining.decrement(taken);
                state.change(remaining, transaction);
            }
            return taken;
        }

        @Override public boolean isResourceBlank() { return stacks.get(state.slot).isEmpty(); }
        @Override public ItemVariant getResource() { return ItemVariant.of(stacks.get(state.slot)); }
        @Override public long getAmount() { return stacks.get(state.slot).getCount(); }
        @Override public long getCapacity() {
            ItemStack stack = stacks.get(state.slot);
            return Math.min(getSlotLimit(state.slot), stack.isEmpty() ? 64 : stack.getMaxCount());
        }
    }

    @Override
    public String toString() {
        return "ItemHandlerSimple " + stacks;
    }
}
