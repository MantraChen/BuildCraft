package buildcraft.test.lib.inventory;

import buildcraft.api.items.IList;
import buildcraft.api.lists.ListMatchHandler;
import buildcraft.api.lists.ListRegistry;
import buildcraft.api.recipes.IngredientStack;
import buildcraft.lib.list.ListHandler;
import buildcraft.lib.list.ListMatchHandlerClass;
import buildcraft.lib.misc.StackUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

public class ListHandlerTest {
    @BeforeClass public static void registerHandlers() { ListRegistry.registerHandler(new ListMatchHandlerClass()); }

    @Test public void listRowsAndOptionsSurviveSaveReload() {
        ItemStack list = new ItemStack(Items.PAPER);
        ListHandler.Line[] lines = ListHandler.getLines(list);
        lines[0].setStack(3, new ItemStack(Items.DIAMOND, 50));
        lines[0].precise = true;
        lines[1].setStack(0, new ItemStack(Items.APPLE));
        lines[1].byType = true;
        ListHandler.saveLines(list, lines);
        ListHandler.Line[] restored = ListHandler.getLines(ItemStack.fromNbt(list.writeNbt(new NbtCompound())));
        assertEquals(Items.DIAMOND, restored[0].getStack(3).getItem());
        assertEquals(1, restored[0].getStack(3).getCount());
        assertTrue(restored[0].precise);
        assertTrue(restored[1].byType);
        assertTrue(ListHandler.hasItems(list));
    }

    @Test public void readingOrMatchingBlankListDoesNotAttachNbt() {
        ItemStack list = new ItemStack(Items.PAPER);
        assertTrue(ListHandler.isDefault(list));
        assertFalse(ListHandler.hasItems(list));
        assertFalse(ListHandler.matches(list, new ItemStack(Items.DIAMOND)));
        assertNull(list.getNbt());
    }

    @Test public void clearingListPreservesLabelAndUnrelatedData() {
        ItemStack list = new ItemStack(Items.PAPER);
        list.getOrCreateNbt().putString("label", "Mining output");
        ListHandler.Line[] lines = ListHandler.getLines(list);
        lines[0].setStack(0, new ItemStack(Items.DIAMOND));
        ListHandler.saveLines(list, lines);
        ListHandler.saveLines(list, new ListHandler.Line[] {new ListHandler.Line(), new ListHandler.Line()});
        assertEquals("Mining output", list.getNbt().getString("label"));
        assertFalse(list.getNbt().contains("lines"));
        assertFalse(list.getNbt().contains("written"));
    }

    @Test public void preciseModeIncludesNbtAndDamageButIgnoresStackCount() {
        ListHandler.Line line = new ListHandler.Line();
        ItemStack source = new ItemStack(Items.DIAMOND_PICKAXE);
        source.setDamage(5);
        source.getOrCreateNbt().putString("name", "mine");
        line.setStack(0, source);
        ItemStack different = source.copy();
        different.setDamage(30);
        assertTrue(line.matches(different));
        line.precise = true;
        assertFalse(line.matches(different));
        ItemStack equal = source.copy();
        equal.setCount(2);
        assertTrue(line.matches(equal));
        equal.getOrCreateNbt().putString("name", "other");
        assertFalse(line.matches(equal));
        assertFalse(line.matches(ItemStack.EMPTY));
    }

    @Test public void oneStackModeClearsExtraSlotsWithoutChangingInput() {
        ListHandler.Line line = new ListHandler.Line();
        ItemStack input = new ItemStack(Items.APPLE, 18);
        line.setStack(0, input);
        line.setStack(4, new ItemStack(Items.STONE));
        line.toggleOption(1);
        assertTrue(line.isOneStackMode());
        assertTrue(line.getStack(4).isEmpty());
        assertEquals(18, input.getCount());
        assertTrue(line.matches(new ItemStack(Items.BREAD)));
        assertFalse(line.matches(new ItemStack(Items.STONE)));
        line.setStack(4, new ItemStack(Items.DIAMOND));
        assertTrue(line.getStack(4).isEmpty());
    }

    @Test public void oversizedSavedRowsCannotOverflowFixedSlots() {
        NbtCompound data = new NbtCompound();
        NbtList stacks = new NbtList();
        for (int i = 0; i < 40; i++) stacks.add(new ItemStack(Items.DIAMOND).writeNbt(new NbtCompound()));
        data.put("st", stacks);
        assertEquals(ListHandler.WIDTH, ListHandler.Line.fromNBT(data).stacks.size());
        ItemStack list = new ItemStack(Items.PAPER);
        NbtList rows = new NbtList();
        for (int i = 0; i < 20; i++) rows.add(data.copy());
        list.getOrCreateNbt().putBoolean("written", true);
        list.getOrCreateNbt().put("lines", rows);
        assertEquals(ListHandler.HEIGHT, ListHandler.getLines(list).length);
    }

    @Test public void listItemsParticipateInInventoryMatching() {
        Item listItem = new TestListItem();
        ItemStack list = new ItemStack(listItem);
        ItemStack matching = new ItemStack(Items.DIAMOND);
        ItemStack other = new ItemStack(Items.STONE);
        assertTrue(StackUtil.matchesStackOrList(list, matching));
        assertTrue(StackUtil.isMatchingItemOrList(list, matching));
        assertFalse(StackUtil.matchesStackOrList(list, other));
        assertFalse(StackUtil.isMatchingItemOrList(list, ItemStack.EMPTY));
    }

    @Test public void damageAndNbtFlagsAreIndependentAndDoNotMutateInputs() {
        ItemStack one = new ItemStack(Items.IRON_PICKAXE);
        ItemStack two = one.copy();
        one.setDamage(10);
        two.setDamage(20);
        NbtCompound before = one.getNbt().copy();
        assertTrue(StackUtil.isMatchingItem(one, two, false, false));
        assertFalse(StackUtil.isMatchingItem(one, two, true, false));
        assertTrue(StackUtil.isMatchingItem(one, two, false, true));
        two.getOrCreateNbt().putString("owner", "different");
        assertFalse(StackUtil.isMatchingItem(one, two, false, true));
        assertEquals(before, one.getNbt());
    }

    @Test public void ingredientFactoriesMatchActualItemsAndCounts() {
        IngredientStack fromItem = IngredientStack.of(Items.DIAMOND);
        assertTrue(fromItem.ingredient.test(new ItemStack(Items.DIAMOND)));
        assertFalse(fromItem.ingredient.test(new ItemStack(Items.STONE)));
        IngredientStack required = new IngredientStack(IngredientStack.of("minecraft:diamond").ingredient, 3);
        assertFalse(StackUtil.contains(required, new ItemStack(Items.DIAMOND, 2)));
        assertTrue(StackUtil.contains(required, new ItemStack(Items.DIAMOND, 3)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void unknownIngredientDoesNotSilentlyBecomeEmpty() { IngredientStack.of("buildcraft_test:missing"); }

    @Test public void invalidItemIdentifierDoesNotSilentlyBecomeAir() {
        assertNull(StackUtil.getItemByNameOrId("NOT AN ID"));
        assertNull(StackUtil.getItemByNameOrId("buildcraft_test:missing"));
        assertEquals(Items.DIAMOND, StackUtil.getItemByNameOrId("minecraft:diamond"));
    }

    private static final class TestListItem extends Item implements IList {
        TestListItem() { super(new Settings()); }
        @Override public String getLabel(ItemStack stack) { return "test"; }
        @Override public boolean setName(ItemStack stack, String name) { return true; }
        @Override public boolean matches(ItemStack list, ItemStack item) { return item.isOf(Items.DIAMOND); }
    }
}
