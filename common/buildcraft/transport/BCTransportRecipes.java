/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 *
 * Ported to Fabric 1.20.1 by R.Chen (https://github.com/MantraChen).
 */
package buildcraft.transport;

/** Data-driven upgrades retain the source pipe's colour. Remake by FelixMantra. */
public class BCTransportRecipes {
    public static final net.minecraft.recipe.RecipeSerializer<buildcraft.transport.recipe.PipeUpgradeRecipe> PIPE_UPGRADE =
        new buildcraft.transport.recipe.PipeUpgradeRecipe.Serializer();
    public static void preInit() {}
    public static void init() {
        net.minecraft.registry.Registry.register(net.minecraft.registry.Registries.RECIPE_SERIALIZER,
            new net.minecraft.util.Identifier("buildcrafttransport", "pipe_upgrade"), PIPE_UPGRADE);
    }
    public static void postInit() {}
}
