/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import buildcraft.silicon.*;
import buildcraft.silicon.client.BCSiliconClient;
import buildcraft.silicon.container.ContainerGate;
import buildcraft.silicon.gate.*;
import buildcraft.silicon.gui.GuiGate;
import buildcraft.silicon.item.ItemPluggableControl;
import buildcraft.silicon.plug.*;
import buildcraft.transport.*;
import buildcraft.transport.tile.TilePipeHolder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.*;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.*;
import net.minecraft.item.*;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.world.gen.*;
import net.minecraft.world.level.LevelInfo;

/** Native attachment interactions, searched gate choices, real transport and full saved pulse recovery. */
public final class ControlPlugClientSmoke implements ClientModInitializer {
    private static final BlockPos POS=new BlockPos(2,-56,2);
    private int stage,ticks;private long deadline;private String saveName;private CompletableFuture<Void> work,resourceReload;
    @Override public void onInitializeClient(){if(Boolean.getBoolean("buildcraft.controlPlugClientSmoke"))ClientTickEvents.END_CLIENT_TICK.register(this::tick);}
    private void tick(MinecraftClient client) {
        if(stage==0) {
            if(client.getOverlay()!=null || !(client.currentScreen instanceof TitleScreen))return;
            client.options.pauseOnLostFocus=false;client.options.getViewDistance().setValue(6);client.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
            deadline=System.nanoTime()+300_000_000_000L;saveName="control-plugs-smoke-"+System.currentTimeMillis();stage=1;
            var rules=new GameRules();rules.get(GameRules.DO_MOB_SPAWNING).set(false,null);
            client.createIntegratedServerLoader().createAndStart(saveName,new LevelInfo("BuildCraft pipe controls — Remake by FelixMantra",GameMode.CREATIVE,false,Difficulty.PEACEFUL,true,rules,DataConfiguration.SAFE_MODE),
                new GeneratorOptions(4754L,false,false),registries->registries.get(RegistryKeys.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createDimensionsRegistryHolder());return;
        }
        if(stage==30)return;
        if(System.nanoTime()>deadline)throw new IllegalStateException("Control plug client timed out at stage "+stage);
        if(stage==8){if(client.getServer()!=null || client.world!=null)return;stage=9;ticks=0;client.createIntegratedServerLoader().start(new TitleScreen(),saveName);return;}
        if(client.world==null || client.player==null || client.getServer()==null)return;
        ticks++;if(ticks%20==0)client.getToastManager().clear();
        if(stage==1) {
            work=client.getServer().submit(()->{
                var world=client.getServer().getOverworld();var player=world.getServer().getPlayerManager().getPlayer(client.player.getUuid());world.setTimeOfDay(18000);
                world.setBlockState(POS.west(),Blocks.BARREL.getDefaultState());world.setBlockState(POS.east(2),Blocks.BARREL.getDefaultState());
                for(int i=0;i<2;i++){var pos=POS.east(i);world.setBlockState(pos,BCTransportBlocks.pipeHolder.getDefaultState());((TilePipeHolder)world.getBlockEntity(pos)).onPlacedBy(null,new ItemStack(i==0?BCTransportItems.pipeItemWood:BCTransportItems.pipeItemStone));}
                source(client).setStack(0,new ItemStack(Items.DIAMOND));
                for(var face:List.of(Direction.NORTH,Direction.UP,Direction.DOWN,Direction.SOUTH)) {
                    var stack=switch(face) {
                        case NORTH->new ItemStack(BCSiliconItems.plugPulsar);case UP->new ItemStack(BCSiliconItems.plugTimer);case DOWN->new ItemStack(BCSiliconItems.plugLightSensor);
                        default->BCSiliconItems.plugGate.getStack(new GateVariant(EnumGateLogic.OR,EnumGateMaterial.NETHER_BRICK,EnumGateModifier.NO_MODIFIER));
                    };
                    aim(player,face);player.setStackInHand(Hand.MAIN_HAND,stack);
                    var trace=BCTransportBlocks.pipeHolder.rayTrace(world,POS,player);
                    var result=player.interactionManager.interactBlock(player,world,stack,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(POS),face,POS,false));
                    require(result.isAccepted() && tile(client).getPluggable(face)!=null,"Native control placement failed "+face+" result="+result+" trace="+trace+" look="+player.getRotationVec(1)+" eye="+player.getEyePos());
                }
                player.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);aim(player,Direction.NORTH);
                require(tile(client).getPluggable(Direction.NORTH) instanceof PluggablePulsar && tile(client).getPluggable(Direction.UP) instanceof PluggableTimer
                    && tile(client).getPluggable(Direction.DOWN) instanceof PluggableLightSensor,"Missing native controls");
            });advance();
        } else if(stage==2 && ready() && ticks>25) {
            if(resourceReload==null){checkModels(client);resourceReload=client.reloadResources();return;}
            if(!resourceReload.isDone() || client.getOverlay()!=null)return;
            resourceReload.join();
            require(client.world.getBlockEntity(POS) instanceof TilePipeHolder,"Client pipe absent");checkModels(client);interact(client,Direction.NORTH);advance();
        } else if(stage==3 && ticks>35) {
            require(clientPulse(client).isManual() && clientPulse(client).isPulsing(),"Manual pulsar state did not synchronize");screenshot(client,"controls-manual-pulsar.png");interact(client,Direction.NORTH);advance();
        } else if(stage==4 && ticks>130) {
            require(!clientPulse(client).isManual() && !clientPulse(client).isPulsing(),"Manual pulsar did not stop");
            work=client.getServer().submit(()->{require(count(target(client))==1 && source(client).isEmpty(),"Manual native pipeline did not deliver exactly one item");aim(player(client),Direction.SOUTH);});advance();
        } else if(stage==5 && ready()){interact(client,Direction.SOUTH);advance();}
        else if(stage==6 && ticks>25 && client.currentScreen instanceof GuiGate) {
            var menu=menu(client);
            require(menu.triggers.stream().filter(w->w.delegate instanceof buildcraft.silicon.statement.TriggerTimer).count()==3
                && menu.triggers.stream().filter(w->w.delegate instanceof buildcraft.silicon.statement.TriggerLightSensor).count()==2,"Gate choices did not sync sensor/timer");
            choose(client,false,"buildcraftcore:trigger.true",null);choose(client,true,"buildcraft:pulsar.single",Direction.NORTH);advance();
        } else if(stage==7 && ticks>45) {
            require(menu(client).gate.logic.statements[0].action.get().getUniqueTag().equals("buildcraft:pulsar.single") && clientPulse(client).isAutomatic(),"Native searched pulse selection did not synchronize");screenshot(client,"controls-queued-single-gate.png");
            work=client.getServer().submit(()->require(((PluggablePulsar)tile(client).getPluggable(Direction.NORTH)).getPendingPulses()==1,"Empty source discarded queued pulse"));work.join();
            stage=8;ticks=0;client.player.closeHandledScreen();client.world.disconnect();client.disconnect(new TitleScreen());
        } else if(stage==9 && ticks>35) {
            work=client.getServer().submit(()->{
                var pulse=(PluggablePulsar)tile(client).getPluggable(Direction.NORTH);
                require(pulse.getPendingPulses()==1 && !pulse.isManual(),"Restart lost/duplicated queued single action");source(client).setStack(0,new ItemStack(Items.DIAMOND,3));aim(player(client),Direction.NORTH);
            });advance();
        } else if(stage==10 && ready() && ticks>130) {
            work=client.getServer().submit(()->{require(count(target(client))==2 && count(source(client))==2,"Reloaded high gate retriggered or lost its single pulse");aim(player(client),Direction.SOUTH);});advance();
        } else if(stage==11 && ready()){interact(client,Direction.SOUTH);advance();}
        else if(stage==12 && ticks>25 && client.currentScreen instanceof GuiGate){choose(client,false,"buildcraft:timer_short",null);advance();}
        else if(stage==13 && ticks>25) {
            require(menu(client).gate.logic.statements[0].trigger.get().getUniqueTag().equals("buildcraft:timer_short"),"Native timer selection did not synchronize");screenshot(client,"controls-timer-gate.png");advance();
        } else if(stage==14 && ticks>330) {
            work=client.getServer().submit(()->{require(count(target(client))==4 && source(client).isEmpty(),"Timer gate did not deliver two remaining items");});
            work.join();choose(client,false,"buildcraft:light_dark",Direction.DOWN);choose(client,true,"buildcraft:pulsar.constant",Direction.NORTH);advance();
        } else if(stage==15 && ticks>25) {
            require(clientPulse(client).isAutomatic() && menu(client).gate.logic.isOn,"Dark sensor did not activate constant pulses");screenshot(client,"controls-light-sensor-gate.png");
            work=client.getServer().submit(()->source(client).setStack(0,new ItemStack(Items.DIAMOND,2)));advance();
        } else if(stage==16 && ready() && ticks>160) {
            work=client.getServer().submit(()->{require(count(target(client))==6 && source(client).isEmpty(),"Light sensor did not drive extraction");aim(player(client),Direction.DOWN);});client.player.closeHandledScreen();advance();
        } else if(stage==17 && ready() && ticks>25) {
            screenshot(client,"controls-light-sensor-model.png");client.interactionManager.attackBlock(POS,Direction.UP);advance();
        } else if(stage==18 && ticks>45) {
            var pipe=(TilePipeHolder)client.world.getBlockEntity(POS);
            require(pipe.getPluggable(Direction.DOWN)==null && pipe.getPluggable(Direction.NORTH) instanceof PluggablePulsar && pipe.getPluggable(Direction.UP) instanceof PluggableTimer
                && !clientPulse(client).isAutomatic() && !clientPulse(client).isPulsing(),"Sensor break lost pipe/other controls or retained gate activity");
            work=client.getServer().submit(()->{source(client).setStack(0,new ItemStack(Items.DIAMOND,3));aim(player(client),Direction.UP);});advance();
        } else if(stage==19 && ready() && ticks>70) {
            screenshot(client,"controls-timer-model.png");work=client.getServer().submit(()->{require(count(source(client))==3 && count(target(client))==6,"Removed sensor continued to drive extraction");aim(player(client),Direction.NORTH);});advance();
        } else if(stage==20 && ready() && ticks>25){client.interactionManager.attackBlock(POS,Direction.SOUTH);advance();}
        else if(stage==21 && ticks>35) {
            require(((TilePipeHolder)client.world.getBlockEntity(POS)).getPluggable(Direction.NORTH)==null,"Native pulsar breaking failed");
            work=client.getServer().submit(()->{
                var world=client.getServer().getOverworld();require(tile(client).getPluggable(Direction.UP) instanceof PluggableTimer && count(source(client))==3,"Breaking pulsar changed timer or source");
                require(world.getBlockEntity(POS.east(2)) instanceof BarrelBlockEntity,"Output barrel lost");
            });advance();
        } else if(stage==22 && ready()) {
            System.out.println("BUILDCRAFT CONTROL PLUG CLIENT: native placement/animated six-facing models/resource reload/manual toggles, searched gate single/timer/dark choices, full pending-pulse restart without duplicate edge, six exact delivered diamonds, native sensor/pulsar removal with remaining pipe preserved; failures=0");stage=30;client.scheduleStop();
        }
    }
    private static void checkModels(MinecraftClient client) {
        for(var item:List.of(BCSiliconItems.plugPulsar,BCSiliconItems.plugLightSensor,BCSiliconItems.plugTimer)) {
            var stack=new ItemStack(item);require(client.getItemRenderer().getModel(stack,client.world,client.player,0)!=client.getBakedModelManager().getMissingModel(),"Control item model missing");
            require(stack.getTooltip(client.player,net.minecraft.client.item.TooltipContext.Default.BASIC).stream().anyMatch(t->t.getString().contains("Remake by FelixMantra")),"Control item credit missing");
            for(var face:Direction.values()) {
                var quads=BCSiliconClient.CONTROL_MODEL.getQuads(item.kind,face,false,false,false,0);
                require(quads.length>=6 && Arrays.stream(quads).allMatch(q->q.getSprite()!=null && !q.getSprite().getContents().getId().getPath().equals("missingno")),"Missing control plate texture "+item.kind+" "+face);
            }
        }
        var a=BCSiliconClient.CONTROL_MODEL.getQuads(ItemPluggableControl.Kind.PULSAR,Direction.WEST,true,false,true,0);
        var b=BCSiliconClient.CONTROL_MODEL.getQuads(ItemPluggableControl.Kind.PULSAR,Direction.WEST,true,true,false,.5);
        require(a.length>6 && b.length==a.length && a[6].vertex_0.position_x!=b[6].vertex_0.position_x,"Pulsar stroke model did not animate");
    }
    private static void choose(MinecraftClient client,boolean action,String id,Direction face) {
        var menu=menu(client);var choice=menu.choices(action).stream().filter(w->w.getUniqueTag().equals(id) && w.sourcePart.face==face).findFirst().orElseThrow();
        String label=choice.getDescription();if(net.minecraft.util.Language.getInstance().hasTranslation(label))label=Text.translatable(label).getString();
        if(face!=null)label+=" ["+Text.translatable("gate.side."+face.asString()).getString()+"]";
        var screen=(GuiGate)client.currentScreen;int x=(screen.width-menu.panelWidth)/2,y=(screen.height-Math.max(206,menu.inventoryY+82))/2;
        screen.mouseClicked(x+8+(action?18*(1+menu.gate.logic.variant.numTriggerArgs)+12:0)+8,y+38,0);
        var search=(TextFieldWidget)screen.children().stream().filter(TextFieldWidget.class::isInstance).findFirst().orElseThrow();search.setText(label);
        var button=(ButtonWidget)screen.children().stream().filter(child->child instanceof ButtonWidget widget && widget.getY()==y+46).findFirst().orElseThrow();screen.mouseClicked(button.getX()+5,button.getY()+5,0);
    }
    private static void aim(net.minecraft.server.network.ServerPlayerEntity player,Direction side) {
        var eye=Vec3d.ofCenter(POS).add(Vec3d.of(side.getVector()).multiply(3));var look=Vec3d.of(side.getVector()).multiply(-1);
        player.teleport(player.getServerWorld(),eye.x,eye.y-player.getEyeHeight(player.getPose()),eye.z,(float)Math.toDegrees(Math.atan2(-look.x,look.z)),(float)-Math.toDegrees(Math.asin(look.y)));
        player.setHeadYaw(player.getYaw());
        player.getAbilities().flying=true;player.sendAbilitiesUpdate();
    }
    private static void interact(MinecraftClient client,Direction side){client.interactionManager.interactBlock(client.player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(POS).add(Vec3d.of(side.getVector()).multiply(.3125)),side,POS,false));}
    private static net.minecraft.server.network.ServerPlayerEntity player(MinecraftClient client){return client.getServer().getPlayerManager().getPlayer(client.player.getUuid());}
    private static TilePipeHolder tile(MinecraftClient client){return (TilePipeHolder)client.getServer().getOverworld().getBlockEntity(POS);}
    private static PluggablePulsar clientPulse(MinecraftClient client){return (PluggablePulsar)((TilePipeHolder)client.world.getBlockEntity(POS)).getPluggable(Direction.NORTH);}
    private static BarrelBlockEntity source(MinecraftClient client){return (BarrelBlockEntity)client.getServer().getOverworld().getBlockEntity(POS.west());}
    private static BarrelBlockEntity target(MinecraftClient client){return (BarrelBlockEntity)client.getServer().getOverworld().getBlockEntity(POS.east(2));}
    private static int count(BarrelBlockEntity barrel){int count=0;for(int i=0;i<barrel.size();i++)count+=barrel.getStack(i).getCount();return count;}
    private static ContainerGate menu(MinecraftClient client){return (ContainerGate)client.player.currentScreenHandler;}
    private static void screenshot(MinecraftClient client,String name){net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(client.runDirectory,name,client.getFramebuffer(),message->{});}
    private void advance(){stage++;ticks=0;System.out.println("BUILDCRAFT CONTROL PLUG CLIENT stage="+stage);}
    private boolean ready(){if(work==null || !work.isDone() || ticks<15)return false;work.join();return true;}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
