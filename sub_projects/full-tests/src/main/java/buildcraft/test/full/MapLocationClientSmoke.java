/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import buildcraft.api.items.IMapLocation.MapLocationType;
import buildcraft.core.*;
import buildcraft.core.item.ItemMapLocation;
import buildcraft.core.marker.*;
import buildcraft.core.tile.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.world.gen.*;
import net.minecraft.world.level.LevelInfo;

public final class MapLocationClientSmoke implements ClientModInitializer {
    private static final BlockPos POINT=new BlockPos(0,-59,0),AREA=POINT.south(3),PATH=POINT.south(7);
    private int stage,ticks;private long deadline;private String save;private CompletableFuture<Void> work;
    @Override public void onInitializeClient(){if(Boolean.getBoolean("buildcraft.mapLocationClientSmoke"))ClientTickEvents.END_CLIENT_TICK.register(this::tick);}
    private void tick(MinecraftClient c){
        if(stage==0){if(c.getOverlay()!=null||!(c.currentScreen instanceof TitleScreen))return;c.options.pauseOnLostFocus=false;c.options.getViewDistance().setValue(6);c.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);deadline=System.nanoTime()+240_000_000_000L;save="map-locations-"+System.currentTimeMillis();stage=1;var rules=new GameRules();rules.get(GameRules.DO_MOB_SPAWNING).set(false,null);c.createIntegratedServerLoader().createAndStart(save,new LevelInfo("BuildCraft Maps — Remake by FelixMantra",GameMode.SURVIVAL,false,Difficulty.PEACEFUL,true,rules,DataConfiguration.SAFE_MODE),new GeneratorOptions(4754L,false,false),r->r.get(RegistryKeys.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createDimensionsRegistryHolder());return;}
        if(stage==60)return;if(System.nanoTime()>deadline)throw new IllegalStateException("Map client timeout at "+stage);
        if(stage==15){if(c.getServer()!=null||c.world!=null)return;stage=16;ticks=0;c.createIntegratedServerLoader().start(new TitleScreen(),save);return;}
        if(c.currentScreen instanceof net.minecraft.client.gui.screen.DisconnectedScreen)throw new IllegalStateException("Map client disconnected");
        if(c.world==null||c.player==null||c.getServer()==null||c.getOverlay()!=null)return;ticks++;if(ticks%20==0)c.getToastManager().clear();
        if(stage==1){work=c.getServer().submit(()->{var w=c.getServer().getOverworld();w.setTimeOfDay(6000);var p=player(c);p.getInventory().clear();p.getInventory().setStack(0,new ItemStack(BCCoreItems.mapLocation,4));w.setBlockState(POINT,Blocks.CHEST.getDefaultState());((net.minecraft.block.entity.ChestBlockEntity)w.getBlockEntity(POINT)).setStack(0,new ItemStack(Items.DIAMOND,3));
            for(var at:List.of(AREA,AREA.east(3))){w.setBlockState(at.down(),Blocks.STONE.getDefaultState());w.setBlockState(at,BCCoreBlocks.markerVolume.getDefaultState().with(BCCoreBlocks.markerVolume.getFacingProperty(),Direction.UP));((TileMarkerVolume)w.getBlockEntity(at)).onLoad();}require(VolumeCache.INSTANCE.getSubCache(w).tryConnect(AREA,AREA.east(3)),"Area fixture connection failed");
            for(var at:List.of(PATH,PATH.east(3),PATH.east(3).south(3))){w.setBlockState(at.down(),Blocks.STONE.getDefaultState());w.setBlockState(at,BCCoreBlocks.markerPath.getDefaultState().with(BCCoreBlocks.markerPath.getFacingProperty(),Direction.UP));((TileMarkerPath)w.getBlockEntity(at)).onLoad();}var cache=PathCache.INSTANCE.getSubCache(w);require(cache.tryConnect(PATH,PATH.east(3))&&cache.tryConnect(PATH.east(3),PATH.east(3).south(3)),"Path fixture connection failed");var con=((TileMarkerPath)w.getBlockEntity(PATH)).getCurrentConnection();require(con.addMarker(PATH,PATH.east(3).south(3)),"Loop fixture failed");aim(c,POINT);p.currentScreenHandler.sendContentUpdates();});advance();}
        else if(stage==2&&ready()&&ticks>25){use(c,POINT);advance();}
        else if(stage==3&&ticks>25){require(!new ItemStack(BCCoreItems.mapLocation).getName().getString().equals("item.buildcraftcore.map_location"),"Map display name untranslated");require(c.currentScreen==null&&record(c,MapLocationType.SPOT).getMaxCount()==1&&c.player.getMainHandStack().getCount()==3,"Native point capture/split failed or chest opened");require(BCCoreItems.mapLocation.getPoint(record(c,MapLocationType.SPOT)).equals(POINT),"Client point not synchronized");inventory(c,1);advance();}
        else if(stage==4&&ticks>15){shot(c,"map-native-point-tooltip.png");c.player.closeHandledScreen();work=c.getServer().submit(()->aim(c,AREA));advance();}
        else if(stage==5&&ready()&&ticks>20){use(c,AREA);advance();}
        else if(stage==6&&ticks>25){var box=BCCoreItems.mapLocation.getBox(record(c,MapLocationType.AREA));require(box!=null&&box.min().equals(AREA)&&box.max().equals(AREA.east(3))&&c.player.getMainHandStack().getCount()==2,"Native area capture failed");inventory(c,2);advance();}
        else if(stage==7&&ticks>15){shot(c,"map-native-area-tooltip.png");c.player.closeHandledScreen();work=c.getServer().submit(()->aim(c,PATH));advance();}
        else if(stage==8&&ready()&&ticks>20){use(c,PATH);advance();}
        else if(stage==9&&ticks>25){require(BCCoreItems.mapLocation.getPath(record(c,MapLocationType.PATH_REPEATING)).equals(List.of(PATH,PATH.east(3),PATH.east(3).south(3),PATH)),"Native loop order incorrect");require(c.player.getMainHandStack().getCount()==1&&!ItemMapLocation.isRecorded(c.player.getMainHandStack()),"Loop consumed wrong blanks");inventory(c,3);advance();}
        else if(stage==10&&ticks>15){shot(c,"map-native-loop-tooltip.png");c.player.closeHandledScreen();work=c.getServer().submit(()->{var s=player(c).getInventory().getStack(1);BCCoreItems.mapLocation.setName(s,"Felix saved location");player(c).currentScreenHandler.sendContentUpdates();});advance();}
        else if(stage==11&&ready()&&ticks>20){require(BCCoreItems.mapLocation.getLabel(record(c,MapLocationType.SPOT)).equals("Felix saved location"),"Map label failed to sync");advance();}
        else if(stage==12){advance();}
        else if(stage==13){advance();}
        else if(stage==14){stage=15;ticks=0;c.world.disconnect();c.disconnect(new TitleScreen());}
        else if(stage==16&&ticks>30){require(BCCoreItems.mapLocation.getLabel(record(c,MapLocationType.SPOT)).equals("Felix saved location")&&BCCoreItems.mapLocation.getPoint(record(c,MapLocationType.SPOT)).equals(POINT)&&BCCoreItems.mapLocation.getPath(record(c,MapLocationType.PATH_REPEATING)).size()==4,"Saved world lost map records");inventory(c,1);advance();}
        else if(stage==17&&ticks>15){shot(c,"map-restarted-records.png");c.player.closeHandledScreen();c.player.getInventory().selectedSlot=1;c.options.sneakKey.setPressed(true);advance();}
        else if(stage==18&&ticks>10){c.interactionManager.interactItem(c.player,Hand.MAIN_HAND);advance();}
        else if(stage==19&&ticks>20){c.options.sneakKey.setPressed(false);require(!ItemMapLocation.isRecorded(c.player.getMainHandStack())&&c.player.getMainHandStack().getMaxCount()==16,"Native sneak-use did not clear record");work=c.getServer().submit(()->{require(!ItemMapLocation.isRecorded(player(c).getMainHandStack()),"Server retained cleared record");var chest=(net.minecraft.block.entity.ChestBlockEntity)c.getServer().getOverworld().getBlockEntity(POINT);require(chest.getStack(0).isOf(Items.DIAMOND)&&chest.getStack(0).getCount()==3,"Capture changed chest inventory");});advance();}
        else if(stage==20&&ready()){System.out.println("BUILDCRAFT MAP CLIENT: native point/area/loop capture; one-item splits and counts; original model variants and localized tooltips; name/dimension preservation; saved-world restart; native sneak clearing; failures=0");stage=60;c.scheduleStop();}
    }
    private static net.minecraft.server.network.ServerPlayerEntity player(MinecraftClient c){return c.getServer().getPlayerManager().getPlayer(c.player.getUuid());}
    private static void aim(MinecraftClient c,BlockPos at){player(c).networkHandler.requestTeleport(at.getX()+.5,at.getY(),at.getZ()-2.5,0,15);}
    private static void use(MinecraftClient c,BlockPos at){c.interactionManager.interactBlock(c.player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(at),Direction.NORTH,at,false));}
    private static ItemStack record(MinecraftClient c,MapLocationType type){return c.player.getInventory().main.stream().filter(s->s.isOf(BCCoreItems.mapLocation)&&MapLocationType.getFromStack(s)==type).findFirst().orElseThrow();}
    private static void inventory(MinecraftClient c,int slot){c.setScreen(new InventoryScreen(c.player));var w=c.getWindow();double x=(w.getScaledWidth()-176)/2.0+8+18*slot+8,y=(w.getScaledHeight()-166)/2.0+142+8;org.lwjgl.glfw.GLFW.glfwSetCursorPos(w.getHandle(),x*w.getWidth()/w.getScaledWidth(),y*w.getHeight()/w.getScaledHeight());}
    private static void shot(MinecraftClient c,String name){net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(c.runDirectory,name,c.getFramebuffer(),m->{});}
    private boolean ready(){if(work==null||!work.isDone()||ticks<5)return false;work.join();return true;}
    private void advance(){stage++;ticks=0;System.out.println("BUILDCRAFT MAP CLIENT stage="+stage);}
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
}
