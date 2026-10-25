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
public class GuiDiamondWoodPipe extends buildcraft.lib.gui.GuiBC8<buildcraft.transport.container.ContainerDiamondWoodPipe> {
    private static final net.minecraft.util.Identifier TEXTURE = new net.minecraft.util.Identifier("buildcrafttransport:textures/gui/pipe_emerald.png");
    private static final net.minecraft.util.Identifier BUTTONS = new net.minecraft.util.Identifier("buildcrafttransport:textures/gui/pipe_emerald_button.png");
    private static final String[] MODE_KEYS = {"gui.buildcrafttransport.filter.whitelist", "gui.buildcrafttransport.filter.blacklist", "gui.buildcrafttransport.filter.round_robin"};

    public GuiDiamondWoodPipe(buildcraft.transport.container.ContainerDiamondWoodPipe handler,
            net.minecraft.entity.player.PlayerInventory inventory, net.minecraft.text.Text title) {
        super(handler, inventory, title);
        xSize = 175;
        ySize = 161;
    }

    @Override protected void init() {
        super.init();
        int modes = container.behaviour.pipe.getFlow() instanceof buildcraft.api.transport.pipe.IFlowItems ? 3 : 2;
        for (int i = 0; i < modes; i++) addDrawableChild(new ModeButton(i));
    }

    @Override public void drawElementBackgrounds(net.minecraft.client.gui.DrawContext context, float delta) {
        context.drawTexture(TEXTURE, x, y, 0, 0, xSize, ySize);
        super.drawElementBackgrounds(context, delta);
    }

    @Override public void drawElementForegrounds(net.minecraft.client.gui.DrawContext context, float delta) {
        context.drawCenteredTextWithShadow(textRenderer, title, x + xSize / 2, y + 6, 0x404040);
        context.drawText(textRenderer, playerInventoryTitle, x + 8, y + 68, 0x404040, false);
        if (container.behaviour.filterMode == buildcraft.transport.pipe.behaviour.PipeBehaviourWoodDiamond.FilterMode.ROUND_ROBIN) {
            boolean valid = container.behaviour.filterValid;
            context.drawTexture(TEXTURE, x + 6 + (valid ? 18 * container.behaviour.currentFilter : 0), y + 16, 176, valid ? 0 : 20, 20, 20);
        }
        super.drawElementForegrounds(context, delta);
    }

    private final class ModeButton extends net.minecraft.client.gui.widget.PressableWidget {
        private final int mode;
        ModeButton(int mode) {
            super(x + 7 + 18 * mode, y + 41, 18, 18, net.minecraft.text.Text.translatable(MODE_KEYS[mode]));
            this.mode = mode;
            setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(getMessage()));
        }
        @Override public void onPress() {
            if (client.interactionManager != null) client.interactionManager.clickButton(container.syncId, mode);
        }
        @Override public void renderButton(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
            boolean selected = container.behaviour.filterMode.ordinal() == mode;
            context.fill(getX(), getY(), getX() + 18, getY() + 18, isHovered() || isFocused() ? 0xffffffff : 0xff555555);
            context.fill(getX() + 1, getY() + 1, getX() + 17, getY() + 17, selected ? 0xff7777bb : 0xffaaaaaa);
            context.drawTexture(BUTTONS, getX() + 1, getY() + 1, 19 + 18 * mode, 19, 16, 16);
        }
        @Override protected void appendClickableNarrations(net.minecraft.client.gui.screen.narration.NarrationMessageBuilder builder) {
            appendDefaultNarrations(builder);
        }
    }
}
