/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import buildcraft.api.mj.*;
import buildcraft.builders.*;
import buildcraft.builders.tile.TileQuarry;
import buildcraft.core.*;
import buildcraft.core.tile.TileMarkerVolume;
import buildcraft.lib.chunkload.ChunkLoaderManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.*;
import net.minecraft.block.entity.BarrelBlockEntity;
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

/** Actual native placement, quarry animation, world restart and distant persistent chunk tickets. */
public final class QuarryClientSmoke implements ClientModInitializer {
    private static final BlockPos POS=new BlockPos(512,-60,512);
    private int stage,ticks,renderedFrames;
    private long deadline,paidWork,balance;
    private String saveName;
    private CompletableFuture<Void> work;
    private final Set<BlockPos> drillPositions=new HashSet<>();
    @Override public void onInitializeClient() {
        if(!Boolean.getBoolean("buildcraft.quarryClientSmoke"))return;
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        WorldRenderEvents.END.register(context->{
            if(stage==7 && MinecraftClient.getInstance().world.getBlockEntity(POS) instanceof TileQuarry tile && tile.clientDrillPos!=null)
                renderedFrames++;
        });
    }
    private void tick(MinecraftClient client) {
        if(stage==0) {
            if(client.getOverlay()!=null || !(client.currentScreen instanceof TitleScreen))return;
            client.options.pauseOnLostFocus=false;
            client.options.getViewDistance().setValue(6);
            client.options.getSimulationDistance().setValue(5);
            deadline=System.nanoTime()+240_000_000_000L;
            saveName="quarry-smoke-"+System.currentTimeMillis();
            var rules=new GameRules();rules.get(GameRules.DO_MOB_SPAWNING).set(false,null);
            stage=1;
            client.createIntegratedServerLoader().createAndStart(saveName,
                new LevelInfo("BuildCraft quarry — Remake by FelixMantra",GameMode.CREATIVE,false,Difficulty.PEACEFUL,true,rules,DataConfiguration.SAFE_MODE),
                new GeneratorOptions(4754L,false,false),
                registries->registries.get(RegistryKeys.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createDimensionsRegistryHolder());
            return;
        }
        if(stage==9)return;
        if(System.nanoTime()>deadline)throw new IllegalStateException("Quarry client smoke timed out at stage "+stage);
        if(stage==4) {
            if(client.getServer()!=null || client.world!=null)return;
            stage=5;ticks=0;
            client.createIntegratedServerLoader().start(new TitleScreen(),saveName);
            return;
        }
        if(client.world==null || client.player==null || client.getServer()==null)return;
        ticks++;
        if(stage==1) {
            work=client.getServer().submit(()->{
                var world=client.getServer().getOverworld();
                var player=world.getServer().getPlayerManager().getPlayer(client.player.getUuid());
                for(int x=31;x<=33;x++)for(int z=31;z<=33;z++)world.getChunk(x,z);
                world.setTimeOfDay(1000);
                player.changeGameMode(GameMode.CREATIVE);
                player.getAbilities().flying=true;player.sendAbilitiesUpdate();
                player.teleport(world,POS.getX()+1.5,-56,POS.getZ()-7,0,35);
                var marker=POS.south();
                for(var p:List.of(marker,marker.south(3),marker.east(3))) {
                    world.setBlockState(p.down(),Blocks.STONE.getDefaultState());
                    world.setBlockState(p,BCCoreBlocks.markerVolume.getDefaultState().with(BCCoreBlocks.markerVolume.getFacingProperty(),Direction.UP));
                    ((TileMarkerVolume)world.getBlockEntity(p)).onLoad();
                }
                ((TileMarkerVolume)world.getBlockEntity(marker)).onManualConnectionAttempt(player);
                world.setBlockState(POS.down(),Blocks.STONE.getDefaultState());
                world.setBlockState(POS.west(),Blocks.BARREL.getDefaultState());
                player.setStackInHand(Hand.MAIN_HAND,new ItemStack(BCBuildersBlocks.quarry));
                var hit=new BlockHitResult(Vec3d.ofCenter(POS.down()).add(0,.5,0),Direction.UP,POS.down(),false);
                require(player.interactionManager.interactBlock(player,world,player.getMainHandStack(),Hand.MAIN_HAND,hit).isAccepted(),"Native quarry item placement failed");
                player.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);
                var tile=(TileQuarry)world.getBlockEntity(POS);
                require(tile!=null && tile.frameBox.size().equals(new BlockPos(4,5,4)),"Native placement lost marker bounds");
                for(int x=1;x<=2;x++)for(int z=2;z<=3;z++) {
                    world.setBlockState(POS.add(x,-1,z),Blocks.DIAMOND_BLOCK.getDefaultState());
                    world.setBlockState(POS.add(x,-2,z),Blocks.GOLD_BLOCK.getDefaultState());
                }
                var advancement=world.getServer().getAdvancementLoader().get(new Identifier("buildcraftbuilders:shaping_the_world"));
                require(advancement!=null && player.getAdvancementTracker().getProgress(advancement).isDone(),"Native quarry placement did not grant advancement");
                power(tile,10*MjAPI.MJ);
            });
            advance();
        } else if(stage==2 && ready()) {
            if(!(client.world.getBlockEntity(POS) instanceof TileQuarry tile) || !tile.frameBox.isInitialized() || ticks<60)return;
            require(tile.frameBox.size().equals(new BlockPos(4,5,4)),"Client quarry render bounds missing");
            work=client.getServer().submit(()->{
                var server=client.getServer();var world=server.getOverworld();var quarry=(TileQuarry)world.getBlockEntity(POS);
                var data=quarry.createNbt();
                paidWork=data.getCompound("currentTaskData").getLong("power");balance=quarry.getStoredPower();
                require(paidWork>0 && data.contains("currentTaskId"),"Fixture failed to save partially paid quarry task");
                server.getPlayerManager().getPlayer(client.player.getUuid()).teleport(world,0,-60,0,0,0);
            });
            advance();
        } else if(stage==3 && ready()) {
            stage=4;ticks=0;
            client.world.disconnect();
            client.disconnect(new TitleScreen());
        } else if(stage==5 && ticks>100) {
            work=client.getServer().submit(()->{
                var world=client.getServer().getOverworld();
                require(world.isChunkLoaded(POS),"Saved quarry ticket did not load a distant quarry on restart");
                var tile=(TileQuarry)world.getBlockEntity(POS);
                require(tile!=null && !ChunkLoaderManager.getActiveChunks(world,POS).isEmpty(),"Quarry lost persistent ticket ownership");
                long resumedWork=tile.createNbt().getCompound("currentTaskData").getLong("power");
                require(resumedWork>=paidWork && resumedWork+tile.getStoredPower()==paidWork+balance,
                    "World restart lost paid task/energy: before="+paidWork+"+"+balance+", after="+resumedWork+"+"+tile.getStoredPower());
                BCBuildersConfig.quarryMaxBlockMineRate=2;
                power(tile,20_000*MjAPI.MJ);
                var player=world.getServer().getPlayerManager().getPlayer(client.player.getUuid());
                player.getAbilities().flying=true;player.sendAbilitiesUpdate();
                player.teleport(world,POS.getX()+1.5,-56,POS.getZ()-7,0,35);
            });
            advance();
        } else if(stage==6 && ready()) {
            if(!(client.world.getBlockEntity(POS) instanceof TileQuarry tile) || !tile.frameBox.isInitialized())return;
            advance();
        } else if(stage==7) {
            if(client.world.getBlockEntity(POS) instanceof TileQuarry tile && tile.clientDrillPos!=null) {
                drillPositions.add(BlockPos.ofFloored(tile.clientDrillPos));
                require(tile.getCollisionBoxes().size()==3,"Client has no physical quarry arms");
            }
            if(ticks<350)return;
            require(renderedFrames>30 && drillPositions.size()>1,"Quarry animation did not progress on the GPU");
            work=client.getServer().submit(()->{
                var world=client.getServer().getOverworld();var tile=(TileQuarry)world.getBlockEntity(POS);
                var bank=(BarrelBlockEntity)world.getBlockEntity(POS.west());
                require(tile.isMiningFinished(),"Restarted quarry failed to complete excavation");
                require(count(bank,Items.DIAMOND_BLOCK)==4 && count(bank,Items.GOLD_BLOCK)==4,"Restarted quarry lost/duplicated output");
                require(tile.framePoses.stream().allMatch(p->world.getBlockState(p).isOf(BCBuildersBlocks.frame)),"Quarry frame vanished after restart");
            });
            net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(client.runDirectory,"quarry-smoke.png",client.getFramebuffer(),message->{});
            advance();
        } else if(stage==8 && ready()) {
            System.out.println("BUILDCRAFT QUARRY CLIENT: native item placement, marker selection, advancement, paid work, distant persistent tickets across world restart, animated drill, collision, exact mined output; frames="+renderedFrames+"; failures=0");
            stage=9;client.scheduleStop();
        }
    }
    private void advance(){stage++;ticks=0;System.out.println("BUILDCRAFT QUARRY CLIENT stage="+stage);}
    private boolean ready(){if(work==null || !work.isDone() || ticks<15)return false;work.join();return true;}
    private static void power(TileQuarry tile,long amount) {
        var input=(IMjReceiver)MjAPI.CONNECTOR.find(tile.getWorld(),tile.getPos(),Direction.UP);
        require(input!=null && input.receivePower(amount,false)==0,"Quarry rejected MJ");
    }
    private static int count(BarrelBlockEntity bank,Item item) {
        int result=0;for(int i=0;i<bank.size();i++)if(bank.getStack(i).isOf(item))result+=bank.getStack(i).getCount();return result;
    }
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
}
