/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import buildcraft.api.enums.*;
import buildcraft.api.mj.*;
import buildcraft.api.properties.BuildCraftProperties;
import buildcraft.builders.*;
import buildcraft.builders.gui.GuiBuilder;
import buildcraft.builders.container.ContainerBuilder;
import buildcraft.builders.snapshot.*;
import buildcraft.builders.tile.TileBuilder;
import buildcraft.core.*;
import buildcraft.core.marker.*;
import buildcraft.core.tile.*;
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
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.world.gen.*;
import net.minecraft.world.level.LevelInfo;

public final class BuilderClientSmoke implements ClientModInitializer {
    private static final BlockPos POS=new BlockPos(2,-56,2),A=POS.south(),B=A.east(3),C=B.south(2),ENGINE=POS.west(3),VOLUME=POS.east(12);
    private static final List<BlockPos> PATH=List.of(A,B,C);
    private int stage,ticks;private long deadline;private String saveName;private CompletableFuture<Void> work;private boolean reverseAgain;
    @Override public void onInitializeClient(){if(Boolean.getBoolean("buildcraft.builderClientSmoke"))ClientTickEvents.END_CLIENT_TICK.register(this::tick);}
    private void tick(MinecraftClient client){
        if(stage==0){if(client.getOverlay()!=null || !(client.currentScreen instanceof TitleScreen))return;client.options.pauseOnLostFocus=false;client.options.getViewDistance().setValue(6);client.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);deadline=System.nanoTime()+360_000_000_000L;saveName="builder-smoke-"+System.currentTimeMillis();stage=1;var rules=new GameRules();rules.get(GameRules.DO_MOB_SPAWNING).set(false,null);client.createIntegratedServerLoader().createAndStart(saveName,new LevelInfo("BuildCraft Builder — Remake by FelixMantra",GameMode.CREATIVE,false,Difficulty.PEACEFUL,true,rules,DataConfiguration.SAFE_MODE),new GeneratorOptions(4754L,false,false),registries->registries.get(RegistryKeys.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createDimensionsRegistryHolder());return;}
        if(stage==40)return;if(System.nanoTime()>deadline)throw new IllegalStateException("Builder client timeout stage "+stage);
        if(stage==11 || stage==20){if(client.getServer()!=null || client.world!=null)return;stage++;ticks=0;client.createIntegratedServerLoader().start(new TitleScreen(),saveName);return;}
        if(stage>1 && stage!=12 && stage!=21 && client.currentScreen instanceof net.minecraft.client.gui.screen.DisconnectedScreen)throw new IllegalStateException("Builder disconnected stage "+stage);
        if(client.world==null || client.player==null || client.getServer()==null)return;ticks++;if(ticks%20==0)client.getToastManager().clear();
        if(stage==1){work=client.getServer().submit(()->{var w=client.getServer().getOverworld();w.setTimeOfDay(6000);w.setBlockState(POS.down(),Blocks.STONE.getDefaultState());marker(w,A);marker(w,B);player(client).getInventory().clear();player(client).getInventory().setStack(0,new ItemStack(BCCoreItems.markerConnector));player(client).getInventory().selectedSlot=0;aimLine(client,Vec3d.ofCenter(A).lerp(Vec3d.ofCenter(B),.5));});advance();}
        else if(stage==2 && ready() && ticks>25){client.interactionManager.interactItem(client.player,Hand.MAIN_HAND);advance();}
        else if(stage==3 && ticks>25){work=client.getServer().submit(()->{var w=client.getServer().getOverworld();require(((TileMarkerPath)w.getBlockEntity(A)).getPath().size()==2,"Native connector failed first path segment");require(player(client).getAdvancementTracker().getProgress(w.getServer().getAdvancementLoader().get(new Identifier("buildcraftcore:path_markers"))).isDone(),"Path connection advancement missing");marker(w,C);aimLine(client,Vec3d.ofCenter(B).lerp(Vec3d.ofCenter(C),.5));});advance();}
        else if(stage==4 && ready() && ticks>25){client.interactionManager.interactItem(client.player,Hand.MAIN_HAND);advance();}
        else if(stage==5 && ticks>25){work=client.getServer().submit(()->{require(((TileMarkerPath)client.getServer().getOverworld().getBlockEntity(A)).getPath().size()==3,"Native connector failed bend");player(client).getInventory().clear();player(client).currentScreenHandler.sendContentUpdates();aim(client,A);});advance();}
        else if(stage==6 && ready() && ticks>25){interact(client,A);advance();}
        else if(stage==7 && ticks>20){work=client.getServer().submit(()->{var path=((TileMarkerPath)client.getServer().getOverworld().getBlockEntity(A)).getPath();reverseAgain=!path.get(0).equals(A);});advance();}
        else if(stage==8 && ready() && ticks>10){if(reverseAgain)interact(client,A);advance();}
        else if(stage==9 && ticks>20){work=client.getServer().submit(()->{
            var w=client.getServer().getOverworld();require(((TileMarkerPath)w.getBlockEntity(A)).getPath().equals(PATH),"Native direction reversal did not restore expected order");
            for(var p:List.of(VOLUME,VOLUME.east(2),VOLUME.south(2))){w.setBlockState(p.down(),Blocks.STONE.getDefaultState());w.setBlockState(p,BCCoreBlocks.markerVolume.getDefaultState().with(BCCoreBlocks.markerVolume.getFacingProperty(),Direction.UP));}

            var blueprint=new Blueprint();blueprint.size=new BlockPos(1,1,1);blueprint.offset=BlockPos.ORIGIN;blueprint.facing=Direction.NORTH;blueprint.data=new int[]{0};var p=POS.down();blueprint.palette.add(SchematicBlockManager.getSchematicBlock(new buildcraft.api.schematics.SchematicBlockContext(w,p,p,w.getBlockState(p),Blocks.STONE)));blueprint.computeKey();
            player(client).getInventory().setStack(9,used(client,blueprint));player(client).currentScreenHandler.sendContentUpdates();aim(client,A.east());
        });advance();}
        else if(stage==10 && ready() && ticks>20){client.getServer().submit(()->{var marker=(TileMarkerVolume)client.getServer().getOverworld().getBlockEntity(VOLUME);marker.onManualConnectionAttempt(player(client));require(marker.getCurrentConnection()!=null,"Volume fixture did not connect before save");}).join();shot(client,"builder-connected-path-markers.png");stage=11;ticks=0;client.world.disconnect();client.disconnect(new TitleScreen());}
        else if(stage==12 && ticks>35){work=client.getServer().submit(()->{var w=client.getServer().getOverworld();require(((TileMarkerPath)w.getBlockEntity(A)).getPath().equals(PATH),"Full restart lost ordered path connection");require(((TileMarkerVolume)w.getBlockEntity(VOLUME)).getCurrentConnection()!=null,"Full restart lost volume marker connection");player(client).getInventory().setStack(0,new ItemStack(BCBuildersBlocks.builder));player(client).getInventory().setStack(10,new ItemStack(Items.STONE,6));player(client).getInventory().selectedSlot=0;player(client).currentScreenHandler.sendContentUpdates();aim(client,POS);});advance();}
        else if(stage==13 && ready() && ticks>25){client.interactionManager.interactBlock(client.player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(POS.down()).add(0,.5,0),Direction.UP,POS.down(),false));advance();}
        else if(stage==14 && ticks>25){work=client.getServer().submit(()->{require(tile(client).path.equals(PATH) && tile(client).getPathCount()==6,"Native builder placement failed path adoption");for(var p:PATH)require(client.getServer().getOverworld().isAir(p),"Native adoption left marker");player(client).getInventory().setStack(0,ItemStack.EMPTY);player(client).currentScreenHandler.sendContentUpdates();aim(client,POS);});advance();}
        else if(stage==15 && ready() && ticks>25){interact(client,POS);advance();}
        else if(stage==16 && ticks>20){require(client.currentScreen instanceof GuiBuilder,"Native builder screen missing");require(((ContainerBuilder)client.player.currentScreenHandler).tile.getPathCount()==6,"Native path progress not synchronized");quickMove(client,BCBuildersItems.snapshot);quickMove(client,Items.STONE);advance();}
        else if(stage==17 && ticks>20){work=client.getServer().submit(()->{require(count(tile(client),Items.STONE)==6 && tile(client).getBlueprintBuildingInfo()!=null,"Native GUI did not supply plan/materials");try(var tx=net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()){var tanks=tile(client).getTankManager().getTanks();tanks.get(0).insert(net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant.of(net.minecraft.fluid.Fluids.WATER),81_000,tx);tanks.get(1).insert(net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant.of(net.minecraft.fluid.Fluids.LAVA),162_000,tx);tx.commit();}input(tile(client)).receivePower(15*MjAPI.MJ,false);});shot(client,"builder-native-path-gui.png");client.player.closeHandledScreen();advance();}
        else if(stage==18 && ready() && ticks>25){var t=(TileBuilder)client.world.getBlockEntity(POS);require(t.getPathIndex()==1 && t.getBuilder().clientWork().stream().anyMatch(v->v.item().isOf(Items.STONE) && v.progress()>0),"Paid path material/robot animation missing");require(t.getTankManager().getTanks().get(0).getClientAmount()==1000 && t.getTankManager().getTanks().get(1).getClientAmount()==2000 && t.getTankManager().getTanks().get(2).getClientAmount()==0,"Mixed empty/nonempty tank flags lost during status sync");shot(client,"builder-paid-path-placement.png");advance();}
        else if(stage==19 && ticks>5){stage=20;ticks=0;client.world.disconnect();client.disconnect(new TitleScreen());}
        else if(stage==21 && ticks>35){work=client.getServer().submit(()->{var t=tile(client);require(t.getTankManager().getTanks().get(0).getStorage().getAmount()==81_000 && t.getTankManager().getTanks().get(1).getStorage().getAmount()==162_000,"Builder restart lost tank fluids");long paid=0;for(var tag:t.getBuilder().serializeNBT().getList("work",10))paid+=((net.minecraft.nbt.NbtCompound)tag).getLong("power");require(t.path.equals(PATH) && t.getPathIndex()==1 && paid==5*MjAPI.MJ && count(t,Items.STONE)==5,"Restart lost path position or paid work");var w=client.getServer().getOverworld();for(int i=1;i<=2;i++){w.setBlockState(POS.west(i),BCTransportBlocks.pipeHolder.getDefaultState());((TilePipeHolder)w.getBlockEntity(POS.west(i))).onPlacedBy(null,new ItemStack(i==2?BCTransportItems.pipePowerWood:BCTransportItems.pipePowerStone));}w.setBlockState(ENGINE.down(),Blocks.REDSTONE_BLOCK.getDefaultState());w.setBlockState(ENGINE,BCCoreBlocks.engine.getDefaultState().with(BuildCraftProperties.ENGINE_TYPE,EnumEngineType.CREATIVE));((TileEngineCreative)w.getBlockEntity(ENGINE)).currentOutputIndex=4;aim(client,A.east());});advance();}
        else if(stage==22 && ready()){
            if(!client.getServer().submit(()->tile(client).isFinished()).join()){require(ticks<350,"Engine-driven path did not finish");return;}
            work=client.getServer().submit(()->{var t=tile(client);var w=client.getServer().getOverworld();for(var p:steps())require(w.getBlockState(p).isOf(Blocks.STONE),"Path left a gap");require(count(t,Items.STONE)==0,"Path lost/duplicated materials");require(player(client).getAdvancementTracker().getProgress(w.getServer().getAdvancementLoader().get(new Identifier("buildcraftbuilders:paving_the_way"))).isDone(),"Path completion advancement missing");w.setBlockState(ENGINE,Blocks.AIR.getDefaultState());w.setBlockState(POS.west(),Blocks.AIR.getDefaultState());w.setBlockState(POS.west(2),Blocks.AIR.getDefaultState());t.getBattery().extractPower(0,Long.MAX_VALUE);w.setBlockState(A,Blocks.OBSIDIAN.getDefaultState());var clear=new Template();clear.size=new BlockPos(1,1,1);clear.offset=BlockPos.ORIGIN;clear.facing=Direction.NORTH;clear.data=new BitSet();clear.computeKey();player(client).getInventory().clear();player(client).getInventory().setStack(9,used(client,clear));player(client).currentScreenHandler.sendContentUpdates();aim(client,POS);});shot(client,"builder-engine-completed-path.png");advance();
        }else if(stage==23 && ready() && ticks>25){interact(client,POS);advance();}
        else if(stage==24 && ticks>20){require(client.currentScreen instanceof GuiBuilder,"Builder did not reopen");client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId,36,0,net.minecraft.screen.slot.SlotActionType.QUICK_MOVE,client.player);quickMove(client,BCBuildersItems.snapshot);advance();}
        else if(stage==25 && ticks>20){work=client.getServer().submit(()->{require(tile(client).snapshotType==EnumSnapshotType.TEMPLATE,"Native blueprint replacement failed");input(tile(client)).receivePower(256*MjAPI.MJ,false);});client.player.closeHandledScreen();advance();}
        else if(stage==26 && ready() && ticks>25){var t=(TileBuilder)client.world.getBlockEntity(POS);require(t.getBuilder().clientWork().stream().anyMatch(v->v.breaking() && v.pos().equals(A) && v.progress()>0),"Builder mining beam missing");shot(client,"builder-native-robot-mining-beam.png");work=client.getServer().submit(()->{input(tile(client)).receivePower(10000*MjAPI.MJ,false);});advance();}
        else if(stage==27 && ready()){
            if(!client.getServer().submit(()->tile(client).isFinished()).join()){require(ticks<200,"Path clearing did not finish");return;}
            work=client.getServer().submit(()->{var t=tile(client);require(count(t,Items.OBSIDIAN)==1 && count(t,Items.COBBLESTONE)==5,"Path clearing lost/duplicated native loot");for(var p:steps())require(client.getServer().getOverworld().isAir(p),"Clearing skipped path cell");aim(client,POS);});advance();
        }else if(stage==28 && ready() && ticks>25){interact(client,POS);advance();}
        else if(stage==29 && ticks>20){require(client.currentScreen instanceof GuiBuilder,"Final builder inventory unavailable");shot(client,"builder-final-mined-inventory.png");System.out.println("BUILDCRAFT BUILDER CLIENT: native connector and reversal packets, persistent path/volume markers, native builder placement and GUI, ordered bend traversal, paid material/robot visual, second restart with exact index/MJ, engine/power-pipe supply, template replacement, mining beam and exact loot; failures=0");stage=40;client.scheduleStop();}
    }
    private static void marker(net.minecraft.server.world.ServerWorld world,BlockPos pos){world.setBlockState(pos.down(),Blocks.STONE.getDefaultState());world.setBlockState(pos,BCCoreBlocks.markerPath.getDefaultState().with(BCCoreBlocks.markerPath.getFacingProperty(),Direction.UP));}
    private static List<BlockPos> steps(){List<BlockPos> result=new ArrayList<>();result.add(A);result.addAll(buildcraft.lib.misc.PositionUtil.getAllOnPath(A,B));result.addAll(buildcraft.lib.misc.PositionUtil.getAllOnPath(B,C));return result;}
    private static ItemStack used(MinecraftClient client,Snapshot snapshot){require(GlobalSavedDataSnapshots.get(client.getServer().getOverworld()).addSnapshot(snapshot),"Cannot save client fixture snapshot");return BCBuildersItems.snapshot.getUsed(snapshot.getType(),new Snapshot.Header(snapshot.key,player(client).getUuid(),new Date(),"Path — Remake by FelixMantra"));}
    private static TileBuilder tile(MinecraftClient client){return (TileBuilder)client.getServer().getOverworld().getBlockEntity(POS);}
    private static IMjReceiver input(TileBuilder tile){return (IMjReceiver)MjAPI.CONNECTOR.find(tile.getWorld(),tile.getPos(),Direction.UP);}
    private static int count(TileBuilder tile,Item item){int n=0;for(int i=0;i<27;i++)if(tile.invResources.getStackInSlot(i).isOf(item))n+=tile.invResources.getStackInSlot(i).getCount();return n;}
    private static net.minecraft.server.network.ServerPlayerEntity player(MinecraftClient client){return client.getServer().getPlayerManager().getPlayer(client.player.getUuid());}
    private static void quickMove(MinecraftClient client,Item item){var h=client.player.currentScreenHandler;for(int i=0;i<h.slots.size();i++)if(h.getSlot(i).inventory==client.player.getInventory() && h.getSlot(i).getStack().isOf(item)){client.interactionManager.clickSlot(h.syncId,i,0,net.minecraft.screen.slot.SlotActionType.QUICK_MOVE,client.player);return;}throw new IllegalStateException("Missing GUI input "+item);}
    private static void interact(MinecraftClient client,BlockPos at){client.interactionManager.interactBlock(client.player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(at).add(0,0,-.5),Direction.NORTH,at,false));}
    private static void aim(MinecraftClient client,BlockPos at){look(client,Vec3d.ofCenter(at).add(1,2,-3),Vec3d.ofCenter(at));}
    private static void aimLine(MinecraftClient client,Vec3d at){look(client,at.add(0,1,-2),at);}
    private static void look(MinecraftClient client,Vec3d eye,Vec3d at){var p=player(client);var dir=at.subtract(eye).normalize();p.teleport(p.getServerWorld(),eye.x,eye.y-p.getEyeHeight(p.getPose()),eye.z,(float)Math.toDegrees(Math.atan2(-dir.x,dir.z)),(float)-Math.toDegrees(Math.asin(dir.y)));p.setHeadYaw(p.getYaw());p.getAbilities().flying=true;p.sendAbilitiesUpdate();}
    private static void shot(MinecraftClient client,String name){net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(client.runDirectory,name,client.getFramebuffer(),message->{});}
    private void advance(){stage++;ticks=0;System.out.println("BUILDCRAFT BUILDER CLIENT stage="+stage);}
    private boolean ready(){if(work==null || !work.isDone() || ticks<5)return false;work.join();return true;}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
