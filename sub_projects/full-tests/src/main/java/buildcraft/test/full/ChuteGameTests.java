package buildcraft.test.full;

import buildcraft.api.mj.IMjReceiver;
import buildcraft.api.mj.MjAPI;
import buildcraft.core.BCCoreItems;
import buildcraft.factory.BCFactoryBlocks;
import buildcraft.factory.BCFactoryGuis;
import buildcraft.factory.BCFactoryScreens;
import buildcraft.factory.container.ContainerChute;
import buildcraft.factory.tile.TileChute;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.Blocks;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.vehicle.ChestMinecartEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/** Real world tests for the complete packaged chute, including its native inventories and screen. */
public final class ChuteGameTests implements FabricGameTest {
    private static final BlockPos LOCAL = new BlockPos(3, 3, 3);

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 160)
    public void unpoweredUpChuteCollectsItemsDuringWorldTicks(TestContext context) {
        var tile = place(context, Direction.UP);
        var stack = new ItemStack(Items.DIAMOND, 5);
        stack.setCustomName(Text.literal("Keep this name"));
        var entity = item(context, mouth(tile.getPos(), Direction.UP), stack);
        context.waitAndRun(110, () -> {
            context.assertTrue(count(tile) == 3 && entity.getStack().getCount() == 2, "Gravity pickup did not move exactly three items");
            context.assertTrue(ItemStack.canCombine(tile.inv.getStackInSlot(0), stack), "Pickup lost item NBT");
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void poweredPickupHonorsAllFacesAndItsQuarterBlockReach(TestContext context) {
        for (Direction face : Direction.values()) {
            var tile = place(context, face);
            var inside = item(context, mouth(tile.getPos(), face), new ItemStack(Items.DIAMOND, 5));
            var outside = item(context, mouth(tile.getPos(), face).add(Vec3d.of(face.getVector()).multiply(1)), new ItemStack(Items.EMERALD, 7));
            var wrongFace = item(context, mouth(tile.getPos(), face.getOpposite()), new ItemStack(Items.GOLD_INGOT, 9));
            tile.tick();
            context.assertTrue(count(tile) == 0, "Unpowered pickup occurred before gravity could accumulate");
            var receiver = (IMjReceiver) MjAPI.CONNECTOR.find(context.getWorld(), tile.getPos(), face);
            context.assertTrue(receiver != null, "Chute has no native MJ receiver");
            context.assertTrue(receiver.receivePower(100_000, true) == 0, "MJ simulation refused power");
            context.assertTrue(tile.createNbt().getCompound("battery").getLong("stored") == 0, "MJ simulation changed battery");
            receiver.receivePower(100_000, false);
            tile.tick();
            context.assertTrue(count(tile) == 3 && inside.getStack().getCount() == 2, "Wrong pickup count on " + face);
            context.assertTrue(outside.getStack().getCount() == 7 && wrongFace.getStack().getCount() == 9, "Pickup reached outside the mouth on " + face);
            if (face != Direction.UP) context.assertTrue(tile.createNbt().getCompound("battery").getLong("stored") == 0, "Pickup consumed incorrect MJ");
            context.getWorld().removeBlock(tile.getPos(), false);
            context.getWorld().getEntitiesByClass(ItemEntity.class, new Box(tile.getPos()).expand(2), entity -> true).forEach(ItemEntity::discard);
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void fullAndPartlyFullChutesPreserveItemEntities(TestContext context) {
        var tile = place(context, Direction.NORTH);
        for (int slot = 0; slot < 4; slot++) tile.inv.setStackInSlot(slot, new ItemStack(Items.STONE, 64));
        var named = new ItemStack(Items.DIAMOND, 63);
        named.setCustomName(Text.literal("Partial stack"));
        tile.inv.setStackInSlot(3, named.copy());
        var source = item(context, mouth(tile.getPos(), Direction.NORTH), named.copyWithCount(5));
        powerAndTick(context, tile);
        context.assertTrue(source.isAlive() && source.getStack().getCount() == 4 && count(tile) == 256, "Partial insertion lost or duplicated items");
        powerAndTick(context, tile);
        context.assertTrue(source.isAlive() && source.getStack().getCount() == 4 && count(tile) == 256, "Full chute consumed an item entity");
        tile.inv.setStackInSlot(3, ItemStack.EMPTY);
        powerAndTick(context, tile);
        context.assertTrue(source.getStack().getCount() == 1 && tile.inv.getStackInSlot(3).getCount() == 3, "Pickup exceeded its three-item limit");
        powerAndTick(context, tile);
        context.assertTrue(source.isRemoved() && tile.inv.getStackInSlot(3).getCount() == 4, "Empty item entity survived final pickup");
        context.assertTrue(ItemStack.canCombine(tile.inv.getStackInSlot(3), named), "Repeated pickup changed item data");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void outputMovesOneItemPerInventoryAndNeverUsesTheMouth(TestContext context) {
        var tile = place(context, Direction.UP);
        for (Direction side : Direction.values()) context.getWorld().setBlockState(tile.getPos().offset(side), Blocks.BARREL.getDefaultState());
        var named = new ItemStack(Items.DIAMOND, 10);
        named.setCustomName(Text.literal("Output NBT"));
        tile.inv.setStackInSlot(0, named.copy());
        tile.tick();
        for (Direction side : Direction.values()) {
            var inventory = (Inventory) context.getWorld().getBlockEntity(tile.getPos().offset(side));
            int expected = side == Direction.UP ? 0 : 1;
            context.assertTrue(count(inventory) == expected, "Wrong output count on " + side);
            if (expected > 0) context.assertTrue(ItemStack.canCombine(inventory.getStack(0), named), "Output lost item NBT");
            for (int slot = 0; slot < inventory.size(); slot++) inventory.setStack(slot, new ItemStack(Items.DIRT, 64));
        }
        context.assertTrue(count(tile) == 5, "Output duplicated or lost its remainder");
        tile.tick();
        context.assertTrue(count(tile) == 5, "Full destinations consumed source items");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void outputSupportsMinecartsWithoutDoubleVisitingOverlappingFaces(TestContext context) {
        var tile = place(context, Direction.UP);
        var pos = tile.getPos();
        var cart = new ChestMinecartEntity(context.getWorld(), pos.getX() + 1, pos.getY(), pos.getZ() + 1);
        cart.setNoGravity(true);
        context.getWorld().spawnEntity(cart);
        var above = new ChestMinecartEntity(context.getWorld(), pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5);
        above.setNoGravity(true);
        context.getWorld().spawnEntity(above);
        tile.inv.setStackInSlot(0, new ItemStack(Items.DIAMOND, 5));
        tile.tick();
        context.assertTrue(count(cart) == 1 && count(tile) == 4, "One minecart was visited more than once");
        context.assertTrue(count(above) == 0, "Chute output through its mouth into an entity");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void nativeAutomationIsInsertOnlyAndBreakingDropsContentsOnce(TestContext context) {
        var tile = place(context, Direction.UP);
        var resource = ItemVariant.of(Items.DIAMOND);
        for (Direction side : Direction.values()) {
            var storage = ItemStorage.SIDED.find(context.getWorld(), tile.getPos(), side);
            context.assertTrue(storage != null, "Missing inventory on " + side);
            int before = count(tile);
            try (var tx = Transaction.openOuter()) { context.assertTrue(storage.insert(resource, 1, tx) == 1, "Insertion rejected"); }
            context.assertTrue(count(tile) == before, "Aborted insertion persisted");
            try (var tx = Transaction.openOuter()) { storage.insert(resource, 1, tx); tx.commit(); }
            try (var tx = Transaction.openOuter()) { context.assertTrue(storage.extract(resource, 64, tx) == 0, "External extraction bypassed insert-only inventory"); tx.commit(); }
        }
        context.assertTrue(count(tile) == 6, "Unexpected inserted amount");
        context.getWorld().breakBlock(tile.getPos(), true);
        int contents = 0, machines = 0;
        for (var dropped : context.getWorld().getEntitiesByClass(ItemEntity.class, new Box(tile.getPos()).expand(1), ItemEntity::isAlive)) {
            if (dropped.getStack().isOf(Items.DIAMOND)) contents += dropped.getStack().getCount();
            if (dropped.getStack().isOf(BCFactoryBlocks.chute.asItem())) machines += dropped.getStack().getCount();
        }
        context.assertTrue(contents == 6 && machines == 1, "Breaking chute lost/duplicated contents or the machine: contents=" + contents + ", machines=" + machines);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void savedWorkEnergyAndInventorySurviveNativeReload(TestContext context) {
        var tile = place(context, Direction.UP);
        for (int tick = 0; tick < 99; tick++) tile.tick();
        tile.inv.setStackInSlot(0, new ItemStack(Items.GOLD_INGOT, 7));
        ((IMjReceiver) MjAPI.CONNECTOR.find(context.getWorld(), tile.getPos(), Direction.NORTH)).receivePower(25_000, false);
        var saved = tile.createNbt();
        var replacement = (TileChute) tile.getType().instantiate(tile.getPos(), tile.getCachedState());
        replacement.readNbt(saved);
        context.getWorld().removeBlockEntity(tile.getPos());
        context.getWorld().addBlockEntity(replacement);
        context.assertTrue(count(replacement) == 7, "Reload lost inventory");
        context.assertTrue(replacement.createNbt().getCompound("battery").getLong("stored") == 25_000, "Reload lost stored MJ");
        var source = item(context, mouth(tile.getPos(), Direction.UP), new ItemStack(Items.DIAMOND, 3));
        replacement.tick();
        context.assertTrue(source.isRemoved() && count(replacement) == 10, "Reload lost accumulated gravity work");
        context.assertTrue(replacement.createNbt().getCompound("battery").getLong("stored") == 25_000, "Gravity pickup consumed unnecessary MJ");
        var invalid = replacement.createNbt();
        invalid.putInt("progress", Integer.MAX_VALUE);
        replacement.readNbt(invalid);
        replacement.tick();
        context.assertTrue(replacement.createNbt().getInt("progress") == 0, "Oversized saved work was not bounded");
        invalid.putInt("progress", Integer.MIN_VALUE);
        invalid.getCompound("battery").putLong("stored", 0);
        replacement.readNbt(invalid);
        replacement.tick();
        context.assertTrue(replacement.createNbt().getInt("progress") == 1_000, "Negative saved work was not bounded");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void successfulOutputAwardsTheOwningPlayer(TestContext context) {
        var tile = place(context, Direction.UP);
        var player = context.createMockCreativeServerPlayerInWorld();
        tile.onPlacedBy(player, new ItemStack(BCFactoryBlocks.chute));
        context.getWorld().setBlockState(tile.getPos().down(), Blocks.BARREL.getDefaultState());
        tile.inv.setStackInSlot(0, new ItemStack(Items.DIAMOND));
        tile.tick();
        var advancement = context.getWorld().getServer().getAdvancementLoader().get(new Identifier("buildcraftfactory", "retired_hopper"));
        context.assertTrue(advancement != null && player.getAdvancementTracker().getProgress(advancement).isDone(), "Successful output did not grant the owner advancement");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void nativeScreenMovesStacksAndWrenchDoesNotOpenTheScreen(TestContext context) {
        var tile = place(context, Direction.UP);
        var player = context.createMockCreativeServerPlayerInWorld();
        player.refreshPositionAndAngles(tile.getPos(), 0, 0);
        BCFactoryGuis.CHUTE.openGUI(player, tile.getPos());
        context.assertTrue(player.currentScreenHandler instanceof ContainerChute, "Native chute screen did not open");
        var screen = (ContainerChute) player.currentScreenHandler;
        context.assertTrue(screen.getType() == BCFactoryScreens.CHUTE && screen.slots.size() == 40, "Wrong screen type or slot layout");
        player.getInventory().setStack(0, new ItemStack(Items.DIAMOND, 7));
        screen.quickMove(player, 27);
        context.assertTrue(count(tile) == 7 && player.getInventory().getStack(0).isEmpty(), "Shift-click into chute lost items");
        screen.quickMove(player, 36);
        context.assertTrue(count(tile) == 0 && count(player.getInventory()) == 7, "Shift-click out of chute lost items");
        player.refreshPositionAndAngles(tile.getPos().add(20, 0, 0), 0, 0);
        context.assertTrue(!screen.canUse(player) && screen.quickMove(player, 27).isEmpty(), "Distant player retained screen access");
        player.closeHandledScreen();
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(BCCoreItems.wrench));
        var hit = new BlockHitResult(Vec3d.ofCenter(tile.getPos()), Direction.UP, tile.getPos(), false);
        context.assertTrue(!BCCoreItems.wrench.useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, hit)).isAccepted(), "Distant player rotated a machine");
        player.refreshPositionAndAngles(tile.getPos(), 0, 0);
        context.assertTrue(!tile.getCachedState().onUse(context.getWorld(), player, Hand.MAIN_HAND, hit).isAccepted(), "Chute swallowed the wrench interaction");
        context.assertTrue(BCCoreItems.wrench.useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, hit)).isAccepted(), "Native wrench rotation failed");
        context.assertTrue(context.getWorld().getBlockState(tile.getPos()).get(BCFactoryBlocks.chute.getFacingProperty()) != Direction.UP, "Wrench did not change orientation");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void survivalCraftingChainIsLoadedByTheNativeRecipeManager(TestContext context) {
        var player = context.createMockCreativeServerPlayerInWorld();
        var handler = new CraftingScreenHandler(1, player.getInventory(), ScreenHandlerContext.create(context.getWorld(), context.getAbsolutePos(LOCAL)));
        craft(context, handler, new String[] {" s ", "s s", " s "}, java.util.Map.of('s', Items.STICK), BCCoreItems.gearWood);
        craft(context, handler, new String[] {" c ", "cgc", " c "}, java.util.Map.of('c', Items.COBBLESTONE, 'g', BCCoreItems.gearWood), BCCoreItems.gearStone);
        craft(context, handler, new String[] {"ici", "igi", " i "}, java.util.Map.of('i', Items.IRON_INGOT, 'c', Items.CHEST, 'g', BCCoreItems.gearStone), BCFactoryBlocks.chute.asItem());
        craft(context, handler, new String[] {"i i", " g ", " i "}, java.util.Map.of('i', Items.IRON_INGOT, 'g', BCCoreItems.gearStone), BCCoreItems.wrench);
        context.assertTrue(context.getWorld().getServer().getAdvancementLoader().get(new Identifier("buildcraftfactory", "retired_hopper")) != null, "Chute advancement was not loaded");
        context.complete();
    }

    private static void craft(TestContext context, CraftingScreenHandler handler, String[] pattern,
            java.util.Map<Character, net.minecraft.item.Item> ingredients, net.minecraft.item.Item expected) {
        for (int slot = 0; slot < 9; slot++) {
            var item = ingredients.get(pattern[slot / 3].charAt(slot % 3));
            handler.getSlot(slot + 1).setStack(item == null ? ItemStack.EMPTY : new ItemStack(item));
        }
        // The vanilla handler computes its result through the world recipe manager.
        handler.onContentChanged(handler.getSlot(1).inventory);
        context.assertTrue(handler.getSlot(0).getStack().isOf(expected), "Missing or wrong native crafting result: " + expected);
    }
    private static TileChute place(TestContext context, Direction facing) {
        // Reused GameTest worlds may retain inventory entities from an older test
        // layout even after its block template resets. They are real chute outputs:
        // clear only this fixture's mouth/output volume before arranging the test.
        context.getWorld().getOtherEntities(null, new Box(context.getAbsolutePos(LOCAL)).expand(2),
            entity -> entity instanceof ItemEntity || entity instanceof Inventory && !(entity instanceof net.minecraft.entity.LivingEntity))
            .forEach(entity -> {
                // Removing a storage minecart can spill its inventory into the test.
                if (entity instanceof Inventory inventory) inventory.clear();
                entity.discard();
            });
        context.setBlockState(LOCAL, BCFactoryBlocks.chute.getDefaultState().with(BCFactoryBlocks.chute.getFacingProperty(), facing));
        return (TileChute) context.getBlockEntity(LOCAL);
    }
    private static Vec3d mouth(BlockPos pos, Direction side) { return Vec3d.ofCenter(pos).add(Vec3d.of(side.getVector()).multiply(0.625)); }
    private static ItemEntity item(TestContext context, Vec3d pos, ItemStack stack) {
        var entity = new ItemEntity(context.getWorld(), pos.x, pos.y, pos.z, stack.copy());
        entity.setNoGravity(true); entity.setVelocity(Vec3d.ZERO);
        context.getWorld().spawnEntity(entity); return entity;
    }
    private static void powerAndTick(TestContext context, TileChute tile) {
        ((IMjReceiver) MjAPI.CONNECTOR.find(context.getWorld(), tile.getPos(), Direction.SOUTH)).receivePower(100_000, false);
        tile.tick();
    }
    private static int count(TileChute tile) { return tile.inv.stacks.stream().mapToInt(ItemStack::getCount).sum(); }
    private static int count(Inventory inventory) {
        int total = 0; for (int slot = 0; slot < inventory.size(); slot++) total += inventory.getStack(slot).getCount(); return total;
    }
}
