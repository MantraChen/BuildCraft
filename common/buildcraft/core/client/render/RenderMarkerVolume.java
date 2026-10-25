/* Copyright (c) 2016 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.core.client.render;

import java.util.Set;
import net.minecraft.client.render.*;
import net.minecraft.client.render.block.entity.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.*;
import buildcraft.core.BCCoreConfig;
import buildcraft.core.client.BuildCraftLaserManager;
import buildcraft.core.tile.TileMarkerVolume;
import buildcraft.lib.client.render.laser.*;

public final class RenderMarkerVolume implements BlockEntityRenderer<TileMarkerVolume> {
    public RenderMarkerVolume(BlockEntityRendererFactory.Context context) {}
    @Override public void render(TileMarkerVolume marker, float delta, MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay) {
        if (!marker.isShowingSignals()) return;
        var connection = marker.getCurrentConnection();
        Set<Direction.Axis> occupied = connection == null ? Set.of() : connection.getConnectedAxis();
        Vec3d origin = Vec3d.of(marker.getPos());
        for (Direction direction : Direction.values()) {
            if (occupied.contains(direction.getAxis())) continue;
            Vec3d axis = Vec3d.of(direction.getVector());
            Vec3d start = origin.add(.5, .5, .5).add(axis.multiply(1/16.0));
            Vec3d end = origin.add(.5, .5, .5).add(axis.multiply(BCCoreConfig.markerMaxDistance-1/16.0));
            LaserRenderer_BC8.render(new LaserData_BC8(BuildCraftLaserManager.MARKER_VOLUME_SIGNAL, start, end, 1/16.2),
                matrices, consumers, origin, overlay);
        }
    }
    @Override public boolean rendersOutsideBoundingBox(TileMarkerVolume tile) { return true; }
    @Override public int getRenderDistance() { return 256; }
}
