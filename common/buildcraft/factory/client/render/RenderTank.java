/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.factory.client.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;

import buildcraft.factory.tile.TileTank;

@Environment(EnvType.CLIENT)
public class RenderTank implements BlockEntityRenderer<TileTank> {

    public RenderTank(BlockEntityRendererFactory.Context ctx) {
    }

    @Override
    public void render(TileTank be, float tickDelta, MatrixStack matrices,
            VertexConsumerProvider vcp, int light, int overlay) {
        var fluid = be.tank.getFluidForRender();
        if (fluid == null || fluid.isEmpty()) return;
        int emission = fluid.getFluid().getDefaultState().getBlockState().getLuminance();
        int fluidLight = Math.max(light & 0xffff, emission << 4) | (light & 0xffff0000);
        buildcraft.lib.client.render.fluid.FluidRenderer.renderFluid(
            buildcraft.lib.client.render.fluid.FluidSpriteType.STILL, fluid, fluid.amount,
            be.tank.getStorage().getCapacity(),
            new buildcraft.lib.client.render.fluid.FluidRenderer.TankSize(3, 0.01, 3, 13, 15.99, 13),
            matrices, vcp, fluidLight);
    }
}
