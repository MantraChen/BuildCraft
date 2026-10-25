/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;

import java.util.concurrent.CompletableFuture;
import buildcraft.api.mj.*;
import buildcraft.core.*;
import buildcraft.energy.BCEnergyFluids;
import buildcraft.factory.*;
import buildcraft.factory.tile.*;
import buildcraft.factory.tile.TileHeatExchange.*;
import buildcraft.factory.container.ContainerDistiller;
import buildcraft.factory.gui.GuiDistiller;
import buildcraft.transport.*;
import buildcraft.transport.tile.TilePipeHolder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.item.*;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.world.gen.*;
import net.minecraft.world.level.LevelInfo;

public final class HeatExchangeClientSmoke implements ClientModInitializer {
    private static final BlockPos START=new BlockPos(2,-59,2),END=START.east(2),DISTILLER=START.west(2);
    private int stage,ticks;private long deadline;private String saveName;
    private volatile int savedWarmup;
    private Storage<FluidVariant> oldPort;
    private CompletableFuture<Void> work;
    @Override public void onInitializeClient() {
        if(Boolean.getBoolean("buildcraft.heatExchangeClientSmoke"))ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }
    private void tick(MinecraftClient client) {
        if(stage==0) {
            if(client.getOverlay()!=null || !(client.currentScreen instanceof TitleScreen))return;
            client.options.pauseOnLostFocus=false;client.options.getViewDistance().setValue(6);
            client.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
            deadline=System.nanoTime()+300_000_000_000L;saveName="heat-exchange-smoke-"+System.currentTimeMillis();stage=1;
            var rules=new GameRules();rules.get(GameRules.DO_MOB_SPAWNING).set(false,null);
            client.createIntegratedServerLoader().createAndStart(saveName,
                new LevelInfo("BuildCraft heat exchange — Remake by FelixMantra",GameMode.CREATIVE,false,Difficulty.PEACEFUL,true,rules,DataConfiguration.SAFE_MODE),
                new GeneratorOptions(4754L,false,false),
                registries->registries.get(RegistryKeys.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createDimensionsRegistryHolder());return;
        }
        if(stage==21)return;
        if(System.nanoTime()>deadline)throw new IllegalStateException("Heat exchange client timed out at stage "+stage);
        if(stage==7) {
            if(client.getServer()!=null || client.world!=null)return;
            stage=8;ticks=0;client.createIntegratedServerLoader().start(new TitleScreen(),saveName);return;
        }
        if(client.world==null || client.player==null || client.getServer()==null)return;
        ticks++;if(ticks%20==0)client.getToastManager().clear();
        if(stage==1) {
            work=client.getServer().submit(()->{
                var world=client.getServer().getOverworld();var player=world.getServer().getPlayerManager().getPlayer(client.player.getUuid());
                world.setTimeOfDay(1000);player.teleport(world,3.5,-59,5.5,180,15);
                for(int i=0;i<3;i++)place(world,player,START.east(i),BCFactoryBlocks.heatExchange);
                place(world,player,DISTILLER,BCFactoryBlocks.distiller);
                for(int i=1;i<=2;i++) {
                    world.setBlockState(DISTILLER.west(i),BCTransportBlocks.pipeHolder.getDefaultState());
                    ((TilePipeHolder)world.getBlockEntity(DISTILLER.west(i))).onPlacedBy(null,new ItemStack(i==2?BCTransportItems.pipePowerWood:BCTransportItems.pipePowerStone));
                }
                world.setBlockState(DISTILLER.west(3),BCCoreBlocks.engine.getDefaultState()
                    .with(buildcraft.api.properties.BuildCraftProperties.ENGINE_TYPE,buildcraft.api.enums.EnumEngineType.CREATIVE));
                ((buildcraft.core.tile.TileEngineCreative)world.getBlockEntity(DISTILLER.west(3))).currentOutputIndex=4;
                player.changeGameMode(GameMode.SURVIVAL);
                player.getInventory().setStack(0,new ItemStack(BCEnergyFluids.crudeOil[0].getBucketItem()));
                player.getInventory().setStack(1,new ItemStack(Items.LAVA_BUCKET));
                player.getInventory().setStack(3,new ItemStack(BCCoreItems.wrench));
                player.currentScreenHandler.sendContentUpdates();
            });advance();
        } else if(stage==2 && ready() && ticks>25) {
            var first=(TileHeatExchange)client.world.getBlockEntity(START);var last=(TileHeatExchange)client.world.getBlockEntity(END);
            require(first!=null && first.isStart() && last!=null && last.isEnd() && first.getFacing()==Direction.SOUTH,
                "Native item placement did not form the correctly oriented multiblock on the client");
            require(first.getCachedState().getBlock().getName().getString().equals("Heat Exchanger"),"Heat exchanger name not translated");
            select(client,0);interact(client,START);advance();
        } else if(stage==3 && ticks>20) {
            var first=(TileHeatExchange)client.world.getBlockEntity(START);
            require(first.getSection().tankInput.getClientAmount()==1000 && client.player.getInventory().getStack(0).isOf(Items.BUCKET),
                "Cold oil bucket interaction failed to consume/synchronize");
            select(client,1);interact(client,END);advance();
        } else if(stage==4 && ticks>30) {
            var first=(TileHeatExchange)client.world.getBlockEntity(START);var last=(TileHeatExchange)client.world.getBlockEntity(END);
            require(last.getSection().tankInput.getClientAmount()==1000 && client.player.getInventory().getStack(1).isOf(Items.BUCKET)
                && ((ExchangeSectionStart)first.getSection()).getProgressState()==EnumProgressState.PREPARING,
                "Lava input or native warmup animation did not synchronize");
            screenshot(client,"heat-warming.png");
            work=client.getServer().submit(()->{
                var firstServer=heat(client,START);savedWarmup=((ExchangeSectionStart)firstServer.getSection()).getWarmupTicks();
                require(savedWarmup>10 && savedWarmup<100 && firstServer.getSection().tankInput.getStorage().getAmount()==FluidConstants.BUCKET,
                    "Warmup save fixture processed fluids too early");
            });advance();
        } else if(stage==5 && ready()) {
            stage=6;ticks=0;
        } else if(stage==6) {
            stage=7;ticks=0;client.world.disconnect();client.disconnect(new TitleScreen());
        } else if(stage==8 && ticks>15) {
            work=client.getServer().submit(()->{
                var world=client.getServer().getOverworld();var first=heat(client,START);var last=heat(client,END);
                require(first.isStart() && last.isEnd() && ((ExchangeSectionStart)first.getSection()).getWarmupTicks()>=savedWarmup,
                    "World restart lost structure or stored warmup");
                require(first.getSection().tankInput.getStorage().getAmount()+last.getSection().tankOutput.getStorage().getAmount()==FluidConstants.BUCKET,
                    "World restart lost or duplicated cold-side oil");
                world.setBlockState(END.up(),BCFactoryBlocks.tank.getDefaultState());
            });advance();
        } else if(stage==9 && ready() && ticks>130) {
            var first=(TileHeatExchange)client.world.getBlockEntity(START);
            require(first.isStart() && ((ExchangeSectionStart)first.getSection()).getProgressState()==EnumProgressState.RUNNING
                && first.getSection().tankInput.getClientAmount()>0 && first.getSection().tankInput.getClientAmount()<1000,
                "Counterflow did not animate while the native exchanger processed oil");
            screenshot(client,"heat-running.png");advance();
        } else if(stage==10 && ticks>240) {
            work=client.getServer().submit(()->{
                var world=client.getServer().getOverworld();var first=heat(client,START);var last=heat(client,END);
                var tank=(TileTank)world.getBlockEntity(END.up());
                require(first.getSection().tankInput.getStorage().getAmount()==0 && last.getSection().tankInput.getStorage().getAmount()==0
                    && tank.tank.getStorage().getAmount()==FluidConstants.BUCKET
                    && tank.tank.getStorage().getResource().isOf(BCEnergyFluids.crudeOil[1]),
                    "Heating did not consume one bucket of lava for exactly one bucket of warm oil");
            });advance();
        } else if(stage==11 && ready()) {
            select(client,0);interact(client,END.up());advance();
        } else if(stage==12 && ticks>25) {
            require(client.player.getMainHandStack().isOf(BCEnergyFluids.crudeOil[1].getBucketItem()),"Native tank did not return warm-oil bucket");
            interact(client,DISTILLER);advance();
        } else if(stage==13 && ticks>25) {
            require(client.player.getMainHandStack().isOf(Items.BUCKET),"Warm oil was not consumed by distiller input");
            work=client.getServer().submit(()->{
                var world=client.getServer().getOverworld();var distiller=(TileDistiller_BC8)world.getBlockEntity(DISTILLER);
                require(distiller.tankIn.getStorage().getAmount()==FluidConstants.BUCKET
                    && distiller.tankIn.getStorage().getResource().isOf(BCEnergyFluids.crudeOil[1]),"Wrong temperature entered distillation");
                world.setBlockState(DISTILLER.west(3).down(),Blocks.REDSTONE_BLOCK.getDefaultState());
            });advance();
        } else if(stage==14 && ready() && ticks>650) {
            interact(client,DISTILLER);advance();
        } else if(stage==15 && ticks>25) {
            if(!(client.currentScreen instanceof GuiDistiller))return;
            var handler=(ContainerDistiller)client.player.currentScreenHandler;
            require(handler.tile.tankIn.getClientAmount()==0 && handler.tile.tankGasOut.getClientAmount()==1250
                && handler.tile.tankLiquidOut.getClientAmount()==250,"Warm oil did not refine into exact temperature-specific products");
            screenshot(client,"heat-refinery.png");client.player.closeHandledScreen();
            work=client.getServer().submit(()->{
                var world=client.getServer().getOverworld();var distiller=(TileDistiller_BC8)world.getBlockEntity(DISTILLER);
                require(distiller.tankGasOut.getStorage().getResource().isOf(BCEnergyFluids.fuelMixedLight[1])
                    && distiller.tankLiquidOut.getStorage().getResource().isOf(BCEnergyFluids.oilDense[1]),"Distillation lost warm product identities");
                oldPort=FluidStorage.SIDED.find(world,START,Direction.DOWN);
                var player=world.getServer().getPlayerManager().getPlayer(client.player.getUuid());
                for(String id:new String[]{"heat_exchange","refining"}) {
                    var advancement=world.getServer().getAdvancementLoader().get(new Identifier("buildcraftfactory",id));
                    require(player.getAdvancementTracker().getProgress(advancement).isDone(),"Missing native owner advancement "+id);
                }
            });advance();
        } else if(stage==16 && ready()) {
            select(client,3);interact(client,START.east());advance();
        } else if(stage==17 && ticks>25) {
            var first=(TileHeatExchange)client.world.getBlockEntity(END);var last=(TileHeatExchange)client.world.getBlockEntity(START);
            require(first.isStart() && last.isEnd() && first.getFacing()==Direction.NORTH,"Native wrench reversal was not synchronized");
            screenshot(client,"heat-reversed.png");
            work=client.getServer().submit(()->{
                try(var tx=Transaction.openOuter()){require(oldPort.insert(FluidVariant.of(BCEnergyFluids.crudeOil[0]),1,tx)==0,"Old heat input survived reversal");}
                var world=client.getServer().getOverworld();var player=world.getServer().getPlayerManager().getPlayer(client.player.getUuid());
                player.changeGameMode(GameMode.CREATIVE);
                require(player.interactionManager.tryBreakBlock(START.east()),"Native middle-section removal failed");
            });advance();
        } else if(stage==18 && ready() && ticks>25) {
            require(!((TileHeatExchange)client.world.getBlockEntity(START)).isFormed()
                && !((TileHeatExchange)client.world.getBlockEntity(END)).isFormed(),"Broken multiblock stayed active on client");
            work=client.getServer().submit(()->{
                var world=client.getServer().getOverworld();var player=world.getServer().getPlayerManager().getPlayer(client.player.getUuid());
                require(player.interactionManager.tryBreakBlock(DISTILLER),"Native refinery removal failed");
                long remaining=world.getEntitiesByClass(net.minecraft.entity.ItemEntity.class,new Box(DISTILLER).expand(1),e->true).stream()
                    .map(e->buildcraft.core.item.ItemFragileFluidContainer.getFluid(e.getStack())).mapToLong(f->f.amount).sum();
                require(remaining==FluidConstants.BUCKET*1500/1000,"Final warm refining products were lost or duplicated on removal");
            });advance();
        } else if(stage==19 && ready()) {
            System.out.println("BUILDCRAFT HEAT EXCHANGE CLIENT: native 3-block placement, cold/lava buckets, saved warmup, counterflow/fluid render, exact warm-oil tank output, warm distillation via live engine/MJ pipes, native wrench reversal, stale-port rejection, advancements and exact recovery; failures=0");
            stage=20;ticks=0;
        } else if(stage==20) {stage=21;client.scheduleStop();}
    }
    private static void place(net.minecraft.server.world.ServerWorld world,net.minecraft.server.network.ServerPlayerEntity player,BlockPos pos,Block block) {
        world.setBlockState(pos.down(),Blocks.STONE.getDefaultState());world.setBlockState(pos,Blocks.AIR.getDefaultState());
        player.setStackInHand(Hand.MAIN_HAND,new ItemStack(block));
        var hit=new BlockHitResult(Vec3d.ofCenter(pos.down()).add(0,.5,0),Direction.UP,pos.down(),false);
        require(player.interactionManager.interactBlock(player,world,player.getMainHandStack(),Hand.MAIN_HAND,hit).isAccepted(),"Native machine placement failed");
    }
    private static TileHeatExchange heat(MinecraftClient client,BlockPos pos){return (TileHeatExchange)client.getServer().getOverworld().getBlockEntity(pos);}
    private static void select(MinecraftClient client,int slot) {
        client.player.getInventory().selectedSlot=slot;client.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
    }
    private static void interact(MinecraftClient client,BlockPos pos) {
        client.interactionManager.interactBlock(client.player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(pos),Direction.SOUTH,pos,false));
    }
    private static void screenshot(MinecraftClient client,String name) {
        net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(client.runDirectory,name,client.getFramebuffer(),message->{});
    }
    private void advance(){stage++;ticks=0;System.out.println("BUILDCRAFT HEAT EXCHANGE CLIENT stage="+stage);}
    private boolean ready(){if(work==null || !work.isDone() || ticks<15)return false;work.join();return true;}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
