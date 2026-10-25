/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.transport.pipe.behaviour;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.item.ItemStack;

/** Read ghost samples through the native item provider without consuming the sample container. */
final class FluidPipeFilters {
    private FluidPipeFilters() {}
    static List<FluidVariant> samples(ItemStack stack) {
        if (stack.isEmpty()) return List.of();
        var storage = ContainerItemContext.withConstant(stack.copy()).find(FluidStorage.ITEM);
        if (storage == null) return List.of();
        var fluids = new ArrayList<FluidVariant>();
        for (var view : storage.nonEmptyViews()) fluids.add(view.getResource());
        return fluids;
    }
}
