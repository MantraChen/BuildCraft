/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;

import java.util.*;
import buildcraft.api.enums.EnumRedstoneChipset;
import buildcraft.api.mj.*;
import buildcraft.api.properties.BuildCraftProperties;
import buildcraft.lib.net.PacketBufferBC;
import buildcraft.lib.registry.BlockEntityRegistry;
import buildcraft.lib.tile.craft.WorkbenchCrafting;
import buildcraft.silicon.*;
import buildcraft.silicon.container.ContainerAdvancedCraftingTable;
import buildcraft.silicon.tile.*;
import buildcraft.transport.*;
import buildcraft.transport.tile.TilePipeHolder;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.item.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.*;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.recipe.*;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.screen.*;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.test.*;
import net.minecraft.util.*;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.*;

public final class AdvancedCraftingGameTests implements FabricGameTest {
    private static final BlockPos POS=new BlockPos(3,3,3);
    private static final long COST=500*MjAPI.MJ;
    private static TileAdvancedCraftingTable table(TestContext c) {
        c.setBlockState(POS,BCSiliconBlocks.advancedCraftingTable);var tile=(TileAdvancedCraftingTable)c.getBlockEntity(POS);tile.onLoad();
        tile.onPlacedBy(c.createMockCreativeServerPlayerInWorld(),new ItemStack(BCSiliconBlocks.advancedCraftingTable));return tile;
    }
    private static void pattern(TileAdvancedCraftingTable tile,int slot,ItemStack stack){tile.invBlueprint.setStackInSlot(slot,stack.copyWithCount(1));}
    private static void logs(TileAdvancedCraftingTable tile,int n){pattern(tile,0,new ItemStack(Items.OAK_LOG));tile.invMaterials.setStackInSlot(0,new ItemStack(Items.OAK_LOG,n));}
    private static int count(buildcraft.lib.tile.item.ItemHandlerSimple inventory,Item item){return inventory.stacks.stream().filter(s->s.isOf(item)).mapToInt(ItemStack::getCount).sum();}
    private static net.minecraft.server.network.ServerPlayerEntity near(TestContext c,TileAdvancedCraftingTable tile) {
        var player=c.createMockCreativeServerPlayerInWorld();var v=Vec3d.ofCenter(tile.getPos());player.refreshPositionAndAngles(v.x,v.y,v.z-2,0,0);return player;
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void exactFiveHundredMjAndNoPassiveOrDirectMjCrafting(TestContext c) {
        var tile=table(c);pattern(tile,0,new ItemStack(Items.OAK_LOG));c.assertTrue(tile.getTarget()==0 && tile.receiveLaserPower(COST)==COST,"Ghost materials accepted laser energy");
        logs(tile,2);for(int i=0;i<250;i++)tile.tick();c.assertTrue(count(tile.invResults,Items.OAK_PLANKS)==0 && tile.power==0,"Advanced table crafted passively");
        c.assertTrue(MjAPI.CONNECTOR.find(c.getWorld(),tile.getPos(),Direction.UP)==null,"Advanced table bypassed laser supply");
        tile.receiveLaserPower(COST-1);tile.tick();c.assertTrue(count(tile.invMaterials,Items.OAK_LOG)==2 && tile.getRequiredLaserPower()==1,"Sub-MJ work completed early");
        c.assertTrue(tile.receiveLaserPower(2)==1,"Advanced table overfilled");tile.tick();tile.tick();
        c.assertTrue(count(tile.invResults,Items.OAK_PLANKS)==4 && count(tile.invMaterials,Items.OAK_LOG)==1 && tile.power==0,"Exact craft or charge wrong");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nineOutputSlotsBlockAtomicallyAndResumeAtExactCapacity(TestContext c) {
        var tile=table(c);logs(tile,2);tile.receiveLaserPower(COST/2);
        for(int i=0;i<8;i++)tile.invResults.setStackInSlot(i,new ItemStack(Items.STONE,64));tile.invResults.setStackInSlot(8,new ItemStack(Items.OAK_PLANKS,61));
        c.assertTrue(!tile.hasWork() && tile.receiveLaserPower(COST)==COST,"Full nine-slot output accepted work");tile.tick();
        c.assertTrue(count(tile.invMaterials,Items.OAK_LOG)==2 && count(tile.invResults,Items.OAK_PLANKS)==61 && tile.power==COST/2,"Blocked output consumed input/work");
        tile.invResults.setStackInSlot(8,new ItemStack(Items.OAK_PLANKS,60));tile.receiveLaserPower(COST/2);tile.tick();
        c.assertTrue(count(tile.invResults,Items.OAK_PLANKS)==64 && count(tile.invMaterials,Items.OAK_LOG)==1 && !tile.hasWork(),"Last output slot limit incorrect");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void allFacesInsertOnlyMaterialsAndExtractOnlyRealOutputs(TestContext c) {
        var tile=table(c);logs(tile,3);tile.invResults.setStackInSlot(0,new ItemStack(Items.STICK,5));
        for(var face:Direction.values())try(var tx=Transaction.openOuter()) {
            var storage=ItemStorage.SIDED.find(c.getWorld(),tile.getPos(),face);
            c.assertTrue(storage.insert(ItemVariant.of(Items.OAK_LOG),2,tx)==2 && storage.extract(ItemVariant.of(Items.OAK_LOG),1,tx)==0
                && storage.extract(ItemVariant.of(Items.STICK),3,tx)==3,"Wrong advanced sided storage permissions");
        }
        c.assertTrue(count(tile.invMaterials,Items.OAK_LOG)==3 && count(tile.invResults,Items.STICK)==5 && tile.invBlueprint.getStackInSlot(0).getCount()==1,"Rollback changed real or phantom inventory");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void patternChangesAndWorldReloadPreservePaidLaserWork(TestContext c) {
        var tile=table(c);logs(tile,1);tile.receiveLaserPower(COST/2);
        pattern(tile,0,new ItemStack(Items.OAK_PLANKS));pattern(tile,3,new ItemStack(Items.OAK_PLANKS));tile.tick();
        c.assertTrue(tile.power==COST/2 && !tile.hasWork(),"Pattern change lost generic paid laser work");
        tile.invMaterials.setStackInSlot(0,new ItemStack(Items.OAK_PLANKS,2));var oldStorage=ItemStorage.SIDED.find(c.getWorld(),tile.getPos(),Direction.NORTH);
        var copy=BlockEntityRegistry.create(TileAdvancedCraftingTable.class,tile.getPos(),tile.getCachedState());copy.readNbt(tile.createNbt());
        c.getWorld().removeBlockEntity(tile.getPos());c.getWorld().addBlockEntity(copy);copy.onLoad();
        c.assertTrue(copy.power==COST/2 && copy.getRecipePreview().isOf(Items.STICK) && copy.getRequiredLaserPower()==COST/2,"Reload lost materials/template/paid work");
        try(var tx=Transaction.openOuter()){c.assertTrue(oldStorage.insert(ItemVariant.of(Items.OAK_LOG),1,tx)==0,"Stale storage survived block-entity replacement");}
        c.assertTrue(tile.receiveLaserPower(1)==1,"Stale table accepted laser power");copy.receiveLaserPower(COST/2);copy.tick();c.assertTrue(count(copy.invResults,Items.STICK)==4,"Reloaded recipe did not finish");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void dynamicRepairsRequireExactTemplateNbt(TestContext c) {
        var tile=table(c);var one=new ItemStack(Items.IRON_SWORD);one.setDamage(120);var two=new ItemStack(Items.IRON_SWORD);two.setDamage(170);
        pattern(tile,0,one);pattern(tile,1,two);tile.invMaterials.setStackInSlot(0,one.copy());tile.invMaterials.setStackInSlot(1,one.copy());
        c.assertTrue(!tile.hasWork() && tile.getRecipePreview().getDamage()==28,"Wrong-NBT material matched or dynamic result lost durability");
        tile.invMaterials.setStackInSlot(1,two.copy());tile.receiveLaserPower(COST);tile.tick();
        c.assertTrue(tile.invResults.getStackInSlot(0).isOf(Items.IRON_SWORD) && tile.invResults.getStackInSlot(0).getDamage()==28 && count(tile.invMaterials,Items.IRON_SWORD)==0,"Repair lost durability or duplicated sword");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void cakeReturnsEveryBucketToMaterials(TestContext c) {
        var tile=table(c);Item[] grid={Items.MILK_BUCKET,Items.MILK_BUCKET,Items.MILK_BUCKET,Items.SUGAR,Items.EGG,Items.SUGAR,Items.WHEAT,Items.WHEAT,Items.WHEAT};
        for(int i=0;i<9;i++){pattern(tile,i,new ItemStack(grid[i]));tile.invMaterials.setStackInSlot(i,new ItemStack(grid[i]));}
        tile.receiveLaserPower(COST);tile.tick();
        c.assertTrue(count(tile.invResults,Items.CAKE)==1 && count(tile.invMaterials,Items.BUCKET)==3 && count(tile.invMaterials,Items.MILK_BUCKET)==0 && tile.power==0,"Cake remainder accounting failed");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void overflowRemaindersExportOnlyAfterCommittedCraft(TestContext c) {
        var manager=c.getWorld().getRecipeManager();var saved=new ArrayList<Recipe<?>>(manager.values());var all=new ArrayList<Recipe<?>>(saved);
        all.add(new ShapelessRecipe(new Identifier("buildcraftsilicon:test_remainder"),"",CraftingRecipeCategory.MISC,new ItemStack(Items.GOLD_INGOT),DefaultedList.copyOf(Ingredient.EMPTY,Ingredient.ofItems(Items.DEAD_BUSH))) {
            @Override public DefaultedList<ItemStack> getRemainder(RecipeInputInventory input){var out=DefaultedList.ofSize(input.size(),ItemStack.EMPTY);out.set(0,new ItemStack(Items.BUCKET));return out;}
        });
        try {
            manager.setRecipes(all);var tile=table(c);pattern(tile,0,new ItemStack(Items.DEAD_BUSH));tile.invMaterials.setStackInSlot(0,new ItemStack(Items.DEAD_BUSH,64));for(int i=1;i<15;i++)tile.invMaterials.setStackInSlot(i,new ItemStack(Items.DIRT,64));
            c.setBlockState(POS.down(),Blocks.BARREL);var barrel=(BarrelBlockEntity)c.getBlockEntity(POS.down());tile.hasWork();tile.getRequiredLaserPower();c.assertTrue(barrel.isEmpty(),"Simulation exported recipe remainder");
            tile.receiveLaserPower(COST);tile.tick();c.assertTrue(barrel.getStack(0).isOf(Items.BUCKET) && barrel.getStack(0).getCount()==1 && count(tile.invResults,Items.GOLD_INGOT)==1 && count(tile.invMaterials,Items.DEAD_BUSH)==63,"Overflow remainder lost/duplicated");
        }finally{manager.setRecipes(saved);}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void dataPackReplacementAndRemovalNeverUseStaleResult(TestContext c) {
        var tile=table(c);logs(tile,2);tile.receiveLaserPower(COST/2);var manager=c.getWorld().getRecipeManager();var saved=new ArrayList<Recipe<?>>(manager.values());var id=new Identifier("minecraft:oak_planks");
        try {
            var all=new ArrayList<Recipe<?>>(saved.stream().filter(r->!r.getId().equals(id)).toList());all.add(new ShapelessRecipe(id,"",CraftingRecipeCategory.MISC,new ItemStack(Items.DIAMOND),DefaultedList.copyOf(Ingredient.EMPTY,Ingredient.ofItems(Items.OAK_LOG))));manager.setRecipes(all);
            tile.receiveLaserPower(COST/2);tile.tick();c.assertTrue(count(tile.invResults,Items.DIAMOND)==1 && tile.power==0,"Reload did not use replacement output/exact cost");
            tile.receiveLaserPower(COST/2);manager.setRecipes(all.stream().filter(r->!r.getId().equals(id)).toList());tile.tick();c.assertTrue(!tile.hasWork() && tile.getRecipePreview().isEmpty() && count(tile.invMaterials,Items.OAK_LOG)==1 && tile.power==COST/2,"Removed recipe consumed materials or lost paid work");
        }finally{manager.setRecipes(saved);}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void phantomAndPreviewCannotBeTakenOrConsumeCursor(TestContext c) {
        var tile=table(c);var player=near(c,tile);var menu=new ContainerAdvancedCraftingTable(player,1,tile);menu.setCursorStack(new ItemStack(Items.OAK_LOG,12));
        menu.onSlotClick(ContainerAdvancedCraftingTable.BLUEPRINT_START,0,SlotActionType.PICKUP,player);menu.sendContentUpdates();
        c.assertTrue(menu.getCursorStack().getCount()==12 && tile.invBlueprint.getStackInSlot(0).getCount()==1 && menu.getSlot(0).getStack().isOf(Items.OAK_PLANKS),"Template consumed cursor or preview missing");menu.setCursorStack(ItemStack.EMPTY);
        for(var type:SlotActionType.values()){menu.onSlotClick(0,0,type,player);if(type!=SlotActionType.PICKUP)menu.onSlotClick(ContainerAdvancedCraftingTable.BLUEPRINT_START,0,type,player);}
        menu.quickMove(player,0);menu.quickMove(player,ContainerAdvancedCraftingTable.BLUEPRINT_START);
        c.assertTrue(menu.getCursorStack().isEmpty() && tile.invBlueprint.getStackInSlot(0).getCount()==1 && count(tile.invResults,Items.OAK_PLANKS)==0,"Ghost/result preview became real items");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void malformedTemplatePacketsApplyNothing(TestContext c) throws Exception {
        var tile=table(c);var menu=new ContainerAdvancedCraftingTable(near(c,tile),1,tile);var buffer=new PacketBufferBC(io.netty.buffer.Unpooled.buffer());
        try {
            buffer.writeByte(2);buffer.writeVarInt(ContainerAdvancedCraftingTable.BLUEPRINT_START);buffer.writeItemStack(new ItemStack(Items.DIAMOND,64));buffer.writeVarInt(ContainerAdvancedCraftingTable.OUTPUT_START);buffer.writeItemStack(new ItemStack(Items.DIAMOND));
            boolean failed=false;try{menu.readMessage(ContainerAdvancedCraftingTable.NET_SET_PHANTOM_MULTI,buffer,false,null);}catch(java.io.IOException expected){failed=true;}
            c.assertTrue(failed && tile.invBlueprint.stacks.stream().allMatch(ItemStack::isEmpty) && tile.invResults.stacks.stream().allMatch(ItemStack::isEmpty),"Malformed multi-slot update partially mutated inventory");
            buffer.clear();buffer.writeVarInt(ContainerAdvancedCraftingTable.BLUEPRINT_START);buffer.writeItemStack(new ItemStack(Items.OAK_LOG,64));menu.readMessage(ContainerAdvancedCraftingTable.NET_SET_PHANTOM,buffer,false,null);
            c.assertTrue(tile.invBlueprint.getStackInSlot(0).getCount()==1,"Phantom packet kept real stack count");
        }finally{buffer.release();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void survivalRecipeBookChecksUnlocksAndScreenDistance(TestContext c) {
        var tile=table(c);var player=new net.minecraft.server.network.ServerPlayerEntity(c.getWorld().getServer(),c.getWorld(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"advanced-survival"));
        c.getWorld().getServer().getPlayerManager().onPlayerConnect(new net.minecraft.network.ClientConnection(net.minecraft.network.NetworkSide.SERVERBOUND),player);player.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
        var v=Vec3d.ofCenter(tile.getPos());player.refreshPositionAndAngles(v.x,v.y,v.z-2,0,0);var menu=new ContainerAdvancedCraftingTable(player,1,tile);var id=new Identifier("minecraft:stick");
        menu.selectRecipe(id);c.assertTrue(tile.invBlueprint.stacks.stream().allMatch(ItemStack::isEmpty),"Locked survival recipe accepted");
        tile.invMaterials.setStackInSlot(0,new ItemStack(Items.BIRCH_PLANKS,4));player.unlockRecipes(List.of(c.getWorld().getRecipeManager().get(id).orElseThrow()));menu.selectRecipe(id);
        c.assertTrue(count(tile.invBlueprint,Items.BIRCH_PLANKS)==2 && tile.getRecipePreview().isOf(Items.STICK),"Recipe selection ignored available tag variant");
        player.refreshPositionAndAngles(v.x+100,v.y,v.z,0,0);menu.selectRecipe(new Identifier("minecraft:birch_planks"));c.assertTrue(count(tile.invBlueprint,Items.BIRCH_PLANKS)==2,"Distant player changed pattern");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativeLaserDeliversExactWorkAndStopsForFullOutput(TestContext c) {
        var tile=table(c);logs(tile,1);c.setBlockState(POS.west(2),BCSiliconBlocks.laser.getDefaultState().with(BuildCraftProperties.BLOCK_FACING_6,Direction.EAST));var laser=(TileLaser)c.getBlockEntity(POS.west(2));laser.onLoad();var mj=(IMjReceiver)MjAPI.CONNECTOR.find(c.getWorld(),laser.getPos(),Direction.UP);
        for(int i=0;i<200;i++){mj.receivePower(16*MjAPI.MJ,false);laser.tick();tile.tick();}
        c.assertTrue(count(tile.invResults,Items.OAK_PLANKS)==4 && count(tile.invMaterials,Items.OAK_LOG)==0 && tile.power==0,"Laser did not produce exact advanced output");laser.tick();c.assertTrue(laser.getLastTransfer()==0,"Laser kept firing at no-work advanced table");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=150)
    public void woodenPipeExtractsResultsButNeverMaterialsOrTemplate(TestContext c) {
        var tile=table(c);logs(tile,2);tile.receiveLaserPower(COST);tile.tick();
        c.setBlockState(POS.east(),BCTransportBlocks.pipeHolder);var wood=(TilePipeHolder)c.getBlockEntity(POS.east());wood.onPlacedBy(null,new ItemStack(BCTransportItems.pipeItemWood));
        c.setBlockState(POS.east(2),BCTransportBlocks.pipeHolder);((TilePipeHolder)c.getBlockEntity(POS.east(2))).onPlacedBy(null,new ItemStack(BCTransportItems.pipeItemStone));c.setBlockState(POS.east(3),Blocks.BARREL);wood.tick();
        ((IMjReceiver)MjAPI.CONNECTOR.find(c.getWorld(),wood.getPos(),Direction.UP)).receivePower(8*MjAPI.MJ,false);
        c.waitAndRun(90,()->{var barrel=(BarrelBlockEntity)c.getBlockEntity(POS.east(3));int planks=0;for(int i=0;i<barrel.size();i++)if(barrel.getStack(i).isOf(Items.OAK_PLANKS))planks+=barrel.getStack(i).getCount();
            c.assertTrue(planks==4 && count(tile.invMaterials,Items.OAK_LOG)==1 && count(tile.invBlueprint,Items.OAK_LOG)==1,"Pipe stole materials/ghosts or lost output");c.complete();});
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void removalDropsRealMaterialsAndOutputsWithoutGhostCopies(TestContext c) {
        var tile=table(c);logs(tile,3);tile.receiveLaserPower(COST);tile.tick();c.getWorld().breakBlock(tile.getPos(),false);tile.tick();
        var drops=c.getWorld().getEntitiesByClass(net.minecraft.entity.ItemEntity.class,new Box(tile.getPos()).expand(1),e->true);int logs=0,planks=0;
        for(var entity:drops){if(entity.getStack().isOf(Items.OAK_LOG))logs+=entity.getStack().getCount();if(entity.getStack().isOf(Items.OAK_PLANKS))planks+=entity.getStack().getCount();}
        c.assertTrue(logs==2 && planks==4 && tile.receiveLaserPower(100)==100,"Removal duplicated phantom, lost items or accepted stale energy");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativeMachineRecipeRequiresRedChipsetAndSyncsNbtIngredient(TestContext c) {
        var id=new Identifier("buildcraftsilicon:advanced_crafting_table");var recipe=(CraftingRecipe)c.getWorld().getRecipeManager().get(id).orElseThrow();var grid=new WorkbenchCrafting.Grid(3,3);
        for(int i:new int[]{0,2,3,5,6,8})grid.setStack(i,new ItemStack(Items.OBSIDIAN));grid.setStack(1,new ItemStack(Items.CRAFTING_TABLE));grid.setStack(4,new ItemStack(Items.CHEST));grid.setStack(7,EnumRedstoneChipset.IRON.getStack());
        c.assertTrue(!recipe.matches(grid,c.getWorld()),"Iron chipset replaced red chipset in survival recipe");grid.setStack(7,EnumRedstoneChipset.RED.getStack());c.assertTrue(recipe.matches(grid,c.getWorld()) && recipe.craft(grid,c.getWorld().getRegistryManager()).isOf(BCSiliconBlocks.advancedCraftingTable.asItem()),"Native original machine recipe missing");
        var buffer=new net.minecraft.network.PacketByteBuf(io.netty.buffer.Unpooled.buffer());try{RecipeSerializer.SHAPED.write(buffer,(ShapedRecipe)recipe);var copy=RecipeSerializer.SHAPED.read(id,buffer);grid.setStack(7,EnumRedstoneChipset.GOLD.getStack());c.assertTrue(!copy.matches(grid,c.getWorld()),"Recipe synchronization lost custom NBT ingredient");}finally{buffer.release();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void malformedSavedPowerCannotCreateUnpaidBulkCrafts(TestContext c) {
        var tile=table(c);logs(tile,64);var nbt=tile.createNbt();nbt.putLong("power",Long.MAX_VALUE);tile.readNbt(nbt);
        c.assertTrue(tile.power==COST,"Advanced table did not clamp saved energy to one craft");tile.tick();tile.tick();c.assertTrue(count(tile.invResults,Items.OAK_PLANKS)==4 && count(tile.invMaterials,Items.OAK_LOG)==63,"Oversized saved balance produced unpaid bulk output");c.complete();
    }
}
