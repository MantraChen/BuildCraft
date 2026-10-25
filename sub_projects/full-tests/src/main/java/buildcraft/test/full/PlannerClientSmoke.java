/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import buildcraft.api.mj.MjAPI;
import buildcraft.builders.*;
import buildcraft.builders.addon.AddonFillerPlanner;
import buildcraft.builders.gui.*;
import buildcraft.builders.tile.TileFiller;
import buildcraft.core.*;
import buildcraft.core.marker.volume.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.world.gen.*;
import net.minecraft.world.level.LevelInfo;

/** Real client packets, native screens, persistence and paid construction from an editable plan. */
public final class PlannerClientSmoke implements ClientModInitializer {
    private static final BlockPos BASE=new BlockPos(2,-54,2),MAX=BASE.add(2,2,2),MACHINE=BASE.north();
    private int stage,ticks;private long deadline;private String save;private UUID id;private CompletableFuture<Void> work;
    @Override public void onInitializeClient(){if(Boolean.getBoolean("buildcraft.plannerClientSmoke"))ClientTickEvents.END_CLIENT_TICK.register(this::tick);}
    private void tick(MinecraftClient c){
        if(stage==0){
            if(c.getOverlay()!=null||!(c.currentScreen instanceof TitleScreen))return;
            c.options.pauseOnLostFocus=false;c.options.getViewDistance().setValue(6);c.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
            deadline=System.nanoTime()+360_000_000_000L;save="planner-"+System.currentTimeMillis();stage=1;
            var rules=new GameRules();rules.get(GameRules.DO_MOB_SPAWNING).set(false,null);
            c.createIntegratedServerLoader().createAndStart(save,new LevelInfo("BuildCraft Planner — Remake by FelixMantra",GameMode.CREATIVE,false,Difficulty.PEACEFUL,true,rules,DataConfiguration.SAFE_MODE),new GeneratorOptions(4754L,false,false),r->r.get(RegistryKeys.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createDimensionsRegistryHolder());return;
        }
        if(stage==60)return;if(System.nanoTime()>deadline)throw new IllegalStateException("Planner client timeout stage "+stage);
        if(stage==26){if(c.getServer()!=null||c.world!=null)return;stage=27;ticks=0;c.createIntegratedServerLoader().start(new TitleScreen(),save);return;}
        if(c.currentScreen instanceof net.minecraft.client.gui.screen.DisconnectedScreen)throw new IllegalStateException("Planner client disconnected stage "+stage);
        if(c.world==null||c.player==null||c.getServer()==null)return;ticks++;if(ticks%20==0)c.getToastManager().clear();
        if(stage==1){work=c.getServer().submit(()->{
            var w=c.getServer().getOverworld();w.setTimeOfDay(6000);w.setBlockState(BASE.down(),Blocks.STONE.getDefaultState());w.setBlockState(MACHINE.down(),Blocks.STONE.getDefaultState());
            var p=player(c);p.getInventory().clear();hold(c,BCCoreItems.volumeBox);aim(c,Vec3d.ofCenter(BASE),new Vec3d(0,1,-3));
        });advance();}
        else if(stage==2&&ready()&&ticks>25){place(c,BASE);advance();}
        else if(stage==3&&ticks>25){work=c.getServer().submit(()->{
            var v=WorldSavedDataVolumeBoxes.get(c.getServer().getOverworld()).getVolumeBoxAt(BASE);require(v!=null&&player(c).getMainHandStack().getCount()==1,"Reusable volume tool did not create volume");id=v.id;hold(c,BCCoreItems.markerConnector);aim(c,Vec3d.ofCenter(BASE),new Vec3d(0,0,-3));
        });advance();}
        else if(stage==4&&ready()&&ticks>25){require(local(c).box.min().equals(BASE),"Client volume did not synchronize");use(c);advance();}
        else if(stage==5&&ticks>15){work=c.getServer().submit(()->{
            require(volume(c).isEditingBy(player(c)),"Native connector did not start drag");aim(c,Vec3d.ofCenter(MAX),new Vec3d(0,0,-volume(c).getDist()));
        });advance();}
        else if(stage==6&&ready()&&ticks>25){require(local(c).box.max().equals(MAX),"Native world tick/client drag bounds wrong");use(c);advance();}
        else if(stage==7&&ticks>15){work=c.getServer().submit(()->{
            require(!volume(c).isEditing()&&volume(c).box.max().equals(MAX),"Native drag confirmation failed");hold(c,BCBuildersItems.addonFillerPlanner);aimAddon(c);
        });advance();}
        else if(stage==8&&ready()&&ticks>25){use(c);advance();}
        else if(stage==9&&ticks>20){work=c.getServer().submit(()->{require(addon(c)!=null&&player(c).getMainHandStack().getCount()==1,"Reusable native planner attachment failed");hold(c,BCCoreItems.markerConnector);});advance();}
        else if(stage==10&&ready()&&ticks>20){use(c);advance();}
        else if(stage==11&&ticks>20){require(c.currentScreen instanceof GuiFillerPlanner,"Native planner screen missing");click(c,80,28);advance();}
        else if(stage==12&&ticks>10){click(c,80,28);advance();}
        else if(stage==13&&ticks>15){require(localAddon(c).patternStatement.get()==BCBuildersStatements.PATTERN_FILL&&count(c)==27,"Native fill button did not synchronize");shot(c,"planner-fill-settings.png");click(c,80,28);advance();}
        else if(stage==14&&ticks>15){require(count(c)==26,"Native hollow box preview wrong");click(c,80,70);advance();}
        else if(stage==15&&ticks>15){require(localAddon(c).inverted&&count(c)==1,"Native inverse preview wrong");shot(c,"planner-inverted-box.png");click(c,80,70);advance();}
        else if(stage==16&&ticks>10){click(c,80,28);advance();}
        else if(stage==17&&ticks>10){click(c,80,28);advance();}
        else if(stage==18&&ticks>10){click(c,80,28);advance();}
        else if(stage==19&&ticks>15){require(localAddon(c).patternStatement.get()==BCBuildersStatements.PATTERN_STAIRS,"Native stairs selection failed");click(c,25,50);advance();}
        else if(stage==20&&ticks>15){
            require(localAddon(c).patternStatement.get(0)==buildcraft.builders.snapshot.pattern.parameter.PatternParameterYDir.DOWN,"Native direction parameter did not change to Down");shot(c,"planner-direction-parameter.png");advance();
        }else if(stage==21&&ticks>8){
            if(localAddon(c).patternStatement.get()!=BCBuildersStatements.PATTERN_FILL){click(c,80,28);ticks=0;return;}
            require(count(c)==27&&!localAddon(c).inverted,"Final fill plan wrong");c.player.closeHandledScreen();work=c.getServer().submit(()->aim(c,Vec3d.ofCenter(BASE.add(1,1,1)),new Vec3d(-5,3,-5)));advance();
        }else if(stage==22&&ready()&&ticks>25){
            require(!buildcraft.core.client.render.VolumeRender.white().getContents().getId().toString().equals("minecraft:missingno"),"Native preview white sprite missing");shot(c,"planner-native-volume-preview.png");
            work=c.getServer().submit(()->{
                var w=c.getServer().getWorld(World.NETHER);w.setBlockState(new BlockPos(2,80,2),Blocks.STONE.getDefaultState());
                var v=new VolumeBox(w,new BlockPos(2,82,2));WorldSavedDataVolumeBoxes.get(w).volumeBoxes.add(v);WorldSavedDataVolumeBoxes.get(w).markDirty();
                var p=player(c);p.teleport(w,2,83,0,0,0);
            });advance();
        }else if(stage==23&&ready()&&ticks>50){
            require(c.world.getRegistryKey()==World.NETHER&&ClientVolumeBoxes.INSTANCE.volumeBoxes.size()==1&&ClientVolumeBoxes.INSTANCE.volumeBoxes.get(0).world==c.world&&!ClientVolumeBoxes.INSTANCE.volumeBoxes.get(0).id.equals(id),"Dimension sync leaked overworld volume");
            work=c.getServer().submit(()->{var p=player(c);p.teleport(c.getServer().getOverworld(),0,-52,0,0,0);aimAddon(c);});advance();
        }else if(stage==24&&ready()&&ticks>40){require(local(c).box.max().equals(MAX)&&count(c)==27,"Return dimension lost plan");advance();}
        else if(stage==25){stage=26;ticks=0;c.world.disconnect();c.disconnect(new TitleScreen());}
        else if(stage==27&&ticks>40){require(local(c).box.min().equals(BASE)&&local(c).box.max().equals(MAX)&&count(c)==27,"Restart lost bounds/pattern or join synchronization");work=c.getServer().submit(()->{hold(c,BCCoreItems.markerConnector);aimAddon(c);});advance();}
        else if(stage==28&&ready()&&ticks>25){use(c);advance();}
        else if(stage==29&&ticks>20){require(c.currentScreen instanceof GuiFillerPlanner&&count(c)==27,"Restarted native planner failed");shot(c,"planner-restarted-settings.png");c.player.closeHandledScreen();work=c.getServer().submit(()->{hold(c,BCBuildersBlocks.filler.asItem());aim(c,Vec3d.ofCenter(MACHINE),new Vec3d(0,1,-3));});advance();}
        else if(stage==30&&ready()&&ticks>25){place(c,MACHINE);advance();}
        else if(stage==31&&ticks>25){work=c.getServer().submit(()->{
            var t=filler(c);require(t!=null&&t.addon==addon(c)&&volume(c).isAddonLocked(EnumAddonSlot.WEST_DOWN_NORTH),"Native filler failed to adopt/lock plan");
            hold(c,Items.AIR);player(c).getInventory().setStack(9,new ItemStack(Items.STONE,27));player(c).currentScreenHandler.sendContentUpdates();
        });advance();}
        else if(stage==32&&ready()&&ticks>20){c.interactionManager.interactBlock(c.player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(MACHINE),Direction.NORTH,MACHINE,false));advance();}
        else if(stage==33&&ticks>20){require(c.currentScreen instanceof GuiFiller,"Native linked filler GUI missing");var h=c.player.currentScreenHandler;boolean moved=false;for(int i=0;i<h.slots.size();i++)if(h.getSlot(i).inventory==c.player.getInventory()&&h.getSlot(i).getStack().isOf(Items.STONE)){c.interactionManager.clickSlot(h.syncId,i,0,net.minecraft.screen.slot.SlotActionType.QUICK_MOVE,c.player);moved=true;break;}require(moved,"Native filler material input missing");advance();}
        else if(stage==34&&ticks>20){shot(c,"planner-linked-filler.png");c.player.closeHandledScreen();work=c.getServer().submit(()->{filler(c).getBattery().addPower(1000*MjAPI.MJ,false);hold(c,BCCoreItems.markerConnector);aimAddon(c);});advance();}
        else if(stage==35&&ready()&&ticks>25){use(c);advance();}
        else if(stage==36&&ticks>60){
            require(c.currentScreen==null,"Machine lock allowed planner screen");work=c.getServer().submit(()->{
                require(filler(c).isFinished(),"Linked plan did not finish");for(var pos:BlockPos.iterate(BASE,MAX))require(c.getServer().getOverworld().getBlockState(pos).isOf(Blocks.STONE),"Planned block missing "+pos);
                for(int i=0;i<27;i++)require(filler(c).invResources.getStackInSlot(i).isEmpty(),"Construction did not pay all 27 stone");aim(c,Vec3d.ofCenter(BASE.add(1,1,1)),new Vec3d(-5,3,-5));
            });advance();
        }else if(stage==37&&ready()&&ticks>25){shot(c,"planner-paid-construction.png");work=c.getServer().submit(()->{c.getServer().getOverworld().setBlockState(MACHINE,Blocks.AIR.getDefaultState());aimAddon(c);});advance();}
        else if(stage==38&&ready()&&ticks>25){require(local(c).locks.isEmpty(),"Removed machine left stale client lock");use(c);advance();}
        else if(stage==39&&ticks>20){require(c.currentScreen instanceof GuiFillerPlanner,"Planner did not unlock after machine removal");shot(c,"planner-unlocked-after-machine.png");
            System.out.println("BUILDCRAFT PLANNER CLIENT: reusable native tools; drag/confirm; planner screen and pattern/parameter/invert packets; native volume/ghost rendering; dimension isolation; complete world restart; linked native filler placement/GUI/material payment for 27 blocks; machine lock and removal unlock; failures=0");stage=60;c.scheduleStop();}
    }
    private static net.minecraft.server.network.ServerPlayerEntity player(MinecraftClient c){return c.getServer().getPlayerManager().getPlayer(c.player.getUuid());}
    private VolumeBox volume(MinecraftClient c){return WorldSavedDataVolumeBoxes.get(c.getServer().getOverworld()).getVolumeBoxFromId(id);}
    private VolumeBox local(MinecraftClient c){return ClientVolumeBoxes.INSTANCE.volumeBoxes.stream().filter(v->v.id.equals(id)&&v.world==c.world).findFirst().orElseThrow(()->new IllegalStateException("Missing client volume stage "+stage));}
    private AddonFillerPlanner addon(MinecraftClient c){return (AddonFillerPlanner)volume(c).addons.get(EnumAddonSlot.WEST_DOWN_NORTH);}
    private AddonFillerPlanner localAddon(MinecraftClient c){return (AddonFillerPlanner)local(c).addons.get(EnumAddonSlot.WEST_DOWN_NORTH);}
    private int count(MinecraftClient c){return localAddon(c).buildingInfo.getSnapshot().data.cardinality();}
    private static TileFiller filler(MinecraftClient c){return (TileFiller)c.getServer().getOverworld().getBlockEntity(MACHINE);}
    private static void hold(MinecraftClient c,Item item){var p=player(c);p.getInventory().selectedSlot=0;p.getInventory().setStack(0,new ItemStack(item));p.currentScreenHandler.sendContentUpdates();}
    private void aimAddon(MinecraftClient c){aim(c,EnumAddonSlot.WEST_DOWN_NORTH.getBoundingBox(volume(c)).getCenter(),new Vec3d(-2,1,-2));}
    private static void aim(MinecraftClient c,Vec3d target,Vec3d offset){var p=player(c);var eye=target.add(offset);var look=target.subtract(eye).normalize();p.teleport(p.getServerWorld(),eye.x,eye.y-p.getEyeHeight(p.getPose()),eye.z,(float)Math.toDegrees(Math.atan2(-look.x,look.z)),(float)-Math.toDegrees(Math.asin(look.y)));p.setHeadYaw(p.getYaw());p.getAbilities().flying=true;p.sendAbilitiesUpdate();}
    private static void use(MinecraftClient c){c.interactionManager.interactItem(c.player,Hand.MAIN_HAND);}
    private static void place(MinecraftClient c,BlockPos p){c.interactionManager.interactBlock(c.player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(p.down()).add(0,.5,0),Direction.UP,p.down(),false));}
    private static void click(MinecraftClient c,int x,int y){c.currentScreen.mouseClicked((c.currentScreen.width-176)/2+x,(c.currentScreen.height-116)/2+y,0);}
    private static void shot(MinecraftClient c,String name){net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(c.runDirectory,name,c.getFramebuffer(),m->{});}
    private boolean ready(){if(work==null||!work.isDone()||ticks<5)return false;work.join();return true;}
    private void advance(){stage++;ticks=0;System.out.println("BUILDCRAFT PLANNER CLIENT stage="+stage);}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
