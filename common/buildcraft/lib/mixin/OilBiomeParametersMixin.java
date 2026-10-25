/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.lib.mixin;

import net.minecraft.registry.RegistryEntryLookup;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import buildcraft.core.BCCoreConfig;
import buildcraft.energy.BCEnergyConfig;
import buildcraft.energy.generation.BiomeInitializer;

@Mixin(MultiNoiseBiomeSourceParameterList.class)
public abstract class OilBiomeParametersMixin {
    @Shadow @Final @Mutable private MultiNoiseUtil.Entries<RegistryEntry<Biome>> entries;
    @Inject(method="<init>",at=@At("RETURN"))
    private void buildcraft_oilBiomes(MultiNoiseBiomeSourceParameterList.Preset preset,RegistryEntryLookup<Biome> biomes,CallbackInfo callback) {
        if(preset==MultiNoiseBiomeSourceParameterList.Preset.OVERWORLD && BCCoreConfig.worldGen)
            entries=BiomeInitializer.addOilBiomes(entries,biomes,BCEnergyConfig.enableOilDesertBiome,BCEnergyConfig.enableOilOceanBiome);
    }
}
