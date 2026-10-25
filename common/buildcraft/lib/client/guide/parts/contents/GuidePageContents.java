/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 */
package buildcraft.lib.client.guide.parts.contents;

import java.util.*;
import buildcraft.lib.client.guide.GuiGuide;
import buildcraft.lib.client.guide.GuideManager;
import buildcraft.lib.client.guide.font.IFontRenderer;
import buildcraft.lib.client.guide.parts.*;

public class GuidePageContents extends GuidePageBase {
    private final ContentsNodeGui contents;
    private String query = "";
    public GuidePageContents(GuiGuide gui) {
        super(gui);
        contents = GuideManager.INSTANCE.getGuiContents(gui, this, gui.sortingOrder);
        contents.setFontRenderer(gui.getCurrentFont());
        setupChapters();
    }
    public void setQuery(String query) {
        this.query = query;
        if (query.isBlank()) contents.node.resetVisibility();
        else contents.node.setVisible(new HashSet<>(GuideManager.INSTANCE.quickSearcher.search(query.toLowerCase(Locale.ROOT), Integer.MAX_VALUE).results));
        contents.invalidate();
        numPages = -1;
        goToPage(0);
        setupChapters();
        gui.refreshChapters();
    }
    @Override public void setFontRenderer(IFontRenderer font) { super.setFontRenderer(font); contents.setFontRenderer(font); }
    @Override public String getTitle() { return "Contents"; }
    @Override public List<GuideChapter> getChapters() { return contents.getChapters(); }
    @Override public GuidePageBase createReloaded() {
        GuidePageContents page = new GuidePageContents(gui);
        page.setQuery(query);
        return page;
    }
    @Override protected void renderPage(int x, int y, int width, int height, int index) {
        super.renderPage(x, y, width, height, index);
        PagePosition end = contents.render(x, y, width, height, new PagePosition(0, 0), index);
        numPages = Math.max(1, end.newPage().page);
        if (end.page == 0 && end.pixel == 0 && index == 0) getFontRenderer().drawString("No matching entries", x, y, 0);
    }
    @Override public void handleMouseClick(int x, int y, int width, int height, int mouseX, int mouseY,
            int button, int index, boolean editing) {
        super.handleMouseClick(x, y, width, height, mouseX, mouseY, button, index, editing);
        contents.onClicked(x, y, width, height, new PagePosition(0, 0), index);
    }
}
