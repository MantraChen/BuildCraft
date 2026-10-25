/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;

import java.util.*;
import buildcraft.api.mj.*;
import buildcraft.factory.*;
import buildcraft.factory.container.ContainerAutoCraftItems;
import buildcraft.factory.tile.*;
import buildcraft.lib.net.PacketBufferBC;
import buildcraft.lib.registry.BlockEntityRegistry;
import buildcraft.lib.tile.craft.WorkbenchCrafting;
import buildcraft.transport.*;
import buildcraft.transport.tile.TilePipeHolder;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.item.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.*;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.recipe.*;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.test.*;
import net.minecraft.util.*;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;

public final class AutoCraftGameTests implements FabricGameTest {
    private static final BlockPos POS=new BlockPos(3,3,3);

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void passiveCraftingConsumesExactlyOneLogAfterTwoHundredTicks(TestContext c) {
        var tile=bench(c);pattern(tile,0,new ItemStack(Items.OAK_LOG));insert(c,tile,Items.OAK_LOG,2);
        c.assertTrue(tile.getRecipePreview().isOf(Items.OAK_PLANKS) && tile.hasWork(),"Native recipe preview/work signal missing");
        for(int i=0;i<199;i++)tile.tick();
        c.assertTrue(tile.invResult.getStackInSlot(0).isEmpty() && count(tile,Items.OAK_LOG)==2,"Passive work completed early");
        tile.tick();
        c.assertTrue(tile.invResult.getStackInSlot(0).isOf(Items.OAK_PLANKS) && tile.invResult.getStackInSlot(0).getCount()==4
            && count(tile,Items.OAK_LOG)==1 && tile.getStoredPower()==0,"Passive craft lost/duplicated ingredients or charged incorrectly");
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativeMjIsBoundedSimulatedAndActuallyAcceleratesCrafting(TestContext c) {
        var tile=bench(c);pattern(tile,0,new ItemStack(Items.OAK_LOG));insert(c,tile,Items.OAK_LOG,1);
        var input=(IMjReceiver)MjAPI.CONNECTOR.find(c.getWorld(),tile.getPos(),Direction.UP);
        c.assertTrue(input.receivePower(60*MjAPI.MJ,true)==20*MjAPI.MJ && tile.getStoredPower()==0,"MJ simulation changed crafting progress");
        c.assertTrue(input.receivePower(60*MjAPI.MJ,false)==20*MjAPI.MJ && input.getPowerRequested()==0,"MJ input exceeded 40 MJ capacity");
        tile.tick();c.assertTrue(count(tile,Items.OAK_LOG)==0 && tile.invResult.getStackInSlot(0).getCount()==4,"Stored MJ was not used for crafting");
        var player=c.getWorld().getServer().getPlayerManager().getPlayer(tile.getOwner().getId());
        var advancement=c.getWorld().getServer().getAdvancementLoader().get(new Identifier("buildcraftfactory:lazy_crafting"));
        c.assertTrue(advancement!=null && player.getAdvancementTracker().getProgress(advancement).isDone(),"Native automatic-crafting advancement missing");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void missingIngredientsAndFullOutputRollbackEveryInventoryChange(TestContext c) {
        var tile=bench(c);pattern(tile,0,new ItemStack(Items.OAK_PLANKS));pattern(tile,3,new ItemStack(Items.OAK_PLANKS));
        insert(c,tile,Items.OAK_PLANKS,1);power(tile,40*MjAPI.MJ);tile.tick();
        c.assertTrue(count(tile,Items.OAK_PLANKS)==1 && tile.invResult.getStackInSlot(0).isEmpty() && tile.getStoredPower()==38*MjAPI.MJ,"Missing second ingredient consumed a partial craft");
        insert(c,tile,Items.OAK_PLANKS,1);tile.invResult.setStackInSlot(0,new ItemStack(Items.STICK,63));tile.tick();
        c.assertTrue(count(tile,Items.OAK_PLANKS)==2 && tile.invResult.getStackInSlot(0).getCount()==63,"Full output caused a partial craft or spill");
        tile.invResult.setStackInSlot(0,ItemStack.EMPTY);power(tile,4*MjAPI.MJ);tile.tick();
        c.assertTrue(count(tile,Items.OAK_PLANKS)==0 && tile.invResult.getStackInSlot(0).getCount()==4,"Unblocked craft did not resume");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void duplicateNbtIngredientsStayExactAndDynamicRepairUsesRealRecipe(TestContext c) {
        var tile=bench(c);var first=new ItemStack(Items.IRON_SWORD);first.setDamage(120);
        var second=new ItemStack(Items.IRON_SWORD);second.setDamage(170);
        pattern(tile,0,first);pattern(tile,1,second);
        tile.invMaterials.setStackInSlot(0,first.copy());tile.invMaterials.setStackInSlot(1,first.copy());
        power(tile,40*MjAPI.MJ);tile.tick();
        c.assertTrue(tile.invResult.getStackInSlot(0).isEmpty() && tile.invMaterials.getStackInSlot(0).getDamage()==120,"Mismatched NBT satisfied an exact ingredient");
        tile.invMaterials.setStackInSlot(1,second.copy());power(tile,2*MjAPI.MJ);tile.tick();
        var repaired=tile.invResult.getStackInSlot(0);
        c.assertTrue(repaired.isOf(Items.IRON_SWORD) && repaired.getCount()==1 && repaired.getDamage()==28
            && count(tile,Items.IRON_SWORD)==0,"Dynamic vanilla repair did not preserve its calculated durability");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void cakeReturnsThreeBucketsThroughNativeInventoryWithoutDuplicatingBlueprint(TestContext c) {
        var tile=bench(c);cakePattern(tile);c.setBlockState(POS.west(),Blocks.BARREL);
        insert(c,tile,Items.MILK_BUCKET,3);insert(c,tile,Items.SUGAR,2);insert(c,tile,Items.EGG,1);insert(c,tile,Items.WHEAT,3);
        power(tile,40*MjAPI.MJ);tile.tick();
        c.assertTrue(tile.invResult.getStackInSlot(0).isOf(Items.CAKE) && tile.invResult.getStackInSlot(0).getCount()==1
            && tile.invMaterials.stacks.stream().allMatch(ItemStack::isEmpty),"Cake ingredients were not consumed exactly once");
        var bank=(BarrelBlockEntity)c.getBlockEntity(POS.west());int buckets=0;
        for(int i=0;i<bank.size();i++)if(bank.getStack(i).isOf(Items.BUCKET))buckets+=bank.getStack(i).getCount();
        c.assertTrue(buckets==3 && tile.invBlueprint.getStackInSlot(0).isOf(Items.MILK_BUCKET),"Recipe remainders were lost or blueprint consumed");
        c.setBlockState(POS,Blocks.AIR);tile.onRemove();
        var drops=c.getWorld().getEntitiesByClass(net.minecraft.entity.ItemEntity.class,new Box(tile.getPos()).expand(1),e->true);
        c.assertTrue(drops.stream().noneMatch(e->e.getStack().isOf(Items.MILK_BUCKET) || e.getStack().isOf(Items.SUGAR)),
            "Removing the workbench dropped phantom ingredients");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void filteredNativeStorageRejectsWrongVariantsAndProtectsInputExtraction(TestContext c) {
        var tile=bench(c);pattern(tile,0,new ItemStack(Items.OAK_LOG));
        var storage=ItemStorage.SIDED.find(c.getWorld(),tile.getPos(),Direction.NORTH);
        try(var tx=Transaction.openOuter()) {
            c.assertTrue(storage.insert(ItemVariant.of(Items.SPRUCE_LOG),20,tx)==0 && storage.insert(ItemVariant.of(Items.OAK_LOG),20,tx)==20
                && storage.extract(ItemVariant.of(Items.OAK_LOG),1,tx)==0,"Sided filter/access rules incorrect");
        }
        c.assertTrue(count(tile,Items.OAK_LOG)==0,"Aborted native insertion leaked into machine");
        insert(c,tile,Items.OAK_LOG,2);pattern(tile,0,new ItemStack(Items.SPRUCE_LOG));
        c.assertTrue(count(tile,Items.OAK_LOG)==2 && tile.getStoredPower()==0,"Recipe editing discarded real inventory or kept unrelated progress");
        insert(c,tile,Items.SPRUCE_LOG,1);power(tile,40*MjAPI.MJ);tile.tick();
        c.assertTrue(tile.invResult.getStackInSlot(0).isOf(Items.SPRUCE_PLANKS) && count(tile,Items.OAK_LOG)==2,"New recipe consumed unrelated old materials");
        try(var tx=Transaction.openOuter()){c.assertTrue(storage.extract(ItemVariant.of(Items.SPRUCE_PLANKS),4,tx)==4,"Native output cannot be extracted");tx.commit();}
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void savedProgressAndPatternSurviveWorldAttachmentAndStaleHandlesStop(TestContext c) {
        var tile=bench(c);pattern(tile,0,new ItemStack(Items.OAK_LOG));insert(c,tile,Items.OAK_LOG,1);power(tile,10*MjAPI.MJ);tile.tick();
        long paid=tile.getStoredPower();var stale=(IMjReceiver)MjAPI.CONNECTOR.find(c.getWorld(),tile.getPos(),Direction.UP);
        var storage=ItemStorage.SIDED.find(c.getWorld(),tile.getPos(),Direction.NORTH);
        var copy=BlockEntityRegistry.create(TileAutoWorkbenchItems.class,tile.getPos(),tile.getCachedState());copy.readNbt(tile.createNbt());
        c.getWorld().removeBlockEntity(tile.getPos());c.getWorld().addBlockEntity(copy);copy.onLoad();copy.tick();
        c.assertTrue(copy.getStoredPower()==paid+MjAPI.MJ/5 && count(copy,Items.OAK_LOG)==1,"Loading before world attachment lost saved paid work");
        c.assertTrue(stale.receivePower(MjAPI.MJ,false)==MjAPI.MJ,"Removed workbench accepted energy");
        try(var tx=Transaction.openOuter()){c.assertTrue(storage.insert(ItemVariant.of(Items.OAK_LOG),1,tx)==0,"Removed workbench accepted material");}
        power(copy,TileAutoWorkbenchBase.POWER_REQUIRED-copy.getStoredPower());copy.tick();
        c.assertTrue(copy.invResult.getStackInSlot(0).getCount()==4 && count(copy,Items.OAK_LOG)==0,"Reloaded craft duplicated or lost output");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void recipeReloadReplacesCachedResultAndNeverCraftsRemovedRecipe(TestContext c) {
        var tile=bench(c);pattern(tile,0,new ItemStack(Items.OAK_LOG));insert(c,tile,Items.OAK_LOG,1);power(tile,10*MjAPI.MJ);
        var manager=c.getWorld().getRecipeManager();var saved=new ArrayList<Recipe<?>>(manager.values());
        var id=new Identifier("minecraft:oak_planks");
        try {
            var changed=new ArrayList<Recipe<?>>(saved.stream().filter(recipe->!recipe.getId().equals(id)).toList());
            changed.add(new ShapelessRecipe(id,"",CraftingRecipeCategory.MISC,new ItemStack(Items.DIAMOND),DefaultedList.copyOf(Ingredient.EMPTY,Ingredient.ofItems(Items.OAK_LOG))));
            manager.setRecipes(changed);tile.tick();
            c.assertTrue(tile.getRecipePreview().isOf(Items.DIAMOND) && tile.getStoredPower()==MjAPI.MJ/5,"Recipe reload kept old output or old pattern progress");
            manager.setRecipes(changed.stream().filter(recipe->!recipe.getId().equals(id)).toList());tile.tick();
            c.assertTrue(tile.getRecipePreview().isEmpty() && count(tile,Items.OAK_LOG)==1 && tile.invResult.getStackInSlot(0).isEmpty(),"Removed recipe continued crafting");
        } finally {manager.setRecipes(saved);}
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativeGhostClicksAndShiftClicksNeverYieldPhantomItems(TestContext c) {
        var tile=bench(c);var player=nearPlayer(c,tile);var handler=new ContainerAutoCraftItems(player,7,tile);
        handler.setCursorStack(new ItemStack(Items.OAK_LOG,12));
        handler.onSlotClick(1,0,SlotActionType.PICKUP,player);
        c.assertTrue(handler.getCursorStack().getCount()==12 && tile.invBlueprint.getStackInSlot(0).getCount()==1,"Ghost click consumed cursor stack");
        handler.setCursorStack(ItemStack.EMPTY);
        handler.quickMove(player,1);handler.onSlotClick(1,0,SlotActionType.THROW,player);
        c.assertTrue(handler.getCursorStack().isEmpty() && tile.invBlueprint.getStackInSlot(0).getCount()==1,"Ghost slot could be extracted");
        player.getInventory().setStack(9,new ItemStack(Items.OAK_LOG,5));handler.quickMove(player,ContainerAutoCraftItems.PLAYER_START);
        c.assertTrue(count(tile,Items.OAK_LOG)==5 && player.getInventory().getStack(9).isEmpty(),"Shift-click failed to fill filtered material storage");
        handler.onSlotClick(10,0,SlotActionType.PICKUP,player);
        c.assertTrue(tile.invMaterialFilter.getStackInSlot(0).isOf(Items.OAK_LOG),"Derived filter was edited by native click");
        handler.onClosed(player);c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void recipeSelectionAndBlueprintPacketsAreAtomicAndRejectDerivedSlots(TestContext c) throws Exception {
        var tile=bench(c);var player=nearPlayer(c,tile);var handler=new ContainerAutoCraftItems(player,8,tile);
        var recipe=c.getWorld().getRecipeManager().get(new Identifier("minecraft:stick")).orElseThrow();player.unlockRecipes(List.of(recipe));
        handler.selectRecipe(recipe.getId());c.assertTrue(tile.getRecipePreview().isOf(Items.STICK),"Unlocked native recipe selection failed");
        var saved=tile.invBlueprint.serializeNBT();var packet=PacketBufferBC.write(b->{b.writeByte(2);b.writeVarInt(1);b.writeItemStack(new ItemStack(Items.DIAMOND));b.writeVarInt(10);b.writeItemStack(new ItemStack(Items.DIAMOND));});
        try {
            boolean rejected=false;try{handler.readMessage(handler.NET_SET_PHANTOM_MULTI,packet,false,null);}catch(java.io.IOException expected){rejected=true;}
            c.assertTrue(rejected && saved.equals(tile.invBlueprint.serializeNBT()),"Invalid multi-slot packet partially edited blueprint or derived filters");
        } finally{packet.release();}
        var valid=PacketBufferBC.write(b->{b.writeVarInt(1);b.writeItemStack(new ItemStack(Items.OAK_LOG,64));});
        try{handler.readMessage(handler.NET_SET_PHANTOM,valid,false,null);}finally{valid.release();}
        c.assertTrue(tile.invBlueprint.getStackInSlot(0).getCount()==1,"Ghost packet retained an arbitrary count");
        c.setBlockState(POS,Blocks.AIR);handler.selectRecipe(new Identifier("minecraft:oak_planks"));
        c.assertTrue(!handler.canUse(player),"Removed workbench retained a usable screen");handler.onClosed(player);c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=200)
    public void nativeWoodenPipeExtractsOnlyCraftedOutput(TestContext c) {
        var tile=bench(c);pattern(tile,0,new ItemStack(Items.OAK_LOG));insert(c,tile,Items.OAK_LOG,2);power(tile,40*MjAPI.MJ);tile.tick();
        c.setBlockState(POS.west(),BCTransportBlocks.pipeHolder);
        var pipe=(TilePipeHolder)c.getBlockEntity(POS.west());pipe.onPlacedBy(null,new ItemStack(BCTransportItems.pipeItemWood));
        c.setBlockState(POS.west(2),BCTransportBlocks.pipeHolder);((TilePipeHolder)c.getBlockEntity(POS.west(2))).onPlacedBy(null,new ItemStack(BCTransportItems.pipeItemStone));
        c.setBlockState(POS.west(3),Blocks.BARREL);pipe.tick();
        var receiver=(IMjReceiver)MjAPI.CONNECTOR.find(c.getWorld(),pipe.getPos(),Direction.UP);receiver.receivePower(8*MjAPI.MJ,false);
        c.waitAndRun(90,()->{
            var barrel=(BarrelBlockEntity)c.getBlockEntity(POS.west(3));int planks=0;for(int i=0;i<barrel.size();i++)if(barrel.getStack(i).isOf(Items.OAK_PLANKS))planks+=barrel.getStack(i).getCount();
            c.assertTrue(planks==4 && count(tile,Items.OAK_LOG)==1,"Native pipe extracted ghost/input items or lost crafted output");c.complete();
        });
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void survivalWorkbenchRecipeConsumesTwoGearsAndCraftingTable(TestContext c) {
        var player=c.createMockCreativeServerPlayerInWorld();player.changeGameMode(GameMode.SURVIVAL);
        var handler=new net.minecraft.screen.CraftingScreenHandler(1,player.getInventory(),net.minecraft.screen.ScreenHandlerContext.create(c.getWorld(),c.getAbsolutePos(POS)));
        Item gear=net.minecraft.registry.Registries.ITEM.get(new Identifier("buildcraftcore:gear_stone"));
        handler.getSlot(1).setStack(new ItemStack(gear));handler.getSlot(2).setStack(new ItemStack(Items.CRAFTING_TABLE));handler.getSlot(3).setStack(new ItemStack(gear));
        handler.onContentChanged(handler.getSlot(1).inventory);
        c.assertTrue(handler.getSlot(0).getStack().isOf(BCFactoryBlocks.autoWorkbenchItems.asItem()),"Native automatic-workbench recipe missing");
        handler.onSlotClick(0,0,SlotActionType.PICKUP,player);
        for(int i=1;i<=9;i++)c.assertTrue(handler.getSlot(i).getStack().isEmpty(),"Workbench recipe did not consume original ingredients");c.complete();
    }

    private static TileAutoWorkbenchItems bench(TestContext c) {
        c.setBlockState(POS,BCFactoryBlocks.autoWorkbenchItems);var tile=(TileAutoWorkbenchItems)c.getBlockEntity(POS);
        tile.onLoad();tile.onPlacedBy(c.createMockCreativeServerPlayerInWorld(),new ItemStack(BCFactoryBlocks.autoWorkbenchItems));return tile;
    }
    private static net.minecraft.server.network.ServerPlayerEntity nearPlayer(TestContext c,TileAutoWorkbenchItems tile) {
        var player=c.createMockCreativeServerPlayerInWorld();player.changeGameMode(GameMode.SURVIVAL);
        var center=Vec3d.ofCenter(tile.getPos());player.refreshPositionAndAngles(center.x,center.y,center.z-2,0,0);return player;
    }
    private static void pattern(TileAutoWorkbenchItems tile,int slot,ItemStack stack){tile.invBlueprint.setStackInSlot(slot,stack.copyWithCount(1));}
    private static void power(TileAutoWorkbenchItems tile,long amount) {
        var receiver=(IMjReceiver)MjAPI.CONNECTOR.find(tile.getWorld(),tile.getPos(),Direction.UP);
        if(receiver==null || receiver.receivePower(amount,false)!=0)throw new AssertionError("Workbench rejected fixture MJ");
    }
    private static void insert(TestContext c,TileAutoWorkbenchItems tile,Item item,int count) {
        var storage=ItemStorage.SIDED.find(c.getWorld(),tile.getPos(),Direction.UP);
        try(var tx=Transaction.openOuter()){c.assertTrue(storage!=null && storage.insert(ItemVariant.of(item),count,tx)==count,"Filtered native insertion failed for "+item);tx.commit();}
    }
    private static int count(TileAutoWorkbenchItems tile,Item item){return tile.invMaterials.stacks.stream().filter(s->s.isOf(item)).mapToInt(ItemStack::getCount).sum();}
    private static void cakePattern(TileAutoWorkbenchItems tile) {
        Item[] items={Items.MILK_BUCKET,Items.MILK_BUCKET,Items.MILK_BUCKET,Items.SUGAR,Items.EGG,Items.SUGAR,Items.WHEAT,Items.WHEAT,Items.WHEAT};
        for(int i=0;i<9;i++)pattern(tile,i,new ItemStack(items[i]));
    }
}
