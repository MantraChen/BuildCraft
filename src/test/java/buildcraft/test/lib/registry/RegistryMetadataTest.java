package buildcraft.test.lib.registry;

import buildcraft.lib.BCLibTags;
import buildcraft.core.BCCoreTags;
import buildcraft.energy.BCEnergyTags;
import buildcraft.factory.BCFactoryTags;
import buildcraft.builders.BCBuildersTags;
import buildcraft.robotics.BCRoboticsTags;
import buildcraft.silicon.BCSiliconTags;
import buildcraft.lib.registry.TagManager;
import buildcraft.lib.registry.TagManager.EnumTagType;
import buildcraft.lib.registry.TagManager.EnumTagTypeMulti;
import org.junit.Test;
import static org.junit.Assert.*;

public class RegistryMetadataTest {
    @Test public void nativeMetadataIsIndependentAndRepeatable() {
        for (int pass = 0; pass < 2; pass++) {
            BCLibTags.register();
            BCCoreTags.register();
            BCEnergyTags.register();
            BCFactoryTags.register();
            BCSiliconTags.register();
            BCBuildersTags.register();
            BCRoboticsTags.register();
            assertEquals("buildcraftlib:guide", id("item.guide"));
            assertEquals("buildcraftcore:engine.wood", id("tile.engine.wood"));
            assertEquals("buildcraftenergy:engine.iron", id("tile.engine.iron"));
            assertEquals("buildcraftfactory:tank", id("block.tank"));
            assertEquals("buildcraftsilicon:laser", id("tile.laser"));
            assertEquals("buildcraftbuilders:quarry", id("block.quarry"));
            assertEquals("buildcraftrobotics:zone_planner", id("block.zone_planner"));
            assertEquals(id("block.engine.bc"), id("item.block.engine.bc"));
            assertArrayEquals(new String[] {"wrenchItem"}, TagManager.getMultiTag("item.wrench", EnumTagTypeMulti.OLD_REGISTRY_NAME));
        }
    }
    private static String id(String tag) { return TagManager.getTag(tag, EnumTagType.REGISTRY_NAME); }
}
