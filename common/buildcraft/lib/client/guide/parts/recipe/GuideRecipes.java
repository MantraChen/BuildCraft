package buildcraft.lib.client.guide.parts.recipe;

import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeType;
import net.minecraft.registry.DynamicRegistryManager;

/** Reads the connected world's recipes on demand, including server datapack replacements. */
final class GuideRecipes {
    private GuideRecipes() {}
    static <I extends net.minecraft.inventory.Inventory, T extends Recipe<I>> List<T> all(RecipeType<T> type) {
        var handler = MinecraftClient.getInstance().getNetworkHandler();
        if (handler == null) return List.of();
        return handler.getRecipeManager().listAllOfType(type).stream()
            .sorted(java.util.Comparator.comparing(r -> r.getId().toString())).toList();
    }
    static DynamicRegistryManager registries() {
        var world = MinecraftClient.getInstance().world;
        return world == null ? DynamicRegistryManager.EMPTY : world.getRegistryManager();
    }
}
