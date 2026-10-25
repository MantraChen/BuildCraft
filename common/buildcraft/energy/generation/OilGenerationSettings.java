/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.energy.generation;

import java.util.Set;
import net.minecraft.util.Identifier;
import buildcraft.core.BCCoreConfig;
import buildcraft.energy.BCEnergyConfig;

/** Immutable settings shared by a single generation pass, including legacy dimension IDs. */
public record OilGenerationSettings(boolean enabled, double rate, double small, double medium, double large,
        boolean spouts, int smallMin, int smallMax, int largeMin, int largeMax,
        Set<Identifier> excessive, Set<Identifier> surface, Set<Identifier> excludedBiomes, boolean biomeBlacklist,
        Set<Integer> legacyDimensions, Set<Identifier> dimensions, boolean dimensionBlacklist) {
    public OilGenerationSettings {
        rate = finite(rate, 0, Double.MAX_VALUE);
        small = finite(small, 0, 1); medium = finite(medium, 0, 1); large = finite(large, 0, 1);
        smallMin = Math.max(0, Math.min(128, smallMin)); smallMax = Math.max(0, Math.min(128, smallMax));
        largeMin = Math.max(0, Math.min(128, largeMin)); largeMax = Math.max(0, Math.min(128, largeMax));
        excessive = Set.copyOf(excessive); surface = Set.copyOf(surface); excludedBiomes = Set.copyOf(excludedBiomes);
        legacyDimensions = Set.copyOf(legacyDimensions); dimensions = Set.copyOf(dimensions);
    }
    private static double finite(double value, double min, double max) {
        return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : min;
    }
    public boolean allowsDimension(Identifier dimension) {
        Integer legacy = switch (dimension.toString()) {
            case "minecraft:overworld" -> 0;
            case "minecraft:the_nether" -> -1;
            case "minecraft:the_end" -> 1;
            default -> null;
        };
        boolean listed = dimensions.contains(dimension) || legacy != null && legacyDimensions.contains(legacy);
        return enabled && listed != dimensionBlacklist;
    }
    public boolean allowsBiome(Identifier biome) { return excludedBiomes.contains(biome) != biomeBlacklist; }
    public double multiplier(Identifier biome) {
        return rate * (surface.contains(biome) ? 3 : 1) * (excessive.contains(biome) ? 30 : 1);
    }
    public static OilGenerationSettings current() {
        return new OilGenerationSettings(BCCoreConfig.worldGen && BCEnergyConfig.enableOilGeneration,
            BCEnergyConfig.oilWellGenerationRate, BCEnergyConfig.smallOilGenProb, BCEnergyConfig.mediumOilGenProb,
            BCEnergyConfig.largeOilGenProb, BCEnergyConfig.enableOilSpouts, BCEnergyConfig.smallSpoutMinHeight,
            BCEnergyConfig.smallSpoutMaxHeight, BCEnergyConfig.largeSpoutMinHeight, BCEnergyConfig.largeSpoutMaxHeight,
            BCEnergyConfig.excessiveBiomes, BCEnergyConfig.surfaceDepositBiomes, BCEnergyConfig.excludedBiomes,
            BCEnergyConfig.excludedBiomesIsBlackList, BCEnergyConfig.excludedDimensions,
            BCEnergyConfig.excludedDimensionNames, BCEnergyConfig.excludedDimensionsIsBlackList);
    }
}
