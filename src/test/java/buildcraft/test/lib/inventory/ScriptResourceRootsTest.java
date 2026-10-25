package buildcraft.test.lib.inventory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import buildcraft.lib.script.ScriptResourceRoots;
import net.minecraft.resource.DirectoryResourcePack;
import net.minecraft.resource.LifecycledResourceManagerImpl;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class ScriptResourceRootsTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private Path pack(String name, String prefix, String text) throws Exception {
        Path root = temporary.newFolder(name).toPath();
        Path script = root.resolve(prefix + "/sample/compat/buildcraft/recipes.txt");
        Files.createDirectories(script.getParent());
        Files.writeString(script, text);
        return root;
    }

    @Test public void dataPackScriptsAndAdjacentJsonKeepTheirNamespaces() throws Exception {
        Path pack = pack("data", "data", "~{buildcraft/json/insn}\nadd \"recipe\"");
        Path recipe = pack.resolve("data/sample/compat/buildcraft/recipes/recipe.json");
        Files.createDirectories(recipe.getParent());
        Files.writeString(recipe, "{\"count\":3}");
        try (var manager = new LifecycledResourceManagerImpl(ResourceType.SERVER_DATA,
            List.of(new DirectoryResourcePack("test data", pack, false)))) {
            List<Path> copied;
            try (ScriptResourceRoots sources = ScriptResourceRoots.open("data", manager)) {
                copied = sources.roots();
                assertEquals(1, copied.size());
                assertEquals("{\"count\":3}", Files.readString(copied.get(0)
                    .resolve("sample/compat/buildcraft/recipes/recipe.json")));
            }
            assertFalse(Files.exists(copied.get(0)));
            assertTrue(Files.exists(recipe));
            assertTrue(manager.getResource(new Identifier("sample", "compat/buildcraft/recipes.txt")).isPresent());
        }
    }

    @Test public void distinctPacksWithSameResourceDoNotOverwriteEachOther() throws Exception {
        Path low = pack("low", "assets", "low");
        Path high = pack("high", "assets", "high");
        try (var manager = new LifecycledResourceManagerImpl(ResourceType.CLIENT_RESOURCES,
            List.of(new DirectoryResourcePack("low", low, false), new DirectoryResourcePack("high", high, false)));
            ScriptResourceRoots sources = ScriptResourceRoots.open("assets", manager)) {
            assertEquals(2, sources.roots().size());
            assertEquals("low", Files.readString(sources.roots().get(0).resolve("sample/compat/buildcraft/recipes.txt")));
            assertEquals("high", Files.readString(sources.roots().get(1).resolve("sample/compat/buildcraft/recipes.txt")));
        }
    }

    @Test public void emptyResourceManagerHasNoPhantomRoots() throws Exception {
        try (var manager = new LifecycledResourceManagerImpl(ResourceType.SERVER_DATA, List.of());
            ScriptResourceRoots sources = ScriptResourceRoots.open("data", manager)) {
            assertTrue(sources.roots().isEmpty());
        }
    }
}
