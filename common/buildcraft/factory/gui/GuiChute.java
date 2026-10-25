/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.factory.gui;

import net.minecraft.util.Identifier;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

import buildcraft.lib.gui.GuiBC8;
import buildcraft.lib.gui.GuiIcon;

import buildcraft.factory.container.ContainerChute;

public class GuiChute extends GuiBC8<ContainerChute> {
    private static final Identifier TEXTURE_BASE = new Identifier("buildcraftfactory:textures/gui/chute.png");
    private static final int SIZE_X = 176, SIZE_Y = 153;
    private static final GuiIcon ICON_GUI = new GuiIcon(TEXTURE_BASE, 0, 0, SIZE_X, SIZE_Y);

    public GuiChute(ContainerChute container) {
        this(container, container.player.getInventory(), container.tile.getCachedState().getBlock().getName());
    }

    public GuiChute(ContainerChute container, PlayerInventory inventory, Text title) {
        super(container, inventory, title);
        xSize = SIZE_X;
        ySize = SIZE_Y;
    }

    @Override public void drawElementBackgrounds(DrawContext context, float partialTicks) {
        ICON_GUI.drawAt(rootElement);
        super.drawElementBackgrounds(context, partialTicks);
    }

    @Override public void drawElementForegrounds(DrawContext context, float partialTicks) {
        context.drawText(textRenderer, title, x + (SIZE_X - textRenderer.getWidth(title)) / 2, y + 6, 0x404040, false);
        context.drawText(textRenderer, playerInventoryTitle, x + 8, y + SIZE_Y - 96, 0x404040, false);
        super.drawElementForegrounds(context, partialTicks);
    }
}
