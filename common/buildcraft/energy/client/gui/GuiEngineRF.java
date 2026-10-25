/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.energy.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import buildcraft.lib.gui.GuiBC8;
import buildcraft.lib.gui.ledger.LedgerEngine;
import buildcraft.energy.container.ContainerEngineRF;
import buildcraft.energy.tile.TileEngineRF;

public class GuiEngineRF extends GuiBC8<ContainerEngineRF> {
    private static final Identifier TEXTURE = new Identifier("buildcraftenergy:textures/gui/rf_engine_gui.png");
    public GuiEngineRF(ContainerEngineRF container) {
        this(container, container.player.getInventory(), Text.translatable("tile.engine.rf.name"));
    }
    public GuiEngineRF(ContainerEngineRF container, PlayerInventory inventory, Text title) {
        super(container, inventory, title);
        xSize = 176; ySize = 176;
        shownElements.add(new LedgerEngine(this, container.tile, true));
    }
    @Override public void drawElementBackgrounds(DrawContext context, float delta) {
        context.drawTexture(TEXTURE, x, y, 0, 0, 176, 176);
        int filled = (int) Math.min(60, container.tile.getCurrentRF() * 60L / TileEngineRF.MAX_RF);
        if (filled > 0) context.drawTexture(TEXTURE, x + 31, y + 78 - filled, 176, 60 - filled, 6, filled);
        super.drawElementBackgrounds(context, delta);
    }
    @Override public void drawElementForegrounds(DrawContext context, float delta) {
        context.drawText(textRenderer, title, x + 8, y + 6, 0x404040, false);
        context.drawText(textRenderer, container.tile.getCurrentRF() + " / " + TileEngineRF.MAX_RF + " RF", x + 48, y + 65, 0x404040, false);
        context.drawText(textRenderer, "Remake by FelixMantra", x + 8, y + 83, 0x404040, false);
        super.drawElementForegrounds(context, delta);
    }
}
