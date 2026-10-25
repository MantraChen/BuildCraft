/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.client.guide.font;

import java.util.List;

public interface IFontRenderer {
    int getStringWidth(String text);

    /** 1.20.1 compat alias for {@link #getStringWidth(String)}. */
    default int getWidth(String text) { return getStringWidth(text); }

    int getFontHeight(String text);

    int getMaxFontHeight();

    default int drawString(String text, int x, int y, int colour) {
        return drawString(text, x, y, colour, false, false, 1);
    }

    default int draw(net.minecraft.client.gui.DrawContext context, String text, int x, int y, int colour) {
        try (var frame = buildcraft.lib.gui.GuiDrawContext.use(context)) {
            return drawString(text, x, y, colour);
        }
    }

    default int drawString(String text, int x, int y, int colour, boolean shadow, boolean centered) {
        return drawString(text, x, y, colour, shadow, centered, 1);
    }

    /** @param scale The scale, relative to {@link #getFontHeight(String)} */
    int drawString(String text, int x, int y, int colour, boolean shadow, boolean centered, float scale);

    /** Breaks a string into a list of pieces where the width of each line is always less than or equal to the provided
     * width. Formatting codes will be preserved between lines. */
    default List<String> wrapString(String text, int maxWidth, boolean shadow, float scale) {
        if (!Float.isFinite(scale) || scale <= 0) throw new IllegalArgumentException("scale must be positive");
        List<String> lines = new java.util.ArrayList<>();
        var remainder = buildcraft.lib.client.guide.node.FormatString.split(text);
        int width = Math.max(1, (int) (maxWidth / scale) - (shadow ? 1 : 0));
        while (true) {
            var split = remainder.wrap(this, width);
            lines.add(split[0].getFormatted());
            if (split.length == 1) return lines;
            remainder = split[1];
        }
    }
}
