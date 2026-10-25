/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.energy.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import buildcraft.lib.gui.*;
import buildcraft.lib.gui.ledger.LedgerEngine;
import buildcraft.lib.gui.pos.GuiRectangle;
import buildcraft.energy.container.ContainerEngineIron_BC8;

public class GuiEngineIron_BC8 extends GuiBC8<ContainerEngineIron_BC8> {
    private static final Identifier TEXTURE = new Identifier("buildcraftenergy:textures/gui/combustion_engine_gui.png");
    private static final GuiIcon BACKGROUND = new GuiIcon(TEXTURE,0,0,176,177);
    private static final GuiIcon OVERLAY = new GuiIcon(TEXTURE,176,0,16,60);
    public GuiEngineIron_BC8(ContainerEngineIron_BC8 container) {
        this(container,container.player.getInventory(),Text.translatable("gui.buildcraftenergy.engine_iron"));
    }
    public GuiEngineIron_BC8(ContainerEngineIron_BC8 container, PlayerInventory inventory, Text title) {
        super(container,inventory,title); xSize=176; ySize=177;
        shownElements.add(new LedgerEngine(this,container.tile,true));
        shownElements.add(container.widgetTankFuel.createGuiElement(this,new GuiRectangle(26,18,16,60).offset(rootElement),OVERLAY));
        shownElements.add(container.widgetTankCoolant.createGuiElement(this,new GuiRectangle(80,18,16,60).offset(rootElement),OVERLAY));
        shownElements.add(container.widgetTankResidue.createGuiElement(this,new GuiRectangle(134,18,16,60).offset(rootElement),OVERLAY));
    }
    @Override public void drawElementBackgrounds(DrawContext context,float partialTicks) {
        BACKGROUND.drawAt(rootElement); super.drawElementBackgrounds(context,partialTicks);
    }
    @Override public void drawElementForegrounds(DrawContext context,float partialTicks) {
        context.drawText(textRenderer,title,x+(176-textRenderer.getWidth(title))/2,y+6,0x404040,false);
        context.drawText(textRenderer,playerInventoryTitle,x+8,y+81,0x404040,false);
        super.drawElementForegrounds(context,partialTicks);
    }
}
