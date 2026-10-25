/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.builders.snapshot;

import java.util.List;
import java.util.function.BooleanSupplier;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.item.ItemStack;
import buildcraft.lib.compat.FluidStackBC;

/** Items and fluids share one reservation, including repeated requirements and split tanks/slots. */
public final class BlueprintMaterials {
    private BlueprintMaterials() {}

    public static boolean available(Storage<ItemVariant> items, Storage<FluidVariant> fluids,
            List<ItemStack> requiredItems, List<FluidStackBC> requiredFluids) {
        try (Transaction transaction = Transaction.openNested(Transaction.getCurrentUnsafe())) {
            return extract(items, fluids, requiredItems, requiredFluids, transaction);
        }
    }

    /** A placement callback must leave the world unchanged when returning false.
     * World mutation cannot nest inside somebody else's uncommitted storage transaction.
     */
    public static boolean place(Storage<ItemVariant> items, Storage<FluidVariant> fluids,
            List<ItemStack> requiredItems, List<FluidStackBC> requiredFluids, BooleanSupplier placement) {
        try (Transaction transaction = Transaction.openOuter()) {
            if (!extract(items, fluids, requiredItems, requiredFluids, transaction) || !placement.getAsBoolean()) return false;
            transaction.commit();
            return true;
        }
    }

    private static boolean extract(Storage<ItemVariant> items, Storage<FluidVariant> fluids,
            List<ItemStack> requiredItems, List<FluidStackBC> requiredFluids, Transaction transaction) {
        if (requiredItems != null) for (ItemStack stack : requiredItems) {
            if (!stack.isEmpty() && items.extract(ItemVariant.of(stack), stack.getCount(), transaction) != stack.getCount())
                return false;
        }
        if (requiredFluids != null) for (FluidStackBC stack : requiredFluids) {
            if (!stack.isEmpty() && fluids.extract(stack.getFluidVariant(), stack.amount, transaction) != stack.amount)
                return false;
        }
        return true;
    }
}
