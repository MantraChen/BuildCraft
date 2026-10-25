package buildcraft.test.lib.inventory;

import buildcraft.lib.recipe.IngredientNBTBC;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.Ingredient;
import org.junit.Test;
import static org.junit.Assert.*;

public class IngredientNBTBCTest {
    private ItemStack tagged() {
        ItemStack stack = new ItemStack(Items.DIAMOND);
        stack.getOrCreateNbt().putString("owner", "FelixMantra");
        return stack;
    }

    @Test public void matchesTagButIgnoresCount() {
        Ingredient ingredient = IngredientNBTBC.of(tagged());
        ItemStack candidate = tagged();
        candidate.setCount(32);
        assertTrue(ingredient.test(candidate));
        assertFalse(ingredient.test(new ItemStack(Items.DIAMOND)));
        assertFalse(ingredient.test(new ItemStack(Items.EMERALD)));
    }

    @Test public void strictIngredientRejectsExtraTags() {
        Ingredient ingredient = IngredientNBTBC.of(tagged());
        ItemStack extra = tagged();
        extra.getOrCreateNbt().putInt("extra", 1);
        assertFalse(ingredient.test(extra));
    }

    @Test public void mutationOfInputDoesNotChangeRecipe() {
        ItemStack original = tagged();
        Ingredient ingredient = IngredientNBTBC.of(original);
        original.getOrCreateNbt().putString("owner", "different");
        assertTrue(ingredient.test(tagged()));
        assertFalse(ingredient.test(original));
    }

    @Test public void nativeJsonSerializationPreservesMatcher() {
        Ingredient ingredient = IngredientNBTBC.of(tagged());
        Ingredient reloaded = Ingredient.fromJson(ingredient.toJson());
        assertTrue(reloaded.test(tagged()));
        assertFalse(reloaded.test(new ItemStack(Items.DIAMOND)));
    }
}
