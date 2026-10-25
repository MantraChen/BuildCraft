/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 *
 * Ported to Fabric 1.20.1 by R.Chen (https://github.com/MantraChen).
 */
package buildcraft.transport.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.block.Block;
import net.minecraft.nbt.NbtElement;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;

import buildcraft.api.transport.pipe.IItemPipe;
import buildcraft.api.transport.pipe.PipeDefinition;

/** Native pipe placement with type-specific items and saved dye colours. Remake by FelixMantra. */
public class ItemPipeHolder extends BlockItem implements IItemPipe {

    public static final List<ItemPipeHolder> items = new ArrayList<>();

    private final PipeDefinition definition;

    public ItemPipeHolder(PipeDefinition definition) {
        super(buildcraft.transport.BCTransportBlocks.pipeHolder, new Settings());
        this.definition = definition;
    }

    @Override
    public PipeDefinition getDefinition() {
        return definition;
    }

    // Many pipe items share one block. There is no single correct Block.asItem() value.
    @Override public void appendBlocks(Map<Block, Item> map, Item item) {}

    @Override public String getTranslationKey() { return "item.buildcraft_pipe_" + definition.identifier.getPath() + ".name"; }

    @Override public Text getName(ItemStack stack) {
        DyeColor colour = getColour(stack);
        Text name = super.getName(stack);
        return colour == null ? name : Text.translatable("color.minecraft." + colour.getName()).append(" ").append(name);
    }

    public static DyeColor getColour(ItemStack stack) {
        var tag = stack.getNbt();
        if (tag == null || !tag.contains("pipe_colour", NbtElement.NUMBER_TYPE)) return null;
        int id = tag.getInt("pipe_colour");
        return id >= 0 && id < 16 ? DyeColor.byId(id) : null;
    }

    public static ItemStack withColour(ItemStack stack, DyeColor colour) {
        if (colour == null) stack.removeSubNbt("pipe_colour");
        else stack.getOrCreateNbt().putInt("pipe_colour", colour.getId());
        return stack;
    }
}
