/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.factory.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import buildcraft.lib.gui.*;
import buildcraft.lib.gui.pos.GuiRectangle;
import buildcraft.factory.container.ContainerDistiller;

/** Native tank interactions and server-authoritative MJ progress. Remake by FelixMantra. */
public class GuiDistiller extends GuiBC8<ContainerDistiller> {
    private static final Identifier TEXTURE = new Identifier("buildcraftfactory:textures/gui/distiller.png");
    private static final GuiIcon BACKGROUND = new GuiIcon(TEXTURE, 0, 0, 176, 161);
    private static final GuiIcon VERTICAL = new GuiIcon(TEXTURE, 0, 161, 16, 38);
    private static final GuiIcon HORIZONTAL = new GuiIcon(TEXTURE, 17, 161, 34, 17);
    public GuiDistiller(ContainerDistiller container) {
        this(container, container.player.getInventory(), container.tile.getCachedState().getBlock().getName());
    }
    public GuiDistiller(ContainerDistiller container, PlayerInventory inventory, Text title) {
        super(container, inventory, title);
        xSize = 176; ySize = 161;
        shownElements.add(container.widgetInputTank.createGuiElement(this,
            new GuiRectangle(44, 23, 16, 38).offset(rootElement), VERTICAL));
        shownElements.add(container.widgetOutputGasTank.createGuiElement(this,
            new GuiRectangle(98, 10, 34, 17).offset(rootElement), HORIZONTAL));
        shownElements.add(container.widgetOutputLiquidTank.createGuiElement(this,
            new GuiRectangle(98, 54, 34, 17).offset(rootElement), HORIZONTAL));
    }
    @Override public void drawElementBackgrounds(DrawContext context, float ticks) {
        BACKGROUND.drawAt(rootElement);
        context.fill(x + 137, y + 23, x + 145, y + 61, 0xFF303030);
        int filled = 36 * container.getProgress() / 10000;
        context.fill(x + 138, y + 60 - filled, x + 144, y + 60, 0xFFDEA832);
        super.drawElementBackgrounds(context, ticks);
    }
    @Override public void drawElementForegrounds(DrawContext context, float ticks) {
        context.drawText(textRenderer, title, x + 8, y + 6, 0x404040, false);
        context.drawText(textRenderer, container.getStoredMj() + " MJ", x + 8, y + 65, 0x404040, false);
        context.fill(x, y + 164, x + 176, y + 178, 0xC0202020);
        String credit = "Remake by FelixMantra";
        context.drawText(textRenderer, credit, x + (176 - textRenderer.getWidth(credit)) / 2, y + 167, 0xFFF0CE, false);
        super.drawElementForegrounds(context, ticks);
    }
}
