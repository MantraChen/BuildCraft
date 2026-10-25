/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.client.guide.parts;

import java.util.List;

import javax.annotation.Nonnull;

import com.google.common.collect.ImmutableList;

import net.minecraft.item.ItemStack;

import buildcraft.lib.client.guide.GuiGuide;
import buildcraft.lib.client.guide.entry.ItemStackValueFilter;
import buildcraft.lib.client.guide.entry.PageEntryItemStack;
import buildcraft.lib.client.guide.entry.PageValue;

@Deprecated
public class GuidePageStandInRecipes extends GuidePage {
    public GuidePageStandInRecipes(GuiGuide gui, List<GuidePart> parts, ItemStack stack) {
        super(gui, parts, new PageValue<>(PageEntryItemStack.INSTANCE, new ItemStackValueFilter(stack)));
    }

    @Nonnull
    public static GuidePageFactory createFactory(@Nonnull ItemStack stack) {
        // Resolve at page-open time, so a server recipe reload cannot leave cached factories stale.
        ItemStack copy = stack.copy();
        return gui -> new GuidePageStandInRecipes(gui, ImmutableList.of(), copy);
    }

    @Override
    public boolean shouldPersistHistory() {
        return false;
    }

    @Override
    public GuidePageBase createReloaded() {
        // Rebuild recipe parts and sprites after the resource/recipe source changes.
        return createFactory(entry.value instanceof buildcraft.lib.client.guide.entry.ItemStackValueFilter filter ? filter.stack.baseStack : ItemStack.EMPTY).createNew(gui);
    }
}
