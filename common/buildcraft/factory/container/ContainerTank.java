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

import buildcraft.factory.tile.TileTank;

public class ContainerTank extends ContainerBCTile<TileTank> {
    public final WidgetFluidTank widgetTank;

    public ContainerTank(PlayerEntity player, TileTank tank) {
        this(player, 0, tank);
    }

    public ContainerTank(PlayerEntity player, int syncId, TileTank tank) {
        super(buildcraft.factory.BCFactoryScreens.TANK, player, syncId, tank);

        addFullPlayerInventory(99);

        widgetTank = addWidget(new WidgetFluidTank(this, tank.tank));
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        if (!player.getWorld().isClient && canUse(player) && index >= 0 && index < slots.size()) {
            Slot slot = slots.get(index);
            if (slot.inventory == player.getInventory() && tile.tank.interactInventorySlot(this, slot)) {
                tile.balanceTankFluids();
                sendContentUpdates();
            }
        }
        // Fluid exchange already changed the actual inventory transactionally; no ordinary item move occurred.
        return ItemStack.EMPTY;
    }
}
