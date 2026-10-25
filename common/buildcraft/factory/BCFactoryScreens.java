package buildcraft.factory;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.entity.player.*;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.*;
import net.minecraft.screen.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import buildcraft.factory.container.ContainerTank;
import buildcraft.factory.tile.TileTank;
import buildcraft.factory.container.ContainerChute;
import buildcraft.factory.tile.TileChute;
import buildcraft.lib.gui.ContainerBC_Neptune;

/** Native server-authoritative machine screens. */
public final class BCFactoryScreens {
    private BCFactoryScreens() {}
    public static final ScreenHandlerType<ContainerBC_Neptune> TANK = new ExtendedScreenHandlerType<>(
        (syncId, inventory, data) -> {
            var pos = data.readBlockPos();
            if (!(inventory.player.getWorld().getBlockEntity(pos) instanceof TileTank tank)) {
                throw new IllegalStateException("Tank screen opened for missing tank at " + pos);
            }
            return new ContainerTank(inventory.player, syncId, tank);
        });
    public static final ScreenHandlerType<ContainerBC_Neptune> CHUTE = new ExtendedScreenHandlerType<>(
        (syncId, inventory, data) -> {
            var pos = data.readBlockPos();
            if (!(inventory.player.getWorld().getBlockEntity(pos) instanceof TileChute chute)) {
                throw new IllegalStateException("Chute screen opened for missing chute at " + pos);
            }
            return new ContainerChute(inventory.player, syncId, chute);
        });
    public static void register() {
        Registry.register(Registries.SCREEN_HANDLER, new Identifier("buildcraftfactory", "tank"), TANK);
        Registry.register(Registries.SCREEN_HANDLER, new Identifier("buildcraftfactory", "chute"), CHUTE);
    }
    public static void openChute(PlayerEntity player, TileChute chute) {
        if (!(player instanceof ServerPlayerEntity) || !chute.canInteractWith(player)) return;
        player.openHandledScreen(new ExtendedScreenHandlerFactory() {
            @Override public Text getDisplayName() { return chute.getCachedState().getBlock().getName(); }
            @Override public void writeScreenOpeningData(ServerPlayerEntity player, PacketByteBuf data) { data.writeBlockPos(chute.getPos()); }
            @Override public ScreenHandler createMenu(int syncId, PlayerInventory inventory, PlayerEntity user) {
                return chute.canInteractWith(user) ? new ContainerChute(user, syncId, chute) : null;
            }
        });
    }
    public static void openTank(PlayerEntity player, TileTank tank) {
        if (!(player instanceof ServerPlayerEntity) || !tank.canInteractWith(player)) return;
        player.openHandledScreen(new ExtendedScreenHandlerFactory() {
            @Override public Text getDisplayName() { return tank.getCachedState().getBlock().getName(); }
            @Override public void writeScreenOpeningData(ServerPlayerEntity player, PacketByteBuf data) { data.writeBlockPos(tank.getPos()); }
            @Override public ScreenHandler createMenu(int syncId, PlayerInventory inventory, PlayerEntity user) {
                return tank.canInteractWith(user) ? new ContainerTank(user, syncId, tank) : null;
            }
        });
    }
}
