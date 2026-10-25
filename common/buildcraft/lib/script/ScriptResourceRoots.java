package buildcraft.lib.script;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

/** Adapts native resource packs to the existing path-based script parser. */
public final class ScriptResourceRoots implements AutoCloseable {
    private final List<Path> roots = new ArrayList<>();
    private Path temporary;

    public static ScriptResourceRoots open(String prefix, ResourceManager manager) throws IOException {
        ScriptResourceRoots sources = new ScriptResourceRoots();
        try {
            if (manager == null) {
                // Loader owns these file systems. Never close them ourselves.
                for (ModContainer mod : FabricLoader.getInstance().getAllMods().stream()
                    .sorted(Comparator.comparing(m -> m.getMetadata().getId())).toList()) {
                    for (Path root : mod.getRootPaths()) {
                        Path contents = root.resolve(prefix);
                        if (Files.isDirectory(contents)) sources.roots.add(contents);
                    }
                }
            } else {
                sources.temporary = Files.createTempDirectory("buildcraft-scripts-");
                Map<String, Path> packs = new LinkedHashMap<>();
                List<Map.Entry<Identifier, List<Resource>>> entries = new ArrayList<>(
                    manager.findAllResources("compat", id -> true).entrySet());
                entries.sort(Map.Entry.comparingByKey());
                for (Map.Entry<Identifier, List<Resource>> entry : entries) {
                    for (Resource resource : entry.getValue()) {
                        String packName = resource.getResourcePackName();
                        Path root = packs.get(packName);
                        if (root == null) {
                            root = sources.temporary.resolve(Integer.toString(packs.size()));
                            packs.put(packName, root);
                            sources.roots.add(root);
                        }
                        Identifier id = entry.getKey();
                        Path target = root.resolve(id.getNamespace()).resolve(id.getPath()).normalize();
                        if (!target.startsWith(root)) throw new IOException("Invalid script resource path: " + id);
                        Files.createDirectories(target.getParent());
                        try (InputStream input = resource.getInputStream()) {
                            Files.copy(input, target);
                        }
                    }
                }
            }
            return sources;
        } catch (IOException | RuntimeException failure) {
            try { sources.close(); } catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    public List<Path> roots() { return List.copyOf(roots); }

    @Override public void close() throws IOException {
        if (temporary == null) return;
        // Only delete the private directory created by this instance; mod roots are untouched.
        try (var paths = Files.walk(temporary)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }
        temporary = null;
    }
}
