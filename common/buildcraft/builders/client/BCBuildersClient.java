package buildcraft.builders.client;

import buildcraft.builders.BCBuildersScreens;
import buildcraft.builders.container.ContainerFiller;
import buildcraft.builders.gui.GuiFiller;
import buildcraft.builders.tile.TileFiller;
import buildcraft.builders.client.render.RenderFiller;
import buildcraft.builders.container.ContainerBuilder;
import buildcraft.builders.gui.GuiBuilder;
import buildcraft.builders.tile.TileBuilder;
import buildcraft.builders.client.render.RenderBuilder;
import buildcraft.lib.gui.ContainerBC_Neptune;
import buildcraft.lib.registry.BlockEntityRegistry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.minecraft.util.Identifier;
import buildcraft.builders.BCBuildersItems;
import buildcraft.builders.item.ItemSnapshot;

@Environment(EnvType.CLIENT)
public final class BCBuildersClient {
    private BCBuildersClient() {}
    public static void register() {
        if (BCBuildersItems.snapshot != null) ModelPredicateProviderRegistry.register(BCBuildersItems.snapshot, new Identifier("buildcraftbuilders", "snapshot_type"),
            (stack, world, entity, seed) -> ItemSnapshot.EnumItemSnapshotType.getFromStack(stack).ordinal());
        HandledScreens.<ContainerBC_Neptune, GuiFiller>register(BCBuildersScreens.FILLER,
            (handler, inventory, title) -> new GuiFiller((ContainerFiller) handler, inventory, title));
        BlockEntityRendererFactories.register(BlockEntityRegistry.type(TileFiller.class), RenderFiller::new);
        HandledScreens.<ContainerBC_Neptune, GuiBuilder>register(BCBuildersScreens.BUILDER,
            (handler, inventory, title) -> new GuiBuilder((ContainerBuilder) handler, inventory, title));
        BlockEntityRendererFactories.register(BlockEntityRegistry.type(TileBuilder.class), RenderBuilder::new);
    }
}
