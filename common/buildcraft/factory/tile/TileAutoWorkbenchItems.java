/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported to Fabric 1.20.1 by R.Chen (https://github.com/MantraChen).
 */
package buildcraft.factory.tile;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.util.math.BlockPos;

public class TileAutoWorkbenchItems extends TileAutoWorkbenchBase {
    /** Resolve against the current world's recipes, including data pack reloads and dynamic recipes. */
    public net.minecraft.item.ItemStack getRecipePreview() {
        if (world == null) return net.minecraft.item.ItemStack.EMPTY;
        PreviewInventory input = new PreviewInventory();
        for (int i = 0; i < 9; i++) input.setStack(i, invBlueprint.getStackInSlot(i).copy());
        return world.getRecipeManager().getFirstMatch(net.minecraft.recipe.RecipeType.CRAFTING, input, world)
            .map(recipe -> recipe.craft(input, world.getRegistryManager()))
            .orElse(net.minecraft.item.ItemStack.EMPTY);
    }

    private static final class PreviewInventory extends net.minecraft.inventory.SimpleInventory
            implements net.minecraft.inventory.RecipeInputInventory {
        PreviewInventory() { super(9); }
        @Override public int getWidth() { return 3; }
        @Override public int getHeight() { return 3; }
        @Override public java.util.List<net.minecraft.item.ItemStack> getInputStacks() {
            return java.util.stream.IntStream.range(0, size()).mapToObj(this::getStack).toList();
        }
    }
    public TileAutoWorkbenchItems(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, 3, 3);
    }
}
