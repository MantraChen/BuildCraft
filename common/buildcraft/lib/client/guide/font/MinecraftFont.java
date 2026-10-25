/* Copyright (c) 2017 SpaceToad and the BuildCraft team. MPL-2.0. */
package buildcraft.lib.client.guide.font;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import buildcraft.lib.gui.GuiDrawContext;

/** The game's font, rendered through the current native GUI frame. */
public enum MinecraftFont implements IFontRenderer {
    INSTANCE;

    private static TextRenderer renderer() { return MinecraftClient.getInstance().textRenderer; }
    @Override public int getStringWidth(String text) { return renderer().getWidth(text); }
    @Override public int getFontHeight(String text) { return getMaxFontHeight(); }
    @Override public int getMaxFontHeight() { return renderer().fontHeight; }

    @Override
    public int drawString(String text, int x, int y, int colour, boolean shadow, boolean centered, float scale) {
        if (!Float.isFinite(scale) || scale <= 0) throw new IllegalArgumentException("scale must be positive");
        var context = GuiDrawContext.current();
        var matrices = context.getMatrices();
        int width = getStringWidth(text);
        matrices.push();
        try {
            matrices.translate(x - (centered ? width * scale / 2 : 0), y, 0);
            matrices.scale(scale, scale, 1);
            context.drawText(renderer(), text, 0, 0, colour, shadow);
        } finally {
            matrices.pop();
        }
        return (int) Math.ceil((width + (shadow ? 1 : 0)) * scale);
    }
}
