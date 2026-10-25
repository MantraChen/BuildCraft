package buildcraft.test.lib.world;

import buildcraft.lib.misc.BlockUtil;
import buildcraft.lib.misc.FakePlayerProvider;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.ItemEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

public class MiningGameTests implements FabricGameTest {
    private static final BlockPos POS = new BlockPos(3, 2, 3);
    private static final GameProfile OWNER = new GameProfile(UUID.fromString("b9a33be0-f0f4-41a5-a998-17c70ab41d8c"), "MiningTest");
    private static final GameProfile DENIED = new GameProfile(UUID.fromString("b9a33be0-f0f4-41a5-a998-17c70ab41d8d"), "DeniedTest");

    static {
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, entity) -> !player.getUuid().equals(DENIED.getId()));
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void mineralDropsAreCollectedOnce(TestContext context) {
        context.setBlockState(POS, Blocks.DIAMOND_ORE);
        List<ItemStack> drops = mine(context, pickaxe(), false).orElseThrow();
        context.assertTrue(count(drops, Items.DIAMOND) == 1, "Diamond ore must return exactly one diamond");
        context.expectBlock(Blocks.AIR, POS);
        context.assertTrue(nearbyItems(context).isEmpty(), "Collected ore must not remain in the world");
        context.assertTrue(mine(context, pickaxe(), false).isEmpty(), "Mining air must not duplicate drops");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void silkTouchAndToolCopyArePreserved(TestContext context) {
        context.setBlockState(POS, Blocks.DIAMOND_ORE);
        ItemStack tool = pickaxe();
        tool.addEnchantment(Enchantments.SILK_TOUCH, 1);
        List<ItemStack> drops = mine(context, tool, false).orElseThrow();
        context.assertTrue(count(drops, Items.DIAMOND_ORE) == 1 && count(drops, Items.DIAMOND) == 0, "Silk Touch must reach the loot table");
        context.assertTrue(tool.getDamage() == 0, "A quarry's simulated tool must not mutate the supplied stack");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void chestContentsAreNotLostOrDuplicated(TestContext context) {
        context.setBlockState(POS, Blocks.CHEST);
        ChestBlockEntity chest = (ChestBlockEntity) context.getBlockEntity(POS);
        ItemStack named = new ItemStack(Items.DIAMOND, 7);
        named.getOrCreateNbt().putString("buildcraft_test", "preserved");
        chest.setStack(0, named);
        chest.setStack(1, new ItemStack(Items.IRON_INGOT, 13));
        List<ItemStack> drops = mine(context, pickaxe(), false).orElseThrow();
        context.assertTrue(count(drops, Items.CHEST) == 1 && count(drops, Items.DIAMOND) == 7
            && count(drops, Items.IRON_INGOT) == 13, "Chest and every inventory item must be collected once");
        context.assertTrue(drops.stream().filter(s -> s.isOf(Items.DIAMOND)).allMatch(s -> s.hasNbt()
            && "preserved".equals(s.getNbt().getString("buildcraft_test"))), "Dropped stacks must preserve NBT");
        context.assertTrue(nearbyItems(context).isEmpty(), "Chest drops must not remain in the world");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void existingItemsAreOnlyCollectedWhenRequested(TestContext context) {
        context.setBlockState(POS, Blocks.STONE);
        ItemEntity existing = context.spawnItem(Items.GOLD_INGOT, POS);
        List<ItemStack> drops = mine(context, pickaxe(), false).orElseThrow();
        context.assertTrue(count(drops, Items.COBBLESTONE) == 1 && count(drops, Items.GOLD_INGOT) == 0,
            "Normal harvesting must leave unrelated items alone");
        context.assertTrue(!existing.isRemoved(), "Existing item was removed");
        context.setBlockState(POS, Blocks.STONE);
        List<ItemStack> all = mine(context, pickaxe(), true).orElseThrow();
        context.assertTrue(count(all, Items.COBBLESTONE) == 1 && count(all, Items.GOLD_INGOT) == 1,
            "grabAll must collect both new and existing drops");
        context.assertTrue(existing.isRemoved(), "Collected item was not removed");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void protectionCancellationKeepsBlockAndContents(TestContext context) {
        context.setBlockState(POS, Blocks.CHEST);
        ChestBlockEntity chest = (ChestBlockEntity) context.getBlockEntity(POS);
        chest.setStack(0, new ItemStack(Items.DIAMOND, 9));
        Optional<List<ItemStack>> drops = BlockUtil.breakBlockAndGetDrops(context.getWorld(),
            context.getAbsolutePos(POS), pickaxe(), DENIED, true);
        context.assertTrue(drops.isEmpty(), "Fabric break cancellation must stop harvesting");
        context.expectBlock(Blocks.CHEST, POS);
        context.assertTrue(chest.getStack(0).getCount() == 9 && nearbyItems(context).isEmpty(), "Cancelled break must preserve inventory");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void unbreakableAndWrongToolDoNotDestroyBlocks(TestContext context) {
        context.setBlockState(POS, Blocks.BEDROCK);
        context.assertTrue(mine(context, pickaxe(), true).isEmpty(), "Bedrock must be refused");
        context.expectBlock(Blocks.BEDROCK, POS);
        context.setBlockState(POS, Blocks.DIAMOND_ORE);
        context.assertTrue(mine(context, new ItemStack(Items.WOODEN_PICKAXE), false).isEmpty(), "Insufficient tools must not destroy ore");
        context.expectBlock(Blocks.DIAMOND_ORE, POS);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void fakePlayerKeepsOwnerAndRestoresHeldItem(TestContext context) {
        FakePlayer player = FakePlayerProvider.INSTANCE.getFakePlayer(context.getWorld(), OWNER);
        ItemStack held = new ItemStack(Items.STICK, 3);
        player.setStackInHand(Hand.MAIN_HAND, held);
        context.setBlockState(POS, Blocks.STONE);
        mine(context, pickaxe(), false).orElseThrow();
        context.assertTrue(player.getUuid().equals(OWNER.getId()) && !player.getGameProfile().getName().equals(OWNER.getName()),
            "Fake player must retain the owner's UUID with a distinct machine name");
        context.assertTrue(player.getMainHandStack() == held, "Temporary mining tool leaked into the cached player");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void fluidDetectionIncludesFlowingAndWaterloggedBlocks(TestContext context) {
        BlockPos absolute = context.getAbsolutePos(POS);
        context.setBlockState(POS, Fluids.FLOWING_WATER.getFlowing(3, false).getBlockState());
        context.assertTrue(BlockUtil.getFluidWithFlowing(context.getWorld(), absolute) == Fluids.WATER, "Flowing water must normalize to water");
        context.setBlockState(POS, Blocks.OAK_SLAB.getDefaultState().with(net.minecraft.state.property.Properties.WATERLOGGED, true));
        context.assertTrue(BlockUtil.getFluidWithFlowing(context.getWorld(), absolute) == Fluids.WATER, "Waterlogged block must report water");
        context.setBlockState(POS, Blocks.STONE);
        context.assertTrue(BlockUtil.getFluidWithFlowing(context.getWorld(), absolute) == null, "Dry block must not report fluid");
        context.complete();
    }

    private static ItemStack pickaxe() { return new ItemStack(Items.DIAMOND_PICKAXE); }
    private static int count(List<ItemStack> stacks, Item item) {
        return stacks.stream().filter(s -> s.isOf(item)).mapToInt(ItemStack::getCount).sum();
    }
    private static Optional<List<ItemStack>> mine(TestContext context, ItemStack tool, boolean grabAll) {
        return BlockUtil.breakBlockAndGetDrops(context.getWorld(), context.getAbsolutePos(POS), tool, OWNER, grabAll);
    }
    private static List<ItemEntity> nearbyItems(TestContext context) {
        return context.getWorld().getEntitiesByClass(ItemEntity.class, new Box(context.getAbsolutePos(POS)).expand(1), e -> !e.isRemoved());
    }
}
