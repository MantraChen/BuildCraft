/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.container;

import net.minecraft.entity.player.PlayerEntity;

import buildcraft.lib.gui.ContainerBCTile;
import buildcraft.lib.gui.slot.SlotBase;

import buildcraft.builders.tile.TileReplacer;

public class ContainerReplacer extends ContainerBCTile<TileReplacer> {
    public ContainerReplacer(PlayerEntity player, TileReplacer tile) {
        this(player,0,tile);
    }
    public ContainerReplacer(PlayerEntity player,int syncId,TileReplacer tile){
        super(buildcraft.builders.BCBuildersScreens.REPLACER,player,syncId,tile);
        addSlot(new SlotBase(tile.invSnapshot,0,8,96));
        addSlot(new SlotBase(tile.invSchematicFrom,0,92,96));
        addSlot(new SlotBase(tile.invSchematicTo,0,140,96));
        addFullPlayerInventory(48,130);
        addProperties(tile.properties);
    }
}
