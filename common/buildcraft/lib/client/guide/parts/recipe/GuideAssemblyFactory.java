/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.lib.client.guide.parts.recipe;

import java.util.Arrays;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import buildcraft.lib.client.guide.GuiGuide;
import buildcraft.lib.client.guide.parts.GuidePartFactory;
import buildcraft.lib.recipe.ChangingItemStack;
import buildcraft.lib.recipe.ChangingObject;

public class GuideAssemblyFactory implements GuidePartFactory {
    private final ChangingItemStack[] input;
    private final ChangingItemStack output;
    private final ChangingObject<Long> mjCost;
    private final NbtCompound identity;
    public GuideAssemblyFactory(ChangingItemStack[] input,ChangingItemStack output,ChangingObject<Long> mjCost){
        this.input=input;this.output=output;this.mjCost=mjCost;identity=identity(input,output,mjCost);
    }
    public GuideAssemblyFactory(ItemStack[] input,ItemStack output,long mjCost){
        this(Arrays.stream(input).map(ChangingItemStack::new).toArray(ChangingItemStack[]::new),new ChangingItemStack(output),new ChangingObject<>(new Long[]{mjCost}));
    }
    /** Recipe equality includes counts and costs; ItemStackKey deliberately ignores stack counts. */
    static NbtCompound identity(ChangingItemStack[] input,ChangingItemStack output,ChangingObject<Long> cost){
        var tag=new NbtCompound();var inputs=new NbtList();for(var stack:input)inputs.add(options(stack));
        tag.put("inputs",inputs);tag.put("output",options(output));tag.putLongArray("mj",cost.getOptions().stream().mapToLong(Long::longValue).toArray());return tag;
    }
    private static NbtList options(ChangingItemStack stack){var list=new NbtList();for(var option:stack.getOptions())list.add(option.baseStack.writeNbt(new NbtCompound()));return list;}
    @Override public GuideAssembly createNew(GuiGuide gui){return new GuideAssembly(gui,input,output,mjCost);}
    @Override public int hashCode(){return identity.hashCode();}
    @Override public boolean equals(Object other){return other instanceof GuideAssemblyFactory factory&&identity.equals(factory.identity);}
}
