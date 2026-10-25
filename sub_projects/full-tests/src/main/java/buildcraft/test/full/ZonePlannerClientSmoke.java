/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import buildcraft.core.*;
import buildcraft.core.item.*;
import buildcraft.robotics.*;
import buildcraft.robotics.container.ContainerZonePlanner;
import buildcraft.robotics.gui.GuiZonePlanner;
import buildcraft.robotics.tile.TileZonePlanner;
import buildcraft.robotics.zone.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.world.gen.*;
import net.minecraft.world.level.LevelInfo;

/** Real native screen, cursor item/drag packets, terrain rendering and saved-world import/export. */
public final class ZonePlannerClientSmoke implements ClientModInitializer {
    private static final BlockPos FIRST=new BlockPos(0,-59,0),SECOND=FIRST.east(5);
    private int stage,ticks,count;private long deadline;private String save;private CompletableFuture<Void> work;
    private volatile boolean synchronizeBusyLayer;
    private BlockPos a,b,erased;private ZonePlan exported;private double oldCenter;
    @Override public void onInitializeClient(){if(Boolean.getBoolean("buildcraft.zonePlannerClientSmoke")){ClientTickEvents.END_CLIENT_TICK.register(this::tick);net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server->{if(synchronizeBusyLayer&&server.getOverworld().getBlockEntity(FIRST) instanceof TileZonePlanner tile)tile.replaceLayer(0,new ZonePlan());});}}
    private void tick(MinecraftClient c){
        if(stage==0){
            if(c.getOverlay()!=null||!(c.currentScreen instanceof TitleScreen))return;
            c.options.pauseOnLostFocus=false;c.options.getViewDistance().setValue(6);c.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
            deadline=System.nanoTime()+360_000_000_000L;save="zone-planner-"+System.currentTimeMillis();stage=1;
            var rules=new GameRules();rules.get(GameRules.DO_MOB_SPAWNING).set(false,null);
            c.createIntegratedServerLoader().createAndStart(save,new LevelInfo("BuildCraft Zones — Remake by FelixMantra",GameMode.CREATIVE,false,Difficulty.PEACEFUL,true,rules,DataConfiguration.SAFE_MODE),new GeneratorOptions(4754L,false,false),r->r.get(RegistryKeys.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createDimensionsRegistryHolder());return;
        }
        if(stage==60)return;if(System.nanoTime()>deadline)throw new IllegalStateException("Zone planner client timeout at "+stage);
        if(stage==27){if(c.getServer()!=null||c.world!=null)return;stage=28;ticks=0;c.createIntegratedServerLoader().start(new TitleScreen(),save);return;}
        if(c.currentScreen instanceof net.minecraft.client.gui.screen.DisconnectedScreen)throw new IllegalStateException("Zone planner disconnected at "+stage);
        if(c.world==null||c.player==null||c.getServer()==null||c.getOverlay()!=null)return;ticks++;if(ticks%20==0)c.getToastManager().clear();
        if(stage==1){work=c.getServer().submit(()->{
            var w=c.getServer().getOverworld();w.setTimeOfDay(6000);w.setBlockState(FIRST.down(),Blocks.STONE.getDefaultState());w.setBlockState(SECOND.down(),Blocks.STONE.getDefaultState());var p=player(c);p.getInventory().clear();hold(c,new ItemStack(BCRoboticsBlocks.zonePlanner));aim(c,FIRST);
            for(var pos:BlockPos.iterate(FIRST.add(10,0,6),FIRST.add(22,5,16)))w.setBlockState(pos,Blocks.STONE.getDefaultState());
            for(var pos:BlockPos.iterate(FIRST.add(-18,-1,-14),FIRST.add(-8,-1,-6)))w.setBlockState(pos,Blocks.BLUE_CONCRETE.getDefaultState());
            for(var pos:BlockPos.iterate(FIRST.add(-14,-1,10),FIRST.add(-3,-1,17)))w.setBlockState(pos,Blocks.SAND.getDefaultState());
        });advance();}
        else if(stage==2&&ready()&&ticks>25){place(c,FIRST);advance();}
        else if(stage==3&&ticks>25){work=c.getServer().submit(()->{
            var t=tile(c,FIRST);require(t!=null,"Native planner placement failed");synchronizeBusyLayer=true;t.invPaintbrushes.setStackInSlot(0,brush(DyeColor.RED));t.invPaintbrushes.setStackInSlot(4,brush(DyeColor.BLUE));
            player(c).getInventory().setStack(9,new ItemStack(BCCoreItems.mapLocation,3));hold(c,ItemStack.EMPTY);aim(c,FIRST);
        });advance();}
        else if(stage==4&&ready()&&ticks>30){shot(c,"zone-native-front-display.png");use(c,FIRST);advance();}
        else if(stage==5&&ticks>50){require(c.currentScreen instanceof GuiZonePlanner,"Native zone screen missing");require(handler(c).ready[15],"Busy first layer starved other layer synchronization");synchronizeBusyLayer=false;cursor(c,105,52);scroll(c,4);slot(c,0);advance();}
        else if(stage==6&&ticks>30){require(ItemPaintbrush_BC8.getColour(handler(c).getCursorStack())==DyeColor.RED,"Native brush pickup failed");a=gui(c).hovered;shot(c,"zone-terrain-loaded.png");require(a!=null,"Terrain data/picking missing");mouse(c,105,52,0,true);cursor(c,125,65);advance();}
        else if(stage==7&&ticks>10){b=gui(c).hovered;require(b!=null&&!b.equals(a),"Terrain endpoint missing");drag(c,125,65,0,20,13);shot(c,"zone-native-red-drag.png");mouse(c,125,65,0,false);count=(Math.abs(b.getX()-a.getX())+1)*(Math.abs(b.getZ()-a.getZ())+1);advance();}
        else if(stage==8&&ticks>20){require(local(c).layers[14].getAll().size()==count,"Native edit packet lost rectangle cells");shot(c,"zone-native-red-layer.png");cursor(c,105,52);advance();}
        else if(stage==9&&ticks>10){erased=gui(c).hovered;mouse(c,105,52,1,true);mouse(c,105,52,1,false);advance();}
        else if(stage==10&&ticks>15){require(local(c).layers[14].getAll().size()==count-1&&!local(c).layers[14].get(erased.getX(),erased.getZ()),"Native right erase failed");slot(c,0);slot(c,4);cursor(c,95,72);advance();}
        else if(stage==11&&ticks>15){mouse(c,95,72,0,true);mouse(c,95,72,0,false);advance();}
        else if(stage==12&&ticks>15){require(local(c).layers[11].getAll().size()==1&&local(c).layers[14].getAll().size()==count-1,"Second colour overwrote first layer");slot(c,4);advance();}
        else if(stage==13&&ticks>10){oldCenter=gui(c).centerX;mouse(c,110,55,0,true);drag(c,120,55,0,10,0);mouse(c,120,55,0,false);scroll(c,-2);advance();}
        else if(stage==14&&ticks>20){require(gui(c).centerX<oldCenter&&gui(c).distance>50,"Native pan/zoom failed");shot(c,"zone-native-all-layers-pan.png");slot(c,0);slot(c,19);quick(c,22);advance();}
        else if(stage==15&&ticks>70){require(local(c).progress(true)>0&&local(c).progress(true)<200&&handler(c).getSlot(22).getStack().getCount()==2,"Native partial export/map payment failed");shot(c,"zone-native-export-progress.png");advance();}
        else if(stage==16&&ticks>160){
            require(!local(c).invOutputResult.getStackInSlot(0).isEmpty(),"Native export did not complete");exported=(ZonePlan)BCCoreItems.mapLocation.getZone(local(c).invOutputResult.getStackInSlot(0));require(exported!=null&&exported.getAll().size()==count-1&&!exported.get(erased.getX(),erased.getZ()),"Exported absolute zone incorrect");
            quick(c,21);quick(c,19);advance();
        }else if(stage==17&&ticks>15){require(ItemPaintbrush_BC8.usesLeft(find(c,BCCoreItems.paintbrush))==64,"Planning consumed paint");c.player.closeHandledScreen();work=c.getServer().submit(()->{hold(c,new ItemStack(BCRoboticsBlocks.zonePlanner));aim(c,SECOND);});advance();}
        else if(stage==18&&ready()&&ticks>25){place(c,SECOND);advance();}
        else if(stage==19&&ticks>20){work=c.getServer().submit(()->{require(tile(c,SECOND)!=null,"Second native placement failed");hold(c,ItemStack.EMPTY);aim(c,SECOND);});advance();}
        else if(stage==20&&ready()&&ticks>20){use(c,SECOND);advance();}
        else if(stage==21&&ticks>25){require(c.currentScreen instanceof GuiZonePlanner,"Second native screen missing");pickupItem(c,BCCoreItems.paintbrush,false);slot(c,16);pickupItem(c,BCCoreItems.mapLocation,true);slot(c,17);advance();}
        else if(stage==22&&ticks>70){require(local(c).progress(false)>0&&local(c).progress(false)<200,"Native import did not start");shot(c,"zone-native-import-progress.png");advance();}
        else if(stage==23&&ticks>160){
            var layer=local(c).layers[14].getWithOffset(SECOND.getX(),SECOND.getZ());require(same(layer,exported)&&!ItemMapLocation.isRecorded(local(c).invInputResult.getStackInSlot(0)),"Imported zone moved absolute coordinates or failed blank return");shot(c,"zone-native-imported-layer.png");quick(c,18);quick(c,16);advance();
        }else if(stage==24&&ticks>15){c.player.closeHandledScreen();c.getLanguageManager().setLanguage("zh_cn");c.options.language="zh_cn";work=c.reloadResources();advance();}
        else if(stage==25&&ready()&&ticks>30){use(c,SECOND);advance();}
        else if(stage==26&&ticks>35){require(c.currentScreen instanceof GuiZonePlanner&&same(local(c).layers[14].getWithOffset(5,0),exported),"Resource reload lost synchronized zone");c.player.closeHandledScreen();stage=27;ticks=0;c.world.disconnect();c.disconnect(new TitleScreen());}
        else if(stage==28&&ticks>35){work=c.getServer().submit(()->{
            require(same(tile(c,FIRST).layers[14],exported)&&same(tile(c,SECOND).layers[14].getWithOffset(5,0),exported),"World restart lost planner layers");
            int maps=0,brushes=0;for(var s:player(c).getInventory().main){if(s.isOf(BCCoreItems.mapLocation)){maps+=s.getCount();require(!ItemMapLocation.isRecorded(s),"Import failed to return blank");}if(s.isOf(BCCoreItems.paintbrush)){brushes+=s.getCount();require(ItemPaintbrush_BC8.usesLeft(s)==64,"Planner consumed brush");}}
            require(maps==3&&brushes==1&&tile(c,FIRST).invPaintbrushes.getStackInSlot(4).isOf(BCCoreItems.paintbrush),"Round trip lost or duplicated maps/brushes");aim(c,SECOND);
        });advance();}
        else if(stage==29&&ready()&&ticks>25){use(c,SECOND);advance();}
        else if(stage==30&&ticks>50){require(c.currentScreen instanceof GuiZonePlanner&&same(local(c).layers[14].getWithOffset(5,0),exported),"Restarted native GUI missing data");cursor(c,110,55);scroll(c,4);advance();}
        else if(stage==31&&ticks>25){require(gui(c).getTitle().getString().equals("区域规划台"),"Chinese native screen title missing");shot(c,"zone-restarted-native-terrain.png");System.out.println("BUILDCRAFT ZONE PLANNER CLIENT: native placement/front map; sixteen layers under continuous first-layer updates; native brush pickup, drag and erase packets; pan/zoom; exact map count and two hundred tick import/export; absolute zones across planners; resources/Chinese language reload; complete world restart; failures=0");stage=60;c.scheduleStop();}
    }
    private static GuiZonePlanner gui(MinecraftClient c){return (GuiZonePlanner)c.currentScreen;}
    private static ContainerZonePlanner handler(MinecraftClient c){return (ContainerZonePlanner)c.player.currentScreenHandler;}
    private static TileZonePlanner local(MinecraftClient c){return handler(c).tile;}
    private static TileZonePlanner tile(MinecraftClient c,BlockPos p){return (TileZonePlanner)c.getServer().getOverworld().getBlockEntity(p);}
    private static net.minecraft.server.network.ServerPlayerEntity player(MinecraftClient c){return c.getServer().getPlayerManager().getPlayer(c.player.getUuid());}
    private static ItemStack brush(DyeColor colour){return ItemPaintbrush_BC8.coloured(new ItemStack(BCCoreItems.paintbrush),colour);}
    private static void hold(MinecraftClient c,ItemStack s){var p=player(c);p.getInventory().selectedSlot=0;p.getInventory().setStack(0,s);p.currentScreenHandler.sendContentUpdates();}
    private static void aim(MinecraftClient c,BlockPos p){var s=player(c);s.networkHandler.requestTeleport(p.getX()+.5,p.getY(),p.getZ()-2.5,0,15);s.getAbilities().flying=true;s.sendAbilitiesUpdate();}
    private static void place(MinecraftClient c,BlockPos p){c.interactionManager.interactBlock(c.player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(p.down()).add(0,.5,0),Direction.UP,p.down(),false));}
    private static void use(MinecraftClient c,BlockPos p){c.interactionManager.interactBlock(c.player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(p),Direction.NORTH,p,false));}
    private static void slot(MinecraftClient c,int id){c.interactionManager.clickSlot(handler(c).syncId,id,0,SlotActionType.PICKUP,c.player);}
    private static void quick(MinecraftClient c,int id){c.interactionManager.clickSlot(handler(c).syncId,id,0,SlotActionType.QUICK_MOVE,c.player);}
    private static void pickupItem(MinecraftClient c,Item item,boolean recorded){var h=handler(c);for(int i=22;i<h.slots.size();i++){var s=h.getSlot(i).getStack();if(s.isOf(item)&&(item!=BCCoreItems.mapLocation||ItemMapLocation.isRecorded(s)==recorded)){slot(c,i);return;}}throw new IllegalStateException("Missing player item "+item);}
    private static ItemStack find(MinecraftClient c,Item item){return c.player.getInventory().main.stream().filter(s->s.isOf(item)).findFirst().orElseThrow();}
    private static boolean same(ZonePlan a,ZonePlan b){return new HashSet<>(a.getAll()).equals(new HashSet<>(b.getAll()));}
    private static double x(MinecraftClient c,double v){return (c.currentScreen.width-256)/2+v;}
    private static double y(MinecraftClient c,double v){return (c.currentScreen.height-228)/2+v;}
    private static void cursor(MinecraftClient c,double x,double y){var w=c.getWindow();org.lwjgl.glfw.GLFW.glfwSetCursorPos(w.getHandle(),x(c,x)*w.getWidth()/w.getScaledWidth(),y(c,y)*w.getHeight()/w.getScaledHeight());}
    private static void mouse(MinecraftClient c,double x,double y,int button,boolean down){if(down)gui(c).mouseClicked(x(c,x),y(c,y),button);else gui(c).mouseReleased(x(c,x),y(c,y),button);}
    private static void drag(MinecraftClient c,double x,double y,int button,double dx,double dy){gui(c).mouseDragged(x(c,x),y(c,y),button,dx,dy);}
    private static void scroll(MinecraftClient c,double amount){gui(c).mouseScrolled(x(c,110),y(c,55),amount);}
    private static void shot(MinecraftClient c,String name){net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(c.runDirectory,name,c.getFramebuffer(),m->{});}
    private boolean ready(){if(work==null||!work.isDone()||ticks<5)return false;work.join();return true;}
    private void advance(){stage++;ticks=0;System.out.println("BUILDCRAFT ZONE PLANNER CLIENT stage="+stage);}
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
}
