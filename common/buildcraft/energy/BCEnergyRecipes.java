/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.energy;

public class BCEnergyRecipes {
    private static boolean initialized;
    public static void init() {
        if (initialized) return;
        BCEnergyFuels.registerDefaults(buildcraft.api.fuels.BuildcraftFuelRegistry.fuel,
            buildcraft.api.fuels.BuildcraftFuelRegistry.coolant);
        registerDistillation();
        initialized = true;
        // Heat exchange is restored with its separate machine simulation.
    }

    /** Original BuildCraft ratios, in mB; FluidStackBC converts to exact native droplets. */
    private static void registerDistillation() {
        var oil = BCEnergyFluids.crudeOil;
        var distilled = BCEnergyFluids.oilDistilled;
        var mixedLight = BCEnergyFluids.fuelMixedLight;
        var heavy = BCEnergyFluids.oilHeavy;
        var mixedHeavy = BCEnergyFluids.fuelMixedHeavy;
        var denseOil = BCEnergyFluids.oilDense;
        var gas = BCEnergyFluids.fuelGaseous;
        var light = BCEnergyFluids.fuelLight;
        var dense = BCEnergyFluids.fuelDense;
        var residue = BCEnergyFluids.oilResidue;
        add(oil, 8, gas, 16, heavy, 3, 0, 32);
        add(oil, 8, mixedLight, 10, denseOil, 2, 1, 16);
        add(oil, 8, distilled, 8, residue, 1, 2, 12);
        add(distilled, 8, gas, 16, mixedHeavy, 5, 0, 24);
        add(distilled, 8, mixedLight, 10, dense, 2, 1, 16);
        add(mixedLight, 10, gas, 16, light, 4, 0, 24);
        add(heavy, 3, light, 4, denseOil, 2, 1, 16);
        add(heavy, 3, mixedHeavy, 5, residue, 1, 2, 12);
        add(mixedHeavy, 5, light, 4, dense, 2, 1, 16);
        add(denseOil, 2, dense, 2, residue, 1, 2, 12);
    }
    private static void add(net.minecraft.fluid.Fluid[] in, int input, net.minecraft.fluid.Fluid[] gas, int gasAmount,
            net.minecraft.fluid.Fluid[] liquid, int liquidAmount, int heat, int mj) {
        int divisor = gcd(gcd(input, gasAmount), liquidAmount);
        buildcraft.api.recipes.BuildcraftRecipeRegistry.refineryRecipes.addDistillationRecipe(
            new buildcraft.lib.compat.FluidStackBC(in[heat], input / divisor),
            new buildcraft.lib.compat.FluidStackBC(gas[heat], gasAmount / divisor),
            new buildcraft.lib.compat.FluidStackBC(liquid[heat], liquidAmount / divisor),
            mj * buildcraft.api.mj.MjAPI.MJ / divisor);
    }
    private static int gcd(int a, int b) { return b == 0 ? a : gcd(b, a % b); }
}
