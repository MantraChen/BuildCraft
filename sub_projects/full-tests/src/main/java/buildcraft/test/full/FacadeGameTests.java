/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;

import java.util.*;
import buildcraft.api.facades.*;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.transport.EnumWirePart;
import buildcraft.core.BCCoreStatements;
import buildcraft.lib.statement.*;
import buildcraft.silicon.*;
import buildcraft.silicon.gate.*;
import buildcraft.silicon.item.ItemPluggableFacade;
import buildcraft.silicon.plug.*;
import buildcraft.silicon.recipe.*;
import buildcraft.silicon.tile.TileAssemblyTable;
import buildcraft.transport.*;
import buildcraft.transport.tile.TilePipeHolder;
import buildcraft.transport.wire.WorldSavedDataWireSystems;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.item.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.*;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.recipe.*;
import net.minecraft.test.*;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;

public final class FacadeGameTests implements FabricGameTest {
    private static final BlockPos POS=new BlockPos(3,3,3);
    private static FacadeBlockStateInfo info(BlockState state){return FacadeStateManager.INSTANCE.find(state);}
    private static FacadeInstance facade(BlockState state,boolean hollow){return FacadeInstance.createSingle(info(state),hollow);}
    private static TilePipeHolder pipe(TestContext c,BlockPos pos,Item item){
        c.setBlockState(pos,BCTransportBlocks.pipeHolder);var tile=(TilePipeHolder)c.getBlockEntity(pos);tile.onPlacedBy(null,new ItemStack(item));return tile;
    }
    private static TilePipeHolder pipe(TestContext c){return pipe(c,POS,BCTransportItems.pipeItemStone);}
    private static PluggableFacade attach(TilePipeHolder tile,Direction face,FacadeInstance data){
        var plug=new PluggableFacade(BCSiliconPlugs.facade,tile,face,data);tile.replacePluggable(face,plug);tile.tick();return plug;
    }
    private static FacadeInstance phases(boolean hollow){
        return new FacadeInstance(new FacadePhasedState[]{info(Blocks.STONE.getDefaultState()).createPhased(null),
            info(Blocks.OAK_LOG.getDefaultState().with(PillarBlock.AXIS,Direction.Axis.Z)).createPhased(DyeColor.RED),
            info(Blocks.GLASS.getDefaultState()).createPhased(DyeColor.BLUE)},hollow);
    }
    private static PacketByteBuf buffer(){return new PacketByteBuf(io.netty.buffer.Unpooled.buffer());}
    private static void same(TestContext c,FacadeInstance a,FacadeInstance b,String message){c.assertTrue(a.writeToNbt().equals(b.writeToNbt()),message);}
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativeCompatibilityHooksPreserveOwnedStacksAndRevision(TestContext c){
        // A registered, already ineligible block avoids changing the discovered facade catalog.
        var block=Blocks.TORCH;var state=block.getDefaultState();
        int before=FacadeAPI.configurationRevision();FacadeAPI.disableBlock(block);
        c.assertTrue(FacadeAPI.isDisabled(block) && FacadeAPI.configurationRevision()>before,"Native facade-disable hook ignored");
        int disabled=FacadeAPI.configurationRevision();FacadeAPI.disableBlock(block);c.assertTrue(disabled==FacadeAPI.configurationRevision(),"Repeated disable invalidated registry");
        var stack=new ItemStack(Items.DIAMOND,12);stack.getOrCreateNbt().putString("mapping","facade-test");FacadeAPI.mapStateToStack(state,stack);stack.setCount(1);stack.getNbt().putString("mapping","mutated");
        var mapped=FacadeAPI.getMappedStack(state);c.assertTrue(mapped.getCount()==1 && mapped.getNbt().getString("mapping").equals("facade-test"),"Caller mutated native facade mapping");
        mapped.getNbt().putString("mapping","returned-copy");c.assertTrue(FacadeAPI.getMappedStack(state).getNbt().getString("mapping").equals("facade-test"),"Mapping getter leaked owned NBT");
        FacadeAPI.mapStateToStack(state,ItemStack.EMPTY);c.assertTrue(FacadeAPI.getMappedStack(state).isEmpty(),"Empty mapping not retained");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void everyDiscoveredStateAndGeneratedRecipeRoundTrips(TestContext c){
        var states=FacadeStateManager.INSTANCE.getValidFacades();c.assertTrue(states.size()>500,"Facade discovery omitted native full cubes");
        var ids=new HashSet<Identifier>();int generated=0,totalBytes=0;
        for(var state:states)for(boolean hollow:new boolean[]{false,true}){
            var original=FacadeInstance.createSingle(state,hollow);
            same(c,original,FacadeInstance.readFromNbt(original.writeToNbt()),"Block properties lost in facade NBT: "+state.state);
            var packet=buffer();try{original.writeToBuffer(packet);same(c,original,FacadeInstance.readFromBuffer(packet),"Facade wire state changed");c.assertTrue(!packet.isReadable(),"Trailing facade bytes");}finally{packet.release();}
            if(!state.isVisible)continue;
            var id=FacadeAssemblyRecipes.id(state,hollow);c.assertTrue(ids.add(id),"Facade recipe id collided");generated++;
            var recipe=(NativeAssemblyRecipe)c.getWorld().getRecipeManager().get(id).orElseThrow();
            c.assertTrue(recipe.cost()==64*MjAPI.MJ && recipe.output().getCount()==6,"Facade cost/count changed");
            same(c,original,ItemPluggableFacade.getStates(recipe.output()),"Wrong generated facade output");
            packet=buffer();try{
                BCSiliconRecipes.ASSEMBLY_SERIALIZER.write(packet,recipe);totalBytes+=packet.readableBytes();
                var copy=BCSiliconRecipes.ASSEMBLY_SERIALIZER.read(id,packet);
                c.assertTrue(ItemStack.areEqual(copy.output(),recipe.output()) && copy.cost()==recipe.cost(),"Facade recipe network changed output");
                c.assertTrue(copy.allocate(List.of(state.getRequiredStack(),new ItemStack(BCTransportItems.pipeStructure,3)))!=null,"Facade recipe rejected its inputs");
            }finally{packet.release();}
        }
        c.assertTrue(c.getWorld().getRecipeManager().listAllOfType(BCSiliconRecipes.ASSEMBLY).size()==82+generated,"Generated recipes missing or duplicated");
        System.out.println("BUILDCRAFT FACADES: states="+states.size()+", recipes="+generated+", recipe payload bytes="+totalBytes);c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void registryRejectsLiquidsEntitiesAndPartialShapes(TestContext c){
        for(var block:List.of(Blocks.AIR,Blocks.WATER,Blocks.LAVA,Blocks.CHEST,Blocks.FURNACE,Blocks.OAK_SLAB,Blocks.OAK_STAIRS,Blocks.TORCH,Blocks.BARRIER))
            c.assertTrue(info(block.getDefaultState())==null && BCSiliconItems.plugFacade.getFacadeForBlock(block.getDefaultState()).isEmpty(),"Invalid facade "+block);
        for(var block:List.of(Blocks.STONE,Blocks.GLASS,Blocks.RED_STAINED_GLASS,Blocks.OAK_LOG,Blocks.GRASS_BLOCK))
            c.assertTrue(info(block.getDefaultState())!=null,"Valid facade missing "+block);
        var log=info(Blocks.OAK_LOG.getDefaultState());var required=log.getRequiredStack();required.setCount(32);
        c.assertTrue(log.getRequiredStack().getCount()==1 && log.varyingProperties.contains(PillarBlock.AXIS),"Mutable ingredient or missing state tooltip");
        c.assertTrue(FacadeAPI.facadeItem==BCSiliconItems.plugFacade && FacadeAPI.registry==FacadeStateManager.INSTANCE,"Facade public API not installed");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void legacyWrapperAndPhaseColourMigrationAreLossless(TestContext c){
        var original=phases(true);var legacy=original.writeToNbt();legacy.remove("isHollow");legacy.getList("states",NbtElement.COMPOUND_TYPE).getCompound(0).putBoolean("isHollow",true);
        same(c,original,FacadeInstance.fromContainer(legacy),"Legacy facade wrapper lost hollow or phases");
        var silver=info(Blocks.GLASS.getDefaultState()).createPhased(DyeColor.LIGHT_GRAY).writeToNbt();silver.putString("activeColour","SILVER");
        c.assertTrue(FacadePhasedState.readFromNbt(silver).activeColour==DyeColor.LIGHT_GRAY,"Legacy SILVER colour lost");
        var unknown=new NbtCompound();unknown.put("states",new NbtList());c.assertTrue(FacadeInstance.readFromNbt(unknown).size()==1,"Empty saved facade not recovered");
        var duplicate=original.writeToNbt();var entries=duplicate.getList("states",NbtElement.COMPOUND_TYPE);entries.getCompound(2).putString("activeColour","RED");
        c.assertTrue(FacadeInstance.readFromNbt(duplicate).size()==1,"Duplicate saved phases not bounded");
        var array=original.getPhasedStates();array[0]=array[1];c.assertTrue(original.state(0).activeColour==null,"Caller mutated phase array");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void malformedPacketsRejectCountsColoursStatesAndActiveIndex(TestContext c){
        for(int raw:new int[]{-1,Integer.MAX_VALUE}){
            var packet=buffer();packet.writeVarInt(raw);packet.writeByte(0);boolean rejected=false;
            try{FacadePhasedState.readFromBuffer(packet);}catch(IllegalArgumentException expected){rejected=true;}finally{packet.release();}
            c.assertTrue(rejected,"Out-of-range block-state id accepted");
        }
        var retired=buffer();retired.writeVarInt(Block.getRawIdFromState(Blocks.TORCH.getDefaultState()));retired.writeByte(0);
        try{c.assertTrue(FacadePhasedState.readFromBuffer(retired).stateInfo.state.isOf(Blocks.STONE),"Disabled/ineligible saved state did not recover");}finally{retired.release();}
        for(int count:new int[]{0,18,255}){
            var packet=buffer();packet.writeBoolean(false);packet.writeByte(count);boolean rejected=false;
            try{FacadeInstance.readFromBuffer(packet);}catch(IllegalArgumentException expected){rejected=true;}finally{packet.release();}
            c.assertTrue(rejected,"Invalid phase count accepted");
        }
        for(int colour:new int[]{17,255}){
            var packet=buffer();packet.writeBoolean(false);packet.writeByte(1);packet.writeVarInt(Block.getRawIdFromState(Blocks.STONE.getDefaultState()));packet.writeByte(colour);boolean rejected=false;
            try{FacadeInstance.readFromBuffer(packet);}catch(IllegalArgumentException expected){rejected=true;}finally{packet.release();}
            c.assertTrue(rejected,"Invalid phase colour accepted");
        }
        var tile=pipe(c);var plug=attach(tile,Direction.NORTH,phases(false));
        var packet=buffer();packet.writeByte(3);boolean rejected=false;
        try{plug.readPayload(packet,EnvType.CLIENT,null);}catch(IllegalArgumentException expected){rejected=true;}finally{packet.release();}
        c.assertTrue(rejected && plug.activeIndex()==0,"Invalid active phase mutated attachment");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void originalAssemblyInputsCraftSolidAndHollowFacadesWithoutLoss(TestContext c){
        c.setBlockState(POS,BCSiliconBlocks.assemblyTable);var table=(TileAssemblyTable)c.getBlockEntity(POS);table.onLoad();
        table.onPlacedBy(c.createMockCreativeServerPlayerInWorld(),new ItemStack(BCSiliconBlocks.assemblyTable));
        c.setBlockState(POS.down(),Blocks.BARREL);var output=(BarrelBlockEntity)c.getBlockEntity(POS.down());
        for(var state:List.of(Blocks.STONE.getDefaultState(),Blocks.GLASS.getDefaultState(),Blocks.GRASS_BLOCK.getDefaultState(),
            Blocks.OAK_LOG.getDefaultState().with(PillarBlock.AXIS,Direction.Axis.X)))for(boolean hollow:new boolean[]{false,true}){
            for(var id:List.copyOf(table.getChoices().keySet()))table.selectRecipe(id,false);
            output.clear();table.inv.setStackInSlot(0,info(state).getRequiredStack());table.inv.setStackInSlot(1,new ItemStack(BCTransportItems.pipeStructure,3));
            c.assertTrue(table.selectRecipe(FacadeAssemblyRecipes.id(info(state),hollow),true),"Facade unavailable in assembly table");
            c.assertTrue(table.receiveLaserPower(64*MjAPI.MJ+1)==1,"Facade overcharged MJ");table.tick();
            var stack=output.getStack(0);c.assertTrue(stack.getCount()==6 && table.power==0 && table.inv.stacks.stream().allMatch(ItemStack::isEmpty),"Facade duplicated/lost inputs or output");
            same(c,facade(state,hollow),ItemPluggableFacade.getStates(stack),"Assembly lost facade properties");
        }c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void explicitDataPackRecipeWinsGeneratedDefault(TestContext c){
        var id=FacadeAssemblyRecipes.id(info(Blocks.STONE.getDefaultState()),false);var custom=new com.google.gson.JsonObject();custom.addProperty("sentinel","explicit replacement");
        var input=new HashMap<Identifier,com.google.gson.JsonElement>();input.put(id,custom);
        var output=FacadeAssemblyRecipes.addDefaults(input);c.assertTrue(output.get(id)==custom && input.size()==1,"Default overwrote data-pack recipe or mutated input");
        c.assertTrue(FacadeAssemblyRecipes.id(info(Blocks.OAK_LOG.getDefaultState()),false).equals(FacadeAssemblyRecipes.id(info(Blocks.OAK_LOG.getDefaultState()),false)),"Unstable facade id");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void swapRecipePreservesPhasePropertiesCustomNameAndOneOutput(TestContext c){
        var handler=new net.minecraft.screen.CraftingScreenHandler(1,c.createMockCreativeServerPlayerInWorld().getInventory());
        var input=new net.minecraft.inventory.CraftingInventory(handler,3,3);
        var stack=BCSiliconItems.plugFacade.createFacadeStack(phases(false));stack.setCount(12);stack.setCustomName(net.minecraft.text.Text.literal("Facade test"));
        input.setStack(4,stack);
        var recipe=c.getWorld().getRecipeManager().get(new Identifier("buildcraftsilicon","facade_swap")).orElseThrow();
        var swap=(FacadeSwapRecipe)recipe;c.assertTrue(swap.matches(input,c.getWorld()),"Facade swap did not match");
        var result=swap.craft(input,c.getWorld().getRegistryManager());
        c.assertTrue(result.getCount()==1 && result.getName().getString().equals("Facade test") && stack.getCount()==12,"Swap lost custom NBT or altered input");
        same(c,phases(true),ItemPluggableFacade.getStates(result),"Swap changed phase properties");
        input.setStack(0,new ItemStack(Items.STICK));c.assertTrue(!swap.matches(input,c.getWorld()),"Extra swap ingredient accepted");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativePlacementSupportsEveryPipeFlowAndSixFaces(TestContext c){
        var player=c.createMockCreativeServerPlayerInWorld();player.getAbilities().creativeMode=false;
        for(var item:List.of(BCTransportItems.pipeItemStone,BCTransportItems.pipeFluidStone,BCTransportItems.pipePowerStone,BCTransportItems.pipeRfStone,BCTransportItems.pipeStructure)){
            var tile=pipe(c,POS,item);
            for(var face:Direction.values())for(boolean hollow:new boolean[]{false,true}){
                var eye=Vec3d.ofCenter(tile.getPos()).add(Vec3d.of(face.getVector()).multiply(3));var look=Vec3d.of(face.getVector()).multiply(-1);
                player.refreshPositionAndAngles(eye.x,eye.y-player.getEyeHeight(player.getPose()),eye.z,(float)Math.toDegrees(Math.atan2(-look.x,look.z)),(float)-Math.toDegrees(Math.asin(look.y)));player.setHeadYaw(player.getYaw());
                var stack=BCSiliconItems.plugFacade.createFacadeStack(phases(hollow));stack.setCount(2);player.setStackInHand(Hand.MAIN_HAND,stack);
                var result=BCTransportBlocks.pipeHolder.onUse(tile.getCachedState(),c.getWorld(),tile.getPos(),player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(tile.getPos()),face,tile.getPos(),false));
                var plug=(PluggableFacade)tile.getPluggable(face);
                c.assertTrue(result.isAccepted() && stack.getCount()==1 && plug!=null && plug.isBlocking()!=hollow,"Facade placement failed "+face+" "+item);
                same(c,phases(hollow),ItemPluggableFacade.getStates(plug.getPickStack()),"Facade pick lost phases");
                c.assertTrue(BCSiliconItems.plugFacade.onPlace(stack,tile,face,player,Hand.MAIN_HAND)==null,"Occupied facade overwritten");tile.replacePluggable(face,null);
            }
        }c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void solidFacadeStopsRealItemInsertionAndHollowRestoresIt(TestContext c){
        var tile=pipe(c);var state=Blocks.STONE.getDefaultState();
        for(var face:Direction.values())c.setBlockState(POS.offset(face),Blocks.BARREL);
        for(var face:Direction.values()){
            attach(tile,face,facade(state,false));var storage=ItemStorage.SIDED.find(c.getWorld(),tile.getPos(),face);
            try(var tx=Transaction.openOuter()){c.assertTrue(storage==null || storage.insert(ItemVariant.of(Items.DIAMOND),1,tx)==0,"Solid facade accepted item");}
            attach(tile,face,facade(state,true));storage=ItemStorage.SIDED.find(c.getWorld(),tile.getPos(),face);
            try(var tx=Transaction.openOuter()){c.assertTrue(storage!=null && storage.insert(ItemVariant.of(Items.DIAMOND),1,tx)==1,"Hollow facade blocked item");}
            tile.replacePluggable(face,null);
        }c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativePartialBreakingKeepsPipeAndDropsExactFacade(TestContext c){
        var tile=pipe(c);var player=c.createMockCreativeServerPlayerInWorld();player.getAbilities().creativeMode=false;
        for(var face:Direction.values()){
            var data=phases(face.ordinal()%2==0);attach(tile,face,data);
            var look=Vec3d.of(face.getVector()).multiply(-1);var eye=Vec3d.ofCenter(tile.getPos()).subtract(look.multiply(3));
            player.refreshPositionAndAngles(eye.x,eye.y-player.getEyeHeight(player.getPose()),eye.z,(float)Math.toDegrees(Math.atan2(-look.x,look.z)),(float)-Math.toDegrees(Math.asin(look.y)));player.setHeadYaw(player.getYaw());
            player.interactionManager.tryBreakBlock(tile.getPos());
            c.assertTrue(c.getWorld().getBlockEntity(tile.getPos())==tile && tile.getPluggable(face)==null,"Facade break removed whole pipe "+face);
            var drops=c.getWorld().getEntitiesByClass(net.minecraft.entity.ItemEntity.class,new Box(tile.getPos()).expand(1),e->e.isAlive() && e.getStack().isOf(BCSiliconItems.plugFacade));
            c.assertTrue(drops.size()==1 && drops.get(0).getStack().getCount()==1,"Facade drop missing or duplicated");same(c,data,ItemPluggableFacade.getStates(drops.get(0).getStack()),"Drop lost facade data");drops.forEach(net.minecraft.entity.Entity::discard);
        }c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void phasesFollowLiveWireSignalAndSaveTheirActiveState(TestContext c){
        var tile=pipe(c);var plug=attach(tile,Direction.NORTH,phases(false));
        tile.wireManager.addPart(EnumWirePart.WEST_UP_NORTH,DyeColor.RED);
        var gate=new PluggableGate(BCSiliconPlugs.gate,tile,Direction.UP,new GateVariant(EnumGateLogic.OR,EnumGateMaterial.IRON,EnumGateModifier.NO_MODIFIER));tile.replacePluggable(Direction.UP,gate);
        gate.logic.statements[0].trigger.set(new TriggerWrapper.TriggerWrapperInternal(BCCoreStatements.TRIGGER_TRUE));
        gate.logic.statements[0].action.set(new ActionWrapper.ActionWrapperInternal(BCTransportStatements.ACTION_PIPE_SIGNAL[DyeColor.RED.ordinal()]));
        for(int i=0;i<3;i++){tile.tick();WorldSavedDataWireSystems.get(c.getWorld()).tick();}plug.onTick();
        c.assertTrue(plug.activeIndex()==1,"Live red wire did not select facade phase");
        var packet=buffer();try{plug.writeCreationPayload(packet);var client=new PluggableFacade(BCSiliconPlugs.facade,tile,Direction.NORTH,packet);c.assertTrue(client.activeIndex()==1,"Creation packet lost active phase");}finally{packet.release();}
        var restored=new PluggableFacade(BCSiliconPlugs.facade,tile,Direction.NORTH,plug.writeToNbt());c.assertTrue(restored.activeIndex()==1,"NBT lost active phase");
        tile.replacePluggable(Direction.UP,null);for(int i=0;i<3;i++){tile.tick();WorldSavedDataWireSystems.get(c.getWorld()).tick();}plug.onTick();
        c.assertTrue(plug.activeIndex()==0,"Removed emitter left stale facade phase");
        packet=buffer();try{plug.writePayload(packet,EnvType.SERVER);restored.readPayload(packet,EnvType.CLIENT,null);c.assertTrue(restored.activeIndex()==0,"Phase update did not synchronize");}finally{packet.release();}
        same(c,phases(false),ItemPluggableFacade.getStates(plug.getPickStack()),"Wire changed stored phases");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void phasePriorityAndDefaultAreDeterministic(TestContext c){
        var data=phases(false);
        c.assertTrue(data.activeIndex(colour->true)==1 && data.activeIndex(colour->colour==DyeColor.BLUE)==2 && data.activeIndex(colour->false)==0,"Phase priority/default inconsistent");
        var all=new FacadePhasedState[17];all[0]=info(Blocks.STONE.getDefaultState()).createPhased(null);
        for(var colour:DyeColor.values())all[colour.getId()+1]=info(Blocks.GLASS.getDefaultState()).createPhased(colour);
        var maximum=new FacadeInstance(all,true);same(c,maximum,FacadeInstance.readFromNbt(maximum.writeToNbt()),"17-phase NBT lost entries");
        c.assertTrue(!maximum.canAddColour(DyeColor.RED) && maximum.withState(all[0])==null,"Duplicate/max phase accepted");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void dynamicSupportShapeFollowsSolidAndHollowFacade(TestContext c){
        var tile=pipe(c);var state=tile.getCachedState();
        for(var face:Direction.values()){
            c.assertTrue(!state.isSideSolidFullSquare(c.getWorld(),tile.getPos(),face),"Bare pipe cached a full support face");
            attach(tile,face,facade(Blocks.STONE.getDefaultState(),false));
            c.assertTrue(state.isSideSolidFullSquare(c.getWorld(),tile.getPos(),face),"Solid facade cannot support neighbours");
            attach(tile,face,facade(Blocks.STONE.getDefaultState(),true));
            c.assertTrue(!state.isSideSolidFullSquare(c.getWorld(),tile.getPos(),face),"Hollow facade has solid support");tile.replacePluggable(face,null);
        }c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void facadeBlastResistanceReachesNativeExplosionCalculation(TestContext c){
        var tile=pipe(c);attach(tile,Direction.UP,facade(Blocks.OBSIDIAN.getDefaultState(),false));
        var behavior=new net.minecraft.world.explosion.ExplosionBehavior();
        c.assertTrue(behavior.getBlastResistance(null,c.getWorld(),tile.getPos(),tile.getCachedState(),tile.getCachedState().getFluidState()).orElseThrow()==Blocks.OBSIDIAN.getBlastResistance(),"Explosion ignored obsidian facade");
        tile.replacePluggable(Direction.UP,null);
        c.assertTrue(behavior.getBlastResistance(null,c.getWorld(),tile.getPos(),tile.getCachedState(),tile.getCachedState().getFluidState()).orElseThrow()<Blocks.OBSIDIAN.getBlastResistance(),"Removed facade left blast resistance");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void manyStateChoicesSurviveNetworkAndInventoryCacheInvalidation(TestContext c) throws java.io.IOException {
        c.setBlockState(POS,BCSiliconBlocks.assemblyTable);var table=(TileAssemblyTable)c.getBlockEntity(POS);table.onLoad();
        table.onPlacedBy(c.createMockCreativeServerPlayerInWorld(),new ItemStack(BCSiliconBlocks.assemblyTable));
        table.inv.setStackInSlot(0,new ItemStack(Items.NOTE_BLOCK));table.inv.setStackInSlot(1,new ItemStack(BCTransportItems.pipeStructure,3));
        long expected=FacadeStateManager.INSTANCE.getValidFacades().stream().filter(s->s.state.isOf(Blocks.NOTE_BLOCK)&&s.isVisible).count()*2;
        c.assertTrue(expected>512 && table.getChoices().size()==expected,"Facade variants truncated at old selection limit");
        var copy=new TileAssemblyTable(table.getType(),table.getPos(),table.getCachedState());var packet=new buildcraft.lib.net.PacketBufferBC(io.netty.buffer.Unpooled.buffer());
        try{table.writePayload(TileAssemblyTable.NET_GUI_DATA,packet,buildcraft.lib.tile.TileBC_Neptune.NetSide.SERVER);copy.readPayload(TileAssemblyTable.NET_GUI_DATA,packet,buildcraft.lib.tile.TileBC_Neptune.NetSide.CLIENT,null);}
        finally{packet.release();}
        c.assertTrue(copy.recipesStates.equals(table.recipesStates),"Large facade choices packet lost entries");
        table.inv.stacks.get(1).setCount(2);c.assertTrue(table.getChoices().isEmpty(),"In-place inventory count change bypassed cache");
        table.inv.stacks.get(1).setCount(3);c.assertTrue(table.getChoices().size()==expected,"Cache failed to restore recipes");c.complete();
    }
}
