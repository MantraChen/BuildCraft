package buildcraft.test.lib.config;

import buildcraft.lib.config.*;
import buildcraft.lib.misc.ConfigUtil;
import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class ConfigurationTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private File file() { return new File(temporary.getRoot(), "buildcraft/main.cfg"); }
    private File input(String content) throws Exception {
        File file = file();
        Files.createDirectories(file.toPath().getParent());
        Files.writeString(file.toPath(), content);
        return file;
    }

    @Test public void defaultsEditsAndRestartSurviveDiskRoundTrip() {
        File file = file();
        Configuration config = new Configuration(file);
        Property enabled = config.get("general", "enabled", true);
        assertSame(enabled, config.get("general", "enabled", true));
        config.get("worldgen.oil", "rate", 1.5).set(2.75);
        enabled.set(false);
        assertTrue(config.hasChanged());
        config.save();
        assertFalse(config.hasChanged());
        Configuration restarted = new Configuration(file);
        assertFalse(restarted.get("general", "enabled", true).getBoolean());
        assertEquals(2.75, restarted.get("worldgen.oil", "rate", 1.5).getDouble(), 0);
        assertFalse(restarted.hasChanged());
        enabled.setToDefault();
        assertTrue(enabled.getBoolean());
        assertTrue(config.hasChanged());
    }

    @Test public void readsHistoricalNestedCategoriesAndIntegerLists() throws Exception {
        Configuration config = new Configuration(input("# Configuration file\n~CONFIG_VERSION: 1\nworldgen {\n oil {\n B:enable=false\n I:excludedDimensions <\n -1\n 1\n >\n }\n}\n"));
        assertFalse(config.get("worldgen.oil", "enable", true).getBoolean());
        assertArrayEquals(new int[] {-1, 1}, config.get("worldgen.oil", "excludedDimensions", new int[0]).getIntList());
        assertSame(config.getCategory("worldgen"), config.getCategory("worldgen.oil").parent);
        config.save();
        assertArrayEquals(new int[] {-1, 1}, new Configuration(file()).get("worldgen.oil", "excludedDimensions", new int[0]).getIntList());
    }

    @Test public void listAndStringValuesDoNotLoseCommasWhitespaceOrQuotes() {
        Configuration config = new Configuration(file());
        String[] values = {"minecraft:stone", "a,b", "", "  spaced  ", "#comment", ">", "quote\" and \\slash"};
        config.get("general", "list", values);
        config.get("general", "label", "  quoted \"text\"  ");
        config.save();
        Configuration read = new Configuration(file());
        assertArrayEquals(values, read.get("general", "list", new String[0]).getStringList());
        assertEquals("  quoted \"text\"  ", read.get("general", "label", "default").getString());
    }

    @Test public void listArraysCannotMutateStoredValuesOrDefaults() {
        String[] values = {"one"};
        Property property = new Property("list", values, Property.Type.STRING);
        values[0] = "bad";
        property.getStringList()[0] = "bad";
        assertArrayEquals(new String[] {"one"}, property.getStringList());
        property.set(new String[] {"two"});
        property.setToDefault();
        assertArrayEquals(new String[] {"one"}, property.getStringList());
    }

    @Test public void badNumbersAndBooleanUseDeclaredDefaultsAndBounds() throws Exception {
        Configuration config = new Configuration(input("general {\n I:count=not-a-number\n D:rate=NaN\n B:enabled=typo\n I:range=999\n I:list <\n bad\n >\n}\n"));
        assertEquals(4, config.get("general", "count", 4).getInt());
        assertEquals(0.5, config.get("general", "rate", 0.5).getDouble(), 0);
        assertTrue(config.get("general", "enabled", true).getBoolean());
        assertEquals(20, config.get("general", "range", 4).setMinValue(1).setMaxValue(20).getInt());
        assertArrayEquals(new int[] {-1, 1}, config.get("general", "list", new int[] {-1, 1}).getIntList());
        assertTrue(config.hasChanged());
        config.save();
        assertFalse(config.hasChanged());
    }

    @Test public void loadingAgainUpdatesAlreadyBoundProperties() throws Exception {
        File file = input("general {\n B:enabled=true\n}\n");
        Configuration config = new Configuration(file);
        Property bound = config.get("general", "enabled", true);
        Files.writeString(file.toPath(), "general {\n B:enabled=false\n}\n");
        config.load();
        assertSame(bound, config.get("general", "enabled", true));
        assertFalse(bound.getBoolean());
        assertFalse(config.hasChanged());
    }

    @Test public void movingOldKeysPreservesBoundDefaultsAndExplicitModernValues() throws Exception {
        Configuration config = new Configuration(input("worldgen {\n B:enableOilGen=false\n}\n"));
        Property bound = config.get("worldgen.oil", "enable", true);
        config.moveProperty("worldgen", "enableOilGen", "worldgen.oil");
        config.renameProperty("worldgen.oil", "enableOilGen", "enable");
        assertFalse(bound.getBoolean());
        assertFalse(config.contains("worldgen", "enableOilGen"));
        assertFalse(config.contains("worldgen.oil", "enableOilGen"));
        config.save();
        assertFalse(new Configuration(file()).get("worldgen.oil", "enable", true).getBoolean());
        bound.setToDefault();
        assertTrue(bound.getBoolean());
    }

    @Test public void explicitModernValueWinsOverLegacyAlias() throws Exception {
        Configuration config = new Configuration(input("worldgen {\n B:enableOilGen=false\n oil {\n B:enable=true\n }\n}\n"));
        Property bound = config.get("worldgen.oil", "enable", false);
        config.moveProperty("worldgen", "enableOilGen", "worldgen.oil");
        config.renameProperty("worldgen.oil", "enableOilGen", "enable");
        assertTrue(bound.getBoolean());
    }

    @Test public void removingCategoryRemovesDescendantsAndPersists() {
        Configuration config = new Configuration(file());
        config.get("one.two.three", "value", true);
        config.get("other", "value", true);
        config.save();
        config.removeCategory(config.getCategory("one"));
        assertFalse(config.hasCategory("one.two.three"));
        assertTrue(config.hasChanged());
        config.save();
        Configuration restarted = new Configuration(file());
        assertEquals(Arrays.asList("other"), restarted.getCategoryNames());
    }

    @Test public void malformedFilesFailWithoutOverwritingUserData() throws Exception {
        File file = input("general {\n I:list <\n 1\n");
        String original = Files.readString(file.toPath());
        assertThrows(IllegalArgumentException.class, () -> new Configuration(file));
        assertEquals(original, Files.readString(file.toPath()));
    }

    @Test public void restartAndLanguageMetadataAreRetained() {
        Configuration config = new Configuration(file());
        Property property = config.get("worldgen.oil", "enable", true);
        EnumRestartRequirement.GAME.setTo(property);
        ConfigUtil.setLang(config);
        config.getCategory("worldgen.oil").setShowInGui(false);
        assertEquals("config.worldgen.oil.enable", property.getLanguageKey());
        assertTrue(property.requiresMcRestart());
        assertTrue(property.requiresWorldRestart());
        assertFalse(config.getCategory("worldgen.oil").showInGui());
        assertFalse(EnumRestartRequirement.GAME.hasBeenRestarted(EnumRestartRequirement.WORLD));
    }

    @Test public void enumPrefixDoesNotReadPastShortCandidate() {
        Property property = new Property("mode", "az", Property.Type.STRING);
        assertEquals(Mode.DEFAULT, ConfigUtil.parseEnumForConfig(property, Mode.DEFAULT));
    }
    private enum Mode { A, AB, DEFAULT }

    @Test public void reloadRepairsTypeChangesUsingTheBoundSchema() throws Exception {
        File file = input("general {\n B:enabled=false\n}\n");
        Configuration config = new Configuration(file);
        Property bound = config.get("general", "enabled", true);
        Files.writeString(file.toPath(), "general {\n S:enabled=garbage\n}\n");
        config.load();
        assertTrue(bound.getBoolean());
        assertTrue(config.hasChanged());
        config.save();
        assertFalse(config.hasChanged());
    }
}
