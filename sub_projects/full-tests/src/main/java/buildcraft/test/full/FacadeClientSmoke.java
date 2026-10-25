/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import buildcraft.api.transport.EnumWirePart;
import buildcraft.core.BCCoreStatements;
import buildcraft.lib.statement.*;
import buildcraft.silicon.*;
import buildcraft.silicon.client.BCSiliconClient;
import buildcraft.silicon.gate.*;
import buildcraft.silicon.item.ItemPluggableFacade;
import buildcraft.silicon.plug.*;
import buildcraft.transport.*;
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
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.world.gen.*;
import net.minecraft.world.level.LevelInfo;

public final class FacadeClientSmoke implements ClientModInitializer {
    private static final BlockPos POS=new BlockPos(2,-56,2);
    private int stage,ticks;private long deadline;private String saveName;private CompletableFuture<Void> work,reload;private Object previous;
    @Override public void onInitializeClient(){if(Boolean.getBoolean("buildcraft.facadeClientSmoke"))ClientTickEvents.END_CLIENT_TICK.register(this::tick);}
    private void tick(MinecraftClient client){
        if(stage==0){
            if(client.getOverlay()!=null || !(client.currentScreen instanceof TitleScreen))return;
            client.options.pauseOnLostFocus=false;client.options.getViewDistance().setValue(6);client.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
            deadline=System.nanoTime()+240_000_000_000L;saveName="facades-"+System.currentTimeMillis();stage=1;
            var rules=new GameRules();rules.get(GameRules.DO_MOB_SPAWNING).set(false,null);
            client.createIntegratedServerLoader().createAndStart(saveName,new LevelInfo("BuildCraft facades — Remake by FelixMantra",GameMode.CREATIVE,false,Difficulty.PEACEFUL,true,rules,DataConfiguration.SAFE_MODE),
                new GeneratorOptions(4754L,false,false),registries->registries.get(RegistryKeys.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createDimensionsRegistryHolder());return;
        }
        if(stage==20)return;if(System.nanoTime()>deadline)throw new IllegalStateException("Facade client timeout stage "+stage);
        if(stage==6){if(client.getServer()!=null || client.world!=null)return;stage=7;ticks=0;client.createIntegratedServerLoader().start(new TitleScreen(),saveName);return;}
        if(client.world==null || client.player==null || client.getServer()==null)return;ticks++;
        if(ticks%20==0)client.getToastManager().clear();
        if(stage==1){
            work=client.getServer().submit(()->{
                var world=client.getServer().getOverworld();world.setTimeOfDay(1000);pipe(client,POS);
                pipe(client,POS.west());pipe(client,POS.north());
                world.setBlockState(POS.west(2),Blocks.BARREL.getDefaultState());world.setBlockState(POS.north(2),Blocks.BARREL.getDefaultState());
                pipe(client,POS.south());world.setBlockState(POS.south(2),Blocks.BARREL.getDefaultState());
                place(client,Direction.WEST,basic(Blocks.OAK_LOG.getDefaultState().with(PillarBlock.AXIS,Direction.Axis.Z),true));
                place(client,Direction.NORTH,phases());place(client,Direction.EAST,basic(Blocks.RED_STAINED_GLASS.getDefaultState(),false));
                place(client,Direction.DOWN,basic(Blocks.GRASS_BLOCK.getDefaultState(),true));
                tile(client).wireManager.addPart(EnumWirePart.WEST_UP_NORTH,DyeColor.RED);
                var gate=new PluggableGate(BCSiliconPlugs.gate,tile(client),Direction.UP,new GateVariant(EnumGateLogic.OR,EnumGateMaterial.IRON,EnumGateModifier.NO_MODIFIER));tile(client).replacePluggable(Direction.UP,gate);
                gate.logic.statements[0].trigger.set(new TriggerWrapper.TriggerWrapperInternal(BCCoreStatements.TRIGGER_TRUE));
                gate.logic.statements[0].action.set(new ActionWrapper.ActionWrapperInternal(BCTransportStatements.ACTION_PIPE_SIGNAL[DyeColor.RED.ordinal()]));
                tile(client).tick();player(client).setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);aim(client,Direction.EAST,true);
            });advance();
        }else if(stage==2 && ready() && ticks>30){
            require(((PluggableFacade)clientTile(client).getPluggable(Direction.NORTH)).activeIndex()==1,"Live wire phase not synchronized");
            if(reload==null){models(client);previous=BCSiliconClient.FACADE_MODEL.getQuads(Blocks.STONE.getDefaultState(),Direction.NORTH,false);reload=client.reloadResources();return;}
            if(!reload.isDone() || client.getOverlay()!=null)return;reload.join();models(client);
            require(previous!=BCSiliconClient.FACADE_MODEL.getQuads(Blocks.STONE.getDefaultState(),Direction.NORTH,false),"Facade model kept stale atlas");
            shot(client,"facade-glass-and-active-phase.png");
            work=client.getServer().submit(()->{
                var world=client.getServer().getOverworld();var input=ItemStorage.SIDED.find(world,POS,Direction.WEST);var blocked=ItemStorage.SIDED.find(world,POS,Direction.NORTH);
                try(var tx=Transaction.openOuter()){
                    require(blocked==null || blocked.insert(ItemVariant.of(Items.DIAMOND),1,tx)==0,"Solid facade accepts cargo");
                    require(input!=null && input.insert(ItemVariant.of(Items.DIAMOND),3,tx)==3,"Hollow facade refuses cargo");tx.commit();
                }aim(client,Direction.WEST,true);
            });advance();
        }else if(stage==3 && ready() && ticks>120){
            shot(client,"facade-hollow-log.png");
            work=client.getServer().submit(()->{
                require(count(client)==3,"Hollow facade route lost/duplicated cargo");tile(client).replacePluggable(Direction.UP,null);aim(client,Direction.NORTH,true);
            });advance();
        }else if(stage==4 && ready() && ticks>40){
            require(((PluggableFacade)clientTile(client).getPluggable(Direction.NORTH)).activeIndex()==0,"Removed emitter left stale client phase");
            shot(client,"facade-default-phase.png");stage=5;ticks=0;
        }else if(stage==5 && ticks>15){
            stage=6;ticks=0;client.world.disconnect();client.disconnect(new TitleScreen());
        }else if(stage==7 && ticks>40){
            var north=(PluggableFacade)clientTile(client).getPluggable(Direction.NORTH);var west=(PluggableFacade)clientTile(client).getPluggable(Direction.WEST);
            require(north!=null && north.states.writeToNbt().equals(phases().writeToNbt()) && north.activeIndex()==0,"Restart lost phase data");
            require(west!=null && west.isHollow() && west.active().state.get(PillarBlock.AXIS)==Direction.Axis.Z,"Restart lost hollow/log axis");
            models(client);work=client.getServer().submit(()->{require(count(client)==3,"Restart changed delivered cargo");aim(client,Direction.EAST,false);});advance();
        }else if(stage==8 && ready() && ticks>25){client.interactionManager.attackBlock(POS,Direction.EAST);advance();}
        else if(stage==9 && ticks>30){
            require(clientTile(client).getPluggable(Direction.EAST)==null && clientTile(client).getPluggable(Direction.NORTH)!=null,"Native facade break removed pipe/other faces");
            work=client.getServer().submit(()->{
                var player=player(client);player.changeGameMode(GameMode.SURVIVAL);player.getAbilities().allowFlying=true;player.getAbilities().flying=true;player.sendAbilitiesUpdate();
                int slot=0;for(var state:samples())for(boolean hollow:new boolean[]{false,true})if(slot<35)player.getInventory().setStack(slot++,BCSiliconItems.plugFacade.createFacadeStack(basic(state,hollow)));
                player.getInventory().setStack(35,BCSiliconItems.plugFacade.createFacadeStack(phases()));player.currentScreenHandler.sendContentUpdates();
            });advance();
        }else if(stage==10 && ready() && ticks>25){client.setScreen(new net.minecraft.client.gui.screen.ingame.InventoryScreen(client.player));advance();}
        else if(stage==11 && ticks>30){
            shot(client,"facade-inventory-variants.png");
            System.out.println("BUILDCRAFT FACADE CLIENT: all creative variants; solid/hollow six-facing bevel geometry, sprites, tint and UV bounds; resource reload; native placement/removal; wire phase synchronization; exact three-item delivery; complete save/restart; failures=0");stage=20;client.scheduleStop();
        }
    }
    private static List<BlockState> samples(){return List.of(Blocks.STONE.getDefaultState(),Blocks.GLASS.getDefaultState(),Blocks.RED_STAINED_GLASS.getDefaultState(),Blocks.GRASS_BLOCK.getDefaultState(),
        Blocks.OAK_LOG.getDefaultState().with(PillarBlock.AXIS,Direction.Axis.X),Blocks.OAK_LOG.getDefaultState(),Blocks.OAK_LOG.getDefaultState().with(PillarBlock.AXIS,Direction.Axis.Z),
        Blocks.OAK_PLANKS.getDefaultState(),Blocks.BRICKS.getDefaultState(),Blocks.DIAMOND_BLOCK.getDefaultState(),Blocks.OBSIDIAN.getDefaultState(),Blocks.BROWN_MUSHROOM_BLOCK.getDefaultState(),
        Blocks.OAK_LEAVES.getDefaultState(),Blocks.CRAFTING_TABLE.getDefaultState(),Blocks.NOTE_BLOCK.getDefaultState(),Blocks.REDSTONE_BLOCK.getDefaultState(),Blocks.WHITE_WOOL.getDefaultState());}
    private static FacadeInstance basic(BlockState state,boolean hollow){return FacadeInstance.createSingle(FacadeStateManager.INSTANCE.find(state),hollow);}
    private static FacadeInstance phases(){return new FacadeInstance(new FacadePhasedState[]{
        FacadeStateManager.INSTANCE.find(Blocks.STONE.getDefaultState()).createPhased(null),
        FacadeStateManager.INSTANCE.find(Blocks.OAK_LOG.getDefaultState().with(PillarBlock.AXIS,Direction.Axis.X)).createPhased(DyeColor.RED),
        FacadeStateManager.INSTANCE.find(Blocks.GLASS.getDefaultState()).createPhased(DyeColor.BLUE)},false);}
    private static void models(MinecraftClient client){
        ItemGroups.updateDisplayContext(client.world.getEnabledFeatures(),client.player.hasPermissionLevel(2),client.world.getRegistryManager());
        var actual=ItemGroups.getSearchGroup().getSearchTabStacks().stream().filter(s->s.isOf(BCSiliconItems.plugFacade)).map(s->ItemPluggableFacade.getStates(s).writeToNbt()).collect(java.util.stream.Collectors.toSet());
        var expected=new HashSet<net.minecraft.nbt.NbtCompound>();
        expected.add(ItemPluggableFacade.phasedExample().writeToNbt());
        for(var info:FacadeStateManager.INSTANCE.getValidFacades())if(info.isVisible)for(boolean hollow:new boolean[]{false,true})expected.add(FacadeInstance.createSingle(info,hollow).writeToNbt());
        require(actual.equals(expected),"Creative facade variants missing/duplicated");
        for(var state:samples())for(boolean hollow:new boolean[]{false,true}){
            var stack=BCSiliconItems.plugFacade.createFacadeStack(basic(state,hollow));
            require(client.getItemRenderer().getModel(stack,client.world,client.player,0)!=client.getBakedModelManager().getMissingModel(),"Missing facade item model");
            var itemMatrix=new net.minecraft.client.util.math.MatrixStack();
            client.getItemRenderer().getModel(stack,client.world,client.player,0).getTransformation().getTransformation(net.minecraft.client.render.model.json.ModelTransformationMode.GUI).apply(false,itemMatrix);
            itemMatrix.translate(-.5,-.5,-.5);itemMatrix.translate(7/16.0,0,0);
            for(var q:BCSiliconClient.FACADE_MODEL.getQuads(state,Direction.WEST,hollow))for(var v:List.of(q.vertex_0,q.vertex_1,q.vertex_2,q.vertex_3)){
                var projected=new org.joml.Vector3f((float)v.position_x,(float)v.position_y,(float)v.position_z).mulPosition(itemMatrix.peek().getPositionMatrix());
                require(Math.abs(projected.x)<=.5 && Math.abs(projected.y)<=.5,"Facade icon exceeds inventory slot");
            }
            require(!stack.getName().getString().contains("item.buildcraft"),"Untranslated facade name");
            require(stack.getTooltip(client.player,net.minecraft.client.item.TooltipContext.Default.BASIC).stream().anyMatch(t->t.getString().contains("Remake by FelixMantra")),"Facade remake credit missing");
            for(var face:Direction.values()){
                var quads=BCSiliconClient.FACADE_MODEL.getQuads(state,face,hollow);
                require(quads.size()>=(hollow?16:6),"Incomplete facade surface "+state+" "+face+" count="+quads.size());
                if(!hollow){
                    var original=client.getBlockRenderManager().getModel(state).getQuads(state,face,net.minecraft.util.math.random.Random.create(0));
                    if(!original.isEmpty()){
                        var source=new buildcraft.lib.client.model.MutableQuad().fromBakedBlock(original.get(0));var front=quads.get(0);
                        require(front.getSprite()==source.getSprite(),"Facade used another face's texture");
                        var vertices=List.of(front.vertex_0,front.vertex_1,front.vertex_2,front.vertex_3);var expectedVertices=List.of(source.vertex_0,source.vertex_1,source.vertex_2,source.vertex_3);
                        for(int i=0;i<4;i++)require(Math.abs(vertices.get(i).tex_u-expectedVertices.get(i).tex_u)<.00001 && Math.abs(vertices.get(i).tex_v-expectedVertices.get(i).tex_v)<.00001,"Facade rotated/flipped native block UVs "+state+" "+face);
                    }
                }
                var bounds=new PluggableFacade(BCSiliconPlugs.facade,clientTile(client),face,basic(state,hollow)).getBoundingBox().expand(.0003);
                for(var quad:quads){
                    require(quad.getSprite()!=null && !quad.getSprite().getContents().getId().getPath().equals("missingno"),"Missing facade block sprite");
                    require(quad.getCalculatedNormal().lengthSquared()>1e-9,"Degenerate facade quad");
                    for(var vertex:List.of(quad.vertex_0,quad.vertex_1,quad.vertex_2,quad.vertex_3)){
                        require(bounds.contains(vertex.position_x,vertex.position_y,vertex.position_z),"Facade vertex outside face "+face+" "+vertex);
                        require(vertex.tex_u>=quad.getSprite().getMinU()-.0001 && vertex.tex_u<=quad.getSprite().getMaxU()+.0001
                            && vertex.tex_v>=quad.getSprite().getMinV()-.0001 && vertex.tex_v<=quad.getSprite().getMaxV()+.0001,"Facade UV outside original texture");
                    }
                    if(hollow && quad.getFace()==face){
                        var vertices=List.of(quad.vertex_0,quad.vertex_1,quad.vertex_2,quad.vertex_3);
                        double minA=vertices.stream().mapToDouble(v->face.getAxis()==Direction.Axis.X?v.position_y:v.position_x).min().orElseThrow(),maxA=vertices.stream().mapToDouble(v->face.getAxis()==Direction.Axis.X?v.position_y:v.position_x).max().orElseThrow();
                        double minB=vertices.stream().mapToDouble(v->face.getAxis()==Direction.Axis.Z?v.position_y:v.position_z).min().orElseThrow(),maxB=vertices.stream().mapToDouble(v->face.getAxis()==Direction.Axis.Z?v.position_y:v.position_z).max().orElseThrow();
                        require(!(.5>minA && .5<maxA && .5>minB && .5<maxB),"Hollow facade covered its opening");
                    }
                }
                if(state.isOf(Blocks.GRASS_BLOCK))require(quads.stream().anyMatch(q->q.getTint()>=0),"Grass facade lost biome tint");
            }
        }
        System.out.println("BUILDCRAFT FACADE CLIENT variants="+actual.size());
    }
    private static void place(MinecraftClient client,Direction face,FacadeInstance data){
        var player=player(client);aim(client,face,false);var stack=BCSiliconItems.plugFacade.createFacadeStack(data);player.setStackInHand(Hand.MAIN_HAND,stack);
        var result=player.interactionManager.interactBlock(player,client.getServer().getOverworld(),stack,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(POS),face,POS,false));
        require(result.isAccepted() && tile(client).getPluggable(face) instanceof PluggableFacade,"Native facade placement failed "+face);
    }
    private static void aim(MinecraftClient client,Direction face,boolean angled){
        var player=player(client);var normal=Vec3d.of(face.getVector());var target=Vec3d.ofCenter(POS).add(normal.multiply(.46));
        var eye=Vec3d.ofCenter(POS).add(normal.multiply(3)).add(angled?(face.getAxis()==Direction.Axis.X?new Vec3d(0,1.8,-1.3):new Vec3d(-1.3,1.8,0)):Vec3d.ZERO);
        var look=target.subtract(eye).normalize();player.teleport(player.getServerWorld(),eye.x,eye.y-player.getEyeHeight(player.getPose()),eye.z,(float)Math.toDegrees(Math.atan2(-look.x,look.z)),(float)-Math.toDegrees(Math.asin(look.y)));player.setHeadYaw(player.getYaw());player.getAbilities().flying=true;player.sendAbilitiesUpdate();
    }
    private static void pipe(MinecraftClient client,BlockPos pos){var world=client.getServer().getOverworld();world.setBlockState(pos,BCTransportBlocks.pipeHolder.getDefaultState());((TilePipeHolder)world.getBlockEntity(pos)).onPlacedBy(null,new ItemStack(BCTransportItems.pipeItemStone));}
    private static net.minecraft.server.network.ServerPlayerEntity player(MinecraftClient client){return client.getServer().getPlayerManager().getPlayer(client.player.getUuid());}
    private static TilePipeHolder tile(MinecraftClient client){return (TilePipeHolder)client.getServer().getOverworld().getBlockEntity(POS);}
    private static TilePipeHolder clientTile(MinecraftClient client){return (TilePipeHolder)client.world.getBlockEntity(POS);}
    private static int count(MinecraftClient client){var barrel=(BarrelBlockEntity)client.getServer().getOverworld().getBlockEntity(POS.south(2));int sum=0;for(int i=0;i<barrel.size();i++)if(barrel.getStack(i).isOf(Items.DIAMOND))sum+=barrel.getStack(i).getCount();return sum;}
    private static void shot(MinecraftClient client,String name){net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(client.runDirectory,name,client.getFramebuffer(),message->{});}
    private void advance(){stage++;ticks=0;System.out.println("BUILDCRAFT FACADE CLIENT stage="+stage);}
    private boolean ready(){if(work==null || !work.isDone() || ticks<15)return false;work.join();return true;}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
