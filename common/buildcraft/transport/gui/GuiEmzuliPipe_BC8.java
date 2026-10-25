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
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import buildcraft.lib.gui.GuiBC8;
import buildcraft.transport.container.ContainerEmzuliPipe_BC8;
import buildcraft.transport.pipe.behaviour.PipeBehaviourEmzuli.SlotIndex;

@Environment(EnvType.CLIENT)
public class GuiEmzuliPipe_BC8 extends GuiBC8<ContainerEmzuliPipe_BC8> {
    private static final Identifier TEXTURE = new Identifier("buildcrafttransport:textures/gui/pipe_emzuli.png");
    private final PaintButton[] paints = new PaintButton[4];

    public GuiEmzuliPipe_BC8(ContainerEmzuliPipe_BC8 handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        xSize = 176;
        ySize = 166;
    }

    @Override protected void init() {
        super.init();
        for (var slot : SlotIndex.VALUES) paints[slot.ordinal()] = addDrawableChild(new PaintButton(slot));
    }

    @Override protected void handledScreenTick() {
        super.handledScreenTick();
        for (var button : paints) button.refreshTooltip();
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1 || button == 2) {
            for (var paint : paints) if (paint.isMouseOver(mouseX, mouseY)) { paint.change(button); return true; }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public void drawElementBackgrounds(DrawContext context, float delta) {
        context.drawTexture(TEXTURE, x, y, 0, 0, xSize, ySize);
        for (var slot : SlotIndex.VALUES) {
            int sx = x + (slot.ordinal() < 2 ? 6 : 157), sy = y + (slot.ordinal() % 2 == 0 ? 24 : 52);
            boolean active = container.isActive(slot);
            context.fill(sx, sy, sx + 10, sy + 10, active ? 0xff4b9c39 : 0xff777777);
            if (container.isCurrent(slot)) context.drawText(textRenderer, ">", sx + 2, sy + 1, 0xffffffff, false);
        }
        super.drawElementBackgrounds(context, delta);
    }

    @Override public void drawElementForegrounds(DrawContext context, float delta) {
        context.drawText(textRenderer, title, x + (xSize - textRenderer.getWidth(title)) / 2, y + 6, 0x404040, false);
        context.drawText(textRenderer, playerInventoryTitle, x + 8, y + 73, 0x404040, false);
        super.drawElementForegrounds(context, delta);
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        for (var slot : SlotIndex.VALUES) {
            int sx = x + (slot.ordinal() < 2 ? 4 : 155), sy = y + (slot.ordinal() % 2 == 0 ? 21 : 49);
            if (mouseX >= sx && mouseX < sx + 16 && mouseY >= sy && mouseY < sy + 16) {
                String key = container.isCurrent(slot) ? "current" : container.isActive(slot) ? "active" : "inactive";
                context.drawTooltip(textRenderer, Text.translatable("gui.buildcrafttransport.emzuli." + key), mouseX, mouseY);
            }
        }
    }

    private final class PaintButton extends PressableWidget {
        private final SlotIndex slot;
        PaintButton(SlotIndex slot) {
            super(x + (slot.ordinal() < 2 ? 49 : 106), y + (slot.ordinal() % 2 == 0 ? 19 : 47), 20, 20, Text.empty());
            this.slot = slot;
            refreshTooltip();
        }
        void refreshTooltip() {
            var colour = container.getColour(slot);
            Text label = colour == null ? Text.translatable("gui.buildcrafttransport.emzuli.no_paint")
                : Text.translatable("gui.buildcrafttransport.emzuli.paint", Text.translatable("item.minecraft." + colour.getName() + "_dye"));
            setMessage(label);
            setTooltip(Tooltip.of(label.copy().append("\n").append(Text.translatable("gui.buildcrafttransport.emzuli.paint_controls"))));
        }
        void change(int button) {
            var old = container.getColour(slot);
            int encoded = old == null ? 0 : old.getId() + 1;
            encoded = button == 2 ? 0 : Math.floorMod(encoded + (button == 1 ? -1 : 1), 17);
            if (client.interactionManager != null) client.interactionManager.clickButton(container.syncId, slot.ordinal() * 17 + encoded);
        }
        @Override public void onPress() { change(0); }
        @Override public void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
            context.drawTexture(TEXTURE, getX(), getY(), 176, isHovered() || isFocused() ? 20 : 0, 20, 20);
            var colour = container.getColour(slot);
            if (colour == null) context.drawText(textRenderer, "×", getX() + 7, getY() + 6, 0xff555555, false);
            else {
                context.fill(getX() + 4, getY() + 4, getX() + 16, getY() + 16, 0xff000000 | colour.getSignColor());
                context.fill(getX() + 6, getY() + 6, getX() + 9, getY() + 8, 0xffeeeeee);
            }
        }
        @Override protected void appendClickableNarrations(net.minecraft.client.gui.screen.narration.NarrationMessageBuilder builder) {
            appendDefaultNarrations(builder);
        }
    }
}
