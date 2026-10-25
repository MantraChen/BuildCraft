/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.transport;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.entity.player.*;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.*;
import net.minecraft.screen.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import buildcraft.lib.gui.ContainerBC_Neptune;
import buildcraft.transport.container.ContainerDiamondPipe;
import buildcraft.transport.container.ContainerDiamondWoodPipe;
import buildcraft.transport.pipe.behaviour.PipeBehaviourDiamond;
import buildcraft.transport.pipe.behaviour.PipeBehaviourWoodDiamond;
import buildcraft.transport.tile.TilePipeHolder;

/** Native filter screens. Remake by FelixMantra. */
public final class BCTransportScreens {
    private BCTransportScreens() {}
    public static final ScreenHandlerType<ContainerBC_Neptune> DIAMOND = new ExtendedScreenHandlerType<>((syncId, inventory, data) -> {
        var pos = data.readBlockPos();
        if (!(inventory.player.getWorld().getBlockEntity(pos) instanceof TilePipeHolder holder)
                || holder.getPipe() == null || !(holder.getPipe().behaviour instanceof PipeBehaviourDiamond diamond)) {
            throw new IllegalStateException("Diamond screen opened for missing pipe at " + pos);
        }
        return new ContainerDiamondPipe(inventory.player, syncId, diamond);
    });
    public static final ScreenHandlerType<ContainerBC_Neptune> DIAMOND_WOOD = new ExtendedScreenHandlerType<>((syncId, inventory, data) -> {
        var pos = data.readBlockPos();
        if (!(inventory.player.getWorld().getBlockEntity(pos) instanceof TilePipeHolder holder)
                || holder.getPipe() == null || !(holder.getPipe().behaviour instanceof PipeBehaviourWoodDiamond diamond)) {
            throw new IllegalStateException("Wooden diamond screen opened for missing pipe at " + pos);
        }
        return new ContainerDiamondWoodPipe(inventory.player, syncId, diamond);
    });
    public static void register() {
        Registry.register(Registries.SCREEN_HANDLER, new Identifier("buildcrafttransport", "diamond_pipe"), DIAMOND);
        Registry.register(Registries.SCREEN_HANDLER, new Identifier("buildcrafttransport", "diamond_wood_pipe"), DIAMOND_WOOD);
    }
    public static void openDiamondWood(PlayerEntity player, PipeBehaviourWoodDiamond diamond) {
        var holder = diamond.pipe.getHolder();
        if (!(player instanceof ServerPlayerEntity) || !holder.canPlayerInteract(player) || holder.getPipe() != diamond.pipe) return;
        player.openHandledScreen(new ExtendedScreenHandlerFactory() {
            @Override public Text getDisplayName() { return Text.translatable("gui.pipes.emerald.title"); }
            @Override public void writeScreenOpeningData(ServerPlayerEntity player, PacketByteBuf data) { data.writeBlockPos(holder.getPipePos()); }
            @Override public ScreenHandler createMenu(int syncId, PlayerInventory inventory, PlayerEntity user) {
                return holder.canPlayerInteract(user) && holder.getPipe() == diamond.pipe ? new ContainerDiamondWoodPipe(user, syncId, diamond) : null;
            }
        });
    }
    public static void openDiamond(PlayerEntity player, PipeBehaviourDiamond diamond) {
        var holder = diamond.pipe.getHolder();
        if (!(player instanceof ServerPlayerEntity) || !holder.canPlayerInteract(player) || holder.getPipe() != diamond.pipe) return;
        player.openHandledScreen(new ExtendedScreenHandlerFactory() {
            @Override public Text getDisplayName() { return Text.translatable("gui.pipes.emerald.title"); }
            @Override public void writeScreenOpeningData(ServerPlayerEntity player, PacketByteBuf data) { data.writeBlockPos(holder.getPipePos()); }
            @Override public ScreenHandler createMenu(int syncId, PlayerInventory inventory, PlayerEntity user) {
                return holder.canPlayerInteract(user) && holder.getPipe() == diamond.pipe ? new ContainerDiamondPipe(user, syncId, diamond) : null;
            }
        });
    }
}
