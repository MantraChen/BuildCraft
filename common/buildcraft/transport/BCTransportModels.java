/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 *
 * Ported to Fabric 1.20.1 by R.Chen (https://github.com/MantraChen).
 */
package buildcraft.transport;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

// Native pipe model baking. Pluggable models are restored separately.
@Environment(EnvType.CLIENT)
public class BCTransportModels {
    public static final buildcraft.transport.client.model.TransportPlugModel PLUG_MODEL=new buildcraft.transport.client.model.TransportPlugModel();
    public static void preInit() {} // STUB Phase 5
    public static void init() {
        for(var item:java.util.List.of(BCTransportItems.plugBlocker,BCTransportItems.plugPowerAdaptor))
            net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry.INSTANCE.register(item,(stack,mode,matrices,consumers,light,overlay)->{
                var consumer=consumers.getBuffer(net.minecraft.client.render.RenderLayer.getEntityCutout(net.minecraft.screen.PlayerScreenHandler.BLOCK_ATLAS_TEXTURE));
                matrices.push();matrices.translate(5/16.0,0,0);
                for(var quad:PLUG_MODEL.getQuads(item.powerAdaptor,net.minecraft.util.math.Direction.WEST))quad.render(consumer,matrices.peek(),light,overlay);
                matrices.pop();
            });
        buildcraft.transport.client.PipeRegistryClient.INSTANCE.registerRenderer(buildcraft.transport.plug.PluggableBlocker.class,(plug,matrices,consumer,light,delta)->{
            for(var quad:PLUG_MODEL.getQuads(false,plug.side))quad.render(consumer,matrices.peek(),light,net.minecraft.client.render.OverlayTexture.DEFAULT_UV);
        });
        buildcraft.transport.client.PipeRegistryClient.INSTANCE.registerRenderer(buildcraft.transport.plug.PluggablePowerAdaptor.class,(plug,matrices,consumer,light,delta)->{
            for(var quad:PLUG_MODEL.getQuads(true,plug.side))quad.render(consumer,matrices.peek(),light,net.minecraft.client.render.OverlayTexture.DEFAULT_UV);
        });
        net.minecraft.client.item.ModelPredicateProviderRegistry.register(BCTransportItems.wire,
            new net.minecraft.util.Identifier("buildcrafttransport:wire_colour"),
            (stack, world, entity, seed) -> buildcraft.transport.item.ItemWire.getColour(stack).getId() / 16f);
        net.fabricmc.fabric.api.event.client.player.ClientPickBlockGatherCallback.EVENT.register((player, result) -> {
            if (result instanceof net.minecraft.util.hit.BlockHitResult hit && hit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK
                && player.getWorld().getBlockEntity(hit.getBlockPos()) instanceof buildcraft.transport.tile.TilePipeHolder tile) {
                var trace = BCTransportBlocks.pipeHolder.rayTrace(player.getWorld(), hit.getBlockPos(), player);
                if (trace != null) {
                    if(trace.subHit>6 && trace.subHit<=12){
                        var plug=tile.getPluggable(buildcraft.transport.block.BlockPipeHolder.getPartSideHit(trace));
                        if(plug instanceof buildcraft.transport.plug.PluggableBlocker || plug instanceof buildcraft.transport.plug.PluggablePowerAdaptor)return plug.getPickStack();
                    }
                    var part = buildcraft.transport.block.BlockPipeHolder.getWirePartHit(trace);
                    var between = buildcraft.transport.block.BlockPipeHolder.getWireBetweenHit(trace);
                    if (part == null && between != null) part = between.parts[0];
                    if (part != null && tile.wireManager.getColorOfPart(part) != null)
                        return BCTransportItems.wire.stack(tile.wireManager.getColorOfPart(part));
                }
            }
            return net.minecraft.item.ItemStack.EMPTY;
        });
        net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin.register(plugin -> plugin.modifyModelAfterBake().register((model, context) -> {
            if (model == null || !(context.id() instanceof net.minecraft.client.util.ModelIdentifier id)) return model;
            var identifier = new net.minecraft.util.Identifier(id.getNamespace(), id.getPath());
            if (id.getVariant().equals("inventory") && net.minecraft.registry.Registries.ITEM.get(identifier) instanceof buildcraft.transport.item.ItemPipeHolder item)
                return new buildcraft.transport.client.model.ModelPipe(model, item.getDefinition(), context.textureGetter());
            if (identifier.equals(net.minecraft.registry.Registries.BLOCK.getId(BCTransportBlocks.pipeHolder)))
                return new buildcraft.transport.client.model.ModelPipe(model, null, context.textureGetter());
            return model;
        }));
    }
}
