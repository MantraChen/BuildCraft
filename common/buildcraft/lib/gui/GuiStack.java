/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 *
 * Ported to Fabric 1.20.1 by R.Chen (https://github.com/MantraChen).
 */
package buildcraft.lib.gui;

import java.util.function.Supplier;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;

import buildcraft.lib.gui.pos.IGuiPosition;

@Environment(EnvType.CLIENT)
public class GuiStack implements ISimpleDrawable {
    private final Supplier<ItemStack> stack;

    public GuiStack(ItemStack stack) {
        this.stack = () -> stack;
    }

    public GuiStack(Supplier<ItemStack> stack) {
        this.stack = stack;
    }

    @Override
    public void drawAt(double x, double y) {
        drawWithContext(GuiDrawContext.current(), x, y);
    }

    public void drawAt(IGuiPosition pos, double scale) {
        if (!Double.isFinite(scale) || scale <= 0) throw new IllegalArgumentException("scale must be positive");
        DrawContext context = GuiDrawContext.current();
        var matrices = context.getMatrices();
        matrices.push();
        try {
            matrices.translate(pos.getX(), pos.getY(), 0);
            matrices.scale((float) scale, (float) scale, 1);
            drawWithContext(context, 0, 0);
        } finally {
            matrices.pop();
        }
    }

    public void drawWithContext(DrawContext context, double x, double y) {
        ItemStack is = stack.get();
        if (!is.isEmpty()) {
            context.drawItem(is, (int) x, (int) y);
            context.drawItemInSlot(MinecraftClient.getInstance().textRenderer, is, (int) x, (int) y);
        }
    }
}
