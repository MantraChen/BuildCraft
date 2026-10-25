/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.builders.client.render;

import buildcraft.builders.tile.TileFiller;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;

/** Remake by FelixMantra: native working volume, robot, beams and material previews. */
public class RenderFiller implements BlockEntityRenderer<TileFiller> {
    public RenderFiller(BlockEntityRendererFactory.Context context) { }
    @Override public void render(TileFiller tile, float delta, MatrixStack matrices, VertexConsumerProvider vertices,
        int light, int overlay) {
        if (!tile.hasBox()) return;
        if (tile.markerBox) buildcraft.lib.client.render.laser.LaserBoxRenderer.render(tile.box,
            buildcraft.core.client.BuildCraftLaserManager.STRIPES_WRITE,true,matrices,vertices,
            net.minecraft.util.math.Vec3d.of(tile.getPos()),overlay);
        RenderSnapshotBuilder.render(tile,0,0,0,delta,matrices,vertices,light);
    }
    @Override public boolean rendersOutsideBoundingBox(TileFiller tile) { return true; }
    @Override public int getRenderDistance() { return 256; }
}
