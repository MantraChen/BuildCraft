/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.silicon.recipe;

import buildcraft.silicon.BCSiliconItems;
import buildcraft.silicon.BCSiliconRecipes;
import buildcraft.silicon.gate.*;
import buildcraft.silicon.item.ItemPluggableGate;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.*;
import net.minecraft.recipe.*;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

/** Original basic gates and iron upgrades, plus lossless AND/OR conversion. */
public final class GateCraftingRecipe extends SpecialCraftingRecipe {
    public GateCraftingRecipe(Identifier id,CraftingRecipeCategory category){super(id,category);}
    @Override public boolean matches(RecipeInputInventory input,World world){return !result(input).isEmpty();}
    @Override public ItemStack craft(RecipeInputInventory input,DynamicRegistryManager registries){return result(input);}
    @Override public boolean fits(int width,int height){return width>=1 && height>=1;}
    @Override public RecipeSerializer<?> getSerializer(){return BCSiliconRecipes.GATE_CRAFTING;}
    private static boolean validGate(ItemStack stack) {
        return stack.isOf(BCSiliconItems.plugGate) && stack.hasNbt()
            && stack.getNbt().getCompound("gate").equals(ItemPluggableGate.getVariant(stack).writeToNBT());
    }
    private static ItemStack result(RecipeInputInventory input) {
        int count=0;ItemStack single=ItemStack.EMPTY;
        for(int i=0;i<input.size();i++)if(!input.getStack(i).isEmpty()){count++;single=input.getStack(i);}
        if(count==1 && validGate(single)) {
            var variant=ItemPluggableGate.getVariant(single);
            if(!variant.material.canBeModified)return ItemStack.EMPTY;
            var output=single.copyWithCount(1);
            output.getOrCreateNbt().put("gate",new GateVariant(variant.logic==EnumGateLogic.AND?EnumGateLogic.OR:EnumGateLogic.AND,variant.material,variant.modifier).writeToNBT());
            return output;
        }
        if(count!=5 || input.getWidth()<3 || input.getHeight()<3)return ItemStack.EMPTY;
        for(int oy=0;oy<=input.getHeight()-3;oy++)for(int ox=0;ox<=input.getWidth()-3;ox++) {
            boolean shape=true;
            for(int y=0;y<input.getHeight();y++)for(int x=0;x<input.getWidth();x++) {
                int dx=x-ox,dy=y-oy;
                boolean filled=(dx==1 && dy>=0 && dy<=2)||(dy==1 && dx>=0 && dx<=2);
                if(input.getStack(x+y*input.getWidth()).isEmpty()==filled)shape=false;
            }
            if(!shape)continue;
            ItemStack top=input.getStack(ox+1+oy*input.getWidth()),left=input.getStack(ox+(oy+1)*input.getWidth()),
                middle=input.getStack(ox+1+(oy+1)*input.getWidth()),right=input.getStack(ox+2+(oy+1)*input.getWidth()),bottom=input.getStack(ox+1+(oy+2)*input.getWidth());
            if(!left.isOf(top.getItem()) || !right.isOf(top.getItem()))continue;
            if(middle.isOf(Items.REDSTONE) && bottom.isOf(Items.COBBLESTONE)) {
                EnumGateMaterial material=top.isOf(Items.BRICK)?EnumGateMaterial.CLAY_BRICK:top.isOf(Items.IRON_INGOT)?EnumGateMaterial.IRON:top.isOf(Items.NETHER_BRICK)?EnumGateMaterial.NETHER_BRICK:null;
                if(material!=null)return BCSiliconItems.plugGate.getStack(new GateVariant(EnumGateLogic.AND,material,EnumGateModifier.NO_MODIFIER));
            }
            if(validGate(middle) && bottom.isOf(top.getItem())) {
                var variant=ItemPluggableGate.getVariant(middle);
                if(variant.logic!=EnumGateLogic.AND || variant.material!=EnumGateMaterial.IRON || variant.modifier!=EnumGateModifier.NO_MODIFIER)continue;
                EnumGateModifier modifier=top.isOf(Items.LAPIS_LAZULI)?EnumGateModifier.LAPIS:top.isOf(Items.QUARTZ)?EnumGateModifier.QUARTZ:null;
                if(modifier!=null) {
                    var output=middle.copyWithCount(1);
                    output.getOrCreateNbt().put("gate",new GateVariant(variant.logic,variant.material,modifier).writeToNBT());return output;
                }
            }
        }
        return ItemStack.EMPTY;
    }
}
