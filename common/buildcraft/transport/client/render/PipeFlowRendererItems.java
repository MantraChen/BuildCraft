/* Copyright (c) 2017 SpaceToad and the BuildCraft team. MPL-2.0. */
package buildcraft.transport.client.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import buildcraft.transport.pipe.flow.PipeFlowItems;

/** Native item renderer retains the stack's own model, tint, enchantment and NBT. */
@Environment(EnvType.CLIENT)
public final class PipeFlowRendererItems {
    private PipeFlowRendererItems() {}
    public static void render(PipeFlowItems flow, MatrixStack matrices, VertexConsumerProvider consumers,
            int light, int overlay, float partialTicks) {
        var world = flow.pipe.getHolder().getPipeWorld();
        var renderer = MinecraftClient.getInstance().getItemRenderer();
        for (var item : flow.getAllItemsForRender()) {
            var stack = item.clientItemLink.get();
            if (stack.isEmpty() || !item.isVisible()) continue;
            stack = stack.copyWithCount(item.stackSize);
            var point = item.getRenderPosition(BlockPos.ORIGIN, world.getTime(), partialTicks, flow);
            matrices.push();
            matrices.translate(point.x, point.y, point.z);
            matrices.scale(0.65f, 0.65f, 0.65f);
            renderer.renderItem(stack, ModelTransformationMode.GROUND, light, overlay, matrices, consumers, world, 0);
            matrices.pop();
        }
    }
}
