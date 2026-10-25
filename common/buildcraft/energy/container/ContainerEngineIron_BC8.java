/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.energy.container;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import buildcraft.lib.gui.ContainerBCTile;
import buildcraft.lib.gui.widget.WidgetFluidTank;
import buildcraft.energy.tile.TileEngineIron_BC8;

public class ContainerEngineIron_BC8 extends ContainerBCTile<TileEngineIron_BC8> {
    public final WidgetFluidTank widgetTankFuel, widgetTankCoolant, widgetTankResidue;
    public ContainerEngineIron_BC8(PlayerEntity player, TileEngineIron_BC8 tile) { this(player, 0, tile); }
    public ContainerEngineIron_BC8(PlayerEntity player, int syncId, TileEngineIron_BC8 tile) {
        super(buildcraft.energy.BCEnergyScreens.IRON, player, syncId, tile);
        addFullPlayerInventory(95);
        widgetTankFuel = addWidget(new WidgetFluidTank(this, tile.tankFuel));
        widgetTankCoolant = addWidget(new WidgetFluidTank(this, tile.tankCoolant));
        widgetTankResidue = addWidget(new WidgetFluidTank(this, tile.tankResidue));
    }
    @Override public ItemStack quickMove(PlayerEntity player, int index) {
        if (player.getWorld().isClient || !canUse(player) || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        var slot = slots.get(index);
        if (slot.inventory == player.getInventory() && (tile.tankFuel.interactInventorySlot(this, slot)
            || tile.tankCoolant.interactInventorySlot(this, slot) || tile.tankResidue.interactInventorySlot(this, slot))) sendContentUpdates();
        return ItemStack.EMPTY;
    }
}
