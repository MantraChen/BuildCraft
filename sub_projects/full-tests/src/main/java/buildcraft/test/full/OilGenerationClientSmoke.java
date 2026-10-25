/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;

import java.util.concurrent.CompletableFuture;
import buildcraft.core.*;
import buildcraft.energy.*;
import buildcraft.energy.generation.*;
import buildcraft.energy.tile.TileSpringOil;
import buildcraft.factory.*;
import buildcraft.factory.tile.*;
import buildcraft.transport.*;
import buildcraft.transport.tile.TilePipeHolder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.item.*;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.world.gen.*;
import net.minecraft.world.level.LevelInfo;

/** Uses untouched normal terrain and the normal configured generation probabilities. */
public final class OilGenerationClientSmoke implements ClientModInitializer {
    private int stage,ticks;private long deadline;private String saveName;
    private volatile BlockPos well,pump,desert,ocean;
    private volatile int totalSources,topOil;
    private volatile long savedFluid;
    private CompletableFuture<Void> work;
    @Override public void onInitializeClient() {
        if(Boolean.getBoolean("buildcraft.oilGenerationClientSmoke"))ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }
    private void tick(MinecraftClient client) {
        if(stage==0) {
            if(client.getOverlay()!=null || !(client.currentScreen instanceof TitleScreen))return;
            client.options.pauseOnLostFocus=false;client.options.getViewDistance().setValue(6);
            client.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
            deadline=System.nanoTime()+600_000_000_000L;saveName="oil-worldgen-smoke-"+System.currentTimeMillis();stage=1;
            var rules=new GameRules();rules.get(GameRules.DO_MOB_SPAWNING).set(false,null);
            client.createIntegratedServerLoader().createAndStart(saveName,
                new LevelInfo("BuildCraft natural oil — Remake by FelixMantra",GameMode.CREATIVE,false,Difficulty.PEACEFUL,true,rules,DataConfiguration.SAFE_MODE),
                new GeneratorOptions(4754L,false,false),
                registries->registries.get(RegistryKeys.WORLD_PRESET).getOrThrow(WorldPresets.DEFAULT).createDimensionsRegistryHolder());return;
        }
        if(stage==16)return;
        if(System.nanoTime()>deadline)throw new IllegalStateException("Oil generation client timed out at stage "+stage);
        if(stage==10) {
            if(client.getServer()!=null || client.world!=null)return;
            stage=11;ticks=0;client.createIntegratedServerLoader().start(new TitleScreen(),saveName);return;
        }
        if(client.world==null || client.player==null || client.getServer()==null)return;
        ticks++;if(ticks%20==0)client.getToastManager().clear();
        if(stage==1) {
            work=client.getServer().submit(()->{
                var world=client.getServer().getOverworld();var generator=world.getChunkManager().getChunkGenerator();
                var noise=world.getChunkManager().getNoiseConfig();var settings=OilGenerationSettings.current();
                require(!(generator instanceof net.minecraft.world.gen.chunk.FlatChunkGenerator),"Fixture is not a normal world");
                OilGenStructure selected=null;
                outer:for(int radius=0;radius<=128;radius++)for(int cx=-radius;cx<=radius;cx++)for(int cz=-radius;cz<=radius;cz++) {
                    if(Math.max(Math.abs(cx),Math.abs(cz))!=radius)continue;
                    var at=OilGenerator.center(world.getSeed(),cx,cz);
                    var biome=generator.getBiomeSource().getBiome(at.getX()>>2,generator.getSeaLevel()>>2,at.getZ()>>2,noise.getMultiNoiseSampler()).getKey().orElseThrow();
                    var plan=OilGenerator.plan(world.getSeed(),cx,cz,biome.getValue(),world.getBottomY(),world.getTopY(),settings);
                    if(plan==null || plan.type!=OilGenerator.Type.LARGE)continue;
                    int height=generator.getHeight(at.getX(),at.getZ(),Heightmap.Type.WORLD_SURFACE_WG,world,noise)-1;
                    if(height<64 || height>100)continue;
                    selected=plan;break outer;
                }
                require(selected!=null,"No naturally configured large oil well found near seed 4754");
                well=selected.center;world.getChunk(well);
                require(world.getBlockState(well).isOf(BCEnergyFluids.crudeOil[0].getBlock()),"Native chunk decorator did not generate planned oil reservoir");
                var spring=world.getBlockEntity(new BlockPos(well.getX(),world.getBottomY(),well.getZ()));
                require(spring instanceof TileSpringOil && ((TileSpringOil)spring).totalSources>0,"Natural oil spring was not initialized");
                totalSources=((TileSpringOil)spring).totalSources;
                topOil=world.getTopY()-1;
                while(topOil>well.getY() && !world.getBlockState(new BlockPos(well.getX(),topOil,well.getZ())).isOf(BCEnergyFluids.crudeOil[0].getBlock()))topOil--;
                require(topOil>64,"Oil spout did not reach above natural terrain");
                pump=new BlockPos(well.getX(),topOil+2,well.getZ());
                var player=world.getServer().getPlayerManager().getPlayer(client.player.getUuid());
                world.setTimeOfDay(1000);player.getAbilities().flying=true;player.sendAbilitiesUpdate();
                player.teleport(world,well.getX()+13.5,topOil+5,well.getZ()+13.5,135,25);
                System.out.println("BUILDCRAFT OIL GENERATION native well="+well+" top="+topOil+" estimatedSources="+totalSources);
            });advance();
        } else if(stage==2 && ready() && ticks>100) {
            screenshot(client,"natural-oil-field.png");
            work=client.getServer().submit(()->{
                var world=client.getServer().getOverworld();var player=world.getServer().getPlayerManager().getPlayer(client.player.getUuid());
                player.teleport(world,pump.getX()+2.5,pump.getY()+1,pump.getZ()+3.5,135,35);
                world.setBlockState(pump.down(),Blocks.STONE.getDefaultState());
                player.setStackInHand(Hand.MAIN_HAND,new ItemStack(BCFactoryBlocks.pump));
                var hit=new BlockHitResult(Vec3d.ofCenter(pump.down()).add(0,.5,0),Direction.UP,pump.down(),false);
                require(player.interactionManager.interactBlock(player,world,player.getMainHandStack(),Hand.MAIN_HAND,hit).isAccepted(),"Pump placement on natural field failed");
                player.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);world.setBlockState(pump.down(),Blocks.AIR.getDefaultState());
                world.setBlockState(pump.west(),BCTransportBlocks.pipeHolder.getDefaultState());
                ((TilePipeHolder)world.getBlockEntity(pump.west())).onPlacedBy(null,new ItemStack(BCTransportItems.pipeFluidStone));
                world.setBlockState(pump.west(2),BCFactoryBlocks.tank.getDefaultState());
                for(int i=1;i<=2;i++) {
                    world.setBlockState(pump.east(i),BCTransportBlocks.pipeHolder.getDefaultState());
                    ((TilePipeHolder)world.getBlockEntity(pump.east(i))).onPlacedBy(null,new ItemStack(i==2?BCTransportItems.pipePowerWood:BCTransportItems.pipePowerStone));
                }
                world.setBlockState(pump.east(3).down(),Blocks.REDSTONE_BLOCK.getDefaultState());
                world.setBlockState(pump.east(3),BCCoreBlocks.engine.getDefaultState().with(buildcraft.api.properties.BuildCraftProperties.ENGINE_TYPE,buildcraft.api.enums.EnumEngineType.CREATIVE));
                ((buildcraft.core.tile.TileEngineCreative)world.getBlockEntity(pump.east(3))).currentOutputIndex=4;
            });advance();
        } else if(stage==3 && ready() && ticks>220) {
            work=client.getServer().submit(()->{
                var world=client.getServer().getOverworld();var tank=(TileTank)world.getBlockEntity(pump.west(2));
                require(tank.tank.getStorage().getAmount()>=FluidConstants.BUCKET && tank.tank.getStorage().getResource().isOf(BCEnergyFluids.crudeOil[0]),"Natural oil did not reach native tank through pump/engine/pipes");
                world.setBlockState(pump.east(3).down(),Blocks.AIR.getDefaultState());
                var player=world.getServer().getPlayerManager().getPlayer(client.player.getUuid());
                // A small platform lets the survival player use the native bucket interaction safely.
                for(int x=-3;x<=0;x++)for(int z=2;z<=4;z++)world.setBlockState(pump.add(x,-1,z),Blocks.STONE.getDefaultState());
                player.teleport(world,pump.getX()-1.5,pump.getY(),pump.getZ()+3.5,180,20);
                player.changeGameMode(GameMode.SURVIVAL);player.getInventory().setStack(0,new ItemStack(Items.BUCKET));
            });advance();
        } else if(stage==4 && ready()) {
            client.player.getInventory().selectedSlot=0;client.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(0));
            client.interactionManager.interactBlock(client.player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(pump.west(2)),Direction.SOUTH,pump.west(2),false));advance();
        } else if(stage==5 && ticks>30) {
            require(client.player.getMainHandStack().isOf(BCEnergyFluids.crudeOil[0].getBucketItem()),"Survival bucket could not collect naturally generated oil");
            screenshot(client,"natural-oil-pumping.png");
            work=client.getServer().submit(()->{
                var world=client.getServer().getOverworld();
                var result=world.locateBiome(b->b.matchesKey(BiomeInitializer.OIL_DESERT),new BlockPos(0,64,0),16000,64,64);
                require(result!=null,"Native biome search could not locate a desert oil field");desert=result.getFirst();
                result=world.locateBiome(b->b.matchesKey(BiomeInitializer.OIL_OCEAN),new BlockPos(0,64,0),16000,64,64);
                require(result!=null,"Native biome search could not locate an ocean oil field");ocean=result.getFirst();
                var player=world.getServer().getPlayerManager().getPlayer(client.player.getUuid());
                player.changeGameMode(GameMode.CREATIVE);player.getAbilities().flying=true;player.sendAbilitiesUpdate();
                world.getChunk(desert);int ground=world.getTopY(Heightmap.Type.WORLD_SURFACE,desert.getX(),desert.getZ())-1;
                boolean sand=false;
                for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++) {
                    int y=world.getTopY(Heightmap.Type.WORLD_SURFACE,desert.getX()+x,desert.getZ()+z)-1;
                    var at=new BlockPos(desert.getX()+x,y,desert.getZ()+z);
                    if(world.getBiome(at).matchesKey(BiomeInitializer.OIL_DESERT) && world.getBlockState(at).isOf(Blocks.SAND))sand=true;
                }
                require(sand,"Oil desert lost native sand surface");
                player.teleport(world,desert.getX()+.5,ground+7,desert.getZ()+.5,135,25);
                System.out.println("BUILDCRAFT OIL GENERATION oil biomes desert="+desert+" ocean="+ocean);
            });advance();
        } else if(stage==6 && ready() && ticks>100) {
            screenshot(client,"oil-desert-biome.png");
            work=client.getServer().submit(()->{
                var world=client.getServer().getOverworld();world.getChunk(ocean);
                var player=world.getServer().getPlayerManager().getPlayer(client.player.getUuid());
                player.teleport(world,ocean.getX()+.5,78,ocean.getZ()+.5,135,25);
            });advance();
        } else if(stage==7 && ready() && ticks>100) {
            screenshot(client,"oil-ocean-biome.png");
            work=client.getServer().submit(()->{
                var world=client.getServer().getOverworld();var player=world.getServer().getPlayerManager().getPlayer(client.player.getUuid());
                player.teleport(world,pump.getX()-1.5,pump.getY()+2,pump.getZ()+3.5,180,20);
            });advance();
        } else if(stage==8 && ready() && ticks>100) {
            work=client.getServer().submit(()->{savedFluid=((TileTank)client.getServer().getOverworld().getBlockEntity(pump.west(2))).tank.getStorage().getAmount();});advance();
        } else if(stage==9 && ready()) {
            stage=10;ticks=0;client.world.disconnect();client.disconnect(new TitleScreen());
        } else if(stage==11 && ticks>100) {
            work=client.getServer().submit(()->{
                var world=client.getServer().getOverworld();
                require(world.getBlockState(well).isOf(BCEnergyFluids.crudeOil[0].getBlock()),"World restart lost deep oil reservoir");
                var spring=(TileSpringOil)world.getBlockEntity(new BlockPos(well.getX(),world.getBottomY(),well.getZ()));
                require(spring!=null && spring.totalSources==totalSources,"World restart reset oil spring estimate");
                require(((TileTank)world.getBlockEntity(pump.west(2))).tank.getStorage().getAmount()>=savedFluid,"World restart lost collected natural oil");
                require(world.getRegistryManager().get(RegistryKeys.BIOME).contains(BiomeInitializer.OIL_DESERT),"World restart lost native oil biome registry");
            });advance();
        } else if(stage==12 && ready()) {
            System.out.println("BUILDCRAFT OIL GENERATION CLIENT: normal seeded terrain, natural reservoir/spout/spring, native pump/engine/pipes, survival oil bucket, both located oil biomes, desert sand and complete world restart; failures=0");advance();
        } else if(stage==13) {stage=16;client.scheduleStop();}
    }
    private static void screenshot(MinecraftClient client,String name) {
        net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(client.runDirectory,name,client.getFramebuffer(),message->{});
    }
    private void advance(){stage++;ticks=0;System.out.println("BUILDCRAFT OIL GENERATION CLIENT stage="+stage);}
    private boolean ready(){if(work==null || !work.isDone() || ticks<15)return false;work.join();return true;}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
