/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;

import buildcraft.api.mj.MjAPI;
import buildcraft.transport.*;
import buildcraft.transport.container.ContainerDiamondWoodPipe;
import buildcraft.transport.pipe.behaviour.PipeBehaviourWoodDiamond;
import buildcraft.transport.pipe.behaviour.PipeBehaviourWoodDiamond.FilterMode;
import buildcraft.transport.tile.TilePipeHolder;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtElement;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public final class WoodDiamondGameTests implements FabricGameTest {
    private static final BlockPos POS = new BlockPos(3, 3, 3);

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 220)
    public void whiteAndBlackListsUseExactNbtAndDeliverOneItemPerPulse(TestContext context) {
        var tile = circuit(context);
        var source = inventory(context, POS.west());
        var behaviour = (PipeBehaviourWoodDiamond) tile.getPipe().behaviour;
        var named = new ItemStack(Items.DIAMOND, 4);
        named.setCustomName(Text.literal("Filtered diamond"));
        source.setStack(0, named.copy());
        source.setStack(1, new ItemStack(Items.DIAMOND, 2));
        source.setStack(2, new ItemStack(Items.EMERALD, 3));
        behaviour.filters.setStackInSlot(0, named.copyWithCount(1));
        context.assertTrue(behaviour.getPowerRequested() == MjAPI.MJ, "Filtered pipe requested more than one item's power");
        context.assertTrue(behaviour.receivePower(10 * MjAPI.MJ, true) == 9 * MjAPI.MJ && total(source) == 9 && contents(tile) == 0,
            "Simulation changed contents or charged the wrong amount");
        context.assertTrue(behaviour.receivePower(MjAPI.MJ - 1, false) == MjAPI.MJ - 1 && total(source) == 9, "Insufficient power extracted an item");
        context.assertTrue(behaviour.receivePower(10 * MjAPI.MJ, false) == 9 * MjAPI.MJ && source.getStack(0).getCount() == 3,
            "Whitelist did not extract exactly one named diamond");
        behaviour.setFilterMode(FilterMode.BLACK_LIST);
        behaviour.receivePower(MjAPI.MJ, false);
        context.assertTrue(source.getStack(0).getCount() == 3 && source.getStack(1).getCount() == 1, "Blacklist ignored NBT or extracted a forbidden item");
        behaviour.filters.setStackInSlot(0, ItemStack.EMPTY);
        behaviour.setFilterMode(FilterMode.WHITE_LIST);
        behaviour.receivePower(MjAPI.MJ, false);
        context.assertTrue(source.getStack(0).getCount() == 2 && total(source) == 6 && contents(tile) == 3, "Empty whitelist did not allow all items");
        context.waitAndRun(170, () -> {
            var target = inventory(context, POS.east(2));
            int namedCount = 0;
            for (int i = 0; i < target.size(); i++) if (ItemStack.canCombine(target.getStack(i), named)) namedCount += target.getStack(i).getCount();
            context.assertTrue(total(target) == 3 && namedCount == 2 && total(source) == 6 && contents(tile) == 0,
                "Filtered delivery lost, duplicated, or changed item NBT");
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 220)
    public void roundRobinWaitsForMissingItemsAndSimulationNeverAdvances(TestContext context) {
        var tile = circuit(context);
        var source = inventory(context, POS.west());
        source.setStack(0, new ItemStack(Items.DIAMOND, 2));
        var behaviour = (PipeBehaviourWoodDiamond) tile.getPipe().behaviour;
        behaviour.filters.setStackInSlot(2, new ItemStack(Items.DIAMOND));
        behaviour.filters.setStackInSlot(6, new ItemStack(Items.EMERALD));
        behaviour.setFilterMode(FilterMode.ROUND_ROBIN);
        for (int i = 0; i < 30; i++) {
            context.assertTrue(behaviour.getPowerRequested() == MjAPI.MJ && behaviour.receivePower(MjAPI.MJ, true) == 0,
                "Round-robin simulation did not find the current filter");
        }
        context.assertTrue(behaviour.currentFilter == 2 && total(source) == 2 && contents(tile) == 0, "Power queries advanced the cursor or moved items");
        behaviour.receivePower(MjAPI.MJ, false);
        context.assertTrue(behaviour.currentFilter == 6 && behaviour.getPowerRequested() == 0, "Round robin skipped the missing emerald");
        context.assertTrue(behaviour.receivePower(5 * MjAPI.MJ, false) == 5 * MjAPI.MJ && total(source) == 1 && contents(tile) == 1,
            "Blocked round robin consumed power or extracted a different item");
        source.setStack(1, new ItemStack(Items.EMERALD, 2));
        behaviour.receivePower(MjAPI.MJ, false);
        context.assertTrue(behaviour.currentFilter == 2 && total(source) == 2 && contents(tile) == 2, "Successful extraction did not wrap to the next nonempty filter");
        var copy = BCTransportBlocks.pipeHolderTile.instantiate(tile.getPos(), tile.getCachedState());
        copy.readNbt(tile.createNbt());
        var loaded = (PipeBehaviourWoodDiamond) copy.getPipe().behaviour;
        context.assertTrue(loaded.filterMode == FilterMode.ROUND_ROBIN && loaded.currentFilter == 2 && loaded.filterValid
            && "WEST".equals(loaded.writeToNbt().getString("currentDir")), "Reload lost round-robin mode, cursor, or extraction face");
        context.getWorld().removeBlockEntity(tile.getPos());
        context.getWorld().addBlockEntity(copy);
        copy.tick();
        context.assertTrue(loaded.receivePower(MjAPI.MJ, false) == 0 && loaded.currentFilter == 6 && total(source) == 1,
            "Reloaded extractor did not resume at the saved cursor");
        loaded.filters.setStackInSlot(6, ItemStack.EMPTY);
        context.assertTrue(loaded.currentFilter == 2 && loaded.filterValid, "Removing current filter did not choose the remaining filter");
        loaded.filters.setStackInSlot(2, ItemStack.EMPTY);
        context.assertTrue(!loaded.filterValid && loaded.getPowerRequested() == 0, "Empty round robin extracted arbitrary items");
        context.waitAndRun(170, () -> {
            context.assertTrue(total(inventory(context, POS.east(2))) == 3 && total(source) == 1 && contents(copy) == 0,
                "Round-robin save/reload lost or duplicated travelling items");
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void screenEditsGhostsAndValidatesModeButtons(TestContext context) {
        var tile = circuit(context);
        var player = context.createMockCreativeServerPlayerInWorld();
        player.refreshPositionAndAngles(tile.getPos(), 0, 0);
        BCTransportGuis.PIPE_DIAMOND_WOOD.openGui(player, tile.getPos());
        context.assertTrue(player.currentScreenHandler instanceof ContainerDiamondWoodPipe, "Native extraction filter screen did not open");
        var screen = (ContainerDiamondWoodPipe) player.currentScreenHandler;
        var behaviour = screen.behaviour;
        context.assertTrue(screen.getType() == BCTransportScreens.DIAMOND_WOOD && screen.slots.size() == 45, "Incorrect screen type/slot count");
        screen.setCursorStack(new ItemStack(Items.DIAMOND, 7));
        screen.onSlotClick(36, 0, SlotActionType.PICKUP, player);
        context.assertTrue(screen.getCursorStack().getCount() == 7 && behaviour.filters.getStackInSlot(0).getCount() == 1, "Filter consumed real items");
        context.assertTrue(screen.onButtonClick(player, 2) && behaviour.filterMode == FilterMode.ROUND_ROBIN, "Native mode button did not select round robin");
        context.assertTrue(!screen.onButtonClick(player, -1) && !screen.onButtonClick(player, 3) && behaviour.filterMode == FilterMode.ROUND_ROBIN, "Invalid mode button changed state");
        player.refreshPositionAndAngles(tile.getPos().add(20, 0, 0), 0, 0);
        context.assertTrue(!screen.onButtonClick(player, 0) && behaviour.filterMode == FilterMode.ROUND_ROBIN, "Distant player changed extraction mode");
        player.refreshPositionAndAngles(tile.getPos(), 0, 0);
        tile.readNbt(tile.createNbt());
        context.assertTrue(!screen.onButtonClick(player, 0), "Stale screen changed extraction mode");
        screen.setCursorStack(ItemStack.EMPTY);
        player.closeHandledScreen();
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void filterStateSurvivesPayloadAndMalformedSavedIndexes(TestContext context) throws java.io.IOException {
        var tile = circuit(context);
        var behaviour = (PipeBehaviourWoodDiamond) tile.getPipe().behaviour;
        behaviour.filters.setStackInSlot(8, new ItemStack(Items.EMERALD));
        behaviour.setFilterMode(FilterMode.ROUND_ROBIN);
        var remote = new PipeBehaviourWoodDiamond(tile.getPipe());
        var data = new buildcraft.lib.net.PacketBufferBC(io.netty.buffer.Unpooled.buffer());
        try {
            behaviour.writePayload(data, net.fabricmc.api.EnvType.SERVER);
            remote.readPayload(data, net.fabricmc.api.EnvType.CLIENT, null);
            context.assertTrue(remote.filterMode == FilterMode.ROUND_ROBIN && remote.currentFilter == 8 && remote.filterValid
                && "WEST".equals(remote.writeToNbt().getString("currentDir")) && !data.isReadable(), "Behaviour payload lost direction or filter state");
        } finally { data.release(); }
        var saved = behaviour.writeToNbt();
        saved.putByte("currentFilter", (byte) -128);
        saved.putByte("mode", (byte) 99);
        var loaded = new PipeBehaviourWoodDiamond(tile.getPipe(), saved);
        context.assertTrue(loaded.filterMode == FilterMode.WHITE_LIST && loaded.currentFilter == 8 && loaded.filterValid,
            "Malformed saved index was not normalized to the available filter");
        context.assertTrue(loaded.filters.getStackInSlot(8).isOf(Items.EMERALD), "Load lost filter contents");
        context.complete();
    }

    private static TilePipeHolder circuit(TestContext context) {
        context.setBlockState(POS, BCTransportBlocks.pipeHolder.getDefaultState());
        var tile = (TilePipeHolder) context.getBlockEntity(POS);
        tile.onPlacedBy(null, new ItemStack(BCTransportItems.pipeItemDiaWood));
        context.setBlockState(POS.east(), BCTransportBlocks.pipeHolder.getDefaultState());
        var next = (TilePipeHolder) context.getBlockEntity(POS.east());
        next.onPlacedBy(null, new ItemStack(BCTransportItems.pipeItemStone));
        context.setBlockState(POS.west(), Blocks.BARREL.getDefaultState());
        context.setBlockState(POS.east(2), Blocks.BARREL.getDefaultState());
        tile.tick(); next.tick();
        return tile;
    }
    private static Inventory inventory(TestContext context, BlockPos local) { return (Inventory) context.getBlockEntity(local); }
    private static int total(Inventory inventory) {
        int count = 0; for (int i = 0; i < inventory.size(); i++) count += inventory.getStack(i).getCount(); return count;
    }
    private static int contents(TilePipeHolder tile) {
        int count = 0;
        var items = tile.getPipe().flow.writeToNbt().getList("items", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < items.size(); i++) count += ItemStack.fromNbt(items.getCompound(i).getCompound("stack")).getCount();
        return count;
    }
}
