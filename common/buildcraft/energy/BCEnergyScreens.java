package buildcraft.energy;

import buildcraft.energy.container.*;
import buildcraft.energy.tile.*;
import buildcraft.lib.gui.ContainerBC_Neptune;
import buildcraft.lib.tile.TileBC_Neptune;
import net.fabricmc.fabric.api.screenhandler.v1.*;
import net.minecraft.entity.player.*;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.*;
import net.minecraft.screen.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public final class BCEnergyScreens {
    private BCEnergyScreens() {}
    @FunctionalInterface private interface Factory<T extends TileBC_Neptune> {
        ContainerBC_Neptune create(PlayerEntity player, int syncId, T tile);
    }
    public static final ScreenHandlerType<ContainerBC_Neptune> STONE = type(TileEngineStone_BC8.class, ContainerEngineStone_BC8::new);
    public static final ScreenHandlerType<ContainerBC_Neptune> IRON = type(TileEngineIron_BC8.class, ContainerEngineIron_BC8::new);
    public static final ScreenHandlerType<ContainerBC_Neptune> DYNAMO = type(TileDynamoMJ.class, ContainerDynamoMJ::new);
    public static final ScreenHandlerType<ContainerBC_Neptune> RF = type(TileEngineRF.class, ContainerEngineRF::new);
    private static <T extends TileBC_Neptune> ScreenHandlerType<ContainerBC_Neptune> type(Class<T> tileClass, Factory<T> factory) {
        return new ExtendedScreenHandlerType<>((syncId, inventory, data) -> {
            BlockPos pos = data.readBlockPos();
            var tile = inventory.player.getWorld().getBlockEntity(pos);
            if (!tileClass.isInstance(tile)) throw new IllegalStateException("Engine screen type does not match tile at " + pos);
            return factory.create(inventory.player, syncId, tileClass.cast(tile));
        });
    }
    public static void register() {
        Registry.register(Registries.SCREEN_HANDLER, new Identifier("buildcraftenergy", "engine_stone"), STONE);
        Registry.register(Registries.SCREEN_HANDLER, new Identifier("buildcraftenergy", "engine_iron"), IRON);
        Registry.register(Registries.SCREEN_HANDLER, new Identifier("buildcraftenergy", "mj_dynamo"), DYNAMO);
        Registry.register(Registries.SCREEN_HANDLER, new Identifier("buildcraftenergy", "engine_rf"), RF);
    }
    public static void open(PlayerEntity player, BlockPos pos, BCEnergyGuis gui) {
        if (!(player instanceof ServerPlayerEntity) || !player.getWorld().isChunkLoaded(pos)) return;
        var tile = player.getWorld().getBlockEntity(pos);
        if (gui == BCEnergyGuis.ENGINE_STONE && tile instanceof TileEngineStone_BC8 stone)
            open(player, stone, ContainerEngineStone_BC8::new, "gui.buildcraftenergy.engine_stone");
        else if (gui == BCEnergyGuis.ENGINE_IRON && tile instanceof TileEngineIron_BC8 iron)
            open(player, iron, ContainerEngineIron_BC8::new, "gui.buildcraftenergy.engine_iron");
        else if (gui == BCEnergyGuis.DYNAMO_MJ && tile instanceof TileDynamoMJ dynamo)
            open(player, dynamo, ContainerDynamoMJ::new, "tile.mjDynamo.name");
        else if (gui == BCEnergyGuis.ENGINE_RF && tile instanceof TileEngineRF rf)
            open(player, rf, ContainerEngineRF::new, "tile.engine.rf.name");
    }
    private static <T extends TileBC_Neptune> void open(PlayerEntity player, T tile, Factory<T> factory, String title) {
        if (!tile.canInteractWith(player)) return;
        player.openHandledScreen(new ExtendedScreenHandlerFactory() {
            public Text getDisplayName() { return Text.translatable(title); }
            public void writeScreenOpeningData(ServerPlayerEntity user, PacketByteBuf buffer) { buffer.writeBlockPos(tile.getPos()); }
            public ScreenHandler createMenu(int syncId, PlayerInventory inventory, PlayerEntity user) {
                return tile.canInteractWith(user) ? factory.create(user, syncId, tile) : null;
            }
        });
    }
}
