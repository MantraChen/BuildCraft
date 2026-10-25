/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.core.list;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Formatting;

import java.util.List;
import net.minecraft.text.Text;

import buildcraft.api.items.IList;

import buildcraft.lib.misc.LocaleUtil;

public enum ListTooltipHandler {
    INSTANCE;

    public void append(PlayerEntity player, ItemStack stack, List<Text> lines) {
        if (!stack.isEmpty() && player != null && player.currentScreenHandler instanceof ContainerList) {
            ItemStack list = player.getMainHandStack();
            if (!list.isEmpty() && list.getItem() instanceof IList) {
                if (((IList) list.getItem()).matches(list, stack)) {
                    lines.add(Text.translatable("tip.list.matches").formatted(Formatting.GREEN));
                }
            }
        }
    }
}
