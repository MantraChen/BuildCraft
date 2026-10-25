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
        return buildcraft.lib.recipe.IngredientAllocation.allocate(inputs.stream()
            .map(input->new buildcraft.lib.recipe.IngredientAllocation.Requirement(input::test,input.count())).toList(),stacks);
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
