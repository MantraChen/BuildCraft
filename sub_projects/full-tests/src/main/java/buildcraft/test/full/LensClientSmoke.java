/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import buildcraft.lib.misc.ColourUtil;
import buildcraft.silicon.*;
import buildcraft.silicon.client.BCSiliconClient;
import buildcraft.silicon.item.ItemPluggableLens;
import buildcraft.silicon.plug.PluggableLens;
import buildcraft.transport.*;
import buildcraft.transport.pipe.flow.PipeFlowItems;
import buildcraft.transport.tile.TilePipeHolder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.transfer.v1.item.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.*;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.world.gen.*;
import net.minecraft.world.level.LevelInfo;

/** Real native placement, transparent models, three-way routing and a complete save/restart. */
public final class LensClientSmoke implements ClientModInitializer {
    private static final BlockPos POS=new BlockPos(2,-56,2);
    private int stage,ticks;private long deadline;private String saveName;private CompletableFuture<Void> work,reload;
    private Object beforeReload;
    @Override public void onInitializeClient(){if(Boolean.getBoolean("buildcraft.lensClientSmoke"))ClientTickEvents.END_CLIENT_TICK.register(this::tick);}
    private void tick(MinecraftClient client){
        if(stage==0){
            if(client.getOverlay()!=null || !(client.currentScreen instanceof TitleScreen))return;
            client.options.pauseOnLostFocus=false;client.options.getViewDistance().setValue(6);client.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
            deadline=System.nanoTime()+240_000_000_000L;saveName="lenses-"+System.currentTimeMillis();stage=1;
            var rules=new GameRules();rules.get(GameRules.DO_MOB_SPAWNING).set(false,null);
            client.createIntegratedServerLoader().createAndStart(saveName,new LevelInfo("BuildCraft lenses — Remake by FelixMantra",GameMode.CREATIVE,false,Difficulty.PEACEFUL,true,rules,DataConfiguration.SAFE_MODE),
                new GeneratorOptions(4754L,false,false),registries->registries.get(RegistryKeys.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createDimensionsRegistryHolder());return;
        }
        if(stage==20)return;if(System.nanoTime()>deadline)throw new IllegalStateException("Lens client timeout stage "+stage);
        if(stage==7){if(client.getServer()!=null || client.world!=null)return;stage=8;ticks=0;client.createIntegratedServerLoader().start(new TitleScreen(),saveName);return;}
        if(client.world==null || client.player==null || client.getServer()==null)return;ticks++;if(ticks%20==0)client.getToastManager().clear();
        if(stage==1){
            work=client.getServer().submit(()->{
                var world=client.getServer().getOverworld();world.setTimeOfDay(1000);pipe(client,POS);
                place(client,Direction.WEST,DyeColor.RED,false);place(client,Direction.NORTH,DyeColor.RED,true);
                place(client,Direction.EAST,DyeColor.BLUE,true);place(client,Direction.SOUTH,null,true);
                pipe(client,POS.west());world.setBlockState(POS.west(2),Blocks.BARREL.getDefaultState());
                for(var side:List.of(Direction.NORTH,Direction.EAST,Direction.SOUTH)){pipe(client,POS.offset(side));world.setBlockState(POS.offset(side,2),Blocks.BARREL.getDefaultState());}
                tile(client).tick();player(client).setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);aim(client,Direction.WEST,true);
            });advance();
        }else if(stage==2 && ready() && ticks>25){
            if(reload==null){models(client);beforeReload=BCSiliconClient.LENS_MODEL.getQuads(Direction.WEST,DyeColor.RED,false);reload=client.reloadResources();return;}
            if(!reload.isDone() || client.getOverlay()!=null)return;reload.join();models(client);
            require(beforeReload!=BCSiliconClient.LENS_MODEL.getQuads(Direction.WEST,DyeColor.RED,false),"Lens cache retained old atlas generation");
            shot(client,"lens-red-input-and-filter.png");
            work=client.getServer().submit(()->{
                var input=ItemStorage.SIDED.find(client.getServer().getOverworld(),POS,Direction.WEST);
                try(var tx=Transaction.openOuter()){require(input!=null && input.insert(ItemVariant.of(Items.DIAMOND),4,tx)==4,"Native lens input failed");tx.commit();}
                var items=flow(client).writeToNbt().getList("items",NbtElement.COMPOUND_TYPE);require(items.getCompound(0).getByte("colour")==DyeColor.RED.getId()+1,"Native lens failed to paint");
                aim(client,Direction.WEST,true);
            });advance();
        }else if(stage==3 && ready() && ticks>100){
            work=client.getServer().submit(()->{require(count(client,Direction.NORTH,Items.DIAMOND)==4,"Painted diamonds missed red filter");aim(client,Direction.WEST,true);});advance();
        }else if(stage==4 && ready() && ticks>25){client.interactionManager.attackBlock(POS,Direction.WEST);advance();}
        else if(stage==5 && ticks>35){
            require(((TilePipeHolder)client.world.getBlockEntity(POS)).getPluggable(Direction.WEST)==null,"Native lens break failed");
            work=client.getServer().submit(()->{
                require(flow(client).injectItem(new ItemStack(Items.EMERALD,3),true,Direction.WEST,DyeColor.BLUE,.08).isEmpty(),"Blue input refused");
                require(flow(client).injectItem(new ItemStack(Items.GOLD_INGOT,2),true,Direction.WEST,null,.08).isEmpty(),"Uncoloured input refused");
                aim(client,Direction.SOUTH,true);
            });advance();
        }else if(stage==6 && ready() && ticks>100){
            shot(client,"lens-clear-and-blue-filters.png");
            work=client.getServer().submit(()->{
                require(count(client,Direction.EAST,Items.EMERALD)==3 && count(client,Direction.SOUTH,Items.GOLD_INGOT)==2,"Three-way filter routing failed");
                place(client,Direction.WEST,null,false);player(client).setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);
                require(flow(client).injectItem(new ItemStack(Items.DIAMOND,2),true,Direction.WEST,DyeColor.RED,.01).isEmpty(),"Clear lens refused red cargo");
                require(flow(client).writeToNbt().getList("items",NbtElement.COMPOUND_TYPE).getCompound(0).getByte("colour")==0,"Clear lens retained dye");
            });work.join();stage=7;ticks=0;client.world.disconnect();client.disconnect(new TitleScreen());
        }else if(stage==8 && ticks>35){
            var plug=(PluggableLens)((TilePipeHolder)client.world.getBlockEntity(POS)).getPluggable(Direction.WEST);
            require(plug!=null && plug.colour==null && !plug.isFilter,"Restart lost clear lens");models(client);
            work=client.getServer().submit(()->aim(client,Direction.WEST,true));advance();
        }else if(stage==9 && ready() && ticks==150){
            work=client.getServer().submit(()->System.out.println("BUILDCRAFT LENS CLIENT saved-cargo progress: "+deliveryState(client)));
        }else if(stage==9 && ready() && ticks>350){
            shot(client,"lens-clear-restored.png");
            work=client.getServer().submit(()->{
                require(count(client,Direction.SOUTH,Items.DIAMOND)==2 && count(client,Direction.NORTH,Items.DIAMOND)==4
                    && count(client,Direction.EAST,Items.EMERALD)==3 && count(client,Direction.SOUTH,Items.GOLD_INGOT)==2,"Restart lost or duplicated routed cargo: "+deliveryState(client));
                aim(client,Direction.NORTH,true);
            });advance();
        }else if(stage==10 && ready() && ticks>25){client.interactionManager.attackBlock(POS,Direction.NORTH);advance();}
        else if(stage==11 && ticks>35){
            require(((TilePipeHolder)client.world.getBlockEntity(POS)).getPluggable(Direction.NORTH)==null,"Native filter break failed");
            work=client.getServer().submit(()->{
                require(tile(client).getPipe().isConnected(Direction.NORTH),"Filter removal disconnected pipe");
                player(client).changeGameMode(GameMode.SURVIVAL);
                player(client).getAbilities().allowFlying=true;player(client).getAbilities().flying=true;player(client).sendAbilitiesUpdate();
                int slot=0;for(var data:ItemPluggableLens.variants())player(client).getInventory().setStack(slot++,BCSiliconItems.plugLens.getStack(data.colour(),data.isFilter()));
                player(client).currentScreenHandler.sendContentUpdates();
            });advance();
        }else if(stage==12 && ready() && ticks>25){
            client.setScreen(new net.minecraft.client.gui.screen.ingame.InventoryScreen(client.player));advance();
        }else if(stage==13 && ticks>25){
            shot(client,"lens-all-34-variants.png");
            System.out.println("BUILDCRAFT LENS CLIENT: all 34 variants and six-facing frame/glass geometry, colour/texture checks, resource reload, native placement/breaking, transactional painting, three-way routing of eleven exact items, clear lens and full-world restart; failures=0");
            stage=20;client.scheduleStop();
        }
    }
    private static void models(MinecraftClient client){
        // Vanilla populates creative entries lazily when the inventory is opened.
        ItemGroups.updateDisplayContext(client.world.getEnabledFeatures(),client.player.hasPermissionLevel(2),client.world.getRegistryManager());
        var creative=ItemGroups.getSearchGroup().getSearchTabStacks().stream().filter(stack->stack.isOf(BCSiliconItems.plugLens)).map(ItemPluggableLens::getData).collect(java.util.stream.Collectors.toSet());
        require(creative.equals(new HashSet<>(ItemPluggableLens.variants())),"Creative search lost lens/filter variants: "+creative);
        for(var data:ItemPluggableLens.variants()){
            var stack=BCSiliconItems.plugLens.getStack(data.colour(),data.isFilter());
            require(client.getItemRenderer().getModel(stack,client.world,client.player,0)!=client.getBakedModelManager().getMissingModel(),"Lens item model missing");
            require(!stack.getName().getString().contains("item.buildcraft") && !stack.getName().getString().contains("color.minecraft"),"Untranslated lens name");
            require(stack.getTooltip(client.player,net.minecraft.client.item.TooltipContext.Default.BASIC).stream().anyMatch(t->t.getString().contains("Remake by FelixMantra")),"Lens credit missing");
            for(var face:Direction.values()){
                var quads=BCSiliconClient.LENS_MODEL.getQuads(face,data.colour(),data.isFilter());
                require(quads.frame().length==(data.isFilter()?36:24) && quads.glass().length==(data.isFilter() && data.colour()==null?0:2),"Wrong lens layer geometry "+data+" "+face+" frame="+quads.frame().length+" glass="+quads.glass().length);
                var box=new PluggableLens(BCSiliconPlugs.lens,(TilePipeHolder)client.world.getBlockEntity(POS),face,data.colour(),data.isFilter()).getBoundingBox().expand(.0001);
                for(var layer:List.of(quads.frame(),quads.glass()))for(var quad:layer){
                    require(quad.getSprite()!=null && !quad.getSprite().getContents().getId().getPath().equals("missingno"),"Missing lens sprite "+data);
                    for(var vertex:List.of(quad.vertex_0,quad.vertex_1,quad.vertex_2,quad.vertex_3))
                        require(box.contains(vertex.position_x,vertex.position_y,vertex.position_z),"Lens geometry rotated outside face "+face);
                }
                if(data.colour()!=null)for(var quad:quads.glass()){
                    int expected=ColourUtil.getLightHex(data.colour());var vertex=quad.vertex_0;
                    require(vertex.colour_r==((expected>>16)&255) && vertex.colour_g==((expected>>8)&255) && vertex.colour_b==(expected&255),"Wrong glass dye "+data+" actual="+vertex);
                }
            }
        }
    }
    private static void place(MinecraftClient client,Direction face,DyeColor colour,boolean filter){
        var player=player(client);aim(client,face,false);var stack=BCSiliconItems.plugLens.getStack(colour,filter);player.setStackInHand(Hand.MAIN_HAND,stack);
        var result=player.interactionManager.interactBlock(player,client.getServer().getOverworld(),stack,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(POS),face,POS,false));
        require(result.isAccepted() && tile(client).getPluggable(face) instanceof PluggableLens,"Native lens placement failed "+face);
    }
    private static void aim(MinecraftClient client,Direction face,boolean angled){
        var player=player(client);var normal=Vec3d.of(face.getVector());var target=Vec3d.ofCenter(POS).add(normal.multiply(.46));
        var eye=Vec3d.ofCenter(POS).add(normal.multiply(3)).add(angled?(face.getAxis()==Direction.Axis.X?new Vec3d(0,1.8,-1.3):new Vec3d(-1.3,1.8,0)):Vec3d.ZERO);
        var look=target.subtract(eye).normalize();player.teleport(player.getServerWorld(),eye.x,eye.y-player.getEyeHeight(player.getPose()),eye.z,(float)Math.toDegrees(Math.atan2(-look.x,look.z)),(float)-Math.toDegrees(Math.asin(look.y)));player.setHeadYaw(player.getYaw());player.getAbilities().flying=true;player.sendAbilitiesUpdate();
    }
    private static void pipe(MinecraftClient client,BlockPos pos){var world=client.getServer().getOverworld();world.setBlockState(pos,BCTransportBlocks.pipeHolder.getDefaultState());((TilePipeHolder)world.getBlockEntity(pos)).onPlacedBy(null,new ItemStack(BCTransportItems.pipeItemStone));}
    private static net.minecraft.server.network.ServerPlayerEntity player(MinecraftClient client){return client.getServer().getPlayerManager().getPlayer(client.player.getUuid());}
    private static TilePipeHolder tile(MinecraftClient client){return (TilePipeHolder)client.getServer().getOverworld().getBlockEntity(POS);}
    private static PipeFlowItems flow(MinecraftClient client){return (PipeFlowItems)tile(client).getPipe().getFlow();}
    private static int count(MinecraftClient client,Direction side,Item item){var barrel=(BarrelBlockEntity)client.getServer().getOverworld().getBlockEntity(POS.offset(side,2));int sum=0;for(int i=0;i<barrel.size();i++)if(barrel.getStack(i).isOf(item))sum+=barrel.getStack(i).getCount();return sum;}
    private static String deliveryState(MinecraftClient client){
        var result=new StringBuilder("north diamonds="+count(client,Direction.NORTH,Items.DIAMOND)+", east emeralds="+count(client,Direction.EAST,Items.EMERALD)+", south gold="+count(client,Direction.SOUTH,Items.GOLD_INGOT)+", south diamonds="+count(client,Direction.SOUTH,Items.DIAMOND));
        for(var pos:List.of(POS,POS.north(),POS.east(),POS.south(),POS.west())){
            var tile=(TilePipeHolder)client.getServer().getOverworld().getBlockEntity(pos);
            result.append("; ").append(pos).append(" cargo=").append(tile.getPipe().getFlow().writeToNbt());
        }
        return result.toString();
    }
    private static void shot(MinecraftClient client,String name){net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(client.runDirectory,name,client.getFramebuffer(),message->{});}
    private void advance(){stage++;ticks=0;System.out.println("BUILDCRAFT LENS CLIENT stage="+stage);}
    private boolean ready(){if(work==null || !work.isDone() || ticks<15)return false;work.join();return true;}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
