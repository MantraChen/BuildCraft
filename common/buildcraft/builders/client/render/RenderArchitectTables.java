/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.builders.client.render;

import java.util.Comparator;
import java.util.LinkedHashSet;
import javax.vecmath.Point3f;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import buildcraft.builders.client.ClientArchitectTables;
import buildcraft.lib.client.model.ModelUtil;
import buildcraft.lib.client.render.DetachedRenderer;

/** Translucent, fading scan surfaces clipped to their architect's actual bounds. */
public enum RenderArchitectTables implements DetachedRenderer.IDetachedRenderer {
    INSTANCE;
    private static final Identifier TEXTURE = new Identifier("buildcraftbuilders", "textures/blocks/scan.png");
    @Override public void render(float delta) {
        var context = DetachedRenderer.context();
        Vec3d camera = context.camera().getPos();
        var consumers = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
        var vertices = consumers.getBuffer(RenderLayer.getEntityTranslucent(TEXTURE));
        var positions = ClientArchitectTables.SCANNED_BLOCKS.keySet().stream()
            .filter(pos -> Vec3d.ofCenter(pos).squaredDistanceTo(camera) <= 256*256)
            .sorted(Comparator.<BlockPos>comparingDouble(pos -> Vec3d.ofCenter(pos).squaredDistanceTo(camera)).reversed()).toList();
        for (BlockPos pos : positions) {
            Box block = new Box(pos);
            var clipped = new LinkedHashSet<Box>();
            for (Box box : ClientArchitectTables.BOXES.keySet()) if (box.intersects(block)) clipped.add(box.intersection(block));
            int alpha = MathHelper.clamp(ClientArchitectTables.SCANNED_BLOCKS.get(pos)*50/ClientArchitectTables.START_SCANNED_BLOCK_VALUE, 0, 50);
            for (Box area : clipped) {
                // A slight offset keeps the scan overlay from fighting the scanned block's surface.
                Box bounds = area.expand(.002);
                Vec3d center = bounds.getCenter().subtract(camera);
                for (Direction face : Direction.values()) {
                    var quad = ModelUtil.createFace(face, new Point3f((float)center.x, (float)center.y, (float)center.z),
                        new Point3f((float)(bounds.maxX-bounds.minX)/2, (float)(bounds.maxY-bounds.minY)/2, (float)(bounds.maxZ-bounds.minZ)/2),
                        new ModelUtil.UvFaceData(0, 0, 1, 1));
                    quad.colouri(255, 255, 255, alpha);
                    quad.render(vertices, context.matrixStack().peek(), LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);
                }
            }
        }
    }
}
