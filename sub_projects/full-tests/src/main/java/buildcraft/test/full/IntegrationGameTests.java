/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;

import java.util.*;
import com.google.common.collect.ImmutableList;
import buildcraft.api.enums.EnumRedstoneChipset;
import buildcraft.api.mj.*;
import buildcraft.api.recipes.*;
import buildcraft.api.properties.BuildCraftProperties;
import buildcraft.lib.recipe.*;
import buildcraft.lib.registry.BlockEntityRegistry;
import buildcraft.lib.tile.craft.WorkbenchCrafting;
import buildcraft.silicon.*;
import buildcraft.silicon.container.ContainerIntegrationTable;
import buildcraft.silicon.recipe.*;
import buildcraft.silicon.tile.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.item.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.*;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.recipe.*;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.test.*;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.*;

public final class IntegrationGameTests implements FabricGameTest {
    private static final BlockPos POS=new BlockPos(3,3,3);
    private static final long COST=500*MjAPI.MJ;
    private static final Identifier ID=new Identifier("buildcraft_full_tests:integration_upgrade");
    private static TileIntegrationTable table(TestContext c) {
        c.setBlockState(POS,BCSiliconBlocks.integrationTable);var tile=(TileIntegrationTable)c.getBlockEntity(POS);tile.onLoad();return tile;
    }
    private static void supply(TileIntegrationTable tile,int crafts) {
        tile.invTarget.setStackInSlot(0,EnumRedstoneChipset.RED.getStack().copyWithCount(crafts));
        tile.invToIntegrate.setStackInSlot(0,new ItemStack(Items.IRON_INGOT,crafts*2));
        tile.invToIntegrate.setStackInSlot(1,new ItemStack(Items.REDSTONE,crafts));
    }
    private static NativeAssemblyRecipe.Input input(Item item,int count){return new NativeAssemblyRecipe.Input(Ingredient.ofItems(item),count,null);}
    private static NativeIntegrationRecipe recipe(Identifier id,Item center,List<NativeAssemblyRecipe.Input> ingredients,ItemStack out,long cost) {
        return new NativeIntegrationRecipe(id,input(center,1),ingredients,out,cost,false,false);
    }
    private static net.minecraft.server.network.ServerPlayerEntity near(TestContext c,TileIntegrationTable tile) {
        var player=c.createMockCreativeServerPlayerInWorld();var v=Vec3d.ofCenter(tile.getPos());player.refreshPositionAndAngles(v.x,v.y,v.z-2,0,0);return player;
    }
    private static void withRecipe(TestContext c,NativeIntegrationRecipe recipe,Runnable run) {
        var manager=c.getWorld().getRecipeManager();var saved=new ArrayList<Recipe<?>>(manager.values());
        try{var all=new ArrayList<Recipe<?>>(saved.stream().filter(r->!r.getId().equals(recipe.getId())).toList());all.add(recipe);manager.setRecipes(all);run.run();}finally{manager.setRecipes(saved);}
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void fixtureDataPackChargesExactCostEvenWhenLastIngredientsVanish(TestContext c) {
        var tile=table(c);c.assertTrue(tile.getTarget()==0 && tile.getRecipePreview().isEmpty(),"Empty table invented a recipe");
        supply(tile,1);for(int i=0;i<100;i++)tile.tick();c.assertTrue(tile.power==0 && tile.invResult.getStackInSlot(0).isEmpty(),"Integration crafted passively");
        c.assertTrue(MjAPI.CONNECTOR.find(c.getWorld(),tile.getPos(),Direction.UP)==null,"Integration accepted direct MJ instead of laser work");
        tile.receiveLaserPower(COST-1);tile.tick();c.assertTrue(tile.invTarget.getStackInSlot(0).getCount()==1 && tile.getRequiredLaserPower()==1,"Craft completed early");
        c.assertTrue(tile.receiveLaserPower(2)==1,"Overfilled laser work");tile.tick();tile.tick();
        c.assertTrue(EnumRedstoneChipset.fromStack(tile.invResult.getStackInSlot(0))==EnumRedstoneChipset.IRON && tile.invResult.getStackInSlot(0).getCount()==1
            && tile.invTarget.getStackInSlot(0).isEmpty() && tile.invToIntegrate.stacks.stream().allMatch(ItemStack::isEmpty) && tile.power==0 && tile.getTarget()==0,"Last craft lost/duplicated inputs, result or energy");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void blockedOutputPreservesFullyPaidWorkAndCompletesOnce(TestContext c) {
        var tile=table(c);supply(tile,1);tile.receiveLaserPower(COST);tile.invResult.setStackInSlot(0,EnumRedstoneChipset.IRON.getStack().copyWithCount(64));
        tile.tick();c.assertTrue(tile.power==COST && tile.getTarget()==0 && tile.getStatus()==3 && tile.receiveLaserPower(1)==1
            && tile.invTarget.getStackInSlot(0).getCount()==1 && !tile.getRecipePreview().isEmpty(),"Blocked output lost work/input or hid preview");
        tile.invResult.setStackInSlot(0,EnumRedstoneChipset.IRON.getStack().copyWithCount(63));tile.tick();tile.tick();
        c.assertTrue(tile.invResult.getStackInSlot(0).getCount()==64 && tile.power==0 && tile.invTarget.getStackInSlot(0).isEmpty(),"Exact one-slot capacity failed");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void centerNbtCopiesAndResultOverridesWithoutMergingDifferentNames(TestContext c) {
        var tile=table(c);supply(tile,1);tile.invTarget.getStackInSlot(0).setCustomName(Text.literal("Felix component"));tile.invTarget.getStackInSlot(0).getOrCreateNbt().putInt("configuration",4754);
        var preview=tile.getRecipePreview();preview.getOrCreateNbt().putInt("configuration",0);
        c.assertTrue(tile.getRecipePreview().getNbt().getInt("configuration")==4754 && tile.invTarget.getStackInSlot(0).getNbt().getString("chipset").equals("red"),"Preview mutated center or recipe result");
        tile.receiveLaserPower(COST);tile.invResult.setStackInSlot(0,EnumRedstoneChipset.IRON.getStack());tile.tick();c.assertTrue(tile.power==COST && tile.getStatus()==3,"Different NBT merged into output");
        tile.invResult.setStackInSlot(0,ItemStack.EMPTY);tile.tick();var result=tile.invResult.getStackInSlot(0);
        c.assertTrue(result.getName().getString().equals("Felix component") && result.getNbt().getInt("configuration")==4754 && result.getNbt().getString("chipset").equals("iron"),"Center data or recipe override lost");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void overlappingCountedIngredientsUseSplitStacksWithoutDoubleConsumption(TestContext c) {
        var r=recipe(ID,Items.EMERALD,List.of(new NativeAssemblyRecipe.Input(Ingredient.ofItems(Items.OAK_PLANKS,Items.BIRCH_PLANKS),3,null),input(Items.OAK_PLANKS,2)),new ItemStack(Items.DIAMOND),COST);
        var stacks=DefaultedList.ofSize(8,ItemStack.EMPTY);stacks.set(0,new ItemStack(Items.OAK_PLANKS,2));stacks.set(1,new ItemStack(Items.BIRCH_PLANKS));stacks.set(2,new ItemStack(Items.BIRCH_PLANKS,2));
        c.assertTrue(Arrays.equals(r.allocate(new ItemStack(Items.EMERALD),stacks),new int[]{2,1,2,0,0,0,0,0}),"Overlapping ingredients consumed same units or missed split stacks");
        stacks.set(2,ItemStack.EMPTY);c.assertTrue(r.allocate(new ItemStack(Items.EMERALD),stacks)==null,"Insufficient overlap accepted");
        stacks.set(2,new ItemStack(Items.BIRCH_PLANKS,2));stacks.set(3,new ItemStack(Items.DIRT));c.assertTrue(r.allocate(new ItemStack(Items.EMERALD),stacks)==null,"Strict recipe accepted unrelated component");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativePacketRoundTripPreservesExactNbtCountsAndFlags(TestContext c) {
        var nbt=new NbtCompound();nbt.putInt("grade",2);
        var r=new NativeIntegrationRecipe(ID,new NativeAssemblyRecipe.Input(Ingredient.ofItems(Items.EMERALD),2,nbt),List.of(input(Items.IRON_INGOT,3)),new ItemStack(Items.DIAMOND,2),COST,true,true);
        var buffer=new net.minecraft.network.PacketByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            BCSiliconRecipes.INTEGRATION_SERIALIZER.write(buffer,r);var copy=BCSiliconRecipes.INTEGRATION_SERIALIZER.read(ID,buffer);var stacks=DefaultedList.ofSize(8,ItemStack.EMPTY);stacks.set(0,new ItemStack(Items.IRON_INGOT,3));stacks.set(1,new ItemStack(Items.DIRT));
            var center=new ItemStack(Items.EMERALD,2);c.assertTrue(copy.allocate(center,stacks)==null,"Lost exact center NBT");center.setNbt(nbt.copy());
            c.assertTrue(copy.allocate(center,stacks)!=null && copy.result(center).getNbt().getInt("grade")==2 && copy.result(center).getCount()==2 && copy.cost()==COST,"Packet lost counts/flags/output/cost");
            center.getNbt().putInt("extra",1);c.assertTrue(copy.allocate(center,stacks)==null,"Exact NBT accepted additional keys");
        }finally{buffer.release();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void malformedNativeRecipesRejectImpossibleCountsCostsAndUnknownItems(TestContext c) {
        var template=com.google.gson.JsonParser.parseString("{\"mj\":500,\"center\":{\"ingredient\":{\"item\":\"minecraft:emerald\"}},\"components\":[],\"result\":{\"item\":\"minecraft:diamond\"}}").getAsJsonObject();
        for(String field:List.of("zero_cost","huge_cost","unknown","too_many_results","zero_center","nine_components")) {
            var json=template.deepCopy();switch(field) {
                case "zero_cost"->json.addProperty("mj",0);case "huge_cost"->json.addProperty("mj",1000001);
                case "unknown"->json.getAsJsonObject("result").addProperty("item","buildcraft_full_tests:missing");
                case "too_many_results"->json.getAsJsonObject("result").addProperty("count",65);case "zero_center"->json.getAsJsonObject("center").addProperty("count",0);
                default->{for(int i=0;i<9;i++)json.getAsJsonArray("components").add(json.get("center").deepCopy());}
            }
            boolean failed=false;try{BCSiliconRecipes.INTEGRATION_SERIALIZER.read(ID,json);}catch(RuntimeException expected){failed=true;}c.assertTrue(failed,"Accepted malformed integration recipe "+field);
        }
        var buffer=new net.minecraft.network.PacketByteBuf(io.netty.buffer.Unpooled.buffer());try {
            Ingredient.ofItems(Items.EMERALD).write(buffer);buffer.writeVarInt(1);buffer.writeNbt(null);buffer.writeVarInt(9);
            boolean failed=false;try{BCSiliconRecipes.INTEGRATION_SERIALIZER.read(ID,buffer);}catch(IllegalArgumentException expected){failed=true;}c.assertTrue(failed,"Unbounded component packet");
        }finally{buffer.release();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void replacementAndRemovalResolveFreshRecipesAndKeepPaidWork(TestContext c) {
        var tile=table(c);supply(tile,2);tile.receiveLaserPower(COST/2);
        var old=(NativeIntegrationRecipe)c.getWorld().getRecipeManager().get(ID).orElseThrow();
        withRecipe(c,new NativeIntegrationRecipe(ID,old.center(),old.components(),new ItemStack(Items.DIAMOND),COST/2,false,false),()->{
            tile.tick();c.assertTrue(tile.invResult.getStackInSlot(0).isOf(Items.DIAMOND) && tile.power==0 && tile.invTarget.getStackInSlot(0).getCount()==1,"Reload used stale cost/result");
        });
        tile.invResult.setStackInSlot(0,ItemStack.EMPTY);tile.receiveLaserPower(COST/2);var manager=c.getWorld().getRecipeManager();var saved=new ArrayList<Recipe<?>>(manager.values());
        try{manager.setRecipes(saved.stream().filter(r->!r.getId().equals(ID)).toList());tile.tick();c.assertTrue(tile.getTarget()==0 && tile.power==COST/2 && tile.invTarget.getStackInSlot(0).getCount()==1,"Removed recipe kept crafting or erased work");}finally{manager.setRecipes(saved);}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void publicBasicApiMatchesSplitRequirementsAndDefendsOutputCopies(TestContext c) {
        var out=new ItemStack(Items.DIAMOND);var r=new IntegrationRecipeBasic(ID,COST,IngredientStack.of(Items.EMERALD),List.of(new IngredientStack(Ingredient.ofItems(Items.IRON_INGOT),3)),out);out.setCount(64);
        var stacks=DefaultedList.ofSize(8,ItemStack.EMPTY);stacks.set(0,new ItemStack(Items.IRON_INGOT));stacks.set(1,new ItemStack(Items.IRON_INGOT,2));
        var result=r.getOutput(new ItemStack(Items.EMERALD),stacks);c.assertTrue(result.getCount()==1,"API kept caller's mutable result");result.setCount(32);c.assertTrue(r.getOutput(new ItemStack(Items.EMERALD),stacks).getCount()==1,"API exposed result reference");
        var original=BuildcraftRecipeRegistry.integrationRecipes;var registry=new IntegrationRecipeRegistry();registry.addRecipe(r);BuildcraftRecipeRegistry.integrationRecipes=registry;
        try{var tile=table(c);tile.invTarget.setStackInSlot(0,new ItemStack(Items.EMERALD));tile.invToIntegrate.setStackInSlot(0,stacks.get(0));tile.invToIntegrate.setStackInSlot(1,stacks.get(1));tile.receiveLaserPower(COST);tile.tick();c.assertTrue(tile.invResult.getStackInSlot(0).isOf(Items.DIAMOND) && tile.power==0,"Table ignored public replacement registry");}finally{BuildcraftRecipeRegistry.integrationRecipes=original;}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void dynamicApiRecipesCannotMutateInventoriesDuringPreview(TestContext c) {
        var original=BuildcraftRecipeRegistry.integrationRecipes;var registry=new IntegrationRecipeRegistry();BuildcraftRecipeRegistry.integrationRecipes=registry;
        registry.addRecipe(new IntegrationRecipe(ID) {
            public ItemStack getOutput(ItemStack center,DefaultedList<ItemStack> parts) {
                var result=new ItemStack(Items.DIAMOND,2);result.setCustomName(center.getName());center.setCount(0);parts.forEach(s->s.setCount(0));return result;
            }
            public IngredientStack getCenterStack(){return new IngredientStack(Ingredient.ofItems(Items.EMERALD),2);}
            public ImmutableList<IngredientStack> getRequirements(ItemStack output){return ImmutableList.of(new IngredientStack(Ingredient.ofItems(Items.IRON_INGOT),3));}
            public long getRequiredMicroJoules(ItemStack output){return output.getCount()*COST;}
        });
        try{var tile=table(c);var center=new ItemStack(Items.EMERALD,2);center.setCustomName(Text.literal("Felix dynamic"));tile.invTarget.setStackInSlot(0,center);tile.invToIntegrate.setStackInSlot(0,new ItemStack(Items.IRON_INGOT,3));
            c.assertTrue(tile.getRecipePreview().getName().getString().equals("Felix dynamic") && tile.getTarget()==2*COST && center.getCount()==2 && tile.invToIntegrate.getStackInSlot(0).getCount()==3,"API preview mutated real input");
            tile.receiveLaserPower(2*COST);tile.tick();c.assertTrue(tile.invResult.getStackInSlot(0).getCount()==2 && tile.invTarget.getStackInSlot(0).isEmpty() && tile.invToIntegrate.getStackInSlot(0).isEmpty() && tile.power==0,"Dynamic API accounting incorrect");
        }finally{BuildcraftRecipeRegistry.integrationRecipes=original;}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void allFacesKeepInputAccessAndPrioritizeExtractOnlyResult(TestContext c) {
        var tile=table(c);supply(tile,2);tile.invResult.setStackInSlot(0,new ItemStack(Items.DIAMOND,4));
        for(var face:Direction.values())try(var tx=Transaction.openOuter()) {
            var storage=ItemStorage.SIDED.find(c.getWorld(),tile.getPos(),face);var first=storage.iterator().next();
            c.assertTrue(first.getResource().isOf(Items.DIAMOND),"Output not offered first");
            c.assertTrue(storage.extract(ItemVariant.of(Items.DIAMOND),2,tx)==2 && storage.extract(ItemVariant.of(Items.IRON_INGOT),1,tx)==1,"Output/input extraction denied");
            c.assertTrue(storage.insert(ItemVariant.of(Items.DIAMOND),1,tx)==1 && tile.invResult.getStackInSlot(0).getCount()==2,"Inserted through output slot");
        }
        c.assertTrue(tile.invResult.getStackInSlot(0).getCount()==4 && tile.invToIntegrate.getStackInSlot(0).getCount()==4 && tile.invToIntegrate.stacks.stream().noneMatch(s->s.isOf(Items.DIAMOND)),"Transfer rollback changed inventories");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void savedWorkAndSlotsRestoreBeforeWorldAttachmentAndRejectStaleHandles(TestContext c) {
        var tile=table(c);supply(tile,1);tile.receiveLaserPower(COST/2);tile.tick();var stale=ItemStorage.SIDED.find(c.getWorld(),tile.getPos(),Direction.UP);
        var copy=BlockEntityRegistry.create(TileIntegrationTable.class,tile.getPos(),tile.getCachedState());copy.readNbt(tile.createNbt());c.getWorld().removeBlockEntity(tile.getPos());c.getWorld().addBlockEntity(copy);copy.onLoad();
        c.assertTrue(copy.power==COST/2 && copy.getRequiredLaserPower()==COST/2 && copy.getRecipeId().equals(ID),"Saved work/recipe/slots failed before attachment");
        try(var tx=Transaction.openOuter()){c.assertTrue(stale.extract(ItemVariant.of(Items.IRON_INGOT),1,tx)==0,"Old storage still extracted");}
        c.assertTrue(tile.receiveLaserPower(1)==1,"Old target accepted power");copy.receiveLaserPower(COST/2);copy.tick();c.assertTrue(copy.invResult.getStackInSlot(0).getCount()==1 && copy.power==0,"Reload craft failed");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativeMenuProtectsPreviewRoutesShiftClicksAndChecksDistance(TestContext c) {
        var tile=table(c);var player=near(c,tile);var menu=new ContainerIntegrationTable(player,1,tile);player.getInventory().setStack(9,EnumRedstoneChipset.RED.getStack());menu.quickMove(player,ContainerIntegrationTable.PLAYER_START);
        player.getInventory().setStack(9,new ItemStack(Items.IRON_INGOT,2));menu.quickMove(player,ContainerIntegrationTable.PLAYER_START);
        player.getInventory().setStack(9,new ItemStack(Items.REDSTONE));menu.quickMove(player,ContainerIntegrationTable.PLAYER_START);menu.sendContentUpdates();
        c.assertTrue(menu.slots.size()==47 && tile.invTarget.getStackInSlot(0).getCount()==1 && menu.getSlot(ContainerIntegrationTable.PREVIEW).getStack().getCount()==1,"Menu routing/preview/layout failed");
        for(var action:SlotActionType.values())menu.onSlotClick(ContainerIntegrationTable.PREVIEW,0,action,player);menu.quickMove(player,ContainerIntegrationTable.PREVIEW);
        c.assertTrue(menu.getCursorStack().isEmpty() && tile.invResult.getStackInSlot(0).isEmpty(),"Preview materialized");
        tile.receiveLaserPower(COST);tile.tick();menu.quickMove(player,ContainerIntegrationTable.OUTPUT);c.assertTrue(tile.invResult.getStackInSlot(0).isEmpty(),"Output quick-move failed");
        player.refreshPositionAndAngles(player.getX()+100,player.getY(),player.getZ(),0,0);player.getInventory().setStack(9,EnumRedstoneChipset.RED.getStack());menu.quickMove(player,ContainerIntegrationTable.PLAYER_START);
        c.assertTrue(!menu.canUse(player) && tile.invTarget.getStackInSlot(0).isEmpty(),"Distant player inserted center");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void containerRemaindersExportOnlyAfterCommittedCraft(TestContext c) {
        withRecipe(c,recipe(ID,Items.WATER_BUCKET,List.of(input(Items.MILK_BUCKET,1)),new ItemStack(Items.DIAMOND),COST),()->{
            var tile=table(c);tile.invTarget.setStackInSlot(0,new ItemStack(Items.WATER_BUCKET));tile.invToIntegrate.setStackInSlot(0,new ItemStack(Items.MILK_BUCKET));c.setBlockState(POS.down(),Blocks.BARREL);var barrel=(BarrelBlockEntity)c.getBlockEntity(POS.down());
            tile.receiveLaserPower(COST);tile.invResult.setStackInSlot(0,new ItemStack(Items.DIRT));tile.tick();c.assertTrue(barrel.isEmpty() && tile.power==COST,"Blocked craft exported remainders");
            tile.invResult.setStackInSlot(0,ItemStack.EMPTY);tile.tick();c.assertTrue(barrel.getStack(0).isOf(Items.BUCKET) && barrel.getStack(0).getCount()==2 && tile.invToIntegrate.getStackInSlot(0).isEmpty() && tile.invTarget.getStackInSlot(0).isEmpty(),"Lost center/component buckets");
        });c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativeLaserStopsAtBlockedOrMissingIntegrationWork(TestContext c) {
        var tile=table(c);supply(tile,1);c.setBlockState(POS.west(2),BCSiliconBlocks.laser.getDefaultState().with(BuildCraftProperties.BLOCK_FACING_6,Direction.EAST));var laser=(TileLaser)c.getBlockEntity(POS.west(2));laser.onLoad();var receiver=(IMjReceiver)MjAPI.CONNECTOR.find(c.getWorld(),laser.getPos(),Direction.UP);
        tile.invResult.setStackInSlot(0,new ItemStack(Items.DIRT));for(int i=0;i<20;i++){receiver.receivePower(16*MjAPI.MJ,false);laser.tick();tile.tick();}c.assertTrue(tile.power==0 && laser.getLastTransfer()==0,"Laser worked through blocked output");
        tile.invResult.setStackInSlot(0,ItemStack.EMPTY);for(int i=0;i<200;i++){receiver.receivePower(16*MjAPI.MJ,false);laser.tick();tile.tick();}
        c.assertTrue(tile.invResult.getStackInSlot(0).getCount()==1 && tile.power==0 && laser.getLastTransfer()==0,"Native laser failed exact integration or remained active");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativeSurvivalRecipeRequiresIronChipsetAndSurvivesSync(TestContext c) {
        var id=new Identifier("buildcraftsilicon:integration_table");var recipe=(CraftingRecipe)c.getWorld().getRecipeManager().get(id).orElseThrow();var grid=new WorkbenchCrafting.Grid(3,3);
        for(int i:new int[]{0,2,3,5,6,8})grid.setStack(i,new ItemStack(Items.OBSIDIAN));grid.setStack(1,new ItemStack(Items.GOLD_INGOT));grid.setStack(4,EnumRedstoneChipset.RED.getStack());grid.setStack(7,new ItemStack(net.minecraft.registry.Registries.ITEM.get(new Identifier("buildcraftcore:gear_diamond"))));
        c.assertTrue(!recipe.matches(grid,c.getWorld()),"Red chipset substituted iron chipset");grid.setStack(4,EnumRedstoneChipset.IRON.getStack());c.assertTrue(recipe.matches(grid,c.getWorld()) && recipe.craft(grid,c.getWorld().getRegistryManager()).isOf(BCSiliconBlocks.integrationTable.asItem()),"Survival machine recipe missing");
        var buffer=new net.minecraft.network.PacketByteBuf(io.netty.buffer.Unpooled.buffer());try{RecipeSerializer.SHAPED.write(buffer,(ShapedRecipe)recipe);var copy=RecipeSerializer.SHAPED.read(id,buffer);grid.setStack(4,EnumRedstoneChipset.GOLD.getStack());c.assertTrue(!copy.matches(grid,c.getWorld()),"Recipe packet lost chipset NBT requirement");}finally{buffer.release();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void removalDropsAllThreeRealInventoriesExactlyOnce(TestContext c) {
        var tile=table(c);supply(tile,2);tile.receiveLaserPower(COST);tile.tick();c.getWorld().breakBlock(tile.getPos(),false);tile.tick();
        var drops=c.getWorld().getEntitiesByClass(net.minecraft.entity.ItemEntity.class,new Box(tile.getPos()).expand(1),e->true);
        int red=0,iron=0;for(var e:drops)if(e.getStack().isOf(BCSiliconItems.redstoneChipset)){if(EnumRedstoneChipset.fromStack(e.getStack())==EnumRedstoneChipset.RED)red+=e.getStack().getCount();else iron+=e.getStack().getCount();}
        c.assertTrue(red==1 && iron==1 && drops.stream().filter(e->e.getStack().isOf(Items.IRON_INGOT)).mapToInt(e->e.getStack().getCount()).sum()==2
            && tile.receiveLaserPower(COST)==COST,"Breaking lost/duplicated center, components or output");c.complete();
    }
}
