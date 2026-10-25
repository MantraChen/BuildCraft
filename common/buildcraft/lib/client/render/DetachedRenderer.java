/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 *
 * Ported to Fabric 1.20.1 by R.Chen (https://github.com/MantraChen).
 */
package buildcraft.lib.client.render;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

@Environment(EnvType.CLIENT)
public class DetachedRenderer {

    private static final List<IDetachedRenderer> renderers = new ArrayList<>();
    private static boolean registered;
    private static WorldRenderContext currentContext;
    public static WorldRenderContext context() {
        return java.util.Objects.requireNonNull(currentContext, "No active detached render frame");
    }

    /**
     * Register a renderer to be called each frame at the end of world rendering
     * (equivalent to Forge's {@code RenderWorldLastEvent}).
     */
    public static void register(IDetachedRenderer renderer) {
        if (!renderers.contains(renderer)) renderers.add(renderer);
    }

    /**
     * Wire this class into the Fabric event bus.  Call once from
     * {@code BCLibClientInitializer.onInitializeClient()}.
     */
    public static void registerEvents() {
        if (registered) return;
        registered = true;
        WorldRenderEvents.LAST.register(DetachedRenderer::onWorldRenderLast);
    }

    private static void onWorldRenderLast(WorldRenderContext context) {
        WorldRenderContext previous = currentContext;
        currentContext = context;
        var consumers = net.minecraft.client.MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
        try (var frame = buildcraft.lib.client.render.laser.LaserRenderer_BC8.beginFrame(
                context.matrixStack(), consumers, context.camera().getPos())) {
            for (IDetachedRenderer r : renderers) r.render(context.tickDelta());
        } catch (RuntimeException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalStateException("Could not close world render frame", failure);
        } finally {
            currentContext = previous;
            consumers.draw();
        }
    }

    @Environment(EnvType.CLIENT)
    public interface IDetachedRenderer {
        /** Called each frame at the end of world rendering. */
        void render(float partialTicks);
    }
}
