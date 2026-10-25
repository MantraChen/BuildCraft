/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.lib.mixin;

import java.util.List;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import buildcraft.energy.generation.BiomeInitializer;

@Mixin(targets="net.minecraft.world.gen.surfacebuilder.MaterialRules$BiomeMaterialCondition")
public abstract class OilBiomeSurfaceMixin {
    @ModifyVariable(method="<init>",at=@At("HEAD"),argsOnly=true)
    private static List<RegistryKey<Biome>> buildcraft_oilDesertSurface(List<RegistryKey<Biome>> original) {
        return BiomeInitializer.surfaceBiomes(original);
    }
}
