package buildcraft.lib.gui;

import java.util.function.Consumer;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import buildcraft.api.core.render.ISprite;

/** Native GUI quads with the frame's transform and the sprite's actual texture binding. */
public final class GuiSpriteRenderer {
    private GuiSpriteRenderer() {}

    public static void draw(ISprite sprite, Consumer<VertexConsumer> vertices) {
        DrawContext context = GuiDrawContext.current();
        // Flush text/items before switching to an immediate sprite draw.
        context.draw();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexProgram);
        sprite.bindTexture();
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
        vertices.accept(buffer);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    public static void quad(VertexConsumer out, Matrix4f matrix, ISprite sprite,
            double x1, double y1, double x2, double y2, double u1, double v1, double u2, double v2) {
        vertex(out, matrix, sprite, x1, y1, u1, v1);
        vertex(out, matrix, sprite, x1, y2, u1, v2);
        vertex(out, matrix, sprite, x2, y2, u2, v2);
        vertex(out, matrix, sprite, x2, y1, u2, v1);
    }

    private static void vertex(VertexConsumer out, Matrix4f matrix, ISprite sprite,
            double x, double y, double u, double v) {
        out.vertex(matrix, (float) x, (float) y, 0)
            .texture((float) sprite.getInterpU(u), (float) sprite.getInterpV(v)).next();
    }
}
