/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import buildcraft.api.enums.*;
import buildcraft.api.properties.BuildCraftProperties;
import buildcraft.api.mj.*;
import buildcraft.core.BCCoreBlocks;
import buildcraft.silicon.*;
import buildcraft.silicon.container.ContainerAssemblyTable;
import buildcraft.silicon.gui.GuiAssemblyTable;
import buildcraft.silicon.item.ItemPluggableGate;
import buildcraft.silicon.gate.*;
import buildcraft.silicon.tile.*;
import buildcraft.transport.*;
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
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.world.gen.*;
import net.minecraft.world.level.LevelInfo;

/** Real GUI selection, partial-work world restart and engine/pipe/laser production. */
public final class AssemblyClientSmoke implements ClientModInitializer {
    private static final BlockPos POS=new BlockPos(2,-60,2);
    private static final Direction[] FACES={Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST};
    private int stage,ticks;private long deadline;private String saveName;
    private CompletableFuture<Void> work;
    @Override public void onInitializeClient(){if(Boolean.getBoolean("buildcraft.assemblyClientSmoke"))ClientTickEvents.END_CLIENT_TICK.register(this::tick);}
    private void tick(MinecraftClient client) {
        if(stage==0) {
            if(client.getOverlay()!=null || !(client.currentScreen instanceof TitleScreen))return;
            client.options.pauseOnLostFocus=false;client.options.getViewDistance().setValue(6);client.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
            deadline=System.nanoTime()+600_000_000_000L;saveName="assembly-smoke-"+System.currentTimeMillis();stage=1;
            var rules=new GameRules();rules.get(GameRules.DO_MOB_SPAWNING).set(false,null);
            client.createIntegratedServerLoader().createAndStart(saveName,new LevelInfo("BuildCraft laser assembly — Remake by FelixMantra",GameMode.CREATIVE,false,Difficulty.PEACEFUL,true,rules,DataConfiguration.SAFE_MODE),
                new GeneratorOptions(4754L,false,false),registries->registries.get(RegistryKeys.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createDimensionsRegistryHolder());return;
        }
        if(stage==20)return;
        if(System.nanoTime()>deadline)throw new IllegalStateException("Assembly client timed out at stage "+stage);
        if(stage==7) {if(client.getServer()!=null || client.world!=null)return;stage=8;ticks=0;client.createIntegratedServerLoader().start(new TitleScreen(),saveName);return;}
        if(client.world==null || client.player==null || client.getServer()==null)return;
        ticks++;if(ticks%20==0)client.getToastManager().clear();
        if(stage==1) {
            work=client.getServer().submit(()->{
                var world=client.getServer().getOverworld();var player=world.getServer().getPlayerManager().getPlayer(client.player.getUuid());world.setTimeOfDay(1000);player.teleport(world,2.5,-60,-.5,0,15);
                world.setBlockState(POS.down(),Blocks.STONE.getDefaultState());world.setBlockState(POS,Blocks.AIR.getDefaultState());player.setStackInHand(Hand.MAIN_HAND,new ItemStack(BCSiliconBlocks.assemblyTable));
                require(player.interactionManager.interactBlock(player,world,player.getMainHandStack(),Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(POS.down()).add(0,.5,0),Direction.UP,POS.down(),false)).isAccepted(),"Native table placement failed");
                world.setBlockState(POS.down(),Blocks.BARREL.getDefaultState());player.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);player.changeGameMode(GameMode.SURVIVAL);
                player.getInventory().setStack(9,new ItemStack(Items.REDSTONE));player.getInventory().setStack(10,new ItemStack(Items.BLUE_DYE));player.getInventory().setStack(11,EnumRedstoneChipset.IRON.getStack());player.currentScreenHandler.sendContentUpdates();
            });advance();
        } else if(stage==2 && ready()){open(client);advance();}
        else if(stage==3 && ticks>20 && client.currentScreen instanceof GuiAssemblyTable) {
            var menu=menu(client);require(menu.slots.size()==48,"Preview slots can be stolen");
            for(int slot=12;slot<=14;slot++)client.interactionManager.clickSlot(menu.syncId,slot,0,SlotActionType.QUICK_MOVE,client.player);
            var sprites=new HashSet<Identifier>();
            for(var type:EnumRedstoneChipset.values()) {
                var stack=type.getStack();var model=client.getItemRenderer().getModel(stack,client.world,client.player,0);var sprite=model.getParticleSprite().getContents().getId();
                require(model!=client.getBakedModelManager().getMissingModel() && sprite.getPath().equals("items/redstone_chipset/"+type.name().toLowerCase()),"Missing or wrong chipset model: "+type+" "+sprite);sprites.add(sprite);
                require(!stack.getName().getString().contains("item."),"Missing chipset translation");
                require(stack.getTooltip(client.player,net.minecraft.client.item.TooltipContext.Default.BASIC).stream().anyMatch(t->t.getString().contains("Remake by FelixMantra")),"Missing chipset credit");
            }
            require(sprites.size()==5,"Chipset variants share wrong texture");advance();
        } else if(stage==4 && ticks>25){choose(client,"red_chipset");advance();}
        else if(stage==5 && ticks>25) {
            require(menu(client).tile.recipesStates.get(id("red_chipset"))==EnumAssemblyRecipeState.SAVED_ENOUGH_ACTIVE && menu(client).getCursorStack().isEmpty(),"Native choice did not sync or generated free output");
            work=client.getServer().submit(()->require(tile(client).receiveLaserPower(2500*MjAPI.MJ)==0,"Table rejected partial work"));advance();
        } else if(stage==6 && ready() && ticks>30) {
            require(menu(client).getProgress()==2500 && menu(client).tile.power==2500*MjAPI.MJ,"Native progress did not sync");screenshot(client,"assembly-partial-work.png");
            stage=7;ticks=0;client.player.closeHandledScreen();client.world.disconnect();client.disconnect(new TitleScreen());
        } else if(stage==8 && ticks>25) {
            work=client.getServer().submit(()->{
                var world=client.getServer().getOverworld();var table=tile(client);require(table.power==2500*MjAPI.MJ && table.isSelected(id("red_chipset")) && table.inv.getStackInSlot(0).getCount()==1,"World restart lost work, selection or input");
                for(var face:FACES) {
                    var at=POS.offset(face,3);world.setBlockState(at,BCSiliconBlocks.laser.getDefaultState().with(BuildCraftProperties.BLOCK_FACING_6,face.getOpposite()));
                    for(int i=4;i<=5;i++){var pp=POS.offset(face,i);world.setBlockState(pp,BCTransportBlocks.pipeHolder.getDefaultState());((TilePipeHolder)world.getBlockEntity(pp)).onPlacedBy(null,new ItemStack(i==5?BCTransportItems.pipePowerWood:BCTransportItems.pipePowerStone));}
                    var ep=POS.offset(face,6);world.setBlockState(ep.down(),Blocks.REDSTONE_BLOCK.getDefaultState());world.setBlockState(ep,BCCoreBlocks.engine.getDefaultState().with(BuildCraftProperties.ENGINE_TYPE,EnumEngineType.CREATIVE));((buildcraft.core.tile.TileEngineCreative)world.getBlockEntity(ep)).currentOutputIndex=4;
                }
                var player=world.getServer().getPlayerManager().getPlayer(client.player.getUuid());player.changeGameMode(GameMode.CREATIVE);player.getAbilities().flying=true;player.sendAbilitiesUpdate();player.teleport(world,-3,-56,-5,320,26);
            });advance();
        } else if(stage==9 && ready() && ticks>220) {
            for(var face:FACES) {var laser=(TileLaser)client.world.getBlockEntity(POS.offset(face,3));require(laser!=null && laser.getLastTransfer()>0 && laser.laserPos!=null,"Native beam missing for "+face);}
            screenshot(client,"assembly-laser-production.png");advance();
        } else if(stage==10 && ticks>850) {
            work=client.getServer().submit(()->{
                var world=client.getServer().getOverworld();require(count(client,BCSiliconItems.redstoneChipset)==1 && tile(client).power==0,"Live engine/pipe/laser chain failed to craft exactly one red chipset");
                var player=world.getServer().getPlayerManager().getPlayer(client.player.getUuid());player.teleport(world,2.5,-60,-.5,0,15);player.getAbilities().flying=false;player.changeGameMode(GameMode.SURVIVAL);player.sendAbilitiesUpdate();player.getInventory().setStack(9,new ItemStack(Items.REDSTONE));player.currentScreenHandler.sendContentUpdates();
            });advance();
        } else if(stage==11 && ready()){open(client);advance();}
        else if(stage==12 && ticks>25 && client.currentScreen instanceof GuiAssemblyTable) {
            var menu=menu(client);client.interactionManager.clickSlot(menu.syncId,12,0,SlotActionType.QUICK_MOVE,client.player);advance();
        } else if(stage==13 && ticks>20) {
            choose(client,"red_chipset");choose(client,"wire_blue");choose(client,"gate_iron_and");advance();
        } else if(stage==14 && ticks>25) {
            var choices=menu(client).tile.recipesStates;require(choices.get(id("red_chipset"))==EnumAssemblyRecipeState.POSSIBLE && choices.get(id("wire_blue"))!=EnumAssemblyRecipeState.POSSIBLE && choices.get(id("gate_iron_and"))!=EnumAssemblyRecipeState.POSSIBLE,"Multiple native selections did not sync");
            screenshot(client,"assembly-selected-recipes.png");client.player.closeHandledScreen();advance();
        } else if(stage==15 && ticks>2300) {
            work=client.getServer().submit(()->{
                require(count(client,BCSiliconItems.redstoneChipset)==1 && count(client,BCTransportItems.wire)==8 && count(client,BCSiliconItems.plugGate)==1,"Native queued assembly did not produce one chipset/eight blue wires/one iron gate");
                var world=client.getServer().getOverworld();var barrel=(BarrelBlockEntity)world.getBlockEntity(POS.down());
                for(int i=0;i<barrel.size();i++){var stack=barrel.getStack(i);if(stack.isOf(BCTransportItems.wire))require(stack.getNbt().getInt("wire_colour")==DyeColor.BLUE.getId(),"Wire colour lost");if(stack.isOf(BCSiliconItems.plugGate)){var variant=ItemPluggableGate.getVariant(stack);require(variant.logic==EnumGateLogic.AND && variant.material==EnumGateMaterial.IRON,"Gate variant lost");}}
                require(tile(client).inv.stacks.stream().allMatch(ItemStack::isEmpty) && tile(client).power==0,"Assembly left or duplicated ingredients/energy");
                var player=world.getServer().getPlayerManager().getPlayer(client.player.getUuid());var adv=world.getServer().getAdvancementLoader().get(new Identifier("buildcraftsilicon:laser_power"));require(player.getAdvancementTracker().getProgress(adv).isDone(),"Native laser advancement missing");
            });advance();
        } else if(stage==16 && ready() && ticks>30) {
            for(var face:FACES){var laser=(TileLaser)client.world.getBlockEntity(POS.offset(face,3));require(laser.getLastTransfer()==0 && laser.laserPos==null,"Completed table retained ghost beam");}
            open(client);advance();
        } else if(stage==17 && ticks>25 && client.currentScreen instanceof GuiAssemblyTable) {
            require(menu(client).tile.recipesStates.get(id("wire_blue"))==EnumAssemblyRecipeState.SAVED,"Missing-input saved recipe vanished");screenshot(client,"assembly-finished-queue.png");
            client.player.closeHandledScreen();work=client.getServer().submit(()->{
                var world=client.getServer().getOverworld();var at=POS.north(3);var stale=(IMjReceiver)MjAPI.CONNECTOR.find(world,at,Direction.UP);world.breakBlock(at,true);require(stale.receivePower(MjAPI.MJ,false)==MjAPI.MJ,"Removed laser accepted energy");
            });advance();
        } else if(stage==18 && ready()) {
            client.interactionManager.interactBlock(client.player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(POS.down()),Direction.NORTH,POS.down(),false));advance();
        } else if(stage==19 && ticks>25) {
            require(client.currentScreen instanceof net.minecraft.client.gui.screen.ingame.GenericContainerScreen,"Native output barrel did not open");screenshot(client,"assembly-products.png");
            System.out.println("BUILDCRAFT ASSEMBLY CLIENT: native placement/shift-click/recipe toggles, five chipset models, 2500 MJ world restart, four engine/wood/stone/laser chains, exact red chipset + eight blue wires + iron AND gate, beam stop, advancement and stale connector; failures=0");stage=20;client.scheduleStop();
        }
    }
    private static Identifier id(String name){return new Identifier("buildcraftsilicon","assembly/"+name);}
    private static TileAssemblyTable tile(MinecraftClient client){return (TileAssemblyTable)client.getServer().getOverworld().getBlockEntity(POS);}
    private static ContainerAssemblyTable menu(MinecraftClient client){return (ContainerAssemblyTable)client.player.currentScreenHandler;}
    private static int count(MinecraftClient client,Item item){var barrel=(BarrelBlockEntity)client.getServer().getOverworld().getBlockEntity(POS.down());int n=0;for(int i=0;i<barrel.size();i++)if(barrel.getStack(i).isOf(item))n+=barrel.getStack(i).getCount();return n;}
    private static void choose(MinecraftClient client,String name) {
        var menu=menu(client);var choices=new ArrayList<>(menu.tile.recipesStates.keySet());int index=choices.indexOf(id(name));require(index>=0 && index<12,"Recipe not available on first page: "+name);
        var screen=(GuiAssemblyTable)client.currentScreen;screen.mouseClicked((screen.width-176)/2+116+index%3*18+8,(screen.height-207)/2+36+index/3*18+8,0);
    }
    private static void open(MinecraftClient client){client.interactionManager.interactBlock(client.player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(POS).add(0,-.2,0),Direction.NORTH,POS,false));}
    private static void screenshot(MinecraftClient client,String name){net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(client.runDirectory,name,client.getFramebuffer(),message->{});}
    private void advance(){stage++;ticks=0;System.out.println("BUILDCRAFT ASSEMBLY CLIENT stage="+stage);}
    private boolean ready(){if(work==null || !work.isDone() || ticks<15)return false;work.join();return true;}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
