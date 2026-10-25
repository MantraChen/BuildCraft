/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;

import java.util.concurrent.CompletableFuture;
import buildcraft.api.enums.EnumSnapshotType;
import buildcraft.api.mj.*;
import buildcraft.builders.*;
import buildcraft.builders.container.*;
import buildcraft.builders.gui.*;
import buildcraft.builders.tile.*;
import buildcraft.builders.snapshot.*;
import buildcraft.core.*;
import buildcraft.core.tile.TileMarkerVolume;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.*;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.world.gen.*;
import net.minecraft.world.level.LevelInfo;

public final class ArchitectClientSmoke implements ClientModInitializer {
    private static final BlockPos POS=new BlockPos(2,-56,2),NEAR=POS.south(),BUILDER=POS.west(4);
    private int stage,ticks; private long deadline;private String saveName;private CompletableFuture<Void> work;
    @Override public void onInitializeClient(){if(Boolean.getBoolean("buildcraft.architectClientSmoke"))ClientTickEvents.END_CLIENT_TICK.register(this::tick);}
    private void tick(MinecraftClient client){
        if(stage==0){
            if(client.getOverlay()!=null || !(client.currentScreen instanceof TitleScreen))return;
            client.options.pauseOnLostFocus=false;client.options.getViewDistance().setValue(6);client.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
            deadline=System.nanoTime()+240_000_000_000L;saveName="architect-smoke-"+System.currentTimeMillis();stage=1;
            var rules=new GameRules();rules.get(GameRules.DO_MOB_SPAWNING).set(false,null);
            client.createIntegratedServerLoader().createAndStart(saveName,new LevelInfo("BuildCraft Architect — Remake by FelixMantra",GameMode.CREATIVE,false,Difficulty.PEACEFUL,true,rules,DataConfiguration.SAFE_MODE),new GeneratorOptions(4754L,false,false),registries->registries.get(RegistryKeys.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createDimensionsRegistryHolder());return;
        }
        if(stage==30)return;if(System.nanoTime()>deadline)throw new IllegalStateException("Architect timeout at "+stage);
        if(stage==11){if(client.getServer()!=null || client.world!=null)return;stage=12;ticks=0;client.createIntegratedServerLoader().start(new TitleScreen(),saveName);return;}
        if(stage>1 && stage!=12 && client.currentScreen instanceof net.minecraft.client.gui.screen.DisconnectedScreen)throw new IllegalStateException("Architect disconnected at "+stage);
        if(client.world==null || client.player==null || client.getServer()==null)return;ticks++;if(ticks%20==0)client.getToastManager().clear();
        if(stage==1){work=client.getServer().submit(()->{var world=client.getServer().getOverworld();world.setTimeOfDay(6000);world.setBlockState(POS.down(),Blocks.STONE.getDefaultState());for(var p:java.util.List.of(NEAR,NEAR.south(2),NEAR.east(2))){world.setBlockState(p.down(),Blocks.STONE.getDefaultState());world.setBlockState(p,BCCoreBlocks.markerVolume.getDefaultState().with(BCCoreBlocks.markerVolume.getFacingProperty(),Direction.UP));}player(client).getInventory().clear();aim(client,NEAR);});advance();}
        else if(stage==2 && ready() && ticks>25){interact(client,NEAR);advance();}
        else if(stage==3 && ticks>25){work=client.getServer().submit(()->{require(((TileMarkerVolume)client.getServer().getOverworld().getBlockEntity(NEAR)).getCurrentConnection()!=null,"Marker interaction did not connect");player(client).getInventory().setStack(0,new ItemStack(BCBuildersBlocks.architect));player(client).getInventory().selectedSlot=0;player(client).currentScreenHandler.sendContentUpdates();aim(client,POS);});advance();}
        else if(stage==4 && ready() && ticks>25){client.interactionManager.interactBlock(client.player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(POS.down()).add(0,.5,0),Direction.UP,POS.down(),false));advance();}
        else if(stage==5 && ticks>25){
            require(client.world.getBlockEntity(POS) instanceof TileArchitectTable tile && tile.hasArea() && tile.box.size().equals(new BlockPos(3,1,3)),"Native architect marker placement failed");
            for(var type:EnumSnapshotType.values()){var recipe=client.getNetworkHandler().getRecipeManager().get(new Identifier("buildcraftbuilders:"+type.name().toLowerCase(java.util.Locale.ROOT))).orElseThrow();require(buildcraft.builders.item.ItemSnapshot.EnumItemSnapshotType.getFromStack(recipe.getOutput(client.world.getRegistryManager())).snapshotType==type,"Recipe sync lost snapshot type");}
            require(new ItemStack(BCBuildersBlocks.architect).getTooltip(client.player,net.minecraft.client.item.TooltipContext.Default.BASIC).stream().anyMatch(t->t.getString().contains("Remake by FelixMantra")),"Architect credit missing");
            work=client.getServer().submit(()->{var world=client.getServer().getOverworld();for(var p:BlockPos.iterate(NEAR,NEAR.add(2,0,2)))world.setBlockState(p,Blocks.STONE.getDefaultState());world.setBlockState(NEAR.add(1,0,1),Blocks.OAK_LOG.getDefaultState().with(PillarBlock.AXIS,Direction.Axis.X));player(client).getInventory().clear();player(client).getInventory().setStack(9,BCBuildersItems.snapshot.getClean(EnumSnapshotType.BLUEPRINT));player(client).currentScreenHandler.sendContentUpdates();aim(client,POS);});advance();
        }else if(stage==6 && ready() && ticks>25){interact(client,POS);advance();}
        else if(stage==7 && ticks>15){require(client.currentScreen instanceof GuiArchitectTable,"Native architect screen missing");var field=client.currentScreen.children().stream().filter(TextFieldWidget.class::isInstance).map(TextFieldWidget.class::cast).findFirst().orElseThrow();field.setFocused(true);field.setText("Felix 1.20.1");client.currentScreen.keyPressed(69,0,0);require(client.currentScreen instanceof GuiArchitectTable,"Inventory key closed focused name field");quickMove(client,BCBuildersItems.snapshot);advance();}
        else if(stage==8 && ticks>8){
            require(client.currentScreen instanceof GuiArchitectTable && ((ContainerArchitectTable)client.player.currentScreenHandler).tile.getScanStatus()==5,"Architect output/status did not synchronize");
            work=client.getServer().submit(()->{var tile=architect(client);var result=tile.invSnapshotOut.getStackInSlot(0);var header=BCBuildersItems.snapshot.getHeader(result);require(header!=null && header.name.equals("Felix 1.20.1") && GlobalSavedDataSnapshots.get(tile.getWorld()).getSnapshot(header.key) instanceof Blueprint,"Native name/scan did not persist");});shot(client,"architect-native-name-and-result.png");client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId,37,0,net.minecraft.screen.slot.SlotActionType.QUICK_MOVE,client.player);client.player.closeHandledScreen();advance();
        }else if(stage==9 && ready() && ticks>8){require(buildcraft.builders.client.ClientArchitectTables.BOXES.size()>0,"Architect volume renderer cache empty");shot(client,"architect-native-scan-area.png");advance();}
        else if(stage==10 && ticks>5){stage=11;ticks=0;client.world.disconnect();client.disconnect(new TitleScreen());}
        else if(stage==12 && ticks>35){work=client.getServer().submit(()->{
            var tile=architect(client);require(tile.hasArea() && tile.name.equals("Felix 1.20.1") && tile.invSnapshotIn.getStackInSlot(0).isEmpty() && tile.invSnapshotOut.getStackInSlot(0).isEmpty(),"Restart lost architect state or duplicated output");
            var inventory=player(client).getInventory();boolean found=false;for(int i=0;i<inventory.size();i++){var header=BCBuildersItems.snapshot.getHeader(inventory.getStack(i));if(header!=null){found=true;require(GlobalSavedDataSnapshots.get(tile.getWorld()).getSnapshot(header.key) instanceof Blueprint,"Blueprint content missing after full restart");}}require(found,"Blueprint missing after restart");
            var world=client.getServer().getOverworld();for(var p:BlockPos.iterate(BUILDER.south(),BUILDER.add(2,0,3)))world.setBlockState(p,Blocks.AIR.getDefaultState());world.setBlockState(BUILDER,BCBuildersBlocks.builder.getDefaultState().with(BCBuildersBlocks.builder.getFacingProperty(),Direction.NORTH));var builder=(TileBuilder)world.getBlockEntity(BUILDER);builder.onPlacedBy(player(client),new ItemStack(BCBuildersBlocks.builder));builder.invResources.setStackInSlot(0,new ItemStack(Items.STONE,8));builder.invResources.setStackInSlot(1,new ItemStack(Items.OAK_LOG));aim(client,BUILDER);
        });advance();}
        else if(stage==13 && ready() && ticks>25){interact(client,BUILDER);advance();}
        else if(stage==14 && ticks>20){require(client.currentScreen instanceof GuiBuilder,"Native builder screen missing");quickMove(client,BCBuildersItems.snapshot);advance();}
        else if(stage==15 && ticks>20){shot(client,"architect-blueprint-in-native-builder.png");work=client.getServer().submit(()->{var builder=builder(client);require(builder.getBlueprintBuildingInfo()!=null,"Native builder rejected scanned blueprint");((IMjReceiver)MjAPI.CONNECTOR.find(builder.getWorld(),BUILDER,Direction.UP)).receivePower(500*MjAPI.MJ,false);});client.player.closeHandledScreen();advance();}
        else if(stage==16 && ready() && ticks>25){
            work=client.getServer().submit(()->{var builder=builder(client);require(builder.isFinished(),"Native builder did not finish scanned building");var world=client.getServer().getOverworld();for(var p:BlockPos.iterate(BUILDER.south(),BUILDER.add(2,0,3))){var expected=p.equals(BUILDER.add(1,0,2))?Blocks.OAK_LOG.getDefaultState().with(PillarBlock.AXIS,Direction.Axis.X):Blocks.STONE.getDefaultState();require(world.getBlockState(p)==expected,"Blueprint changed reconstructed block/state at "+p);}long cost=0;for(var p:BlockPos.iterate(BUILDER.south(),BUILDER.add(2,0,3)))cost+=Math.max(MjAPI.MJ,(long)(Math.sqrt(p.getSquaredDistance(BUILDER))*10*MjAPI.MJ));require(builder.getBattery().getStored()==500*MjAPI.MJ-cost,"Builder used wrong distance-based energy cost");for(int i=0;i<27;i++)require(builder.invResources.getStackInSlot(i).isEmpty(),"Builder retained/duplicated material");aim(client,BUILDER.south().east());});advance();
        }else if(stage==17 && ready() && ticks>25){shot(client,"architect-reconstructed-building.png");System.out.println("BUILDCRAFT ARCHITECT CLIENT: native marker connection/placement, recipe NBT sync, native name field and inventory-key focus, blueprint quick move/capture/output, area rendering, full save/restart, native builder GUI and exact block-state reconstruction; failures=0");stage=30;client.scheduleStop();}
    }
    private static TileArchitectTable architect(MinecraftClient client){return (TileArchitectTable)client.getServer().getOverworld().getBlockEntity(POS);}
    private static TileBuilder builder(MinecraftClient client){return (TileBuilder)client.getServer().getOverworld().getBlockEntity(BUILDER);}
    private static net.minecraft.server.network.ServerPlayerEntity player(MinecraftClient client){return client.getServer().getPlayerManager().getPlayer(client.player.getUuid());}
    private static void quickMove(MinecraftClient client,Item item){var h=client.player.currentScreenHandler;for(int i=0;i<h.slots.size();i++)if(h.getSlot(i).inventory==client.player.getInventory() && h.getSlot(i).getStack().isOf(item)){client.interactionManager.clickSlot(h.syncId,i,0,net.minecraft.screen.slot.SlotActionType.QUICK_MOVE,client.player);return;}throw new IllegalStateException("Missing input item "+item);}
    private static void interact(MinecraftClient client,BlockPos at){client.interactionManager.interactBlock(client.player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(at).add(0,0,-.5),Direction.NORTH,at,false));}
    private static void aim(MinecraftClient client,BlockPos at){var p=player(client);var eye=Vec3d.ofCenter(at).add(1,2,-3);var look=Vec3d.ofCenter(at).subtract(eye).normalize();p.teleport(p.getServerWorld(),eye.x,eye.y-p.getEyeHeight(p.getPose()),eye.z,(float)Math.toDegrees(Math.atan2(-look.x,look.z)),(float)-Math.toDegrees(Math.asin(look.y)));p.setHeadYaw(p.getYaw());p.getAbilities().flying=true;p.sendAbilitiesUpdate();}
    private static void shot(MinecraftClient client,String name){net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(client.runDirectory,name,client.getFramebuffer(),message->{});}
    private void advance(){stage++;ticks=0;System.out.println("BUILDCRAFT ARCHITECT CLIENT stage="+stage);}
    private boolean ready(){if(work==null || !work.isDone() || ticks<5)return false;work.join();return true;}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
