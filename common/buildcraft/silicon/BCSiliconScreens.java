/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.silicon;

import net.fabricmc.fabric.api.screenhandler.v1.*;
import net.minecraft.entity.player.*;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.*;
import net.minecraft.screen.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import buildcraft.silicon.container.ContainerGate;
import buildcraft.silicon.plug.PluggableGate;
import buildcraft.transport.tile.TilePipeHolder;

public final class BCSiliconScreens {
    private BCSiliconScreens() {}
    public static final ScreenHandlerType<ContainerGate> GATE = new ExtendedScreenHandlerType<>((syncId, inventory, data) -> {
        var pos = data.readBlockPos();
        int side = data.readUnsignedByte();
        if (side >= 6 || !(inventory.player.getWorld().getBlockEntity(pos) instanceof TilePipeHolder holder)
            || !(holder.getPluggable(Direction.values()[side]) instanceof PluggableGate gate)) throw new IllegalStateException("Missing gate at " + pos);
        var screen = new ContainerGate(inventory.player, syncId, gate);
        screen.readOpeningData(data);
        return screen;
    });
    public static void register() { Registry.register(Registries.SCREEN_HANDLER, new Identifier("buildcraftsilicon", "gate"), GATE); }
    public static void open(PlayerEntity player, PluggableGate gate) {
        var holder = gate.holder;
        if (!(player instanceof ServerPlayerEntity) || !holder.canPlayerInteract(player) || holder.getPluggable(gate.side) != gate) return;
        player.openHandledScreen(new ExtendedScreenHandlerFactory() {
            private ContainerGate menu;
            public Text getDisplayName() { return BCSiliconItems.plugGate.getName(gate.getPickStack()); }
            public void writeScreenOpeningData(ServerPlayerEntity player, PacketByteBuf data) {
                data.writeBlockPos(holder.getPipePos());
                data.writeByte(gate.side.ordinal());
                menu.writeOpeningData(data);
            }
            public ScreenHandler createMenu(int syncId, PlayerInventory inventory, PlayerEntity user) {
                return menu = holder.canPlayerInteract(user) && holder.getPluggable(gate.side) == gate ? new ContainerGate(user, syncId, gate) : null;
            }
        });
    }
}
