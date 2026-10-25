/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;

import buildcraft.transport.*;
import buildcraft.transport.container.ContainerDiamondPipe;
import buildcraft.transport.item.ItemPipeHolder;
import buildcraft.transport.pipe.behaviour.PipeBehaviourDiamond;
import buildcraft.transport.pipe.flow.PipeFlowItems;
import buildcraft.transport.tile.TilePipeHolder;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public final class PipeFilterGameTests implements FabricGameTest {
    private static final BlockPos LOCAL = new BlockPos(3, 3, 3);

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void nativeFilterScreenKeepsGhostsSeparateFromRealInventory(TestContext context) {
        var tile = place(context, BCTransportItems.pipeItemDiamond);
        var player = context.createMockCreativeServerPlayerInWorld();
        player.refreshPositionAndAngles(tile.getPos(), 0, 0);
        BCTransportGuis.PIPE_DIAMOND.openGui(player, tile.getPos());
        context.assertTrue(player.currentScreenHandler instanceof ContainerDiamondPipe, "Native diamond filter screen did not open");
        var screen = (ContainerDiamondPipe) player.currentScreenHandler;
        context.assertTrue(screen.getType() == BCTransportScreens.DIAMOND && screen.slots.size() == 90, "Wrong filter handler type/slot count");
        var filter = (PipeBehaviourDiamond) tile.getPipe().behaviour;
        var named = new ItemStack(Items.DIAMOND, 7);
        named.setCustomName(Text.literal("Keep this NBT"));
        screen.setCursorStack(named);
        int slot = Direction.EAST.ordinal() * 9;
        screen.onSlotClick(slot, 0, SlotActionType.PICKUP, player);
        screen.onSlotClick(slot, 0, SlotActionType.PICKUP, player);
        context.assertTrue(screen.getCursorStack().getCount() == 7 && filter.filters.getStackInSlot(slot).getCount() == 2,
            "Editing a weighted filter consumed or duplicated cursor items");
        context.assertTrue(ItemStack.canCombine(filter.filters.getStackInSlot(slot), named), "Filter lost stack NBT");
        for (var action : new SlotActionType[] {SlotActionType.QUICK_MOVE, SlotActionType.SWAP, SlotActionType.CLONE,
                SlotActionType.THROW, SlotActionType.PICKUP_ALL, SlotActionType.QUICK_CRAFT}) {
            screen.onSlotClick(slot, 0, action, player);
            context.assertTrue(filter.filters.getStackInSlot(slot).getCount() == 2 && screen.getCursorStack().getCount() == 7, "Unsafe filter click: " + action);
        }
        player.getInventory().setStack(9, new ItemStack(Items.EMERALD, 4));
        context.assertTrue(screen.quickMove(player, 54).isEmpty() && player.getInventory().getStack(9).getCount() == 4,
            "Shift-click moved real items into a phantom filter");
        screen.setCursorStack(ItemStack.EMPTY);
        screen.onSlotClick(slot, 0, SlotActionType.PICKUP, player);
        context.assertTrue(filter.filters.getStackInSlot(slot).isEmpty(), "Empty-hand click did not clear filter");
        screen.setCursorStack(named);
        player.refreshPositionAndAngles(tile.getPos().add(20, 0, 0), 0, 0);
        screen.onSlotClick(slot, 0, SlotActionType.PICKUP, player);
        context.assertTrue(filter.filters.getStackInSlot(slot).isEmpty() && !screen.canUse(player), "Distant player changed filters");
        player.refreshPositionAndAngles(tile.getPos(), 0, 0);
        tile.readNbt(tile.createNbt());
        context.assertTrue(!screen.canUse(player), "A stale pipe instance retained screen access");
        screen.setCursorStack(ItemStack.EMPTY);
        player.closeHandledScreen();
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void filtersPersistForEveryFaceAndDoNotDropRealItems(TestContext context) {
        var tile = place(context, BCTransportItems.pipeItemDiamond);
        var behaviour = (PipeBehaviourDiamond) tile.getPipe().behaviour;
        for (var face : Direction.values()) {
            context.assertTrue(behaviour.getTextureIndex(face) == face.ordinal() + 1, "Missing coloured face texture");
            for (int slot = 0; slot < 9; slot++) {
                var stack = new ItemStack(Items.DIAMOND, slot + 1);
                stack.setCustomName(Text.literal(face.getName()));
                behaviour.filters.setStackInSlot(face.ordinal() * 9 + slot, stack);
            }
        }
        var saved = tile.createNbt();
        var copy = BCTransportBlocks.pipeHolderTile.instantiate(tile.getPos(), tile.getCachedState());
        copy.readNbt(saved);
        var loaded = (PipeBehaviourDiamond) copy.getPipe().behaviour;
        for (int i = 0; i < 54; i++) context.assertTrue(ItemStack.areEqual(behaviour.filters.getStackInSlot(i), loaded.filters.getStackInSlot(i)),
            "Native unattached load lost filter " + i);
        ((PipeFlowItems) tile.getPipe().flow).insertItemsForce(new ItemStack(Items.EMERALD, 3), Direction.WEST, null, 0.08);
        context.getWorld().breakBlock(tile.getPos(), true);
        int pipes = 0, emeralds = 0, diamonds = 0;
        for (var entity : context.getWorld().getEntitiesByClass(net.minecraft.entity.ItemEntity.class, new net.minecraft.util.math.Box(tile.getPos()).expand(1), e -> true)) {
            var stack = entity.getStack();
            if (stack.isOf(BCTransportItems.pipeItemDiamond)) pipes += stack.getCount();
            if (stack.isOf(Items.EMERALD)) emeralds += stack.getCount();
            if (stack.isOf(Items.DIAMOND)) diamonds += stack.getCount();
        }
        context.assertTrue(pipes == 1 && emeralds == 3 && diamonds == 0, "Breaking a pipe lost contents or materialized phantom filters");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 220)
    public void matchingFiltersSplitByWeightAndUnmatchedItemsUseTheOpenSide(TestContext context) {
        var tile = place(context, BCTransportItems.pipeItemDiamond);
        for (var face : new Direction[] {Direction.EAST, Direction.NORTH, Direction.SOUTH})
            context.getWorld().setBlockState(tile.getPos().offset(face), Blocks.BARREL.getDefaultState());
        var filter = (PipeBehaviourDiamond) tile.getPipe().behaviour;
        var named = new ItemStack(Items.DIAMOND);
        named.setCustomName(Text.literal("Weighted payload"));
        filter.filters.setStackInSlot(Direction.EAST.ordinal() * 9, named.copyWithCount(2));
        filter.filters.setStackInSlot(Direction.NORTH.ordinal() * 9, named.copyWithCount(1));
        tile.tick();
        var flow = (PipeFlowItems) tile.getPipe().flow;
        flow.insertItemsForce(named.copyWithCount(12), Direction.WEST, null, 0.08);
        flow.insertItemsForce(new ItemStack(Items.EMERALD, 5), Direction.WEST, null, 0.08);
        context.waitAndRun(150, () -> {
            var east = inventory(context, tile.getPos().east());
            var north = inventory(context, tile.getPos().north());
            var south = inventory(context, tile.getPos().south());
            context.assertTrue(count(east, Items.DIAMOND) == 8 && count(north, Items.DIAMOND) == 4, "2:1 weighted routing failed");
            context.assertTrue(count(south, Items.DIAMOND) == 0 && count(south, Items.EMERALD) == 5, "Filtered/open-side priority failed");
            context.assertTrue(ItemStack.canCombine(east.getStack(0), named) && ItemStack.canCombine(north.getStack(0), named), "Routing lost NBT");
            context.assertTrue(flow.writeToNbt().getList("items", net.minecraft.nbt.NbtElement.COMPOUND_TYPE).isEmpty(), "Delivered items remained in the pipe");
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void fillingFilterRowAwardsOwnerAndUnlocksNativeListRecipe(TestContext context) {
        var tile = place(context, BCTransportItems.pipeItemDiamond);
        var player = context.createMockCreativeServerPlayerInWorld();
        tile.onPlacedBy(player, new ItemStack(BCTransportItems.pipeItemDiamond));
        var filter = (PipeBehaviourDiamond) tile.getPipe().behaviour;
        for (int slot = 0; slot < 7; slot++) filter.filters.setStackInSlot(slot, new ItemStack(Items.DIAMOND));
        var advancement = context.getWorld().getServer().getAdvancementLoader().get(PipeBehaviourDiamond.ADVANCEMENT_NEED_LIST);
        context.assertTrue(advancement != null && player.getAdvancementTracker().getProgress(advancement).isDone(), "Filter advancement not awarded");
        context.assertTrue(context.getWorld().getRecipeManager().get(new net.minecraft.util.Identifier("buildcraftcore", "list")).isPresent(), "Advancement reward recipe missing");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void specialPipeControlsRequireWrenchAndPreserveTheirSetting(TestContext context) {
        var player = context.createMockCreativeServerPlayerInWorld();
        for (var item : new ItemPipeHolder[] {BCTransportItems.pipeItemLapis, BCTransportItems.pipeItemDaizuli, BCTransportItems.pipePowerIron, BCTransportItems.pipeRfDiamond}) {
            var tile = place(context, item);
            player.refreshPositionAndAngles(tile.getPos(), 0, 0);
            var behaviour = tile.getPipe().behaviour;
            var hit = new net.minecraft.util.hit.BlockHitResult(net.minecraft.util.math.Vec3d.ofCenter(tile.getPos()), Direction.UP, tile.getPos(), false);
            player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
            var before = behaviour.writeToNbt();
            context.assertTrue(!behaviour.onPipeActivate(player, hit, 0.5f, 0.5f, 0.5f, buildcraft.api.core.EnumPipePart.CENTER), "Bare-hand interaction changed a wrench control");
            context.assertTrue(before.equals(behaviour.writeToNbt()), "Bare-hand control mutated state");
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(buildcraft.core.BCCoreItems.wrench));
            context.assertTrue(behaviour.onPipeActivate(player, hit, 0.5f, 0.5f, 0.5f, buildcraft.api.core.EnumPipePart.CENTER), "Wrench rejected");
            context.assertTrue(!before.equals(behaviour.writeToNbt()), "Wrench left setting unchanged");
            var copy = BCTransportBlocks.pipeHolderTile.instantiate(tile.getPos(), tile.getCachedState());
            copy.readNbt(tile.createNbt());
            context.assertTrue(copy.getPipe().behaviour.writeToNbt().equals(behaviour.writeToNbt()), "Control setting did not survive native NBT load");
            if (item == BCTransportItems.pipeItemLapis || item == BCTransportItems.pipeItemDaizuli) {
                player.setSneaking(true);
                behaviour.onPipeActivate(player, hit, 0.5f, 0.5f, 0.5f, buildcraft.api.core.EnumPipePart.CENTER);
                player.setSneaking(false);
                context.assertTrue(before.equals(behaviour.writeToNbt()), "Sneaking did not reverse colour selection");
            }
            context.getWorld().removeBlock(tile.getPos(), false);
        }
        context.complete();
    }


    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 220)
    public void lapisColoursItemsAndDaizuliRoutesMatchingAndOtherColours(TestContext context) {
        var daizuli = place(context, BCTransportItems.pipeItemDaizuli);
        var saved = daizuli.createNbt();
        saved.getCompound("pipe").getCompound("beh").putString("currentDir", "EAST");
        saved.getCompound("pipe").getCompound("beh").putString("colour", "BLUE");
        daizuli.readNbt(saved);
        context.setBlockState(LOCAL.west(), BCTransportBlocks.pipeHolder.getDefaultState());
        var lapis = (TilePipeHolder) context.getBlockEntity(LOCAL.west());
        lapis.onPlacedBy(null, new ItemStack(BCTransportItems.pipeItemLapis));
        var lapisSaved = lapis.createNbt();
        lapisSaved.getCompound("pipe").getCompound("beh").putString("colour", "BLUE");
        lapis.readNbt(lapisSaved);
        context.setBlockState(LOCAL.east(), Blocks.BARREL.getDefaultState());
        context.setBlockState(LOCAL.north(), Blocks.BARREL.getDefaultState());
        lapis.tick(); daizuli.tick();
        ((PipeFlowItems) lapis.getPipe().flow).insertItemsForce(new ItemStack(Items.DIAMOND, 4), Direction.WEST, null, 0.08);
        ((PipeFlowItems) daizuli.getPipe().flow).insertItemsForce(new ItemStack(Items.EMERALD, 2), Direction.WEST, net.minecraft.util.DyeColor.RED, 0.08);
        context.waitAndRun(150, () -> {
            context.assertTrue(count(inventory(context, daizuli.getPos().east()), Items.DIAMOND) == 4, "Lapis-coloured items did not follow the matching Daizuli output");
            context.assertTrue(count(inventory(context, daizuli.getPos().north()), Items.EMERALD) == 2, "Nonmatching colour used the selected Daizuli output");
            context.assertTrue(count(inventory(context, daizuli.getPos().east()), Items.EMERALD) == 0
                && count(inventory(context, daizuli.getPos().north()), Items.DIAMOND) == 0, "Colour routing crossed outputs");
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 220)
    public void ironPipeWrenchRedirectsRealDeliveries(TestContext context) {
        var tile = place(context, BCTransportItems.pipeItemIron);
        context.setBlockState(LOCAL.east(), Blocks.BARREL.getDefaultState());
        context.setBlockState(LOCAL.north(), Blocks.BARREL.getDefaultState());
        var saved = tile.createNbt();
        saved.getCompound("pipe").getCompound("beh").putString("currentDir", "EAST");
        tile.readNbt(saved);
        tile.tick();
        var flow = (PipeFlowItems) tile.getPipe().flow;
        flow.insertItemsForce(new ItemStack(Items.DIAMOND, 3), Direction.WEST, null, 0.08);
        context.waitAndRun(70, () -> {
            context.assertTrue(count(inventory(context, tile.getPos().east()), Items.DIAMOND) == 3, "Iron pipe ignored its initial output");
            var player = context.createMockCreativeServerPlayerInWorld();
            player.refreshPositionAndAngles(tile.getPos(), 0, 0);
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(buildcraft.core.BCCoreItems.wrench));
            var hit = new net.minecraft.util.hit.BlockHitResult(net.minecraft.util.math.Vec3d.ofCenter(tile.getPos()), Direction.UP, tile.getPos(), false);
            tile.getPipe().behaviour.onPipeActivate(player, hit, 0.5f, 0.5f, 0.5f, buildcraft.api.core.EnumPipePart.CENTER);
            context.assertTrue("NORTH".equals(tile.getPipe().behaviour.writeToNbt().getString("currentDir")), "Iron wrench did not select the other connected output");
            flow.insertItemsForce(new ItemStack(Items.EMERALD, 2), Direction.WEST, null, 0.08);
        });
        context.waitAndRun(150, () -> {
            context.assertTrue(count(inventory(context, tile.getPos().north()), Items.EMERALD) == 2
                && count(inventory(context, tile.getPos().east()), Items.EMERALD) == 0, "Wrench rotation did not redirect delivery");
            context.complete();
        });
    }

    private static TilePipeHolder place(TestContext context, ItemPipeHolder item) {
        context.setBlockState(LOCAL, BCTransportBlocks.pipeHolder.getDefaultState());
        var tile = (TilePipeHolder) context.getBlockEntity(LOCAL);
        tile.onPlacedBy(null, new ItemStack(item));
        return tile;
    }
    private static Inventory inventory(TestContext context, BlockPos pos) { return (Inventory) context.getWorld().getBlockEntity(pos); }
    private static int count(Inventory inv, net.minecraft.item.Item item) {
        int result = 0; for (int i = 0; i < inv.size(); i++) if (inv.getStack(i).isOf(item)) result += inv.getStack(i).getCount(); return result;
    }
}
