/* Copyright (c) 2017 SpaceToad and the BuildCraft team. MPL-2.0. */
package buildcraft.lib.client.guide.parts;

import java.util.List;
import buildcraft.lib.client.guide.GuiGuide;
import buildcraft.lib.client.guide.font.IFontRenderer;
import buildcraft.lib.gui.GuiDrawContext;

/** Wrapped code with line numbers; long blocks continue on subsequent book pages. */
public class GuidePartCodeBlock extends GuidePart {
    public final List<String> lines;
    public GuidePartCodeBlock(GuiGuide gui, List<String> lines) {
        super(gui);
        this.lines = List.copyOf(lines);
    }
    @Override public PagePosition renderIntoArea(int x, int y, int width, int height, PagePosition current, int index) {
        IFontRenderer font = getFontRenderer();
        int numberWidth = font.getWidth(Integer.toString(Math.max(1, lines.size())));
        int rowHeight = font.getMaxFontHeight() + 6;
        for (int lineNumber = 0; lineNumber < lines.size(); lineNumber++) {
            List<String> wrapped = font.wrapString(lines.get(lineNumber), Math.max(1, width - numberWidth - 14), false, 1);
            for (int wrap = 0; wrap < wrapped.size(); wrap++) {
                current = current.guaranteeSpace(rowHeight, height);
                if (current.page == index) {
                    int top = y + current.pixel;
                    GuiGuide.BOX_CODE_SLICED.draw(x + numberWidth + 5, top, width - numberWidth - 5, rowHeight);
                    if ((lineNumber & 1) == 1) GuiDrawContext.current().fill(x + numberWidth + 8, top + 2,
                        x + width - 2, top + rowHeight - 2, 0x40AAAAAA);
                    if (wrap == 0) {
                        String number = Integer.toString(lineNumber + 1);
                        font.drawString(number, x + numberWidth - font.getWidth(number), top + 3, 0);
                    }
                    font.drawString(wrapped.get(wrap), x + numberWidth + 9, top + 3, 0);
                }
                current = current.nextLine(rowHeight, height);
            }
        }
        return current;
    }
    @Override public PagePosition handleMouseClick(int x, int y, int width, int height, PagePosition current,
            int index, int mouseX, int mouseY) {
        return renderIntoArea(x, y, width, height, current, -1);
    }
}
