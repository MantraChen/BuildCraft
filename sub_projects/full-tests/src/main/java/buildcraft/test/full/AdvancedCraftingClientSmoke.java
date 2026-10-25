/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import buildcraft.api.mj.*;
import buildcraft.api.properties.BuildCraftProperties;
import buildcraft.core.BCCoreBlocks;
import buildcraft.silicon.*;
import buildcraft.silicon.container.ContainerAdvancedCraftingTable;
import buildcraft.silicon.gui.GuiAdvancedCraftingTable;
import buildcraft.silicon.tile.*;
import buildcraft.transport.*;
import buildcraft.transport.tile.TilePipeHolder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.*;
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

/** Real ghost clicks, searchable recipes, paid-work restart and native laser production. */
public final class AdvancedCraftingClientSmoke implements ClientModInitializer {
    private static final BlockPos POS=new BlockPos(2,-60,2),LASER=POS.east(3);
    private int stage,ticks;private long deadline;private String saveName;private CompletableFuture<Void> work;
    @Override public void onInitializeClient(){if(Boolean.getBoolean("buildcraft.advancedCraftingClientSmoke"))ClientTickEvents.END_CLIENT_TICK.register(this::tick);}
    private void tick(MinecraftClient client) {
        if(stage==0) {
            if(client.getOverlay()!=null || !(client.currentScreen instanceof TitleScreen))return;
            client.options.pauseOnLostFocus=false;client.options.getViewDistance().setValue(6);client.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
            deadline=System.nanoTime()+300_000_000_000L;saveName="advanced-crafting-smoke-"+System.currentTimeMillis();stage=1;
            var rules=new GameRules();rules.get(GameRules.DO_MOB_SPAWNING).set(false,null);
            client.createIntegratedServerLoader().createAndStart(saveName,new LevelInfo("BuildCraft advanced crafting — Remake by FelixMantra",GameMode.CREATIVE,false,Difficulty.PEACEFUL,true,rules,DataConfiguration.SAFE_MODE),
                new GeneratorOptions(4754L,false,false),registries->registries.get(RegistryKeys.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createDimensionsRegistryHolder());return;
        }
        if(stage==20)return;
        if(System.nanoTime()>deadline)throw new IllegalStateException("Advanced crafting client timed out at stage "+stage);
        if(stage==7) {if(client.getServer()!=null || client.world!=null)return;stage=8;ticks=0;client.createIntegratedServerLoader().start(new TitleScreen(),saveName);return;}
        if(client.world==null || client.player==null || client.getServer()==null)return;
        ticks++;if(ticks%20==0)client.getToastManager().clear();
        if(stage==1) {
            work=client.getServer().submit(()->{
                var world=client.getServer().getOverworld();var player=world.getServer().getPlayerManager().getPlayer(client.player.getUuid());world.setTimeOfDay(1000);player.teleport(world,2.5,-60,-.5,0,15);
                world.setBlockState(POS.down(),Blocks.STONE.getDefaultState());world.setBlockState(POS,Blocks.AIR.getDefaultState());player.setStackInHand(Hand.MAIN_HAND,new ItemStack(BCSiliconBlocks.advancedCraftingTable));
                require(player.interactionManager.interactBlock(player,world,player.getMainHandStack(),Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(POS.down()).add(0,.5,0),Direction.UP,POS.down(),false)).isAccepted(),"Native advanced table placement failed");
                player.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);player.changeGameMode(GameMode.SURVIVAL);player.getInventory().setStack(9,new ItemStack(Items.OAK_PLANKS,8));player.currentScreenHandler.sendContentUpdates();
                player.unlockRecipes(List.of(world.getRecipeManager().get(new Identifier("minecraft:chest")).orElseThrow()));
            });advance();
        } else if(stage==2 && ready()){open(client);advance();}
        else if(stage==3 && ticks>20 && client.currentScreen instanceof GuiAdvancedCraftingTable) {
            var menu=menu(client);require(menu.slots.size()==70,"Original advanced inventory layout missing");click(client,ContainerAdvancedCraftingTable.PLAYER_START,SlotActionType.PICKUP);
            click(client,ContainerAdvancedCraftingTable.BLUEPRINT_START,SlotActionType.PICKUP);click(client,ContainerAdvancedCraftingTable.BLUEPRINT_START+3,SlotActionType.PICKUP);advance();
        } else if(stage==4 && ticks>25) {
            var menu=menu(client);require(menu.getCursorStack().getCount()==8 && menu.getSlot(0).getStack().isOf(Items.STICK) && menu.tile.invBlueprint.stacks.stream().filter(s->s.isOf(Items.OAK_PLANKS)).count()==2,"Native ghost editing consumed cursor or preview failed");
            click(client,0,SlotActionType.PICKUP);click(client,ContainerAdvancedCraftingTable.PLAYER_START,SlotActionType.PICKUP);click(client,ContainerAdvancedCraftingTable.PLAYER_START,SlotActionType.QUICK_MOVE);advance();
        } else if(stage==5 && ticks>30) {
            require(menu(client).getCursorStack().isEmpty() && menu(client).tile.invMaterials.stacks.stream().mapToInt(ItemStack::getCount).sum()==8 && menu(client).tile.invResults.stacks.stream().allMatch(ItemStack::isEmpty),"Preview stealing, material loss or passive crafting");
            work=client.getServer().submit(()->require(tile(client).receiveLaserPower(250*MjAPI.MJ)==0,"Advanced table rejected half craft"));advance();
        } else if(stage==6 && ready() && ticks>30) {
            require(menu(client).getProgress()==.5f && menu(client).tile.power==250*MjAPI.MJ,"Native advanced progress did not sync");screenshot(client,"advanced-partial-work.png");
            stage=7;ticks=0;client.player.closeHandledScreen();client.world.disconnect();client.disconnect(new TitleScreen());
        } else if(stage==8 && ticks>25) {
            work=client.getServer().submit(()->{
                var world=client.getServer().getOverworld();var table=tile(client);require(table.power==250*MjAPI.MJ && table.getRecipePreview().isOf(Items.STICK) && table.invMaterials.stacks.stream().mapToInt(ItemStack::getCount).sum()==8,"Full world restart lost paid work/template/materials");
                world.setBlockState(LASER,BCSiliconBlocks.laser.getDefaultState().with(BuildCraftProperties.BLOCK_FACING_6,Direction.WEST));
                for(int i=1;i<=2;i++){var at=LASER.east(i);world.setBlockState(at,BCTransportBlocks.pipeHolder.getDefaultState());((TilePipeHolder)world.getBlockEntity(at)).onPlacedBy(null,new ItemStack(i==2?BCTransportItems.pipePowerWood:BCTransportItems.pipePowerStone));}
                var engine=LASER.east(3);world.setBlockState(engine.down(),Blocks.REDSTONE_BLOCK.getDefaultState());world.setBlockState(engine,BCCoreBlocks.engine.getDefaultState().with(BuildCraftProperties.ENGINE_TYPE,buildcraft.api.enums.EnumEngineType.CREATIVE));((buildcraft.core.tile.TileEngineCreative)world.getBlockEntity(engine)).currentOutputIndex=4;
            });advance();
        } else if(stage==9 && ready() && ticks>45) {
            var laser=(TileLaser)client.world.getBlockEntity(LASER);require(laser.laserPos!=null && laser.getLastTransfer()>0,"Native laser did not target advanced crafting table");screenshot(client,"advanced-laser-production.png");advance();
        } else if(stage==10 && ticks>600) {open(client);advance();}
        else if(stage==11 && ticks>25 && client.currentScreen instanceof GuiAdvancedCraftingTable) {
            require(menu(client).tile.invResults.stacks.stream().filter(s->s.isOf(Items.STICK)).mapToInt(ItemStack::getCount).sum()==16 && menu(client).tile.power==0,"Laser chain did not craft exactly sixteen sticks");
            click(client,ContainerAdvancedCraftingTable.OUTPUT_START,SlotActionType.QUICK_MOVE);
            var screen=(GuiAdvancedCraftingTable)client.currentScreen;var toggle=(ButtonWidget)screen.children().stream().filter(child->child instanceof ButtonWidget b && b.getMessage().getString().equals("R")).findFirst().orElseThrow();screen.mouseClicked(toggle.getX()+8,toggle.getY()+8,0);
            var search=(TextFieldWidget)screen.children().stream().filter(TextFieldWidget.class::isInstance).findFirst().orElseThrow();screen.mouseClicked(search.getX()+5,search.getY()+5,0);
            for(char chr:"minecraft:chest".toCharArray())screen.charTyped(chr,0);screen.mouseClicked(search.getX()+21,search.getY()+40,0);advance();
        } else if(stage==12 && ticks>25) {
            require(menu(client).getSlot(0).getStack().isOf(Items.CHEST) && menu(client).tile.invBlueprint.stacks.stream().filter(s->s.isOf(Items.OAK_PLANKS)).count()==8,"Native recipe search failed to synchronize chest template");
            screenshot(client,"advanced-recipe-search.png");work=client.getServer().submit(()->{
                var player=client.getServer().getPlayerManager().getPlayer(client.player.getUuid());player.getInventory().setStack(9,new ItemStack(Items.OAK_PLANKS,8));player.currentScreenHandler.sendContentUpdates();
            });advance();
        } else if(stage==13 && ready()){click(client,ContainerAdvancedCraftingTable.PLAYER_START,SlotActionType.QUICK_MOVE);advance();}
        else if(stage==14 && ticks>180) {
            require(menu(client).tile.invResults.getStackInSlot(0).isOf(Items.CHEST) && menu(client).tile.invResults.getStackInSlot(0).getCount()==1 && menu(client).tile.invMaterials.stacks.stream().allMatch(ItemStack::isEmpty),"Selected chest recipe did not consume eight planks");
            screenshot(client,"advanced-crafted-output.png");client.player.closeHandledScreen();advance();
        } else if(stage==15 && ticks>30) {
            var laser=(TileLaser)client.world.getBlockEntity(LASER);require(laser.getLastTransfer()==0 && laser.laserPos==null,"Advanced table retained a ghost beam");
            work=client.getServer().submit(()->{
                var world=client.getServer().getOverworld();var table=tile(client);var player=world.getServer().getPlayerManager().getPlayer(client.player.getUuid());
                require(player.getInventory().main.stream().filter(s->s.isOf(Items.STICK)).mapToInt(ItemStack::getCount).sum()==16,"Output shift-click lost sticks");
                var stale=net.fabricmc.fabric.api.transfer.v1.item.ItemStorage.SIDED.find(world,POS,Direction.UP);player.changeGameMode(GameMode.CREATIVE);require(player.interactionManager.tryBreakBlock(POS),"Native advanced table removal failed");
                require(table.receiveLaserPower(MjAPI.MJ)==MjAPI.MJ,"Removed advanced table accepted work");
                try(var tx=net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()){require(stale.insert(net.fabricmc.fabric.api.transfer.v1.item.ItemVariant.of(Items.OAK_PLANKS),1,tx)==0,"Removed advanced table accepted materials");}
                var drops=world.getEntitiesByClass(net.minecraft.entity.ItemEntity.class,new Box(POS).expand(1),e->true);
                require(drops.stream().filter(e->e.getStack().isOf(Items.CHEST)).mapToInt(e->e.getStack().getCount()).sum()==1 && drops.stream().noneMatch(e->e.getStack().isOf(Items.OAK_PLANKS)),"Breaking lost output or materialized ghosts");
            });stage=19;ticks=0;
        } else if(stage==19 && ready()) {
            System.out.println("BUILDCRAFT ADVANCED CRAFTING CLIENT: native placement/ghost clicks/preview protection/shift-click, 250 MJ world restart, live engine/MJ pipes/laser, sixteen sticks and one searched chest, exact material consumption, beam stop and native removal; failures=0");stage=20;client.scheduleStop();
        }
    }
    private static TileAdvancedCraftingTable tile(MinecraftClient client){return (TileAdvancedCraftingTable)client.getServer().getOverworld().getBlockEntity(POS);}
    private static ContainerAdvancedCraftingTable menu(MinecraftClient client){return (ContainerAdvancedCraftingTable)client.player.currentScreenHandler;}
    private static void click(MinecraftClient client,int slot,SlotActionType type){client.interactionManager.clickSlot(menu(client).syncId,slot,0,type,client.player);}
    private static void open(MinecraftClient client){client.interactionManager.interactBlock(client.player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(POS).add(0,-.2,0),Direction.NORTH,POS,false));}
    private static void screenshot(MinecraftClient client,String name){net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(client.runDirectory,name,client.getFramebuffer(),message->{});}
    private void advance(){stage++;ticks=0;System.out.println("BUILDCRAFT ADVANCED CRAFTING CLIENT stage="+stage);}
    private boolean ready(){if(work==null || !work.isDone() || ticks<15)return false;work.join();return true;}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
