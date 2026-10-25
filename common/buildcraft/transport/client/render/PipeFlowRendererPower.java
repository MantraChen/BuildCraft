/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 *
 * Ported to Fabric 1.20.1 by R.Chen (https://github.com/MantraChen).
 */
package buildcraft.transport.client.render;

import org.joml.Vector3f;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import buildcraft.api.mj.MjAPI;
import buildcraft.api.transport.pipe.IPipeFlowRenderer;

import buildcraft.lib.client.model.ModelUtil;
import buildcraft.lib.client.model.ModelUtil.UvFaceData;
import buildcraft.lib.client.model.MutableQuad;
import buildcraft.lib.client.sprite.SpriteHolderRegistry.SpriteHolder;
import buildcraft.lib.misc.MathUtil;
import buildcraft.lib.misc.VecUtil;

import buildcraft.transport.BCTransportSprites;
import buildcraft.transport.pipe.flow.PipeFlowPower;
import buildcraft.transport.pipe.flow.PipeFlowPower.Section;

@Environment(EnvType.CLIENT)
public enum PipeFlowRendererPower implements IPipeFlowRenderer<PipeFlowPower> {
    INSTANCE;

    @Override
    public void render(PipeFlowPower flow, MatrixStack matrices, VertexConsumer vc,
            int light, float partialTicks) {
        double[] power = new double[6];
        boolean[] connected = new boolean[6];
        double[] offsets = new double[6];
        for (Direction side : Direction.values()) {
            Section s = flow.getSection(side);
            int i = side.ordinal();
            power[i] = s.displayPower / (double) MjAPI.MJ;
            connected[i] = flow.pipe.isConnected(side);
            offsets[i] = computeOffset(s.clientDisplayFlowLast, s.clientDisplayFlow, partialTicks);
        }

        Vec3d offsetLast = flow.clientDisplayFlowCentreLast;
        Vec3d offsetThis = flow.clientDisplayFlowCentre;
        double offsetX = computeOffset(offsetLast.x, offsetThis.x, partialTicks);
        double offsetY = computeOffset(offsetLast.y, offsetThis.y, partialTicks);
        double offsetZ = computeOffset(offsetLast.z, offsetThis.z, partialTicks);
        renderSections(power, connected, offsets, new Vec3d(offsetX, offsetY, offsetZ), matrices, vc, light);
    }

    /** Shared with GPU checks; texture repeats are clipped within their atlas sprite. */
    public static void renderSections(double[] power, boolean[] connected, double[] offsets, Vec3d centerOffset,
            MatrixStack matrices, VertexConsumer vc, int light) {
        double center = 0;
        for (double value : power) if (Double.isFinite(value)) center = Math.max(center, Math.min(1, value));
        if (center <= 0) return;
        for (Direction side : Direction.values()) if (connected[side.ordinal()]) {
            double value = power[side.ordinal()];
            if (Double.isFinite(value) && value > 0) renderSidePower(side, Math.min(1, value), center,
                offsets[side.ordinal()], matrices, vc, light);
        }
        renderCentrePower(center, centerOffset.x, centerOffset.y, centerOffset.z, matrices, vc, light);
    }

    private static double computeOffset(double tick0, double tick1, float partialTicks) {
        if (tick0 + 8 < tick1) tick0 += 16;
        else if (tick1 + 8 < tick0) tick1 += 16;
        double offset = MathUtil.interp(partialTicks, tick0, tick1);
        if (offset >= 16) offset -= 16;
        return offset;
    }

    private static void renderSidePower(Direction side, double power, double centrePower,
            double offset, MatrixStack matrices, VertexConsumer vc, int light) {
        if (power < 0) return;
        double radius = 0.248 * power;
        if (radius >= 0.248) radius = 0.248;
        boolean overload = false;

        SpriteHolder spriteHolder = overload ? BCTransportSprites.POWER_FLOW_OVERLOAD
                                              : BCTransportSprites.POWER_FLOW;
        if (spriteHolder == null) return;
        Sprite sprite = spriteHolder.getSprite();
        if (sprite == null) return;

        double centreRadius = 0.252 - (0.248 * centrePower);
        Vec3d centre = VecUtil.offset(VecUtil.VEC_HALF, side, 0.25 + 0.125 - centreRadius / 2);
        Vec3d radiusV = new Vec3d(radius, radius, radius);
        radiusV = VecUtil.replaceValue(radiusV, side.getAxis(), 0.125 + centreRadius / 2);

        Vector3f centreF = VecUtil.convertFloat(centre);
        Vector3f radiusF = VecUtil.convertFloat(radiusV);

        UvFaceData uvs = new UvFaceData();
        for (Direction face : Direction.values()) {
            if (face == side.getOpposite()) continue;
            Box box = new Box(
                centre.subtract(radiusV).multiply(0.5),
                centre.add(radiusV).multiply(0.5)
            ).offset(VecUtil.offset(Vec3d.ZERO, side,
                offset * side.getDirection().offset() / 32.0));
            ModelUtil.mapBoxToUvs(box, face, uvs);

            MutableQuad quad = ModelUtil.createFace(face, centreF, radiusF, uvs);
            renderWrapped(quad, face, sprite, matrices, vc);
        }
    }

    private static void renderCentrePower(double power, double offsetX, double offsetY, double offsetZ,
            MatrixStack matrices, VertexConsumer vc, int light) {
        float radius = 0.248f * (float) power;
        if (radius > 0.248f) radius = 0.248f;

        SpriteHolder spriteHolder = BCTransportSprites.POWER_FLOW;
        if (spriteHolder == null) return;
        Sprite sprite = spriteHolder.getSprite();
        if (sprite == null) return;

        Vector3f centre = new Vector3f(0.5f, 0.5f, 0.5f);
        Vector3f radiusP = new Vector3f(radius, radius, radius);

        Vec3d centreVec = new Vec3d(0.5, 0.5, 0.5);
        Vec3d radiusVec = new Vec3d(radius, radius, radius);
        UvFaceData uvs = new UvFaceData();
        for (Direction face : Direction.values()) {
            Box box = new Box(
                centreVec.subtract(radiusVec).multiply(0.5),
                centreVec.add(radiusVec).multiply(0.5)
            ).offset(offsetX / 32.0, offsetY / 32.0, offsetZ / 32.0);
            ModelUtil.mapBoxToUvs(box, face, uvs);

            MutableQuad quad = ModelUtil.createFace(face, centre, radiusP, uvs);
            renderWrapped(quad, face, sprite, matrices, vc);
        }
    }

    private static void renderWrapped(MutableQuad quad, Direction face, Sprite sprite, MatrixStack matrices, VertexConsumer vc) {
        var vertices = new buildcraft.lib.client.model.MutableVertex[]{quad.vertex_0, quad.vertex_1, quad.vertex_2, quad.vertex_3};
        float minU=Float.POSITIVE_INFINITY,minV=Float.POSITIVE_INFINITY,maxU=Float.NEGATIVE_INFINITY,maxV=Float.NEGATIVE_INFINITY;
        for (var v:vertices) { minU=Math.min(minU,v.tex_u);maxU=Math.max(maxU,v.tex_u);minV=Math.min(minV,v.tex_v);maxV=Math.max(maxV,v.tex_v); }
        var origin=vertices[0]; var along=vertices[1]; var across=vertices[3];
        double du1=along.tex_u-origin.tex_u,dv1=along.tex_v-origin.tex_v;
        double du3=across.tex_u-origin.tex_u,dv3=across.tex_v-origin.tex_v;
        double determinant=du1*dv3-du3*dv1;
        if (Math.abs(determinant)<1e-15) return;
        for (int tileU=(int)Math.floor(minU);tileU<Math.ceil(maxU);tileU++) {
            for (int tileV=(int)Math.floor(minV);tileV<Math.ceil(maxV);tileV++) {
                double u0=Math.max(minU,tileU),u1=Math.min(maxU,tileU+1);
                double v0=Math.max(minV,tileV),v1=Math.min(maxV,tileV+1);
                if (u1<=u0 || v1<=v0) continue;
                for (var vertex:vertices) {
                    double u=vertex.tex_u==minU?u0:u1,v=vertex.tex_v==minV?v0:v1;
                    double a=((u-origin.tex_u)*dv3-(v-origin.tex_v)*du3)/determinant;
                    double b=((v-origin.tex_v)*du1-(u-origin.tex_u)*dv1)/determinant;
                    float x=(float)(origin.position_x+a*(along.position_x-origin.position_x)+b*(across.position_x-origin.position_x));
                    float y=(float)(origin.position_y+a*(along.position_y-origin.position_y)+b*(across.position_y-origin.position_y));
                    float z=(float)(origin.position_z+a*(along.position_z-origin.position_z)+b*(across.position_z-origin.position_z));
                    vc.vertex(matrices.peek().getPositionMatrix(),x,y,z).color(255,255,255,255)
                        .texture(sprite.getFrameU((u-tileU)*16),sprite.getFrameV((v-tileV)*16))
                        .overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV)
                        .light(net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE)
                        .normal(matrices.peek().getNormalMatrix(),face.getOffsetX(),face.getOffsetY(),face.getOffsetZ()).next();
                }
            }
        }
    }
}
