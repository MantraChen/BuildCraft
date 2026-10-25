/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.energy.container;

import net.minecraft.entity.player.PlayerEntity;

import buildcraft.lib.gui.ContainerBCTile;
import buildcraft.lib.gui.slot.SlotBase;

import buildcraft.energy.tile.TileEngineRF;

public class ContainerEngineRF extends ContainerBCTile<TileEngineRF> {

    public ContainerEngineRF(PlayerEntity player, TileEngineRF engine) {
        this(player, 0, engine);
    }

    public ContainerEngineRF(PlayerEntity player, int syncId, TileEngineRF engine) {
        super(buildcraft.energy.BCEnergyScreens.RF, player, syncId, engine);

        addFullPlayerInventory(95);
        for (int slot = 0; slot < 4; slot++) {
            addSlot(new SlotBase(engine.invUpgrades, slot, 62 + 18 * slot, 44));
        }
    }
}
