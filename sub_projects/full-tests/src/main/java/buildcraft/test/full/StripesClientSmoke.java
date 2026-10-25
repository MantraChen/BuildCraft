/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;

import java.util.concurrent.CompletableFuture;
import buildcraft.api.enums.EnumEngineType;
import buildcraft.api.mj.*;
import buildcraft.api.properties.BuildCraftProperties;
import buildcraft.api.transport.pipe.IFlowItems;
import buildcraft.core.*;
import buildcraft.core.tile.TileEngineCreative;
import buildcraft.transport.*;
import buildcraft.transport.client.model.StripesModel;
import buildcraft.transport.pipe.behaviour.PipeBehaviourStripes;
import buildcraft.transport.tile.TilePipeHolder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
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

/** Actual placement/wrench packets, complete restart and item-flow driven stripes actions. */
public final class StripesClientSmoke implements ClientModInitializer {
    private static final BlockPos POS=new BlockPos(2,-56,2),ENGINE=POS.south(),TARGET=POS.west(3);
    private int stage,ticks;private long deadline;private String saveName;private CompletableFuture<Void> work,reload;
    private PipeBehaviourStripes stale;private final StripesModel model=new StripesModel();private Object oldQuads;
    @Override public void onInitializeClient(){if(Boolean.getBoolean("buildcraft.stripesClientSmoke"))ClientTickEvents.END_CLIENT_TICK.register(this::tick);}
    private void tick(MinecraftClient client){
        if(stage==0){
            if(client.getOverlay()!=null || !(client.currentScreen instanceof TitleScreen))return;
            client.options.pauseOnLostFocus=false;client.options.getViewDistance().setValue(6);client.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
            deadline=System.nanoTime()+360_000_000_000L;saveName="stripes-smoke-"+System.currentTimeMillis();stage=1;
            var rules=new GameRules();rules.get(GameRules.DO_MOB_SPAWNING).set(false,null);
            client.createIntegratedServerLoader().createAndStart(saveName,new LevelInfo("BuildCraft Stripes — Remake by FelixMantra",GameMode.CREATIVE,false,Difficulty.PEACEFUL,true,rules,DataConfiguration.SAFE_MODE),new GeneratorOptions(4754L,false,false),registries->registries.get(RegistryKeys.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createDimensionsRegistryHolder());return;
        }
        if(stage==30)return;if(System.nanoTime()>deadline)throw new IllegalStateException("Stripes client timeout stage "+stage);
        if(stage==6){if(client.getServer()!=null || client.world!=null)return;stage=7;ticks=0;client.createIntegratedServerLoader().start(new TitleScreen(),saveName);return;}
        if(client.world==null || client.player==null || client.getServer()==null)return;ticks++;if(ticks%20==0)client.getToastManager().clear();
        if(stage==1){
            work=client.getServer().submit(()->{
                var world=client.getServer().getOverworld();world.setTimeOfDay(6000);world.setBlockState(POS.down(),Blocks.STONE.getDefaultState());world.setBlockState(TARGET,Blocks.BARREL.getDefaultState());
                for(int i=1;i<=2;i++){var p=POS.west(i);world.setBlockState(p,BCTransportBlocks.pipeHolder.getDefaultState());((TilePipeHolder)world.getBlockEntity(p)).onPlacedBy(null,new ItemStack(BCTransportItems.pipeItemStone));}
                world.setBlockState(ENGINE.down(),Blocks.STONE.getDefaultState());world.setBlockState(ENGINE,BCCoreBlocks.engine.getDefaultState().with(BuildCraftProperties.ENGINE_TYPE,EnumEngineType.CREATIVE));((TileEngineCreative)world.getBlockEntity(ENGINE)).currentOutputIndex=4;
                player(client).getInventory().clear();player(client).getInventory().setStack(0,new ItemStack(BCTransportItems.pipeItemStripes));player(client).getInventory().selectedSlot=0;player(client).currentScreenHandler.sendContentUpdates();aim(client,POS);
            });advance();
        }else if(stage==2 && ready() && ticks>25){
            client.interactionManager.interactBlock(client.player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(POS.down()).add(0,.5,0),Direction.UP,POS.down(),false));advance();
        }else if(stage==3 && ticks>25){
            require(client.world.getBlockEntity(POS) instanceof TilePipeHolder holder && holder.getPipe().getBehaviour() instanceof PipeBehaviourStripes stripe && stripe.direction==Direction.EAST,"Native stripes placement/direction sync failed");
            if(reload==null){checkModels(client);oldQuads=model.getQuads(Direction.EAST);reload=client.reloadResources();return;}
            if(!reload.isDone() || client.getOverlay()!=null)return;reload.join();checkModels(client);require(oldQuads!=model.getQuads(Direction.EAST),"Stripes model retained old atlas geometry");shot(client,"stripes-native-direction.png");
            work=client.getServer().submit(()->{var world=client.getServer().getOverworld();world.setBlockState(POS.east(),Blocks.STONE.getDefaultState());head(client,POS).receivePower(30*MjAPI.MJ,false);});advance();
        }else if(stage==4 && ready() && ticks>12){
            work=client.getServer().submit(()->require(head(client,POS).getProgressMicroMj()==30*MjAPI.MJ && head(client,POS).getStoredMicroMj()==0,"Initial paid mining work missing"));work.join();shot(client,"stripes-paid-mining.png");advance();
        }else if(stage==5 && ticks>2){stage=6;ticks=0;client.world.disconnect();client.disconnect(new TitleScreen());}
        else if(stage==7 && ticks>30){
            work=client.getServer().submit(()->{require(head(client,POS).getProgressMicroMj()==30*MjAPI.MJ,"Restart lost paid mining work");client.getServer().getOverworld().setBlockState(ENGINE.down(),Blocks.REDSTONE_BLOCK.getDefaultState());aim(client,POS);});advance();
        }else if(stage==8 && ready()){
            if(!client.getServer().submit(()->count(client,Items.COBBLESTONE)==1).join()){require(ticks<300,"Engine did not finish native mining/delivery");return;}
            work=client.getServer().submit(()->{
                var world=client.getServer().getOverworld();world.setBlockState(ENGINE.down(),Blocks.STONE.getDefaultState());world.setBlockState(ENGINE,Blocks.AIR.getDefaultState());
                // Empty the remaining battery by saving the live head, so subsequent use operations cannot be mined away.
                var tile=tile(client,POS);var nbt=tile.createNbt();nbt.getCompound("pipe").getCompound("beh").getCompound("battery").putLong("stored",0);tile.readNbt(nbt);tile.onLoad();
                inject(client,POS,new ItemStack(Items.STONE,3));aim(client,POS);
            });advance();
        }else if(stage==9 && ready()){
            if(!client.getServer().submit(()->count(client,Items.STONE)==2).join()){require(ticks<300,"Native block-use remainder did not arrive");return;}
            shot(client,"stripes-place-block.png");work=client.getServer().submit(()->{
                var world=client.getServer().getOverworld();require(world.getBlockState(POS.east()).isOf(Blocks.STONE),"Travelling block did not place");
                world.setBlockState(POS.east(),Blocks.DIRT.getDefaultState());inject(client,POS,new ItemStack(Items.IRON_HOE));
            });advance();
        }else if(stage==10 && ready()){
            if(!client.getServer().submit(()->count(client,Items.IRON_HOE)==1).join()){require(ticks<300,"Native hoe did not return");return;}
            work=client.getServer().submit(()->{
                var world=client.getServer().getOverworld();require(world.getBlockState(POS.east()).isOf(Blocks.FARMLAND),"Native hoe did not till");
                var barrel=(BarrelBlockEntity)world.getBlockEntity(TARGET);for(int i=0;i<barrel.size();i++)if(barrel.getStack(i).isOf(Items.IRON_HOE))require(barrel.getStack(i).getDamage()==1,"Native hoe wear wrong");
                world.setBlockState(POS.east(),Blocks.AIR.getDefaultState());stale=head(client,POS);inject(client,POS,new ItemStack(BCTransportItems.pipeItemStone,2));
            });advance();
        }else if(stage==11 && ready()){
            if(!(client.world.getBlockEntity(POS.east()) instanceof TilePipeHolder moved && moved.getPipe().getBehaviour() instanceof PipeBehaviourStripes)){require(ticks<150,"Extension did not synchronize new head");return;}
            work=client.getServer().submit(()->{require(!stale.isLive() && tile(client,POS).getPipe().getDefinition()==BCTransportPipes.stoneItem,"Old holder survived extension");aim(client,POS.east());});advance();
        }else if(stage==12 && ready() && ticks>25){
            shot(client,"stripes-extended-head.png");work=client.getServer().submit(()->inject(client,POS.east(),new ItemStack(BCTransportItems.pipeItemVoid)));advance();
        }else if(stage==13 && ready()){
            if(!client.getServer().submit(()->client.getServer().getOverworld().isAir(POS.east()) && count(client,BCTransportItems.pipeItemStone)==2 && count(client,BCTransportItems.pipeItemVoid)==1).join()){require(ticks<400,"Retraction/refunds did not finish");return;}
            require(client.world.getBlockEntity(POS) instanceof TilePipeHolder holder && holder.getPipe().getBehaviour() instanceof PipeBehaviourStripes,"Retraction client retained stale base pipe");
            shot(client,"stripes-retracted-head.png");work=client.getServer().submit(()->{
                require(count(client,Items.COBBLESTONE)==1 && count(client,Items.STONE)==2 && count(client,Items.IRON_HOE)==1,"Previously delivered contents changed");
                player(client).getInventory().setStack(0,new ItemStack(BCCoreItems.wrench));player(client).currentScreenHandler.sendContentUpdates();aim(client,POS);
            });advance();
        }else if(stage==14 && ready() && ticks>25){
            client.interactionManager.interactBlock(client.player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(POS).add(0,0,-.2),Direction.NORTH,POS,false));advance();
        }else if(stage==15 && ticks>25){
            require(((PipeBehaviourStripes)((TilePipeHolder)client.world.getBlockEntity(POS)).getPipe().getBehaviour()).direction==Direction.DOWN,"Native wrench direction packet failed");shot(client,"stripes-wrench-direction.png");
            work=client.getServer().submit(()->{player(client).getInventory().clear();player(client).currentScreenHandler.sendContentUpdates();aim(client,TARGET);});advance();
        }else if(stage==16 && ready() && ticks>25){
            client.interactionManager.interactBlock(client.player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(TARGET).add(0,0,-.5),Direction.NORTH,TARGET,false));advance();
        }else if(stage==17 && ticks>20){
            require(client.currentScreen instanceof net.minecraft.client.gui.screen.ingame.GenericContainerScreen,"Native barrel screen failed");shot(client,"stripes-native-return-inventory.png");client.player.closeHandledScreen();
            System.out.println("BUILDCRAFT STRIPES CLIENT: native placement/wrench packets, six gold-rod models and resource reload, saved paid mining work and full restart, engine-driven mining, item-flow block/hoe use, extension/retraction and exact returned tools/pipes/catalyst; failures=0");stage=30;client.scheduleStop();
        }
    }
    private static void inject(MinecraftClient client,BlockPos pos,ItemStack stack){((IFlowItems)tile(client,pos).getPipe().flow).insertItemsForce(stack,Direction.WEST,null,.2);}
    private static TilePipeHolder tile(MinecraftClient client,BlockPos pos){return (TilePipeHolder)client.getServer().getOverworld().getBlockEntity(pos);}
    private static PipeBehaviourStripes head(MinecraftClient client,BlockPos pos){return (PipeBehaviourStripes)tile(client,pos).getPipe().getBehaviour();}
    private static int count(MinecraftClient client,Item item){var barrel=(BarrelBlockEntity)client.getServer().getOverworld().getBlockEntity(TARGET);int total=0;for(int i=0;i<barrel.size();i++)if(barrel.getStack(i).isOf(item))total+=barrel.getStack(i).getCount();return total;}
    private static net.minecraft.server.network.ServerPlayerEntity player(MinecraftClient client){return client.getServer().getPlayerManager().getPlayer(client.player.getUuid());}
    private static void aim(MinecraftClient client,BlockPos at){
        var player=player(client);var eye=Vec3d.ofCenter(at).add(1,1.5,-3);var look=Vec3d.ofCenter(at).subtract(eye).normalize();
        player.teleport(player.getServerWorld(),eye.x,eye.y-player.getEyeHeight(player.getPose()),eye.z,(float)Math.toDegrees(Math.atan2(-look.x,look.z)),(float)-Math.toDegrees(Math.asin(look.y)));player.setHeadYaw(player.getYaw());player.getAbilities().flying=true;player.sendAbilitiesUpdate();
    }
    private void checkModels(MinecraftClient client){
        var stack=new ItemStack(BCTransportItems.pipeItemStripes);require(client.getItemRenderer().getModel(stack,client.world,client.player,0)!=client.getBakedModelManager().getMissingModel(),"Stripes item model missing");
        require(stack.getTooltip(client.player,net.minecraft.client.item.TooltipContext.Default.BASIC).stream().anyMatch(t->t.getString().contains("Remake by FelixMantra")),"Stripes credit missing");
        require(client.getNetworkHandler().getRecipeManager().get(new Identifier("buildcrafttransport:pipe_stripes_item")).isPresent(),"Stripes survival recipe missing");
        for(var side:Direction.values()){
            var quads=model.getQuads(side);require(quads.length==6,"Direction rod geometry missing "+side);double x=0,y=0,z=0;
            for(var quad:quads){
                require(quad.getSprite()!=null && quad.getSprite().getContents().getId().toString().equals("minecraft:block/gold_block"),"Gold rod texture missing");
                for(var vertex:new buildcraft.lib.client.model.MutableVertex[]{quad.vertex_0,quad.vertex_1,quad.vertex_2,quad.vertex_3}){x+=vertex.position_x;y+=vertex.position_y;z+=vertex.position_z;}
            }
            require(new Vec3d(x/24,y/24,z/24).distanceTo(new Vec3d(.5+side.getOffsetX()*.375,.5+side.getOffsetY()*.375,.5+side.getOffsetZ()*.375))<.0001,"Direction rod rotated to wrong face "+side);
        }
    }
    private static void shot(MinecraftClient client,String name){net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(client.runDirectory,name,client.getFramebuffer(),message->{});}
    private void advance(){stage++;ticks=0;System.out.println("BUILDCRAFT STRIPES CLIENT stage="+stage);}
    private boolean ready(){if(work==null || !work.isDone() || ticks<5)return false;work.join();return true;}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
