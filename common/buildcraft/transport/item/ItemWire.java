/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 *
 * Ported to Fabric 1.20.1 by R.Chen (https://github.com/MantraChen).
 */
package buildcraft.transport.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DyeColor;
import net.minecraft.text.Text;

/** Native wire stacks preserve colour in inventories and drops. Remake by FelixMantra. */
public class ItemWire extends Item {

    public ItemWire(String id) {
        super(new Settings());
    }
    public static DyeColor getColour(ItemStack stack) {
        var nbt = stack.getNbt();
        int id = nbt == null ? 0 : nbt.getInt("wire_colour");
        return id >= 0 && id < 16 ? DyeColor.byId(id) : DyeColor.WHITE;
    }
    public ItemStack stack(DyeColor colour) {
        var stack = new ItemStack(this);
        stack.getOrCreateNbt().putInt("wire_colour", colour.getId());
        return stack;
    }
    @Override public Text getName(ItemStack stack) {
        return Text.translatable("item.buildcrafttransport.wire", Text.translatable("color." + getColour(stack).getName().replace('_','.')));
    }
}
