package net.minecraftforge.fluids;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import buildcraft.lib.compat.BCFluidStorage;
import buildcraft.lib.compat.FluidStackBC;
import buildcraft.lib.fluid.FluidTankStorage;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;

/** Legacy mB method signatures backed by the same exact native storage used by Fabric pipes. */
public class FluidTank implements IFluidTank, IFluidHandler {
    protected final int capacity;
    protected BlockEntity tile;
    protected final FluidTankStorage storage;

    public FluidTank(int capacity) {
        if (capacity < 0) throw new IllegalArgumentException("Negative capacity");
        this.capacity = capacity;
        storage = new FluidTankStorage(BCFluidStorage.mBToDroplets(capacity), this::onContentsChanged);
    }
    public FluidTankStorage getStorage() { return storage; }
    @Override public FluidStackBC getFluid() {
        return storage.isResourceBlank() ? null : FluidStackBC.of(storage.getResource(), storage.getAmount());
    }
    @Override public int getFluidAmount() { return (int) BCFluidStorage.dropletsToMB(storage.getAmount()); }
    @Override public int getCapacity() { return capacity; }
    public void setCanFill(boolean allowed) { storage.setCanInsert(allowed); }
    public void setCanDrain(boolean allowed) { storage.setCanExtract(allowed); }
    @Override public boolean canFillFluidType(FluidStackBC fluid) {
        return fluid != null && storage.accepts(fluid.getFluidVariant());
    }
    /** Returns mB. Resource stacks always contain droplets, including those from legacy constructors. */
    @Override public int fill(FluidStackBC resource, boolean doFill) {
        if (resource == null || resource.isEmpty() || !canFillFluidType(resource)) return 0;
        long available = storage.getCapacity() - storage.getAmount();
        // This API can only report whole mB; never move an unreportable fractional remainder.
        long request = BCFluidStorage.mBToDroplets(BCFluidStorage.dropletsToMB(Math.min(resource.amount, available)));
        try (Transaction transaction = Transaction.openOuter()) {
            long moved = storage.insert(resource.getFluidVariant(), request, transaction);
            if (doFill) transaction.commit();
            return (int) BCFluidStorage.dropletsToMB(moved);
        }
    }
    /** maxDrain is mB; the returned stack records the corresponding droplet amount. */
    @Override public FluidStackBC drain(int maxDrain, boolean doDrain) {
        if (maxDrain <= 0 || storage.isResourceBlank()) return null;
        long request = BCFluidStorage.mBToDroplets(Math.min(maxDrain, getFluidAmount()));
        try (Transaction transaction = Transaction.openOuter()) {
            var variant = storage.getResource();
            long moved = storage.extract(variant, request, transaction);
            if (doDrain) transaction.commit();
            return moved == 0 ? null : FluidStackBC.of(variant, moved);
        }
    }
    @Override public FluidStackBC drain(FluidStackBC resource, boolean doDrain) {
        if (resource == null || !storage.getResource().equals(resource.getFluidVariant())) return null;
        return drain((int) Math.min(Integer.MAX_VALUE, resource.getAmountMB()), doDrain);
    }
    @Override public IFluidTankProperties[] getTankProperties() {
        return new IFluidTankProperties[] { new net.minecraftforge.fluids.capability.FluidTankProperties(
            getFluid(), capacity, storage.supportsInsertion(), storage.supportsExtraction()) };
    }
    protected void onContentsChanged() { if (tile != null) tile.markDirty(); }
    public NbtCompound writeToNBT(NbtCompound nbt) { return storage.writeNbt(nbt); }
    public FluidTank readFromNBT(NbtCompound nbt) { storage.readNbt(nbt); return this; }
}
