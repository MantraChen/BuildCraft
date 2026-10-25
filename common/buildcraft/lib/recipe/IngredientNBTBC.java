package buildcraft.lib.recipe;

import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Ingredient;
import net.fabricmc.fabric.api.recipe.v1.ingredient.DefaultCustomIngredients;

/** NBT-sensitive ingredients use Fabric's serializable custom ingredient implementation. */
public final class IngredientNBTBC {
    private IngredientNBTBC() {}

    public static Ingredient of(ItemStack stack) {
        return DefaultCustomIngredients.nbt(stack.copy(), true);
    }
}
