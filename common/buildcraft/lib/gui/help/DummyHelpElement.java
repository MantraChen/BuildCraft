/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 *
 * Ported to Fabric 1.20.1 by R.Chen (https://github.com/MantraChen).
 */
package buildcraft.lib.gui.help;

import java.util.List;

import buildcraft.lib.gui.IGuiElement;
import buildcraft.lib.gui.pos.IGuiArea;
import buildcraft.lib.gui.pos.GuiRectangle;

public class DummyHelpElement implements IGuiElement {
    public static final DummyHelpElement INSTANCE = new DummyHelpElement();
    private final IGuiArea area;
    private final ElementHelpInfo info;
    public DummyHelpElement() { this(new GuiRectangle(0,0,0,0), null); }
    public DummyHelpElement(IGuiArea area, ElementHelpInfo info) { this.area = area; this.info = info; }
    @Override public double getX() { return area.getX(); }
    @Override public double getY() { return area.getY(); }
    @Override public double getWidth() { return area.getWidth(); }
    @Override public double getHeight() { return area.getHeight(); }
    @Override public void addHelpElements(List<ElementHelpInfo.HelpPosition> elements) {
        if (info != null) elements.add(new ElementHelpInfo.HelpPosition(info, area));
    }
    @Override public void addToolTips(List<buildcraft.lib.gui.elem.ToolTip> tooltips) {}
}
