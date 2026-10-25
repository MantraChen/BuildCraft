/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package buildcraft.lib.fluid;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.Formatting;

import net.minecraft.fluid.Fluid;
import buildcraft.lib.compat.FluidStackBC;
import net.minecraftforge.fluids.FluidTank;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import buildcraft.api.core.IFluidFilter;
import buildcraft.api.core.IFluidHandlerAdv;

import buildcraft.lib.gui.ContainerBC_Neptune;
import buildcraft.lib.gui.elem.ToolTip;
import buildcraft.lib.gui.help.ElementHelpInfo;
import buildcraft.lib.misc.LocaleUtil;
import buildcraft.lib.net.PacketBufferBC;
import buildcraft.lib.compat.FluidRegistryBC;
import buildcraft.lib.net.cache.BuildCraftObjectCaches;
import buildcraft.lib.net.cache.NetworkedFluidStackCache;
import buildcraft.lib.tile.TileBC_Neptune;

/** Provides a useful implementation of a fluid tank that can save + load, and has a few helper functions. Can
 * optionally specify a filter to only allow a limited types of fluids in the tank. */
public class Tank extends FluidTank implements IFluidHandlerAdv {
    public static final String DEFAULT_HELP_KEY = "buildcraft.help.tank.generic";

    public int colorRenderCache = 0xFFFFFF;

    protected final ToolTip toolTip = new ToolTip() {
        @Override
        public void refresh() {
            refreshTooltip();
        }
    };

    @Nonnull
    private final String name;

    @Nonnull
    private Predicate<FluidStackBC> filter;

    NetworkedFluidStackCache.Link clientFluid = null;
    long clientAmount = 0;

    public ElementHelpInfo helpInfo;

    protected static Map<Fluid, Integer> fluidColors = new HashMap<>();

    /** Creates a tank with the given name and capacity (in milli buckets) with no filter set (so any fluid can go into
     * the tank) */
    public Tank(@Nonnull String name, int capacity, BlockEntity tile) {
        this(name, capacity, tile, null);
    }

    /** Creates a tank with the given name and capacity (in milli buckets) with the specified filter set. If the filter
     * returns true for a given fluidstack then it will be allowed in the tank. The given fluidstack will NEVER be
     * null. */
    public Tank(@Nonnull String name, int capacity, BlockEntity tile, @Nullable Predicate<FluidStackBC> filter) {
        super(capacity);
        this.name = name;
        this.tile = tile;
        this.filter = filter == null ? ((f) -> true) : filter;
        storage.setFilter(variant -> this.filter.test(FluidStackBC.of(variant, 1)));
        helpInfo = new ElementHelpInfo("buildcraft.help.tank.title." + name, 0xFF_00_00_00 | name.hashCode(),
            DEFAULT_HELP_KEY);
    }

    public void setFilter(Predicate<FluidStackBC> filter) {
        if (filter == null) {
            throw new NullPointerException("filter");
        }
        this.filter = filter;
    }

    @Nonnull
    public String getTankName() {
        return name;
    }

    public boolean isEmpty() {
        FluidStackBC fluidStack = getFluid();
        return fluidStack == null || fluidStack.amount <= 0;
    }

    public boolean isFull() {
        FluidStackBC fluidStack = getFluid();
        return fluidStack != null && fluidStack.amount >= storage.getCapacity();
    }

    public Fluid getFluidType() {
        FluidStackBC fluidStack = getFluid();
        return fluidStack != null ? fluidStack.getFluid() : null;
    }

    public NbtCompound createNbt() { return serializeNBT(); }
    public NbtCompound serializeNBT() {
        return writeToNBT(new NbtCompound());
    }

    // @Override -- removed: method does not exist in Fabric 1.20.1
    public final NbtCompound writeToNBT(NbtCompound nbt) {
        super.writeToNBT(nbt);
        writeTankToNBT(nbt);
        return nbt;
    }

    // @Override -- removed: method does not exist in Fabric 1.20.1
    public final FluidTank readFromNBT(NbtCompound nbt) {
        if (nbt.contains(name)) {
            // Old style of saving + loading
            NbtCompound tankData = nbt.getCompound(name);
            super.readFromNBT(tankData);
            readTankFromNBT(tankData);
        } else {
            super.readFromNBT(nbt);
            readTankFromNBT(nbt);
        }
        return this;
    }

    /** Writes some additional information to the nbt, for example {@link SingleUseTank} will write out the filtering
     * fluid. */
    protected void writeTankToNBT(NbtCompound nbt) {}

    /** Reads some additional information to the nbt, for example {@link SingleUseTank} will read in the filtering
     * fluid. */
    protected void readTankFromNBT(NbtCompound nbt) {}

    public ToolTip getToolTip() {
        return toolTip;
    }

    protected void refreshTooltip() {
        toolTip.clear();
        FluidStackBC fluidStack = getFluidForRender();
        if (fluidStack != null && !fluidStack.isEmpty()) toolTip.add(fluidStack.getLocalizedName());
        toolTip.add(Formatting.GRAY + LocaleUtil.localizeFluidStaticAmount(
            buildcraft.lib.compat.BCFluidStorage.dropletsToMB(clientAmount), getCapacity()));
    }

    @Override
    public boolean canFillFluidType(FluidStackBC fluid) {
        return super.canFillFluidType(fluid) && fluid != null && filter.test(fluid);
    }

    @Override
    public int fill(FluidStackBC resource, boolean doFill) {
        if (canFillFluidType(resource)) {
            return super.fill(resource, doFill);
        }
        return 0;
    }

    @Override
    public long drain(IFluidFilter drainFilter, long maxDrain, boolean doDrain) {
        if (drainFilter == null || maxDrain <= 0 || storage.isResourceBlank()
            || !drainFilter.matches(storage.getResource(), maxDrain)) return 0;
        try (var transaction = net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()) {
            long drained = storage.extract(storage.getResource(), maxDrain, transaction);
            if (doDrain) transaction.commit();
            return drained;
        }
    }

    // @Override -- removed: method does not exist in Fabric 1.20.1
    protected void onContentsChanged() {
        super.onContentsChanged();
        if (tile instanceof TileBC_Neptune) {
            ((TileBC_Neptune) tile).markChunkDirty();
        }
    }

    @Override
    public String toString() {
        return "Tank [" + getContentsString() + "]";
    }

    public String getContentsString() {
        FluidStackBC fluid = getFluid();
        if (fluid != null) {
            return fluid.getLocalizedName() + LocaleUtil.localizeFluidStaticAmount(this);
        }
        return LocaleUtil.localizeFluidStaticAmount(0, getCapacity());
    }

    public void writeToBuffer(PacketBufferBC buffer) {
        FluidStackBC fluid = getFluid();
        if (fluid == null) {
            buffer.writeBoolean(false);
        } else {
            buffer.writeBoolean(true);
            buffer.writeInt(BuildCraftObjectCaches.CACHE_FLUIDS.server().store(fluid));
        }
        buffer.writeVarLong(storage.getAmount());
    }

    @Environment(EnvType.CLIENT)
    public void readFromBuffer(PacketBufferBC buffer) {
        if (buffer.readBoolean()) {
            clientFluid = BuildCraftObjectCaches.CACHE_FLUIDS.client().retrieve(buffer.readInt());
        } else {
            clientFluid = null;
        }
        clientAmount = buffer.readVarLong();
        if (clientAmount < 0 || clientAmount > storage.getCapacity()) throw new IllegalArgumentException("Invalid tank amount");
        if (clientFluid == null) clientAmount = 0;
    }

    public FluidStackBC getFluidForRender() {
        if (clientFluid == null) {
            return null;
        } else {
            FluidStackBC stackBase = clientFluid.get();
            return FluidStackBC.of(stackBase.getFluidVariant(), clientAmount);
        }
    }

    public int getClientAmount() {
        return (int) buildcraft.lib.compat.BCFluidStorage.dropletsToMB(clientAmount);
    }

    public String getDebugString() {
        FluidStackBC f = getFluidForRender();
        if (f == null) f = getFluid();
        return (f == null ? 0 : f.getAmountMB()) + " / " + capacity + " mB of " + (f != null ? FluidRegistryBC.getFluidName(f.getFluid()) : "n/a");
    }

    public void onGuiClicked(ContainerBC_Neptune container) {
        PlayerEntity player = container.player;
        var context = player.isCreative()
            ? net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext.forCreativeInteraction(player, container.getCursorStack())
            : net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext.ofPlayerCursor(player, container);
        if (FluidTransfers.interact(this, context, player)) container.sendContentUpdates();
    }

    public boolean interactInventorySlot(ContainerBC_Neptune container, net.minecraft.screen.slot.Slot slot) {
        PlayerEntity player = container.player;
        var slotStorage = net.fabricmc.fabric.api.transfer.v1.item.InventoryStorage.of(slot.inventory, null).getSlot(slot.getIndex());
        var context = player.isCreative()
            ? net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext.forCreativeInteraction(player, slot.getStack())
            : net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext.ofPlayerSlot(player, slotStorage);
        return FluidTransfers.interact(this, context, player);
    }

    @Override public java.util.Iterator<net.fabricmc.fabric.api.transfer.v1.storage.StorageView<net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant>> iterator() {
        return storage.iterator();
    }
    @Override public long extract(net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant resource, long maxAmount,
            net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext transaction) {
        return storage.extract(resource, maxAmount, transaction);
    }
    @Override public long insert(net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant resource, long maxAmount,
            net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext transaction) {
        return storage.insert(resource, maxAmount, transaction);
    }
    @Override public boolean supportsInsertion() { return storage.supportsInsertion(); }
    @Override public boolean supportsExtraction() { return storage.supportsExtraction(); }
}
