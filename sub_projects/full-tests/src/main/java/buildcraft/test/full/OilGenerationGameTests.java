/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;

import java.util.*;
import java.lang.reflect.*;
import buildcraft.energy.*;
import buildcraft.energy.generation.*;
import buildcraft.energy.tile.TileSpringOil;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.GenerationStep;

public final class OilGenerationGameTests implements FabricGameTest {
    private static final Identifier PLAINS=new Identifier("minecraft:plains");
    private static final Identifier OVERWORLD=new Identifier("minecraft:overworld");
    private static OilGenerationSettings settings(double small,double medium,double large,boolean spouts) {
        return new OilGenerationSettings(true,1,small,medium,large,spouts,6,12,10,20,
            Set.of(),Set.of(PLAINS),Set.of(),true,Set.of(-1,1),Set.of(),true);
    }
    private static OilGenStructure large(int cx,int cz) {return OilGenerator.plan(4754,cx,cz,PLAINS,-64,180,settings(0,0,1,true));}
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativeOilBiomesAreLoadedAndReachableThroughOverworldClimate(TestContext c) {
        var registry=c.getWorld().getRegistryManager().get(RegistryKeys.BIOME);
        var parameters=new net.minecraft.world.biome.source.MultiNoiseBiomeSourceParameterList(
            net.minecraft.world.biome.source.MultiNoiseBiomeSourceParameterList.Preset.OVERWORLD,registry.getReadOnlyWrapper());
        var source=net.minecraft.world.biome.source.MultiNoiseBiomeSource.create(parameters.getEntries());
        c.assertTrue(source.getBiomes().stream().anyMatch(b->b.matchesKey(BiomeInitializer.OIL_DESERT))
            && source.getBiomes().stream().anyMatch(b->b.matchesKey(BiomeInitializer.OIL_OCEAN)),"Oil biomes are unreachable in the native climate source");
        for(var key:List.of(BiomeInitializer.OIL_DESERT,BiomeInitializer.OIL_OCEAN)) {
            boolean reachable=false;
            for(var entry:parameters.getEntries().getEntries())if(entry.getSecond().matchesKey(key)) {
                var cube=entry.getFirst();var point=new net.minecraft.world.biome.source.util.MultiNoiseUtil.NoiseValuePoint(
                    midpoint(cube.temperature()),midpoint(cube.humidity()),midpoint(cube.continentalness()),
                    midpoint(cube.erosion()),midpoint(cube.depth()),midpoint(cube.weirdness()));
                if(parameters.getEntries().get(point).matchesKey(key)){reachable=true;break;}
            }
            c.assertTrue(reachable,"Native climate search never selects "+key.getValue());
        }c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void oilBiomeTogglesAndNetherPresetAreRespected(TestContext c) {
        var registry=c.getWorld().getRegistryManager().get(RegistryKeys.BIOME);
        var nether=new net.minecraft.world.biome.source.MultiNoiseBiomeSourceParameterList(
            net.minecraft.world.biome.source.MultiNoiseBiomeSourceParameterList.Preset.NETHER,registry.getReadOnlyWrapper()).getEntries();
        c.assertTrue(nether.getEntries().stream().noneMatch(e->e.getSecond().matchesKey(BiomeInitializer.OIL_DESERT)
            || e.getSecond().matchesKey(BiomeInitializer.OIL_OCEAN)),"Oil biome polluted Nether climate");
        var cube=net.minecraft.world.biome.source.util.MultiNoiseUtil.createNoiseHypercube(1,-.39f,0,0,0,0,0);
        var original=new net.minecraft.world.biome.source.util.MultiNoiseUtil.Entries<>(List.of(
            com.mojang.datafixers.util.Pair.of(cube,(net.minecraft.registry.entry.RegistryEntry<net.minecraft.world.biome.Biome>)registry.entryOf(net.minecraft.world.biome.BiomeKeys.DESERT))));
        c.assertTrue(BiomeInitializer.addOilBiomes(original,registry.getReadOnlyWrapper(),false,false)==original,"Disabled oil biomes changed climate");
        c.assertTrue(BiomeInitializer.addOilBiomes(original,registry.getReadOnlyWrapper(),true,false).getEntries().get(0).getSecond().matchesKey(BiomeInitializer.OIL_DESERT),"Desert-only toggle failed");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void actualSurfaceConditionIncludesOilDesertWithoutDuplicatingKeys(TestContext c) {
        var rule=net.minecraft.world.gen.surfacebuilder.MaterialRules.biome(net.minecraft.world.biome.BiomeKeys.DESERT);
        var json=net.minecraft.world.gen.surfacebuilder.MaterialRules.MaterialCondition.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,rule)
            .getOrThrow(false,message->{throw new AssertionError(message);}).toString();
        c.assertTrue(json.contains("buildcraftenergy:oil_desert"),"Native sand/sandstone predicate omits oil desert");
        var input=List.of(net.minecraft.world.biome.BiomeKeys.DESERT,BiomeInitializer.OIL_DESERT);
        c.assertTrue(BiomeInitializer.surfaceBiomes(input)==input,"Repeated surface decoding duplicated oil key");c.complete();
    }
    private static long midpoint(net.minecraft.world.biome.source.util.MultiNoiseUtil.ParameterRange range) {return range.min()+(range.max()-range.min())/2;}
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativeConfiguredPlacedFeatureAndBiomeDecorationAreRegistered(TestContext c) {
        var registries=c.getWorld().getRegistryManager();
        c.assertTrue(Registries.FEATURE.get(BCEnergyWorldGen.OIL)==OilGenerator.INSTANCE,"Native oil feature missing");
        c.assertTrue(registries.get(RegistryKeys.CONFIGURED_FEATURE).containsId(BCEnergyWorldGen.OIL)
            && registries.get(RegistryKeys.PLACED_FEATURE).contains(BCEnergyWorldGen.OIL_PLACED),"Data-pack feature registration missing");
        var biome=registries.get(RegistryKeys.BIOME).get(PLAINS);
        c.assertTrue(biome.getGenerationSettings().getFeatures().get(GenerationStep.Feature.LAKES.ordinal()).stream()
            .anyMatch(entry->entry.matchesKey(BCEnergyWorldGen.OIL_PLACED)),"Actual biome decorator does not include oil");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void seedAndChunkDetermineTheWholeDeposit(TestContext c) {
        var a=large(-11,19);var b=large(-11,19);
        c.assertTrue(signature(a).equals(signature(b)),"Repeat planning changed geometry");
        var changed=OilGenerator.plan(4755,-11,19,PLAINS,-64,180,settings(0,0,1,true));
        c.assertTrue(!signature(a).equals(signature(changed)),"Different seed produced identical deposit");
        c.assertTrue(new ChunkPos(a.center).equals(new ChunkPos(-11,19)),"Negative-coordinate candidate escaped its chunk");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void allThreeOriginalDepositTypesRemainAvailable(TestContext c) {
        c.assertTrue(OilGenerator.plan(1,0,0,PLAINS,-64,320,settings(0,0,1,true)).type==OilGenerator.Type.LARGE,"Large deposit missing");
        c.assertTrue(OilGenerator.plan(1,0,0,PLAINS,-64,320,settings(0,1,0,true)).type==OilGenerator.Type.MEDIUM,"Medium deposit missing");
        var lake=OilGenerator.plan(1,0,0,PLAINS,-64,320,settings(1,0,0,true));
        c.assertTrue(lake.type==OilGenerator.Type.LAKE && lake.reservoirRadius==0 && !lake.spout,"Surface lake grew a reservoir/spout");
        c.assertTrue(OilGenerator.plan(1,0,0,new Identifier("minecraft:desert"),-64,320,settings(1,0,0,true))==null,"Small deposits ignored surface-biome setting");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void zeroProbabilitiesAndInvalidConfigDoNotGenerate(TestContext c) {
        c.assertTrue(OilGenerator.plan(1,0,0,PLAINS,-64,320,settings(0,0,0,true))==null,"Zero probabilities generated oil");
        c.assertTrue(OilGenerator.plan(1,0,0,PLAINS,-64,320,settings(Double.NaN,-1,Double.POSITIVE_INFINITY,true))==null,"Invalid probabilities generated oil");
        var disabled=new OilGenerationSettings(false,1,1,1,1,true,6,12,10,20,Set.of(),Set.of(),Set.of(),true,Set.of(),Set.of(),true);
        c.assertTrue(OilGenerator.plan(1,0,0,PLAINS,-64,320,disabled)==null && !disabled.allowsDimension(OVERWORLD),"Disabled generation still active");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void highFiniteGenerationRateCanMakeOriginalRareWellsCertain(TestContext c) {
        var high=new OilGenerationSettings(true,2500,0,0,.0004,false,0,0,0,0,Set.of(),Set.of(),Set.of(),true,Set.of(),Set.of(),true);
        for(int cx=-5;cx<=5;cx++)for(int cz=-5;cz<=5;cz++)
            c.assertTrue(OilGenerator.plan(4754,cx,cz,PLAINS,-64,320,high)!=null,"A finite configured rate was silently capped below certainty");
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void biomeBlacklistWhitelistAndOriginalBonusesApply(TestContext c) {
        var allow=new OilGenerationSettings(true,2,1,1,1,true,0,0,0,0,Set.of(PLAINS),Set.of(PLAINS),Set.of(PLAINS),false,Set.of(),Set.of(),true);
        c.assertTrue(allow.allowsBiome(PLAINS) && !allow.allowsBiome(new Identifier("minecraft:desert"))
            && allow.multiplier(PLAINS)==180,"Biome whitelist or 3x/30x bonuses changed");
        var deny=new OilGenerationSettings(true,1,1,1,1,true,0,0,0,0,Set.of(),Set.of(),Set.of(PLAINS),true,Set.of(),Set.of(),true);
        c.assertTrue(OilGenerator.plan(1,0,0,PLAINS,-64,320,deny)==null,"Blacklisted biome generated oil");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativeAndLegacyDimensionListsComposeWithoutAliasingCustomWorlds(TestContext c) {
        var custom=new Identifier("test:moon");var current=settings(0,1,0,true);
        c.assertTrue(current.allowsDimension(OVERWORLD) && current.allowsDimension(custom)
            && !current.allowsDimension(new Identifier("minecraft:the_nether")) && !current.allowsDimension(new Identifier("minecraft:the_end")),"Legacy dimension IDs mapped incorrectly");
        var whitelist=new OilGenerationSettings(true,1,1,1,1,true,0,0,0,0,Set.of(),Set.of(),Set.of(),true,Set.of(0),Set.of(custom),false);
        c.assertTrue(whitelist.allowsDimension(OVERWORLD) && whitelist.allowsDimension(custom)
            && !whitelist.allowsDimension(new Identifier("test:other")),"Native whitelist leaked into a custom dimension");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void flatWorldsDoNotReceiveAutomaticOil(TestContext c) {
        c.assertTrue(OilGenerator.INSTANCE.generate(c.getWorld(),c.getWorld().getChunkManager().getChunkGenerator(),0,0)==0,"Flat-world generation was enabled");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void deepReservoirAndWorldBottomSpringAreNativeAndPersistent(TestContext c) {
        var p=large(1000,1000);var chunk=new ChunkPos(p.center);var w=c.getWorld();clear(w,chunk,-64,180);
        c.assertTrue(p.generate(strict(w,chunk),chunk,(x,z)->60)>0,"Oil deposit wrote nothing");
        c.assertTrue(w.getBlockState(p.center).isOf(BCEnergyFluids.crudeOil[0].getBlock()) && w.getFluidState(p.center).isStill(),"Reservoir is not native source oil");
        c.assertTrue(w.getBlockEntity(p.springPos()) instanceof TileSpringOil,"Modern world-bottom spring missing");
        var spring=(TileSpringOil)w.getBlockEntity(p.springPos());var tag=spring.createNbt();
        c.assertTrue(spring.totalSources>0 && tag.getInt("totalSources")==spring.totalSources,"Spring source estimate not saved");
        c.assertTrue(w.getBlockState(p.springPos().up()).isOf(BCEnergyFluids.crudeOil[0].getBlock()),"Spring disconnected from reservoir");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void chunkSlicesStayLocalAndAreIndependentOfApplicationOrder(TestContext c) {
        var p=large(1020,1000);var center=new ChunkPos(p.center);
        var chunks=List.of(center,new ChunkPos(center.x+1,center.z),new ChunkPos(center.x-1,center.z));
        for(var chunk:chunks)clear(c.getWorld(),chunk,-64,180);
        for(var chunk:chunks)p.generate(strict(c.getWorld(),chunk),chunk,(x,z)->60);
        var before=snapshot(c.getWorld(),chunks,-64,180);
        for(var chunk:chunks)clear(c.getWorld(),chunk,-64,180);
        for(int i=chunks.size()-1;i>=0;i--)p.generate(strict(c.getWorld(),chunks.get(i)),chunks.get(i),(x,z)->60);
        c.assertTrue(before.equals(snapshot(c.getWorld(),chunks,-64,180)),"Cross-chunk reservoir/tendrils depend on generation order");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void terrainFollowingLakeUsesEachColumnsHeight(TestContext c) {
        var p=OilGenerator.plan(1,1040,1000,PLAINS,-64,180,settings(1,0,0,true));var chunk=new ChunkPos(p.center);
        clear(c.getWorld(),chunk,-64,180);p.generate(strict(c.getWorld(),chunk),chunk,(x,z)->60+(x&3));
        for(int x=chunk.getStartX();x<=chunk.getEndX();x++)for(int z=chunk.getStartZ();z<=chunk.getEndZ();z++)if(p.hasSurfaceOil(x,z)) {
            c.assertTrue(c.getWorld().getBlockState(new BlockPos(x,60+(x&3),z)).isOf(BCEnergyFluids.crudeOil[0].getBlock()),"Surface oil ignored terrain column height");
            c.assertTrue(c.getWorld().getBlockState(new BlockPos(x,61+(x&3),z)).isAir(),"Lake grew above terrain");
        }c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void generatedOilPreservesBlockEntitiesConstructedBlocksAndWaterloggedHosts(TestContext c) {
        var p=large(1060,1000);var chunk=new ChunkPos(p.center);var w=c.getWorld();clear(w,chunk,-64,180);
        BlockPos chest=p.center,diamond=p.center.up(),waterlogged=p.center.up(2),bedrock=p.center.up(3);
        w.setBlockState(chest,Blocks.CHEST.getDefaultState());w.setBlockState(diamond,Blocks.DIAMOND_BLOCK.getDefaultState());
        w.setBlockState(waterlogged,Blocks.OAK_FENCE.getDefaultState().with(net.minecraft.state.property.Properties.WATERLOGGED,true));
        w.setBlockState(bedrock,Blocks.BEDROCK.getDefaultState());
        p.generate(strict(w,chunk),chunk,(x,z)->60);
        c.assertTrue(w.getBlockState(chest).isOf(Blocks.CHEST) && w.getBlockState(diamond).isOf(Blocks.DIAMOND_BLOCK)
            && w.getBlockState(waterlogged).isOf(Blocks.OAK_FENCE) && w.getBlockState(bedrock).isOf(Blocks.BEDROCK),"Oil overwrote protected terrain/containers");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void disabledSpoutRetainsReservoirAndSurfaceWithoutConnectingShaft(TestContext c) {
        var p=OilGenerator.plan(4754,1080,1000,PLAINS,-64,180,settings(0,1,0,false));var chunk=new ChunkPos(p.center);
        clear(c.getWorld(),chunk,-64,180);p.generate(strict(c.getWorld(),chunk),chunk,(x,z)->60);
        c.assertTrue(c.getWorld().getBlockState(p.center).isOf(BCEnergyFluids.crudeOil[0].getBlock())
            && c.getWorld().getBlockState(new BlockPos(p.center.getX(),20,p.center.getZ())).isAir(),"Disabled spout still created a shaft");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void reapplyingOilDoesNotResetSpringCredit(TestContext c) {
        var p=large(1100,1000);var chunk=new ChunkPos(p.center);var w=c.getWorld();clear(w,chunk,-64,180);
        p.generate(strict(w,chunk),chunk,(x,z)->60);var spring=(TileSpringOil)w.getBlockEntity(p.springPos());
        spring.totalSources=1234;var nbt=spring.createNbt();
        p.generate(strict(w,chunk),chunk,(x,z)->60);
        c.assertTrue(w.getBlockEntity(p.springPos())==spring && spring.createNbt().equals(nbt),"Generation reset saved pump credit");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void malformedHeightsAreBoundedAndShortDimensionsAreSkipped(TestContext c) {
        var extreme=new OilGenerationSettings(true,1,0,0,1,true,Integer.MAX_VALUE,Integer.MIN_VALUE,1000000,-100,
            Set.of(),Set.of(),Set.of(),true,Set.of(),Set.of(),true);
        var p=OilGenerator.plan(1,1120,1000,PLAINS,-64,0,extreme);var chunk=new ChunkPos(p.center);
        clear(c.getWorld(),chunk,-64,20);p.generate(strict(c.getWorld(),chunk),chunk,(x,z)->10000);
        c.assertTrue(p.spoutHeight<=128 && c.getWorld().getBlockState(new BlockPos(p.center.getX(),0,p.center.getZ())).isAir(),"Spout exceeded world height");
        c.assertTrue(OilGenerator.plan(1,0,0,PLAINS,0,32,extreme)==null,"Too-short dimension received a clipped reservoir");c.complete();
    }
    private static String signature(OilGenStructure p) {
        var result=new StringBuilder(p.center+":"+p.type+":"+p.reservoirRadius+":"+p.spoutHeight+":"+p.depth);
        for(int x=p.center.getX()-44;x<=p.center.getX()+44;x++)for(int z=p.center.getZ()-44;z<=p.center.getZ()+44;z++)if(p.hasSurfaceOil(x,z))result.append(';').append(x).append(',').append(z);
        return result.toString();
    }
    private static StructureWorldAccess strict(ServerWorld world,ChunkPos chunk) {
        return (StructureWorldAccess)Proxy.newProxyInstance(StructureWorldAccess.class.getClassLoader(),new Class[]{StructureWorldAccess.class},(proxy,method,args)->{
            if(args!=null)for(Object arg:args)if(arg instanceof BlockPos pos && !new ChunkPos(pos).equals(chunk))
                throw new AssertionError("Oil accessed neighboring chunk through "+method.getName()+" at "+pos);
            try{return method.invoke(world,args);}catch(InvocationTargetException error){throw error.getCause();}
        });
    }
    private static void clear(ServerWorld world,ChunkPos chunk,int bottom,int top) {
        world.getChunk(chunk.x,chunk.z);
        for(BlockPos pos:BlockPos.iterate(chunk.getStartX(),bottom,chunk.getStartZ(),chunk.getEndX(),top-1,chunk.getEndZ()))
            world.setBlockState(pos,Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS | Block.SKIP_DROPS);
    }
    private static Map<BlockPos,BlockState> snapshot(ServerWorld world,List<ChunkPos> chunks,int bottom,int top) {
        Map<BlockPos,BlockState> states=new HashMap<>();
        for(var chunk:chunks)for(BlockPos pos:BlockPos.iterate(chunk.getStartX(),bottom,chunk.getStartZ(),chunk.getEndX(),top-1,chunk.getEndZ())) {
            var state=world.getBlockState(pos);if(!state.isAir())states.put(pos.toImmutable(),state);
        }return states;
    }
}
