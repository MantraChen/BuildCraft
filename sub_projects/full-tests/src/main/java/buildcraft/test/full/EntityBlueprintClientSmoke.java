/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import buildcraft.api.enums.EnumSnapshotType;
import buildcraft.api.mj.*;
import buildcraft.builders.*;
import buildcraft.builders.container.*;
import buildcraft.builders.gui.*;
import buildcraft.builders.snapshot.*;
import buildcraft.builders.tile.*;
import buildcraft.core.*;
import buildcraft.core.tile.TileMarkerVolume;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.*;
import net.minecraft.entity.decoration.*;
import net.minecraft.entity.decoration.painting.*;
import net.minecraft.entity.vehicle.BoatEntity;
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

public final class EntityBlueprintClientSmoke implements ClientModInitializer {
    private static final BlockPos ARCH=new BlockPos(2,-56,2),SOURCE=ARCH.south(),BUILDER=ARCH.east(10),TARGET=BUILDER.west();
    private int stage,ticks;private long deadline;private String save;private CompletableFuture<Void> work;private Set<UUID> savedIds=Set.of();
    @Override public void onInitializeClient(){if(Boolean.getBoolean("buildcraft.entityBlueprintClientSmoke"))ClientTickEvents.END_CLIENT_TICK.register(this::tick);}
    private void tick(MinecraftClient client){
        if(stage==0){if(client.getOverlay()!=null||!(client.currentScreen instanceof TitleScreen))return;client.options.pauseOnLostFocus=false;client.options.getViewDistance().setValue(6);client.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);deadline=System.nanoTime()+360_000_000_000L;save="entities-smoke-"+System.currentTimeMillis();stage=1;var rules=new GameRules();rules.get(GameRules.DO_MOB_SPAWNING).set(false,null);client.createIntegratedServerLoader().createAndStart(save,new LevelInfo("BuildCraft Entities — Remake by FelixMantra",GameMode.CREATIVE,false,Difficulty.PEACEFUL,true,rules,DataConfiguration.SAFE_MODE),new GeneratorOptions(4754L,false,false),registries->registries.get(RegistryKeys.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createDimensionsRegistryHolder());return;}
        if(stage==40)return;if(System.nanoTime()>deadline)throw new IllegalStateException("Entity client timeout stage "+stage);
        if(stage==11||stage==19||stage==26){if(client.getServer()!=null||client.world!=null)return;stage++;ticks=0;client.createIntegratedServerLoader().start(new TitleScreen(),save);return;}
        if(stage>1 && stage!=12 && stage!=20 && stage!=27 && client.currentScreen instanceof net.minecraft.client.gui.screen.DisconnectedScreen)throw new IllegalStateException("Entity client disconnected stage "+stage);
        if(client.world==null||client.player==null||client.getServer()==null)return;ticks++;if(ticks%20==0)client.getToastManager().clear();
        if(stage==1){work=client.getServer().submit(()->{var w=client.getServer().getOverworld();w.setTimeOfDay(6000);w.setBlockState(ARCH.down(),Blocks.STONE.getDefaultState());for(var p:List.of(SOURCE,SOURCE.east(4),SOURCE.south(2),SOURCE.up(2))){w.setBlockState(p.down(),Blocks.STONE.getDefaultState());w.setBlockState(p,BCCoreBlocks.markerVolume.getDefaultState().with(BCCoreBlocks.markerVolume.getFacingProperty(),Direction.UP));}player(client).getInventory().clear();aim(client,SOURCE,false);});advance();}
        else if(stage==2&&ready()&&ticks>25){interact(client,SOURCE);advance();}
        else if(stage==3&&ticks>25){work=client.getServer().submit(()->{require(((TileMarkerVolume)client.getServer().getOverworld().getBlockEntity(SOURCE)).getCurrentConnection()!=null,"Native volume connection failed");player(client).getInventory().setStack(0,new ItemStack(BCBuildersBlocks.architect));player(client).getInventory().selectedSlot=0;player(client).currentScreenHandler.sendContentUpdates();aim(client,ARCH,false);});advance();}
        else if(stage==4&&ready()&&ticks>25){client.interactionManager.interactBlock(client.player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(ARCH.down()).add(0,.5,0),Direction.UP,ARCH.down(),false));advance();}
        else if(stage==5&&ticks>25){work=client.getServer().submit(()->{var w=client.getServer().getOverworld();require(architect(client).hasArea()&&architect(client).box.size().equals(new BlockPos(5,3,3)),"Native entity selection wrong");for(var p:BlockPos.iterate(SOURCE,SOURCE.add(4,2,2)))w.setBlockState(p,p.getY()==SOURCE.getY()||p.getZ()==SOURCE.getZ()+2?Blocks.STONE.getDefaultState():Blocks.AIR.getDefaultState());
            var boat=new BoatEntity(w,SOURCE.getX()+.8,SOURCE.getY()+1,SOURCE.getZ()+.8);boat.setYaw(30);w.spawnEntity(boat);
            var armor=new ArmorStandEntity(w,0,0,0);armor.setNoGravity(true);armor.setYaw(30);armor.equipStack(EquipmentSlot.HEAD,helmet());armor.setHeadRotation(new EulerAngle(10,20,30));armor.startRiding(boat,true);boat.updatePassengerPosition(armor);w.spawnEntity(armor);
            var frame=new ItemFrameEntity(w,SOURCE.add(2,1,1),Direction.NORTH);frame.setHeldItemStack(display());frame.setRotation(3);w.spawnEntity(frame);
            var glow=new GlowItemFrameEntity(w,SOURCE.add(3,2,1),Direction.NORTH);glow.setHeldItemStack(new ItemStack(Items.CLOCK));w.spawnEntity(glow);
            w.spawnEntity(new PaintingEntity(w,SOURCE.add(4,1,1),Direction.NORTH,Registries.PAINTING_VARIANT.entryOf(PaintingVariants.KEBAB)));
            player(client).getInventory().clear();player(client).getInventory().setStack(9,BCBuildersItems.snapshot.getClean(EnumSnapshotType.BLUEPRINT));player(client).currentScreenHandler.sendContentUpdates();aim(client,ARCH,false);});advance();}
        else if(stage==6&&ready()&&ticks>25){interact(client,ARCH);advance();}
        else if(stage==7&&ticks>20){require(client.currentScreen instanceof GuiArchitectTable,"Native architect GUI missing");var name=client.currentScreen.children().stream().filter(TextFieldWidget.class::isInstance).map(TextFieldWidget.class::cast).findFirst().orElseThrow();name.setFocused(true);name.setText("Entities — FelixMantra");quickMove(client,BCBuildersItems.snapshot);advance();}
        else if(stage==8&&ticks>20){work=client.getServer().submit(()->{var t=architect(client);var h=BCBuildersItems.snapshot.getHeader(t.invSnapshotOut.getStackInSlot(0));require(h!=null&&((Blueprint)GlobalSavedDataSnapshots.get(t.getWorld()).getSnapshot(h.key)).entities.size()==4,"Architect omitted native entities");require(player(client).getAdvancementTracker().getProgress(client.getServer().getAdvancementLoader().get(new Identifier("buildcraftbuilders:architect"))).isDone(),"Architect advancement missing");aim(client,SOURCE.add(2,1,1),false);});shot(client,"entities-architect-output.png");client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId,37,0,net.minecraft.screen.slot.SlotActionType.QUICK_MOVE,client.player);client.player.closeHandledScreen();advance();}
        else if(stage==9&&ready()&&ticks>25){shot(client,"entities-source-display.png");advance();}
        else if(stage==10&&ticks>5){disconnect(client,11);}
        else if(stage==12&&ticks>35){work=client.getServer().submit(()->{var w=client.getServer().getOverworld();var used=used(client);require(((Blueprint)GlobalSavedDataSnapshots.get(w).getSnapshot(BCBuildersItems.snapshot.getHeader(used).key)).entities.size()==4,"Stored entity file lost after restart");w.setBlockState(BUILDER.down(),Blocks.STONE.getDefaultState());w.setBlockState(BUILDER,BCBuildersBlocks.builder.getDefaultState().with(BCBuildersBlocks.builder.getFacingProperty(),Direction.EAST));var t=builder(client);t.onPlacedBy(player(client),new ItemStack(BCBuildersBlocks.builder));List<ItemStack> materials=List.of(new ItemStack(Items.STONE,25),new ItemStack(Items.OAK_BOAT),new ItemStack(Items.ARMOR_STAND),new ItemStack(Items.ITEM_FRAME),display(),new ItemStack(Items.GLOW_ITEM_FRAME),new ItemStack(Items.CLOCK),new ItemStack(Items.PAINTING));for(int i=0;i<materials.size();i++)t.invResources.setStackInSlot(i,materials.get(i));aim(client,BUILDER,true);});advance();}
        else if(stage==13&&ready()&&ticks>25){interact(client,BUILDER);advance();}
        else if(stage==14&&ticks>20){require(client.currentScreen instanceof GuiBuilder,"Native entity builder GUI missing");quickMove(client,BCBuildersItems.snapshot);advance();}
        else if(stage==15&&ticks>20){shot(client,"entities-builder-materials.png");work=client.getServer().submit(()->{require(builder(client).getBlueprintBuildingInfo().rotation==BlockRotation.CLOCKWISE_90,"Builder did not rotate plan");charge(builder(client));});client.player.closeHandledScreen();advance();}
        else if(stage==16&&ready()&&ticks>30){work=client.getServer().submit(()->{require(builder(client).getCountToBreak()==0&&builder(client).getCountToPlace()==0,"Native block phase unfinished");charge(builder(client));aim(client,TARGET.add(-1,1,2),true);});advance();}
        else if(stage==17&&ready()&&ticks>25){work=client.getServer().submit(()->{var t=builder(client);require(!t.isFinished()&&t.getPendingEntities()==2&&entities(client).size()==3,"Missing helmet did not leave the whole riding group pending");savedIds=ids(client);});shot(client,"entities-awaiting-exact-helmet.png");advance();}
        else if(stage==18&&ready()&&ticks>5){disconnect(client,19);}
        else if(stage==20&&ticks>35){work=client.getServer().submit(()->{require(ids(client).equals(savedIds)&&builder(client).getPendingEntities()==2,"Partial entity restart duplicated or lost entity");player(client).getInventory().setStack(9,helmet());player(client).currentScreenHandler.sendContentUpdates();aim(client,BUILDER,true);});advance();}
        else if(stage==21&&ready()&&ticks>25){interact(client,BUILDER);advance();}
        else if(stage==22&&ticks>20){require(client.currentScreen instanceof GuiBuilder,"Resumed builder GUI missing");quickMove(client,Items.DIAMOND_HELMET);advance();}
        else if(stage==23&&ticks>25){work=client.getServer().submit(()->{var t=builder(client);require(t.isFinished()&&t.getPendingEntities()==0&&entities(client).size()==5,"Entity reconstruction did not finish");var info=t.getBlueprintBuildingInfo();for(var desired:info.entities)require(entities(client).stream().anyMatch(e->(desired instanceof SchematicEntityGroup group?group.matches(e,info.offsetPos):((SchematicEntityDefault)desired).matches(e,info.offsetPos))),"Native entity NBT/orientation mismatch");for(int i=0;i<27;i++)require(t.invResources.getStackInSlot(i).isEmpty(),"Entity material duplicated/left unspent");require(t.getBattery().getStored()==16_000*MjAPI.MJ,"Entity phase changed original full-battery semantics");savedIds=ids(client);aim(client,TARGET.add(-1,1,2),true);});client.player.closeHandledScreen();advance();}
        else if(stage==24&&ready()&&ticks>25){require(client.world.getEntitiesByClass(Entity.class,area(),EntityBlueprintClientSmoke::displayEntity).size()==5,"Client did not receive reconstructed entities");requireClientGroup(client);shot(client,"entities-reconstructed-rotated.png");advance();}
        else if(stage==25&&ticks>5){disconnect(client,26);}
        else if(stage==27&&ticks>35){work=client.getServer().submit(()->{require(ids(client).equals(savedIds)&&builder(client).isFinished(),"Completed entity restart duplicated entities");aim(client,BUILDER,true);});advance();}
        else if(stage==28&&ready()&&ticks>25){interact(client,BUILDER);advance();}
        else if(stage==29&&ticks>20){require(client.currentScreen instanceof GuiBuilder&&((ContainerBuilder)client.player.currentScreenHandler).tile.getPendingEntities()==0,"Completed entity status did not sync");requireClientGroup(client);shot(client,"entities-final-builder-inventory.png");System.out.println("BUILDCRAFT ENTITY BLUEPRINT CLIENT: native volume/architect GUI capture, named equipment and displayed items, five native entity types including a whole riding group, three complete world restarts, rotated construction, whole-group exact material shortage/resume, vehicle/passenger UUID conservation, final client rendering and GUI; failures=0");stage=40;client.scheduleStop();}
    }
    private static void requireClientGroup(MinecraftClient c){var boats=c.world.getEntitiesByClass(BoatEntity.class,area(),e->true);require(boats.size()==1&&boats.get(0).getPassengerList().size()==1&&boats.get(0).getFirstPassenger() instanceof ArmorStandEntity,"Client riding relationship did not synchronize/reload");}
    private static boolean displayEntity(Entity e){return e instanceof BoatEntity||e instanceof ArmorStandEntity||e instanceof AbstractDecorationEntity;}
    private static Box area(){return new Box(TARGET.add(-2,0,0),TARGET.add(1,3,5));}
    private static List<Entity> entities(MinecraftClient c){return c.getServer().getOverworld().getEntitiesByClass(Entity.class,area(),EntityBlueprintClientSmoke::displayEntity);}
    private static Set<UUID> ids(MinecraftClient c){return entities(c).stream().map(Entity::getUuid).collect(java.util.stream.Collectors.toSet());}
    private static ItemStack helmet(){var s=new ItemStack(Items.DIAMOND_HELMET);s.setCustomName(Text.literal("Remake by FelixMantra"));return s;}
    private static ItemStack display(){var s=new ItemStack(Items.DIAMOND);s.setCustomName(Text.literal("Blueprint original"));return s;}
    private static ItemStack used(MinecraftClient c){var inv=player(c).getInventory();for(int i=0;i<inv.size();i++)if(BCBuildersItems.snapshot.getHeader(inv.getStack(i))!=null)return inv.getStack(i);throw new IllegalStateException("No saved blueprint");}
    private static void charge(TileBuilder t){((IMjReceiver)t.getMjConnector(Direction.UP)).receivePower(16_000*MjAPI.MJ,false);}
    private static TileBuilder builder(MinecraftClient c){return (TileBuilder)c.getServer().getOverworld().getBlockEntity(BUILDER);}
    private static TileArchitectTable architect(MinecraftClient c){return (TileArchitectTable)c.getServer().getOverworld().getBlockEntity(ARCH);}
    private static net.minecraft.server.network.ServerPlayerEntity player(MinecraftClient c){return c.getServer().getPlayerManager().getPlayer(c.player.getUuid());}
    private static void quickMove(MinecraftClient c,Item item){var h=c.player.currentScreenHandler;for(int i=0;i<h.slots.size();i++)if(h.getSlot(i).inventory==c.player.getInventory()&&h.getSlot(i).getStack().isOf(item)){c.interactionManager.clickSlot(h.syncId,i,0,net.minecraft.screen.slot.SlotActionType.QUICK_MOVE,c.player);return;}throw new IllegalStateException("Missing inventory input "+item);}
    private static void interact(MinecraftClient c,BlockPos p){c.interactionManager.interactBlock(c.player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(p),Direction.NORTH,p,false));}
    private static void aim(MinecraftClient c,BlockPos at,boolean rotated){var p=player(c);var eye=Vec3d.ofCenter(at).add(rotated?5:1,2,rotated?-1:-4);var look=Vec3d.ofCenter(at).subtract(eye).normalize();p.teleport(p.getServerWorld(),eye.x,eye.y-p.getEyeHeight(p.getPose()),eye.z,(float)Math.toDegrees(Math.atan2(-look.x,look.z)),(float)-Math.toDegrees(Math.asin(look.y)));p.setHeadYaw(p.getYaw());p.getAbilities().flying=true;p.sendAbilitiesUpdate();}
    private void disconnect(MinecraftClient c,int next){stage=next;ticks=0;c.world.disconnect();c.disconnect(new TitleScreen());}
    private static void shot(MinecraftClient c,String name){net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(c.runDirectory,name,c.getFramebuffer(),m->{});}
    private boolean ready(){if(work==null||!work.isDone()||ticks<5)return false;work.join();return true;}
    private void advance(){stage++;ticks=0;System.out.println("BUILDCRAFT ENTITY BLUEPRINT CLIENT stage="+stage);}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
}
