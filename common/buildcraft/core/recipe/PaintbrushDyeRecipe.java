/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.core.recipe;
import buildcraft.core.*;
import buildcraft.core.item.ItemPaintbrush_BC8;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.*;
import net.minecraft.recipe.*;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
public final class PaintbrushDyeRecipe extends SpecialCraftingRecipe {
    public static final SpecialRecipeSerializer<PaintbrushDyeRecipe> SERIALIZER=new SpecialRecipeSerializer<>(PaintbrushDyeRecipe::new);
    public PaintbrushDyeRecipe(Identifier id,CraftingRecipeCategory category){super(id,category);}
    @Override public boolean matches(RecipeInputInventory input,World world){return !result(input).isEmpty();}
    @Override public ItemStack craft(RecipeInputInventory input,DynamicRegistryManager registries){return result(input);}
    @Override public boolean fits(int width,int height){return width*height>=2;}
    @Override public RecipeSerializer<?> getSerializer(){return SERIALIZER;}
    private static ItemStack result(RecipeInputInventory input){
        ItemStack brush=ItemStack.EMPTY;DyeItem dye=null;
        for(int i=0;i<input.size();i++){var stack=input.getStack(i);if(stack.isEmpty())continue;
            if(stack.isOf(BCCoreItems.paintbrush)&&brush.isEmpty()&&ItemPaintbrush_BC8.getColour(stack)==null)brush=stack;
            else if(stack.getItem() instanceof DyeItem d&&dye==null)dye=d;else return ItemStack.EMPTY;
        }
        return brush.isEmpty()||dye==null?ItemStack.EMPTY:ItemPaintbrush_BC8.coloured(brush,dye.getColor());
    }
}
