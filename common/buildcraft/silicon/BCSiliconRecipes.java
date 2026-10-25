/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 *
 * Ported to Fabric 1.20.1 by R.Chen (https://github.com/MantraChen).
 */
package buildcraft.silicon;

/** Native data-pack recipe registration. Remake by FelixMantra. */
public class BCSiliconRecipes {
    public static final net.minecraft.recipe.RecipeType<buildcraft.silicon.recipe.NativeIntegrationRecipe> INTEGRATION=new net.minecraft.recipe.RecipeType<>() {
        @Override public String toString(){return "buildcraftsilicon:integration";}
    };
    public static final buildcraft.silicon.recipe.NativeIntegrationRecipe.Serializer INTEGRATION_SERIALIZER=new buildcraft.silicon.recipe.NativeIntegrationRecipe.Serializer();
    public static final net.minecraft.recipe.RecipeType<buildcraft.silicon.recipe.NativeAssemblyRecipe> ASSEMBLY=new net.minecraft.recipe.RecipeType<>() {
        @Override public String toString(){return "buildcraftsilicon:assembly";}
    };
    public static final buildcraft.silicon.recipe.NativeAssemblyRecipe.Serializer ASSEMBLY_SERIALIZER=new buildcraft.silicon.recipe.NativeAssemblyRecipe.Serializer();
    public static final net.minecraft.recipe.SpecialRecipeSerializer<buildcraft.silicon.recipe.GateCraftingRecipe> GATE_CRAFTING=
        new net.minecraft.recipe.SpecialRecipeSerializer<>(buildcraft.silicon.recipe.GateCraftingRecipe::new);
    public static void init() {
        var integration=new net.minecraft.util.Identifier("buildcraftsilicon","integration");
        net.minecraft.registry.Registry.register(net.minecraft.registry.Registries.RECIPE_TYPE,integration,INTEGRATION);
        net.minecraft.registry.Registry.register(net.minecraft.registry.Registries.RECIPE_SERIALIZER,integration,INTEGRATION_SERIALIZER);
        var id=new net.minecraft.util.Identifier("buildcraftsilicon","assembly");
        net.minecraft.registry.Registry.register(net.minecraft.registry.Registries.RECIPE_TYPE,id,ASSEMBLY);
        net.minecraft.registry.Registry.register(net.minecraft.registry.Registries.RECIPE_SERIALIZER,id,ASSEMBLY_SERIALIZER);
        net.minecraft.registry.Registry.register(net.minecraft.registry.Registries.RECIPE_SERIALIZER,new net.minecraft.util.Identifier("buildcraftsilicon","gate_crafting"),GATE_CRAFTING);
    }
}
