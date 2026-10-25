/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.builders.client.render;

import buildcraft.builders.tile.TileBuilder;
import net.minecraft.client.render.*;
import net.minecraft.client.render.block.entity.*;
import net.minecraft.client.util.math.MatrixStack;

public class RenderBuilder implements BlockEntityRenderer<TileBuilder> {
    public RenderBuilder(BlockEntityRendererFactory.Context context) {}
    @Override public void render(TileBuilder tile, float delta, MatrixStack matrices, VertexConsumerProvider vertices,
            int light, int overlay) {
        var box = tile.getBox();
        if (!box.isInitialized()) return;
        var origin = tile.getPos();
        WorldRenderer.drawBox(matrices, vertices.getBuffer(RenderLayer.getLines()),
            box.min().getX()-origin.getX(), box.min().getY()-origin.getY(), box.min().getZ()-origin.getZ(),
            box.max().getX()+1-origin.getX(), box.max().getY()+1-origin.getY(), box.max().getZ()+1-origin.getZ(),
            .3f, .7f, 1f, 1f);
    }
    @Override public boolean rendersOutsideBoundingBox(TileBuilder tile) { return true; }
    @Override public int getRenderDistance() { return 256; }
}
