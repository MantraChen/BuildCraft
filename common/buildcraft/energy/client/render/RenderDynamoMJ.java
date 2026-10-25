/* Copyright (c) 2017 SpaceToad and the BuildCraft team. MPL-2.0. */
package buildcraft.energy.client.render;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.screen.PlayerScreenHandler;
import buildcraft.energy.BCEnergyModels;
import buildcraft.energy.tile.TileDynamoMJ;

public final class RenderDynamoMJ implements BlockEntityRenderer<TileDynamoMJ> {
    public static final RenderDynamoMJ INSTANCE = new RenderDynamoMJ();
    @Override public void render(TileDynamoMJ tile, float delta, MatrixStack matrices,
            VertexConsumerProvider consumers, int light, int overlay) {
        var buffer = consumers.getBuffer(RenderLayer.getEntityCutout(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE));
        for (var quad : BCEnergyModels.getMjDynamoQuads(tile, delta)) quad.render(buffer, matrices.peek(), light, overlay);
    }
}
