/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.lib.client.render.laser;

import java.util.function.ToIntFunction;
import net.minecraft.util.math.Vec3d;
import buildcraft.lib.client.model.MutableQuad;

/** Double-precision beam basis; positions are kept in world coordinates until vertex emission. */
public final class LaserContext {
    public final double length;
    private final ILaserRenderer renderer;
    private final LaserData_BC8 data;
    private final Vec3d axis, up, across;
    private final ToIntFunction<Vec3d> lighting;
    private final boolean diffuse, doubleSided, valid;
    private Vec3d normal = Vec3d.ZERO;
    private float brightness = 1;
    private final double[][] face = new double[4][];
    private int faceIndex;

    public LaserContext(ILaserRenderer renderer, LaserData_BC8 data, boolean diffuse, boolean doubleSided) {
        this(renderer, data, diffuse, doubleSided,
            point -> LaserRenderer_BC8.computeLightmap(point.x, point.y, point.z, data.minBlockLight));
    }
    public LaserContext(ILaserRenderer renderer, LaserData_BC8 data, boolean diffuse, boolean doubleSided,
            ToIntFunction<Vec3d> lighting) {
        this.renderer = renderer; this.data = data; this.diffuse = diffuse; this.doubleSided = doubleSided; this.lighting = lighting;
        Vec3d delta = data.end.subtract(data.start);
        double distance = delta.length();
        valid = finite(data.start) && finite(data.end) && Double.isFinite(distance) && distance > 1e-9
            && Double.isFinite(data.scale) && data.scale > 0 && Double.isFinite(distance / data.scale);
        length = valid ? distance / data.scale : 0;
        axis = valid ? delta.multiply(1 / distance) : new Vec3d(1, 0, 0);
        Vec3d reference = Math.abs(axis.y) > .999 ? new Vec3d(0, 0, 1) : new Vec3d(0, 1, 0);
        across = axis.crossProduct(reference).normalize();
        up = across.crossProduct(axis).normalize();
    }
    private static boolean finite(Vec3d vector) { return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z); }
    public boolean isValid() { return valid; }
    public void setFaceNormal(double nx, double ny, double nz) {
        normal = axis.multiply(nx).add(up.multiply(ny)).add(across.multiply(nz)).normalize();
        brightness = diffuse ? MutableQuad.diffuseLight((float) normal.x, (float) normal.y, (float) normal.z) : 1;
    }
    public void addPoint(double x, double y, double z, double u, double v) {
        if (!valid) return;
        Vec3d point = data.start.add(axis.multiply(x * data.scale)).add(up.multiply(y * data.scale)).add(across.multiply(z * data.scale));
        int light = lighting.applyAsInt(point);
        renderer.vertex(point.x, point.y, point.z, u, v, light, (float) normal.x, (float) normal.y, (float) normal.z, brightness);
        if (doubleSided) {
            face[faceIndex++] = new double[] {point.x, point.y, point.z, u, v, light};
            if (faceIndex == 4) {
                float reverseShade = diffuse ? MutableQuad.diffuseLight((float)-normal.x, (float)-normal.y, (float)-normal.z) : 1;
                for (int i = 3; i >= 0; i--) {
                    double[] vertex = face[i];
                    renderer.vertex(vertex[0], vertex[1], vertex[2], vertex[3], vertex[4], (int)vertex[5],
                        (float)-normal.x, (float)-normal.y, (float)-normal.z, reverseShade);
                }
                faceIndex = 0;
            }
        }
    }
}
