/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.lib.mixin;

import java.util.Map;
import com.google.gson.JsonElement;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(RecipeManager.class)
public abstract class FacadeRecipeManagerMixin implements buildcraft.lib.recipe.RecipeGeneration {
    @org.spongepowered.asm.mixin.Shadow private Map<net.minecraft.recipe.RecipeType<?>,Map<Identifier,net.minecraft.recipe.Recipe<?>>> recipes;
    @Override public Object buildcraft$recipeGeneration(){return recipes;}
    @ModifyVariable(method="apply(Ljava/util/Map;Lnet/minecraft/resource/ResourceManager;Lnet/minecraft/util/profiler/Profiler;)V",at=@At("HEAD"),argsOnly=true)
    private Map<Identifier,JsonElement> buildcraft$facades(Map<Identifier,JsonElement> recipes){
        return buildcraft.silicon.recipe.FacadeAssemblyRecipes.addDefaults(recipes);
    }
}
