/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import buildcraft.api.enums.EnumEngineType;
import buildcraft.api.mj.*;
import buildcraft.api.properties.BuildCraftProperties;
import buildcraft.core.BCCoreBlocks;
import buildcraft.core.tile.TileEngineCreative;
import buildcraft.transport.*;
import buildcraft.transport.pipe.behaviour.PipeBehaviourObsidian;
import buildcraft.transport.tile.TilePipeHolder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.*;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.entity.*;
import net.minecraft.entity.projectile.*;
import net.minecraft.entity.vehicle.ChestMinecartEntity;
import net.minecraft.item.*;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.world.gen.*;
import net.minecraft.world.level.LevelInfo;

/** Native placement and collision, saved travelling items, live engine collection and native breaking. */
public final class ObsidianClientSmoke implements ClientModInitializer {
    private static final BlockPos POS=new BlockPos(2,-56,2),ENGINE=POS.south(),TARGET=POS.west(3);
    private int stage,ticks;private long deadline;private String saveName;private CompletableFuture<Void> work,reload;
    private ItemEntity collision,loose;private ChestMinecartEntity cart;private SpectralArrowEntity arrow;private TridentEntity trident;private IMjReceiver stale;
    @Override public void onInitializeClient(){if(Boolean.getBoolean("buildcraft.obsidianClientSmoke"))ClientTickEvents.END_CLIENT_TICK.register(this::tick);}
    private void tick(MinecraftClient client){
        if(stage==0){
            if(client.getOverlay()!=null || !(client.currentScreen instanceof TitleScreen))return;
            client.options.pauseOnLostFocus=false;client.options.getViewDistance().setValue(6);client.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
            deadline=System.nanoTime()+300_000_000_000L;saveName="obsidian-smoke-"+System.currentTimeMillis();stage=1;
            var rules=new GameRules();rules.get(GameRules.DO_MOB_SPAWNING).set(false,null);
            client.createIntegratedServerLoader().createAndStart(saveName,new LevelInfo("BuildCraft Obsidian — Remake by FelixMantra",GameMode.CREATIVE,false,Difficulty.PEACEFUL,true,rules,DataConfiguration.SAFE_MODE),new GeneratorOptions(4754L,false,false),registries->registries.get(RegistryKeys.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createDimensionsRegistryHolder());return;
        }
        if(stage==30)return;if(System.nanoTime()>deadline)throw new IllegalStateException("Obsidian client timeout stage "+stage);
        if(stage==6){if(client.getServer()!=null || client.world!=null)return;stage=7;ticks=0;client.createIntegratedServerLoader().start(new TitleScreen(),saveName);return;}
        if(client.world==null || client.player==null || client.getServer()==null)return;ticks++;if(ticks%20==0)client.getToastManager().clear();
        if(stage==1){
            work=client.getServer().submit(()->{
                var world=client.getServer().getOverworld();world.setTimeOfDay(6000);world.setBlockState(POS.down(),Blocks.STONE.getDefaultState());world.setBlockState(TARGET,Blocks.BARREL.getDefaultState());
                for(int i=1;i<=2;i++){var p=POS.west(i);world.setBlockState(p,BCTransportBlocks.pipeHolder.getDefaultState());((TilePipeHolder)world.getBlockEntity(p)).onPlacedBy(null,new ItemStack(BCTransportItems.pipeItemStone));}
                world.setBlockState(ENGINE.down(),Blocks.STONE.getDefaultState());world.setBlockState(ENGINE,BCCoreBlocks.engine.getDefaultState().with(BuildCraftProperties.ENGINE_TYPE,EnumEngineType.CREATIVE));((TileEngineCreative)world.getBlockEntity(ENGINE)).currentOutputIndex=4;
                player(client).getInventory().clear();player(client).getInventory().setStack(0,new ItemStack(BCTransportItems.pipeItemObsidian));player(client).getInventory().selectedSlot=0;player(client).currentScreenHandler.sendContentUpdates();aim(client);
            });advance();
        }else if(stage==2 && ready() && ticks>25){
            client.interactionManager.interactBlock(client.player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(POS.down()).add(0,.5,0),Direction.UP,POS.down(),false));advance();
        }else if(stage==3 && ticks>25){
            require(client.world.getBlockEntity(POS) instanceof TilePipeHolder holder && holder.getPipe().getBehaviour() instanceof PipeBehaviourObsidian,"Native client pipe placement failed");
            if(reload==null){checkModel(client);reload=client.reloadResources();return;}if(!reload.isDone() || client.getOverlay()!=null)return;reload.join();checkModel(client);shot(client,"obsidian-native-placement.png");
            work=client.getServer().submit(()->{
                require(tile(client).getPipe().isConnected(Direction.WEST),"Collector output missing");var world=client.getServer().getOverworld();var point=Vec3d.ofCenter(POS).add(0,1,0);
                collision=new ItemEntity(world,point.x,point.y,point.z,named(Items.DIAMOND,3));collision.setVelocity(0,-.1,0);world.spawnEntity(collision);
            });advance();
        }else if(stage==4 && ready()){
            if(!client.getServer().submit(()->collision.isRemoved()).join()){require(ticks<60,"Falling item never reached native collector collision");return;}
            work=client.getServer().submit(()->{require(total(client,Items.DIAMOND)==3 && moving(tile(client))>0,"Native collision did not put exact items into pipe before save");});work.join();shot(client,"obsidian-collision-in-flight.png");advance();
        }else if(stage==5 && ticks>2){stage=6;ticks=0;client.world.disconnect();client.disconnect(new TitleScreen());}
        else if(stage==7 && ticks>90){
            int restoredTotal=client.getServer().submit(()->total(client,Items.DIAMOND)).join();
            require(restoredTotal==3,"Restart lost travelling items: total="+restoredTotal);
            int delivered=client.getServer().submit(()->count(target(client),Items.DIAMOND)).join();
            if(delivered!=3){require(ticks<240,"Restored stone-pipe delivery timed out: delivered="+delivered);return;}
            work=client.getServer().submit(()->{
                var world=client.getServer().getOverworld();require(total(client,Items.DIAMOND)==3 && count(target(client),Items.DIAMOND)==3,"Restart lost travelling items or delivery");
                var point=Vec3d.ofCenter(POS).add(3,0,0);loose=new ItemEntity(world,point.x,point.y,point.z,named(Items.DIAMOND,4));loose.setNoGravity(true);loose.setVelocity(Vec3d.ZERO);world.spawnEntity(loose);
                cart=new ChestMinecartEntity(world,point.x,point.y,point.z+1.5);cart.setNoGravity(true);cart.setStack(0,named(Items.EMERALD,2));world.spawnEntity(cart);
                arrow=new SpectralArrowEntity(world,point.x,point.y+1,point.z-1.5);arrow.pickupType=PersistentProjectileEntity.PickupPermission.ALLOWED;arrow.setNoGravity(true);world.spawnEntity(arrow);
                var stack=named(Items.TRIDENT,1);stack.setDamage(29);stack.addEnchantment(net.minecraft.enchantment.Enchantments.UNBREAKING,2);trident=new TridentEntity(world,player(client),stack);trident.setPosition(point.add(0,2,0));trident.pickupType=PersistentProjectileEntity.PickupPermission.ALLOWED;trident.setNoGravity(true);world.spawnEntity(trident);
                aim(client);
            });advance();
        }else if(stage==8 && ready() && ticks>30){
            shot(client,"obsidian-powered-sources.png");work=client.getServer().submit(()->{client.getServer().getOverworld().setBlockState(ENGINE.down(),Blocks.REDSTONE_BLOCK.getDefaultState());});advance();
        }else if(stage==9 && ready() && ticks>200){
            boolean delivered=client.getServer().submit(()->loose.isRemoved() && arrow.isRemoved() && trident.isRemoved() && cart.isEmpty()
                && count(target(client),Items.DIAMOND)==7 && count(target(client),Items.EMERALD)==2 && count(target(client),Items.SPECTRAL_ARROW)==1 && count(target(client),Items.TRIDENT)==1).join();
            if(!delivered){require(ticks<400,"Engine collection/delivery did not finish within its bound");return;}
            work=client.getServer().submit(()->{
                require(loose.isRemoved() && arrow.isRemoved() && trident.isRemoved() && cart.isEmpty(),"Engine did not collect all native entity types");
                require(count(target(client),Items.DIAMOND)==7 && count(target(client),Items.EMERALD)==2 && count(target(client),Items.SPECTRAL_ARROW)==1 && count(target(client),Items.TRIDENT)==1,"Engine-driven delivery lost/duplicated items");
                int count=0;for(int slot=0;slot<target(client).size();slot++){var stack=target(client).getStack(slot);count+=stack.getCount();if(stack.isOf(Items.DIAMOND) || stack.isOf(Items.EMERALD) || stack.isOf(Items.TRIDENT))require(stack.getName().getString().equals("Obsidian native sample"),"Native delivery lost item name");if(stack.isOf(Items.TRIDENT))require(stack.getDamage()==29 && stack.hasEnchantments(),"Trident lost damage/enchantment");}
                require(count==11 && moving(tile(client))==0,"Unexpected delivered/remaining contents");stale=(IMjReceiver)MjAPI.CONNECTOR.find(client.getServer().getOverworld(),POS,Direction.UP);aim(client);
            });advance();
        }else if(stage==10 && ready() && ticks>25){
            shot(client,"obsidian-powered-delivered.png");client.interactionManager.interactBlock(client.player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(TARGET).add(0,0,-.5),Direction.NORTH,TARGET,false));advance();
        }else if(stage==11 && ticks>20){
            require(client.currentScreen instanceof net.minecraft.client.gui.screen.ingame.GenericContainerScreen,"Native destination screen did not open");shot(client,"obsidian-native-delivery-inventory.png");client.player.closeHandledScreen();work=client.getServer().submit(()->aim(client));advance();
        }else if(stage==12 && ready() && ticks>25){client.interactionManager.attackBlock(POS,Direction.NORTH);advance();}
        else if(stage==13 && ticks>25){
            require(client.world.getBlockEntity(POS)==null,"Native client retained broken collector");work=client.getServer().submit(()->{require(client.getServer().getOverworld().getBlockEntity(POS)==null && stale.getPowerRequested()==0 && stale.receivePower(MjAPI.MJ,false)==MjAPI.MJ,"Removed collector retained MJ service");});advance();
        }else if(stage==14 && ready()){
            System.out.println("BUILDCRAFT OBSIDIAN CLIENT: native placement/collision/break packets, model reload, saved travelling items and complete restart, live engine collection of loose items/minecart/spectral arrow/enchanted trident, exact 11-item delivery and native barrel screen; failures=0");stage=30;client.scheduleStop();
        }
    }
    private static ItemStack named(Item item,int count){var stack=new ItemStack(item,count);stack.setCustomName(Text.literal("Obsidian native sample"));return stack;}
    private static int count(BarrelBlockEntity barrel,Item item){int count=0;for(int i=0;i<barrel.size();i++)if(barrel.getStack(i).isOf(item))count+=barrel.getStack(i).getCount();return count;}
    private static int moving(TilePipeHolder tile){var drops=DefaultedList.<ItemStack>of();tile.getPipe().flow.addDrops(drops,0);return drops.stream().mapToInt(ItemStack::getCount).sum();}
    private static int total(MinecraftClient client,Item item){int count=count(target(client),item);for(int i=0;i<3;i++){var holder=(TilePipeHolder)client.getServer().getOverworld().getBlockEntity(POS.west(i));var drops=DefaultedList.<ItemStack>of();holder.getPipe().flow.addDrops(drops,0);count+=drops.stream().filter(s->s.isOf(item)).mapToInt(ItemStack::getCount).sum();}return count;}
    private static TilePipeHolder tile(MinecraftClient client){return (TilePipeHolder)client.getServer().getOverworld().getBlockEntity(POS);}
    private static BarrelBlockEntity target(MinecraftClient client){return (BarrelBlockEntity)client.getServer().getOverworld().getBlockEntity(TARGET);}
    private static net.minecraft.server.network.ServerPlayerEntity player(MinecraftClient client){return client.getServer().getPlayerManager().getPlayer(client.player.getUuid());}
    private static void aim(MinecraftClient client){
        var player=player(client);var eye=Vec3d.ofCenter(POS).add(-1,2,-3);var look=Vec3d.ofCenter(POS.west()).subtract(eye).normalize();
        player.teleport(player.getServerWorld(),eye.x,eye.y-player.getEyeHeight(player.getPose()),eye.z,(float)Math.toDegrees(Math.atan2(-look.x,look.z)),(float)-Math.toDegrees(Math.asin(look.y)));player.setHeadYaw(player.getYaw());player.getAbilities().flying=true;player.sendAbilitiesUpdate();
    }
    private static void checkModel(MinecraftClient client){
        var stack=new ItemStack(BCTransportItems.pipeItemObsidian);var model=client.getItemRenderer().getModel(stack,client.world,client.player,0);require(model!=client.getBakedModelManager().getMissingModel(),"Obsidian model missing");
        require(stack.getTooltip(client.player,net.minecraft.client.item.TooltipContext.Default.BASIC).stream().anyMatch(t->t.getString().contains("Remake by FelixMantra")),"Obsidian credit missing");require(client.getNetworkHandler().getRecipeManager().get(new Identifier("buildcrafttransport:pipe_obsidian_item")).isPresent(),"Obsidian survival recipe missing");
    }
    private static void shot(MinecraftClient client,String name){net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(client.runDirectory,name,client.getFramebuffer(),message->{});}
    private void advance(){stage++;ticks=0;System.out.println("BUILDCRAFT OBSIDIAN CLIENT stage="+stage);}
    private boolean ready(){if(work==null || !work.isDone() || ticks<5)return false;work.join();return true;}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
