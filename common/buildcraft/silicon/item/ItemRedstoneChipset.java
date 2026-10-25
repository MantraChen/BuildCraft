/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 *
 * Ported to Fabric 1.20.1 by R.Chen (https://github.com/MantraChen).
 */
package buildcraft.silicon.item;

import net.minecraft.item.Item;

/** Original five chipset materials preserved as native NBT variants. */
public class ItemRedstoneChipset extends Item {

    public ItemRedstoneChipset(Settings settings) {
        super(settings);
    }
    @Override public net.minecraft.text.Text getName(net.minecraft.item.ItemStack stack) {
        return net.minecraft.text.Text.translatable("item.buildcraftsilicon.redstone_chipset."+buildcraft.api.enums.EnumRedstoneChipset.fromStack(stack).asString());
    }
    @Override public void appendTooltip(net.minecraft.item.ItemStack stack,net.minecraft.world.World world,
            java.util.List<net.minecraft.text.Text> tooltip,net.minecraft.client.item.TooltipContext context) {
        tooltip.add(net.minecraft.text.Text.literal("Remake by FelixMantra").formatted(net.minecraft.util.Formatting.GOLD));
    }
}
