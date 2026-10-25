/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.lib.list;

import java.util.*;
import java.util.stream.Stream;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import buildcraft.api.lists.ListMatchHandler;

/** Material/form matching from live item tags, including the two common tag naming conventions. */
public final class ListMatchHandlerTags extends ListMatchHandler {
    private static final List<String> FORMS = List.of("storage_blocks", "raw_materials", "ingots", "nuggets", "ores",
        "dusts", "gems", "plates", "gears", "rods", "wires", "logs", "planks");
    private record Kind(String form, String material) {
        String key(Type type) { return switch (type) {
            case TYPE -> form;
            case MATERIAL -> material;
            case CLASS -> form + "/" + material;
        }; }
    }
    private static Optional<Kind> kind(Identifier id) {
        if (!Set.of("c", "forge", "minecraft").contains(id.getNamespace())) return Optional.empty();
        String path = id.getPath();
        for (String form : FORMS) {
            if (path.equals(form)) return Optional.of(new Kind(form, ""));
            if (path.startsWith(form + "/") && path.length() > form.length() + 1)
                return Optional.of(new Kind(form, path.substring(form.length() + 1)));
            if (path.endsWith("_" + form) && path.length() > form.length() + 1)
                return Optional.of(new Kind(form, path.substring(0, path.length() - form.length() - 1)));
        }
        return Optional.empty();
    }
    private static Stream<String> keys(Type type, ItemStack stack) {
        if (stack.isEmpty()) return Stream.empty();
        return stack.streamTags().map(tag -> kind(tag.id())).flatMap(Optional::stream)
            // Broad tags like c:ingots describe a form, never a specific material or class.
            .filter(kind -> type == Type.TYPE || !kind.material.isEmpty()).map(kind -> kind.key(type));
    }
    @Override public boolean isValidSource(Type type, ItemStack stack) { return keys(type, stack).findAny().isPresent(); }
    @Override public boolean matches(Type type, ItemStack stack, ItemStack target, boolean precise) {
        Set<String> source = keys(type, stack).collect(java.util.stream.Collectors.toSet());
        return keys(type, target).anyMatch(source::contains);
    }
    @Override public DefaultedList<ItemStack> getClientExamples(Type type, ItemStack stack) {
        DefaultedList<ItemStack> result = DefaultedList.of();
        for (var item : Registries.ITEM) {
            ItemStack candidate = item.getDefaultStack();
            if (matches(type, stack, candidate, false)) result.add(candidate);
        }
        return result;
    }
}
