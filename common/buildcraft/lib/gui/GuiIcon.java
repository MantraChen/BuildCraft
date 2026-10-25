/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 *
 * Ported to Fabric 1.20.1 by R.Chen (https://github.com/MantraChen).
 */
package buildcraft.lib.gui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.minecraft.util.Identifier;

import buildcraft.api.core.render.ISprite;

import buildcraft.lib.gui.pos.IGuiArea;

@Environment(EnvType.CLIENT)
public class GuiIcon implements ISimpleDrawable {
    public final ISprite sprite;
    public final int textureSize;

    // Legacy constructor: (Identifier texture, int u, int v, int width, int height)
    // Used by unmigrated GUI code before SpriteRaw migration.
    public final int u, v, width, height;
    public final Identifier texture;

    public GuiIcon(ISprite sprite, int textureSize) {
        this.sprite = java.util.Objects.requireNonNull(sprite, "sprite");
        this.textureSize = textureSize;
        this.texture = null;
        this.u = this.v = 0;
        this.width = (int) Math.round(Math.abs(sprite.getInterpU(1) - sprite.getInterpU(0)) * textureSize);
        this.height = (int) Math.round(Math.abs(sprite.getInterpV(1) - sprite.getInterpV(0)) * textureSize);
    }

    /** Forge-compat constructor: (texture, u, v, width, height). Used before SpriteRaw migration. */
    public GuiIcon(Identifier texture, int u, int v, int width, int height) {
        this.sprite = new buildcraft.lib.client.sprite.SpriteRaw(texture, u, v, width, height, 256);
        this.textureSize = 256;
        this.texture = texture;
        this.u = u;
        this.v = v;
        this.width = width;
        this.height = height;
    }

    public static void draw(ISprite sprite, double x1, double y1, double x2, double y2) {
        if (x1 == x2 || y1 == y2) return;
        var matrix = GuiDrawContext.current().getMatrices().peek().getPositionMatrix();
        GuiSpriteRenderer.draw(sprite, out -> GuiSpriteRenderer.quad(out, matrix, sprite, x1, y1, x2, y2, 0, 0, 1, 1));
    }

    @Override
    public void drawAt(double x, double y) {
        draw(sprite, x, y, x + width, y + height);
    }

    public void drawAt(IGuiArea area) {
        drawAt(area.getX(), area.getY());
    }

    public void drawScaledInside(IGuiArea element) {
        draw(sprite, element.getX(), element.getY(), element.getX() + element.getWidth(),
            element.getY() + element.getHeight());
    }

    /** Returns a new GuiIcon with (u,v) offset by (du,dv) on the same texture. */
    public GuiIcon offset(int du, int dv) {
        if (texture != null) {
            return new GuiIcon(texture, u + du, v + dv, width, height);
        }
        ISprite parent = sprite;
        double duNormalized = du / (double) textureSize, dvNormalized = dv / (double) textureSize;
        return new GuiIcon(new ISprite() {
            public void bindTexture() { parent.bindTexture(); }
            public double getInterpU(double u) { return parent.getInterpU(u) + duNormalized; }
            public double getInterpV(double v) { return parent.getInterpV(v) + dvNormalized; }
        }, textureSize);
    }

    /** Returns true if the mouse position is within this icon drawn at (x, y). */
    public boolean containsGuiPos(double x, double y, buildcraft.lib.gui.pos.IGuiPosition mouse) {
        if (mouse == null) return false;
        double mx = mouse.getX(), my = mouse.getY();
        return mx >= x && mx < x + width && my >= y && my < y + height;
    }
}
