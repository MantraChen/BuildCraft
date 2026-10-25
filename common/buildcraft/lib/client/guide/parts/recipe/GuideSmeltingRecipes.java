/* Copyright (c) 2017 SpaceToad and the BuildCraft team. MPL-2.0. */
package buildcraft.lib.client.guide.parts.recipe;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.RecipeType;
import buildcraft.lib.client.guide.parts.GuidePartFactory;

public enum GuideSmeltingRecipes implements IStackRecipes {
    INSTANCE;
    @Override public List<GuidePartFactory> getUsages(ItemStack target) { return find(target, true); }
    @Override public List<GuidePartFactory> getRecipes(ItemStack target) { return find(target, false); }
    private List<GuidePartFactory> find(ItemStack target, boolean input) {
        List<GuidePartFactory> result = new ArrayList<>();
        for (var recipe : GuideRecipes.all(RecipeType.SMELTING)) {
            ItemStack output = recipe.getOutput(GuideRecipes.registries());
            var ingredient = recipe.getIngredients().get(0);
            boolean matches = input ? target.isOf(Items.FURNACE) || ingredient.test(target)
                : ItemStack.areItemsEqual(target, output);
            if (matches) {
                for (ItemStack stack : ingredient.getMatchingStacks()) result.add(new GuideSmeltingFactory(stack, output));
            }
        }
        return result;
    }
}
