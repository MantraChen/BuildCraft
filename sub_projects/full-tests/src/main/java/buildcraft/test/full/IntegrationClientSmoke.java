/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;

import java.util.concurrent.CompletableFuture;
import buildcraft.api.enums.EnumRedstoneChipset;
import buildcraft.api.mj.*;
import buildcraft.api.properties.BuildCraftProperties;
import buildcraft.core.BCCoreBlocks;
import buildcraft.silicon.*;
import buildcraft.silicon.container.ContainerIntegrationTable;
import buildcraft.silicon.gui.GuiIntegrationTable;
import buildcraft.silicon.tile.*;
import buildcraft.transport.*;
import buildcraft.transport.tile.TilePipeHolder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.item.*;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.world.gen.*;
import net.minecraft.world.level.LevelInfo;

/** Test-pack-only integration recipe, real inventory clicks, full restart and laser production. */
public final class IntegrationClientSmoke implements ClientModInitializer {
    private static final BlockPos POS=new BlockPos(2,-60,2),LASER=POS.east(3);
    private int stage,ticks;private long deadline;private String saveName;private CompletableFuture<Void> work;
    @Override public void onInitializeClient(){if(Boolean.getBoolean("buildcraft.integrationClientSmoke"))ClientTickEvents.END_CLIENT_TICK.register(this::tick);}
    private void tick(MinecraftClient client) {
        if(stage==0) {
            if(client.getOverlay()!=null || !(client.currentScreen instanceof TitleScreen))return;
            client.options.pauseOnLostFocus=false;client.options.getViewDistance().setValue(6);client.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
            deadline=System.nanoTime()+300_000_000_000L;saveName="integration-smoke-"+System.currentTimeMillis();stage=1;
            var rules=new GameRules();rules.get(GameRules.DO_MOB_SPAWNING).set(false,null);
            client.createIntegratedServerLoader().createAndStart(saveName,new LevelInfo("BuildCraft integration — Remake by FelixMantra",GameMode.CREATIVE,false,Difficulty.PEACEFUL,true,rules,DataConfiguration.SAFE_MODE),
                new GeneratorOptions(4754L,false,false),registries->registries.get(RegistryKeys.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createDimensionsRegistryHolder());return;
        }
        if(stage==20)return;
        if(System.nanoTime()>deadline)throw new IllegalStateException("Integration client timed out at stage "+stage);
        if(stage==8){if(client.getServer()!=null || client.world!=null)return;stage=9;ticks=0;client.createIntegratedServerLoader().start(new TitleScreen(),saveName);return;}
        if(client.world==null || client.player==null || client.getServer()==null)return;
        ticks++;if(ticks%20==0)client.getToastManager().clear();
        if(stage==1) {
            work=client.getServer().submit(()->{
                var world=client.getServer().getOverworld();var player=world.getServer().getPlayerManager().getPlayer(client.player.getUuid());world.setTimeOfDay(1000);player.teleport(world,2.5,-60,-.5,0,15);
                world.setBlockState(POS.down(),Blocks.STONE.getDefaultState());world.setBlockState(POS,Blocks.AIR.getDefaultState());player.setStackInHand(Hand.MAIN_HAND,new ItemStack(BCSiliconBlocks.integrationTable));
                require(player.interactionManager.interactBlock(player,world,player.getMainHandStack(),Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(POS.down()).add(0,.5,0),Direction.UP,POS.down(),false)).isAccepted(),"Native integration placement failed");
                player.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);player.changeGameMode(GameMode.SURVIVAL);
                var center=EnumRedstoneChipset.RED.getStack().copyWithCount(2);center.setCustomName(Text.literal("Felix component"));center.getOrCreateNbt().putInt("configuration",4754);
                player.getInventory().setStack(9,center);player.getInventory().setStack(10,new ItemStack(Items.IRON_INGOT,4));player.getInventory().setStack(11,new ItemStack(Items.REDSTONE,2));player.currentScreenHandler.sendContentUpdates();
            });advance();
        } else if(stage==2 && ready()){open(client);advance();}
        else if(stage==3 && ticks>20 && client.currentScreen instanceof GuiIntegrationTable) {
            require(menu(client).slots.size()==47,"Integration inventory layout missing");
            for(int i=0;i<3;i++)click(client,ContainerIntegrationTable.PLAYER_START+i,SlotActionType.QUICK_MOVE);advance();
        } else if(stage==4 && ticks>25) {
            var menu=menu(client);
            require(menu.tile.invTarget.getStackInSlot(0).getCount()==2 && menu.tile.invToIntegrate.stacks.stream().mapToInt(ItemStack::getCount).sum()==6
                && menu.getSlot(ContainerIntegrationTable.PREVIEW).getStack().getNbt().getString("chipset").equals("iron") && menu.tile.costClient==500*MjAPI.MJ,"Center/component shift routing or preview/sync failed");
            click(client,ContainerIntegrationTable.PREVIEW,SlotActionType.PICKUP);
            work=client.getServer().submit(()->require(tile(client).receiveLaserPower(250*MjAPI.MJ)==0,"Integration rejected half craft"));advance();
        } else if(stage==5 && ready() && ticks>25) {
            require(menu(client).getCursorStack().isEmpty() && menu(client).tile.power==250*MjAPI.MJ && menu(client).tile.invResult.getStackInSlot(0).isEmpty(),"Preview materialized or paid work failed to sync");
            screenshot(client,"integration-partial-work.png");
            work=client.getServer().submit(()->{var table=tile(client);table.invResult.setStackInSlot(0,new ItemStack(Items.DIRT));require(table.receiveLaserPower(MjAPI.MJ)==MjAPI.MJ && table.power==250*MjAPI.MJ,"Blocked output accepted energy");});advance();
        } else if(stage==6 && ready() && ticks>25) {
            require(menu(client).tile.statusClient==3 && menu(client).tile.power==250*MjAPI.MJ && menu(client).tile.invTarget.getStackInSlot(0).getCount()==2,"Blocked state/input preservation did not sync");
            screenshot(client,"integration-blocked-output.png");click(client,ContainerIntegrationTable.OUTPUT,SlotActionType.QUICK_MOVE);advance();
        } else if(stage==7 && ticks>25) {
            require(menu(client).tile.statusClient==1 && menu(client).tile.invResult.getStackInSlot(0).isEmpty(),"Native removal did not unblock output");
            stage=8;ticks=0;client.player.closeHandledScreen();client.world.disconnect();client.disconnect(new TitleScreen());
        } else if(stage==9 && ticks>25) {
            work=client.getServer().submit(()->{
                var world=client.getServer().getOverworld();var table=tile(client);
                require(table.power==250*MjAPI.MJ && table.invTarget.getStackInSlot(0).getCount()==2 && table.getRecipePreview().getNbt().getInt("configuration")==4754,"Full restart lost work/center data/materials");
                world.setBlockState(LASER,BCSiliconBlocks.laser.getDefaultState().with(BuildCraftProperties.BLOCK_FACING_6,Direction.WEST));
                for(int i=1;i<=2;i++){var at=LASER.east(i);world.setBlockState(at,BCTransportBlocks.pipeHolder.getDefaultState());((TilePipeHolder)world.getBlockEntity(at)).onPlacedBy(null,new ItemStack(i==2?BCTransportItems.pipePowerWood:BCTransportItems.pipePowerStone));}
                var engine=LASER.east(3);world.setBlockState(engine.down(),Blocks.REDSTONE_BLOCK.getDefaultState());world.setBlockState(engine,BCCoreBlocks.engine.getDefaultState().with(BuildCraftProperties.ENGINE_TYPE,buildcraft.api.enums.EnumEngineType.CREATIVE));((buildcraft.core.tile.TileEngineCreative)world.getBlockEntity(engine)).currentOutputIndex=4;
            });advance();
        } else if(stage==10 && ready() && ticks>45) {
            var laser=(TileLaser)client.world.getBlockEntity(LASER);require(laser.laserPos!=null && laser.getLastTransfer()>0,"Native laser did not target integration table");screenshot(client,"integration-laser-production.png");advance();
        } else if(stage==11 && ticks>400){open(client);advance();}
        else if(stage==12 && ticks>25 && client.currentScreen instanceof GuiIntegrationTable) {
            var table=menu(client).tile;var output=table.invResult.getStackInSlot(0);
            require(output.getCount()==2 && output.getNbt().getString("chipset").equals("iron") && output.getName().getString().equals("Felix component")
                && output.getNbt().getInt("configuration")==4754 && table.invTarget.getStackInSlot(0).isEmpty() && table.invToIntegrate.stacks.stream().allMatch(ItemStack::isEmpty) && table.power==0,"Native laser integration lost NBT/counts or energy accounting");
            screenshot(client,"integration-crafted-output.png");click(client,ContainerIntegrationTable.OUTPUT,SlotActionType.QUICK_MOVE);advance();
        } else if(stage==13 && ticks>25) {
            require(menu(client).tile.invResult.getStackInSlot(0).isEmpty() && menu(client).tile.statusClient==0,"Output quick-move failed");client.player.closeHandledScreen();advance();
        } else if(stage==14 && ticks>25) {
            var laser=(TileLaser)client.world.getBlockEntity(LASER);require(laser.laserPos==null && laser.getLastTransfer()==0,"Integration retained a ghost beam");
            work=client.getServer().submit(()->{
                var world=client.getServer().getOverworld();var table=tile(client);var player=world.getServer().getPlayerManager().getPlayer(client.player.getUuid());
                require(player.getInventory().main.stream().filter(s->s.isOf(BCSiliconItems.redstoneChipset) && EnumRedstoneChipset.fromStack(s)==EnumRedstoneChipset.IRON).mapToInt(ItemStack::getCount).sum()==2,"Native output shift-click lost upgraded chipsets");
                var stale=net.fabricmc.fabric.api.transfer.v1.item.ItemStorage.SIDED.find(world,POS,Direction.UP);table.invTarget.setStackInSlot(0,EnumRedstoneChipset.RED.getStack());
                player.changeGameMode(GameMode.CREATIVE);require(player.interactionManager.tryBreakBlock(POS),"Native integration removal failed");
                require(table.receiveLaserPower(MjAPI.MJ)==MjAPI.MJ,"Removed integration target accepted work");
                try(var tx=net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()){require(stale.insert(net.fabricmc.fabric.api.transfer.v1.item.ItemVariant.of(Items.IRON_INGOT),1,tx)==0,"Stale integration storage accepted items");}
                var drops=world.getEntitiesByClass(net.minecraft.entity.ItemEntity.class,new Box(POS).expand(1),e->true);
                require(drops.stream().filter(e->e.getStack().isOf(BCSiliconItems.redstoneChipset)).mapToInt(e->e.getStack().getCount()).sum()==1,"Native breaking lost center item or duplicated preview");
            });stage=19;ticks=0;
        } else if(stage==19 && ready()) {
            System.out.println("BUILDCRAFT INTEGRATION CLIENT: native placement/center and component shift-clicks/protected preview, blocked output, 250 MJ full restart, engine/MJ pipes/laser, two exact NBT upgrades using test-pack recipe, beam stop/output extraction and native removal; failures=0");stage=20;client.scheduleStop();
        }
    }
    private static TileIntegrationTable tile(MinecraftClient client){return (TileIntegrationTable)client.getServer().getOverworld().getBlockEntity(POS);}
    private static ContainerIntegrationTable menu(MinecraftClient client){return (ContainerIntegrationTable)client.player.currentScreenHandler;}
    private static void click(MinecraftClient client,int slot,SlotActionType type){client.interactionManager.clickSlot(menu(client).syncId,slot,0,type,client.player);}
    private static void open(MinecraftClient client){client.interactionManager.interactBlock(client.player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(POS).add(0,-.2,0),Direction.NORTH,POS,false));}
    private static void screenshot(MinecraftClient client,String name){net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(client.runDirectory,name,client.getFramebuffer(),message->{});}
    private void advance(){stage++;ticks=0;System.out.println("BUILDCRAFT INTEGRATION CLIENT stage="+stage);}
    private boolean ready(){if(work==null || !work.isDone() || ticks<15)return false;work.join();return true;}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
