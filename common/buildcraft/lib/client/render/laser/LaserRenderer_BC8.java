/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.lib.client.render.laser;

import java.util.IdentityHashMap;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;

/** Native vertex consumers; no global GL state, display lists or world-coordinate float caches. */
@Environment(EnvType.CLIENT)
public final class LaserRenderer_BC8 {
    public static final VertexFormat FORMAT_LESS = VertexFormats.POSITION_COLOR_TEXTURE_LIGHT_NORMAL;
    public static final VertexFormat FORMAT_ALL = FORMAT_LESS;
    private static final Map<LaserData_BC8.LaserType, CompiledLaserType> TYPES = new IdentityHashMap<>();
    private static Frame frame;
    private record Frame(MatrixStack matrices, VertexConsumerProvider consumers, Vec3d origin) {}
    public static void clearModels() { TYPES.clear(); }
    public static AutoCloseable beginFrame(MatrixStack matrices, VertexConsumerProvider consumers, Vec3d origin) {
        Frame previous = frame;
        frame = new Frame(matrices, consumers, origin);
        return () -> frame = previous;
    }
    public static int computeLightmap(double x, double y, double z, int minimum) {
        int block = Math.max(0, Math.min(15, minimum));
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) return LightmapTextureManager.pack(block, 0);
        BlockPos pos = BlockPos.ofFloored(x, y, z);
        return LightmapTextureManager.pack(Math.max(block, client.world.getLightLevel(LightType.BLOCK, pos)),
            client.world.getLightLevel(LightType.SKY, pos));
    }
    public static void bake(LaserData_BC8 data, ILaserRenderer renderer) {
        if (data.laserType == null) throw new IllegalArgumentException("Laser has no texture definition");
        TYPES.computeIfAbsent(data.laserType, CompiledLaserType::new)
            .bakeFor(new LaserContext(renderer, data, data.enableDiffuse, data.doubleFace));
    }
    public static void render(LaserData_BC8 data, MatrixStack matrices, VertexConsumerProvider consumers, Vec3d origin, int overlay) {
        VertexConsumer vertices = consumers.getBuffer(RenderLayer.getEntityCutout(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE));
        var transform = matrices.peek();
        bake(data, (x, y, z, u, v, light, nx, ny, nz, colour) -> vertices
            .vertex(transform.getPositionMatrix(), (float)(x-origin.x), (float)(y-origin.y), (float)(z-origin.z))
            .color(colour, colour, colour, 1).texture((float)u, (float)v).overlay(overlay).light(light)
            .normal(transform.getNormalMatrix(), nx, ny, nz).next());
    }
    /** World-space callers are scoped by DetachedRenderer's native world-render callback. */
    public static void renderLaserStatic(LaserData_BC8 data) {
        if (frame == null) throw new IllegalStateException("World laser rendered outside its native frame");
        render(data, frame.matrices, frame.consumers, frame.origin, OverlayTexture.DEFAULT_UV);
    }
    public static void renderLaserDynamic(LaserData_BC8 data, BufferBuilder buffer) {
        bake(data, (x, y, z, u, v, light, nx, ny, nz, colour) -> buffer.vertex(x, y, z)
            .color(colour, colour, colour, 1).texture((float)u, (float)v).light(light).normal(nx, ny, nz).next());
    }
}
