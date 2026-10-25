/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 *
 * Ported to Fabric 1.20.1 by R.Chen (https://github.com/MantraChen).
 */
package buildcraft.lib.tile.item;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;

/** Defines a way of inserting items into an inventory — overridable for custom stack-merge rules. */
@FunctionalInterface
public interface StackInsertionFunction {

    @Nonnull
    InsertionResult modifyForInsertion(int slot, @Nonnull ItemStack addingTo, @Nonnull ItemStack toInsert);

    static StackInsertionFunction getInsertionFunction(int maxStackSize) {
        if (maxStackSize < 0) throw new IllegalArgumentException("Negative slot capacity");
        return (slot, addingTo, toInsert) -> {
            if (toInsert.isEmpty()) {
                return new InsertionResult(addingTo, ItemStack.EMPTY);
            }
            if (!addingTo.isEmpty() && !ItemStack.canCombine(addingTo, toInsert)) {
                return new InsertionResult(addingTo, toInsert);
            }
            int capacity = Math.min(maxStackSize, toInsert.getMaxCount());
            int accepted = Math.min(toInsert.getCount(), Math.max(0, capacity - addingTo.getCount()));
            if (accepted == 0) return new InsertionResult(addingTo, toInsert);
            ItemStack complete = toInsert.copy();
            complete.setCount(addingTo.getCount() + accepted);
            ItemStack leftOver = toInsert.copy();
            leftOver.decrement(accepted);
            return new InsertionResult(complete, leftOver);
        };
    }

    static StackInsertionFunction getDefaultInserter() {
        return getInsertionFunction(Integer.MAX_VALUE);
    }

    class InsertionResult {
        public static final InsertionResult EMPTY_STACKS = new InsertionResult(ItemStack.EMPTY, ItemStack.EMPTY);

        @Nonnull
        public final ItemStack toSet, toReturn;

        public InsertionResult(@Nonnull ItemStack toSet, @Nonnull ItemStack toReturn) {
            this.toSet = toSet;
            this.toReturn = toReturn;
        }
    }
}
