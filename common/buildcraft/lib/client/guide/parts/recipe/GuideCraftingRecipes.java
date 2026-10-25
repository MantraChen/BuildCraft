/* Copyright (c) 2017 SpaceToad and the BuildCraft team. MPL-2.0. */
package buildcraft.lib.client.guide.parts.recipe;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeType;
import buildcraft.lib.client.guide.parts.GuidePartFactory;

public enum GuideCraftingRecipes implements IStackRecipes {
    INSTANCE;
    @Override public List<GuidePartFactory> getUsages(ItemStack target) { return find(target, true); }
    @Override public List<GuidePartFactory> getRecipes(ItemStack target) { return find(target, false); }
    private List<GuidePartFactory> find(ItemStack target, boolean input) {
        List<GuidePartFactory> result = new ArrayList<>();
        for (var recipe : GuideRecipes.all(RecipeType.CRAFTING)) {
            boolean matches = input ? recipe.getIngredients().stream().anyMatch(i -> i.test(target))
                : ItemStack.areItemsEqual(target, recipe.getOutput(GuideRecipes.registries()));
            if (matches) {
                var factory = GuideCraftingFactory.getFactory(recipe);
                if (factory != null) result.add(factory);
            }
        }
        return result;
    }
}
