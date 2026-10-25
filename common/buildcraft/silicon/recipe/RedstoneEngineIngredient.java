/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.silicon.recipe;

import java.util.List;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.recipe.v1.ingredient.*;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import buildcraft.api.enums.EnumEngineType;
import buildcraft.core.BCCoreBlocks;

/** Uses the same variant resolution as placement, including default and legacy metadata stacks. */
public final class RedstoneEngineIngredient implements CustomIngredient {
    public static final CustomIngredientSerializer<RedstoneEngineIngredient> SERIALIZER=new CustomIngredientSerializer<>() {
        public Identifier getIdentifier(){return new Identifier("buildcraftsilicon:redstone_engine");}
        public RedstoneEngineIngredient read(JsonObject json){return new RedstoneEngineIngredient();}
        public void write(JsonObject json,RedstoneEngineIngredient ingredient){}
        public RedstoneEngineIngredient read(PacketByteBuf buffer){return new RedstoneEngineIngredient();}
        public void write(PacketByteBuf buffer,RedstoneEngineIngredient ingredient){}
    };
    @Override public boolean test(ItemStack stack){return stack.isOf(BCCoreBlocks.engine.asItem()) && BCCoreBlocks.engine.getEngineType(stack)==EnumEngineType.WOOD;}
    @Override public List<ItemStack> getMatchingStacks(){return List.of(BCCoreBlocks.engine.getStack(EnumEngineType.WOOD));}
    @Override public boolean requiresTesting(){return true;}
    @Override public CustomIngredientSerializer<?> getSerializer(){return SERIALIZER;}
}
