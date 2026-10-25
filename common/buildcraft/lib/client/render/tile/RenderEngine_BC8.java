/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team.
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 */
package buildcraft.lib.client.render.tile;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.screen.PlayerScreenHandler;
import buildcraft.lib.client.model.MutableQuad;
import buildcraft.lib.engine.TileEngineBase_BC8;

public abstract class RenderEngine_BC8<T extends TileEngineBase_BC8> implements BlockEntityRenderer<T> {
    @Override
    public void render(T engine, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers,
            int light, int overlay) {
        var buffer = consumers.getBuffer(RenderLayer.getEntityCutout(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE));
        for (MutableQuad quad : getEngineModel(engine, tickDelta)) {
            quad.render(buffer, matrices.peek(), light, overlay);
        }
    }

    protected abstract MutableQuad[] getEngineModel(T engine, float partialTicks);
}
