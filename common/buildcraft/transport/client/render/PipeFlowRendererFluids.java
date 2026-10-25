/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 *
 * Ported to Fabric 1.20.1 by R.Chen (https://github.com/MantraChen).
 */
package buildcraft.transport.client.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;

import buildcraft.api.core.EnumPipePart;
import buildcraft.lib.client.render.fluid.FluidRenderer;
import buildcraft.lib.client.render.fluid.FluidRenderer.TankSize;
import buildcraft.lib.client.render.fluid.FluidSpriteType;
import buildcraft.lib.compat.FluidStackBC;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import buildcraft.transport.pipe.flow.PipeFlowFluids;

@Environment(EnvType.CLIENT)
public final class PipeFlowRendererFluids {
    private PipeFlowRendererFluids() {}
    public static void render(PipeFlowFluids flow, MatrixStack matrices, VertexConsumerProvider consumers,
            int light, float partialTicks) {
        var fluid = flow.getFluidStackForRender();
        if (fluid == null || fluid.isBlank()) return;
        double[] lengths = new double[6];
        for (var side : Direction.values()) lengths[side.ordinal()] = ((buildcraft.transport.pipe.Pipe)flow.pipe).getConnectedDist(side);
        renderSections(fluid, flow.getAmountsForRender(partialTicks), lengths, flow.capacity, matrices, consumers, light);
    }

    /** Shares the actual renderer with GPU geometry tests; amounts/capacity use the same unit. */
    public static void renderSections(FluidVariant fluid, double[] amounts, double[] lengths, long capacity,
            MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        if (fluid == null || fluid.isBlank() || capacity <= 0) return;
        boolean gas = FluidVariantAttributes.isLighterThanAir(fluid), horizontal = false;
        boolean vertical = lengths[(gas ? Direction.DOWN : Direction.UP).ordinal()] > 0;
        int emission = fluid.getFluid().getDefaultState().getBlockState().getLuminance();
        int brightness = light & 0xffff0000 | Math.max(light & 0xffff, emission * 16);
        var stack = FluidStackBC.of(fluid,1);
        for (var side : Direction.values()) {
            double amount = amounts[side.ordinal()], length = lengths[side.ordinal()];
            if (length <= 0 || amount <= 0) continue;
            double fraction = Math.max(0, Math.min(1, amount / capacity));
            var center = new Vec3d(0.5,0.5,0.5).add(Vec3d.of(side.getVector()).multiply(0.245+length/2));
            double axial = 0.005+length/2, width = side.getAxis()==Direction.Axis.Y ? 0.24*Math.sqrt(fraction) : 0.24;
            var radius = new Vec3d(side.getAxis()==Direction.Axis.X ? axial : width,
                side.getAxis()==Direction.Axis.Y ? axial : width, side.getAxis()==Direction.Axis.Z ? axial : width);
            var bounds = new TankSize(center.subtract(radius),center.add(radius));
            if (side.getAxis()!=Direction.Axis.Y) { horizontal=true; bounds=FluidRenderer.filledSize(bounds,(long)amount,capacity,gas); }
            FluidRenderer.renderFluid(FluidSpriteType.FLOWING,stack,1,1,bounds,matrices,consumers,brightness);
        }
        double amount = amounts[EnumPipePart.CENTER.getIndex()];
        if (amount <= 0) return;
        double fraction = Math.max(0,Math.min(1,amount/capacity)), horizontalTop = 0.26;
        if (horizontal || !vertical) {
            var bounds = FluidRenderer.filledSize(new TankSize(new Vec3d(0.26,0.26,0.26),new Vec3d(0.74,0.74,0.74)),(long)amount,capacity,gas);
            FluidRenderer.renderFluid(FluidSpriteType.FLOWING,stack,1,1,bounds,matrices,consumers,brightness);
            horizontalTop += 0.48*fraction;
        }
        if (vertical && horizontalTop < 0.74) {
            double radius = 0.24*Math.sqrt(fraction);
            var bounds = new TankSize(new Vec3d(0.5-radius,gas ? 0.26 : horizontalTop,0.5-radius),
                new Vec3d(0.5+radius,gas ? 1-horizontalTop : 0.74,0.5+radius));
            FluidRenderer.renderFluid(FluidSpriteType.FLOWING,stack,1,1,bounds,matrices,consumers,brightness);
        }
    }
}
