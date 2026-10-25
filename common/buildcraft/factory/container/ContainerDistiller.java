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

    public ContainerDistiller(PlayerEntity player, TileDistiller_BC8 tank) {
        super(player, tank);

        addFullPlayerInventory(79);

        widgetInputTank = addWidget(new WidgetFluidTank(this, tank.tankIn));
        widgetOutputGasTank = addWidget(new WidgetFluidTank(this, tank.tankGasOut));
        widgetOutputLiquidTank = addWidget(new WidgetFluidTank(this, tank.tankLiquidOut));
    }

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
