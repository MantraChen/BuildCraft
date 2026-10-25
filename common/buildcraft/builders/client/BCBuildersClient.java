package buildcraft.builders.client;

import buildcraft.builders.BCBuildersScreens;
import buildcraft.builders.container.ContainerFiller;
import buildcraft.builders.gui.GuiFiller;
import buildcraft.builders.tile.TileFiller;
import buildcraft.builders.client.render.RenderFiller;
import buildcraft.lib.gui.ContainerBC_Neptune;
import buildcraft.lib.registry.BlockEntityRegistry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;

@Environment(EnvType.CLIENT)
public final class BCBuildersClient {
    private BCBuildersClient() {}
    public static void register() {
        HandledScreens.<ContainerBC_Neptune, GuiFiller>register(BCBuildersScreens.FILLER,
            (handler, inventory, title) -> new GuiFiller((ContainerFiller) handler, inventory, title));
        BlockEntityRendererFactories.register(BlockEntityRegistry.type(TileFiller.class), RenderFiller::new);
    }
}
