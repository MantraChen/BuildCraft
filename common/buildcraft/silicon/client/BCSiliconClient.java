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
    public static final ControlPlugModel CONTROL_MODEL = new ControlPlugModel();
    public static final LensModel LENS_MODEL = new LensModel();
    public static final FacadeModel FACADE_MODEL = new FacadeModel();
    public static void register() {
        net.minecraft.client.item.ModelPredicateProviderRegistry.register(BCSiliconItems.gateCopier,new net.minecraft.util.Identifier("buildcraftsilicon","copied"),
            (stack,world,entity,seed)->buildcraft.silicon.item.ItemGateCopier.hasCopiedGateData(stack)?1:0);
        PipeRegistryClient.INSTANCE.registerLayeredRenderer(buildcraft.silicon.plug.PluggableFacade.class,(plug,matrices,consumers,light,overlay,delta)->
            FACADE_MODEL.render(plug.active().state,plug.side,plug.isHollow(),plug.holder.getPipeWorld(),plug.holder.getPipePos(),matrices,consumers,light,overlay));
        net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry.INSTANCE.register(BCSiliconItems.plugFacade,buildcraft.silicon.client.model.plug.ModelFacadeItem.INSTANCE);
        net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry.ITEM.register(FacadeItemColours.INSTANCE,BCSiliconItems.plugFacade);
        PipeRegistryClient.INSTANCE.registerLayeredRenderer(buildcraft.silicon.plug.PluggableLens.class,(plug,matrices,consumers,light,overlay,delta)->
            LENS_MODEL.render(plug.side,plug.colour,plug.isFilter,matrices,consumers,light,overlay));
        net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry.INSTANCE.register(BCSiliconItems.plugLens,(stack,mode,matrices,consumers,light,overlay)->{
            var data=buildcraft.silicon.item.ItemPluggableLens.getData(stack);
            matrices.push();matrices.translate(7/16.0,0,0);
            LENS_MODEL.render(Direction.WEST,data.colour(),data.isFilter(),matrices,consumers,light,overlay);matrices.pop();
        });
        net.minecraft.client.gui.screen.ingame.HandledScreens.<buildcraft.lib.gui.ContainerBC_Neptune,buildcraft.silicon.gui.GuiIntegrationTable>register(BCSiliconScreens.INTEGRATION,
            (handler,inventory,title)->new buildcraft.silicon.gui.GuiIntegrationTable((buildcraft.silicon.container.ContainerIntegrationTable)handler,inventory,title));
        net.minecraft.client.gui.screen.ingame.HandledScreens.<buildcraft.lib.gui.ContainerBC_Neptune,buildcraft.silicon.gui.GuiAdvancedCraftingTable>register(BCSiliconScreens.ADVANCED,
            (handler,inventory,title)->new buildcraft.silicon.gui.GuiAdvancedCraftingTable((buildcraft.silicon.container.ContainerAdvancedCraftingTable)handler,inventory,title));
        net.minecraft.client.gui.screen.ingame.HandledScreens.<buildcraft.lib.gui.ContainerBC_Neptune,buildcraft.silicon.gui.GuiAssemblyTable>register(BCSiliconScreens.ASSEMBLY,
            (handler,inventory,title)->new buildcraft.silicon.gui.GuiAssemblyTable((buildcraft.silicon.container.ContainerAssemblyTable)handler,inventory,title));
        net.minecraft.client.render.block.entity.BlockEntityRendererFactories.register(buildcraft.lib.registry.BlockEntityRegistry.type(buildcraft.silicon.tile.TileLaser.class),buildcraft.silicon.client.render.RenderLaser::new);
        net.minecraft.client.item.ModelPredicateProviderRegistry.register(BCSiliconItems.redstoneChipset,
            new net.minecraft.util.Identifier("buildcraftsilicon","chipset"),(stack,world,entity,seed)->buildcraft.api.enums.EnumRedstoneChipset.fromStack(stack).ordinal()/4f);
        net.fabricmc.fabric.api.event.client.player.ClientPickBlockGatherCallback.EVENT.register((player, result) -> {
            if (result instanceof net.minecraft.util.hit.BlockHitResult hit && hit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK
                && player.getWorld().getBlockEntity(hit.getBlockPos()) instanceof buildcraft.transport.tile.TilePipeHolder tile) {
                var trace = buildcraft.transport.BCTransportBlocks.pipeHolder.rayTrace(player.getWorld(), hit.getBlockPos(), player);
                if (trace != null && trace.subHit > 6 && trace.subHit <= 12) {
                    var plug = tile.getPluggable(buildcraft.transport.block.BlockPipeHolder.getPartSideHit(trace));
                    if (plug instanceof PluggableGate || plug instanceof buildcraft.silicon.plug.PluggableSidedPlate
                        || plug instanceof buildcraft.silicon.plug.PluggableLens || plug instanceof buildcraft.silicon.plug.PluggableFacade) return plug.getPickStack();
                }
            }
            return net.minecraft.item.ItemStack.EMPTY;
        });
        net.minecraft.client.gui.screen.ingame.HandledScreens.register(BCSiliconScreens.GATE, GuiGate::new);
        PipeRegistryClient.INSTANCE.registerRenderer(PluggableGate.class, (gate, matrices, consumer, light, delta) -> {
            for (var quad : MODEL.getQuads(gate.logic.variant, gate.side, gate.logic.isOn)) quad.render(consumer, matrices.peek(), light, OverlayTexture.DEFAULT_UV);
        });
        PipeRegistryClient.INSTANCE.registerRenderer(buildcraft.silicon.plug.PluggableLightSensor.class,(plug,matrices,consumer,light,delta)->{
            for(var quad:CONTROL_MODEL.getQuads(buildcraft.silicon.item.ItemPluggableControl.Kind.LIGHT_SENSOR,plug.side,false,false,false,0))quad.render(consumer,matrices.peek(),light,OverlayTexture.DEFAULT_UV);
        });
        PipeRegistryClient.INSTANCE.registerRenderer(buildcraft.silicon.plug.PluggableTimer.class,(plug,matrices,consumer,light,delta)->{
            for(var quad:CONTROL_MODEL.getQuads(buildcraft.silicon.item.ItemPluggableControl.Kind.TIMER,plug.side,false,false,false,0))quad.render(consumer,matrices.peek(),light,OverlayTexture.DEFAULT_UV);
        });
        PipeRegistryClient.INSTANCE.registerRenderer(buildcraft.silicon.plug.PluggablePulsar.class,(plug,matrices,consumer,light,delta)->{
            for(var quad:CONTROL_MODEL.getQuads(buildcraft.silicon.item.ItemPluggableControl.Kind.PULSAR,plug.side,plug.isPulsing(),plug.isAutomatic(),plug.isManual(),(plug.getPulseStage()+delta)/20.0))quad.render(consumer,matrices.peek(),light,OverlayTexture.DEFAULT_UV);
        });
        for(var item:java.util.List.of(BCSiliconItems.plugPulsar,BCSiliconItems.plugLightSensor,BCSiliconItems.plugTimer)) {
            net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry.INSTANCE.register(item,(stack,mode,matrices,consumers,light,overlay)->{
                var consumer=consumers.getBuffer(RenderLayer.getEntityCutout(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE));
                matrices.push();matrices.translate(5/16.0,0,0);
                for(var quad:CONTROL_MODEL.getQuads(item.kind,Direction.WEST,false,false,false,0))quad.render(consumer,matrices.peek(),light,overlay);
                matrices.pop();
            });
        }
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
