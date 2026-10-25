/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.lib.fluid;

import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.storage.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;

/** Atomic fluid/container exchanges, including stacked buckets and inventory overflow. */
public final class FluidTransfers {
    private FluidTransfers() {}
    public record Exchange(FluidVariant variant, long amount, boolean filledItem) {}

    public static Exchange exchange(Storage<FluidVariant> tank, ContainerItemContext context,
            TransactionContext transaction) {
        Storage<FluidVariant> item = context.find(FluidStorage.ITEM);
        if (item == null) return null;
        Exchange result = moveOne(item, tank, false, transaction);
        return result != null ? result : moveOne(tank, item, true, transaction);
    }

    private static Exchange moveOne(Storage<FluidVariant> from, Storage<FluidVariant> to,
            boolean filledItem, TransactionContext transaction) {
        for (StorageView<FluidVariant> view : from) {
            if (view.isResourceBlank()) continue;
            FluidVariant variant = view.getResource();
            long available;
            try (Transaction probe = transaction.openNested()) {
                available = view.extract(variant, Long.MAX_VALUE, probe);
            }
            try (Transaction transfer = transaction.openNested()) {
                long moved = to.insert(variant, available, transfer);
                if (moved > 0 && view.extract(variant, moved, transfer) == moved) {
                    transfer.commit();
                    return new Exchange(variant, moved, filledItem);
                }
            }
        }
        return null;
    }

    public static boolean interact(Storage<FluidVariant> tank, ContainerItemContext context, PlayerEntity player) {
        if (player.getWorld().isClient) return false;
        Exchange result;
        try (Transaction transaction = Transaction.openOuter()) {
            result = exchange(tank, context, transaction);
            if (result == null) return false;
            transaction.commit();
        }
        player.playSound(result.filledItem ? FluidVariantAttributes.getFillSound(result.variant)
            : FluidVariantAttributes.getEmptySound(result.variant), SoundCategory.BLOCKS, 1, 1);
        return true;
    }
}
