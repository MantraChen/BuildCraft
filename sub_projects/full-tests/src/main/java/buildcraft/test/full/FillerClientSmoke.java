/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;

import java.util.concurrent.CompletableFuture;
import buildcraft.api.enums.EnumEngineType;
import buildcraft.api.mj.*;
import buildcraft.api.properties.BuildCraftProperties;
import buildcraft.builders.*;
import buildcraft.builders.gui.GuiFiller;
import buildcraft.builders.container.ContainerFiller;
import buildcraft.builders.tile.TileFiller;
import buildcraft.core.*;
import buildcraft.core.tile.*;
import buildcraft.transport.*;
import buildcraft.transport.tile.TilePipeHolder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.*;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.world.gen.*;
import net.minecraft.world.level.LevelInfo;

/** Native marker/placement packets, actual GUI, restart, machine and pipe-driven construction. */
public final class FillerClientSmoke implements ClientModInitializer {
    private static final BlockPos POS=new BlockPos(2,-56,2),NEAR=POS.south(),ENGINE=POS.west(3);
    private int stage,ticks;private long deadline;private String saveName;private CompletableFuture<Void> work,reload;
    @Override public void onInitializeClient(){if(Boolean.getBoolean("buildcraft.fillerClientSmoke"))ClientTickEvents.END_CLIENT_TICK.register(this::tick);}
    private void tick(MinecraftClient client){
        if(stage==0){
            if(client.getOverlay()!=null || !(client.currentScreen instanceof TitleScreen))return;
            client.options.pauseOnLostFocus=false;client.options.getViewDistance().setValue(6);client.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
            deadline=System.nanoTime()+360_000_000_000L;saveName="filler-smoke-"+System.currentTimeMillis();stage=1;
            var rules=new GameRules();rules.get(GameRules.DO_MOB_SPAWNING).set(false,null);
            client.createIntegratedServerLoader().createAndStart(saveName,new LevelInfo("BuildCraft Filler — Remake by FelixMantra",GameMode.CREATIVE,false,Difficulty.PEACEFUL,true,rules,DataConfiguration.SAFE_MODE),new GeneratorOptions(4754L,false,false),registries->registries.get(RegistryKeys.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createDimensionsRegistryHolder());return;
        }
        if(stage==30)return;if(System.nanoTime()>deadline)throw new IllegalStateException("Filler client timeout stage "+stage);
        if(stage==11){if(client.getServer()!=null || client.world!=null)return;stage=12;ticks=0;client.createIntegratedServerLoader().start(new TitleScreen(),saveName);return;}
        if(stage>1 && stage!=12 && client.currentScreen instanceof net.minecraft.client.gui.screen.DisconnectedScreen)throw new IllegalStateException("Filler smoke disconnected at stage "+stage);
        if(client.world==null || client.player==null || client.getServer()==null)return;ticks++;if(ticks%20==0)client.getToastManager().clear();
        if(stage==1){
            work=client.getServer().submit(()->{
                var world=client.getServer().getOverworld();world.setTimeOfDay(6000);world.setBlockState(POS.down(),Blocks.STONE.getDefaultState());
                for(var p:java.util.List.of(NEAR,NEAR.south(2),NEAR.east(2))){world.setBlockState(p.down(),Blocks.STONE.getDefaultState());world.setBlockState(p,BCCoreBlocks.markerVolume.getDefaultState().with(BCCoreBlocks.markerVolume.getFacingProperty(),Direction.UP));}
                player(client).getInventory().clear();aim(client,NEAR);
            });advance();
        }else if(stage==2 && ready() && ticks>25){interact(client,NEAR);advance();}
        else if(stage==3 && ticks>25){
            work=client.getServer().submit(()->{
                var marker=(TileMarkerVolume)client.getServer().getOverworld().getBlockEntity(NEAR);require(marker.getCurrentConnection()!=null,"Native marker interaction did not connect");
                player(client).getInventory().setStack(0,new ItemStack(BCBuildersBlocks.filler));player(client).getInventory().selectedSlot=0;player(client).currentScreenHandler.sendContentUpdates();aim(client,POS);
            });advance();
        }else if(stage==4 && ready() && ticks>25){
            client.interactionManager.interactBlock(client.player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(POS.down()).add(0,.5,0),Direction.UP,POS.down(),false));advance();
        }else if(stage==5 && ticks>25){
            require(client.world.getBlockEntity(POS) instanceof TileFiller filler && filler.hasBox() && filler.box.size().equals(new BlockPos(3,1,3)),"Native filler placement/marker adoption failed");
            require(client.getNetworkHandler().getRecipeManager().get(new Identifier("buildcraftbuilders:filler")).isPresent(),"Survival filler recipe missing");
            require(new ItemStack(BCBuildersBlocks.filler).getTooltip(client.player,net.minecraft.client.item.TooltipContext.Default.BASIC).stream().anyMatch(t->t.getString().contains("Remake by FelixMantra")),"Filler credit missing");
            shot(client,"filler-native-marker-volume.png");
            work=client.getServer().submit(()->{player(client).getInventory().clear();player(client).getInventory().setStack(9,new ItemStack(Items.STONE,9));player(client).currentScreenHandler.sendContentUpdates();aim(client,POS);});advance();
        }else if(stage==6 && ready() && ticks>25){interact(client,POS);advance();}
        else if(stage==7 && ticks>20){
            require(client.currentScreen instanceof GuiFiller,"Native filler screen missing");
            var button=client.currentScreen.children().stream().filter(child->child instanceof ButtonWidget b && b.getWidth()==160).map(ButtonWidget.class::cast).findFirst().orElseThrow();
            button.onPress();button.onPress();
            var handler=(ContainerFiller)client.player.currentScreenHandler;int slot=-1;
            for(int i=0;i<handler.slots.size();i++)if(handler.getSlot(i).inventory==client.player.getInventory() && handler.getSlot(i).getStack().isOf(Items.STONE)){slot=i;break;}
            require(slot>=0,"Native player material slot missing");client.interactionManager.clickSlot(handler.syncId,slot,0,net.minecraft.screen.slot.SlotActionType.QUICK_MOVE,client.player);advance();
        }else if(stage==8 && ticks>25){
            work=client.getServer().submit(()->{var tile=tile(client);require(tile.patternStatement.get()==BCBuildersStatements.PATTERN_FILL && count(tile,Items.STONE)==9,"GUI pattern/shift transfer did not reach server");input(tile).receivePower(5*MjAPI.MJ,false);});
            shot(client,"filler-native-gui-materials.png");client.player.closeHandledScreen();advance();
        }else if(stage==9 && ready() && ticks>20){
            var tile=(TileFiller)client.world.getBlockEntity(POS);require(!tile.builder.clientWork().isEmpty() && !tile.builder.clientWork().get(0).item().isEmpty(),"Native material animation missing");
            if(reload==null){reload=client.reloadResources();return;}if(!reload.isDone() || client.getOverlay()!=null)return;reload.join();
            require(BCBuildersSprites.ROBOT.getSprite().getContents().getId().toString().equals("buildcraftbuilders:robot"),"Robot texture missing after resource reload");
            for(var pattern:BCBuildersStatements.PATTERNS){var holder=(buildcraft.lib.client.sprite.SpriteHolderRegistry.SpriteHolder)pattern.getSprite();require(holder.getSprite().getContents().getId().equals(holder.spriteLocation),"Filler gate pattern sprite missing: "+pattern.getUniqueTag());}
            shot(client,"filler-paid-placement.png");advance();
        }else if(stage==10 && ticks>5){stage=11;ticks=0;client.world.disconnect();client.disconnect(new TitleScreen());}
        else if(stage==12 && ticks>35){
            work=client.getServer().submit(()->{
                var tile=tile(client);long paid=0;for(var tag:tile.builder.serializeNBT().getList("work",10))paid+=((net.minecraft.nbt.NbtCompound)tag).getLong("power");
                require(paid==5*MjAPI.MJ && tile.getBattery().getStored()==0 && count(tile,Items.STONE)==9,"Restart lost paid placement/materials");
                var world=client.getServer().getOverworld();for(int i=1;i<=2;i++){world.setBlockState(POS.west(i),BCTransportBlocks.pipeHolder.getDefaultState());((TilePipeHolder)world.getBlockEntity(POS.west(i))).onPlacedBy(null,new ItemStack(i==2?BCTransportItems.pipePowerWood:BCTransportItems.pipePowerStone));}
                world.setBlockState(ENGINE.down(),Blocks.REDSTONE_BLOCK.getDefaultState());world.setBlockState(ENGINE,BCCoreBlocks.engine.getDefaultState().with(BuildCraftProperties.ENGINE_TYPE,EnumEngineType.CREATIVE));((TileEngineCreative)world.getBlockEntity(ENGINE)).currentOutputIndex=4;aim(client,NEAR.east());
            });advance();
        }else if(stage==13 && ready()){
            if(!client.getServer().submit(()->tile(client).isFinished()).join()){require(ticks<350,"Native engine/power pipes did not finish filler");return;}
            work=client.getServer().submit(()->{
                var tile=tile(client);var world=client.getServer().getOverworld();require(count(tile,Items.STONE)==0,"Filled volume retained materials");
                for(var p:BlockPos.iterate(tile.box.min(),tile.box.max()))require(world.getBlockState(p).isOf(Blocks.STONE),"Fill omitted native cell");
                world.setBlockState(ENGINE,Blocks.AIR.getDefaultState());world.setBlockState(POS.west(1),Blocks.AIR.getDefaultState());world.setBlockState(POS.west(2),Blocks.AIR.getDefaultState());tile.getBattery().extractPower(0,Long.MAX_VALUE);
                world.setBlockState(NEAR,Blocks.OBSIDIAN.getDefaultState());aim(client,POS);
            });shot(client,"filler-engine-completed-volume.png");advance();
        }else if(stage==14 && ready() && ticks>25){interact(client,POS);advance();}
        else if(stage==15 && ticks>20){
            require(client.currentScreen instanceof GuiFiller,"Filler screen did not reopen after restart");var handler=(ContainerFiller)client.player.currentScreenHandler;
            handler.getPatternStatementClient().set(BCBuildersStatements.PATTERN_CLEAR);handler.onStatementChange();client.player.closeHandledScreen();advance();
        }else if(stage==16 && ticks>20){
            work=client.getServer().submit(()->{require(tile(client).patternStatement.get()==BCBuildersStatements.PATTERN_CLEAR,"Clear setting did not synchronize");input(tile(client)).receivePower(256*MjAPI.MJ,false);aim(client,NEAR);});advance();
        }else if(stage==17 && ready() && ticks>25){
            var tile=(TileFiller)client.world.getBlockEntity(POS);require(tile.builder.clientWork().stream().anyMatch(w->w.breaking() && w.pos().equals(NEAR) && w.progress()>0),"Native robot mining beam missing");shot(client,"filler-robot-hardness-progress.png");
            work=client.getServer().submit(()->{input(tile(client)).receivePower(4000*MjAPI.MJ,false);});advance();
        }else if(stage==18 && ready()){
            if(!client.getServer().submit(()->tile(client).isFinished()).join()){require(ticks<150,"Clear did not finish");return;}
            work=client.getServer().submit(()->{var tile=tile(client);require(count(tile,Items.COBBLESTONE)==8 && count(tile,Items.OBSIDIAN)==1,"Clear duplicated/lost native loot");aim(client,POS);});advance();
        }else if(stage==19 && ready() && ticks>25){interact(client,POS);advance();}
        else if(stage==20 && ticks>20){
            require(client.currentScreen instanceof GuiFiller,"Final filler inventory screen missing");shot(client,"filler-native-mined-inventory.png");client.player.closeHandledScreen();
            System.out.println("BUILDCRAFT FILLER CLIENT: native marker/placement packets, GUI pattern and material quick-move, recipe/credit, resource reload, paid placement plus full restart, engine/power-pipe fill, robot/material/mining visuals and exact native clear loot; failures=0");stage=30;client.scheduleStop();
        }
    }
    private static TileFiller tile(MinecraftClient client){return (TileFiller)client.getServer().getOverworld().getBlockEntity(POS);}
    private static IMjReceiver input(TileFiller tile){return (IMjReceiver)MjAPI.CONNECTOR.find(tile.getWorld(),tile.getPos(),Direction.UP);}
    private static int count(TileFiller tile,Item item){int total=0;for(int i=0;i<27;i++)if(tile.invResources.getStackInSlot(i).isOf(item))total+=tile.invResources.getStackInSlot(i).getCount();return total;}
    private static net.minecraft.server.network.ServerPlayerEntity player(MinecraftClient client){return client.getServer().getPlayerManager().getPlayer(client.player.getUuid());}
    private static void interact(MinecraftClient client,BlockPos at){client.interactionManager.interactBlock(client.player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(at).add(0,0,-.5),Direction.NORTH,at,false));}
    private static void aim(MinecraftClient client,BlockPos at){
        var player=player(client);var eye=Vec3d.ofCenter(at).add(1,2,-3);var look=Vec3d.ofCenter(at).subtract(eye).normalize();
        player.teleport(player.getServerWorld(),eye.x,eye.y-player.getEyeHeight(player.getPose()),eye.z,(float)Math.toDegrees(Math.atan2(-look.x,look.z)),(float)-Math.toDegrees(Math.asin(look.y)));player.setHeadYaw(player.getYaw());player.getAbilities().flying=true;player.sendAbilitiesUpdate();
    }
    private static void shot(MinecraftClient client,String name){net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(client.runDirectory,name,client.getFramebuffer(),message->{});}
    private void advance(){stage++;ticks=0;System.out.println("BUILDCRAFT FILLER CLIENT stage="+stage);}
    private boolean ready(){if(work==null || !work.isDone() || ticks<5)return false;work.join();return true;}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
