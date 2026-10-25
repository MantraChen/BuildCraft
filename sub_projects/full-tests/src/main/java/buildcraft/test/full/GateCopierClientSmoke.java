/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;

import java.util.concurrent.CompletableFuture;
import buildcraft.api.statements.StatementParameterItemStack;
import buildcraft.core.BCCoreStatements;
import buildcraft.core.statements.StatementParamGateSideOnly;
import buildcraft.lib.statement.*;
import buildcraft.silicon.*;
import buildcraft.silicon.container.ContainerGate;
import buildcraft.silicon.gate.*;
import buildcraft.silicon.gui.GuiGate;
import buildcraft.silicon.item.ItemGateCopier;
import buildcraft.silicon.plug.PluggableGate;
import buildcraft.transport.*;
import buildcraft.transport.tile.TilePipeHolder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.world.gen.*;
import net.minecraft.world.level.LevelInfo;

/** Real native item packets, model predicates, open-screen synchronization and full world restart. */
public final class GateCopierClientSmoke implements ClientModInitializer {
    private static final BlockPos POS=new BlockPos(2,-56,2);
    private static final GateVariant FULL=new GateVariant(EnumGateLogic.AND,EnumGateMaterial.GOLD,EnumGateModifier.DIAMOND);
    private int stage,ticks;private long deadline;private String saveName;private NbtCompound expected;
    private CompletableFuture<Void> work,reload;private ContainerGate openMenu;
    @Override public void onInitializeClient(){if(Boolean.getBoolean("buildcraft.gateCopierClientSmoke"))ClientTickEvents.END_CLIENT_TICK.register(this::tick);}
    private void tick(MinecraftClient client){
        if(stage==0){
            if(client.getOverlay()!=null || !(client.currentScreen instanceof TitleScreen))return;
            client.options.pauseOnLostFocus=false;client.options.getViewDistance().setValue(6);client.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
            deadline=System.nanoTime()+240_000_000_000L;saveName="gate-copier-smoke-"+System.currentTimeMillis();stage=1;
            var rules=new GameRules();rules.get(GameRules.DO_MOB_SPAWNING).set(false,null);
            client.createIntegratedServerLoader().createAndStart(saveName,new LevelInfo("BuildCraft Gate Copier — Remake by FelixMantra",GameMode.CREATIVE,false,Difficulty.PEACEFUL,true,rules,DataConfiguration.SAFE_MODE),
                new GeneratorOptions(4754L,false,false),registries->registries.get(RegistryKeys.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createDimensionsRegistryHolder());return;
        }
        if(stage==30)return;
        if(System.nanoTime()>deadline)throw new IllegalStateException("Gate copier client timed out at stage "+stage);
        if(stage==12){if(client.getServer()!=null || client.world!=null)return;stage=13;ticks=0;client.createIntegratedServerLoader().start(new TitleScreen(),saveName);return;}
        if(client.world==null || client.player==null || client.getServer()==null)return;
        ticks++;if(ticks%20==0)client.getToastManager().clear();
        if(stage==1){
            work=client.getServer().submit(()->{
                var world=client.getServer().getOverworld();world.setTimeOfDay(6000);
                world.setBlockState(POS,BCTransportBlocks.pipeHolder.getDefaultState());var tile=tile(client);tile.onPlacedBy(null,new ItemStack(BCTransportItems.pipeItemStone));
                for(var face:new Direction[]{Direction.NORTH,Direction.SOUTH})tile.replacePluggable(face,new PluggableGate(BCSiliconPlugs.gate,tile,face,FULL));
                var sample=new ItemStack(Items.DIAMOND,7);sample.setCustomName(Text.literal("copier sample"));
                world.setBlockState(POS.west(),Blocks.BARREL.getDefaultState());((BarrelBlockEntity)world.getBlockEntity(POS.west())).setStack(0,sample.copy());
                var logic=gate(client,Direction.NORTH).logic;
                logic.statements[0].trigger.set(TriggerWrapper.wrap(BCCoreStatements.TRIGGER_INVENTORY_CONTAINS,Direction.WEST));logic.statements[0].trigger.set(0,new StatementParameterItemStack(sample));
                logic.statements[0].action.set(ActionWrapper.wrap(BCCoreStatements.ACTION_REDSTONE,null));logic.statements[0].action.set(0,StatementParamGateSideOnly.SPECIFIC);
                logic.statements[1].trigger.set(TriggerWrapper.wrap(BCCoreStatements.TRIGGER_TRUE,null));logic.statements[1].action.set(ActionWrapper.wrap(BCTransportStatements.ACTION_PIPE_SIGNAL[DyeColor.RED.ordinal()],null));logic.connections[0]=true;
                logic.statements[1].action.set(0,buildcraft.transport.statements.ActionParameterSignal.get(DyeColor.BLUE));
                logic.statements[2].trigger.set(TriggerWrapper.wrap(BCTransportStatements.TRIGGER_PIPE_SIGNAL[DyeColor.BLUE.ordinal()*2+1],null));
                logic.statements[2].trigger.set(0,buildcraft.transport.statements.TriggerParameterSignal.get(false,DyeColor.GREEN));
                expected=logic.writeConfigData();tile.markDirty();
                var stack=new ItemStack(BCSiliconItems.gateCopier);stack.setCustomName(Text.literal("Remake by FelixMantra"));stack.getOrCreateNbt().putString("owner_note","keep me");
                player(client).getInventory().setStack(0,stack);player(client).getInventory().selectedSlot=0;player(client).currentScreenHandler.sendContentUpdates();aim(player(client),Direction.NORTH);
            });advance();
        }else if(stage==2 && ready() && ticks>25){
            if(reload==null){checkModels(client);reload=client.reloadResources();return;}
            if(!reload.isDone() || client.getOverlay()!=null)return;reload.join();checkModels(client);interact(client,Direction.NORTH);advance();
        }else if(stage==3 && ticks>25){
            require(client.currentScreen==null && expected.equals(ItemGateCopier.getCopiedGateData(client.player.getMainHandStack())),"Native copy did not synchronize item data or opened GUI");
            require(modelTexture(client,client.player.getMainHandStack()).endsWith("gatecopier/full"),"Filled copier kept empty model");screenshot(client,"gate-copier-filled.png");
            work=client.getServer().submit(()->aim(player(client),Direction.SOUTH));advance();
        }else if(stage==4 && ready() && ticks>25){interact(client,Direction.SOUTH);advance();}
        else if(stage==5 && ticks>25){
            work=client.getServer().submit(()->require(gate(client,Direction.SOUTH).logic.writeConfigData().equals(expected),"Native paste lost parameters, sides or groups"));
            work.join();client.player.getInventory().selectedSlot=1;advance();
        }else if(stage==6 && ticks>25){interact(client,Direction.SOUTH);advance();}
        else if(stage==7 && ticks>25 && client.currentScreen instanceof GuiGate){
            openMenu=(ContainerGate)client.player.currentScreenHandler;require(openMenu.gate.logic.writeConfigData().equals(expected),"Opened GUI differs from pasted configuration");screenshot(client,"gate-copier-pasted-gui.png");
            work=client.getServer().submit(()->{var blank=new NbtCompound();blank.put("variant",FULL.writeToNBT());require(GateConfigCopier.paste(gate(client,Direction.SOUTH).logic,blank).applied(),"Observer setup failed");});advance();
        }else if(stage==8 && ready() && ticks>25){
            require(client.player.currentScreenHandler==openMenu && openMenu.gate.logic.statements[0].trigger.get()==null && !openMenu.gate.logic.connections[0],"Open GUI did not receive cleared rows/connections");
            work=client.getServer().submit(()->require(BCSiliconItems.gateCopier.interact(player(client).getInventory().getStack(0),player(client),gate(client,Direction.SOUTH)).isAccepted(),"Observer paste failed"));advance();
        }else if(stage==9 && ready() && ticks>25){
            require(client.player.currentScreenHandler==openMenu && openMenu.gate.logic.writeConfigData().equals(expected),"Live copier paste replaced screen or lost synchronization");screenshot(client,"gate-copier-live-gui.png");advance();
        }else if(stage==10 && ticks>15){client.player.closeHandledScreen();advance();}
        else if(stage==11 && ticks>15){stage=12;ticks=0;client.world.disconnect();client.disconnect(new TitleScreen());}
        else if(stage==13 && ticks>35){
            work=client.getServer().submit(()->{
                require(gate(client,Direction.SOUTH).logic.writeConfigData().equals(expected),"Restart lost pasted gate data");
                require(expected.equals(ItemGateCopier.getCopiedGateData(player(client).getInventory().getStack(0))),"Restart lost copied item data");aim(player(client),Direction.SOUTH);
            });advance();
        }else if(stage==14 && ready() && ticks>25){
            client.player.getInventory().selectedSlot=0;client.options.sneakKey.setPressed(true);advance();
        }else if(stage==15 && ticks>25){
            require(client.player.isSneaking(),"Native sneak input did not activate");client.interactionManager.interactItem(client.player,Hand.MAIN_HAND);advance();
        }else if(stage==16 && ticks>25){
            client.options.sneakKey.setPressed(false);var stack=client.player.getMainHandStack();
            require(!ItemGateCopier.hasCopiedGateData(stack) && stack.getName().getString().equals("Remake by FelixMantra") && stack.getNbt().getString("owner_note").equals("keep me"),"Native sneak clear lost custom NBT or failed to sync");
            require(modelTexture(client,stack).endsWith("gatecopier/empty"),"Cleared copier retained full model");
            work=client.getServer().submit(()->require(gate(client,Direction.SOUTH).logic.writeConfigData().equals(expected),"Clearing changed the pasted gate"));advance();
        }else if(stage==17 && ready() && ticks>25){
            screenshot(client,"gate-copier-cleared.png");System.out.println("BUILDCRAFT GATE COPIER CLIENT: native copy/paste packets and empty/full models, reload, named item parameters and sides/groups, live open-screen synchronization, complete saved-world restart, native sneak clear preserving custom item NBT; failures=0");stage=30;client.scheduleStop();
        }
    }
    private static void checkModels(MinecraftClient client){
        var empty=new ItemStack(BCSiliconItems.gateCopier);var full=empty.copy();var data=new NbtCompound();data.put("variant",FULL.writeToNBT());ItemGateCopier.setCopiedGateData(full,data);
        require(modelTexture(client,empty).endsWith("gatecopier/empty") && modelTexture(client,full).endsWith("gatecopier/full"),"Copier predicate/texture model missing");
        require(empty.getTooltip(client.player,net.minecraft.client.item.TooltipContext.Default.BASIC).stream().anyMatch(t->t.getString().contains("Remake by FelixMantra")),"Copier credit absent");
    }
    private static String modelTexture(MinecraftClient client,ItemStack stack){var model=client.getItemRenderer().getModel(stack,client.world,client.player,0);require(model!=client.getBakedModelManager().getMissingModel(),"Missing copier model");return model.getParticleSprite().getContents().getId().getPath();}
    private static net.minecraft.server.network.ServerPlayerEntity player(MinecraftClient client){return client.getServer().getPlayerManager().getPlayer(client.player.getUuid());}
    private static TilePipeHolder tile(MinecraftClient client){return (TilePipeHolder)client.getServer().getOverworld().getBlockEntity(POS);}
    private static PluggableGate gate(MinecraftClient client,Direction face){return (PluggableGate)tile(client).getPluggable(face);}
    private static void aim(net.minecraft.server.network.ServerPlayerEntity player,Direction side){
        var eye=Vec3d.ofCenter(POS).add(Vec3d.of(side.getVector()).multiply(3));var look=Vec3d.of(side.getVector()).multiply(-1);
        player.teleport(player.getServerWorld(),eye.x,eye.y-player.getEyeHeight(player.getPose()),eye.z,(float)Math.toDegrees(Math.atan2(-look.x,look.z)),(float)-Math.toDegrees(Math.asin(look.y)));
        player.setHeadYaw(player.getYaw());player.getAbilities().flying=true;player.sendAbilitiesUpdate();
    }
    private static void interact(MinecraftClient client,Direction side){client.interactionManager.interactBlock(client.player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(POS).add(Vec3d.of(side.getVector()).multiply(.3125)),side,POS,false));}
    private static void screenshot(MinecraftClient client,String name){net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(client.runDirectory,name,client.getFramebuffer(),message->{});}
    private void advance(){stage++;ticks=0;System.out.println("BUILDCRAFT GATE COPIER CLIENT stage="+stage);}
    private boolean ready(){if(work==null || !work.isDone() || ticks<15)return false;work.join();return true;}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
