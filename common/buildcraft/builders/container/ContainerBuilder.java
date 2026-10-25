/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.builders.container;

import java.util.List;
import net.minecraft.entity.player.PlayerEntity;
import buildcraft.builders.BCBuildersScreens;
import buildcraft.builders.tile.TileBuilder;
import buildcraft.lib.gui.ContainerBCTile;
import buildcraft.lib.gui.slot.SlotBase;
import buildcraft.lib.gui.widget.WidgetFluidTank;

public class ContainerBuilder extends ContainerBCTile<TileBuilder> {
    public final List<WidgetFluidTank> widgetTanks;
    public ContainerBuilder(PlayerEntity player, int syncId, TileBuilder tile) {
        super(BCBuildersScreens.BUILDER, player, syncId, tile);
        addFullPlayerInventory(140);
        addSlot(new SlotBase(tile.invSnapshot, 0, 80, 27));
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new SlotBase(tile.invResources, col + row * 9, 8 + col * 18, 72 + row * 18));
        widgetTanks = tile.getTankManager().getTanks().stream().map(tank -> addWidget(new WidgetFluidTank(this, tank))).toList();
    }
}
