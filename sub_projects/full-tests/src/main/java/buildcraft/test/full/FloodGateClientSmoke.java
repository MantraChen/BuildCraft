/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;

import java.util.concurrent.CompletableFuture;
import buildcraft.api.enums.EnumEngineType;
import buildcraft.api.properties.BuildCraftProperties;
import buildcraft.core.*;
import buildcraft.core.item.ItemFragileFluidContainer;
import buildcraft.core.tile.TileEngineCreative;
import buildcraft.factory.*;
import buildcraft.factory.block.BlockFloodGate;
import buildcraft.factory.tile.*;
import buildcraft.transport.*;
import buildcraft.transport.pipe.flow.PipeFlowFluids;
import buildcraft.transport.tile.TilePipeHolder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.entity.ItemEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.*;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.world.gen.*;
import net.minecraft.world.level.LevelInfo;

/** Native bucket/wrench/break packets, real engine-driven fluid pipes, and two complete world restarts. */
public final class FloodGateClientSmoke implements ClientModInitializer {
    private static final BlockPos POS=new BlockPos(2,-56,2), ENGINE=POS.north(2).east();
    private static final long BUCKET=FluidConstants.BUCKET;
    private static final FluidVariant WATER=FluidVariant.of(Fluids.WATER);
    private int stage,ticks;private long deadline;private String saveName;private boolean restoredPosition;
    private CompletableFuture<Void> work,reload;private Storage<FluidVariant> stale;
    @Override public void onInitializeClient(){if(Boolean.getBoolean("buildcraft.floodGateClientSmoke"))ClientTickEvents.END_CLIENT_TICK.register(this::tick);}
    private void tick(MinecraftClient client){
        if(stage==0){
            if(client.getOverlay()!=null || !(client.currentScreen instanceof TitleScreen))return;
            client.options.pauseOnLostFocus=false;client.options.getViewDistance().setValue(6);client.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
            deadline=System.nanoTime()+300_000_000_000L;saveName="flood-gate-smoke-"+System.currentTimeMillis();stage=1;
            var rules=new GameRules();rules.get(GameRules.DO_MOB_SPAWNING).set(false,null);
            client.createIntegratedServerLoader().createAndStart(saveName,new LevelInfo("BuildCraft Flood Gate — Remake by FelixMantra",GameMode.CREATIVE,false,Difficulty.PEACEFUL,true,rules,DataConfiguration.SAFE_MODE),
                new GeneratorOptions(4754L,false,false),registries->registries.get(RegistryKeys.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createDimensionsRegistryHolder());return;
        }
        if(stage==30)return;
        if(System.nanoTime()>deadline)throw new IllegalStateException("Flood gate client timed out at stage "+stage);
        if(stage==7 || stage==13){if(client.getServer()!=null || client.world!=null)return;stage++;ticks=0;client.createIntegratedServerLoader().start(new TitleScreen(),saveName);return;}
        if(client.world==null || client.player==null || client.getServer()==null)return;
        ticks++;if(ticks%20==0)client.getToastManager().clear();
        if(stage==1){
            work=client.getServer().submit(()->{
                var world=client.getServer().getOverworld();var player=player(client);world.setTimeOfDay(6000);
                world.setBlockState(POS.down(),Blocks.STONE.getDefaultState());player.setStackInHand(Hand.MAIN_HAND,new ItemStack(BCFactoryBlocks.floodGate));aim(player);
                require(player.interactionManager.interactBlock(player,world,player.getMainHandStack(),Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(POS.down()).add(0,.5,0),Direction.UP,POS.down(),false)).isAccepted(),"Native flood gate placement failed");
                world.setBlockState(POS.west(),Blocks.GLASS.getDefaultState());world.setBlockState(POS.south(),Blocks.GLASS.getDefaultState());
                for(int i=1;i<=3;i++){var cell=POS.east(i);world.setBlockState(cell.down(),Blocks.GLASS.getDefaultState());world.setBlockState(cell.north(),Blocks.GLASS.getDefaultState());world.setBlockState(cell.south(),Blocks.GLASS.getDefaultState());}
                world.setBlockState(POS.east(4),Blocks.GLASS.getDefaultState());
                for(int i=1;i<=2;i++){var cell=POS.north(i);world.setBlockState(cell,BCTransportBlocks.pipeHolder.getDefaultState());((TilePipeHolder)world.getBlockEntity(cell)).onPlacedBy(null,new ItemStack(i==1?BCTransportItems.pipeFluidStone:BCTransportItems.pipeFluidWood));}
                world.setBlockState(POS.north(3),BCFactoryBlocks.tank.getDefaultState());
                try(var tx=Transaction.openOuter()){require(((TileTank)world.getBlockEntity(POS.north(3))).tank.insert(WATER,BUCKET+37,tx)==BUCKET+37,"Fixture source tank rejected water");tx.commit();}
                world.setBlockState(ENGINE.down(),Blocks.STONE.getDefaultState());world.setBlockState(ENGINE,BCCoreBlocks.engine.getDefaultState().with(BuildCraftProperties.ENGINE_TYPE,EnumEngineType.CREATIVE));((TileEngineCreative)world.getBlockEntity(ENGINE)).currentOutputIndex=4;
                player.changeGameMode(GameMode.SURVIVAL);player.getAbilities().allowFlying=true;player.getAbilities().flying=true;player.sendAbilitiesUpdate();
                player.getInventory().clear();player.getInventory().setStack(0,new ItemStack(BCCoreItems.wrench));player.getInventory().setStack(1,new ItemStack(Items.WATER_BUCKET));player.getInventory().setStack(2,new ItemStack(Items.WATER_BUCKET));player.getInventory().selectedSlot=0;player.currentScreenHandler.sendContentUpdates();aim(player);
            });advance();
        }else if(stage==2 && ready() && ticks>25){
            if(reload==null){checkModels(client);reload=client.reloadResources();return;}
            if(!reload.isDone() || client.getOverlay()!=null)return;reload.join();checkModels(client);interact(client);advance();
        }else if(stage==3 && ticks>25){
            require(!clientGate(client).openSides.contains(Direction.EAST) && !client.world.getBlockState(POS).get(BlockFloodGate.CONNECTED_MAP.get(Direction.EAST)),"Native wrench state/model did not close");client.player.getInventory().selectedSlot=1;interact(client);advance();
        }else if(stage==4 && ticks>25){
            require(clientGate(client).getFluidAmount()==BUCKET && client.player.getInventory().getStack(1).isOf(Items.BUCKET),"Native bucket input did not debit/synchronize");client.player.getInventory().selectedSlot=2;interact(client);advance();
        }else if(stage==5 && ticks>25){
            require(clientGate(client).getFluidAmount()==2*BUCKET && client.player.getInventory().getStack(2).isOf(Items.BUCKET),"Second bucket input lost fluid/container");screenshot(client,"flood-gate-buffered-closed.png");
            work=client.getServer().submit(()->{require(gate(client).getFluidAmount()==2*BUCKET && client.getServer().getOverworld().isAir(POS.east()),"Closed outlet leaked buffered fluid");});advance();
        }else if(stage==6 && ready()){stage=7;ticks=0;client.world.disconnect();client.disconnect(new TitleScreen());}
        else if(stage==8 && ticks>35){
            if(!restoredPosition){
                work=client.getServer().submit(()->{
                    require(gate(client).getFluidAmount()==2*BUCKET && !gate(client).openSides.contains(Direction.EAST),"First restart lost buffered water or port state");
                    player(client).getAbilities().allowFlying=true;aim(player(client));
                });work.join();restoredPosition=true;ticks=0;return; // Let the native teleport acknowledgement arrive before using the wrench.
            }
            require(clientGate(client).getFluidAmount()==2*BUCKET && !clientGate(client).openSides.contains(Direction.EAST),"Restored client tank/port snapshot wrong");client.player.getInventory().selectedSlot=0;interact(client);advance();
        }else if(stage==9 && ticks>90){
            work=client.getServer().submit(()->{
                var world=client.getServer().getOverworld();require(gate(client).getFluidAmount()==0 && world.getFluidState(POS.east()).isStill() && world.getFluidState(POS.east(2)).isStill() && !world.getFluidState(POS.east(3)).isStill(),"Native opened outlet did not place exactly two sources: "+gate(client).getFluidAmount()+" droplets, outlets="+gate(client).openSides);
                world.setBlockState(ENGINE.down(),Blocks.REDSTONE_BLOCK.getDefaultState());
            });advance();
        }else if(stage==10 && ready() && ticks>240){
            work=client.getServer().submit(()->{
                var world=client.getServer().getOverworld();for(int i=1;i<=3;i++)require(world.getFluidState(POS.east(i)).isOf(Fluids.WATER) && world.getFluidState(POS.east(i)).isStill(),"Pipeline-fed source missing "+i);
                require(gate(client).getFluidAmount()==37 && ((TileTank)world.getBlockEntity(POS.north(3))).tank.getStorage().getAmount()==0,"Pipeline rounded/lost droplet remainder");
                for(int i=1;i<=2;i++)require(((PipeFlowFluids)((TilePipeHolder)world.getBlockEntity(POS.north(i))).getPipe().flow).getAmount()==0,"Pipeline retained fluid");
                var advancement=world.getServer().getAdvancementLoader().get(new Identifier("buildcraftfactory:flooding_the_world"));require(advancement!=null && player(client).getAdvancementTracker().getProgress(advancement).isDone(),"Native owner flooding advancement missing");
            });work.join();
            require(clientGate(client).getFluidAmount()==37 && clientGate(client).getFluidVariant().equals(WATER),"Remainder did not synchronize");require(client.getNetworkHandler().getRecipeManager().get(new Identifier("buildcraftfactory:flood_gate")).isPresent(),"Flood recipe missing on client");
            screenshot(client,"flood-gate-pipeline-filled.png");interact(client);advance();
        }else if(stage==11 && ticks>25){require(!clientGate(client).openSides.contains(Direction.EAST),"Native final close did not synchronize");screenshot(client,"flood-gate-final-closed.png");advance();}
        else if(stage==12 && ticks>15){stage=13;ticks=0;client.world.disconnect();client.disconnect(new TitleScreen());}
        else if(stage==14 && ticks>35){
            require(clientGate(client).getFluidAmount()==37 && !clientGate(client).openSides.contains(Direction.EAST),"Second restart lost fractional water or closed state");
            work=client.getServer().submit(()->{
                var world=client.getServer().getOverworld();require(gate(client).getFluidAmount()==37 && !gate(client).openSides.contains(Direction.EAST),"Second server restart lost remainder/settings");stale=FluidStorage.SIDED.find(world,POS,Direction.UP);
                player(client).changeGameMode(GameMode.CREATIVE);aim(player(client));
            });advance();
        }else if(stage==15 && ready() && ticks>25){client.interactionManager.attackBlock(POS,Direction.EAST);advance();}
        else if(stage==16 && ticks>35){
            require(!client.world.getBlockState(POS).isOf(BCFactoryBlocks.floodGate) && client.world.getBlockEntity(POS)==null,"Native client retained broken flood gate");
            work=client.getServer().submit(()->{
                var world=client.getServer().getOverworld();require(!world.getBlockState(POS).isOf(BCFactoryBlocks.floodGate) && world.getBlockEntity(POS)==null,"Native server retained broken flood gate");
                // Water flows into the vacated machine cell and can carry its recovered fluid shard.
                long amount=world.getEntitiesByClass(ItemEntity.class,new Box(POS).expand(4),e->true).stream().map(e->ItemFragileFluidContainer.getFluid(e.getStack())).filter(f->f.getFluidVariant().equals(WATER)).mapToLong(f->f.getAmount()).sum();
                require(amount==37,"Native removal did not recover exact remaining droplets");try(var tx=Transaction.openOuter()){require(stale.insert(WATER,1,tx)==0 && stale.extract(WATER,1,tx)==0,"Removed gate retained fluid access");}
            });advance();
        }else if(stage==17 && ready()){
            System.out.println("BUILDCRAFT FLOOD GATE CLIENT: native placement/bucket/wrench/break packets, 32 port models and reload, two full saved-world restarts, engine/wood/stone fluid delivery, exactly three water sources and 37 recovered droplets, owner advancement; failures=0");stage=30;client.scheduleStop();
        }
    }
    private static void checkModels(MinecraftClient client){
        for(var state:BCFactoryBlocks.floodGate.getStateManager().getStates())for(var side:Direction.values()){
            var model=client.getBlockRenderManager().getModel(state);var quads=model.getQuads(state,side,net.minecraft.util.math.random.Random.create(0));
            String expected=side==Direction.UP?"top":state.get(BlockFloodGate.CONNECTED_MAP.get(side))?"open":"closed";
            require(quads.size()==1 && quads.get(0).getSprite().getContents().getId().getPath().equals("blocks/flood_gate/"+expected),"Flood model ignored port state "+side+" "+state);
        }
        var stack=new ItemStack(BCFactoryBlocks.floodGate);require(client.getItemRenderer().getModel(stack,client.world,client.player,0)!=client.getBakedModelManager().getMissingModel(),"Flood item model missing");
        require(stack.getTooltip(client.player,net.minecraft.client.item.TooltipContext.Default.BASIC).stream().anyMatch(t->t.getString().contains("Remake by FelixMantra")),"Flood gate credit missing");
    }
    private static TileFloodGate gate(MinecraftClient client){return (TileFloodGate)client.getServer().getOverworld().getBlockEntity(POS);}
    private static TileFloodGate clientGate(MinecraftClient client){return (TileFloodGate)client.world.getBlockEntity(POS);}
    private static net.minecraft.server.network.ServerPlayerEntity player(MinecraftClient client){return client.getServer().getPlayerManager().getPlayer(client.player.getUuid());}
    private static void aim(net.minecraft.server.network.ServerPlayerEntity player){
        var eye=Vec3d.ofCenter(POS).add(3,2,0);var look=Vec3d.ofCenter(POS).subtract(eye).normalize();
        player.teleport(player.getServerWorld(),eye.x,eye.y-player.getEyeHeight(player.getPose()),eye.z,(float)Math.toDegrees(Math.atan2(-look.x,look.z)),(float)-Math.toDegrees(Math.asin(look.y)));player.setHeadYaw(player.getYaw());player.getAbilities().flying=true;player.sendAbilitiesUpdate();
    }
    private static void interact(MinecraftClient client){client.interactionManager.interactBlock(client.player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(POS).add(.5,0,0),Direction.EAST,POS,false));}
    private static void screenshot(MinecraftClient client,String name){net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(client.runDirectory,name,client.getFramebuffer(),message->{});}
    private void advance(){stage++;ticks=0;System.out.println("BUILDCRAFT FLOOD GATE CLIENT stage="+stage);}
    private boolean ready(){if(work==null || !work.isDone() || ticks<15)return false;work.join();return true;}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
