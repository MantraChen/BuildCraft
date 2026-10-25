/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;

import buildcraft.silicon.*;
import buildcraft.silicon.gate.*;
import buildcraft.silicon.item.ItemPluggableGate;
import buildcraft.silicon.plug.PluggableGate;
import buildcraft.silicon.container.ContainerGate;
import buildcraft.transport.*;
import buildcraft.transport.tile.TilePipeHolder;
import buildcraft.transport.pipe.behaviour.PipeBehaviourEmzuli;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.item.*;
import net.minecraft.test.*;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;

public final class GateGameTests implements FabricGameTest {
    private static final BlockPos POS = new BlockPos(3, 3, 3);
    private static TilePipeHolder pipe(TestContext context) {
        context.setBlockState(POS, BCTransportBlocks.pipeHolder.getDefaultState());
        var tile = (TilePipeHolder) context.getBlockEntity(POS);
        tile.onPlacedBy(null, new ItemStack(BCTransportItems.pipeItemEmzuli));
        return tile;
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void allGateVariantsPlaceOnSixFacesAndSurviveReload(TestContext context) {
        var tile = pipe(context);
        var player = context.createMockCreativeServerPlayerInWorld();
        player.getAbilities().creativeMode = false;
        var variants = ItemPluggableGate.variants();
        context.assertTrue(variants.size() == 25, "Expected 25 gate variants");
        for (var variant : variants) for (var side : Direction.values()) {
            var look = Vec3d.of(side.getVector()).multiply(-1);
            var eye = Vec3d.ofCenter(tile.getPos()).subtract(look.multiply(3));
            player.refreshPositionAndAngles(eye.x, eye.y - player.getEyeHeight(player.getPose()), eye.z,
                (float) Math.toDegrees(Math.atan2(-look.x, look.z)), (float) -Math.toDegrees(Math.asin(look.y)));
            player.setHeadYaw(player.getYaw());
            var stack = BCSiliconItems.plugGate.getStack(variant); stack.setCount(2);
            player.setStackInHand(Hand.MAIN_HAND, stack);
            var hit = new BlockHitResult(Vec3d.ofCenter(tile.getPos()), side, tile.getPos(), false);
            var result = BCTransportBlocks.pipeHolder.onUse(tile.getCachedState(), context.getWorld(), tile.getPos(), player, Hand.MAIN_HAND, hit);
            context.assertTrue(result.isAccepted() && tile.getPluggable(side) instanceof PluggableGate && stack.getCount() == 1,
                "Native gate placement/count failed for " + variant.getVariantName() + " / " + side + " result=" + result + " count=" + stack.getCount()
                    + " look=" + player.getRotationVec(1) + " eye=" + player.getEyePos() + " trace=" + BCTransportBlocks.pipeHolder.rayTrace(context.getWorld(),tile.getPos(),player));
            var gate = (PluggableGate) tile.getPluggable(side);
            context.assertTrue(ItemPluggableGate.getVariant(gate.getPickStack()).equals(variant), "Pick stack lost variant");
            var drops = new java.util.ArrayList<ItemStack>(); gate.addDrops(drops, 0);
            context.assertTrue(drops.size() == 1 && ItemPluggableGate.getVariant(drops.get(0)).equals(variant), "Gate drop lost variant");
            var copy = BCTransportBlocks.pipeHolderTile.instantiate(tile.getPos(), tile.getCachedState());
            copy.readNbt(tile.createNbt());
            context.assertTrue(((PluggableGate) copy.getPluggable(side)).logic.variant.equals(variant), "Reload lost gate");
            var box = gate.getBoundingBox();
            context.assertTrue(box.maxX > box.minX && box.maxY > box.minY && box.maxZ > box.minZ && !box.contains(0.5,0.5,0.5), "Gate collision covers pipe centre");
            tile.replacePluggable(side, null);
        }
        var malformed = new net.minecraft.nbt.NbtCompound(); malformed.putByte("material", (byte)0); malformed.putByte("modifier", (byte)3);
        var normalized = new GateVariant(malformed);
        context.assertTrue(normalized.numSlots == 1 && normalized.numTriggerArgs == 0 && normalized.modifier == EnumGateModifier.NO_MODIFIER, "Malformed brick gate can create zero slots");
        context.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void nativeGateScreenControlsLinkedEmzuliPresetsAndRejectsStaleEdits(TestContext context) {
        var tile = pipe(context);
        var gate = new PluggableGate(BCSiliconPlugs.gate, tile, Direction.UP, new GateVariant(EnumGateLogic.AND, EnumGateMaterial.NETHER_BRICK, EnumGateModifier.NO_MODIFIER));
        tile.replacePluggable(Direction.UP, gate);
        var player = context.createMockCreativeServerPlayerInWorld(); player.refreshPositionAndAngles(tile.getPos(), 0, 0);
        BCSiliconScreens.open(player, gate);
        context.assertTrue(player.currentScreenHandler instanceof ContainerGate, "Native gate screen failed to open");
        var screen = (ContainerGate) player.currentScreenHandler;
        choose(context, screen, player, 0, false, "buildcraftcore:trigger.true");
        choose(context, screen, player, 0, true, "buildcraft:extraction.preset.red");
        screen.onButtonClick(player, 0); tile.tick();
        context.assertTrue(!gate.logic.isOn, "AND group ignored empty linked condition");
        choose(context, screen, player, 1, false, "buildcraftcore:trigger.true");
        tile.tick();
        context.assertTrue(gate.logic.isOn && ((PipeBehaviourEmzuli) tile.getPipe().behaviour).getActiveSlots().contains(PipeBehaviourEmzuli.SlotIndex.SQUARE), "Configured gate did not activate extractor");
        var saved = tile.createNbt();
        var copy = BCTransportBlocks.pipeHolderTile.instantiate(tile.getPos(), tile.getCachedState()); copy.readNbt(saved);
        var loaded = (PluggableGate) copy.getPluggable(Direction.UP);
        context.assertTrue(loaded.logic.connections[0] && loaded.logic.statements[0].action.get().getUniqueTag().equals("buildcraft:extraction.preset.red"), "GUI edits were not saved");
        context.assertTrue(!screen.onButtonClick(player, -1) && !screen.onButtonClick(player, Integer.MAX_VALUE)
            && !screen.onButtonClick(player, ContainerGate.selectionId(7, true, 0)), "Malformed screen action accepted");
        player.refreshPositionAndAngles(tile.getPos().add(20,0,0), 0,0);
        context.assertTrue(!screen.onButtonClick(player, 0), "Distant player edited gate");
        player.refreshPositionAndAngles(tile.getPos(),0,0);
        tile.replacePluggable(Direction.UP, new PluggableGate(BCSiliconPlugs.gate,tile,Direction.UP,gate.logic.variant));
        context.assertTrue(!screen.canUse(player) && !screen.onButtonClick(player, 0), "Replaced gate allowed stale screen edits");
        player.closeHandledScreen(); context.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void nativeGateParametersAndOpeningPayloadRoundTrip(TestContext context) throws java.io.IOException {
        var tile = pipe(context);
        var gate = new PluggableGate(BCSiliconPlugs.gate,tile,Direction.NORTH,new GateVariant(EnumGateLogic.OR,EnumGateMaterial.GOLD,EnumGateModifier.DIAMOND));
        tile.replacePluggable(Direction.NORTH, gate);
        var player = context.createMockCreativeServerPlayerInWorld(); player.refreshPositionAndAngles(tile.getPos(),0,0);
        BCSiliconScreens.open(player, gate); var screen = (ContainerGate) player.currentScreenHandler;
        choose(context,screen,player,0,false,"buildcraftcore:trigger.true");
        choose(context,screen,player,0,true,"buildcraft:redstone.output");
        screen.setCursorStack(new ItemStack(Items.DIAMOND,7));
        context.assertTrue(screen.onButtonClick(player,ContainerGate.parameterId(0,true,0,0,false)),"Parameter option could not be cycled");
        context.assertTrue(gate.logic.statements[0].action.get(0) == buildcraft.core.statements.StatementParamGateSideOnly.SPECIFIC && screen.getCursorStack().getCount()==7,"Parameter toggle consumed sample or lost side-only setting");
        var data = new buildcraft.lib.net.PacketBufferBC(io.netty.buffer.Unpooled.buffer());
        try {
            screen.writeOpeningData(data);
            var loaded = new PluggableGate(BCSiliconPlugs.gate,tile,Direction.NORTH,gate.logic.variant);
            var remote = new ContainerGate(player,99,loaded); remote.readOpeningData(data);
            context.assertTrue(remote.actions.equals(screen.actions) && remote.triggers.equals(screen.triggers) && !data.isReadable()
                && loaded.logic.statements[0].action.get(0) == buildcraft.core.statements.StatementParamGateSideOnly.SPECIFIC,"Opening data lost candidates or parameters");
        } finally { data.release(); }
        tile.tick();
        context.assertTrue(tile.getRedstoneOutput(Direction.NORTH)==15 && tile.getRedstoneOutput(Direction.SOUTH)==0,"Side-only gate output ignored its parameter");
        screen.onButtonClick(player,ContainerGate.selectionId(0,true,-1)); tile.tick();
        context.assertTrue(tile.getRedstoneOutput(Direction.NORTH)==0,"Clearing gate action left redstone latched");
        screen.setCursorStack(ItemStack.EMPTY); player.closeHandledScreen(); context.complete();
    }
    private static void choose(TestContext c, ContainerGate screen, net.minecraft.entity.player.PlayerEntity player, int row, boolean action, String id) {
        var choices = screen.choices(action);
        for (int i=0;i<choices.size();i++) if (choices.get(i).getUniqueTag().equals(id)) {
            int choiceId = ContainerGate.selectionId(row,action,i);
            var message = buildcraft.lib.net.MessageContainer.of(screen.syncId, ContainerGate.NET_EDIT, data -> data.writeVarInt(choiceId));
            var payload = message.getPayload();
            try { screen.readMessage(message.getMessageId(),payload,false,null); }
            catch(java.io.IOException e) { throw new IllegalStateException(e); }
            finally { payload.release(); }
            c.assertTrue(screen.statement(row,action).get() != null && screen.statement(row,action).get().getUniqueTag().equals(id),"Rejected available statement " + id); return;
        }
        throw new IllegalStateException("Statement missing from gate options: "+id);
    }
}
