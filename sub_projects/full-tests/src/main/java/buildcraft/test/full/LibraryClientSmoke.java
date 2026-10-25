/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import buildcraft.api.enums.EnumSnapshotType;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.schematics.SchematicBlockContext;
import buildcraft.builders.*;
import buildcraft.builders.container.*;
import buildcraft.builders.gui.*;
import buildcraft.builders.snapshot.*;
import buildcraft.builders.tile.*;
import buildcraft.lib.tile.TileBC_Neptune.NetSide;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.*;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.world.gen.*;
import net.minecraft.world.level.LevelInfo;

public final class LibraryClientSmoke implements ClientModInitializer {
    private static final BlockPos LIBRARY=new BlockPos(2,-56,2),SOURCE=LIBRARY.east(3),BUILDER=LIBRARY.east(8);
    private int stage,ticks;private long deadline;private String first,second;private CompletableFuture<Void> work;
    private Blueprint blueprint;private Snapshot.Header metadata;private java.nio.file.Path blocked;
    private final List<Snapshot.Key> examples=new ArrayList<>();
    @Override public void onInitializeClient(){if(Boolean.getBoolean("buildcraft.libraryClientSmoke"))ClientTickEvents.END_CLIENT_TICK.register(this::tick);}
    private void tick(MinecraftClient c){
        if(stage==0){
            if(c.getOverlay()!=null||!(c.currentScreen instanceof TitleScreen))return;
            c.options.pauseOnLostFocus=false;c.options.getViewDistance().setValue(6);c.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
            deadline=System.nanoTime()+360_000_000_000L;first="library-export-"+System.currentTimeMillis();second="library-import-"+System.currentTimeMillis();stage=1;create(c,first);return;
        }
        if(stage==60)return;if(System.nanoTime()>deadline)throw new IllegalStateException("Library client timeout stage "+stage);
        if(stage==14){if(c.getServer()!=null||c.world!=null)return;stage=15;ticks=0;GlobalSavedDataSnapshots.reInit(NetSide.CLIENT);create(c,second);return;}
        if(stage==25){if(c.getServer()!=null||c.world!=null)return;stage=26;ticks=0;c.createIntegratedServerLoader().start(new TitleScreen(),second);return;}
        if(stage>1&&stage!=15&&stage!=26&&c.currentScreen instanceof net.minecraft.client.gui.screen.DisconnectedScreen)throw new IllegalStateException("Library client disconnected stage "+stage);
        if(c.world==null||c.player==null||c.getServer()==null)return;ticks++;if(ticks%20==0)c.getToastManager().clear();
        if(stage==1){
            work=c.getServer().submit(()->{
                var w=c.getServer().getOverworld();w.setTimeOfDay(6000);w.setBlockState(LIBRARY.down(),Blocks.STONE.getDefaultState());w.setBlockState(SOURCE.down(),Blocks.STONE.getDefaultState());w.setBlockState(SOURCE,Blocks.CHEST.getDefaultState());
                var chest=(ChestBlockEntity)w.getBlockEntity(SOURCE);chest.setStack(0,gem());
                blueprint=new Blueprint();blueprint.size=new BlockPos(1,1,1);blueprint.offset=BlockPos.ORIGIN;blueprint.facing=Direction.NORTH;blueprint.data=new int[]{0};
                blueprint.palette.add(SchematicBlockManager.getSchematicBlock(new SchematicBlockContext(w,SOURCE,SOURCE,w.getBlockState(SOURCE),Blocks.CHEST)));blueprint.computeKey();
                metadata=new Snapshot.Header(blueprint.key,player(c).getUuid(),new Date(4754),"Felix travelling chest");
                require(GlobalSavedDataSnapshots.get(NetSide.SERVER).addSnapshot(blueprint),"Source snapshot save failed");
                try{require(LibraryTransfer.pack(blueprint,metadata).length>3*LibraryTransfer.CHUNK_BYTES,"Client fixture is not multipart");}catch(Exception e){throw new RuntimeException(e);}
                var p=player(c);p.getInventory().clear();p.getInventory().setStack(0,new ItemStack(BCBuildersBlocks.library));p.getInventory().setStack(9,BCBuildersItems.snapshot.getUsed(blueprint.getType(),metadata));p.currentScreenHandler.sendContentUpdates();aim(c,LIBRARY);
            });advance();
        }else if(stage==2&&ready()&&ticks>25){
            var local=GlobalSavedDataSnapshots.get(NetSide.CLIENT);local.removeSnapshot(blueprint.key);
            blocked=c.runDirectory.toPath().resolve("snapshots-client").resolve(blueprint.key+".bcnbt");
            try{java.nio.file.Files.createDirectories(blocked);}catch(Exception e){throw new RuntimeException(e);}
            place(c,LIBRARY);advance();
        }else if(stage==3&&ticks>25){work=c.getServer().submit(()->{require(library(c)!=null,"Native library placement missing");player(c).getInventory().setStack(0,ItemStack.EMPTY);player(c).currentScreenHandler.sendContentUpdates();});advance();}
        else if(stage==4&&ready()&&ticks>20){interact(c,LIBRARY);advance();}
        else if(stage==5&&ticks>25){require(c.currentScreen instanceof GuiElectronicLibrary,"Native library GUI missing");quick(c,BCBuildersItems.snapshot);advance();}
        else if(stage==6&&ticks>80){
            work=c.getServer().submit(()->{var t=library(c);require(((ContainerElectronicLibrary)player(c).currentScreenHandler).status()==7&&!t.invDownIn.getStackInSlot(0).isEmpty()&&t.invDownOut.getStackInSlot(0).isEmpty(),"Failed local save consumed input");});
            shot(c,"library-save-failure-retains-input.png");advance();
        }else if(stage==7&&ready()){
            try{java.nio.file.Files.delete(blocked);}catch(Exception e){throw new RuntimeException(e);}advance();
        }else if(stage==8&&ticks>170){
            work=c.getServer().submit(()->{var t=library(c);require(t.invDownIn.getStackInSlot(0).isEmpty()&&BCBuildersItems.snapshot.getHeader(t.invDownOut.getStackInSlot(0)).equals(metadata),"Export retry lost original media");});
            var saved=GlobalSavedDataSnapshots.get(NetSide.CLIENT).getSnapshot(blueprint.key);require(saved!=null&&saved.key.header.equals(metadata),"Local collection lost header");require(Snapshot.writeToNBT(saved.copy()).equals(Snapshot.writeToNBT(blueprint)),"Local collection lost full chest payload");
            // Additional real local entries exercise paging, filtering and deletion.
            for(int i=0;i<7;i++){
                var t=new Template();t.size=new BlockPos(8,1,1);t.offset=new BlockPos(900+i,0,0);t.facing=Direction.NORTH;t.data=BitSet.valueOf(new byte[]{(byte)i});t.computeKey();
                t.key=new Snapshot.Key(t.key,new Snapshot.Header(t.key,metadata.owner,new Date(0),"Example "+(i+1)));require(GlobalSavedDataSnapshots.get(NetSide.CLIENT).addSnapshot(t),"Example collection save failed");examples.add(t.key);
            }advance();
        }else if(stage==9&&ready()&&ticks>30){
            shot(c,"library-local-collection-page-one.png");click(c,146,117);advance();
        }else if(stage==10&&ticks>10){shot(c,"library-local-collection-page-two.png");search(c,"Felix travelling");advance();}
        else if(stage==11&&ticks>20){
            click(c,60,48);shot(c,"library-exported-chest.png");c.interactionManager.clickSlot(c.player.currentScreenHandler.syncId,1,0,net.minecraft.screen.slot.SlotActionType.QUICK_MOVE,c.player);advance();
        }else if(stage==12&&ticks>15){
            work=c.getServer().submit(()->{
                require(GlobalSavedDataSnapshots.get(NetSide.SERVER).removeSnapshot(blueprint.key),"Could not remove fixture server file");
                GlobalSavedDataSnapshots.reInit(NetSide.SERVER);require(GlobalSavedDataSnapshots.get(NetSide.SERVER).getSnapshot(blueprint.key)==null,"Server copy still available before cross-world test");
            });advance();
        }else if(stage==13&&ready()){disconnect(c,14);}
        else if(stage==15&&ticks>30){
            require(GlobalSavedDataSnapshots.get(NetSide.CLIENT).getSnapshot(blueprint.key)!=null,"Collection lost on complete world change");
            work=c.getServer().submit(()->{
                var w=c.getServer().getOverworld();w.setTimeOfDay(6000);w.setBlockState(LIBRARY.down(),Blocks.STONE.getDefaultState());w.setBlockState(BUILDER.down(),Blocks.STONE.getDefaultState());
                require(GlobalSavedDataSnapshots.get(NetSide.SERVER).getSnapshot(blueprint.key)==null,"Second world had source server file");
                var p=player(c);p.getInventory().clear();p.getInventory().setStack(0,new ItemStack(BCBuildersBlocks.library));p.getInventory().setStack(9,BCBuildersItems.snapshot.getClean(EnumSnapshotType.TEMPLATE));p.currentScreenHandler.sendContentUpdates();aim(c,LIBRARY);
            });advance();
        }else if(stage==16&&ready()&&ticks>25){place(c,LIBRARY);advance();}
        else if(stage==17&&ticks>25){work=c.getServer().submit(()->{player(c).getInventory().setStack(0,ItemStack.EMPTY);player(c).currentScreenHandler.sendContentUpdates();});advance();}
        else if(stage==18&&ready()&&ticks>20){interact(c,LIBRARY);advance();}
        else if(stage==19&&ticks>25){require(c.currentScreen instanceof GuiElectronicLibrary,"Second world library screen missing");search(c,"Felix travelling");click(c,60,48);quick(c,BCBuildersItems.snapshot);advance();}
        else if(stage==20&&ticks>100){
            work=c.getServer().submit(()->{
                var t=library(c);var result=BCBuildersItems.snapshot.getHeader(t.invUpOut.getStackInSlot(0));require(result!=null&&result.equals(metadata)&&t.invUpIn.getStackInSlot(0).isEmpty(),"Cross-world import lost metadata/media");
                GlobalSavedDataSnapshots.reInit(NetSide.SERVER);var imported=GlobalSavedDataSnapshots.get(NetSide.SERVER).getSnapshot(blueprint.key);require(imported!=null&&Snapshot.writeToNBT(imported).equals(Snapshot.writeToNBT(blueprint)),"Cross-world full data restore failed");
            });shot(c,"library-cross-world-import.png");advance();
        }else if(stage==21&&ready()){
            click(c,203,117);advance();
        }else if(stage==22&&ticks>15){
            require(GlobalSavedDataSnapshots.get(NetSide.CLIENT).getSnapshot(blueprint.key)==null,"Delete local retained file");
            work=c.getServer().submit(()->{require(GlobalSavedDataSnapshots.get(NetSide.SERVER).getSnapshot(blueprint.key)!=null,"Delete local erased world blueprint");});shot(c,"library-local-delete-world-copy-preserved.png");
            c.interactionManager.clickSlot(c.player.currentScreenHandler.syncId,3,0,net.minecraft.screen.slot.SlotActionType.QUICK_MOVE,c.player);advance();
        }else if(stage==23&&ready()&&ticks>15){work=c.getServer().submit(()->{require(used(c)!=null,"Native output quick move failed");});advance();}
        else if(stage==24&&ready()){disconnect(c,25);}
        else if(stage==26&&ticks>35){
            work=c.getServer().submit(()->{require(BCBuildersItems.snapshot.getHeader(used(c)).equals(metadata),"Imported item lost metadata on restart");var p=player(c);p.getInventory().setStack(0,new ItemStack(BCBuildersBlocks.builder));p.getInventory().setStack(10,new ItemStack(Items.CHEST));p.getInventory().setStack(11,gem());p.currentScreenHandler.sendContentUpdates();aim(c,BUILDER);});advance();
        }else if(stage==27&&ready()&&ticks>25){place(c,BUILDER);advance();}
        else if(stage==28&&ticks>25){work=c.getServer().submit(()->{require(builder(c)!=null,"Native builder placement failed");player(c).getInventory().setStack(0,ItemStack.EMPTY);player(c).currentScreenHandler.sendContentUpdates();});advance();}
        else if(stage==29&&ready()&&ticks>20){interact(c,BUILDER);advance();}
        else if(stage==30&&ticks>25){require(c.currentScreen instanceof GuiBuilder,"Native builder GUI missing");quick(c,BCBuildersItems.snapshot);quick(c,Items.CHEST);quick(c,Items.DIAMOND);advance();}
        else if(stage==31&&ticks>25){work=c.getServer().submit(()->{builder(c).getBattery().addPower(1000*MjAPI.MJ,false);});c.player.closeHandledScreen();advance();}
        else if(stage==32&&ready()&&ticks>50){
            work=c.getServer().submit(()->{
                var t=builder(c);require(t.isFinished(),"Imported chest did not build");var at=t.getBlueprintBuildingInfo().box.min();var chest=(ChestBlockEntity)t.getWorld().getBlockEntity(at);
                require(chest!=null&&ItemStack.areEqual(chest.getStack(0),gem()),"Imported construction lost exact chest contents");
                for(int i=0;i<27;i++)require(t.invResources.getStackInSlot(i).isEmpty(),"Imported construction left paid material");aim(c,at);
            });advance();
        }else if(stage==33&&ready()&&ticks>25){
            shot(c,"library-imported-chest-built.png");for(var key:examples)GlobalSavedDataSnapshots.get(NetSide.CLIENT).removeSnapshot(key);
            System.out.println("BUILDCRAFT LIBRARY CLIENT: native library placement/screens/slots; multipart full chest export; local disk failure retains input and retries; collection paging/search; different world and missing server file; full local upload with original owner/date/name; delete local preserves server; complete restart and native paid chest reconstruction; failures=0");stage=60;c.scheduleStop();
        }
    }
    private static ItemStack gem(){var s=new ItemStack(Items.DIAMOND,3);s.setCustomName(Text.literal("Remake by FelixMantra"));byte[] data=new byte[110000];new Random(4754).nextBytes(data);s.getOrCreateNbt().putByteArray("FelixLibraryPayload",data);return s;}
    private static void create(MinecraftClient c,String name){var rules=new GameRules();rules.get(GameRules.DO_MOB_SPAWNING).set(false,null);c.createIntegratedServerLoader().createAndStart(name,new LevelInfo("BuildCraft Library — Remake by FelixMantra",GameMode.CREATIVE,false,Difficulty.PEACEFUL,true,rules,DataConfiguration.SAFE_MODE),new GeneratorOptions(4754L,false,false),registries->registries.get(RegistryKeys.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createDimensionsRegistryHolder());}
    private static void search(MinecraftClient c,String value){for(var child:c.currentScreen.children())if(child instanceof TextFieldWidget field){field.setText(value);return;}throw new IllegalStateException("Native collection search missing");}
    private static void click(MinecraftClient c,int x,int y){c.currentScreen.mouseClicked((c.currentScreen.width-244)/2+x,(c.currentScreen.height-234)/2+y,0);}
    private static ItemStack used(MinecraftClient c){for(var s:player(c).getInventory().main)if(BCBuildersItems.snapshot.getHeader(s)!=null)return s;throw new IllegalStateException("Missing imported blueprint");}
    private static TileElectronicLibrary library(MinecraftClient c){return (TileElectronicLibrary)c.getServer().getOverworld().getBlockEntity(LIBRARY);}
    private static TileBuilder builder(MinecraftClient c){return (TileBuilder)c.getServer().getOverworld().getBlockEntity(BUILDER);}
    private static net.minecraft.server.network.ServerPlayerEntity player(MinecraftClient c){return c.getServer().getPlayerManager().getPlayer(c.player.getUuid());}
    private static void quick(MinecraftClient c,Item item){var h=c.player.currentScreenHandler;for(int i=0;i<h.slots.size();i++)if(h.getSlot(i).inventory==c.player.getInventory()&&h.getSlot(i).getStack().isOf(item)){c.interactionManager.clickSlot(h.syncId,i,0,net.minecraft.screen.slot.SlotActionType.QUICK_MOVE,c.player);return;}throw new IllegalStateException("Missing quick move "+item);}
    private static void interact(MinecraftClient c,BlockPos p){c.interactionManager.interactBlock(c.player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(p),Direction.NORTH,p,false));}
    private static void place(MinecraftClient c,BlockPos p){c.interactionManager.interactBlock(c.player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(p.down()).add(0,.5,0),Direction.UP,p.down(),false));}
    private static void aim(MinecraftClient c,BlockPos at){var p=player(c);var eye=Vec3d.ofCenter(at).add(1,2,-4);var look=Vec3d.ofCenter(at).subtract(eye).normalize();p.teleport(p.getServerWorld(),eye.x,eye.y-p.getEyeHeight(p.getPose()),eye.z,(float)Math.toDegrees(Math.atan2(-look.x,look.z)),(float)-Math.toDegrees(Math.asin(look.y)));p.setHeadYaw(p.getYaw());p.getAbilities().flying=true;p.sendAbilitiesUpdate();}
    private void disconnect(MinecraftClient c,int next){stage=next;ticks=0;c.world.disconnect();c.disconnect(new TitleScreen());}
    private static void shot(MinecraftClient c,String name){net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(c.runDirectory,name,c.getFramebuffer(),m->{});}
    private boolean ready(){if(work==null||!work.isDone()||ticks<5)return false;work.join();return true;}
    private void advance(){stage++;ticks=0;System.out.println("BUILDCRAFT LIBRARY CLIENT stage="+stage);}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
