/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.lib.client.render.laser;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import buildcraft.lib.client.render.laser.LaserData_BC8.LaserType;
import buildcraft.lib.misc.data.Box;

public final class LaserBoxRenderer {
    public static List<LaserData_BC8> edges(Box box, LaserType type, boolean center) {
        if (!box.isInitialized()) return List.of();
        double offset = center ? .5 : 0;
        Vec3d min = Vec3d.of(box.min()).add(offset, offset, offset);
        double extra = center ? .5 : 1;
        Vec3d max = Vec3d.of(box.max()).add(extra, extra, extra);
        List<LaserData_BC8> result = new ArrayList<>(12);
        for (int i = 0; i < 4; i++) {
            double x = (i & 1) == 0 ? min.x : max.x;
            double y = (i & 2) == 0 ? min.y : max.y;
            result.add(new LaserData_BC8(type, new Vec3d(x, y, min.z), new Vec3d(x, y, max.z), 1/16.0));
            double z = (i & 1) == 0 ? min.z : max.z;
            result.add(new LaserData_BC8(type, new Vec3d(min.x, y, z), new Vec3d(max.x, y, z), 1/16.0));
            result.add(new LaserData_BC8(type, new Vec3d(x, min.y, (i & 2) == 0 ? min.z : max.z),
                new Vec3d(x, max.y, (i & 2) == 0 ? min.z : max.z), 1/16.0));
        }
        return List.copyOf(result);
    }
    public static void render(Box box, LaserType type, boolean center, MatrixStack matrices,
            VertexConsumerProvider consumers, Vec3d origin, int overlay) {
        for (var edge : edges(box, type, center)) LaserRenderer_BC8.render(edge, matrices, consumers, origin, overlay);
    }
    public static void renderLaserBoxStatic(Box box, LaserType type, boolean center) {
        for (var edge : edges(box, type, center)) LaserRenderer_BC8.renderLaserStatic(edge);
    }
    public static void renderLaserBoxDynamic(Box box, LaserType type, BufferBuilder buffer, boolean center) {
        for (var edge : edges(box, type, center)) LaserRenderer_BC8.renderLaserDynamic(edge, buffer);
    }
}
