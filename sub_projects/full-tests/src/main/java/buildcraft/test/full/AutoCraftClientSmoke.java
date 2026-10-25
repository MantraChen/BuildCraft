/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import buildcraft.api.mj.*;
import buildcraft.core.BCCoreBlocks;
import buildcraft.factory.*;
import buildcraft.factory.container.ContainerAutoCraftItems;
import buildcraft.factory.gui.GuiAutoCraftItems;
import buildcraft.factory.tile.*;
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

/** Real native GUI packets, saved partial work, engine acceleration and searchable recipe selection. */
public final class AutoCraftClientSmoke implements ClientModInitializer {
    private static final BlockPos POS=new BlockPos(2,-60,2);
    private int stage,ticks;
    private long deadline;
    private volatile long paidAtSave;
    private String saveName;
    private CompletableFuture<Void> work;
    @Override public void onInitializeClient() {
        if(Boolean.getBoolean("buildcraft.autoCraftClientSmoke"))ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }
    private void tick(MinecraftClient client) {
        if(stage==0) {
            if(client.getOverlay()!=null || !(client.currentScreen instanceof TitleScreen))return;
            client.options.pauseOnLostFocus=false;client.options.getViewDistance().setValue(6);
            client.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
            deadline=System.nanoTime()+240_000_000_000L;saveName="autocraft-smoke-"+System.currentTimeMillis();stage=1;
            var rules=new GameRules();rules.get(GameRules.DO_MOB_SPAWNING).set(false,null);
            client.createIntegratedServerLoader().createAndStart(saveName,
                new LevelInfo("BuildCraft automatic workbench — Remake by FelixMantra",GameMode.CREATIVE,false,Difficulty.PEACEFUL,true,rules,DataConfiguration.SAFE_MODE),
                new GeneratorOptions(4754L,false,false),
                registries->registries.get(RegistryKeys.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createDimensionsRegistryHolder());
            return;
        }
        if(stage==16)return;
        if(System.nanoTime()>deadline)throw new IllegalStateException("Automatic workbench client timed out at stage "+stage);
        if(stage==7) {
            if(client.getServer()!=null || client.world!=null)return;
            stage=8;ticks=0;client.createIntegratedServerLoader().start(new TitleScreen(),saveName);return;
        }
        if(client.world==null || client.player==null || client.getServer()==null)return;
        ticks++;
        if(stage==5 && ticks==60 || stage==13 && ticks==115)client.getToastManager().clear();
        if(stage==1) {
            work=client.getServer().submit(()->{
                var world=client.getServer().getOverworld();var player=world.getServer().getPlayerManager().getPlayer(client.player.getUuid());
                world.setTimeOfDay(1000);player.teleport(world,2.5,-60,-.5,0,15);
                world.setBlockState(POS.down(),Blocks.STONE.getDefaultState());world.setBlockState(POS,Blocks.AIR.getDefaultState());
                player.setStackInHand(Hand.MAIN_HAND,new ItemStack(BCFactoryBlocks.autoWorkbenchItems));
                var hit=new BlockHitResult(Vec3d.ofCenter(POS.down()).add(0,.5,0),Direction.UP,POS.down(),false);
                require(player.interactionManager.interactBlock(player,world,player.getMainHandStack(),Hand.MAIN_HAND,hit).isAccepted(),"Native workbench placement failed");
                player.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);player.changeGameMode(GameMode.SURVIVAL);
                player.getInventory().setStack(9,new ItemStack(Items.OAK_LOG,8));player.currentScreenHandler.sendContentUpdates();
                player.unlockRecipes(List.of(world.getRecipeManager().get(new Identifier("minecraft:oak_planks")).orElseThrow(),
                    world.getRecipeManager().get(new Identifier("minecraft:stick")).orElseThrow()));
            });advance();
        } else if(stage==2 && ready()) {
            open(client);advance();
        } else if(stage==3) {
            if(!(client.currentScreen instanceof GuiAutoCraftItems) || !(client.player.currentScreenHandler instanceof ContainerAutoCraftItems handler) || ticks<20)return;
            require(handler.getSlot(ContainerAutoCraftItems.PLAYER_START).getStack().getCount()==8,"Native GUI did not receive player inventory");
            click(client,handler,ContainerAutoCraftItems.PLAYER_START,SlotActionType.PICKUP);
            click(client,handler,1,SlotActionType.PICKUP);
            click(client,handler,ContainerAutoCraftItems.PLAYER_START,SlotActionType.PICKUP);
            advance();
        } else if(stage==4 && ticks>25) {
            var handler=(ContainerAutoCraftItems)client.player.currentScreenHandler;
            require(handler.tile.invBlueprint.getStackInSlot(0).isOf(Items.OAK_LOG) && handler.getCursorStack().isEmpty()
                && handler.getSlot(ContainerAutoCraftItems.PLAYER_START).getStack().getCount()==8
                && handler.getSlot(ContainerAutoCraftItems.PREVIEW).getStack().getCount()==4,"Ghost pattern or server preview was not synchronized");
            click(client,handler,ContainerAutoCraftItems.PLAYER_START,SlotActionType.QUICK_MOVE);advance();
        } else if(stage==5 && ticks>65) {
            var handler=(ContainerAutoCraftItems)client.player.currentScreenHandler;
            require(handler.getProgress()>.1f && handler.getProgress()<.8f && handler.tile.invMaterials.stacks.stream().mapToInt(ItemStack::getCount).sum()==8,
                "Passive work/filter inventory did not synchronize");
            net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(client.runDirectory,"autocraft-progress.png",client.getFramebuffer(),message->{});
            work=client.getServer().submit(()->{
                var tile=(TileAutoWorkbenchItems)client.getServer().getOverworld().getBlockEntity(POS);paidAtSave=tile.getStoredPower();
                require(paidAtSave>10*MjAPI.MJ && paidAtSave<30*MjAPI.MJ,"Fixture did not save a partial craft");
            });advance();
        } else if(stage==6 && ready()) {
            stage=7;ticks=0;client.player.closeHandledScreen();client.world.disconnect();client.disconnect(new TitleScreen());
        } else if(stage==8 && ticks>10) {
            work=client.getServer().submit(()->{
                var world=client.getServer().getOverworld();var tile=(TileAutoWorkbenchItems)world.getBlockEntity(POS);
                int logs=tile.invMaterials.stacks.stream().filter(stack->stack.isOf(Items.OAK_LOG)).mapToInt(ItemStack::getCount).sum();
                int crafts=tile.invResult.getStackInSlot(0).getCount()/4;
                require(logs+crafts==8 && tile.getStoredPower()+crafts*TileAutoWorkbenchBase.POWER_REQUIRED>=paidAtSave,
                    "World restart lost partial work or duplicated material/output");
                for(int i=1;i<=2;i++) {
                    world.setBlockState(POS.east(i),BCTransportBlocks.pipeHolder.getDefaultState());
                    ((TilePipeHolder)world.getBlockEntity(POS.east(i))).onPlacedBy(null,new ItemStack(i==2?BCTransportItems.pipePowerWood:BCTransportItems.pipePowerStone));
                }
                world.setBlockState(POS.east(3).down(),Blocks.REDSTONE_BLOCK.getDefaultState());
                world.setBlockState(POS.east(3),BCCoreBlocks.engine.getDefaultState().with(buildcraft.api.properties.BuildCraftProperties.ENGINE_TYPE,buildcraft.api.enums.EnumEngineType.CREATIVE));
                ((buildcraft.core.tile.TileEngineCreative)world.getBlockEntity(POS.east(3))).currentOutputIndex=4;
            });advance();
        } else if(stage==9 && ready() && ticks>100) {
            open(client);advance();
        } else if(stage==10 && ticks>20) {
            if(!(client.currentScreen instanceof GuiAutoCraftItems))return;
            var handler=(ContainerAutoCraftItems)client.player.currentScreenHandler;
            require(handler.tile.invResult.getStackInSlot(0).isOf(Items.OAK_PLANKS) && handler.tile.invResult.getStackInSlot(0).getCount()==32,
                "Native engine/MJ pipes did not accelerate all eight crafts");
            click(client,handler,0,SlotActionType.QUICK_MOVE);advance();
        } else if(stage==11 && ticks>20) {
            var screen=(GuiAutoCraftItems)client.currentScreen;
            var toggle=(ButtonWidget)screen.children().stream().filter(child->child instanceof ButtonWidget b && b.getMessage().getString().equals("R")).findFirst().orElseThrow();
            screen.mouseClicked(toggle.getX()+8,toggle.getY()+8,0);
            var search=(TextFieldWidget)screen.children().stream().filter(TextFieldWidget.class::isInstance).findFirst().orElseThrow();
            screen.mouseClicked(search.getX()+5,search.getY()+5,0);
            for(char letter:"stick".toCharArray())screen.charTyped(letter,0);
            // First result in the filtered native recipe browser; server resolves and validates its ID.
            screen.mouseClicked(search.getX()+21,search.getY()+40,0);advance();
        } else if(stage==12 && ticks>25) {
            var handler=(ContainerAutoCraftItems)client.player.currentScreenHandler;
            require(handler.getSlot(ContainerAutoCraftItems.PREVIEW).getStack().isOf(Items.STICK)
                && handler.tile.invBlueprint.stacks.stream().filter(stack->stack.isOf(Items.OAK_PLANKS)).count()==2,
                "Searchable recipe selection did not synchronize the native pattern");
            int playerSlot=-1;
            for(int slot=ContainerAutoCraftItems.PLAYER_START;slot<handler.slots.size();slot++)
                if(handler.getSlot(slot).getStack().isOf(Items.OAK_PLANKS)){playerSlot=slot;break;}
            require(playerSlot>=0 && handler.getSlot(playerSlot).getStack().getCount()==32,"Shift-click did not preserve crafted planks");
            click(client,handler,playerSlot,SlotActionType.QUICK_MOVE);advance();
        } else if(stage==13 && ticks>120) {
            var handler=(ContainerAutoCraftItems)client.player.currentScreenHandler;
            require(handler.tile.invResult.getStackInSlot(0).isOf(Items.STICK) && handler.tile.invResult.getStackInSlot(0).getCount()==64,
                "Selected recipe did not craft exact 64 sticks");
            require(net.minecraft.text.Text.translatable("advancements.buildcraftfactory.lazy_crafting.remake_description").getString().contains("Remake by FelixMantra"),"Remake credit missing");
            net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(client.runDirectory,"autocraft-recipes.png",client.getFramebuffer(),message->{});
            work=client.getServer().submit(()->{
                var world=client.getServer().getOverworld();var tile=(TileAutoWorkbenchItems)world.getBlockEntity(POS);
                require(tile.invMaterials.stacks.stream().allMatch(ItemStack::isEmpty),"Final craft retained unconsumed materials");
                var player=world.getServer().getPlayerManager().getPlayer(client.player.getUuid());
                var advancement=world.getServer().getAdvancementLoader().get(new Identifier("buildcraftfactory:lazy_crafting"));
                require(player.getAdvancementTracker().getProgress(advancement).isDone(),"Native owner crafting advancement missing");
            });advance();
        } else if(stage==14 && ready()) {
            client.player.closeHandledScreen();
            work=client.getServer().submit(()->{
                var world=client.getServer().getOverworld();var player=world.getServer().getPlayerManager().getPlayer(client.player.getUuid());
                var input=(IMjReceiver)MjAPI.CONNECTOR.find(world,POS,Direction.UP);
                player.changeGameMode(GameMode.CREATIVE);
                require(player.interactionManager.tryBreakBlock(POS),"Native workbench removal failed");
                require(input.receivePower(MjAPI.MJ,false)==MjAPI.MJ,"Removed workbench accepted stale MJ");
                var drops=world.getEntitiesByClass(net.minecraft.entity.ItemEntity.class,new Box(POS).expand(1),entity->true);
                int sticks=drops.stream().filter(e->e.getStack().isOf(Items.STICK)).mapToInt(e->e.getStack().getCount()).sum();
                require(sticks==64 && drops.stream().noneMatch(e->e.getStack().isOf(Items.OAK_PLANKS)),"Removal lost output or dropped ghost ingredients");
            });advance();
        } else if(stage==15 && ready()) {
            System.out.println("BUILDCRAFT AUTOCRAFT CLIENT: native placement/screen, ghost clicks, exact shift-click, preview/filter/progress sync, saved paid work, engine/MJ pipes, searchable recipe selection, 8 logs -> 32 planks -> 64 sticks, advancement/removal; failures=0");
            stage=16;client.scheduleStop();
        }
    }
    private static void click(MinecraftClient client,ContainerAutoCraftItems handler,int slot,SlotActionType type) {
        client.interactionManager.clickSlot(handler.syncId,slot,0,type,client.player);
    }
    private static void open(MinecraftClient client) {
        client.interactionManager.interactBlock(client.player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(POS),Direction.NORTH,POS,false));
    }
    private void advance(){stage++;ticks=0;System.out.println("BUILDCRAFT AUTOCRAFT CLIENT stage="+stage);}
    private boolean ready(){if(work==null || !work.isDone() || ticks<15)return false;work.join();return true;}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
