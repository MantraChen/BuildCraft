/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 *
 * Ported to Fabric 1.20.1 by R.Chen (https://github.com/MantraChen).
 */
package buildcraft.transport.gui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class GuiDiamondPipe extends buildcraft.lib.gui.GuiBC8<buildcraft.transport.container.ContainerDiamondPipe> {
    private static final int SIZE_X = 175, SIZE_Y = 225;
    private static final buildcraft.lib.gui.GuiIcon BACKGROUND = new buildcraft.lib.gui.GuiIcon(
        new net.minecraft.util.Identifier("buildcrafttransport:textures/gui/filter.png"), 0, 0, SIZE_X, SIZE_Y);
    private static final buildcraft.lib.gui.GuiIcon ACCESSIBLE_BACKGROUND = new buildcraft.lib.gui.GuiIcon(
        new net.minecraft.util.Identifier("buildcrafttransport:textures/gui/filter_cb.png"), 0, 0, SIZE_X, SIZE_Y);

    public GuiDiamondPipe(buildcraft.transport.container.ContainerDiamondPipe handler, net.minecraft.entity.player.PlayerInventory inventory,
            net.minecraft.text.Text title) {
        super(handler, inventory, title);
        xSize = SIZE_X;
        ySize = SIZE_Y;
    }
    @Override public void drawElementBackgrounds(net.minecraft.client.gui.DrawContext context, float partialTicks) {
        (buildcraft.lib.BCLibConfig.colourBlindMode ? ACCESSIBLE_BACKGROUND : BACKGROUND).drawAt(rootElement);
        super.drawElementBackgrounds(context, partialTicks);
    }
    @Override public void drawElementForegrounds(net.minecraft.client.gui.DrawContext context, float partialTicks) {
        context.drawText(textRenderer, title, x + 8, y + 6, 0x404040, false);
        context.drawText(textRenderer, playerInventoryTitle, x + 8, y + SIZE_Y - 97, 0x404040, false);
        super.drawElementForegrounds(context, partialTicks);
    }
}
