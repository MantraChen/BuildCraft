/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.energy;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.registry.*;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.feature.PlacedFeature;
import buildcraft.energy.generation.OilGenerator;

public final class BCEnergyWorldGen {
    public static final Identifier OIL = new Identifier("buildcraftenergy","oil");
    public static final RegistryKey<PlacedFeature> OIL_PLACED = RegistryKey.of(RegistryKeys.PLACED_FEATURE,OIL);
    private static boolean initialized;
    public static void init() {
        if (initialized) return;
        Registry.register(Registries.FEATURE,OIL,OilGenerator.INSTANCE);
        // Runtime config handles dimensions/biomes, including custom dimensions and their native names.
        BiomeModifications.addFeature(BiomeSelectors.all(),GenerationStep.Feature.LAKES,OIL_PLACED);
        initialized = true;
    }
    public static void fmlInit() { init(); }
    public static void fmlPostInit() {}
}
