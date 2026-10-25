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
        initialized = true;
        // Refinery/heat-exchange recipes are migrated with their machine simulation.
    }
}
