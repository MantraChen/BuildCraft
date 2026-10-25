/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.builders.client.render;

import buildcraft.builders.tile.TileFiller;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;

/** Native outline for the selected working volume. */
public class RenderFiller implements BlockEntityRenderer<TileFiller> {
    public RenderFiller(BlockEntityRendererFactory.Context context) { }
    @Override public void render(TileFiller tile, float delta, MatrixStack matrices, VertexConsumerProvider vertices,
        int light, int overlay) {
        if (!tile.hasBox()) return;
        var box = tile.getBox();
        var origin = tile.getPos();
        var lines = vertices.getBuffer(RenderLayer.getLines());
        WorldRenderer.drawBox(matrices, lines,
            box.min().getX()-origin.getX(), box.min().getY()-origin.getY(), box.min().getZ()-origin.getZ(),
            box.max().getX()+1-origin.getX(), box.max().getY()+1-origin.getY(), box.max().getZ()+1-origin.getZ(),
            1f, .75f, .1f, 1f);
    }
    @Override public boolean rendersOutsideBoundingBox(TileFiller tile) { return true; }
    @Override public int getRenderDistance() { return 256; }
}
