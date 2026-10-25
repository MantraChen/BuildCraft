/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.client.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;

import buildcraft.builders.snapshot.ITileForSnapshotBuilder;
import buildcraft.builders.BCBuildersSprites;
import buildcraft.core.client.BuildCraftLaserManager;
import buildcraft.lib.client.model.ModelUtil;
import buildcraft.lib.client.render.laser.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.util.math.*;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public class RenderSnapshotBuilder {

    public static <T extends ITileForSnapshotBuilder> void render(T tile, double x, double y, double z,
            float partialTicks, MatrixStack matrices, VertexConsumerProvider vcp, int light) {
        if (tile.getBuilder()==null) return;
        var work = tile.getBuilder().clientWork();
        if (work.isEmpty()) return;
        Vec3d origin = Vec3d.of(tile.getBuilderPos());
        Vec3d robot = Vec3d.ofCenter(work.get(0).pos()).add(0, 1.25, 0);
        var sprite = BCBuildersSprites.ROBOT.getSprite();
        matrices.push();
        matrices.translate(robot.x-origin.x, robot.y-origin.y, robot.z-origin.z);
        var consumer = vcp.getBuffer(RenderLayer.getEntityCutout(net.minecraft.client.texture.SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE));
        for (var face : Direction.values()) {
            float u = face.ordinal()/8f;
            ModelUtil.createFace(face, new Vector3f(), new Vector3f(.25f), new ModelUtil.UvFaceData(u,0,u+.125f,.125f))
                .texFromSprite(sprite).render(consumer,matrices.peek(),light,OverlayTexture.DEFAULT_UV);
        }
        matrices.pop();
        for (var task : work) {
            Vec3d target = Vec3d.ofCenter(task.pos());
            if (task.breaking()) {
                var type = BuildCraftLaserManager.POWERS[Math.min(3,(int)(task.progress()*4))];
                LaserRenderer_BC8.render(new LaserData_BC8(type,robot.add(0,-.27,0),target,1/16.0,true,true,0),
                    matrices,vcp,origin,OverlayTexture.DEFAULT_UV);
            } else if (!task.item().isEmpty()) {
                Vec3d item = Vec3d.ofCenter(tile.getBuilderPos()).add(0,.6,0).lerp(target,task.progress());
                matrices.push(); matrices.translate(item.x-origin.x,item.y-origin.y,item.z-origin.z);
                MinecraftClient.getInstance().getItemRenderer().renderItem(task.item(),ModelTransformationMode.GROUND,
                    light,OverlayTexture.DEFAULT_UV,matrices,vcp,tile.getWorldBC(),0);
                matrices.pop();
            }
        }
    }
}
