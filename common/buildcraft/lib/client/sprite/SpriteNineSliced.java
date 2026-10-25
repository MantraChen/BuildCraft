/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 *
 * Ported to Fabric 1.20.1 by R.Chen (https://github.com/MantraChen).
 */
package buildcraft.lib.client.sprite;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;


import buildcraft.api.core.render.ISprite;

import buildcraft.lib.gui.pos.IGuiArea;

/** Defines and draws a 9-sliced sprite, retaining the sprite's texture and the GUI transform. */
@Environment(EnvType.CLIENT)
public class SpriteNineSliced {
    public final ISprite sprite;
    public final double xMin, yMin, xMax, yMax;
    public final double xScale, yScale;

    public SpriteNineSliced(ISprite sprite, int xMin, int yMin, int xMax, int yMax, int textureSize) {
        this(sprite, xMin, yMin, xMax, yMax, textureSize, textureSize);
    }

    public SpriteNineSliced(ISprite sprite, int xMin, int yMin, int xMax, int yMax, int xScale, int yScale) {
        this.sprite = sprite;
        this.xMin = xMin / (double) xScale;
        this.yMin = yMin / (double) yScale;
        this.xMax = xMax / (double) xScale;
        this.yMax = yMax / (double) yScale;
        this.xScale = xScale;
        this.yScale = yScale;
    }

    public SpriteNineSliced(ISprite sprite, double xMin, double yMin, double xMax, double yMax, double scale) {
        this(sprite, xMin, yMin, xMax, yMax, scale, scale);
    }

    public SpriteNineSliced(ISprite sprite, double xMin, double yMin, double xMax, double yMax, double xScale,
        double yScale) {
        this.sprite = sprite;
        this.xMin = xMin;
        this.yMin = yMin;
        this.xMax = xMax;
        this.yMax = yMax;
        this.xScale = xScale;
        this.yScale = yScale;
    }

    public void draw(IGuiArea element) {
        draw(element.getX(), element.getY(), element.getWidth(), element.getHeight());
    }

    public void draw(double x, double y, double width, double height) {
        if (width <= 0 || height <= 0) return;
        var matrix = buildcraft.lib.gui.GuiDrawContext.current().getMatrices().peek().getPositionMatrix();
        buildcraft.lib.gui.GuiSpriteRenderer.draw(sprite, out -> emit(out, matrix, x, y, width, height));
    }

    /** Emits the same geometry used by draw, without requiring an OpenGL context. */
    public void emit(net.minecraft.client.render.VertexConsumer out, org.joml.Matrix4f matrix,
            double x, double y, double width, double height) {
        if (width <= 0 || height <= 0) return;
        double[] xa = edges(x, width, xMin * xScale, (1 - xMax) * xScale);
        double[] ya = edges(y, height, yMin * yScale, (1 - yMax) * yScale);
        double[] ua = { 0, xMin, xMax, 1 };
        double[] va = { 0, yMin, yMax, 1 };
        for (int xi = 0; xi < 3; xi++) {
            for (int yi = 0; yi < 3; yi++) {
                if (xa[xi] == xa[xi + 1] || ya[yi] == ya[yi + 1]) continue;
                buildcraft.lib.gui.GuiSpriteRenderer.quad(out, matrix, sprite,
                    xa[xi], ya[yi], xa[xi + 1], ya[yi + 1], ua[xi], va[yi], ua[xi + 1], va[yi + 1]);
            }
        }
    }

    private static double[] edges(double start, double size, double before, double after) {
        // Small panels shrink their borders instead of inverting the centre quad.
        double factor = before + after > size ? size / (before + after) : 1;
        return new double[] { start, start + before * factor, start + size - after * factor, start + size };
    }
}
