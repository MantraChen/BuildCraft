/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.core.statements;

import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.item.*;
import net.fabricmc.fabric.api.transfer.v1.storage.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Direction;

import buildcraft.lib.misc.StackUtil;
import buildcraft.api.statements.IStatementParameter;

/** Side-aware, non-consuming queries for gate conditions. Remake by FelixMantra. */
final class StorageTriggerUtil {
    private StorageTriggerUtil() { }

    static Storage<ItemVariant> items(BlockEntity tile, Direction side) {
        return tile == null || tile.isRemoved() || tile.getWorld() == null ? null
            : ItemStorage.SIDED.find(tile.getWorld(), tile.getPos(), tile.getCachedState(), tile, side.getOpposite());
    }

    static Storage<FluidVariant> fluids(BlockEntity tile, Direction side) {
        return tile == null || tile.isRemoved() || tile.getWorld() == null ? null
            : FluidStorage.SIDED.find(tile.getWorld(), tile.getPos(), tile.getCachedState(), tile, side.getOpposite());
    }

    static boolean hasViews(Storage<?> storage) {
        if (storage != null) for (var view : storage) if (view.getCapacity() > 0) return true;
        return false;
    }

    static ItemStack sample(IStatementParameter[] parameters) {
        return parameters == null || parameters.length == 0 || parameters[0] == null
            ? ItemStack.EMPTY : parameters[0].getItemStack();
    }

    static FluidVariant fluidSample(IStatementParameter[] parameters) {
        var storage = ContainerItemContext.withConstant(sample(parameters)).find(FluidStorage.ITEM);
        return StorageUtil.findStoredResource(storage);
    }

    static boolean matches(ItemStack sample, ItemVariant resource) {
        return !resource.isBlank() && (sample.isEmpty() || StackUtil.matchesStackOrList(sample, resource.toStack()));
    }

    static <T> boolean hasSpace(Storage<T> storage, T sample) {
        if (sample != null) return StorageUtil.simulateInsert(storage, sample, 1, null) > 0;
        // Without a sample the Transfer API can only expose estimated empty-slot capacity.
        for (var view : storage) if (view.getAmount() < view.getCapacity()) return true;
        return false;
    }
}
