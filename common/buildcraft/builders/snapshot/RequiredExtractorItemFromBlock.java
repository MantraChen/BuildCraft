/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.snapshot;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.block.BlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

public class RequiredExtractorItemFromBlock extends RequiredExtractor {
    @Nonnull
    @Override
    public List<ItemStack> extractItemsFromBlock(@Nonnull BlockState blockState, @Nullable NbtCompound tileNbt) {
        if (blockState.getBlock() instanceof net.minecraft.block.FlowerPotBlock pot) {
            ItemStack container = new ItemStack(net.minecraft.item.Items.FLOWER_POT);
            ItemStack plant = new ItemStack(pot.getContent());
            return plant.isEmpty() ? List.of(container) : List.of(container, plant);
        }
        if (blockState.contains(net.minecraft.state.property.Properties.DOUBLE_BLOCK_HALF)
            && blockState.get(net.minecraft.state.property.Properties.DOUBLE_BLOCK_HALF) == net.minecraft.block.enums.DoubleBlockHalf.UPPER)
            return List.of();
        if (blockState.contains(net.minecraft.state.property.Properties.BED_PART)
            && blockState.get(net.minecraft.state.property.Properties.BED_PART) == net.minecraft.block.enums.BedPart.HEAD)
            return List.of();
        int count = blockState.contains(net.minecraft.state.property.Properties.SLAB_TYPE)
            && blockState.get(net.minecraft.state.property.Properties.SLAB_TYPE) == net.minecraft.block.enums.SlabType.DOUBLE ? 2 : 1;
        if (blockState.getBlock() instanceof net.minecraft.block.SnowBlock) count = blockState.get(net.minecraft.block.SnowBlock.LAYERS);
        return List.of(new ItemStack(Item.fromBlock(blockState.getBlock()), count));
    }
}
