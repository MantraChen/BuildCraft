/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.silicon.client;

import buildcraft.silicon.*;
import buildcraft.silicon.item.ItemPluggableGate;
import buildcraft.silicon.plug.PluggableGate;
import buildcraft.silicon.gui.GuiGate;
import buildcraft.transport.client.PipeRegistryClient;
import net.minecraft.client.render.*;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.math.Direction;

public final class BCSiliconClient {
    public static final GateModel MODEL = new GateModel();
    public static void register() {
        net.fabricmc.fabric.api.event.client.player.ClientPickBlockGatherCallback.EVENT.register((player, result) -> {
            if (result instanceof net.minecraft.util.hit.BlockHitResult hit && hit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK
                && player.getWorld().getBlockEntity(hit.getBlockPos()) instanceof buildcraft.transport.tile.TilePipeHolder tile) {
                var trace = buildcraft.transport.BCTransportBlocks.pipeHolder.rayTrace(player.getWorld(), hit.getBlockPos(), player);
                if (trace != null && trace.subHit > 6 && trace.subHit <= 12) {
                    var plug = tile.getPluggable(buildcraft.transport.block.BlockPipeHolder.getPartSideHit(trace));
                    if (plug instanceof PluggableGate) return plug.getPickStack();
                }
            }
            return net.minecraft.item.ItemStack.EMPTY;
        });
        net.minecraft.client.gui.screen.ingame.HandledScreens.register(BCSiliconScreens.GATE, GuiGate::new);
        PipeRegistryClient.INSTANCE.registerRenderer(PluggableGate.class, (gate, matrices, consumer, light, delta) -> {
            for (var quad : MODEL.getQuads(gate.logic.variant, gate.side, gate.logic.isOn)) quad.render(consumer, matrices.peek(), light, OverlayTexture.DEFAULT_UV);
        });
        net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry.INSTANCE.register(BCSiliconItems.plugGate,
            (stack, mode, matrices, consumers, light, overlay) -> {
                var consumer = consumers.getBuffer(RenderLayer.getEntityCutout(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE));
                matrices.push();
                matrices.translate(5/16.0, 0, 0);
                for (var quad : MODEL.getQuads(ItemPluggableGate.getVariant(stack), Direction.WEST, false)) quad.render(consumer, matrices.peek(), light, overlay);
                matrices.pop();
            });
    }
}
