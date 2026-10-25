/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.client.guide.parts;

import buildcraft.lib.gui.GuiDrawContext;
import buildcraft.lib.gui.GuiStack;

import net.minecraft.item.ItemStack;

import buildcraft.lib.client.guide.GuiGuide;
import buildcraft.lib.client.guide.GuideManager;
import buildcraft.lib.gui.pos.GuiRectangle;
import buildcraft.lib.misc.ItemStackKey;

public abstract class GuidePartItem extends GuidePart {
    public static final GuiRectangle STACK_RECT = new GuiRectangle(0, 0, 16, 16);

    public GuidePartItem(GuiGuide gui) {
        super(gui);
    }

    protected void drawItemStack(ItemStackKey stack, int x, int y) {
        drawItemStack(stack.baseStack, x, y);
    }

    protected void drawItemStack(ItemStack stack, int x, int y) {
        if (stack != null && !stack.isEmpty()) {
            GuiDrawContext.current().setShaderColor(1, 1, 1, 1);
            new GuiStack(stack).drawAt(x, y);
            if (STACK_RECT.offset(x, y).contains(gui.mouse)) {
                gui.tooltipStack = stack;
            }
        }
    }

    protected void testClickItemStack(ItemStackKey stack, int x, int y) {
        testClickItemStack(stack.baseStack, x, y);
    }

    protected void testClickItemStack(ItemStack stack, int x, int y) {
        if (stack != null && !stack.isEmpty() && STACK_RECT.offset(x, y).contains(gui.mouse)) {
            gui.openPage(GuideManager.INSTANCE.getPageFor(stack).createNew(gui));
        }
    }
}
