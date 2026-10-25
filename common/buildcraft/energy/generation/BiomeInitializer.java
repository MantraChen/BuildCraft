/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.energy.generation;

import java.util.*;
import com.mojang.datafixers.util.Pair;
import net.minecraft.registry.*;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.*;
import net.minecraft.world.biome.source.util.MultiNoiseUtil.*;

/** Native climate partitions retain the surrounding terrain and all other climate dimensions. */
public final class BiomeInitializer {
    public static final RegistryKey<Biome> OIL_DESERT=RegistryKey.of(RegistryKeys.BIOME,new Identifier("buildcraftenergy","oil_desert"));
    public static final RegistryKey<Biome> OIL_OCEAN=RegistryKey.of(RegistryKeys.BIOME,new Identifier("buildcraftenergy","oil_ocean"));
    private static final Set<RegistryKey<Biome>> OCEANS=Set.of(BiomeKeys.OCEAN,BiomeKeys.DEEP_OCEAN,
        BiomeKeys.COLD_OCEAN,BiomeKeys.DEEP_COLD_OCEAN,BiomeKeys.FROZEN_OCEAN,BiomeKeys.DEEP_FROZEN_OCEAN,
        BiomeKeys.LUKEWARM_OCEAN,BiomeKeys.DEEP_LUKEWARM_OCEAN,BiomeKeys.WARM_OCEAN);
    private BiomeInitializer() {}
    public static Entries<RegistryEntry<Biome>> addOilBiomes(Entries<RegistryEntry<Biome>> original,
            RegistryEntryLookup<Biome> biomes,boolean desert,boolean ocean) {
        if(!desert && !ocean)return original;
        var result=new ArrayList<Pair<NoiseHypercube,RegistryEntry<Biome>>>();
        for(var entry:original.getEntries()) {
            var cube=entry.getFirst();var biome=entry.getSecond();
            if(desert && biome.matchesKey(BiomeKeys.DESERT))
                partition(result,cube,biome,biomes.getOrThrow(OIL_DESERT),true,-4200,-3600);
            else if(ocean && biome.getKey().filter(OCEANS::contains).isPresent())
                partition(result,cube,biome,biomes.getOrThrow(OIL_OCEAN),false,3500,4000);
            else result.add(entry);
        }
        return new Entries<>(List.copyOf(result));
    }
    private static void partition(List<Pair<NoiseHypercube,RegistryEntry<Biome>>> result,NoiseHypercube cube,
            RegistryEntry<Biome> original,RegistryEntry<Biome> oil,boolean humidity,long from,long to) {
        var range=humidity?cube.humidity():cube.erosion();
        long min=Math.max(range.min(),from),max=Math.min(range.max(),to);
        if(min>max) {result.add(Pair.of(cube,original));return;}
        if(range.min()<min)result.add(Pair.of(replace(cube,new ParameterRange(range.min(),min-1),humidity),original));
        result.add(Pair.of(replace(cube,new ParameterRange(min,max),humidity),oil));
        if(max<range.max())result.add(Pair.of(replace(cube,new ParameterRange(max+1,range.max()),humidity),original));
    }
    private static NoiseHypercube replace(NoiseHypercube cube,ParameterRange range,boolean humidity) {
        return new NoiseHypercube(cube.temperature(),humidity?range:cube.humidity(),cube.continentalness(),
            humidity?cube.erosion():range,cube.depth(),cube.weirdness(),cube.offset());
    }
    /** Desert oil fields use the same sand/sandstone surface rules, including data-pack rules. */
    public static List<RegistryKey<Biome>> surfaceBiomes(List<RegistryKey<Biome>> original) {
        if(!original.contains(BiomeKeys.DESERT) || original.contains(OIL_DESERT))return original;
        var result=new ArrayList<>(original);result.add(OIL_DESERT);return List.copyOf(result);
    }
}
