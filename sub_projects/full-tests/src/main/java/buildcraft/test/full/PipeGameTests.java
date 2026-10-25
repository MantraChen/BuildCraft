/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;

import buildcraft.transport.BCTransportBlocks;
import buildcraft.transport.BCTransportItems;
import buildcraft.transport.item.ItemPipeHolder;
import buildcraft.transport.pipe.PipeRegistry;
import buildcraft.transport.pipe.flow.PipeFlowItems;
import buildcraft.transport.tile.TilePipeHolder;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.Blocks;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Hand;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public final class PipeGameTests implements FabricGameTest {
    private static final BlockPos LOCAL = new BlockPos(3, 3, 3);

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void registeredPipeItemsPlacePickAndReloadAllDefinitions(TestContext context) {
        var player = context.createMockCreativeServerPlayerInWorld();
        player.getAbilities().creativeMode = false;
        var pos = context.getAbsolutePos(LOCAL);
        player.refreshPositionAndAngles(pos.add(0,0,2), 0, 0);
        context.getWorld().setBlockState(pos.down(), Blocks.STONE.getDefaultState());
        int count = 0;
        for (var def : PipeRegistry.INSTANCE.getAllRegisteredPipes()) {
            var item = (ItemPipeHolder) PipeRegistry.INSTANCE.getItemForPipe(def);
            context.assertTrue(item != null && Registries.ITEM.getId(item).getPath().equals("pipe_" + def.identifier.getPath()), "Missing registered pipe " + def.identifier);
            var stack = ItemPipeHolder.withColour(new ItemStack(item, 2), DyeColor.BLUE);
            player.setStackInHand(Hand.MAIN_HAND, stack);
            var hit = new BlockHitResult(Vec3d.ofCenter(pos.down()).add(0,0.5,0), Direction.UP, pos.down(), false);
            context.assertTrue(item.useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, hit)).isAccepted(), "Native placement failed for " + def.identifier);
            var tile = (TilePipeHolder) context.getWorld().getBlockEntity(pos);
            context.assertTrue(tile != null && tile.getPipe() != null && tile.getPipe().definition == def, "Placement lost pipe definition");
            context.assertTrue(stack.getCount() == 1, "Survival placement consumed the wrong item count");
            var picked = BCTransportBlocks.pipeHolder.getPickStack(context.getWorld(), pos, tile.getCachedState());
            context.assertTrue(picked.isOf(item) && ItemPipeHolder.getColour(picked) == DyeColor.BLUE, "Picking lost pipe colour/type");
            tile.tick();
            var saved = tile.createNbt();
            var copy = BCTransportBlocks.pipeHolderTile.instantiate(pos, tile.getCachedState());
            copy.readNbt(saved); // Native loading reads NBT before assigning a world.
            context.assertTrue(copy.getPipe().definition == def && copy.getPipe().getColour() == DyeColor.BLUE, "Unattached NBT reconstruction lost pipe identity");
            context.assertTrue(copy.createNbt().getCompound("pipe").getString("def").equals(def.identifier.toString()), "Unattached save failed");
            context.getWorld().removeBlock(pos, false);
            count++;
        }
        context.assertTrue(count == 46, "Expected all 46 default pipe definitions, found " + count);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void pipeConnectionsHonorColourAndMaterialBoundaries(TestContext context) {
        var first = place(context, LOCAL, BCTransportItems.pipeItemStone, DyeColor.RED);
        var other = place(context, LOCAL.east(), BCTransportItems.pipeItemStone, DyeColor.BLUE);
        first.tick(); other.tick();
        context.assertTrue(!first.getPipe().isConnected(Direction.EAST), "Different dye colours connected");
        other.getPipe().setColour(DyeColor.RED);
        other.tick(); first.tick();
        context.assertTrue(first.getPipe().isConnected(Direction.EAST) && other.getPipe().isConnected(Direction.WEST), "Matching pipes did not connect");
        context.getWorld().removeBlock(other.getPos(), false);
        var cobble = place(context, LOCAL.east(), BCTransportItems.pipeItemCobble, DyeColor.RED);
        first.tick(); cobble.tick();
        context.assertTrue(!first.getPipe().isConnected(Direction.EAST), "Cobblestone and stone pipes connected");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void nativePipeInputCommitsOnceAndRollsBackAcrossFaces(TestContext context) {
        var pipe = place(context, LOCAL, BCTransportItems.pipeItemStone, null);
        for (var face : Direction.values()) context.getWorld().setBlockState(pipe.getPos().offset(face), Blocks.BARREL.getDefaultState());
        pipe.tick();
        var west = ItemStorage.SIDED.find(context.getWorld(), pipe.getPos(), Direction.WEST);
        var north = ItemStorage.SIDED.find(context.getWorld(), pipe.getPos(), Direction.NORTH);
        context.assertTrue(west != null && north != null, "Native pipe storage unavailable");
        var stack = new ItemStack(Items.DIAMOND);
        stack.setCustomName(Text.literal("Travel unchanged"));
        var resource = ItemVariant.of(stack);
        try (var outer = Transaction.openOuter()) {
            context.assertTrue(west.insert(resource, 3, outer) == 3, "Native input rejected");
            try (var inner = outer.openNested()) { north.insert(resource, 2, inner); inner.commit(); }
            context.assertTrue(contents(pipe) == 0, "Uncommitted items entered the pipe");
        }
        context.assertTrue(contents(pipe) == 0, "Outer abort left travelling items");
        try (var outer = Transaction.openOuter()) {
            west.insert(resource, 4, outer);
            try (var inner = outer.openNested()) { north.insert(resource, 2, inner); }
            context.assertTrue(north.extract(resource, 64, outer) == 0, "Automation extracted travelling items");
            outer.commit();
        }
        context.assertTrue(contents(pipe) == 4, "Committed input was lost or duplicated");
        var drops = DefaultedList.<ItemStack>of();
        pipe.getPipe().flow.addDrops(drops, 0);
        context.assertTrue(ItemStack.canCombine(drops.get(0), stack), "Automation lost item NBT");
        context.getWorld().removeBlock(pipe.getPos(), false);
        try (var tx = Transaction.openOuter()) { context.assertTrue(west.insert(resource, 1, tx) == 0, "Stale storage accepted items"); tx.commit(); }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300)
    public void chuteTransfersItemsThroughTwoNativePipesIntoBarrel(TestContext context) {
        var first = place(context, LOCAL, BCTransportItems.pipeItemStone, null);
        var second = place(context, LOCAL.east(), BCTransportItems.pipeItemStone, null);
        context.setBlockState(LOCAL.west(), buildcraft.factory.BCFactoryBlocks.chute.getDefaultState());
        context.setBlockState(LOCAL.east(2), Blocks.BARREL.getDefaultState());
        var chute = (buildcraft.factory.tile.TileChute) context.getBlockEntity(LOCAL.west());
        var named = new ItemStack(Items.DIAMOND, 9);
        named.setCustomName(Text.literal("End to end"));
        chute.inv.setStackInSlot(0, named);
        context.waitAndRun(240, () -> {
            var destination = (Inventory) context.getBlockEntity(LOCAL.east(2));
            context.assertTrue(count(destination) == 9 && chute.inv.getStackInSlot(0).isEmpty(), "Chute-to-pipe-to-barrel transfer failed");
            context.assertTrue(contents(first) == 0 && contents(second) == 0, "Delivery duplicated travelling items");
            context.assertTrue(ItemStack.canCombine(destination.getStack(0), named), "Delivery lost NBT");
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 220)
    public void travellingItemsSurviveUnattachedNativeReload(TestContext context) {
        var pipe = place(context, LOCAL, BCTransportItems.pipeItemStone, null);
        context.setBlockState(LOCAL.east(), Blocks.BARREL.getDefaultState());
        ((PipeFlowItems) pipe.getPipe().flow).insertItemsForce(new ItemStack(Items.EMERALD, 7), Direction.WEST, null, 0.08);
        var copy = BCTransportBlocks.pipeHolderTile.instantiate(pipe.getPos(), pipe.getCachedState());
        copy.readNbt(pipe.createNbt());
        context.getWorld().removeBlockEntity(pipe.getPos());
        context.getWorld().addBlockEntity(copy);
        context.waitAndRun(150, () -> {
            context.assertTrue(count((Inventory) context.getBlockEntity(LOCAL.east())) == 7 && contents(copy) == 0, "Reload lost or duplicated travelling items");
            context.complete();
        });
    }


    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void woodenPipeExtractsWithMjAndKeepsItsFacingAfterReload(TestContext context) {
        var tile = place(context, LOCAL, BCTransportItems.pipeItemWood, null);
        var next = place(context, LOCAL.east(), BCTransportItems.pipeItemStone, null);
        context.setBlockState(LOCAL.west(), Blocks.BARREL.getDefaultState());
        context.setBlockState(LOCAL.east(2), Blocks.BARREL.getDefaultState());
        var source = (Inventory) context.getBlockEntity(LOCAL.west());
        source.setStack(0, new ItemStack(Items.DIAMOND, 7));
        tile.tick(); next.tick();
        var copy = BCTransportBlocks.pipeHolderTile.instantiate(tile.getPos(), tile.getCachedState());
        copy.readNbt(tile.createNbt());
        context.assertTrue("WEST".equals(copy.getPipe().behaviour.writeToNbt().getString("currentDir")), "Reload lost extraction direction");
        context.getWorld().removeBlockEntity(tile.getPos());
        context.getWorld().addBlockEntity(copy);
        copy.tick();
        var receiver = (buildcraft.api.mj.IMjReceiver) buildcraft.api.mj.MjAPI.CONNECTOR.find(context.getWorld(), copy.getPos(), Direction.UP);
        context.assertTrue(receiver != null, "Native MJ lookup cannot reach wooden pipe");
        long power = 3 * buildcraft.api.mj.MjAPI.MJ;
        context.assertTrue(receiver.receivePower(power, true) == 0 && count(source) == 7 && contents(copy) == 0, "Simulated extraction changed inventory");
        context.assertTrue(receiver.receivePower(power, false) == 0 && count(source) == 4 && contents(copy) == 3, "MJ extraction lost items or power");
        context.waitAndRun(150, () -> {
            context.assertTrue(count((Inventory) context.getBlockEntity(LOCAL.east(2))) == 3 && contents(copy) == 0 && contents(next) == 0, "Wooden pipe delivery failed");
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void wrenchChangesDirectionalPipeAndRejectsRemotePlayers(TestContext context) {
        var tile = place(context, LOCAL, BCTransportItems.pipeItemWood, null);
        context.setBlockState(LOCAL.west(), Blocks.BARREL.getDefaultState());
        context.setBlockState(LOCAL.east(), Blocks.BARREL.getDefaultState());
        tile.tick();
        var player = context.createMockCreativeServerPlayerInWorld();
        player.refreshPositionAndAngles(tile.getPos().add(0, 0, 2), 0, 0);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(buildcraft.core.BCCoreItems.wrench));
        var hit = new BlockHitResult(Vec3d.ofCenter(tile.getPos()), Direction.SOUTH, tile.getPos(), false);
        var behaviour = tile.getPipe().behaviour;
        var before = behaviour.writeToNbt().getString("currentDir");
        context.assertTrue(behaviour.onPipeActivate(player, hit, 0.5f, 0.5f, 0.5f, buildcraft.api.core.EnumPipePart.CENTER), "Wrench interaction rejected");
        var after = behaviour.writeToNbt().getString("currentDir");
        context.assertTrue(!before.equals(after), "Wrench did not change extraction direction");
        player.refreshPositionAndAngles(tile.getPos().add(20, 0, 0), 0, 0);
        context.assertTrue(!behaviour.onPipeActivate(player, hit, 0.5f, 0.5f, 0.5f, buildcraft.api.core.EnumPipePart.CENTER), "Remote wrench interaction accepted");
        context.assertTrue(behaviour.writeToNbt().getString("currentDir").equals(after), "Remote interaction changed facing");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void flowSnapshotProtocolRoundTripsAndRejectsOversizedSnapshots(TestContext context) throws java.io.IOException {
        var tile = place(context, LOCAL, BCTransportItems.pipeItemStone, null);
        var flow = (PipeFlowItems) tile.getPipe().flow;
        flow.insertItemsForce(new ItemStack(Items.DIAMOND, 3), Direction.WEST, DyeColor.RED, 0.08);
        flow.insertItemsForce(new ItemStack(Items.EMERALD, 5), Direction.NORTH, DyeColor.BLUE, 0.08);
        var buffer = new buildcraft.lib.net.PacketBufferBC(io.netty.buffer.Unpooled.buffer());
        try {
            tile.writePayload(TilePipeHolder.NET_UPDATE_PIPE_FLOW, buffer, buildcraft.lib.tile.TileBC_Neptune.NetSide.SERVER);
            context.assertTrue(buffer.readBoolean(), "Flow packet missing presence flag");
            context.assertTrue(buffer.readShort() == buildcraft.api.transport.pipe.PipeFlow.NET_ID_UPDATE, "Flow packet missing its message discriminator");
            var remote = new PipeFlowItems(tile.getPipe());
            remote.readPayload(buildcraft.api.transport.pipe.PipeFlow.NET_ID_UPDATE, buffer, net.fabricmc.api.EnvType.CLIENT);
            context.assertTrue(!buffer.isReadable(), "Flow reader did not consume its payload");
            var list = remote.writeToNbt().getList("items", net.minecraft.nbt.NbtElement.COMPOUND_TYPE);
            context.assertTrue(list.size() == 2, "Client snapshot lost travelling items");
            var sides = new java.util.HashSet<String>();
            for (int i = 0; i < list.size(); i++) sides.add(list.getCompound(i).getString("side"));
            context.assertTrue(sides.equals(java.util.Set.of("WEST", "NORTH")), "Snapshot corrupted direction encoding");
            buffer.clear();
            buffer.writeVarInt(0);
            remote.readPayload(buildcraft.api.transport.pipe.PipeFlow.NET_ID_UPDATE, buffer, net.fabricmc.api.EnvType.CLIENT);
            context.assertTrue(remote.writeToNbt().getList("items", net.minecraft.nbt.NbtElement.COMPOUND_TYPE).isEmpty(), "Empty authoritative update retained ghost items");
            buffer.clear();
            buffer.writeVarInt(513);
            boolean rejected = false;
            try { remote.readPayload(buildcraft.api.transport.pipe.PipeFlow.NET_ID_UPDATE, buffer, net.fabricmc.api.EnvType.CLIENT); }
            catch (java.io.IOException expected) { rejected = true; }
            context.assertTrue(rejected, "Unbounded client snapshot accepted");
        } finally { buffer.release(); }
        context.complete();
    }


    @GameTest(templateName = EMPTY_STRUCTURE)
    public void nativeRecipesAndPlacementAdvancementAreAvailable(TestContext context) {
        var player = context.createMockCreativeServerPlayerInWorld();
        var handler = new net.minecraft.screen.CraftingScreenHandler(1, player.getInventory(),
            net.minecraft.screen.ScreenHandlerContext.create(context.getWorld(), context.getAbsolutePos(LOCAL)));
        var inputs = new net.minecraft.item.Item[] {Items.OAK_PLANKS, Items.COBBLESTONE, Items.STONE, Items.IRON_INGOT, Items.GOLD_INGOT, Items.DIAMOND};
        var outputs = new ItemPipeHolder[] {BCTransportItems.pipeItemWood, BCTransportItems.pipeItemCobble, BCTransportItems.pipeItemStone,
            BCTransportItems.pipeItemIron, BCTransportItems.pipeItemGold, BCTransportItems.pipeItemDiamond};
        for (int i = 0; i < inputs.length; i++) {
            handler.getSlot(1).setStack(new ItemStack(inputs[i]));
            handler.getSlot(2).setStack(new ItemStack(Items.GLASS));
            handler.getSlot(3).setStack(new ItemStack(inputs[i]));
            handler.onContentChanged(handler.getSlot(1).inventory);
            var output = handler.getSlot(0).getStack();
            context.assertTrue(output.isOf(outputs[i]) && output.getCount() == 8, "Native pipe recipe missing or wrong yield: " + outputs[i]);
        }
        var manager = context.getWorld().getRecipeManager();
        for (var def : PipeRegistry.INSTANCE.getAllRegisteredPipes()) {
            if (def.flowType != buildcraft.transport.BCTransportPipes.structure.flowType && !def.identifier.getPath().endsWith("_item")) continue;
            var recipe = manager.get(new net.minecraft.util.Identifier("buildcrafttransport", "pipe_" + def.identifier.getPath()));
            context.assertTrue(recipe.isPresent(), "Missing pipe survival recipe: " + def.identifier);
        }
        var tile = place(context, LOCAL, BCTransportItems.pipeItemStone, null);
        tile.onPlacedBy(player, new ItemStack(BCTransportItems.pipeItemStone));
        var advancement = context.getWorld().getServer().getAdvancementLoader().get(new net.minecraft.util.Identifier("buildcrafttransport", "pipe_dream"));
        context.assertTrue(advancement != null && player.getAdvancementTracker().getProgress(advancement).isDone(), "Pipe placement advancement was not granted");
        context.complete();
    }

    private static TilePipeHolder place(TestContext context, BlockPos pos, ItemPipeHolder item, DyeColor colour) {
        context.setBlockState(pos, BCTransportBlocks.pipeHolder.getDefaultState());
        var tile = (TilePipeHolder) context.getBlockEntity(pos);
        tile.onPlacedBy(null, ItemPipeHolder.withColour(new ItemStack(item), colour));
        return tile;
    }
    private static int contents(TilePipeHolder tile) {
        var stacks = DefaultedList.<ItemStack>of();
        tile.getPipe().flow.addDrops(stacks, 0);
        return stacks.stream().mapToInt(ItemStack::getCount).sum();
    }
    private static int count(Inventory inv) {
        int count = 0; for (int i = 0; i < inv.size(); i++) count += inv.getStack(i).getCount(); return count;
    }
}
