/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
// Native translucent cuboids share the resource-reloaded FluidVariant sprite atlas.
package buildcraft.lib.client.render.fluid;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import buildcraft.lib.compat.FluidStackBC;

@Environment(EnvType.CLIENT)
public class FluidRenderer {

    public static void renderFluid(FluidSpriteType type, FluidStackBC fluid,
            long amount, long capacity, TankSize size,
            MatrixStack matrices, VertexConsumerProvider vcp, int light) {
        renderFluid(type, fluid, amount, capacity, size, matrices, vcp, light, null);
    }

    public static void renderFluid(FluidSpriteType type, FluidStackBC fluid,
            long amount, long capacity, TankSize size,
            MatrixStack matrices, VertexConsumerProvider vcp, int light, boolean[] faces) {
        if (fluid == null || fluid.isEmpty() || amount <= 0 || capacity <= 0) return;
        var variant = fluid.getFluidVariant();
        var sprites = net.fabricmc.fabric.api.transfer.v1.client.fluid.FluidVariantRendering.getSprites(variant);
        if (sprites == null || sprites.length == 0) return;
        var sprite = sprites[type == FluidSpriteType.FLOWING && sprites.length > 1 ? 1 : 0];
        if (sprite == null) return;
        TankSize filled = filledSize(size, amount, capacity,
            net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes.isLighterThanAir(variant));
        int color = net.fabricmc.fabric.api.transfer.v1.client.fluid.FluidVariantRendering.getColor(variant);
        var buffer = vcp.getBuffer(net.minecraft.client.render.RenderLayer.getEntityTranslucent(
            net.minecraft.screen.PlayerScreenHandler.BLOCK_ATLAS_TEXTURE));
        emit(filled, faces, (face, x, y, z, u, v) -> buffer.vertex(matrices.peek().getPositionMatrix(), x, y, z)
            .color((color >> 16) & 255, (color >> 8) & 255, color & 255, (color >>> 24) & 255)
            .texture(sprite.getFrameU(u * 16), sprite.getFrameV(v * 16))
            .overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV).light(light)
            .normal(matrices.peek().getNormalMatrix(), face.getOffsetX(), face.getOffsetY(), face.getOffsetZ()).next());
    }

    /** Amount and capacity must use the same unit; the caller normally supplies droplets. */
    public static TankSize filledSize(TankSize size, long amount, long capacity, boolean gas) {
        double fraction = capacity <= 0 ? 0 : Math.max(0, Math.min(1, amount / (double) capacity));
        double height = (size.max.y - size.min.y) * fraction;
        return new TankSize(new Vec3d(size.min.x, gas ? size.max.y - height : size.min.y, size.min.z),
            new Vec3d(size.max.x, gas ? size.max.y : size.min.y + height, size.max.z));
    }

    @FunctionalInterface public interface Vertex {
        void add(Direction face, float x, float y, float z, float u, float v);
    }
    public static void emit(TankSize size, boolean[] faces, Vertex out) {
        float x0 = (float) size.min.x, x1 = (float) size.max.x;
        float y0 = (float) size.min.y, y1 = (float) size.max.y;
        float z0 = (float) size.min.z, z1 = (float) size.max.z;
        if (x1 <= x0 || y1 <= y0 || z1 <= z0) return;
        float[][][] vertices = {
            {{x0,y0,z1},{x0,y0,z0},{x1,y0,z0},{x1,y0,z1}},
            {{x0,y1,z0},{x0,y1,z1},{x1,y1,z1},{x1,y1,z0}},
            {{x1,y0,z0},{x0,y0,z0},{x0,y1,z0},{x1,y1,z0}},
            {{x0,y0,z1},{x1,y0,z1},{x1,y1,z1},{x0,y1,z1}},
            {{x0,y0,z0},{x0,y0,z1},{x0,y1,z1},{x0,y1,z0}},
            {{x1,y0,z1},{x1,y0,z0},{x1,y1,z0},{x1,y1,z1}}
        };
        for (Direction face : Direction.values()) {
            if (faces != null && (face.getId() >= faces.length || !faces[face.getId()])) continue;
            float width = face.getAxis() == Direction.Axis.X ? z1-z0 : x1-x0;
            float height = face.getAxis() == Direction.Axis.Y ? z1-z0 : y1-y0;
            for (int i = 0; i < 4; i++) {
                float[] v = vertices[face.getId()][i];
                out.add(face, v[0], v[1], v[2], (i >= 2 ? width : 0), (i == 1 || i == 2 ? height : 0));
            }
        }
    }

    public static class TankSize {
        public final Vec3d min, max;

        public TankSize(double sx, double sy, double sz, double ex, double ey, double ez) {
            this.min = new Vec3d(sx / 16.0, sy / 16.0, sz / 16.0);
            this.max = new Vec3d(ex / 16.0, ey / 16.0, ez / 16.0);
        }

        public TankSize(int sx, int sy, int sz, int ex, int ey, int ez) {
            this.min = new Vec3d(sx / 16.0, sy / 16.0, sz / 16.0);
            this.max = new Vec3d(ex / 16.0, ey / 16.0, ez / 16.0);
        }

        public TankSize(Vec3d min, Vec3d max) {
            this.min = min;
            this.max = max;
        }

        public TankSize shrink(double by) {
            return new TankSize(min.add(by, by, by), max.subtract(by, by, by));
        }

        public TankSize shrink(double x, double y, double z) {
            return new TankSize(min.add(x, y, z), max.subtract(x, y, z));
        }

        public TankSize shink(Vec3d by) {
            return new TankSize(min.add(by), max.subtract(by));
        }

        public TankSize rotateY() {
            Vec3d _min = new Vec3d(1 - max.z, min.y, min.x);
            Vec3d _max = new Vec3d(1 - min.z, max.y, max.x);
            return new TankSize(
                new Vec3d(Math.min(_min.x, _max.x), Math.min(_min.y, _max.y), Math.min(_min.z, _max.z)),
                new Vec3d(Math.max(_min.x, _max.x), Math.max(_min.y, _max.y), Math.max(_min.z, _max.z))
            );
        }

        public TankSize rotateYClockwise() { return rotateY(); }
    }
}
