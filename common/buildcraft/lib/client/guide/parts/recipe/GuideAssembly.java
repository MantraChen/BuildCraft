/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.client.guide.parts.recipe;

import java.util.Arrays;
import java.util.Collections;




import buildcraft.lib.client.guide.GuiGuide;
import buildcraft.lib.client.guide.parts.GuidePartItem;
import buildcraft.lib.gui.GuiIcon;
import buildcraft.lib.gui.pos.GuiRectangle;
import buildcraft.lib.misc.LocaleUtil;
import buildcraft.lib.recipe.ChangingItemStack;
import buildcraft.lib.recipe.ChangingObject;

public class GuideAssembly extends GuidePartItem {
    public static final GuiIcon INPUT_LIST = new GuiIcon(GuiGuide.ICONS_2, 119, 108, 98, 54);
    private static final GuiIcon INPUT_COLUMNS = new GuiIcon(GuiGuide.ICONS_2,119,108,36,54);
    private static final GuiIcon OUTPUT_PANEL = new GuiIcon(GuiGuide.ICONS_2,155,108,62,54);
    public static final GuiRectangle[] ITEM_POSITION = new GuiRectangle[6];
    public static final GuiRectangle OUT_POSITION = new GuiRectangle(77, 19, 16, 16);
    public static final GuiRectangle MJ_POSITION = new GuiRectangle(50, 4, 6, 46);
    public static final GuiRectangle OFFSET = new GuiRectangle((GuiGuide.PAGE_LEFT_TEXT.width - INPUT_LIST.width) / 2,
        0, INPUT_LIST.width, INPUT_LIST.height);
    public static final int PIXEL_HEIGHT = 60;

    static {
        for (int x = 0; x < 2; x++) {
            for (int y = 0; y < 3; y++) {
                ITEM_POSITION[x + y * 2] = new GuiRectangle(1 + x * 18, 1 + y * 18, 16, 16);
            }
        }
    }

    private final ChangingItemStack[] input;
    private final ChangingItemStack output;
    private final ChangingObject<Long> mjCost;
    private final net.minecraft.nbt.NbtCompound identity;
    private final int columns;
    private int extraWidth(){return (columns-2)*18;}
    private GuiRectangle inputPosition(int index){return new GuiRectangle(1+index%columns*18,1+index/columns*18,16,16);}

    GuideAssembly(GuiGuide gui, ChangingItemStack[] input, ChangingItemStack output, ChangingObject<Long> mjCost) {
        super(gui);
        if(input.length>12)throw new IllegalArgumentException("Guide assembly supports at most twelve inputs");
        columns=input.length>6?4:2;
        this.input = input;
        this.output = output;
        this.mjCost = mjCost;
        identity=GuideAssemblyFactory.identity(input,output,mjCost);
    }

    @Override
    public int hashCode() {
        return identity.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) {
            return false;
        }
        GuideAssembly other = (GuideAssembly) obj;
        return identity.equals(other.identity);
    }

    @Override
    public PagePosition renderIntoArea(int x, int y, int width, int height, PagePosition current, int index) {
        if (current.pixel + PIXEL_HEIGHT > height) {
            current = current.newPage();
        }
        x += (width-INPUT_LIST.width-extraWidth())/2;
        y += OFFSET.y + current.pixel;
        if (current.page == index) {
            if(columns==2)INPUT_LIST.drawAt(x,y);
            else{INPUT_COLUMNS.drawAt(x,y);INPUT_COLUMNS.drawAt(x+36,y);OUTPUT_PANEL.drawAt(x+72,y);}
            // Render the item


            for (int i = 0; i < input.length; i++) {
                GuiRectangle rect = inputPosition(i);
                drawItemStack(input[i].get(), x + (int) rect.x, y + (int) rect.y);
            }

            drawItemStack(output.get(), x + (int) OUT_POSITION.x + extraWidth(), y + (int) OUT_POSITION.y);

            if (MJ_POSITION.offset(x+extraWidth(), y).contains(gui.mouse)) {
                gui.tooltips.add(Collections.singletonList(LocaleUtil.localizeMj(mjCost.get())));
            }


        }
        current = current.nextLine(PIXEL_HEIGHT, height);
        return current;
    }

    @Override
    public PagePosition handleMouseClick(int x, int y, int width, int height, PagePosition current, int index,
        int mouseX, int mouseY) {
        if (current.pixel + PIXEL_HEIGHT > height) {
            current = current.newPage();
        }
        x += (width-INPUT_LIST.width-extraWidth())/2;
        y += OFFSET.y + current.pixel;
        if (current.page == index) {
            for (int i = 0; i < input.length; i++) {
                GuiRectangle rect = inputPosition(i);
                testClickItemStack(input[i].get(), x + (int) rect.x, y + (int) rect.y);
            }

            testClickItemStack(output.get(), x + (int) OUT_POSITION.x + extraWidth(), y + (int) OUT_POSITION.y);
        }
        current = current.nextLine(PIXEL_HEIGHT, height);
        return current;
    }
}
