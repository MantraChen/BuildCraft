/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.builders.client.render;

import net.minecraft.client.render.*;
import net.minecraft.client.render.block.entity.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import buildcraft.builders.tile.TileArchitectTable;
import buildcraft.core.client.BuildCraftLaserManager;
import buildcraft.lib.client.render.laser.LaserBoxRenderer;

public final class RenderArchitectTable implements BlockEntityRenderer<TileArchitectTable> {
    public RenderArchitectTable(BlockEntityRendererFactory.Context context) {}
    @Override public void render(TileArchitectTable tile, float delta, MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay) {
        if (tile.markerBox) LaserBoxRenderer.render(tile.box, BuildCraftLaserManager.STRIPES_READ, true,
            matrices, consumers, Vec3d.of(tile.getPos()), overlay);
    }
    @Override public boolean rendersOutsideBoundingBox(TileArchitectTable tile) { return true; }
    @Override public int getRenderDistance() { return 256; }
}
