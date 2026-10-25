/* Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/ */

package buildcraft.lib.client.guide.parts.recipe;

import buildcraft.lib.compat.forge_stubs.OreDictionaryStub;

import javax.annotation.Nonnull;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Ingredient;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.collection.DefaultedList;

// STUB(R.Chen): OreDictionaryStub removed — TODO(R.Chen): implement via Tags

import buildcraft.api.core.BCLog;

import buildcraft.lib.client.guide.GuiGuide;
import buildcraft.lib.client.guide.parts.GuidePartFactory;
import buildcraft.lib.misc.StackUtil;
import buildcraft.lib.misc.data.NonNullMatrix;

public class GuideCraftingFactory implements GuidePartFactory {

    private final NonNullMatrix<Ingredient> input;
    private final @Nonnull ItemStack output;
    private final int hash;

    public GuideCraftingFactory(Ingredient[][] input, ItemStack output) {
        this.input = new NonNullMatrix<>(input, Ingredient.EMPTY);
        this.output = StackUtil.asNonNull(output);
        NbtList hashNbt = new NbtList();
        for (Ingredient ingredient : this.input) {
            NbtList list = new NbtList();
            for (ItemStack stack : ingredient.getMatchingStacks()) {
                list.add(stack.writeNbt(new NbtCompound()));
            }
            hashNbt.add(list);
        }
        this.hash = 31 * hashNbt.hashCode() + output.writeNbt(new NbtCompound()).hashCode();
    }

    public static GuidePartFactory create(@Nonnull ItemStack stack) {
        for (net.minecraft.recipe.CraftingRecipe recipe : GuideRecipes.all(net.minecraft.recipe.RecipeType.CRAFTING)) {
            if (ItemStack.areItemsEqual(stack, recipe.getOutput(GuideRecipes.registries()))) {
                GuidePartFactory val = getFactory(recipe);
                if (val != null) {
                    return val;
                } else {
                    BCLog.logger.warn("[lib.guide.crafting] Found a matching recipe, but of an unknown "
                        + recipe.getClass() + " for " + stack.getName().getString());
                }
            }
        }
        return null;
    }

    public static GuidePartFactory getFactory(net.minecraft.recipe.CraftingRecipe recipe) {
        ItemStack output = recipe.getOutput(GuideRecipes.registries());
        DefaultedList<Ingredient> input = recipe.getIngredients();
        if (input == null || input.isEmpty() || output.isEmpty()) {
            return null;
        }
        Ingredient[][] matrix = new Ingredient[3][3];
        int maxX = recipe instanceof net.minecraft.recipe.ShapedRecipe shaped ? shaped.getWidth() : 3;
        int maxY = recipe instanceof net.minecraft.recipe.ShapedRecipe shaped ? shaped.getHeight() : 3;
        if (maxX < 1 || maxY < 1 || maxX > 3 || maxY > 3 || input.size() > 9) return null;
        int offsetX = maxX == 1 ? 1 : 0;
        int offsetY = maxY == 1 ? 1 : 0;
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                if (x < offsetX || y < offsetY) {
                    matrix[x][y] = Ingredient.EMPTY;
                    continue;
                }
                int i = x - offsetX + (y - offsetY) * maxX;
                if (i >= input.size() || x - offsetX >= maxX) {
                    matrix[x][y] = Ingredient.EMPTY;
                } else {
                    matrix[x][y] = input.get(i);
                }
            }
        }
        return new GuideCraftingFactory(matrix, output);
    }

    public static GuidePartFactory create(Item output) {
        return create(new ItemStack(output));
    }

    @Override
    public GuideCrafting createNew(GuiGuide gui) {
        return new GuideCrafting(gui, input, output);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null) return false;
        if (obj.getClass() != getClass()) return false;
        GuideCraftingFactory other = (GuideCraftingFactory) obj;
        // Shortcut out of this full itemstack comparison as its really expensive
        if (hash != other.hash || !ItemStack.areEqual(output, other.output)) return false;
        if (input.getWidth() != other.input.getWidth() || input.getHeight() != other.input.getHeight()) return false;
        NbtList nbtThis = new NbtList();
        for (Ingredient ingredient : this.input) {
            NbtList list = new NbtList();
            for (ItemStack stack : ingredient.getMatchingStacks()) {
                list.add(stack.writeNbt(new NbtCompound()));
            }
            nbtThis.add(list);
        }
        NbtList nbtThat = new NbtList();
        for (Ingredient ingredient : other.input) {
            NbtList list = new NbtList();
            for (ItemStack stack : ingredient.getMatchingStacks()) {
                list.add(stack.writeNbt(new NbtCompound()));
            }
            nbtThat.add(list);
        }
        return nbtThis.equals(nbtThat);
    }
}
