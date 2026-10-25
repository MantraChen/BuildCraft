/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.energy;

import buildcraft.api.fuels.*;
import buildcraft.api.mj.MjAPI;
import buildcraft.lib.compat.FluidStackBC;
import buildcraft.lib.fluid.BCFluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.*;

/** Original BuildCraft fuel yields. Only cold fluids burn; hot fluids must be cooled first. */
public final class BCEnergyFuels {
    private BCEnergyFuels() {}
    public static void registerDefaults(IFuelManager fuels, ICoolantManager coolants) {
        coolants.addCoolant(Fluids.WATER, .0023f);
        coolants.addSolidCoolant(new ItemStack(Items.ICE), new FluidStackBC(Fluids.WATER, 1000), 1.5f);
        coolants.addSolidCoolant(new ItemStack(Items.PACKED_ICE), new FluidStackBC(Fluids.WATER, 1000), 2f);
        fuel(fuels, BCEnergyFluids.fuelGaseous, 16, 8, 4, false);
        fuel(fuels, BCEnergyFluids.fuelLight, 4, 6, 6, false);
        fuel(fuels, BCEnergyFluids.fuelDense, 2, 4, 12, false);
        fuel(fuels, BCEnergyFluids.fuelMixedLight, 10, 3, 5, false);
        fuel(fuels, BCEnergyFluids.fuelMixedHeavy, 5, 5, 8, false);
        fuel(fuels, BCEnergyFluids.oilDense, 2, 4, 4, true);
        fuel(fuels, BCEnergyFluids.oilDistilled, 8, 1, 5, false);
        fuel(fuels, BCEnergyFluids.oilHeavy, 3, 2, 4, true);
        fuel(fuels, BCEnergyFluids.crudeOil, 8, 3, 4, true);
    }
    private static void fuel(IFuelManager registry, BCFluid[] fluids, int ratio, int multiplier, int boost, boolean dirty) {
        if (fluids == null || fluids.length == 0 || fluids[0] == null) return;
        int ticks = 240_000 * boost / 4 / multiplier / ratio;
        if (dirty && BCEnergyFluids.oilResidue != null && BCEnergyFluids.oilResidue[0] != null) {
            registry.addDirtyFuel(fluids[0], multiplier * MjAPI.MJ, ticks, new FluidStackBC(BCEnergyFluids.oilResidue[0], 1000 / ratio));
        } else registry.addFuel(fluids[0], multiplier * MjAPI.MJ, ticks);
    }
}
