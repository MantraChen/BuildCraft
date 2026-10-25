/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * You can obtain a copy at https://mozilla.org/MPL/2.0/.
 */
package buildcraft.lib.client.sprite;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import buildcraft.lib.gui.GuiDrawContext;

/** CPU pixels in ARGB, uploaded lazily to a native, owned Minecraft texture. */
public final class DynamicTextureBC implements AutoCloseable {
    public final int width, height;
    private NativeImage image;
    private NativeImageBackedTexture texture;
    private Identifier id;
    private boolean dirty = true;

    public DynamicTextureBC(int width, int height) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Texture dimensions must be positive");
        this.width = width;
        this.height = height;
        image = new NativeImage(width, height, true);
    }

    public void setColord(int x, int y, double r, double g, double b, double a) {
        setColori(x, y, channel(r), channel(g), channel(b), channel(a));
    }

    private static int channel(double v) { return (int) Math.round(Math.max(0, Math.min(1, v)) * 255); }
    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    public void setColori(int x, int y, int r, int g, int b, int a) {
        setColor(x, y, clamp(a) << 24 | clamp(r) << 16 | clamp(g) << 8 | clamp(b));
    }

    public void setColor(int x, int y, int argb) {
        ensureOpen();
        image.setColor(x, y, swapRedBlue(argb));
        dirty = true;
    }

    public int getColor(int x, int y) {
        ensureOpen();
        return swapRedBlue(image.getColor(x, y));
    }

    private static int swapRedBlue(int color) {
        return color & 0xFF00FF00 | (color & 0xFF) << 16 | (color >>> 16 & 0xFF);
    }

    public void upload() {
        ensureOpen();
        if (texture == null) {
            texture = new NativeImageBackedTexture(image);
            id = MinecraftClient.getInstance().getTextureManager().registerDynamicTexture("buildcraft_gui", texture);
        } else if (dirty) texture.upload();
        dirty = false;
    }

    public void draw(int x, int y, int z, int u, int v, int drawWidth, int drawHeight) {
        var context = GuiDrawContext.current();
        context.draw();
        upload();
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        context.drawTexture(id, x, y, z, u, v, drawWidth, drawHeight, width, height);
    }

    public void delete() { close(); }

    @Override public void close() {
        if (image == null) return;
        if (id != null) MinecraftClient.getInstance().getTextureManager().destroyTexture(id);
        else image.close();
        image = null;
        texture = null;
        id = null;
    }

    private void ensureOpen() {
        if (image == null) throw new IllegalStateException("Texture has been closed");
    }
}
