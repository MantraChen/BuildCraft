/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.silicon.recipe;

import java.util.*;
import com.google.gson.*;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.recipe.*;
import net.minecraft.registry.*;
import net.minecraft.util.*;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.World;
import buildcraft.api.mj.MjAPI;
import buildcraft.silicon.BCSiliconRecipes;

/** Data-pack assembly recipes with counted, optionally exact-NBT ingredients. */
public final class NativeAssemblyRecipe implements Recipe<Inventory> {
    public static final long MAX_COST=1_000_000L*MjAPI.MJ;
    public record Input(Ingredient ingredient,int count,NbtCompound nbt) {
        public Input {
            if(ingredient==null || ingredient.isEmpty() || count<1 || count>64)throw new IllegalArgumentException("Invalid assembly ingredient");
            nbt=nbt==null?null:nbt.copy();
        }
        public boolean test(ItemStack stack) {return ingredient.test(stack) && (nbt==null || nbt.equals(stack.getNbt()));}
        @Override public NbtCompound nbt(){return nbt==null?null:nbt.copy();}
    }
    private final Identifier id;
    private final List<Input> inputs;
    private final ItemStack output;
    private final long cost;
    public NativeAssemblyRecipe(Identifier id,List<Input> inputs,ItemStack output,long cost) {
        if(inputs.isEmpty() || inputs.size()>12 || output.isEmpty() || output.getCount()>output.getMaxCount()
            || cost<1 || cost>MAX_COST)throw new IllegalArgumentException("Invalid assembly recipe "+id);
        this.id=id;this.inputs=List.copyOf(inputs);this.output=output.copy();this.cost=cost;
    }
    public long cost(){return cost;}
    public List<Input> inputs(){return inputs;}
    public ItemStack output(){return output.copy();}
    /** Capacity matching prevents broad and exact ingredients from consuming the same items twice. */
    public int[] allocate(List<ItemStack> stacks) {
        int slots=stacks.size(),sink=slots+inputs.size()+1,n=sink+1;
        if(slots>64)return null;
        int[][] residual=new int[n][n];int needed=0;
        for(int s=0;s<slots;s++) {
            var stack=stacks.get(s);residual[0][s+1]=stack.getCount();
            for(int i=0;i<inputs.size();i++)if(!stack.isEmpty() && inputs.get(i).test(stack))residual[s+1][slots+i+1]=64;
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
    @Override public boolean matches(Inventory inventory,World world) {
        var stacks=new ArrayList<ItemStack>();for(int i=0;i<inventory.size();i++)stacks.add(inventory.getStack(i));return allocate(stacks)!=null;
    }
    @Override public ItemStack craft(Inventory inventory,DynamicRegistryManager registries){return output();}
    @Override public boolean fits(int width,int height){return width*height>=inputs.size();}
    @Override public ItemStack getOutput(DynamicRegistryManager registries){return output();}
    @Override public Identifier getId(){return id;}
    @Override public RecipeSerializer<?> getSerializer(){return BCSiliconRecipes.ASSEMBLY_SERIALIZER;}
    @Override public RecipeType<?> getType(){return BCSiliconRecipes.ASSEMBLY;}
    @Override public boolean isIgnoredInRecipeBook(){return true;}
    @Override public DefaultedList<Ingredient> getIngredients() {
        var list=DefaultedList.<Ingredient>of();for(var input:inputs)list.add(input.ingredient);return list;
    }
    public static final class Serializer implements RecipeSerializer<NativeAssemblyRecipe> {
        @Override public NativeAssemblyRecipe read(Identifier id,JsonObject json) {
            var inputs=new ArrayList<Input>();var array=JsonHelper.getArray(json,"ingredients");
            if(array.size()<1 || array.size()>12)throw new JsonParseException("Assembly requires 1 to 12 ingredients");
            for(var element:array) {
                var in=element.getAsJsonObject();
                inputs.add(new Input(Ingredient.fromJson(in.get("ingredient")),JsonHelper.getInt(in,"count",1),readNbt(in)));
            }
            var result=JsonHelper.getObject(json,"result");
            var item=Registries.ITEM.getOrEmpty(new Identifier(JsonHelper.getString(result,"item"))).orElseThrow(()->new JsonParseException("Unknown assembly result"));
            ItemStack output=new ItemStack(item,JsonHelper.getInt(result,"count",1));output.setNbt(readNbt(result));
            long mj=JsonHelper.getLong(json,"mj");if(mj<1 || mj>MAX_COST/MjAPI.MJ)throw new JsonParseException("Invalid assembly MJ cost");
            return new NativeAssemblyRecipe(id,inputs,output,mj*MjAPI.MJ);
        }
        private static NbtCompound readNbt(JsonObject json) {
            if(!json.has("nbt"))return null;
            try{return StringNbtReader.parse(JsonHelper.getString(json,"nbt"));}catch(com.mojang.brigadier.exceptions.CommandSyntaxException error){throw new JsonParseException("Invalid assembly NBT",error);}
        }
        @Override public NativeAssemblyRecipe read(Identifier id,PacketByteBuf buffer) {
            int count=buffer.readVarInt();if(count<1 || count>12)throw new IllegalArgumentException("Invalid assembly ingredient count");
            var inputs=new ArrayList<Input>();for(int i=0;i<count;i++)inputs.add(new Input(Ingredient.fromPacket(buffer),buffer.readVarInt(),buffer.readNbt()));
            return new NativeAssemblyRecipe(id,inputs,buffer.readItemStack(),buffer.readVarLong());
        }
        @Override public void write(PacketByteBuf buffer,NativeAssemblyRecipe recipe) {
            buffer.writeVarInt(recipe.inputs.size());
            for(var input:recipe.inputs){input.ingredient.write(buffer);buffer.writeVarInt(input.count);buffer.writeNbt(input.nbt);}
            buffer.writeItemStack(recipe.output);buffer.writeVarLong(recipe.cost);
        }
    }
}
