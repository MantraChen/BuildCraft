/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.factory.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import buildcraft.lib.gui.GuiBC8;
import buildcraft.lib.gui.GuiIcon;
import buildcraft.lib.gui.pos.GuiRectangle;
import buildcraft.factory.container.ContainerTank;

public class GuiTank extends GuiBC8<ContainerTank> {
    private static final Identifier TEXTURE_BASE = new Identifier("buildcraftfactory:textures/gui/tank.png");
    private static final int SIZE_X = 176, SIZE_Y = 181;
    private static final GuiIcon ICON_GUI = new GuiIcon(TEXTURE_BASE, 0, 0, SIZE_X, SIZE_Y);
    private static final GuiIcon ICON_TANK_OVERLAY = new GuiIcon(TEXTURE_BASE, 176, 0, 16, 64);
    public GuiTank(ContainerTank container) {
        this(container, container.player.getInventory(), container.tile.getCachedState().getBlock().getName());
    }
    public GuiTank(ContainerTank container, PlayerInventory inventory, Text title) {
        super(container, inventory, title);
        xSize = SIZE_X; ySize = SIZE_Y;
        shownElements.add(container.widgetTank.createGuiElement(this,
            new GuiRectangle(80, 18, 16, 64).offset(rootElement), ICON_TANK_OVERLAY));
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
