/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team.
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 */
package buildcraft.energy;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import buildcraft.api.enums.EnumEngineType;
import buildcraft.api.enums.EnumPowerStage;
import buildcraft.core.BCCoreBlocks;
import buildcraft.lib.client.model.EngineModel;
import buildcraft.lib.client.model.MutableQuad;
import buildcraft.lib.engine.TileEngineBase_BC8;
import buildcraft.lib.registry.BlockEntityRegistry;
import buildcraft.energy.client.render.RenderEngineStone;
import buildcraft.energy.client.render.RenderEngineIron;
import buildcraft.energy.client.render.RenderEngineRF;
import buildcraft.energy.tile.TileEngineStone_BC8;
import buildcraft.energy.tile.TileEngineIron_BC8;
import buildcraft.energy.tile.TileEngineRF;

@Environment(EnvType.CLIENT)
public final class BCEnergyModels {
    private static final EngineModel STONE = new EngineModel("buildcraftenergy:models/block/engine_stone.json");
    private static final EngineModel IRON = new EngineModel("buildcraftenergy:models/block/engine_iron.json");
    private static final EngineModel RF = new EngineModel("buildcraftenergy:models/block/engine_rf.json");

    public static void fmlPreInit() { /* Class initialization constructs the resource holders. */ }

    public static void fmlInit() {
        if (BCCoreBlocks.engine == null) return;
        BlockEntityRendererFactories.register(BlockEntityRegistry.type(TileEngineStone_BC8.class),
            context -> RenderEngineStone.INSTANCE);
        BlockEntityRendererFactories.register(BlockEntityRegistry.type(TileEngineIron_BC8.class),
            context -> RenderEngineIron.INSTANCE);
        BlockEntityRendererFactories.register(BlockEntityRegistry.type(TileEngineRF.class),
            context -> RenderEngineRF.INSTANCE);
    }

    public static MutableQuad[] getItemQuads(EnumEngineType type) {
        return switch (type) {
            case STONE -> STONE.getItemQuads(EnumPowerStage.BLUE);
            case IRON -> IRON.getItemQuads(EnumPowerStage.BLUE);
            case RF -> RF.getItemQuads(EnumPowerStage.BLUE);
            default -> MutableQuad.EMPTY_ARRAY;
        };
    }

    private static MutableQuad[] getQuads(EngineModel model, TileEngineBase_BC8 tile, float tickDelta) {
        return model.getQuads(tile.getProgressClient(tickDelta), tile.getPowerStage(),
            tile.getCurrentFacing(), tile.clientModelData);
    }

    public static MutableQuad[] getStoneEngineQuads(TileEngineStone_BC8 tile, float tickDelta) {
        return getQuads(STONE, tile, tickDelta);
    }
    public static MutableQuad[] getIronEngineQuads(TileEngineIron_BC8 tile, float tickDelta) {
        return getQuads(IRON, tile, tickDelta);
    }
    public static MutableQuad[] getRfEngineQuads(TileEngineRF tile, float tickDelta) {
        return getQuads(RF, tile, tickDelta);
    }
}
