/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;

import java.util.concurrent.CompletableFuture;
import buildcraft.api.mj.*;
import buildcraft.core.BCCoreBlocks;
import buildcraft.energy.BCEnergyFluids;
import buildcraft.factory.BCFactoryBlocks;
import buildcraft.factory.tile.*;
import buildcraft.transport.*;
import buildcraft.transport.tile.TilePipeHolder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
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

/** Actual client/server placement, shaft animation, paid-work restart and finite oil delivered through pipes. */
public final class PumpClientSmoke implements ClientModInitializer {
    private static final BlockPos POS=new BlockPos(2,-54,2), SOURCE=POS.down(4);
    private int stage,ticks,frames;
    private long deadline;
    private String saveName;
    private CompletableFuture<Void> work;
    @Override public void onInitializeClient() {
        if(!Boolean.getBoolean("buildcraft.pumpClientSmoke"))return;
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        WorldRenderEvents.END.register(context->{
            var client=MinecraftClient.getInstance();
            if(client.world!=null && client.world.getBlockEntity(POS) instanceof TilePump tile
                && tile.getLength(1)>0 && !tile.isComplete())frames++;
        });
    }
    private void tick(MinecraftClient client) {
        if(stage==0) {
            if(client.getOverlay()!=null || !(client.currentScreen instanceof TitleScreen))return;
            client.options.pauseOnLostFocus=false;client.options.getViewDistance().setValue(6);
            deadline=System.nanoTime()+240_000_000_000L;saveName="pump-smoke-"+System.currentTimeMillis();stage=1;
            var rules=new GameRules();rules.get(GameRules.DO_MOB_SPAWNING).set(false,null);
            client.createIntegratedServerLoader().createAndStart(saveName,
                new LevelInfo("BuildCraft pump — Remake by FelixMantra",GameMode.CREATIVE,false,Difficulty.PEACEFUL,true,rules,DataConfiguration.SAFE_MODE),
                new GeneratorOptions(4754L,false,false),
                registries->registries.get(RegistryKeys.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createDimensionsRegistryHolder());
            return;
        }
        if(stage==10)return;
        if(System.nanoTime()>deadline)throw new IllegalStateException("Pump client timed out at stage "+stage);
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
                for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++) {
                    world.setBlockState(SOURCE.add(x,-1,z),Blocks.STONE.getDefaultState());
                    world.setBlockState(SOURCE.add(x,0,z),Math.abs(x)==2 || Math.abs(z)==2?Blocks.GLASS.getDefaultState():BCEnergyFluids.crudeOil[0].getBlock().getDefaultState());
                }
                for(int depth=1;depth<4;depth++)world.setBlockState(POS.down(depth),Blocks.AIR.getDefaultState());
                world.setBlockState(POS.down(),Blocks.STONE.getDefaultState());
                player.setStackInHand(Hand.MAIN_HAND,new ItemStack(BCFactoryBlocks.pump));
                var hit=new BlockHitResult(Vec3d.ofCenter(POS.down()).add(0,.5,0),Direction.UP,POS.down(),false);
                require(player.interactionManager.interactBlock(player,world,player.getMainHandStack(),Hand.MAIN_HAND,hit).isAccepted(),"Native pump placement failed");
                player.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);world.setBlockState(POS.down(),Blocks.AIR.getDefaultState());
                world.setBlockState(POS.west(),BCTransportBlocks.pipeHolder.getDefaultState());
                ((TilePipeHolder)world.getBlockEntity(POS.west())).onPlacedBy(null,new ItemStack(BCTransportItems.pipeFluidStone));
                world.setBlockState(POS.west(2),BCFactoryBlocks.tank.getDefaultState());
                var receiver=(IMjReceiver)MjAPI.CONNECTOR.find(world,POS,Direction.UP);
                require(receiver!=null && receiver.receivePower(4*MjAPI.MJ,false)==0,"Native pump MJ endpoint missing");
            });advance();
        } else if(stage==2 && ready()) {
            if(!(client.world.getBlockEntity(POS) instanceof TilePump tile) || tile.getWantedLength()!=4 || ticks<65)return;
            require(!tile.isComplete() && Math.abs(tile.getLength(1)-4)<.02 && frames>20,"Client pump shaft/LED did not animate");
            net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(client.runDirectory,"pump-active.png",client.getFramebuffer(),message->{});
            work=client.getServer().submit(()->{
                var serverTile=(TilePump)client.getServer().getOverworld().getBlockEntity(POS);
                require(serverTile.getPaidWork()==4*MjAPI.MJ && serverTile.getStoredPower()==0 && serverTile.getFluidAmount()==0,"Fixture did not preserve partial pump work");
            });advance();
        } else if(stage==3 && ready()) {
            stage=4;ticks=0;client.world.disconnect();client.disconnect(new TitleScreen());
        } else if(stage==5 && ticks>80) {
            work=client.getServer().submit(()->{
                var world=client.getServer().getOverworld();var tile=(TilePump)world.getBlockEntity(POS);
                require(tile!=null && tile.getPaidWork()==4*MjAPI.MJ && tile.getFluidAmount()==0,"World restart lost pump paid work");
                for(int i=1;i<4;i++)require(world.getBlockState(POS.down(i)).isOf(BCFactoryBlocks.tube),"World restart lost pump shaft");
                for(int i=1;i<=2;i++) {
                    world.setBlockState(POS.east(i),BCTransportBlocks.pipeHolder.getDefaultState());
                    ((TilePipeHolder)world.getBlockEntity(POS.east(i))).onPlacedBy(null,new ItemStack(i==2?BCTransportItems.pipePowerWood:BCTransportItems.pipePowerStone));
                }
                world.setBlockState(POS.east(3).down(),Blocks.REDSTONE_BLOCK.getDefaultState());
                world.setBlockState(POS.east(3),BCCoreBlocks.engine.getDefaultState().with(buildcraft.api.properties.BuildCraftProperties.ENGINE_TYPE,buildcraft.api.enums.EnumEngineType.CREATIVE));
                ((buildcraft.core.tile.TileEngineCreative)world.getBlockEntity(POS.east(3))).currentOutputIndex=4;
            });advance();
        } else if(stage==6 && ready()) {
            require(net.minecraft.text.Text.translatable("advancements.buildcraftfactory.draining_the_world.remake_description").getString().contains("Remake by FelixMantra"),"Localized remake credit missing");
            require(client.getNetworkHandler().getRecipeManager().get(new Identifier("buildcraftfactory:pump")).isPresent(),"Client pump recipe missing");advance();
        } else if(stage==7) {
            // Stone carries 20 mB/t: nine buckets need 450 ticks plus the three section delays.
            if(ticks<600)return;
            var tile=(TilePump)client.world.getBlockEntity(POS);
            require(tile.isComplete() && tile.getLength(1)<.02 && tile.getFluidAmount()==0,"Client pump state: complete="+tile.isComplete()+", length="+tile.getLength(1)+", fluid="+tile.getFluidAmount());
            work=client.getServer().submit(()->{
                var world=client.getServer().getOverworld();var bank=(TileTank)world.getBlockEntity(POS.west(2));
                require(bank.tank.getStorage().getAmount()==9*FluidConstants.BUCKET && bank.tank.getStorage().getResource().isOf(BCEnergyFluids.crudeOil[0]),"Native oil pipe lost or duplicated finite sources: "+bank.tank.getStorage().getAmount());
                for(int depth=1;depth<4;depth++)require(world.isAir(POS.down(depth)),"Completed pump left orphan tubes");
                var player=world.getServer().getPlayerManager().getPlayer(client.player.getUuid());
                for(String name:new String[]{"draining_the_world","oil_platform"}) {
                    var advancement=world.getServer().getAdvancementLoader().get(new Identifier("buildcraftfactory",name));
                    require(advancement!=null && player.getAdvancementTracker().getProgress(advancement).isDone(),"Native owner advancement missing: "+name);
                }
            });
            net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(client.runDirectory,"pump-complete.png",client.getFramebuffer(),message->{});
            advance();
        } else if(stage==8 && ready()) {
            work=client.getServer().submit(()->{
                var world=client.getServer().getOverworld();var stale=(IMjReceiver)MjAPI.CONNECTOR.find(world,POS,Direction.UP);
                var player=world.getServer().getPlayerManager().getPlayer(client.player.getUuid());
                require(player.interactionManager.tryBreakBlock(POS),"Native player could not remove pump");
                require(stale.receivePower(MjAPI.MJ,false)==MjAPI.MJ,"Removed pump accepted cached MJ");
            });advance();
        } else if(stage==9 && ready()) {
            require(client.world.isAir(POS),"Client retained removed pump");
            System.out.println("BUILDCRAFT PUMP CLIENT: native placement, shaft/LED/fluid sync, paid-work world restart, live engine/MJ pipes, exact 9-bucket oil output, owner advancements, retraction/removal; frames="+frames+"; failures=0");
            stage=10;client.scheduleStop();
        }
    }
    private void advance(){stage++;ticks=0;System.out.println("BUILDCRAFT PUMP CLIENT stage="+stage);}
    private boolean ready(){if(work==null || !work.isDone() || ticks<15)return false;work.join();return true;}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
