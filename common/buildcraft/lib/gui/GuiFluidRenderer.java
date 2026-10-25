package buildcraft.lib.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.*;
import net.fabricmc.fabric.api.transfer.v1.client.fluid.FluidVariantRendering;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes;
import buildcraft.lib.client.sprite.SpriteAtlas;
import buildcraft.lib.compat.FluidStackBC;
import buildcraft.lib.gui.pos.IGuiArea;

@Environment(EnvType.CLIENT)
public final class GuiFluidRenderer {
    private GuiFluidRenderer() {}
    public static void draw(IGuiArea area, FluidStackBC fluid, long amount, long capacity) {
        if (fluid == null || fluid.isEmpty() || amount <= 0 || capacity <= 0) return;
        var variant = fluid.getFluidVariant();
        var texture = FluidVariantRendering.getSprite(variant);
        if (texture == null) return;
        double height = area.getHeight() * Math.min(1, amount / (double) capacity);
        double top = FluidVariantAttributes.isLighterThanAir(variant) ? area.getY() : area.getEndY() - height;
        int color = FluidVariantRendering.getColor(variant);
        var sprite = new SpriteAtlas(texture);
        var context = GuiDrawContext.current();
        context.draw();
        RenderSystem.setShaderColor(((color >> 16) & 255) / 255f, ((color >> 8) & 255) / 255f,
            (color & 255) / 255f, ((color >>> 24) & 255) / 255f);
        try {
            GuiSpriteRenderer.draw(sprite, out -> {
                var matrix = context.getMatrices().peek().getPositionMatrix();
                for (double x = 0; x < area.getWidth(); x += 16) {
                    for (double y = 0; y < height; y += 16) {
                        double w = Math.min(16, area.getWidth() - x), h = Math.min(16, height - y);
                        GuiSpriteRenderer.quad(out, matrix, sprite, area.getX() + x, top + y,
                            area.getX() + x + w, top + y + h, 0, 0, w / 16, h / 16);
                    }
                }
            });
        } finally { RenderSystem.setShaderColor(1, 1, 1, 1); }
    }
}
