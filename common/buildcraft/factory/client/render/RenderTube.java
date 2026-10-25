/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.factory.client.render;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import buildcraft.lib.client.render.laser.*;
import buildcraft.factory.tile.TileMiner;

/** Shared native pipe mesh for a pump or mining well; the machine owns the block-entity renderer. */
public final class RenderTube {
    private final LaserData_BC8.LaserType type;
    public RenderTube(LaserData_BC8.LaserType type) { this.type = type; }
    public void render(TileMiner tile, float delta, MatrixStack matrices, VertexConsumerProvider consumers, int overlay) {
        if (tile.isComplete()) return;
        double length = tile.getLength(delta);
        if (!Double.isFinite(length) || length <= 0) return;
        Vec3d origin = Vec3d.of(tile.getPos());
        Vec3d start = origin.add(.5, 0, .5);
        var beam = new LaserData_BC8(type, start, start.add(0, -length, 0), 1/16.0);
        LaserRenderer_BC8.render(beam, matrices, consumers, origin, overlay);
    }
}
