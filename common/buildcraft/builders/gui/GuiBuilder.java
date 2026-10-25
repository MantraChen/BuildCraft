/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.builders.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import buildcraft.lib.gui.GuiBC8;
import buildcraft.lib.gui.GuiIcon;
import buildcraft.lib.gui.pos.GuiRectangle;
import buildcraft.builders.container.ContainerBuilder;

public class GuiBuilder extends GuiBC8<ContainerBuilder> {
    private static final Identifier BASE = new Identifier("buildcraftbuilders:textures/gui/builder.png");
    private static final Identifier BLUEPRINT = new Identifier("buildcraftbuilders:textures/gui/builder_blueprint.png");
    private static final GuiIcon OVERLAY = new GuiIcon(BLUEPRINT, 0, 54, 16, 47);
    private ButtonWidget excavate;
    public GuiBuilder(ContainerBuilder container, PlayerInventory inventory, Text title) {
        super(container, inventory, title);
        xSize = 256; ySize = 232;
        for (int i = 0; i < container.widgetTanks.size(); i++)
            shownElements.add(container.widgetTanks.get(i).createGuiElement(this,
                new GuiRectangle(179 + i * 18, 145, 16, 47).offset(rootElement), OVERLAY));
    }
    public GuiBuilder(ContainerBuilder container) {
        this(container, container.player.getInventory(), Text.translatable("gui.buildcraftbuilders.builder"));
    }
    @Override protected void init() {
        super.init();
        excavate = addDrawableChild(ButtonWidget.builder(Text.empty(), button ->
            container.tile.sendCanExcavate(!container.tile.canExcavate())
        ).dimensions(x+8, y+48, 160, 18).build());
        refreshLabels();
    }
    @Override protected void handledScreenTick() { super.handledScreenTick(); refreshLabels(); }
    private void refreshLabels() {
        if (excavate != null) excavate.setMessage(Text.translatable("gui.buildcraftbuilders.excavate",
            Text.translatable(container.tile.canExcavate() ? "options.on" : "options.off")));
    }
    @Override public void drawElementBackgrounds(DrawContext context, float delta) {
        context.drawTexture(BASE, x, y, 0, 0, 176, 222);
        context.drawTexture(BLUEPRINT, x+169, y, 169, 0, 87, 222);
        context.fill(x+4, y+221, x+252, y+232, 0xffc6c6c6);
        super.drawElementBackgrounds(context, delta);
    }
    @Override public void drawElementForegrounds(DrawContext context, float delta) {
        context.drawText(textRenderer, title, x+8, y+6, 0x404040, false);
        context.drawText(textRenderer, playerInventoryTitle, x+8, y+128, 0x404040, false);
        context.drawText(textRenderer, Text.translatable("gui.buildcraftbuilders.materials"), x+179, y+6, 0x404040, false);
        context.drawText(textRenderer, "Remake by FelixMantra", x+8, y+223, 0x606060, false);
        if (container.tile.getPathCount()>1) context.drawText(textRenderer,
            Text.translatable("gui.buildcraftbuilders.path_progress",container.tile.getPathIndex()+1,container.tile.getPathCount()),x+8,y+18,0x404040,false);
        var materials = container.tile.getRemainingMaterials();
        for (int i = 0; i < materials.size(); i++) {
            var material = materials.get(i);
            int sx = x+179+(i%4)*18, sy = y+18+(i/4)*18;
            context.drawItem(material.stack(), sx, sy);
            long count = material.count();
            String label = count > 999 ? "999+" : Long.toString(count);
            context.drawItemInSlot(textRenderer, material.stack(), sx, sy, label);
        }
        super.drawElementForegrounds(context, delta);
    }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        var materials = container.tile.getRemainingMaterials();
        for (int i = 0; i < materials.size(); i++) {
            int sx = x+179+(i%4)*18, sy = y+18+(i/4)*18;
            if (mouseX >= sx && mouseX < sx+16 && mouseY >= sy && mouseY < sy+16) {
                var material = materials.get(i);
                context.drawTooltip(textRenderer, Text.literal(material.count()+" × ").append(material.stack().getName()), mouseX, mouseY);
            }
        }
        if (mouseX >= x+8 && mouseX < x+169 && mouseY >= y+6 && mouseY < y+18) {
            Text status = container.tile.getPendingEntities() > 0
                ? Text.translatable("gui.buildcraftbuilders.entities_pending", container.tile.getPendingEntities())
                : container.tile.isFinished() ? Text.translatable("gui.buildcraftbuilders.finished")
                : Text.translatable("gui.buildcraftbuilders.builder_remaining", container.tile.getCountToBreak(), container.tile.getCountToPlace());
            context.drawTooltip(textRenderer, status, mouseX, mouseY);
        }
    }
}
