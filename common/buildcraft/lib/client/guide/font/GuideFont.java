/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * You can obtain a copy at https://mozilla.org/MPL/2.0/.
 */
package buildcraft.lib.client.guide.font;

import java.awt.Color;
import java.awt.Font;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Formatting;
import buildcraft.lib.client.guide.node.FormatSegment;
import buildcraft.lib.client.guide.node.FormatString;
import buildcraft.lib.client.sprite.DynamicTextureBC;
import buildcraft.lib.gui.GuiDrawContext;

/** Optional smooth font. Textures are bounded, reused between frames, and explicitly released. */
public class GuideFont implements IFontRenderer, AutoCloseable {
    private static final FontRenderContext METRICS = new FontRenderContext(null, true, true);
    private static final int MAX_TEXTURES = 64;
    private final Font font;
    private final Map<Key, DynamicTextureBC> cache = new LinkedHashMap<>(16, 0.75f, true);
    private record Key(String text, int color, boolean shadow, float density) {}

    public GuideFont(Font font) { this.font = java.util.Objects.requireNonNull(font); }

    private Font styled(FormatSegment run) {
        int style = font.getStyle();
        if (run.misc.contains(Formatting.BOLD)) style |= Font.BOLD;
        if (run.misc.contains(Formatting.ITALIC)) style |= Font.ITALIC;
        return font.deriveFont(style);
    }

    private double advance(FormatSegment run) { return styled(run).getStringBounds(run.text, METRICS).getWidth(); }

    @Override public int getStringWidth(String text) {
        double width = 0;
        for (var run : FormatString.split(text).segments) width += advance(run);
        return (int) Math.ceil(width);
    }
    @Override public int getFontHeight(String text) { return getMaxFontHeight(); }
    @Override public int getMaxFontHeight() { return (int) Math.ceil(font.getLineMetrics("Ag", METRICS).getHeight()); }

    @Override
    public int drawString(String text, int x, int y, int color, boolean shadow, boolean centered, float scale) {
        if (!Float.isFinite(scale) || scale <= 0) throw new IllegalArgumentException("scale must be positive");
        if (text.isEmpty()) return 0;
        if ((color & 0xFF000000) == 0) color |= 0xFF000000;
        float windowScale = (float) MinecraftClient.getInstance().getWindow().getScaleFactor();
        Key key = new Key(text, color, shadow, windowScale * scale);
        DynamicTextureBC texture = cache.computeIfAbsent(key, this::rasterize);
        if (cache.size() > MAX_TEXTURES) {
            var iterator = cache.entrySet().iterator();
            iterator.next().getValue().close();
            iterator.remove();
        }
        int logicalWidth = getStringWidth(text) + (shadow ? 1 : 0);
        var context = GuiDrawContext.current();
        var matrices = context.getMatrices();
        matrices.push();
        try {
            matrices.translate(x - (centered ? logicalWidth * scale / 2 : 0), y, 0);
            matrices.scale(1 / windowScale, 1 / windowScale, 1);
            context.setShaderColor(1, 1, 1, 1);
            texture.draw(0, 0, 0, 0, 0, texture.width, texture.height);
        } finally {
            matrices.pop();
        }
        return (int) Math.ceil(logicalWidth * scale);
    }

    private DynamicTextureBC rasterize(Key key) {
        return rasterize(key.text, key.color, key.shadow, key.density);
    }

    /** Creates CPU pixels only; the caller owns and must close the returned texture. */
    public DynamicTextureBC rasterize(String text, int color, boolean shadow, float density) {
        if (!Float.isFinite(density) || density <= 0) throw new IllegalArgumentException("density must be positive");
        Key key = new Key(text, color, shadow, density);
        int width = Math.max(1, (int) Math.ceil((getStringWidth(key.text) + (key.shadow ? 1 : 0)) * key.density));
        int height = Math.max(1, (int) Math.ceil((getMaxFontHeight() + (key.shadow ? 1 : 0)) * key.density));
        BufferedImage raster = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        var graphics = raster.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            graphics.scale(key.density, key.density);
            FormatSegment[] runs = FormatString.split(key.text).segments;
            for (int pass = key.shadow ? 0 : 1; pass < 2; pass++) {
                float x = pass == 0 ? 1 : 0;
                float baseline = font.getLineMetrics("Ag", METRICS).getAscent() + (pass == 0 ? 1 : 0);
                for (FormatSegment run : runs) {
                    Font selected = styled(run);
                    graphics.setFont(selected);
                    int rgb = run.colour == null ? key.color : key.color & 0xFF000000 | run.colour.getColorValue();
                    if (pass == 0) rgb = (rgb & 0xFF000000) | ((rgb & 0xFCFCFC) >> 2);
                    graphics.setColor(new Color(rgb, true));
                    graphics.drawString(run.text, x, baseline);
                    float next = x + (float) advance(run);
                    if (run.misc.contains(Formatting.UNDERLINE)) graphics.draw(new java.awt.geom.Line2D.Float(x, baseline + 1, next, baseline + 1));
                    if (run.misc.contains(Formatting.STRIKETHROUGH)) graphics.draw(new java.awt.geom.Line2D.Float(x, baseline - selected.getSize2D() / 3, next, baseline - selected.getSize2D() / 3));
                    x = next;
                }
            }
        } finally {
            graphics.dispose();
        }
        DynamicTextureBC texture = new DynamicTextureBC(width, height);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) texture.setColor(x, y, raster.getRGB(x, y));
        return texture;
    }

    @Override public void close() {
        cache.values().forEach(DynamicTextureBC::close);
        cache.clear();
    }
}
