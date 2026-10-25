/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.builders;

public class BCBuildersRecipes {
    public static final net.minecraft.recipe.RecipeSerializer<buildcraft.builders.recipe.SnapshotRecipe> SNAPSHOT =
        new buildcraft.builders.recipe.SnapshotRecipe.Serializer();
    public static void init() {
        net.minecraft.registry.Registry.register(net.minecraft.registry.Registries.RECIPE_SERIALIZER,
            new net.minecraft.util.Identifier("buildcraftbuilders:snapshot"), SNAPSHOT);

        // Native data-pack recipes provide blank samples, snapshots and builder machines.
    }
}
