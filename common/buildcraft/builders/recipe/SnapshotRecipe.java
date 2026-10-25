/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.builders.recipe;

import buildcraft.api.enums.EnumSnapshotType;
import buildcraft.builders.BCBuildersRecipes;
import buildcraft.builders.item.ItemSnapshot;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;

/** Native shaped recipes retain the blueprint/template kind in their result NBT. */
public final class SnapshotRecipe extends ShapedRecipe {
    private SnapshotRecipe(ShapedRecipe recipe, ItemStack result) {
        super(recipe.getId(), recipe.getGroup(), recipe.getCategory(), recipe.getWidth(), recipe.getHeight(),
            recipe.getIngredients(), result, recipe.showNotification());
    }
    @Override public RecipeSerializer<?> getSerializer() { return BCBuildersRecipes.SNAPSHOT; }

    public static final class Serializer implements RecipeSerializer<SnapshotRecipe> {
        private final ShapedRecipe.Serializer vanilla = new ShapedRecipe.Serializer();
        @Override public SnapshotRecipe read(Identifier id, JsonObject json) {
            var recipe = vanilla.read(id, json);
            var result = recipe.getOutput(DynamicRegistryManager.EMPTY).copy();
            if (!(result.getItem() instanceof ItemSnapshot)) throw new JsonParseException("Snapshot recipe must produce a snapshot");
            try {
                var type = EnumSnapshotType.valueOf(JsonHelper.getString(json,"snapshot_type"));
                result.getOrCreateNbt().putString("snapshotType", type.name());
            } catch (IllegalArgumentException invalid) { throw new JsonParseException("Invalid snapshot type", invalid); }
            return new SnapshotRecipe(recipe, result);
        }
        @Override public SnapshotRecipe read(Identifier id, PacketByteBuf buffer) {
            var recipe = vanilla.read(id, buffer);
            return new SnapshotRecipe(recipe, recipe.getOutput(DynamicRegistryManager.EMPTY));
        }
        @Override public void write(PacketByteBuf buffer, SnapshotRecipe recipe) { vanilla.write(buffer, recipe); }
    }
}
