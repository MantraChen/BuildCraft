/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.builders.snapshot;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import buildcraft.lib.misc.JsonUtil;

/** Original compatibility rules plus native data-pack additions, published only after a complete load. */
public final class RulesLoader {
    private static final Gson GSON = JsonUtil.registerNbtSerializersDeserializers(new GsonBuilder())
        .registerTypeAdapter(BlockPos.class, (JsonDeserializer<BlockPos>) (json, type, context) -> {
            var coordinates = json.getAsJsonArray();
            if (coordinates.size() != 3) throw new IllegalArgumentException("Expected three block coordinates");
            return new BlockPos(coordinates.get(0).getAsInt(), coordinates.get(1).getAsInt(), coordinates.get(2).getAsInt());
        })
        .registerTypeAdapter(RequiredExtractor.class, RequiredExtractor.DESERIALIZER)
        .registerTypeAdapter(EnumNbtCompareOperation.class, EnumNbtCompareOperation.DESERIALIZER)
        .registerTypeAdapter(NbtPath.class, NbtPath.DESERIALIZER)
        .registerTypeAdapterFactory(JsonSelector.TYPE_ADAPTER_FACTORY)
        .registerTypeAdapterFactory(NbtRef.TYPE_ADAPTER_FACTORY)
        .create();
    private static volatile List<JsonRule> rules = List.of();
    public static final Set<String> READ_DOMAINS = ConcurrentHashMap.newKeySet();
    private RulesLoader() {}

    public static List<JsonRule> parse(Reader reader) {
        List<JsonRule> parsed = GSON.fromJson(reader, new TypeToken<List<JsonRule>>() {}.getType());
        if (parsed == null || parsed.stream().anyMatch(rule -> rule == null || rule.selectors == null
            || rule.selectors.isEmpty() || rule.selectors.contains(null))) {
            throw new IllegalArgumentException("Schematic rules require non-empty selectors");
        }
        return List.copyOf(parsed);
    }

    private static List<JsonRule> bundled(Set<String> domains) {
        Set<String> namespaces = new TreeSet<>();
        namespaces.add("buildcraftbuilders");
        FabricLoader.getInstance().getAllMods().forEach(mod -> namespaces.add(mod.getMetadata().getId()));
        Registries.BLOCK.getIds().forEach(id -> namespaces.add(id.getNamespace()));
        List<JsonRule> loaded = new ArrayList<>();
        ClassLoader loader = RulesLoader.class.getClassLoader();
        for (String namespace : namespaces) {
            String base = "assets/" + namespace + "/compat/buildcraft/builders/";
            try (var index = loader.getResourceAsStream(base + "index.json")) {
                if (index == null) continue;
                List<String> files = GSON.fromJson(new InputStreamReader(index, StandardCharsets.UTF_8),
                    new TypeToken<List<String>>() {}.getType());
                if (files == null) throw new IOException("Empty index");
                for (String file : files) {
                    if (file == null || !file.matches("[a-z0-9_./-]+") || file.contains("..") || file.startsWith("/"))
                        throw new IOException("Invalid rule path " + file);
                    String path = base + file + ".json";
                    try (var stream = loader.getResourceAsStream(path)) {
                        if (stream == null) throw new IOException("Missing " + path);
                        loaded.addAll(parse(new InputStreamReader(stream, StandardCharsets.UTF_8)));
                    }
                }
                domains.add(namespace);
            } catch (IOException | RuntimeException failure) {
                throw new IllegalStateException("Cannot load schematic rules from " + base, failure);
            }
        }
        domains.addAll(List.of("minecraft", "buildcraft", "buildcraftlib", "buildcraftcore", "buildcraftbuilders",
            "buildcraftenergy", "buildcraftfactory", "buildcraftrobotics", "buildcraftsilicon", "buildcrafttransport"));
        return loaded;
    }

    public static void loadAll() {
        Set<String> domains = new LinkedHashSet<>();
        publish(bundled(domains), domains);
    }
    public static void reload(ResourceManager resources) {
        Set<String> domains = new LinkedHashSet<>();
        List<JsonRule> loaded = bundled(domains);
        resources.findResources("buildcraft/builders/rules", id -> id.getPath().endsWith(".json"))
            .entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).forEach(entry -> {
                try (Reader reader = entry.getValue().getReader()) {
                    loaded.addAll(parse(reader));
                    domains.add(entry.getKey().getNamespace());
                } catch (IOException | RuntimeException failure) {
                    throw new IllegalStateException("Cannot load schematic rules " + entry.getKey(), failure);
                }
            });
        publish(loaded, domains);
    }
    private static void publish(List<JsonRule> loaded, Set<String> domains) {
        rules = List.copyOf(loaded);
        READ_DOMAINS.clear();
        READ_DOMAINS.addAll(domains);
    }
    public static void registerReloadListener() {
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            public Identifier getFabricId() { return new Identifier("buildcraftbuilders", "schematic_rules"); }
            public void reload(ResourceManager manager) { RulesLoader.reload(manager); }
        });
    }
    public static Set<JsonRule> getRules(BlockState state, NbtCompound tileNbt) {
        NbtCompound nbt = tileNbt == null ? new NbtCompound() : tileNbt;
        Set<JsonRule> matched = new LinkedHashSet<>();
        for (JsonRule rule : rules) if (rule.selectors.stream().anyMatch(selector -> selector.matches(base -> matches(state, base), nbt)))
            matched.add(rule);
        return matched;
    }
    public static Set<JsonRule> getRules(Identifier entityId, NbtCompound entityNbt) {
        Set<JsonRule> matched = new LinkedHashSet<>();
        NbtCompound nbt = entityNbt == null ? new NbtCompound() : entityNbt;
        for (JsonRule rule : rules) if (rule.selectors.stream().anyMatch(selector -> selector.matches(entityId.toString()::equals, nbt)))
            matched.add(rule);
        return matched;
    }
    static boolean matches(BlockState state, String selector) {
        if (selector == null) return false;
        int bracket = selector.indexOf('[');
        String name = bracket < 0 ? selector : selector.substring(0, bracket);
        boolean tag = name.startsWith("#");
        Identifier id = Identifier.tryParse(tag ? name.substring(1) : name);
        if (id == null || (tag ? !state.isIn(TagKey.of(RegistryKeys.BLOCK, id)) : !Registries.BLOCK.getId(state.getBlock()).equals(id))) return false;
        if (bracket < 0) return true;
        if (!selector.endsWith("]")) return false;
        String conditions = selector.substring(bracket + 1, selector.length() - 1);
        return Arrays.stream(conditions.split(",")).allMatch(condition -> {
            String[] pair = condition.trim().split("=", -1);
            if (pair.length != 2) return false;
            Property<?> property = state.getBlock().getStateManager().getProperty(pair[0].trim());
            return property != null && propertyValue(state, property).equals(pair[1].trim());
        });
    }
    private static <T extends Comparable<T>> String propertyValue(BlockState state, Property<T> property) {
        return property.name(state.get(property));
    }
}
