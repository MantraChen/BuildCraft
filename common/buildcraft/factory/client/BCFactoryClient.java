package buildcraft.factory.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import buildcraft.factory.*;
import buildcraft.factory.container.ContainerTank;
import buildcraft.factory.gui.GuiTank;
import buildcraft.factory.tile.TileTank;
import buildcraft.factory.client.render.RenderTank;
import buildcraft.lib.registry.BlockEntityRegistry;

@Environment(EnvType.CLIENT)
public final class BCFactoryClient {
    private BCFactoryClient() {}
    public static void register() {
        if (BCFactoryBlocks.miningWell != null) BlockEntityRendererFactories.register(
            BlockEntityRegistry.type(buildcraft.factory.tile.TileMiningWell.class), buildcraft.factory.client.render.RenderMiningWell::new);
        if (BCFactoryBlocks.pump != null) BlockEntityRendererFactories.register(
            BlockEntityRegistry.type(buildcraft.factory.tile.TilePump.class), buildcraft.factory.client.render.RenderPump::new);
        HandledScreens.<buildcraft.lib.gui.ContainerBC_Neptune, GuiTank>register(BCFactoryScreens.TANK,
            (handler, inventory, title) -> new GuiTank((ContainerTank) handler, inventory, title));
        if (BCFactoryBlocks.tank != null) {
            BlockRenderLayerMap.INSTANCE.putBlock(BCFactoryBlocks.tank, RenderLayer.getCutout());
            BlockEntityRendererFactories.register(BlockEntityRegistry.type(TileTank.class), RenderTank::new);
        }
    }
}
