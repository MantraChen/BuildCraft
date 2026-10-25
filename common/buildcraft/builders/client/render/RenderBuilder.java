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
        var origin = tile.getPos();
        var relative=net.minecraft.util.math.Vec3d.of(origin);
        if (box.isInitialized()) buildcraft.lib.client.render.laser.LaserBoxRenderer.render(box,
            buildcraft.core.client.BuildCraftLaserManager.STRIPES_WRITE,true,matrices,vertices,relative,overlay);
        for (int i=1;i<tile.path.size();i++) {
            var from=net.minecraft.util.math.Vec3d.ofCenter(tile.path.get(i-1));
            var to=net.minecraft.util.math.Vec3d.ofCenter(tile.path.get(i));
            var inset=to.subtract(from).normalize().multiply(.1);
            var laser=new buildcraft.lib.client.render.laser.LaserData_BC8(
                buildcraft.core.client.BuildCraftLaserManager.STRIPES_WRITE_DIRECTION,from.add(inset),to.subtract(inset),1/16.1);
            buildcraft.lib.client.render.laser.LaserRenderer_BC8.render(laser,matrices,vertices,relative,overlay);
        }
        RenderSnapshotBuilder.render(tile,0,0,0,delta,matrices,vertices,light);
    }
    @Override public boolean rendersOutsideBoundingBox(TileBuilder tile) { return true; }
    @Override public int getRenderDistance() { return 256; }
}
