/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

public class ItemGuide extends ItemBC_Neptune {
    public static String getBookName(ItemStack stack) {
        var nbt = stack.getNbt();
        if (nbt == null || !nbt.contains("BookName", 8)) return "buildcraftcore:main";
        String name = nbt.getString("BookName");
        return net.minecraft.util.Identifier.isValid(name) ? name : "buildcraftcore:main";
    }

    public static void setBookName(ItemStack stack, String name) {
        stack.getOrCreateNbt().putString("BookName", new net.minecraft.util.Identifier(name).toString());
    }
    public ItemGuide(Settings settings, String id) {
        super(settings, id);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        return TypedActionResult.success(player.getStackInHand(hand));
    }
}
