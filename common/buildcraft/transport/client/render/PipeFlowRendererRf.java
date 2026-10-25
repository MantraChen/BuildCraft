/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.transport.client.render;

import net.fabricmc.api.*;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.*;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.transport.pipe.IPipeFlowRenderer;
import buildcraft.transport.BCTransportSprites;
import buildcraft.transport.pipe.flow.PipeFlowRedstoneFlux;
import static buildcraft.transport.client.render.PipeFlowRendererPower.computeOffset;

@Environment(EnvType.CLIENT)
public enum PipeFlowRendererRf implements IPipeFlowRenderer<PipeFlowRedstoneFlux> {
    INSTANCE;
    @Override public void render(PipeFlowRedstoneFlux flow, MatrixStack matrices, VertexConsumer vc, int light, float partialTicks) {
        double[] power = new double[6], offsets = new double[6];
        boolean[] connected = new boolean[6];
        for (var side : Direction.values()) {
            var section = flow.getSection(side); int i = side.ordinal();
            power[i] = section.displayPower / (double) MjAPI.MJ;
            connected[i] = flow.pipe.isConnected(side);
            offsets[i] = computeOffset(section.clientDisplayFlowLast, section.clientDisplayFlow, partialTicks);
        }
        var last = flow.clientDisplayFlowCentreLast; var now = flow.clientDisplayFlowCentre;
        var center = new Vec3d(computeOffset(last.x,now.x,partialTicks),computeOffset(last.y,now.y,partialTicks),computeOffset(last.z,now.z,partialTicks));
        var sprite = BCTransportSprites.POWER_FLOW_RF;
        if (sprite != null) PipeFlowRendererPower.renderSections(power,connected,offsets,center,matrices,vc,light,sprite.getSprite());
    }
}
