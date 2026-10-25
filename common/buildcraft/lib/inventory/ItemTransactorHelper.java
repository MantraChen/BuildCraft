/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 *
 * Ported to Fabric 1.20.1 by R.Chen (https://github.com/MantraChen).
 */

package buildcraft.lib.inventory;

import javax.annotation.Nonnull;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.Direction;
import net.minecraft.util.collection.DefaultedList;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;

import buildcraft.api.inventory.IItemTransactor;
import buildcraft.api.transport.IInjectable;
import buildcraft.lib.compat.BCItemStorage;

/** Resolves sided Fabric storage for pipes and machines. */
public class ItemTransactorHelper {

    @Nonnull
    public static IItemTransactor getTransactor(BlockEntity provider, Direction face) {
        if (provider != null && !provider.isRemoved() && provider.getWorld() != null) {
            Storage<ItemVariant> storage = BCItemStorage.fromBlockEntity(provider.getWorld(), provider, face);
            if (storage != null) return new StorageItemTransactor(storage);
        }
        return NoSpaceTransactor.INSTANCE;
    }

    @Nonnull
    public static IInjectable getInjectable(BlockEntity provider, Direction face) {
        if (provider instanceof IInjectable injectable && !provider.isRemoved()) return injectable;
        return NoSpaceInjectable.INSTANCE;
    }

    public static IItemTransactor wrapInjectable(IInjectable injectable, Direction facing) {
        return new InjectableWrapper(injectable, facing);
    }

    public static DefaultedList<ItemStack> insertAllBypass(IItemTransactor transactor,
        DefaultedList<ItemStack> stacks, boolean simulate) {
        DefaultedList<ItemStack> remaining = DefaultedList.of();
        for (ItemStack stack : stacks) {
            ItemStack remainder = transactor.insert(stack, false, simulate);
            if (!remainder.isEmpty()) remaining.add(remainder);
        }
        return remaining;
    }

    /** Fallback when the block has no native pipe injection endpoint. */
    private enum NoSpaceInjectable implements IInjectable {
        INSTANCE;

        @Override
        public boolean canInjectItems(Direction from) {
            return false;
        }

        @Nonnull
        @Override
        public ItemStack injectItem(@Nonnull ItemStack stack, boolean doAdd, Direction from, DyeColor color,
            double speed) {
            return stack;
        }
    }

}
