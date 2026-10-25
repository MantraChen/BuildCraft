package buildcraft.test.lib.inventory;

import java.util.*;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import org.junit.*;
import static org.junit.Assert.*;
import buildcraft.lib.list.ListMatchHandlerTags;
import buildcraft.api.lists.ListMatchHandler.Type;

public class TagListHandlerTest {
    private static final Item ALT_IRON = Registry.register(Registries.ITEM,
        new Identifier("buildcraft_port_tests", "other_iron_ingot"), new Item(new Item.Settings()));
    private final ListMatchHandlerTags handler = new ListMatchHandlerTags();
    private Map<TagKey<Item>, List<RegistryEntry<Item>>> original;
    @Before public void snapshotTags() {
        original = new HashMap<>();
        Registries.ITEM.streamTagsAndEntries().forEach(pair -> original.put(pair.getFirst(), pair.getSecond().stream().toList()));
    }
    @After public void restoreTags() {
        Registries.ITEM.streamTags().forEach(tag -> original.putIfAbsent(tag, List.of()));
        Registries.ITEM.populateTags(original);
    }
    private static TagKey<Item> tag(String id) { return TagKey.of(RegistryKeys.ITEM, new Identifier(id)); }
    private static List<RegistryEntry<Item>> entries(Item... items) { return Arrays.stream(items).map(Registries.ITEM::getEntry).toList(); }
    private void bind(Map<TagKey<Item>, List<RegistryEntry<Item>>> tags) {
        Map<TagKey<Item>, List<RegistryEntry<Item>>> all = new HashMap<>(original); all.putAll(tags); Registries.ITEM.populateTags(all);
    }
    private boolean matches(Type type, Item source, Item target) { return handler.matches(type, source.getDefaultStack(), target.getDefaultStack(), false); }
    @Test public void formMaterialAndClassWorkAcrossCommonTagConventions() {
        bind(Map.of(tag("c:iron_ingots"), entries(Items.IRON_INGOT), tag("forge:ingots/iron"), entries(ALT_IRON),
            tag("minecraft:iron_ores"), entries(Items.IRON_ORE), tag("c:gold_ingots"), entries(Items.GOLD_INGOT)));
        assertTrue(matches(Type.CLASS, Items.IRON_INGOT, ALT_IRON));
        assertTrue(matches(Type.MATERIAL, Items.IRON_INGOT, Items.IRON_ORE));
        assertTrue(matches(Type.TYPE, Items.IRON_INGOT, Items.GOLD_INGOT));
        assertFalse(matches(Type.CLASS, Items.IRON_INGOT, Items.IRON_ORE));
        assertFalse(matches(Type.MATERIAL, Items.IRON_INGOT, Items.GOLD_INGOT));
    }
    @Test public void broadTagsAndUnrelatedGameplayTagsDoNotInventMaterials() {
        bind(Map.of(tag("c:ingots"), entries(Items.IRON_INGOT, Items.GOLD_INGOT),
            tag("minecraft:beacon_payment_items"), entries(Items.IRON_INGOT, Items.DIAMOND)));
        assertTrue(matches(Type.TYPE, Items.IRON_INGOT, Items.GOLD_INGOT));
        assertFalse(handler.isValidSource(Type.MATERIAL, Items.IRON_INGOT.getDefaultStack()));
        assertFalse(handler.isValidSource(Type.CLASS, Items.IRON_INGOT.getDefaultStack()));
        assertFalse(matches(Type.TYPE, Items.IRON_INGOT, Items.DIAMOND));
        assertFalse(handler.isValidSource(Type.TYPE, ItemStack.EMPTY));
    }
    @Test public void reloadedMembershipImmediatelyChangesMatchesAndExamples() {
        var iron = tag("c:iron_ingots");
        bind(Map.of(iron, entries(Items.IRON_INGOT, ALT_IRON)));
        assertTrue(matches(Type.CLASS, Items.IRON_INGOT, ALT_IRON));
        assertTrue(handler.getClientExamples(Type.CLASS, Items.IRON_INGOT.getDefaultStack()).stream().anyMatch(stack -> stack.isOf(ALT_IRON)));
        bind(Map.of(iron, entries(Items.IRON_INGOT)));
        assertFalse(matches(Type.CLASS, Items.IRON_INGOT, ALT_IRON));
        assertFalse(handler.getClientExamples(Type.CLASS, Items.IRON_INGOT.getDefaultStack()).stream().anyMatch(stack -> stack.isOf(ALT_IRON)));
    }
}
