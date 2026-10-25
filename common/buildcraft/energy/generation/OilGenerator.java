/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.energy.generation;

import java.util.Random;
import java.util.function.IntBinaryOperator;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.FlatChunkGenerator;
import net.minecraft.world.gen.feature.*;
import net.minecraft.world.gen.feature.util.FeatureContext;
import buildcraft.lib.misc.RandUtil;

/** Seed-derived oil fields, clipped to the decorating chunk. Remake by FelixMantra. */
public final class OilGenerator extends Feature<DefaultFeatureConfig> {
    public static final OilGenerator INSTANCE = new OilGenerator();
    private static final long SALT = 0xD0_46_B4_E4_0C_7D_07_CFL;
    // The widest original tendril has radius 44; a candidate centre stays inside its chunk.
    public static final int CHUNK_RADIUS = 3;
    public enum Type { LARGE, MEDIUM, LAKE }
    private OilGenerator() { super(DefaultFeatureConfig.CODEC); }

    @Override public boolean generate(FeatureContext<DefaultFeatureConfig> context) {
        var chunk = new ChunkPos(context.getOrigin());
        return generate(context.getWorld(), context.getGenerator(), chunk.x, chunk.z) > 0;
    }
    public int generate(StructureWorldAccess world, ChunkGenerator generator, int chunkX, int chunkZ) {
        var settings = OilGenerationSettings.current();
        var server = world.toServerWorld();
        if (generator instanceof FlatChunkGenerator || !settings.allowsDimension(server.getRegistryKey().getValue())) return 0;
        var noise = server.getChunkManager().getNoiseConfig();
        IntBinaryOperator height = (x,z) -> generator.getHeight(x,z,Heightmap.Type.WORLD_SURFACE_WG,world,noise) - 1;
        int changed = 0;
        for (int dx = -CHUNK_RADIUS; dx <= CHUNK_RADIUS; dx++) for (int dz = -CHUNK_RADIUS; dz <= CHUNK_RADIUS; dz++) {
            int cx = chunkX + dx, cz = chunkZ + dz;
            BlockPos center = center(world.getSeed(),cx,cz);
            var biome = generator.getBiomeSource().getBiome(center.getX() >> 2, generator.getSeaLevel() >> 2,
                center.getZ() >> 2, noise.getMultiNoiseSampler()).getKey();
            if (biome.isEmpty()) continue;
            // Preserve the original exclusion around the central End island even if the dimension is enabled.
            if (server.getRegistryKey() == net.minecraft.world.World.END
                && (Math.abs((long)center.getX()) < 1200 || Math.abs((long)center.getZ()) < 1200)) continue;
            OilGenStructure plan = plan(world.getSeed(),cx,cz,biome.get().getValue(),world.getBottomY(),world.getTopY(),settings);
            if (plan != null) changed += plan.generate(world,new ChunkPos(chunkX,chunkZ),height);
        }
        return changed;
    }
    public static BlockPos center(long seed, int cx, int cz) {
        Random random = RandUtil.createRandomForChunk(seed,cx,cz,SALT);
        return new BlockPos(cx * 16 + random.nextInt(16),0,cz * 16 + random.nextInt(16));
    }
    /** Planning reads neither chunks nor mutable terrain, so adjacent chunks agree in either generation order. */
    public static OilGenStructure plan(long seed, int cx, int cz, Identifier biome, int bottom, int top,
            OilGenerationSettings settings) {
        if (!settings.enabled() || !settings.allowsBiome(biome) || top - bottom < 48) return null;
        Random random = RandUtil.createRandomForChunk(seed,cx,cz,SALT);
        int x = cx * 16 + random.nextInt(16), z = cz * 16 + random.nextInt(16);
        double bonus = settings.multiplier(biome);
        Type type;
        if (random.nextDouble() < settings.large() * bonus) type = Type.LARGE;
        else if (random.nextDouble() < settings.medium() * bonus) type = Type.MEDIUM;
        else if (settings.surface().contains(biome) && random.nextDouble() < settings.small() * bonus) type = Type.LAKE;
        else return null;
        int lakeRadius = type == Type.LARGE ? 4 : type == Type.LAKE ? 6 : 2;
        int radius = type == Type.MEDIUM ? 5 + random.nextInt(10) : 25 + random.nextInt(20);
        boolean[][] pattern = tendril(lakeRadius,radius,random);
        int depth = random.nextBoolean() ? 1 : 2;
        int wellY = bottom + 20 + random.nextInt(10);
        int wellRadius = type == Type.LARGE ? 8 + random.nextInt(9) : type == Type.MEDIUM ? 4 + random.nextInt(4) : 0;
        int min = type == Type.LARGE ? settings.largeMin() : settings.smallMin();
        int max = type == Type.LARGE ? settings.largeMax() : settings.smallMax();
        int spoutHeight = settings.spouts() && type != Type.LAKE
            ? Math.min(min,max) + (min == max ? 0 : random.nextInt(Math.abs(max-min))) : 0;
        return new OilGenStructure(type,new BlockPos(x,wellY,z),bottom,top,wellRadius,radius,pattern,depth,
            settings.spouts() && type != Type.LAKE,spoutHeight);
    }
    private static boolean[][] tendril(int lakeRadius, int radius, Random random) {
        boolean[][] pattern = new boolean[radius * 2 + 1][radius * 2 + 1];
        for (int x=-lakeRadius;x<=lakeRadius;x++) for (int z=-lakeRadius;z<=lakeRadius;z++)
            pattern[radius+x][radius+z] = x*x+z*z <= lakeRadius*lakeRadius;
        for (int w=1;w<radius;w++) {
            float probability = (float)(radius-w+4)/(radius+4);
            grow(random,probability,radius,radius+w,pattern); grow(random,probability,radius,radius-w,pattern);
            grow(random,probability,radius+w,radius,pattern); grow(random,probability,radius-w,radius,pattern);
            for (int i=1;i<=w;i++) for (int signX : new int[]{-1,1}) for (int signZ : new int[]{-1,1}) {
                grow(random,probability,radius+signX*i,radius+signZ*w,pattern);
                grow(random,probability,radius+signX*w,radius+signZ*i,pattern);
            }
        }
        return pattern;
    }
    private static void grow(Random random,float chance,int x,int z,boolean[][] pattern) {
        if (random.nextFloat() <= chance) pattern[x][z] = set(pattern,x-1,z) | set(pattern,x+1,z) | set(pattern,x,z-1) | set(pattern,x,z+1);
    }
    private static boolean set(boolean[][] pattern,int x,int z) {
        return x>=0 && z>=0 && x<pattern.length && z<pattern.length && pattern[x][z];
    }
}
