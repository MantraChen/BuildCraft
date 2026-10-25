/* Copyright (c) 2016 SpaceToad and the BuildCraft team.
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. */
package buildcraft.lib.registry;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import buildcraft.api.transport.pipe.IItemPipe;
import buildcraft.lib.block.BlockBCBase_Neptune;
import buildcraft.lib.config.Configuration;
import buildcraft.lib.config.Property;
import buildcraft.lib.item.IItemBuildCraft;
import buildcraft.lib.registry.TagManager.EnumTagType;

/** Per-object options use intended registry IDs, including before native registration. */
public final class RegistryConfig {
    private static final Map<String, Configuration> configs = new HashMap<>();
    private static final Map<String, Set<Identifier>> disabled = new HashMap<>();

    public static Configuration setRegistryConfig(String modId, File file) {
        return setRegistryConfig(modId, new Configuration(file));
    }
    public static Configuration setRegistryConfig(String modId, Configuration config) {
        configs.put(modId, config);
        return config;
    }
    public static Configuration useOtherModConfigFor(String from, String to) {
        Configuration config = configs.get(to);
        if (config == null) throw new IllegalStateException("No object configuration for " + to);
        configs.put(from, config);
        return config;
    }

    public static Identifier getId(Item item) {
        if (item instanceof IItemBuildCraft bc && !bc.id().isEmpty()) {
            return new Identifier(TagManager.getTag(bc.id(), EnumTagType.REGISTRY_NAME));
        }
        return Registries.ITEM.getKey(item).orElseThrow(() -> new IllegalArgumentException("Item has no registry ID")).getValue();
    }
    public static Identifier getId(Block block) {
        if (block instanceof BlockBCBase_Neptune bc && !bc.id.isEmpty()) {
            return new Identifier(TagManager.getTag(bc.id, EnumTagType.REGISTRY_NAME));
        }
        return Registries.BLOCK.getKey(block).orElseThrow(() -> new IllegalArgumentException("Block has no registry ID")).getValue();
    }
    public static boolean isEnabled(Item item) {
        return isEnabled(item instanceof IItemPipe ? "pipes" : "items", getId(item), item.getTranslationKey());
    }
    public static boolean isEnabled(Block block) { return isEnabled("blocks", getId(block), block.getTranslationKey()); }
    public static boolean isEnabled(String category, String registryId, String languageKey) {
        return isEnabled(category, new Identifier(registryId), languageKey);
    }
    public static boolean isEnabled(String category, Identifier id, String languageKey) {
        Configuration config = configs.get(id.getNamespace());
        if (config == null) throw new IllegalStateException("No object configuration for " + id.getNamespace());
        // Existing unqualified settings retain their historical meaning. New keys include
        // a namespace so unrelated objects in a shared config no longer collide.
        String key = !config.contains(category, id.toString()) && config.contains(category, id.getPath())
            ? id.getPath() : id.toString();
        Property property = config.get(category, key, true);
        property.setLanguageKey(languageKey);
        property.setRequiresMcRestart(true);
        property.setRequiresWorldRestart(true);
        boolean enabled = property.getBoolean(true);
        Set<Identifier> set = disabled.computeIfAbsent(category, ignored -> new HashSet<>());
        if (enabled) set.remove(id); else set.add(id);
        return enabled;
    }
    public static boolean hasItemBeenDisabled(Identifier id) {
        return hasObjectBeenDisabled("items", id) || hasObjectBeenDisabled("pipes", id);
    }
    public static boolean hasBlockBeenDisabled(Identifier id) { return hasObjectBeenDisabled("blocks", id); }
    public static boolean hasObjectBeenDisabled(String category, Identifier id) {
        return disabled.getOrDefault(category, Set.of()).contains(id);
    }
    static void setDisabled(String category, Identifier id) {
        disabled.computeIfAbsent(category, ignored -> new HashSet<>()).add(id);
    }
}
