/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.lib.recipe;

import java.util.*;
import java.util.function.Predicate;
import net.minecraft.item.ItemStack;

/** Counted matching across split stacks, including overlapping broad/exact ingredients. */
public final class IngredientAllocation {
    private IngredientAllocation() {}
    public record Requirement(Predicate<ItemStack> test,int count) {
        public Requirement {
            Objects.requireNonNull(test,"ingredient");
            if(count<1 || count>4096)throw new IllegalArgumentException("Ingredient count out of bounds");
        }
    }
    public static int[] allocate(List<Requirement> inputs,List<ItemStack> stacks) {
        int slots=stacks.size();if(slots>64 || inputs.size()>64)return null;
        int sink=slots+inputs.size()+1,n=sink+1,needed=0;
        int[][] residual=new int[n][n];
        for(int s=0;s<slots;s++) {
            var stack=stacks.get(s);residual[0][s+1]=stack.getCount();
            for(int i=0;i<inputs.size();i++)if(!stack.isEmpty() && inputs.get(i).test.test(stack))residual[s+1][slots+i+1]=stack.getCount();
        }
        for(int i=0;i<inputs.size();i++){int count=inputs.get(i).count;residual[slots+i+1][sink]=count;needed+=count;}
        int supplied=0;
        while(supplied<needed) {
            int[] parent=new int[n];Arrays.fill(parent,-1);parent[0]=0;
            var queue=new ArrayDeque<Integer>();queue.add(0);
            while(!queue.isEmpty() && parent[sink]<0) {
                int from=queue.remove();
                for(int to=1;to<n;to++)if(parent[to]<0 && residual[from][to]>0){parent[to]=from;queue.add(to);}
            }
            if(parent[sink]<0)return null;
            int amount=Integer.MAX_VALUE;
            for(int to=sink;to!=0;to=parent[to])amount=Math.min(amount,residual[parent[to]][to]);
            for(int to=sink;to!=0;to=parent[to]){int from=parent[to];residual[from][to]-=amount;residual[to][from]+=amount;}
            supplied+=amount;
        }
        int[] used=new int[slots];for(int s=0;s<slots;s++)used[s]=stacks.get(s).getCount()-residual[0][s+1];return used;
    }
    /** Surplus amounts of a required material are allowed; unrelated materials are not. */
    public static boolean onlyMatchingInputs(List<Requirement> inputs,List<ItemStack> stacks) {
        for(var stack:stacks)if(!stack.isEmpty() && inputs.stream().noneMatch(in->in.test.test(stack)))return false;
        return true;
    }
}
