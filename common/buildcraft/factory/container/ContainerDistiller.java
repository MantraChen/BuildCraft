/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.factory.container;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.slot.Slot;
import net.minecraft.item.ItemStack;

import buildcraft.lib.gui.ContainerBCTile;
import buildcraft.lib.gui.widget.WidgetFluidTank;

import buildcraft.factory.tile.TileDistiller_BC8;

public class ContainerDistiller extends ContainerBCTile<TileDistiller_BC8> {
    public final WidgetFluidTank widgetInputTank;
    public final WidgetFluidTank widgetOutputGasTank;
    public final WidgetFluidTank widgetOutputLiquidTank;
    private final net.minecraft.screen.PropertyDelegate properties;

    public ContainerDistiller(PlayerEntity player, TileDistiller_BC8 tank) {
        this(player, 0, tank);
    }
    public ContainerDistiller(PlayerEntity player, int syncId, TileDistiller_BC8 tank) {
        super(buildcraft.factory.BCFactoryScreens.DISTILLER, player, syncId, tank);
        properties = player.getWorld().isClient ? new net.minecraft.screen.ArrayPropertyDelegate(2)
            : new net.minecraft.screen.PropertyDelegate() {
                public int size() { return 2; }
                public int get(int index) { return index == 0 ? tank.getProgressScaled()
                    : (int) (tank.getStoredPower() / buildcraft.api.mj.MjAPI.MJ); }
                public void set(int index, int value) {}
            };
        addProperties(properties);

        addFullPlayerInventory(79);

        widgetInputTank = addWidget(new WidgetFluidTank(this, tank.tankIn));
        widgetOutputGasTank = addWidget(new WidgetFluidTank(this, tank.tankGasOut));
        widgetOutputLiquidTank = addWidget(new WidgetFluidTank(this, tank.tankLiquidOut));
    }
    public int getProgress() { return properties.get(0); }
    public int getStoredMj() { return properties.get(1); }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        if (!player.getWorld().isClient && canUse(player) && index >= 0 && index < slots.size()) {
            Slot slot = slots.get(index);
            if (slot.inventory == player.getInventory() && tile.tankIn.interactInventorySlot(this, slot)) {
                sendContentUpdates();
            }
        }
        // Fluid exchange already changed the actual inventory transactionally; no ordinary item move occurred.
        return ItemStack.EMPTY;
    }
}
