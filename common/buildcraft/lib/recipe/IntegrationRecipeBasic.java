/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.lib.recipe;

import java.util.*;
import com.google.common.collect.ImmutableList;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import buildcraft.api.core.BuildCraftAPI;
import buildcraft.api.recipes.*;

public class IntegrationRecipeBasic extends IntegrationRecipe {
    protected final long requiredMicroJoules;
    protected final IngredientStack target;
    protected final ImmutableList<IngredientStack> toIntegrate;
    protected final ItemStack output;
    public IntegrationRecipeBasic(Identifier name,long requiredMicroJoules,IngredientStack target,List<IngredientStack> toIntegrate,ItemStack output) {
        super(Objects.requireNonNull(name));
        if(requiredMicroJoules<1 || requiredMicroJoules>1_000_000*buildcraft.api.mj.MjAPI.MJ
            || target.count>64 || toIntegrate.size()>8 || toIntegrate.stream().anyMatch(r->r.count>4096)
            || output.isEmpty() || output.getCount()>output.getMaxCount())throw new IllegalArgumentException("Invalid integration recipe "+name);
        this.requiredMicroJoules=requiredMicroJoules;this.target=target;
        this.toIntegrate=ImmutableList.copyOf(toIntegrate);this.output=output.copy();
    }
    public IntegrationRecipeBasic(String name,long requiredMicroJoules,IngredientStack target,List<IngredientStack> toIntegrate,ItemStack output) {
        this(BuildCraftAPI.nameToResourceLocation(name),requiredMicroJoules,target,toIntegrate,output);
    }
    protected boolean matches(ItemStack target,DefaultedList<ItemStack> components) {
        if(target.getCount()<this.target.count || !this.target.ingredient.test(target))return false;
        var needs=toIntegrate.stream().map(r->new IngredientAllocation.Requirement(r.ingredient::test,r.count)).toList();
        return IngredientAllocation.onlyMatchingInputs(needs,components) && IngredientAllocation.allocate(needs,components)!=null;
    }
    @Override public ItemStack getOutput(ItemStack target,DefaultedList<ItemStack> components){return matches(target,components)?output.copy():ItemStack.EMPTY;}
    @Override public ImmutableList<IngredientStack> getRequirements(ItemStack output){return toIntegrate;}
    @Override public long getRequiredMicroJoules(ItemStack output){return requiredMicroJoules;}
    @Override public IngredientStack getCenterStack(){return target;}
}
