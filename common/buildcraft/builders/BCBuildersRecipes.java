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

//        if (BCBuildersItems.schematicSingle != null) {
//            ItemStack out = new ItemStack(BCBuildersItems.schematicSingle, 4);
//            ShapelessOreRecipe recipe = new ShapelessOreRecipe(out, paper, paper, "gemLapis");
//            GameRegistry.addRecipe(recipe);
//        }

        // if (BCBuildersItems.template != null) {
        // ItemStack out = new ItemStack(BCBuildersItems.template);
        // RecipeBuilderShaped recipe = new RecipeBuilderShaped(out);
        // recipe.add("ppp");
        // recipe.add("pip");
        // recipe.add("ppp");
        // recipe.map('i', "dyeBlack");
        // recipe.map('p', paper);
        // GameRegistry.addRecipe(recipe.build());
        // }
    }
}
