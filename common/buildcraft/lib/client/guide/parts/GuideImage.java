/* Copyright (c) 2017 SpaceToad and the BuildCraft team. MPL-2.0. */
package buildcraft.lib.client.guide.parts;

import buildcraft.api.core.render.ISprite;
import buildcraft.lib.client.guide.GuiGuide;
import buildcraft.lib.gui.GuiIcon;

public class GuideImage extends GuidePart {
    private final ISprite sprite;
    private final int imageWidth, imageHeight;
    public GuideImage(GuiGuide gui, ISprite sprite, int srcWidth, int srcHeight, int width, int height) {
        super(gui);
        this.sprite = sprite;
        imageWidth = width > 0 ? width : srcWidth;
        imageHeight = height > 0 ? height : srcHeight;
    }
    @Override public PagePosition renderIntoArea(int x, int y, int width, int height, PagePosition current, int index) {
        double scale = Math.min(1, Math.min(width / (double) imageWidth, (height - 1.0) / imageHeight));
        int w = Math.max(1, (int)(imageWidth * scale)), h = Math.max(1, (int)(imageHeight * scale));
        current = current.guaranteeSpace(h, height);
        if (current.page == index) GuiIcon.draw(sprite, x, y + current.pixel, x + w, y + current.pixel + h);
        return current.nextLine(h + 1, height);
    }
    @Override public PagePosition handleMouseClick(int x, int y, int width, int height, PagePosition current,
            int index, int mouseX, int mouseY) {
        return renderIntoArea(x, y, width, height, current, -1);
    }
}
