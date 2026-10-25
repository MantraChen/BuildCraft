/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.silicon.recipe;

import buildcraft.silicon.*;
import buildcraft.silicon.item.ItemPluggableFacade;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.*;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

public final class FacadeSwapRecipe extends SpecialCraftingRecipe {
    public FacadeSwapRecipe(Identifier id,CraftingRecipeCategory category){super(id,category);}
    @Override public boolean matches(RecipeInputInventory input,World world){return !result(input).isEmpty();}
    @Override public ItemStack craft(RecipeInputInventory input,DynamicRegistryManager registry){return result(input);}
    @Override public boolean fits(int width,int height){return width>=1 && height>=1;}
    @Override public RecipeSerializer<?> getSerializer(){return BCSiliconRecipes.FACADE_SWAP;}
    private static ItemStack result(RecipeInputInventory input){
        ItemStack found=ItemStack.EMPTY;
        for(int i=0;i<input.size();i++)if(!input.getStack(i).isEmpty()){
            if(!found.isEmpty() || !input.getStack(i).isOf(BCSiliconItems.plugFacade))return ItemStack.EMPTY;found=input.getStack(i);
        }
        if(found.isEmpty())return ItemStack.EMPTY;
        var result=found.copyWithCount(1);
        result.getOrCreateNbt().remove("states");
        result.getOrCreateNbt().put("facade",ItemPluggableFacade.getStates(found).withSwappedIsHollow().writeToNbt());return result;
    }
}