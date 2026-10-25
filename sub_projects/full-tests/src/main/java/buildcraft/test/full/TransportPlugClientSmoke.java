/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import buildcraft.api.mj.MjAPI;
import buildcraft.energy.BCEnergyBlocks;
import buildcraft.energy.tile.TileDynamoMJ;
import buildcraft.lib.BCLibConfig;
import buildcraft.lib.block.BlockBCBase_Neptune;
import buildcraft.transport.*;
import buildcraft.transport.plug.*;
import buildcraft.transport.tile.TilePipeHolder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
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
import team.reborn.energy.api.EnergyStorage;

/** Real native placement, atlas reload, RF pipeline, fractional saved credit and attachment breaking. */
public final class TransportPlugClientSmoke implements ClientModInitializer {
    private static final BlockPos POS=new BlockPos(2,-56,2);
    private int stage,ticks;private long deadline;private String saveName;private CompletableFuture<Void> work,reload;
    @Override public void onInitializeClient(){if(Boolean.getBoolean("buildcraft.transportPlugClientSmoke"))ClientTickEvents.END_CLIENT_TICK.register(this::tick);}
    private void tick(MinecraftClient client){
        if(stage==0){
            if(client.getOverlay()!=null || !(client.currentScreen instanceof TitleScreen))return;
            client.options.pauseOnLostFocus=false;client.options.getViewDistance().setValue(6);client.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
            deadline=System.nanoTime()+240_000_000_000L;saveName="transport-plugs-"+System.currentTimeMillis();stage=1;
            var rules=new GameRules();rules.get(GameRules.DO_MOB_SPAWNING).set(false,null);
            client.createIntegratedServerLoader().createAndStart(saveName,new LevelInfo("BuildCraft transport plugs — Remake by FelixMantra",GameMode.CREATIVE,false,Difficulty.PEACEFUL,true,rules,DataConfiguration.SAFE_MODE),
                new GeneratorOptions(4754L,false,false),registries->registries.get(RegistryKeys.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createDimensionsRegistryHolder());return;
        }
        if(stage==20)return;if(System.nanoTime()>deadline)throw new IllegalStateException("Transport plug client timeout at stage "+stage);
        if(stage==5){if(client.getServer()!=null || client.world!=null)return;stage=6;ticks=0;client.createIntegratedServerLoader().start(new TitleScreen(),saveName);return;}
        if(client.world==null || client.player==null || client.getServer()==null)return;ticks++;if(ticks%20==0)client.getToastManager().clear();
        if(stage==1){
            work=client.getServer().submit(()->{
                var world=client.getServer().getOverworld();world.setTimeOfDay(1000);
                require(BCLibConfig.powerMode==BCLibConfig.PowerMode.MJ_AUTOCONVERT_RF && BCTransportConfig.itemExtractionCost()==150000,"Fixture config must enable RF conversion and use 0.15 MJ per item");
                world.setBlockState(POS.west(),Blocks.BARREL.getDefaultState());world.setBlockState(POS.east(2),Blocks.BARREL.getDefaultState());
                pipe(client,POS,BCTransportItems.pipeItemWood);pipe(client,POS.east(),BCTransportItems.pipeItemStone);
                for(var side:List.of(Direction.NORTH,Direction.SOUTH)){
                    var player=player(client);aim(client,side,false);var stack=new ItemStack(side==Direction.NORTH?BCTransportItems.plugPowerAdaptor:BCTransportItems.plugBlocker);player.setStackInHand(Hand.MAIN_HAND,stack);
                    var result=player.interactionManager.interactBlock(player,world,stack,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(POS),side,POS,false));
                    require(result.isAccepted() && tile(client).getPluggable(side)!=null,"Native transport attachment placement failed "+side);
                }
                pipe(client,POS.south(),BCTransportItems.pipeItemStone);tile(client).tick();source(client).setStack(0,new ItemStack(Items.DIAMOND,4));player(client).setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);aim(client,Direction.NORTH,false);
            });advance();
        }else if(stage==2 && ready() && ticks>25){
            if(reload==null){models(client);reload=client.reloadResources();return;}
            if(!reload.isDone() || client.getOverlay()!=null)return;reload.join();models(client);shot(client,"transport-adaptor-model.png");
            work=client.getServer().submit(()->{
                require(!tile(client).getPipe().isConnected(Direction.SOUTH),"Blocker retained item connection");
                try(var tx=Transaction.openOuter()){require(input(client).insert(2,tx)==2,"Initial RF insertion failed");tx.commit();}
                require(adaptor(client).getStoredMicroMj()==50000,"Initial RF credit missing");
            });advance();
        }else if(stage==3 && ready() && ticks>140){
            work=client.getServer().submit(()->{require(count(target(client))==1 && count(source(client))==3 && adaptor(client).getStoredMicroMj()==50000,"Initial RF work/delivery incorrect");aim(client,Direction.SOUTH,true);});advance();
        }else if(stage==4 && ready() && ticks>25){
            shot(client,"transport-blocker-model.png");stage=5;ticks=0;client.world.disconnect();client.disconnect(new TitleScreen());
        }else if(stage==6 && ticks>35){
            work=client.getServer().submit(()->{
                var world=client.getServer().getOverworld();require(adaptor(client).getStoredMicroMj()==50000,"Full restart lost fractional credit");
                try(var tx=Transaction.openOuter()){require(input(client).insert(1,tx)==1,"Restored credit not applied");tx.commit();}
                require(adaptor(client).getStoredMicroMj()==0,"Restored credit consumed incorrectly");
                pipe(client,POS.north(),BCTransportItems.pipeRfStone);pipe(client,POS.north(2),BCTransportItems.pipeRfWood);
                world.setBlockState(POS.north(3),BCEnergyBlocks.mjDynamo.getDefaultState().with(BlockBCBase_Neptune.BLOCK_FACING_6,Direction.SOUTH));
                ((TileDynamoMJ)world.getBlockEntity(POS.north(3))).battery.addPower(300000,false);
                aim(client,Direction.NORTH,true);
            });advance();
        }else if(stage==7 && ready() && ticks>30){shot(client,"transport-rf-pipeline.png");advance();}
        else if(stage==8 && ticks>160){
            work=client.getServer().submit(()->{
                require(count(target(client))==4 && source(client).isEmpty(),"Real RF pipeline did not deliver four exact diamonds");
                var relay=(TilePipeHolder)client.getServer().getOverworld().getBlockEntity(POS.north());require(relay.getPipe().getConnectedType(Direction.SOUTH)==buildcraft.api.transport.pipe.IPipe.ConnectedType.TILE,"RF relay bypassed adaptor endpoint");
                aim(client,Direction.NORTH,true);
            });advance();
        }else if(stage==9 && ready() && ticks>25){client.interactionManager.attackBlock(POS,Direction.NORTH);advance();}
        else if(stage==10 && ticks>35){
            require(((TilePipeHolder)client.world.getBlockEntity(POS)).getPluggable(Direction.NORTH)==null,"Native adaptor removal failed");
            work=client.getServer().submit(()->{
                source(client).setStack(0,new ItemStack(Items.DIAMOND,3));((TileDynamoMJ)client.getServer().getOverworld().getBlockEntity(POS.north(3))).battery.addPower(2*MjAPI.MJ,false);
                require(EnergyStorage.SIDED.find(client.getServer().getOverworld(),POS,Direction.NORTH)==null,"Removed adaptor retained native RF input");aim(client,Direction.SOUTH,true);
            });advance();
        }else if(stage==11 && ready() && ticks>100){
            work=client.getServer().submit(()->require(count(source(client))==3 && count(target(client))==4,"Removed adaptor still extracted items"));work.join();
            client.interactionManager.attackBlock(POS,Direction.SOUTH);advance();
        }else if(stage==12 && ticks>35){
            require(((TilePipeHolder)client.world.getBlockEntity(POS)).getPluggable(Direction.SOUTH)==null,"Native blocker removal failed");
            work=client.getServer().submit(()->{require(tile(client).getPipe().isConnected(Direction.SOUTH),"Blocker removal did not reconnect item pipe");require(count(source(client))==3 && count(target(client))==4,"Attachment breaking changed cargo");aim(client,Direction.SOUTH,true);});advance();
        }else if(stage==13 && ready() && ticks>25){
            shot(client,"transport-attachments-removed.png");
            System.out.println("BUILDCRAFT TRANSPORT PLUG CLIENT: native six-facing blocker/adaptor models, resource reload, native placement and breaking, saved fractional RF credit, Dynamo/RF pipes/adaptor/item pipe delivery of four exact diamonds, removed-adaptor stop and blocker reconnection; failures=0");
            stage=20;client.scheduleStop();
        }
    }
    private static void models(MinecraftClient client){
        for(var item:List.of(BCTransportItems.plugBlocker,BCTransportItems.plugPowerAdaptor)){
            var stack=new ItemStack(item);require(client.getItemRenderer().getModel(stack,client.world,client.player,0)!=client.getBakedModelManager().getMissingModel(),"Transport plug item model missing");
            require(stack.getTooltip(client.player,net.minecraft.client.item.TooltipContext.Default.BASIC).stream().anyMatch(t->t.getString().contains("Remake by FelixMantra")),"Credit missing");
            for(var face:Direction.values()){
                var quads=BCTransportModels.PLUG_MODEL.getQuads(item.powerAdaptor,face);
                require(quads.length==(item.powerAdaptor?12:6) && Arrays.stream(quads).allMatch(q->q.getSprite()!=null && !q.getSprite().getContents().getId().getPath().equals("missingno")),"Missing transport plug model "+face);
            }
        }
    }
    private static void aim(MinecraftClient client,Direction side,boolean angled){
        var player=player(client);var target=Vec3d.ofCenter(POS).add(Vec3d.of(side.getVector()).multiply(.35));
        var eye=angled?Vec3d.ofCenter(POS).add(1.3,1.8,side==Direction.NORTH?-3:3):Vec3d.ofCenter(POS).add(Vec3d.of(side.getVector()).multiply(3));
        var look=target.subtract(eye).normalize();player.teleport(player.getServerWorld(),eye.x,eye.y-player.getEyeHeight(player.getPose()),eye.z,(float)Math.toDegrees(Math.atan2(-look.x,look.z)),(float)-Math.toDegrees(Math.asin(look.y)));player.setHeadYaw(player.getYaw());player.getAbilities().flying=true;player.sendAbilitiesUpdate();
    }
    private static void pipe(MinecraftClient client,BlockPos pos,Item item){var world=client.getServer().getOverworld();world.setBlockState(pos,BCTransportBlocks.pipeHolder.getDefaultState());((TilePipeHolder)world.getBlockEntity(pos)).onPlacedBy(null,new ItemStack(item));}
    private static net.minecraft.server.network.ServerPlayerEntity player(MinecraftClient client){return client.getServer().getPlayerManager().getPlayer(client.player.getUuid());}
    private static TilePipeHolder tile(MinecraftClient client){return (TilePipeHolder)client.getServer().getOverworld().getBlockEntity(POS);}
    private static PluggablePowerAdaptor adaptor(MinecraftClient client){return (PluggablePowerAdaptor)tile(client).getPluggable(Direction.NORTH);}
    private static EnergyStorage input(MinecraftClient client){return EnergyStorage.SIDED.find(client.getServer().getOverworld(),POS,Direction.NORTH);}
    private static BarrelBlockEntity source(MinecraftClient client){return (BarrelBlockEntity)client.getServer().getOverworld().getBlockEntity(POS.west());}
    private static BarrelBlockEntity target(MinecraftClient client){return (BarrelBlockEntity)client.getServer().getOverworld().getBlockEntity(POS.east(2));}
    private static int count(BarrelBlockEntity barrel){int count=0;for(int i=0;i<barrel.size();i++)count+=barrel.getStack(i).getCount();return count;}
    private static void shot(MinecraftClient client,String name){net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(client.runDirectory,name,client.getFramebuffer(),message->{});}
    private void advance(){stage++;ticks=0;System.out.println("BUILDCRAFT TRANSPORT PLUG CLIENT stage="+stage);}
    private boolean ready(){if(work==null || !work.isDone() || ticks<15)return false;work.join();return true;}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
