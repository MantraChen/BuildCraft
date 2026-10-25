/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;

import java.util.concurrent.CompletableFuture;
import buildcraft.api.mj.*;
import buildcraft.core.BCCoreBlocks;
import buildcraft.energy.BCEnergyFluids;
import buildcraft.factory.*;
import buildcraft.factory.container.ContainerDistiller;
import buildcraft.factory.gui.GuiDistiller;
import buildcraft.factory.tile.TileDistiller_BC8;
import buildcraft.transport.*;
import buildcraft.transport.tile.TilePipeHolder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.item.*;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.world.gen.*;
import net.minecraft.world.level.LevelInfo;

/** Native placement, fluid GUI packets, world restart, live MJ network and bucket recovery. */
public final class DistillerClientSmoke implements ClientModInitializer {
    private static final BlockPos POS=new BlockPos(2,-60,2);
    private int stage,ticks;
    private long deadline;
    private String saveName;
    private CompletableFuture<Void> work;
    @Override public void onInitializeClient() {
        if(Boolean.getBoolean("buildcraft.distillerClientSmoke"))ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }
    private void tick(MinecraftClient client) {
        if(stage==0) {
            if(client.getOverlay()!=null || !(client.currentScreen instanceof TitleScreen))return;
            client.options.pauseOnLostFocus=false;client.options.getViewDistance().setValue(6);
            client.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
            deadline=System.nanoTime()+240_000_000_000L;saveName="distiller-smoke-"+System.currentTimeMillis();stage=1;
            var rules=new GameRules();rules.get(GameRules.DO_MOB_SPAWNING).set(false,null);
            client.createIntegratedServerLoader().createAndStart(saveName,
                new LevelInfo("BuildCraft distillery — Remake by FelixMantra",GameMode.CREATIVE,false,Difficulty.PEACEFUL,true,rules,DataConfiguration.SAFE_MODE),
                new GeneratorOptions(4754L,false,false),
                registries->registries.get(RegistryKeys.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createDimensionsRegistryHolder());
            return;
        }
        if(stage==16)return;
        if(System.nanoTime()>deadline)throw new IllegalStateException("Distiller client timed out at stage "+stage);
        if(stage==7) {
            if(client.getServer()!=null || client.world!=null)return;
            stage=8;ticks=0;client.createIntegratedServerLoader().start(new TitleScreen(),saveName);return;
        }
        if(client.world==null || client.player==null || client.getServer()==null)return;
        ticks++;
        if(ticks%20==0)client.getToastManager().clear();
        if(stage==1) {
            work=client.getServer().submit(()->{
                var world=client.getServer().getOverworld();var player=world.getServer().getPlayerManager().getPlayer(client.player.getUuid());
                world.setTimeOfDay(1000);player.teleport(world,2.5,-60,-.5,0,15);
                world.setBlockState(POS.down(),Blocks.STONE.getDefaultState());world.setBlockState(POS,Blocks.AIR.getDefaultState());
                player.setStackInHand(Hand.MAIN_HAND,new ItemStack(BCFactoryBlocks.distiller));
                var hit=new BlockHitResult(Vec3d.ofCenter(POS.down()).add(0,.5,0),Direction.UP,POS.down(),false);
                require(player.interactionManager.interactBlock(player,world,player.getMainHandStack(),Hand.MAIN_HAND,hit).isAccepted(),"Native distiller placement failed");
                player.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);player.changeGameMode(GameMode.SURVIVAL);
                player.getInventory().setStack(9,new ItemStack(BCEnergyFluids.crudeOil[0].getBucketItem()));
                player.currentScreenHandler.sendContentUpdates();
            });advance();
        } else if(stage==2 && ready()) {
            open(client);advance();
        } else if(stage==3) {
            if(!(client.currentScreen instanceof GuiDistiller) || ticks<20)return;
            var handler=(ContainerDistiller)client.player.currentScreenHandler;
            require(handler.getSlot(0).getStack().isOf(BCEnergyFluids.crudeOil[0].getBucketItem()),"Native inventory sync missing");
            require(handler.tile.getCachedState().getBlock().getName().getString().equals("Distiller")
                && net.minecraft.text.Text.translatable("advancements.buildcraftfactory.refining.title").getString().equals("Oil Refining")
                && net.minecraft.text.Text.translatable("advancements.buildcraftfactory.refining.remake_description").getString().contains("Remake by FelixMantra"),
                "Native distillery translations/credit missing");
            client.interactionManager.clickSlot(handler.syncId,0,0,SlotActionType.QUICK_MOVE,client.player);advance();
        } else if(stage==4 && ticks>25) {
            var handler=(ContainerDistiller)client.player.currentScreenHandler;
            require(handler.tile.tankIn.getClientAmount()==1000 && handler.getSlot(0).getStack().isOf(Items.BUCKET),"Oil shift-click did not sync exact bucket");
            work=client.getServer().submit(()->require(tile(client).receivePower(4*MjAPI.MJ,false)==0,"Distiller rejected partial MJ"));advance();
        } else if(stage==5 && ready() && ticks>100) {
            var handler=(ContainerDistiller)client.player.currentScreenHandler;
            require(handler.getProgress()==1250 && handler.tile.tankIn.getClientAmount()==1000,"Partial MJ progress did not sync");
            screenshot(client,"distiller-progress.png");
            work=client.getServer().submit(()->require(tile(client).getDistillPower()==4*MjAPI.MJ && tile(client).getStoredPower()==0,"Fixture did not stop with paid partial work"));
            advance();
        } else if(stage==6 && ready()) {
            stage=7;ticks=0;client.player.closeHandledScreen();client.world.disconnect();client.disconnect(new TitleScreen());
        } else if(stage==8 && ticks>15) {
            work=client.getServer().submit(()->{
                var world=client.getServer().getOverworld();var tile=tile(client);
                require(tile.getDistillPower()==4*MjAPI.MJ && tile.tankIn.getStorage().getAmount()==FluidConstants.BUCKET,"World restart lost paid work or oil");
                for(int i=1;i<=2;i++) {
                    world.setBlockState(POS.east(i),BCTransportBlocks.pipeHolder.getDefaultState());
                    ((TilePipeHolder)world.getBlockEntity(POS.east(i))).onPlacedBy(null,new ItemStack(i==2?BCTransportItems.pipePowerWood:BCTransportItems.pipePowerStone));
                }
                world.setBlockState(POS.east(3).down(),Blocks.REDSTONE_BLOCK.getDefaultState());
                world.setBlockState(POS.east(3),BCCoreBlocks.engine.getDefaultState().with(buildcraft.api.properties.BuildCraftProperties.ENGINE_TYPE,buildcraft.api.enums.EnumEngineType.CREATIVE));
                ((buildcraft.core.tile.TileEngineCreative)world.getBlockEntity(POS.east(3))).currentOutputIndex=4;
            });advance();
        } else if(stage==9 && ready() && ticks>850) {
            open(client);advance();
        } else if(stage==10 && ticks>25) {
            if(!(client.currentScreen instanceof GuiDistiller))return;
            var handler=(ContainerDistiller)client.player.currentScreenHandler;
            require(handler.tile.tankIn.getClientAmount()==0 && handler.tile.tankGasOut.getClientAmount()==2000
                && handler.tile.tankLiquidOut.getClientAmount()==375,"Native engine/pipes did not refine 1 bucket into exact 2 buckets gas and 375 mB heavy oil");
            screenshot(client,"distiller-refined.png");
            client.interactionManager.clickSlot(handler.syncId,0,0,SlotActionType.PICKUP,client.player);advance();
        } else if(stage==11 && ticks>20) {
            var handler=(ContainerDistiller)client.player.currentScreenHandler;
            require(handler.getCursorStack().isOf(Items.BUCKET),"Bucket cursor packet lost");
            var screen=(GuiDistiller)client.currentScreen;
            screen.mouseClicked((screen.width-176)/2+110,(screen.height-161)/2+18,0);advance();
        } else if(stage==12 && ticks>25) {
            var handler=(ContainerDistiller)client.player.currentScreenHandler;
            require(handler.getCursorStack().isOf(BCEnergyFluids.fuelGaseous[0].getBucketItem())
                && handler.tile.tankGasOut.getClientAmount()==1000,"Native tank widget did not return exact gas bucket");
            var screen=(GuiDistiller)client.currentScreen;
            screen.mouseClicked((screen.width-176)/2+110,(screen.height-161)/2+18,0);advance();
        } else if(stage==13 && ticks>25) {
            var handler=(ContainerDistiller)client.player.currentScreenHandler;
            require(handler.getCursorStack().isOf(BCEnergyFluids.fuelGaseous[0].getBucketItem()) && handler.tile.tankGasOut.getClientAmount()==1000,
                "Output GUI allowed reverse filling");
            client.interactionManager.clickSlot(handler.syncId,0,0,SlotActionType.PICKUP,client.player);
            client.player.closeHandledScreen();
            work=client.getServer().submit(()->{
                var world=client.getServer().getOverworld();var player=world.getServer().getPlayerManager().getPlayer(client.player.getUuid());
                var adv=world.getServer().getAdvancementLoader().get(new Identifier("buildcraftfactory:refining"));
                require(player.getAdvancementTracker().getProgress(adv).isDone(),"Native refining advancement missing");
            });advance();
        } else if(stage==14 && ready() && ticks>25) {
            screenshot(client,"distiller-world.png");
            work=client.getServer().submit(()->{
                var world=client.getServer().getOverworld();var player=world.getServer().getPlayerManager().getPlayer(client.player.getUuid());
                var mj=(IMjReceiver)MjAPI.CONNECTOR.find(world,POS,Direction.NORTH);
                require(player.getInventory().getStack(9).isOf(BCEnergyFluids.fuelGaseous[0].getBucketItem()),"Gas bucket inventory did not persist");
                player.changeGameMode(GameMode.CREATIVE);require(player.interactionManager.tryBreakBlock(POS),"Native distiller breaking failed");
                require(mj.receivePower(MjAPI.MJ,false)==MjAPI.MJ,"Stale machine accepted MJ");
                long amount=world.getEntitiesByClass(net.minecraft.entity.ItemEntity.class,new Box(POS).expand(1),e->true).stream()
                    .map(e->buildcraft.core.item.ItemFragileFluidContainer.getFluid(e.getStack())).mapToLong(f->f.amount).sum();
                require(amount==FluidConstants.BUCKET*1375/1000,"Removal lost or duplicated remaining tank contents");
            });advance();
        } else if(stage==15 && ready()) {
            System.out.println("BUILDCRAFT DISTILLER CLIENT: native placement/screen/buckets, saved 4 MJ, live engine/MJ pipes, 1000 mB oil -> 2000 mB gas + 375 mB heavy oil, output extraction, advancement, native fluid render/removal; failures=0");
            stage=16;client.scheduleStop();
        }
    }
    private static TileDistiller_BC8 tile(MinecraftClient client) { return (TileDistiller_BC8)client.getServer().getOverworld().getBlockEntity(POS); }
    private static void screenshot(MinecraftClient client,String name) {
        net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(client.runDirectory,name,client.getFramebuffer(),message->{});
    }
    private static void open(MinecraftClient client) {
        client.interactionManager.interactBlock(client.player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(POS),Direction.NORTH,POS,false));
    }
    private void advance(){stage++;ticks=0;System.out.println("BUILDCRAFT DISTILLER CLIENT stage="+stage);}
    private boolean ready(){if(work==null || !work.isDone() || ticks<15)return false;work.join();return true;}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
