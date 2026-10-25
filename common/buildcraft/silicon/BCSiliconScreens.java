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
    public static final ScreenHandlerType<buildcraft.silicon.container.ContainerIntegrationTable> INTEGRATION=new ExtendedScreenHandlerType<>((syncId,inventory,data)->{
        var pos=data.readBlockPos();
        if(!(inventory.player.getWorld().getBlockEntity(pos) instanceof buildcraft.silicon.tile.TileIntegrationTable tile))throw new IllegalStateException("Missing integration table at "+pos);
        return new buildcraft.silicon.container.ContainerIntegrationTable(inventory.player,syncId,tile);
    });
    public static final ScreenHandlerType<buildcraft.silicon.container.ContainerAdvancedCraftingTable> ADVANCED=new ExtendedScreenHandlerType<>((syncId,inventory,data)->{
        var pos=data.readBlockPos();
        if(!(inventory.player.getWorld().getBlockEntity(pos) instanceof buildcraft.silicon.tile.TileAdvancedCraftingTable tile))throw new IllegalStateException("Missing advanced crafting table at "+pos);
        return new buildcraft.silicon.container.ContainerAdvancedCraftingTable(inventory.player,syncId,tile);
    });
    public static final ScreenHandlerType<buildcraft.silicon.container.ContainerAssemblyTable> ASSEMBLY=new ExtendedScreenHandlerType<>((syncId,inventory,data)->{
        var pos=data.readBlockPos();
        if(!(inventory.player.getWorld().getBlockEntity(pos) instanceof buildcraft.silicon.tile.TileAssemblyTable tile))throw new IllegalStateException("Missing assembly table at "+pos);
        return new buildcraft.silicon.container.ContainerAssemblyTable(inventory.player,syncId,tile);
    });
    public static final ScreenHandlerType<ContainerGate> GATE = new ExtendedScreenHandlerType<>((syncId, inventory, data) -> {
        var pos = data.readBlockPos();
        int side = data.readUnsignedByte();
        if (side >= 6 || !(inventory.player.getWorld().getBlockEntity(pos) instanceof TilePipeHolder holder)
            || !(holder.getPluggable(Direction.values()[side]) instanceof PluggableGate gate)) throw new IllegalStateException("Missing gate at " + pos);
        var screen = new ContainerGate(inventory.player, syncId, gate);
        screen.readOpeningData(data);
        return screen;
    });
    public static void register() {
        Registry.register(Registries.SCREEN_HANDLER,new Identifier("buildcraftsilicon","integration_table"),INTEGRATION);
        Registry.register(Registries.SCREEN_HANDLER,new Identifier("buildcraftsilicon","advanced_crafting_table"),ADVANCED);
        Registry.register(Registries.SCREEN_HANDLER,new Identifier("buildcraftsilicon","assembly_table"),ASSEMBLY);
        Registry.register(Registries.SCREEN_HANDLER,new Identifier("buildcraftsilicon","gate"),GATE);
    }
    public static void openIntegration(PlayerEntity player,buildcraft.silicon.tile.TileIntegrationTable tile) {
        if(!(player instanceof ServerPlayerEntity) || !tile.canInteractWith(player))return;
        player.openHandledScreen(new ExtendedScreenHandlerFactory() {
            public Text getDisplayName(){return tile.getCachedState().getBlock().getName();}
            public void writeScreenOpeningData(ServerPlayerEntity user,PacketByteBuf data){data.writeBlockPos(tile.getPos());}
            public ScreenHandler createMenu(int syncId,PlayerInventory inventory,PlayerEntity user) {
                return tile.canInteractWith(user)?new buildcraft.silicon.container.ContainerIntegrationTable(user,syncId,tile):null;
            }
        });
    }
    public static void openAssembly(PlayerEntity player,buildcraft.silicon.tile.TileAssemblyTable tile) {
        if(!(player instanceof ServerPlayerEntity) || !tile.canInteractWith(player))return;
        player.openHandledScreen(new ExtendedScreenHandlerFactory() {
            public Text getDisplayName(){return tile.getCachedState().getBlock().getName();}
            public void writeScreenOpeningData(ServerPlayerEntity user,PacketByteBuf data){data.writeBlockPos(tile.getPos());}
            public ScreenHandler createMenu(int syncId,PlayerInventory inventory,PlayerEntity user) {
                return tile.canInteractWith(user)?new buildcraft.silicon.container.ContainerAssemblyTable(user,syncId,tile):null;
            }
        });
    }
    public static void openAdvanced(PlayerEntity player,buildcraft.silicon.tile.TileAdvancedCraftingTable tile) {
        if(!(player instanceof ServerPlayerEntity) || !tile.canInteractWith(player))return;
        player.openHandledScreen(new ExtendedScreenHandlerFactory() {
            public Text getDisplayName(){return tile.getCachedState().getBlock().getName();}
            public void writeScreenOpeningData(ServerPlayerEntity user,PacketByteBuf data){data.writeBlockPos(tile.getPos());}
            public ScreenHandler createMenu(int syncId,PlayerInventory inventory,PlayerEntity user) {
                return tile.canInteractWith(user)?new buildcraft.silicon.container.ContainerAdvancedCraftingTable(user,syncId,tile):null;
            }
        });
    }
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
