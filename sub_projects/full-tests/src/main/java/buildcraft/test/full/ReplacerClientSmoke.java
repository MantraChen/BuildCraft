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
import buildcraft.builders.item.ItemSchematicSingle;
import buildcraft.builders.snapshot.*;
import buildcraft.builders.tile.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.*;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
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

public final class ReplacerClientSmoke implements ClientModInitializer {
    private static final BlockPos CHEST=new BlockPos(2,-56,2),COPY=CHEST.north(3),STONE=CHEST.east(3),BRICKS=STONE.east(),LOG=BRICKS.east(),REPLACER=CHEST.south(5),BUILDER=REPLACER.east(8);
    private int stage,ticks;private long deadline;private String save;private CompletableFuture<Void> work;private Snapshot.Key originalKey,replacedKey;
    @Override public void onInitializeClient(){if(Boolean.getBoolean("buildcraft.replacerClientSmoke"))ClientTickEvents.END_CLIENT_TICK.register(this::tick);}
    private void tick(MinecraftClient c){
        if(stage==0){if(c.getOverlay()!=null||!(c.currentScreen instanceof TitleScreen))return;c.options.pauseOnLostFocus=false;c.options.getViewDistance().setValue(6);c.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);deadline=System.nanoTime()+360_000_000_000L;save="replacer-smoke-"+System.currentTimeMillis();stage=1;var rules=new GameRules();rules.get(GameRules.DO_MOB_SPAWNING).set(false,null);c.createIntegratedServerLoader().createAndStart(save,new LevelInfo("BuildCraft Replacer — Remake by FelixMantra",GameMode.CREATIVE,false,Difficulty.PEACEFUL,true,rules,DataConfiguration.SAFE_MODE),new GeneratorOptions(4754L,false,false),registries->registries.get(RegistryKeys.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createDimensionsRegistryHolder());return;}
        if(stage==50)return;if(System.nanoTime()>deadline)throw new IllegalStateException("Replacer client timeout stage "+stage);
        if(stage==18||stage==25){if(c.getServer()!=null||c.world!=null)return;stage++;ticks=0;c.createIntegratedServerLoader().start(new TitleScreen(),save);return;}
        if(stage>1 && stage!=19 && stage!=26 && c.currentScreen instanceof net.minecraft.client.gui.screen.DisconnectedScreen)throw new IllegalStateException("Replacer client disconnected stage "+stage);
        if(c.world==null||c.player==null||c.getServer()==null)return;ticks++;if(ticks%20==0)c.getToastManager().clear();
        if(stage==1){work=c.getServer().submit(()->{var w=c.getServer().getOverworld();w.setTimeOfDay(6000);for(var p:List.of(CHEST,COPY,STONE,BRICKS,LOG,REPLACER,BUILDER))w.setBlockState(p.down(),Blocks.STONE.getDefaultState());w.setBlockState(CHEST,Blocks.CHEST.getDefaultState());((ChestBlockEntity)w.getBlockEntity(CHEST)).setStack(0,gem(3));w.setBlockState(STONE,Blocks.STONE.getDefaultState());w.setBlockState(BRICKS,Blocks.BRICKS.getDefaultState());w.setBlockState(LOG,Blocks.OAK_LOG.getDefaultState().with(PillarBlock.AXIS,Direction.Axis.X));var p=player(c);p.getInventory().clear();p.getInventory().setStack(0,new ItemStack(BCBuildersItems.schematicSingle,4));p.getInventory().selectedSlot=0;p.currentScreenHandler.sendContentUpdates();aim(c,CHEST);});advance();}
        else if(stage==2&&ready()&&ticks>25){interact(c,CHEST);advance();}
        else if(stage==3&&ticks>25){require(c.currentScreen==null,"Capture opened native chest GUI");work=c.getServer().submit(()->{var p=player(c);require(p.getMainHandStack().getCount()==3&&!ItemSchematicSingle.isUsed(p.getMainHandStack()),"Capture changed all blank samples");var captured=findSample(c,null);require(captured.getCount()==1,"Capture produced multiple samples");var held=captured.copy();p.getInventory().removeOne(captured);var blanks=p.getMainHandStack().copy();p.getInventory().setStack(0,held);p.getInventory().setStack(9,blanks);p.getInventory().setStack(10,new ItemStack(Items.CHEST));p.getInventory().setStack(11,gem(2));p.getInventory().setStack(12,new ItemStack(Items.DIAMOND));p.currentScreenHandler.sendContentUpdates();aim(c,COPY);});advance();}
        else if(stage==4&&ready()&&ticks>25){shot(c,"replacer-sample-material-hud.png");place(c,COPY);advance();}
        else if(stage==5&&ticks>25){work=c.getServer().submit(()->{require(c.getServer().getOverworld().isAir(COPY)&&count(c,Items.CHEST)==1&&count(c,Items.DIAMOND)==3,"Shortage consumed materials or built chest");player(c).getInventory().setStack(12,gem(1));player(c).currentScreenHandler.sendContentUpdates();});advance();}
        else if(stage==6&&ready()&&ticks>25){place(c,COPY);advance();}
        else if(stage==7&&ticks>25){work=c.getServer().submit(()->{var w=c.getServer().getOverworld();require(w.getBlockEntity(COPY) instanceof ChestBlockEntity chest&&ItemStack.areEqual(chest.getStack(0),gem(3))&&count(c,Items.CHEST)==0&&count(c,Items.DIAMOND)==0,"Direct native copy lost exact data/materials");require(ItemStack.areEqual(((ChestBlockEntity)w.getBlockEntity(CHEST)).getStack(0),gem(3)),"Capture modified original chest");var p=player(c);p.getInventory().setStack(20,p.getMainHandStack().copy());p.getInventory().setStack(0,p.getInventory().getStack(9));p.getInventory().setStack(9,ItemStack.EMPTY);p.currentScreenHandler.sendContentUpdates();aim(c,STONE);});advance();}
        else if(stage==8&&ready()&&ticks>25){interact(c,STONE);advance();}
        else if(stage==9&&ticks>25){work=c.getServer().submit(()->{findSample(c,Blocks.STONE).setCustomName(Text.literal("From stone"));aim(c,BRICKS);player(c).currentScreenHandler.sendContentUpdates();});advance();}
        else if(stage==10&&ready()&&ticks>25){interact(c,BRICKS);advance();}
        else if(stage==11&&ticks>25){work=c.getServer().submit(()->{var p=player(c);findSample(c,Blocks.BRICKS).setCustomName(Text.literal("To bricks"));var w=c.getServer().getOverworld();var from=ItemSchematicSingle.getSchematicSafe(findSample(c,Blocks.STONE));var log=w.getBlockState(LOG);var original=new Blueprint();original.size=new BlockPos(4,1,1);original.offset=BlockPos.ORIGIN;original.facing=Direction.NORTH;original.data=new int[]{0,0,0,1};original.palette.add(from);original.palette.add(SchematicBlockManager.getSchematicBlock(new SchematicBlockContext(w,LOG,LOG,log,log.getBlock())));original.computeKey();originalKey=original.key;require(GlobalSavedDataSnapshots.get(w).addSnapshot(original),"Original file save failed");p.getInventory().setStack(9,BCBuildersItems.snapshot.getUsed(EnumSnapshotType.BLUEPRINT,new Snapshot.Header(originalKey,p.getUuid(),new Date(),"Felix stone design")));p.getInventory().setStack(0,new ItemStack(BCBuildersBlocks.replacer));p.currentScreenHandler.sendContentUpdates();aim(c,REPLACER);});advance();}
        else if(stage==12&&ready()&&ticks>25){place(c,REPLACER);advance();}
        else if(stage==13&&ticks>25){work=c.getServer().submit(()->{require(replacer(c)!=null,"Native replacer placement failed");player(c).getInventory().setStack(0,ItemStack.EMPTY);player(c).currentScreenHandler.sendContentUpdates();});advance();}
        else if(stage==14&&ready()&&ticks>20){interact(c,REPLACER);advance();}
        else if(stage==15&&ticks>25){require(c.currentScreen instanceof GuiReplacer,"Native replacer screen missing");quick(c,BCBuildersItems.snapshot,null);quick(c,BCBuildersItems.schematicSingle,"From stone");advance();}
        else if(stage==16&&ticks>30){work=c.getServer().submit(()->{var t=replacer(c);require(t.getStatus()==0&&!t.invSnapshot.getStackInSlot(0).isEmpty()&&!t.invSchematicFrom.getStackInSlot(0).isEmpty()&&t.invSchematicTo.getStackInSlot(0).isEmpty(),"Partial input consumed or wrong slot");});require(ClientSnapshots.INSTANCE.getSnapshot(originalKey)!=null,"Preview snapshot not received");shot(c,"replacer-original-preview.png");advance();}
        else if(stage==17&&ready()&&ticks>5){disconnect(c,18);}
        else if(stage==19&&ticks>35){work=c.getServer().submit(()->{var t=replacer(c);require(!t.invSchematicFrom.getStackInSlot(0).isEmpty()&&t.invSchematicTo.getStackInSlot(0).isEmpty()&&BCBuildersItems.snapshot.getHeader(t.invSnapshot.getStackInSlot(0)).key.equals(originalKey),"Partial replacer input lost on restart");aim(c,REPLACER);});advance();}
        else if(stage==20&&ready()&&ticks>25){interact(c,REPLACER);advance();}
        else if(stage==21&&ticks>25){require(c.currentScreen instanceof GuiReplacer,"Resumed native replacer screen missing");quick(c,BCBuildersItems.schematicSingle,"To bricks");advance();}
        else if(stage==22&&ticks>30){work=c.getServer().submit(()->{var t=replacer(c);require(t.getStatus()==1&&t.getReplaced()==3&&t.invSchematicFrom.getStackInSlot(0).isEmpty()&&t.invSchematicTo.getStackInSlot(0).isEmpty(),"Replacement result/materials wrong");replacedKey=BCBuildersItems.snapshot.getHeader(t.invSnapshot.getStackInSlot(0)).key;require(!replacedKey.equals(originalKey),"Replacement kept original key");var old=(Blueprint)GlobalSavedDataSnapshots.get(t.getWorld()).getSnapshot(originalKey);require(old.palette.get(0).computeRequiredItems().get(0).isOf(Items.STONE),"Replacement changed original file");});advance();}
        else if(stage==23&&ready()&&ticks>25){require(((ContainerReplacer)c.player.currentScreenHandler).tile.getStatus()==1&&ClientSnapshots.INSTANCE.getSnapshot(replacedKey)!=null,"Replacement status/preview not synchronized");shot(c,"replacer-changed-preview.png");c.interactionManager.clickSlot(c.player.currentScreenHandler.syncId,0,0,net.minecraft.screen.slot.SlotActionType.QUICK_MOVE,c.player);advance();}
        else if(stage==24&&ticks>10){disconnect(c,25);}
        else if(stage==26&&ticks>35){work=c.getServer().submit(()->{require(BCBuildersItems.snapshot.getHeader(used(c)).key.equals(replacedKey),"Saved player output lost new key");GlobalSavedDataSnapshots.reInit(buildcraft.lib.tile.TileBC_Neptune.NetSide.SERVER);require(GlobalSavedDataSnapshots.get(c.getServer().getOverworld()).getSnapshot(replacedKey)!=null,"Replaced file missing after complete restart");var p=player(c);p.getInventory().setStack(0,new ItemStack(BCBuildersBlocks.builder));p.getInventory().setStack(10,new ItemStack(Items.BRICKS,3));p.getInventory().setStack(11,new ItemStack(Items.OAK_LOG));p.currentScreenHandler.sendContentUpdates();aim(c,BUILDER);});advance();}
        else if(stage==27&&ready()&&ticks>25){place(c,BUILDER);advance();}
        else if(stage==28&&ticks>25){work=c.getServer().submit(()->{require(builder(c)!=null,"Native builder placement failed");player(c).getInventory().setStack(0,ItemStack.EMPTY);player(c).currentScreenHandler.sendContentUpdates();});advance();}
        else if(stage==29&&ready()&&ticks>20){interact(c,BUILDER);advance();}
        else if(stage==30&&ticks>25){require(c.currentScreen instanceof GuiBuilder,"Native reconstruction screen missing");quick(c,BCBuildersItems.snapshot,null);quick(c,Items.BRICKS,null);quick(c,Items.OAK_LOG,null);advance();}
        else if(stage==31&&ticks>25){shot(c,"replacer-builder-materials.png");work=c.getServer().submit(()->{builder(c).getBattery().addPower(1000*MjAPI.MJ,false);});c.player.closeHandledScreen();advance();}
        else if(stage==32&&ready()&&ticks>40){work=c.getServer().submit(()->{var t=builder(c);require(t.isFinished(),"Replaced blueprint did not finish native construction");var info=t.getBlueprintBuildingInfo();int bricks=0,logs=0;for(var at:BlockPos.iterate(info.box.min(),info.box.max())){var state=t.getWorld().getBlockState(at);if(state.isOf(Blocks.BRICKS))bricks++;if(state.isOf(Blocks.OAK_LOG))logs++;}require(bricks==3&&logs==1,"Reconstruction used old palette");for(int i=0;i<27;i++)require(t.invResources.getStackInSlot(i).isEmpty(),"Reconstruction left materials");aim(c,info.box.min().add(1,0,0));});advance();}
        else if(stage==33&&ready()&&ticks>30){shot(c,"replacer-reconstructed-world.png");System.out.println("BUILDCRAFT REPLACER CLIENT: native chest capture before GUI, exact material shortage/paid placement, material HUD, native sample capture/replacer placement, partial-input restart, original/new preview files, status and inventory packets, completed-output restart, native builder construction; failures=0");stage=50;c.scheduleStop();}
    }
    private static ItemStack gem(int n){var s=new ItemStack(Items.DIAMOND,n);s.setCustomName(Text.literal("Remake by FelixMantra"));return s;}
    private static ItemStack findSample(MinecraftClient c,Block block){for(var s:player(c).getInventory().main)if(ItemSchematicSingle.isUsed(s)){var schematic=ItemSchematicSingle.getSchematicSafe(s);if(block==null||schematic instanceof SchematicBlockDefault normal&&normal.placementState().isOf(block))return s;}throw new IllegalStateException("Missing captured sample "+block);}
    private static ItemStack used(MinecraftClient c){for(var s:player(c).getInventory().main)if(BCBuildersItems.snapshot.getHeader(s)!=null)return s;throw new IllegalStateException("Missing saved blueprint");}
    private static int count(MinecraftClient c,Item item){int n=0;for(var s:player(c).getInventory().main)if(s.isOf(item))n+=s.getCount();return n;}
    private static TileReplacer replacer(MinecraftClient c){return (TileReplacer)c.getServer().getOverworld().getBlockEntity(REPLACER);}
    private static TileBuilder builder(MinecraftClient c){return (TileBuilder)c.getServer().getOverworld().getBlockEntity(BUILDER);}
    private static net.minecraft.server.network.ServerPlayerEntity player(MinecraftClient c){return c.getServer().getPlayerManager().getPlayer(c.player.getUuid());}
    private static void quick(MinecraftClient c,Item item,String name){var h=c.player.currentScreenHandler;for(int i=0;i<h.slots.size();i++){var s=h.getSlot(i).getStack();if(h.getSlot(i).inventory==c.player.getInventory()&&s.isOf(item)&&(name==null||s.getName().getString().equals(name))){c.interactionManager.clickSlot(h.syncId,i,0,net.minecraft.screen.slot.SlotActionType.QUICK_MOVE,c.player);return;}}throw new IllegalStateException("Missing quick-move input "+item+" "+name);}
    private static void interact(MinecraftClient c,BlockPos p){c.interactionManager.interactBlock(c.player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(p),Direction.NORTH,p,false));}
    private static void place(MinecraftClient c,BlockPos p){c.interactionManager.interactBlock(c.player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(p.down()).add(0,.5,0),Direction.UP,p.down(),false));}
    private static void aim(MinecraftClient c,BlockPos at){var p=player(c);var eye=Vec3d.ofCenter(at).add(1,2,-4);var look=Vec3d.ofCenter(at).subtract(eye).normalize();p.teleport(p.getServerWorld(),eye.x,eye.y-p.getEyeHeight(p.getPose()),eye.z,(float)Math.toDegrees(Math.atan2(-look.x,look.z)),(float)-Math.toDegrees(Math.asin(look.y)));p.setHeadYaw(p.getYaw());p.getAbilities().flying=true;p.sendAbilitiesUpdate();}
    private void disconnect(MinecraftClient c,int next){stage=next;ticks=0;c.world.disconnect();c.disconnect(new TitleScreen());}
    private static void shot(MinecraftClient c,String name){net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(c.runDirectory,name,c.getFramebuffer(),m->{});}
    private boolean ready(){if(work==null||!work.isDone()||ticks<5)return false;work.join();return true;}
    private void advance(){stage++;ticks=0;System.out.println("BUILDCRAFT REPLACER CLIENT stage="+stage);}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
