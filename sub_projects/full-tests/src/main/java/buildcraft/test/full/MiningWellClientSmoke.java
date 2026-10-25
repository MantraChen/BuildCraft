/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import buildcraft.api.mj.*;
import buildcraft.core.BCCoreBlocks;
import buildcraft.factory.BCFactoryBlocks;
import buildcraft.factory.tile.TileMiningWell;
import buildcraft.transport.*;
import buildcraft.transport.tile.TilePipeHolder;
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

/** Native placement, visible shaft, paid-work world restart, live engine power and retraction. */
public final class MiningWellClientSmoke implements ClientModInitializer {
    private static final BlockPos POS=new BlockPos(2,-54,2);
    private int stage,ticks,frames;
    private long deadline;
    private String saveName;
    private CompletableFuture<Void> work;
    private final Set<Integer> lengths=new HashSet<>();
    @Override public void onInitializeClient() {
        if(!Boolean.getBoolean("buildcraft.miningWellClientSmoke"))return;
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        WorldRenderEvents.END.register(context->{
            var client=MinecraftClient.getInstance();
            if((stage==2 || stage==7) && client.world!=null && client.world.getBlockEntity(POS) instanceof TileMiningWell tile
                && tile.getLength(1)>0 && !tile.isComplete())frames++;
        });
    }
    private void tick(MinecraftClient client) {
        if(stage==0) {
            if(client.getOverlay()!=null || !(client.currentScreen instanceof TitleScreen))return;
            client.options.pauseOnLostFocus=false;client.options.getViewDistance().setValue(6);
            deadline=System.nanoTime()+180_000_000_000L;saveName="mining-well-smoke-"+System.currentTimeMillis();stage=1;
            var rules=new GameRules();rules.get(GameRules.DO_MOB_SPAWNING).set(false,null);
            client.createIntegratedServerLoader().createAndStart(saveName,
                new LevelInfo("BuildCraft mining well — Remake by FelixMantra",GameMode.CREATIVE,false,Difficulty.PEACEFUL,true,rules,DataConfiguration.SAFE_MODE),
                new GeneratorOptions(4754L,false,false),
                registries->registries.get(RegistryKeys.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createDimensionsRegistryHolder());
            return;
        }
        if(stage==10)return;
        if(System.nanoTime()>deadline)throw new IllegalStateException("Mining well client timed out at stage "+stage);
        if(stage==4) {
            if(client.getServer()!=null || client.world!=null)return;
            stage=5;ticks=0;client.createIntegratedServerLoader().start(new TitleScreen(),saveName);return;
        }
        if(client.world==null || client.player==null || client.getServer()==null)return;
        ticks++;
        if(stage==1) {
            work=client.getServer().submit(()->{
                var world=client.getServer().getOverworld();var player=world.getServer().getPlayerManager().getPlayer(client.player.getUuid());
                world.setTimeOfDay(1000);player.changeGameMode(GameMode.CREATIVE);
                player.getAbilities().flying=true;player.sendAbilitiesUpdate();player.teleport(world,4,-53,-5,0,28);
                for(int depth=1;depth<=7;depth++)world.setBlockState(POS.down(depth),Blocks.AIR.getDefaultState());
                world.setBlockState(POS.down(),Blocks.STONE.getDefaultState());
                world.setBlockState(POS.down(4),Blocks.DIAMOND_BLOCK.getDefaultState());
                world.setBlockState(POS.down(6),Blocks.GOLD_BLOCK.getDefaultState());
                world.setBlockState(POS.down(7),Blocks.BEDROCK.getDefaultState());
                world.setBlockState(POS.west(),Blocks.BARREL.getDefaultState());
                player.setStackInHand(Hand.MAIN_HAND,new ItemStack(BCFactoryBlocks.miningWell));
                var hit=new BlockHitResult(Vec3d.ofCenter(POS.down()).add(0,.5,0),Direction.UP,POS.down(),false);
                require(player.interactionManager.interactBlock(player,world,player.getMainHandStack(),Hand.MAIN_HAND,hit).isAccepted(),"Native mining-well placement failed");
                player.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);world.setBlockState(POS.down(),Blocks.AIR.getDefaultState());
                var tile=(TileMiningWell)world.getBlockEntity(POS);
                var receiver=(IMjReceiver)MjAPI.CONNECTOR.find(world,POS,Direction.UP);
                require(tile!=null && receiver!=null && receiver.receivePower(40*MjAPI.MJ,false)==0,"Native mining-well MJ endpoint missing");
            });advance();
        } else if(stage==2 && ready()) {
            if(!(client.world.getBlockEntity(POS) instanceof TileMiningWell tile) || tile.getWantedLength()!=4 || ticks<60)return;
            require(!tile.isComplete() && Math.abs(tile.getLength(1)-4)<.02 && frames>20,"Client shaft/LED state did not animate");
            lengths.add(4);
            net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(client.runDirectory,"mining-well-active.png",client.getFramebuffer(),message->{});
            work=client.getServer().submit(()->{
                var tileServer=(TileMiningWell)client.getServer().getOverworld().getBlockEntity(POS);
                require(tileServer.getPaidWork()==40*MjAPI.MJ && tileServer.getStoredPower()==0,"Fixture did not preserve partial mining work");
            });advance();
        } else if(stage==3 && ready()) {
            stage=4;ticks=0;client.world.disconnect();client.disconnect(new TitleScreen());
        } else if(stage==5 && ticks>80) {
            work=client.getServer().submit(()->{
                var world=client.getServer().getOverworld();var tile=(TileMiningWell)world.getBlockEntity(POS);
                require(tile!=null && tile.getPaidWork()==40*MjAPI.MJ && tile.getCurrentPos().equals(POS.down(4)),"World restart lost miner task/paid work");
                for(int i=1;i<4;i++)require(world.getBlockState(POS.down(i)).isOf(BCFactoryBlocks.tube),"World restart lost miner shaft");
                for(int i=1;i<=2;i++) {
                    world.setBlockState(POS.east(i),BCTransportBlocks.pipeHolder.getDefaultState());
                    ((TilePipeHolder)world.getBlockEntity(POS.east(i))).onPlacedBy(null,new ItemStack(i==2?BCTransportItems.pipePowerWood:BCTransportItems.pipePowerStone));
                }
                world.setBlockState(POS.east(3).down(),Blocks.REDSTONE_BLOCK.getDefaultState());
                world.setBlockState(POS.east(3),BCCoreBlocks.engine.getDefaultState().with(buildcraft.api.properties.BuildCraftProperties.ENGINE_TYPE,buildcraft.api.enums.EnumEngineType.CREATIVE));
                ((buildcraft.core.tile.TileEngineCreative)world.getBlockEntity(POS.east(3))).currentOutputIndex=4;
            });advance();
        } else if(stage==6 && ready()) {
            require(client.getNetworkHandler().getRecipeManager().get(new Identifier("buildcraftfactory:mining_well")).isPresent(),"Client mining-well recipe missing");advance();
        } else if(stage==7) {
            if(client.world.getBlockEntity(POS) instanceof TileMiningWell tile)lengths.add(tile.getWantedLength());
            if(ticks<220)return;
            require(lengths.contains(6) && lengths.contains(0),"Client did not extend and retract the shaft");
            var tile=(TileMiningWell)client.world.getBlockEntity(POS);
            require(tile.isComplete() && tile.getLength(1)<.02,"Client retained an active tube after completion");
            work=client.getServer().submit(()->{
                var world=client.getServer().getOverworld();var serverTile=(TileMiningWell)world.getBlockEntity(POS);
                var bank=(BarrelBlockEntity)world.getBlockEntity(POS.west());
                require(serverTile.isComplete() && count(bank,Items.DIAMOND_BLOCK)==1 && count(bank,Items.GOLD_BLOCK)==1,"Live engine mining lost exact loot");
                for(int depth=1;depth<7;depth++)require(world.isAir(POS.down(depth)),"Completed miner left orphan shaft tubes");
            });
            net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(client.runDirectory,"mining-well-complete.png",client.getFramebuffer(),message->{});
            advance();
        } else if(stage==8 && ready()) {
            work=client.getServer().submit(()->{
                var world=client.getServer().getOverworld();var stale=(IMjReceiver)MjAPI.CONNECTOR.find(world,POS,Direction.UP);
                var player=world.getServer().getPlayerManager().getPlayer(client.player.getUuid());
                require(player.interactionManager.tryBreakBlock(POS),"Native player could not remove completed miner");
                require(stale.receivePower(MjAPI.MJ,false)==MjAPI.MJ,"Removed miner accepted cached MJ");
            });advance();
        } else if(stage==9 && ready()) {
            require(client.world.isAir(POS),"Client retained removed mining well");
            System.out.println("BUILDCRAFT MINING WELL CLIENT: native placement, animated shaft/LED, partial-work world restart, engine/MJ-pipe power, exact output, tube retraction, native removal; frames="+frames+"; failures=0");
            stage=10;client.scheduleStop();
        }
    }
    private void advance(){stage++;ticks=0;System.out.println("BUILDCRAFT MINING WELL CLIENT stage="+stage);}
    private boolean ready(){if(work==null || !work.isDone() || ticks<15)return false;work.join();return true;}
    private static int count(BarrelBlockEntity bank,Item item){int total=0;for(int i=0;i<bank.size();i++)if(bank.getStack(i).isOf(item))total+=bank.getStack(i).getCount();return total;}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
