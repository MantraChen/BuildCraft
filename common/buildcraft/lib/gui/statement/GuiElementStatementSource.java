/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 *
 * Ported to Fabric 1.20.1 by R.Chen (https://github.com/MantraChen).
 */
package buildcraft.lib.gui.statement;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class GuiElementStatementSource {
    private static final buildcraft.lib.gui.GuiIcon BACKGROUND = new buildcraft.lib.gui.GuiIcon(
        new net.minecraft.util.Identifier("buildcraftlib", "textures/gui/misc_slots.png"), 0, 0, 18, 18);

    public static void drawGuiSlot(buildcraft.api.statements.IGuiSlot slot, double x, double y) {
        BACKGROUND.drawAt(x, y);
        if (slot != null && slot.getSprite() != null)
            buildcraft.lib.gui.GuiIcon.draw(slot.getSprite(), x + 1, y + 1, x + 17, y + 17);
    }
}
