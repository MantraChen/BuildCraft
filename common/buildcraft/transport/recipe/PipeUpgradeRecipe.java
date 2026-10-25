/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.transport.recipe;

import buildcraft.transport.BCTransportRecipes;
import buildcraft.transport.item.ItemPipeHolder;
import com.google.gson.JsonObject;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.ShapelessRecipe;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;

/** Native shapeless recipe and packet format, with the original dye-preserving upgrade/undo behavior. */
public final class PipeUpgradeRecipe extends ShapelessRecipe {
    private PipeUpgradeRecipe(ShapelessRecipe recipe) {
        super(recipe.getId(), recipe.getGroup(), recipe.getCategory(), recipe.getOutput(DynamicRegistryManager.EMPTY), recipe.getIngredients());
    }

    @Override public ItemStack craft(RecipeInputInventory inventory, DynamicRegistryManager registries) {
        var result = super.craft(inventory, registries);
        for (int i=0;i<inventory.size();i++) {
            var input = inventory.getStack(i);
            if (input.getItem() instanceof ItemPipeHolder) return ItemPipeHolder.withColour(result, ItemPipeHolder.getColour(input));
        }
        return result;
    }

    @Override public RecipeSerializer<?> getSerializer() { return BCTransportRecipes.PIPE_UPGRADE; }

    public static final class Serializer implements RecipeSerializer<PipeUpgradeRecipe> {
        private final ShapelessRecipe.Serializer vanilla = new ShapelessRecipe.Serializer();
        @Override public PipeUpgradeRecipe read(Identifier id, JsonObject json) { return new PipeUpgradeRecipe(vanilla.read(id, json)); }
        @Override public PipeUpgradeRecipe read(Identifier id, PacketByteBuf buffer) { return new PipeUpgradeRecipe(vanilla.read(id, buffer)); }
        @Override public void write(PacketByteBuf buffer, PipeUpgradeRecipe recipe) { vanilla.write(buffer, recipe); }
    }
}
