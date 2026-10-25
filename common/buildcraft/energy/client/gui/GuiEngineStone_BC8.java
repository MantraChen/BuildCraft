/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.energy.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import buildcraft.lib.gui.*;
import buildcraft.lib.gui.help.*;
import buildcraft.lib.gui.ledger.LedgerEngine;
import buildcraft.lib.gui.pos.*;
import buildcraft.energy.container.ContainerEngineStone_BC8;

public class GuiEngineStone_BC8 extends GuiBC8<ContainerEngineStone_BC8> {
    private static final Identifier TEXTURE = new Identifier("buildcraftenergy:textures/gui/steam_engine_gui.png");
    private static final GuiIcon BACKGROUND = new GuiIcon(TEXTURE, 0, 0, 176, 166);
    private final IGuiArea flame = new GuiRectangle(81, 25, 14, 14).offset(rootElement);
    public GuiEngineStone_BC8(ContainerEngineStone_BC8 container) {
        this(container, container.player.getInventory(), Text.translatable("gui.buildcraftenergy.engine_stone"));
    }
    public GuiEngineStone_BC8(ContainerEngineStone_BC8 container, PlayerInventory inventory, Text title) {
        super(container, inventory, title);
        xSize = 176; ySize = 166;
        shownElements.add(new LedgerEngine(this, container.tile, true));
        shownElements.add(new DummyHelpElement(flame.expand(2),
            new ElementHelpInfo("buildcraft.help.stone_engine.flame.title", "buildcraft.help.stone_engine.flame")));
        shownElements.add(new DummyHelpElement(new GuiRectangle(78,39,20,20).offset(rootElement),
            new ElementHelpInfo("buildcraft.help.stone_engine.fuel.title", "buildcraft.help.stone_engine.fuel")));
    }
    @Override public void drawElementBackgrounds(DrawContext context, float partialTicks) {
        BACKGROUND.drawAt(rootElement);
        double amount = Math.max(0, Math.min(1, container.tile.deltaFuelLeft.getDynamic(partialTicks) / 100));
        int height = (int) Math.ceil(amount * 14);
        if (height > 0) new GuiIcon(TEXTURE, 176, 14-height, 14, height).drawAt(flame.getX(), flame.getEndY()-height);
        super.drawElementBackgrounds(context, partialTicks);
    }
    @Override public void drawElementForegrounds(DrawContext context, float partialTicks) {
        context.drawText(textRenderer, title, x+(176-textRenderer.getWidth(title))/2, y+6, 0x404040, false);
        context.drawText(textRenderer, playerInventoryTitle, x+8, y+70, 0x404040, false);
        super.drawElementForegrounds(context, partialTicks);
    }
}
