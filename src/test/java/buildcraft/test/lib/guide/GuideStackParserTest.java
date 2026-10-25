package buildcraft.test.lib.guide;

import static org.junit.Assert.*;
import org.junit.Test;
import net.minecraft.item.Items;
import buildcraft.lib.client.guide.loader.GuideStackParser;

public class GuideStackParserTest {
    @Test public void acceptsPlainAndWrappedStacksWithoutSharingMutableNbt() {
        var first = GuideStackParser.parse("(minecraft:stone)").get();
        var second = GuideStackParser.parse("{minecraft:stone,2,0,{label:\"x,y\",values:[1,2,3]}}").get();
        assertTrue(first.isOf(Items.STONE));
        assertFalse(first.hasNbt());
        assertEquals(2, second.getCount());
        assertEquals("x,y", second.getNbt().getString("label"));
        assertEquals(3, second.getNbt().getList("values", 3).size());
    }

    @Test public void invalidRegistryIdsCountsAndNbtAreReportedAsDisabled() {
        for (String input : new String[] { "missing:item", "NOT VALID", "minecraft:air", "minecraft:stone,0",
                "minecraft:stone,65", "minecraft:stone,bad", "minecraft:stone,1,0,{broken" }) {
            var parsed = GuideStackParser.parse(input);
            assertFalse(input, parsed.isPresent());
            assertFalse(parsed.getDisabledReason().isBlank());
        }
    }

    @Test public void damageableItemsRetainDamageAndExplicitNbt() {
        var stack = GuideStackParser.parse("minecraft:diamond_pickaxe,1,12,{Unbreakable:1b}").get();
        assertEquals(12, stack.getDamage());
        assertTrue(stack.getNbt().getBoolean("Unbreakable"));
    }
}
