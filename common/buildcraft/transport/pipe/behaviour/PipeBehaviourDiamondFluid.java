/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 *
 * Ported to Fabric 1.20.1 by R.Chen (https://github.com/MantraChen).
 */

package buildcraft.transport.pipe.behaviour;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.Direction;

import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.PipeEventFluid;
import buildcraft.api.transport.pipe.PipeEventHandler;

public class PipeBehaviourDiamondFluid extends PipeBehaviourDiamond {
    public PipeBehaviourDiamondFluid(IPipe pipe, NbtCompound nbt) {
        super(pipe, nbt);
    }

    public PipeBehaviourDiamondFluid(IPipe pipe) {
        super(pipe);
    }

    @PipeEventHandler
    public void sideCheck(PipeEventFluid.SideCheck sideCheck) {
        for (var side : Direction.values()) {
            if (!sideCheck.isAllowed(side)) continue;
            boolean filtered = false, matched = false;
            for (int slot = 0; slot < FILTERS_PER_SIDE; slot++) {
                for (var fluid : FluidPipeFilters.samples(filters.getStackInSlot(side.ordinal() * FILTERS_PER_SIDE + slot))) {
                    filtered = true;
                    matched |= fluid.equals(sideCheck.fluid);
                }
            }
            if (matched) sideCheck.increasePriority(side, 12);
            else if (filtered) sideCheck.disallow(side);
        }
    }
}
