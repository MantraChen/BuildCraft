package buildcraft.lib.client.guide.loader;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import buildcraft.api.registry.IScriptableRegistry.OptionallyDisabled;

/** Reads legacy guide stack literals while preserving native NBT (including embedded commas). */
public final class GuideStackParser {
    private GuideStackParser() {}
    public static OptionallyDisabled<ItemStack> parse(String specification) {
        String input = specification.trim();
        if ((input.startsWith("{") && input.endsWith("}")) || (input.startsWith("(") && input.endsWith(")")))
            input = input.substring(1, input.length() - 1);
        String[] fields = input.split(",", 4);
        try {
            Identifier id = Identifier.tryParse(fields[0].trim());
            if (id == null || !Registries.ITEM.containsId(id)) return new OptionallyDisabled<>("Unknown item " + fields[0]);
            ItemStack stack = Registries.ITEM.get(id).getDefaultStack();
            if (stack.isEmpty()) return new OptionallyDisabled<>("Empty item " + id);
            int count = fields.length > 1 ? Integer.parseInt(fields[1].trim()) : 1;
            if (count < 1 || count > stack.getMaxCount()) return new OptionallyDisabled<>("Invalid stack count " + count);
            stack.setCount(count);
            int legacyMeta = fields.length > 2 ? Integer.parseInt(fields[2].trim()) : 0;
            if (fields.length > 3) stack.setNbt(StringNbtReader.parse(fields[3].trim()));
            if (id.getNamespace().equals("buildcraftcore") && id.getPath().equals("engine")) {
                String[] types = { "wood", "stone", "iron", "creative", "rf" };
                if (legacyMeta < 0 || legacyMeta >= types.length) return new OptionallyDisabled<>("Unknown engine type " + legacyMeta);
                var properties = stack.getOrCreateSubNbt("BlockStateTag");
                properties.putString("type", types[legacyMeta]);
            } else if (stack.getItem().isDamageable() && legacyMeta >= 0) stack.setDamage(legacyMeta);
            return new OptionallyDisabled<>(stack);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException | IllegalArgumentException error) {
            return new OptionallyDisabled<>("Invalid guide stack '" + specification + "': " + error.getMessage());
        }
    }
}
