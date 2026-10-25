/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team.
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 */
package buildcraft.energy;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;
import net.fabricmc.fabric.api.client.render.fluid.v1.SimpleFluidRenderHandler;
import net.minecraft.client.render.RenderLayer;
import buildcraft.energy.client.sprite.AtlasSpriteFluid;
import buildcraft.energy.client.render.GasFluidRenderHandler;
import buildcraft.lib.fluid.BCFluid;

@Environment(EnvType.CLIENT)
public final class BCEnergySprites {
    public static void fmlPreInit() {
        AtlasSpriteFluid.register();
        for (BCFluid fluid : BCEnergyFluids.allFluids) {
            FluidRenderHandlerRegistry.INSTANCE.register(fluid, fluid.getFlowing(),
                fluid.isGaseous() ? new GasFluidRenderHandler(fluid.getStillTexture(), fluid.getFlowTexture())
                    : new SimpleFluidRenderHandler(fluid.getStillTexture(), fluid.getFlowTexture()));
            BlockRenderLayerMap.INSTANCE.putFluids(RenderLayer.getTranslucent(), fluid, fluid.getFlowing());
        }
    }
}
