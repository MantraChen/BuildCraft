/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.inventory.filter;

import buildcraft.lib.compat.FluidStackBC;

import buildcraft.api.core.IFluidFilter;

public class InvertedFluidFilter implements IFluidFilter {

    public final IFluidFilter delegate;

    public InvertedFluidFilter(IFluidFilter delegate) {
        this.delegate = delegate;
    }

    public boolean matches(FluidStackBC fluid) {
        return matches(fluid.getFluidVariant(), fluid.getAmount());
    }
    @Override
    public boolean matches(net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant fluid, long amount) {
        return !delegate.matches(fluid, amount);
    }
}
