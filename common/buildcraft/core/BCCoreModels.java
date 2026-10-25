/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team.
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 */
package buildcraft.core;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.screen.PlayerScreenHandler;
import buildcraft.api.enums.EnumEngineType;
import buildcraft.api.enums.EnumPowerStage;
import buildcraft.lib.client.model.EngineModel;
import buildcraft.lib.client.model.MutableQuad;
import buildcraft.lib.engine.TileEngineBase_BC8;
import buildcraft.lib.registry.BlockEntityRegistry;
import buildcraft.core.client.render.RenderEngineCreative;
import buildcraft.core.client.render.RenderEngineWood;
import buildcraft.core.tile.TileEngineCreative;
import buildcraft.core.tile.TileEngineRedstone_BC8;
import buildcraft.energy.BCEnergyModels;

@Environment(EnvType.CLIENT)
public final class BCCoreModels {
    private static final EngineModel REDSTONE = new EngineModel("buildcraftcore:bc_models/block/engine_redstone.json");
    private static final EngineModel CREATIVE = new EngineModel("buildcraftcore:bc_models/block/engine_creative.json");

    public static void fmlPreInit() { /* Class initialization constructs the resource holders. */ }

    public static void fmlInit() {
        net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin.register(plugin -> plugin.modifyModelAfterBake().register((model, context) -> {
            if (model != null && context.id() instanceof net.minecraft.client.util.ModelIdentifier id
                    && id.getNamespace().equals("buildcraftcore") && id.getPath().equals("fragile_fluid_shard") && id.getVariant().equals("inventory")) {
                var mask = context.textureGetter().apply(new net.minecraft.client.util.SpriteIdentifier(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE,
                    new net.minecraft.util.Identifier("buildcraftcore:items/fragile_fluid_shard_fluid")));
                return new buildcraft.core.client.ModelFluidShard(model, mask);
            }
            return model;
        }));
        if (BCCoreItems.fragileFluidShard != null) net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry.ITEM.register(
            (stack, index) -> {
                var fluid = buildcraft.core.item.ItemFragileFluidContainer.getFluid(stack);
                return index == 1 && !fluid.isEmpty()
                    ? net.fabricmc.fabric.api.transfer.v1.client.fluid.FluidVariantRendering.getColor(fluid.getFluidVariant()) : -1;
            }, BCCoreItems.fragileFluidShard);
        var variant = new net.minecraft.util.Identifier("buildcraftcore", "variant");
        if (BCCoreItems.mapLocation != null) net.minecraft.client.item.ModelPredicateProviderRegistry.register(
            BCCoreItems.mapLocation, variant, (stack, world, entity, seed) -> buildcraft.api.items.IMapLocation.MapLocationType.getFromStack(stack).ordinal() / 10.0f);
        if (BCCoreItems.paintbrush != null) net.minecraft.client.item.ModelPredicateProviderRegistry.register(BCCoreItems.paintbrush,variant,
            (stack,world,entity,seed)->{var c=buildcraft.core.item.ItemPaintbrush_BC8.getColour(stack);return c==null?0:(c.getId()+1)/20f;});
        if (BCCoreItems.list != null) net.minecraft.client.item.ModelPredicateProviderRegistry.register(
            BCCoreItems.list, variant, (stack, world, entity, seed) -> buildcraft.lib.list.ListHandler.hasItems(stack) ? 1 : 0);
        if (BCCoreBlocks.decorated != null) net.minecraft.client.item.ModelPredicateProviderRegistry.register(
            BCCoreBlocks.decorated.asItem(), variant, (stack, world, entity, seed) -> buildcraft.core.item.ItemBlockDecorated.getVariant(stack).ordinal() / 10.0f);
        for (var marker : new net.minecraft.block.Block[] {BCCoreBlocks.markerVolume, BCCoreBlocks.markerPath})
            if (marker != null) net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE
                .putBlock(marker, RenderLayer.getCutout());
        buildcraft.lib.client.render.DetachedRenderer.register(buildcraft.lib.client.render.MarkerRenderer.INSTANCE);
        buildcraft.lib.client.render.DetachedRenderer.register(buildcraft.core.client.render.RenderVolumeBoxes.INSTANCE);
        buildcraft.core.marker.volume.MessageVolumeBoxes.registerClientHandler();
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->buildcraft.core.marker.volume.ClientVolumeBoxes.INSTANCE.clear());
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.START_CLIENT_TICK.register(client->buildcraft.core.marker.volume.ClientVolumeBoxes.INSTANCE.worldChanged(client.world));
        if (BCCoreBlocks.markerVolume != null) BlockEntityRendererFactories.register(
            BlockEntityRegistry.type(buildcraft.core.tile.TileMarkerVolume.class), buildcraft.core.client.render.RenderMarkerVolume::new);
        if (BCCoreBlocks.engine == null) return;
        BlockEntityRendererFactories.register(BlockEntityRegistry.type(TileEngineRedstone_BC8.class),
            context -> RenderEngineWood.INSTANCE);
        BlockEntityRendererFactories.register(BlockEntityRegistry.type(TileEngineCreative.class),
            context -> RenderEngineCreative.INSTANCE);
        BuiltinItemRendererRegistry.INSTANCE.register(BCCoreBlocks.engine,
            (stack, mode, matrices, consumers, light, overlay) -> {
                EnumEngineType type = BCCoreBlocks.engine.getEngineType(stack);
                MutableQuad[] quads = switch (type) {
                    case WOOD -> REDSTONE.getItemQuads(EnumPowerStage.BLUE);
                    case CREATIVE -> CREATIVE.getItemQuads(EnumPowerStage.BLACK);
                    default -> BCEnergyModels.getItemQuads(type);
                };
                var buffer = consumers.getBuffer(RenderLayer.getEntityCutout(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE));
                for (MutableQuad quad : quads) quad.render(buffer, matrices.peek(), light, overlay);
            });
    }

    private static MutableQuad[] getQuads(EngineModel model, TileEngineBase_BC8 tile, float tickDelta) {
        return model.getQuads(tile.getProgressClient(tickDelta), tile.getPowerStage(),
            tile.getCurrentFacing(), tile.clientModelData);
    }

    public static MutableQuad[] getRedstoneEngineQuads(TileEngineRedstone_BC8 tile, float tickDelta) {
        return getQuads(REDSTONE, tile, tickDelta);
    }

    public static MutableQuad[] getCreativeEngineQuads(TileEngineCreative tile, float tickDelta) {
        return getQuads(CREATIVE, tile, tickDelta);
    }
}
