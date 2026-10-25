/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;

import buildcraft.api.mj.MjAPI;
import buildcraft.lib.statement.ActionWrapper.ActionWrapperInternal;
import buildcraft.lib.statement.TriggerWrapper.TriggerWrapperInternal;
import buildcraft.silicon.BCSiliconPlugs;
import buildcraft.silicon.gate.*;
import buildcraft.silicon.plug.PluggableGate;
import buildcraft.transport.*;
import buildcraft.transport.container.ContainerEmzuliPipe_BC8;
import buildcraft.transport.pipe.behaviour.PipeBehaviourEmzuli;
import buildcraft.transport.pipe.behaviour.PipeBehaviourEmzuli.SlotIndex;
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
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public final class EmzuliGameTests implements FabricGameTest {
    private static final BlockPos POS = new BlockPos(3, 3, 3);

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 220)
    public void actualGatePresetsExtractAndPaintItemsForDaizuliRouting(TestContext context) {
        var tile = circuit(context);
        var behaviour = (PipeBehaviourEmzuli) tile.getPipe().behaviour;
        var source = inventory(context, POS.west());
        var diamond = new ItemStack(Items.DIAMOND, 8);
        diamond.setCustomName(Text.literal("Gate-selected diamond"));
        source.setStack(0, diamond.copy());
        source.setStack(1, new ItemStack(Items.EMERALD, 5));
        behaviour.invFilters.setStackInSlot(0, diamond.copyWithCount(1));
        behaviour.invFilters.setStackInSlot(1, new ItemStack(Items.EMERALD));
        behaviour.setColour(SlotIndex.SQUARE, DyeColor.BLUE);
        behaviour.setColour(SlotIndex.CIRCLE, DyeColor.RED);
        context.assertTrue(behaviour.getPowerRequested() == 0, "Inactive Emzuli extracted without a gate");
        var gate = attachGate(tile, SlotIndex.SQUARE, SlotIndex.CIRCLE);
        long presetCount = gate.logic.getAllValidActions().stream().filter(a -> a.delegate instanceof buildcraft.transport.statements.ActionExtractionPreset).count();
        context.assertTrue(presetCount == 4, "Gate action provider does not expose exactly four extraction presets");
        tile.tick();
        context.assertTrue(behaviour.getCurrentSlot() == SlotIndex.SQUARE && behaviour.getActiveSlots().size() == 2, "Gate event did not activate both configured presets");
        var before = behaviour.writeToNbt();
        for (int i = 0; i < 20; i++) {
            context.assertTrue(behaviour.getPowerRequested() == 8 * MjAPI.MJ && behaviour.receivePower(3 * MjAPI.MJ, true) == 0, "Simulated preset extraction failed");
        }
        context.assertTrue(before.equals(behaviour.writeToNbt()) && total(source) == 13 && contents(tile) == 0,
            "Power queries changed the preset cursor, TTL, source inventory or pipe contents");
        context.assertTrue(behaviour.receivePower(3 * MjAPI.MJ, false) == 0 && behaviour.getCurrentSlot() == SlotIndex.CIRCLE,
            "First real extraction did not advance to the next enabled preset");
        context.assertTrue(behaviour.receivePower(2 * MjAPI.MJ, false) == 0 && behaviour.getCurrentSlot() == SlotIndex.SQUARE,
            "Second real extraction did not wrap to the first preset");
        var travelling = tile.getPipe().flow.writeToNbt().getList("items", NbtElement.COMPOUND_TYPE);
        context.assertTrue(travelling.size() == 2 && total(source) == 8 && contents(tile) == 5, "Preset extraction lost or duplicated items");
        for (int i = 0; i < travelling.size(); i++) {
            var entry = travelling.getCompound(i);
            var stack = ItemStack.fromNbt(entry.getCompound("stack"));
            int expected = (stack.isOf(Items.DIAMOND) ? DyeColor.BLUE : DyeColor.RED).getId() + 1;
            context.assertTrue(entry.getByte("colour") == expected, "Extracted stack lost its preset paint");
        }
        var copy = BCTransportBlocks.pipeHolderTile.instantiate(tile.getPos(), tile.getCachedState());
        copy.readNbt(tile.createNbt());
        context.getWorld().removeBlockEntity(tile.getPos());
        context.getWorld().addBlockEntity(copy);
        copy.tick();
        context.assertTrue(copy.getPluggable(Direction.UP) instanceof PluggableGate
            && ((PipeBehaviourEmzuli) copy.getPipe().behaviour).getActiveSlots().size() == 2, "Saved gate statements did not resume their presets");
        context.waitAndRun(170, () -> {
            var blueTarget = inventory(context, POS.east(2));
            var redTarget = inventory(context, POS.east().north());
            context.assertTrue(total(blueTarget) == 3 && ItemStack.canCombine(blueTarget.getStack(0), diamond)
                && total(redTarget) == 2 && redTarget.getStack(0).isOf(Items.EMERALD) && total(source) == 8 && contents(copy) == 0,
                "Gate-driven painted items did not reach their matching Daizuli outputs after reload");
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void presetsWaitForTheirItemsAndExpireWhenGateStops(TestContext context) {
        var tile = circuit(context);
        var behaviour = (PipeBehaviourEmzuli) tile.getPipe().behaviour;
        behaviour.invFilters.setStackInSlot(0, new ItemStack(Items.DIAMOND));
        behaviour.invFilters.setStackInSlot(1, new ItemStack(Items.EMERALD));
        inventory(context, POS.west()).setStack(0, new ItemStack(Items.EMERALD, 4));
        var gate = attachGate(tile, SlotIndex.SQUARE, SlotIndex.CIRCLE);
        tile.tick();
        context.assertTrue(behaviour.getPowerRequested() == 0 && behaviour.receivePower(MjAPI.MJ, false) == MjAPI.MJ
            && behaviour.getCurrentSlot() == SlotIndex.SQUARE, "Emzuli skipped a missing enabled preset");
        gate.logic.statements[0].trigger.set(null);
        tile.tick(); tile.tick();
        context.assertTrue(!behaviour.getActiveSlots().contains(SlotIndex.SQUARE) && behaviour.getCurrentSlot() == SlotIndex.CIRCLE,
            "Expired preset remained selected while another gate preset was active");
        context.assertTrue(behaviour.receivePower(2 * MjAPI.MJ, false) == 0 && total(inventory(context, POS.west())) == 2,
            "Remaining preset did not extract after the first preset expired");
        tile.replacePluggable(Direction.UP, null);
        tile.tick(); tile.tick();
        context.assertTrue(behaviour.getActiveSlots().isEmpty() && behaviour.getCurrentSlot() == null && behaviour.getPowerRequested() == 0
            && behaviour.receivePower(MjAPI.MJ, false) == MjAPI.MJ, "Removing gate left a permanently active extractor");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void nativePaintScreenPreservesSamplesAndRejectsStaleButtons(TestContext context) {
        var tile = circuit(context);
        var player = context.createMockCreativeServerPlayerInWorld();
        player.refreshPositionAndAngles(tile.getPos(), 0, 0);
        BCTransportGuis.PIPE_EMZULI.openGui(player, tile.getPos());
        context.assertTrue(player.currentScreenHandler instanceof ContainerEmzuliPipe_BC8, "Emzuli native screen did not open");
        var screen = (ContainerEmzuliPipe_BC8) player.currentScreenHandler;
        context.assertTrue(screen.getType() == BCTransportScreens.EMZULI && screen.slots.size() == 40, "Wrong Emzuli screen type/slot layout");
        screen.setCursorStack(new ItemStack(Items.DIAMOND, 7));
        for (int i = 0; i < 4; i++) screen.onSlotClick(36 + i, 0, SlotActionType.PICKUP, player);
        context.assertTrue(screen.getCursorStack().getCount() == 7, "Preset samples consumed real cursor items");
        for (var slot : SlotIndex.VALUES) {
            context.assertTrue(screen.onButtonClick(player, slot.ordinal() * 17 + slot.colour.getId() + 1)
                && screen.getColour(slot) == slot.colour, "Native paint control failed for " + slot);
        }
        context.assertTrue(screen.onButtonClick(player, 17) && screen.getColour(SlotIndex.CIRCLE) == null, "Clear paint did not remove only its own setting");
        context.assertTrue(!screen.onButtonClick(player, -1) && !screen.onButtonClick(player, 68), "Invalid paint ID accepted");
        var saved = tile.createNbt();
        var copy = BCTransportBlocks.pipeHolderTile.instantiate(tile.getPos(), tile.getCachedState());
        copy.readNbt(saved);
        var loaded = (PipeBehaviourEmzuli) copy.getPipe().behaviour;
        for (var slot : SlotIndex.VALUES) {
            context.assertTrue(loaded.invFilters.getStackInSlot(slot.ordinal()).isOf(Items.DIAMOND)
                && loaded.slotColours.get(slot) == screen.getColour(slot), "Save/reload lost a filter or paint");
        }
        player.refreshPositionAndAngles(tile.getPos().add(20, 0, 0), 0, 0);
        context.assertTrue(!screen.onButtonClick(player, 0), "Distant player changed paint");
        player.refreshPositionAndAngles(tile.getPos(), 0, 0);
        tile.readNbt(saved);
        context.assertTrue(!screen.onButtonClick(player, 0), "Replaced pipe retained stale paint access");
        screen.setCursorStack(ItemStack.EMPTY);
        player.closeHandledScreen();
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void gateAndPresetPayloadsPreserveCompleteLiveState(TestContext context) throws java.io.IOException {
        var tile = circuit(context);
        var behaviour = (PipeBehaviourEmzuli) tile.getPipe().behaviour;
        behaviour.invFilters.setStackInSlot(3, new ItemStack(Items.EMERALD));
        behaviour.setColour(SlotIndex.CROSS, DyeColor.YELLOW);
        var gate = attachGate(tile, SlotIndex.CROSS);
        tile.tick();
        var data = new buildcraft.lib.net.PacketBufferBC(io.netty.buffer.Unpooled.buffer());
        try {
            behaviour.writePayload(data, net.fabricmc.api.EnvType.SERVER);
            var remote = new PipeBehaviourEmzuli(tile.getPipe());
            remote.readPayload(data, net.fabricmc.api.EnvType.CLIENT, null);
            context.assertTrue(remote.getCurrentSlot() == SlotIndex.CROSS && remote.getActiveSlots().equals(behaviour.getActiveSlots())
                && remote.slotColours.get(SlotIndex.CROSS) == DyeColor.YELLOW && !data.isReadable(), "Preset packet lost colour, active mask or current slot");
        } finally { data.release(); }
        data = new buildcraft.lib.net.PacketBufferBC(io.netty.buffer.Unpooled.buffer());
        try {
            gate.writeCreationPayload(data);
            var remote = new PluggableGate(BCSiliconPlugs.gate, tile, Direction.UP, data);
            context.assertTrue(remote.logic.variant.equals(gate.logic.variant) && remote.logic.isOn
                && remote.logic.statements[0].action.get().delegate == BCTransportStatements.ACTION_EXTRACTION_PRESET[3] && !data.isReadable(),
                "Gate creation packet lost configured statements or activation state");
        } finally { data.release(); }
        context.complete();
    }

    private static PluggableGate attachGate(TilePipeHolder tile, SlotIndex... slots) {
        var gate = new PluggableGate(BCSiliconPlugs.gate, tile, Direction.UP,
            new GateVariant(EnumGateLogic.OR, EnumGateMaterial.NETHER_BRICK, EnumGateModifier.NO_MODIFIER));
        for (int i = 0; i < slots.length; i++) {
            // Resolve saved IDs without initializing the statement class as a test-side effect.
            var trigger = buildcraft.api.statements.StatementManager.statements.get("buildcraftcore:trigger.true");
            if (!(trigger instanceof buildcraft.api.statements.ITriggerInternal registered)) throw new AssertionError("Core trigger missing at startup");
            gate.logic.statements[i].trigger.set(new TriggerWrapperInternal(registered));
            gate.logic.statements[i].action.set(new ActionWrapperInternal(BCTransportStatements.ACTION_EXTRACTION_PRESET[slots[i].ordinal()]));
        }
        tile.replacePluggable(Direction.UP, gate);
        return gate;
    }
    private static TilePipeHolder circuit(TestContext context) {
        context.setBlockState(POS, BCTransportBlocks.pipeHolder.getDefaultState());
        var tile = (TilePipeHolder) context.getBlockEntity(POS);
        tile.onPlacedBy(null, new ItemStack(BCTransportItems.pipeItemEmzuli));
        context.setBlockState(POS.east(), BCTransportBlocks.pipeHolder.getDefaultState());
        var sorter = (TilePipeHolder) context.getBlockEntity(POS.east());
        sorter.onPlacedBy(null, new ItemStack(BCTransportItems.pipeItemDaizuli));
        var saved = sorter.createNbt();
        var beh = saved.getCompound("pipe").getCompound("beh");
        beh.putString("currentDir", "EAST");
        beh.putString("colour", "BLUE");
        sorter.readNbt(saved);
        context.setBlockState(POS.west(), Blocks.BARREL.getDefaultState());
        context.setBlockState(POS.east(2), Blocks.BARREL.getDefaultState());
        context.setBlockState(POS.east().north(), Blocks.BARREL.getDefaultState());
        tile.tick(); sorter.tick();
        return tile;
    }
    private static Inventory inventory(TestContext context, BlockPos pos) { return (Inventory) context.getBlockEntity(pos); }
    private static int total(Inventory inv) { int count = 0; for (int i = 0; i < inv.size(); i++) count += inv.getStack(i).getCount(); return count; }
    private static int contents(TilePipeHolder tile) {
        int count = 0; var items = tile.getPipe().flow.writeToNbt().getList("items", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < items.size(); i++) count += ItemStack.fromNbt(items.getCompound(i).getCompound("stack")).getCount(); return count;
    }
}
