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
import buildcraft.lib.recipe.IngredientAllocation;
import buildcraft.silicon.BCSiliconRecipes;

/** A separate central item and eight component slots; supplied by native data packs. */
public final class NativeIntegrationRecipe implements Recipe<Inventory> {
    private final Identifier id;
    private final NativeAssemblyRecipe.Input center;
    private final List<NativeAssemblyRecipe.Input> components;
    private final ItemStack output;
    private final long cost;
    private final boolean copyCenterNbt,allowExtra;
    public NativeIntegrationRecipe(Identifier id,NativeAssemblyRecipe.Input center,List<NativeAssemblyRecipe.Input> components,
            ItemStack output,long cost,boolean copyCenterNbt,boolean allowExtra) {
        if(components.size()>8 || output.isEmpty() || output.getCount()>output.getMaxCount()
            || cost<1 || cost>NativeAssemblyRecipe.MAX_COST)throw new IllegalArgumentException("Invalid integration recipe "+id);
        this.id=Objects.requireNonNull(id);this.center=Objects.requireNonNull(center);this.components=List.copyOf(components);
        this.output=output.copy();this.cost=cost;this.copyCenterNbt=copyCenterNbt;this.allowExtra=allowExtra;
    }
    public NativeAssemblyRecipe.Input center(){return center;}
    public List<NativeAssemblyRecipe.Input> components(){return components;}
    public long cost(){return cost;}
    public boolean acceptsCenter(ItemStack stack){return center.test(stack);}
    public int[] allocate(ItemStack target,List<ItemStack> stacks) {
        if(!center.test(target) || target.getCount()<center.count())return null;
        var needs=components.stream().map(in->new IngredientAllocation.Requirement(in::test,in.count())).toList();
        if(!allowExtra && !IngredientAllocation.onlyMatchingInputs(needs,stacks))return null;
        return IngredientAllocation.allocate(needs,stacks);
    }
    public ItemStack result(ItemStack target) {
        var result=output.copy();
        if(copyCenterNbt && target.hasNbt()) {
            var nbt=target.getNbt().copy();if(output.hasNbt())nbt.copyFrom(output.getNbt());result.setNbt(nbt);
        }
        return result;
    }
    @Override public boolean matches(Inventory input,World world) {
        if(input.size()!=9)return false;var others=new ArrayList<ItemStack>();
        for(int i=1;i<9;i++)others.add(input.getStack(i));return allocate(input.getStack(0),others)!=null;
    }
    @Override public ItemStack craft(Inventory input,DynamicRegistryManager registries){return result(input.getStack(0));}
    @Override public ItemStack getOutput(DynamicRegistryManager registries){return output.copy();}
    @Override public boolean fits(int width,int height){return width*height>=components.size()+1;}
    @Override public Identifier getId(){return id;}
    @Override public RecipeType<?> getType(){return BCSiliconRecipes.INTEGRATION;}
    @Override public RecipeSerializer<?> getSerializer(){return BCSiliconRecipes.INTEGRATION_SERIALIZER;}
    @Override public boolean isIgnoredInRecipeBook(){return true;}
    @Override public DefaultedList<Ingredient> getIngredients() {
        var list=DefaultedList.<Ingredient>of();list.add(center.ingredient());for(var input:components)list.add(input.ingredient());return list;
    }
    public static final class Serializer implements RecipeSerializer<NativeIntegrationRecipe> {
        private static NbtCompound nbt(JsonObject json) {
            if(!json.has("nbt"))return null;
            try{return StringNbtReader.parse(JsonHelper.getString(json,"nbt"));}catch(com.mojang.brigadier.exceptions.CommandSyntaxException error){throw new JsonParseException("Invalid integration NBT",error);}
        }
        private static NativeAssemblyRecipe.Input input(JsonObject json) {
            return new NativeAssemblyRecipe.Input(Ingredient.fromJson(json.get("ingredient")),JsonHelper.getInt(json,"count",1),nbt(json));
        }
        @Override public NativeIntegrationRecipe read(Identifier id,JsonObject json) {
            var center=input(JsonHelper.getObject(json,"center"));var inputs=new ArrayList<NativeAssemblyRecipe.Input>();
            var array=JsonHelper.getArray(json,"components");if(array.size()>8)throw new JsonParseException("Integration supports at most eight components");
            for(var element:array)inputs.add(input(element.getAsJsonObject()));
            var out=JsonHelper.getObject(json,"result");
            var item=Registries.ITEM.getOrEmpty(new Identifier(JsonHelper.getString(out,"item"))).orElseThrow(()->new JsonParseException("Unknown integration result"));
            var result=new ItemStack(item,JsonHelper.getInt(out,"count",1));result.setNbt(nbt(out));
            long mj=JsonHelper.getLong(json,"mj");if(mj<1 || mj>NativeAssemblyRecipe.MAX_COST/MjAPI.MJ)throw new JsonParseException("Invalid integration MJ cost");
            return new NativeIntegrationRecipe(id,center,inputs,result,mj*MjAPI.MJ,JsonHelper.getBoolean(json,"copy_center_nbt",false),JsonHelper.getBoolean(json,"allow_extra",false));
        }
        private static NativeAssemblyRecipe.Input readInput(PacketByteBuf buffer){return new NativeAssemblyRecipe.Input(Ingredient.fromPacket(buffer),buffer.readVarInt(),buffer.readNbt());}
        private static void writeInput(PacketByteBuf buffer,NativeAssemblyRecipe.Input input){input.ingredient().write(buffer);buffer.writeVarInt(input.count());buffer.writeNbt(input.nbt());}
        @Override public NativeIntegrationRecipe read(Identifier id,PacketByteBuf buffer) {
            var center=readInput(buffer);int count=buffer.readVarInt();if(count<0 || count>8)throw new IllegalArgumentException("Invalid integration component count");
            var inputs=new ArrayList<NativeAssemblyRecipe.Input>();for(int i=0;i<count;i++)inputs.add(readInput(buffer));
            return new NativeIntegrationRecipe(id,center,inputs,buffer.readItemStack(),buffer.readVarLong(),buffer.readBoolean(),buffer.readBoolean());
        }
        @Override public void write(PacketByteBuf buffer,NativeIntegrationRecipe recipe) {
            writeInput(buffer,recipe.center);buffer.writeVarInt(recipe.components.size());for(var input:recipe.components)writeInput(buffer,input);
            buffer.writeItemStack(recipe.output);buffer.writeVarLong(recipe.cost);buffer.writeBoolean(recipe.copyCenterNbt);buffer.writeBoolean(recipe.allowExtra);
        }
    }
}
