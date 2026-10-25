/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0
 * Remake by FelixMantra */
package buildcraft.lib.client.guide.parts.recipe;

import java.util.*;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import buildcraft.api.recipes.AssemblyRecipe;
import buildcraft.lib.client.guide.parts.GuidePartFactory;
import buildcraft.lib.recipe.*;
import buildcraft.lib.misc.StackUtil;
import buildcraft.silicon.BCSiliconRecipes;
import buildcraft.silicon.recipe.NativeAssemblyRecipe;

public enum GuideAssemblyRecipes implements IStackRecipes {
    INSTANCE;
    @Override public List<GuidePartFactory> getUsages(ItemStack stack){return find(stack,true);}
    @Override public List<GuidePartFactory> getRecipes(ItemStack stack){return find(stack,false);}
    private List<GuidePartFactory> find(ItemStack stack,boolean usages){
        var result=new ArrayList<GuidePartFactory>();var nativeIds=new HashSet<Identifier>();
        boolean all=usages&&net.minecraft.registry.Registries.ITEM.getId(stack.getItem()).equals(new Identifier("buildcraftsilicon:assembly_table"));
        for(var recipe:GuideRecipes.all(BCSiliconRecipes.ASSEMBLY)){
            nativeIds.add(recipe.getId());
            if(usages?all||recipe.inputs().stream().anyMatch(input->input.test(stack)):matchesOutput(stack,recipe.output()))result.add(getFactory(recipe));
        }
        // Keep the public legacy API usable for add-ons; synchronized data-pack IDs take precedence.
        AssemblyRecipeRegistry.REGISTRY.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry->{
            if(nativeIds.contains(entry.getKey()))return;
            var recipe=entry.getValue();for(var output:recipe.getOutputPreviews())
                if(usages?all||recipe.getInputsFor(output).stream().anyMatch(input->input.ingredient.test(stack)):matchesOutput(stack,output))result.add(getFactory(recipe,output));
        });
        return result;
    }
    private static boolean matchesOutput(ItemStack target,ItemStack output){
        return ItemStack.areItemsEqual(target,output)&&(!target.hasNbt()||!output.hasNbt()||ItemStack.canCombine(target,output));
    }
    private static GuideAssemblyFactory getFactory(NativeAssemblyRecipe recipe){
        var stacks=recipe.inputs().stream().map(input->{
            var choices=DefaultedList.<ItemStack>of();for(var stack:input.ingredient().getMatchingStacks()){
                var copy=stack.copyWithCount(input.count());if(input.nbt()!=null)copy.setNbt(input.nbt());choices.add(copy);
            }return new ChangingItemStack(choices);
        }).toArray(ChangingItemStack[]::new);
        return new GuideAssemblyFactory(stacks,new ChangingItemStack(recipe.output()),new ChangingObject<>(new Long[]{recipe.cost()}));
    }
    private static GuideAssemblyFactory getFactory(AssemblyRecipe recipe,ItemStack output){
        var stacks=recipe.getInputsFor(output).stream().map(input->{
            DefaultedList<ItemStack> items=Arrays.stream(input.ingredient.getMatchingStacks()).map(ItemStack::copy).collect(StackUtil.nonNullListCollector());
            items.forEach(stack->stack.setCount(input.count));return new ChangingItemStack(items);
        }).toArray(ChangingItemStack[]::new);
        return new GuideAssemblyFactory(stacks,new ChangingItemStack(output),new ChangingObject<>(new Long[]{recipe.getRequiredMicroJoulesFor(output)}));
    }
}
