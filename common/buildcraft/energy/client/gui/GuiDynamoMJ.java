/* Copyright (c) 2017 SpaceToad and the BuildCraft team. MPL-2.0. */
package buildcraft.energy.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import buildcraft.lib.gui.GuiBC8;
import buildcraft.energy.container.ContainerDynamoMJ;

public class GuiDynamoMJ extends GuiBC8<ContainerDynamoMJ> {
    private static final Identifier TEXTURE = new Identifier("buildcraftenergy", "textures/gui/mj_dynamo_gui.png");
    public GuiDynamoMJ(ContainerDynamoMJ container) {
        this(container, container.player.getInventory(), Text.translatable("tile.mjDynamo.name"));
    }
    public GuiDynamoMJ(ContainerDynamoMJ container, PlayerInventory inventory, Text title) {
        super(container, inventory, title);
        xSize = 176; ySize = 176;
        shownElements.add(new LedgerDynamoMJ(this, container.tile, true));
    }
    @Override public void drawElementBackgrounds(DrawContext context, float delta) {
        context.drawTexture(TEXTURE, x, y, 0, 0, 176, 176);
        int filled = (int) Math.min(60, container.tile.getMjStored() * 60.0 / container.tile.battery.getCapacity());
        if (filled > 0) context.drawTexture(TEXTURE, x + 139, y + 78 - filled, 176, 60 - filled, 6, filled);
        super.drawElementBackgrounds(context, delta);
    }
    @Override public void drawElementForegrounds(DrawContext context, float delta) {
        context.drawText(textRenderer, title, x + 8, y + 6, 0x404040, false);
        context.drawText(textRenderer, "Remake by FelixMantra", x + 8, y + 83, 0x404040, false);
        super.drawElementForegrounds(context, delta);
    }
}
