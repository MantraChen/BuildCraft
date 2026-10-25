/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.item;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.text.Text;

import buildcraft.lib.compat.FluidStackBC;
import buildcraft.api.items.IItemFluidShard;
import buildcraft.lib.item.ItemBC_Neptune;

public class ItemFragileFluidContainer extends ItemBC_Neptune implements IItemFluidShard {
    /** Half a bucket, in exact Fabric droplets. Remake by FelixMantra. */
    public static final long MAX_FLUID_HELD = FluidConstants.BUCKET / 2;

    public ItemFragileFluidContainer(Settings settings, String id) {
        super(settings.maxCount(1), id);
    }

    public ItemStack stack(FluidVariant fluid, long amount) {
        if (fluid.isBlank() || amount <= 0 || amount > MAX_FLUID_HELD) throw new IllegalArgumentException("Invalid fluid shard amount");
        var stack = new ItemStack(this);
        stack.getOrCreateNbt().put("fluid", FluidStackBC.of(fluid, amount).toNbt());
        return stack;
    }

    public static FluidStackBC getFluid(ItemStack stack) {
        if (!(stack.getItem() instanceof ItemFragileFluidContainer)) return FluidStackBC.EMPTY;
        var fluid = FluidStackBC.fromNbt(stack.getSubNbt("fluid"));
        return fluid == null || fluid.isEmpty() ? FluidStackBC.EMPTY
            : FluidStackBC.of(fluid.getFluidVariant(), Math.min(MAX_FLUID_HELD, fluid.getAmount()));
    }

    @Override public Text getName(ItemStack stack) {
        var fluid = getFluid(stack);
        return fluid.isEmpty() ? Text.translatable("item.buildcraftcore.fragile_fluid_shard.empty")
            : Text.translatable("item.fragile_fluid_shard.name", FluidVariantAttributes.getName(fluid.getFluidVariant()));
    }

    @Override public void appendTooltip(ItemStack stack, @Nullable net.minecraft.world.World world,
            java.util.List<Text> tooltip, net.minecraft.client.item.TooltipContext context) {
        var fluid = getFluid(stack);
        if (!fluid.isEmpty()) {
            var amount = java.math.BigDecimal.valueOf(fluid.getAmount()).divide(java.math.BigDecimal.valueOf(81),
                3, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
            tooltip.add(Text.translatable("tooltip.buildcraftcore.fluid_shard.amount", amount));
        }
    }

    @Override
    public void addFluidDrops(DefaultedList<ItemStack> toDrop, @Nullable FluidStackBC fluid) {
        if (fluid == null || fluid.isEmpty()) return;
        long remaining = fluid.getAmount();
        while (remaining > 0) {
            long amount = Math.min(remaining, MAX_FLUID_HELD);
            toDrop.add(stack(fluid.getFluidVariant(), amount));
            remaining -= amount;
        }
    }

    /** Context-backed extraction keeps both the item replacement and receiver in one transaction. */
    public SingleSlotStorage<FluidVariant> storage(ContainerItemContext context) {
        return new SingleSlotStorage<>() {
            private FluidStackBC contents() { return getFluid(context.getItemVariant().toStack()); }
            @Override public boolean supportsInsertion() { return false; }
            @Override public boolean supportsExtraction() { return context.getItemVariant().isOf(ItemFragileFluidContainer.this); }
            @Override public long insert(FluidVariant resource, long max, TransactionContext tx) {
                StoragePreconditions.notBlankNotNegative(resource, max); return 0;
            }
            @Override public long extract(FluidVariant resource, long max, TransactionContext tx) {
                StoragePreconditions.notBlankNotNegative(resource, max);
                var current = context.getItemVariant();
                if (!current.isOf(ItemFragileFluidContainer.this)) return 0;
                var fluid = contents();
                if (fluid.isEmpty() || !fluid.getFluidVariant().equals(resource)) return 0;
                long removed = Math.min(max, fluid.getAmount());
                if (removed <= 0) return 0;
                try (var nested = tx.openNested()) {
                    if (context.extract(current, 1, nested) != 1) return 0;
                    if (removed < fluid.getAmount()) {
                        var replacement = current.toStack();
                        replacement.getOrCreateNbt().put("fluid", FluidStackBC.of(resource, fluid.getAmount() - removed).toNbt());
                        if (context.insert(ItemVariant.of(replacement), 1, nested) != 1) return 0;
                    }
                    nested.commit(); return removed;
                }
            }
            @Override public boolean isResourceBlank() { return contents().isEmpty(); }
            @Override public FluidVariant getResource() { return contents().getFluidVariant(); }
            @Override public long getAmount() { return contents().getAmount(); }
            @Override public long getCapacity() { return supportsExtraction() ? MAX_FLUID_HELD : 0; }
        };
    }
}
