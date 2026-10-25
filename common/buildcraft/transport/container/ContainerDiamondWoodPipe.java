/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 *
 * Ported to Fabric 1.20.1 by R.Chen (https://github.com/MantraChen).
 */

package buildcraft.transport.container;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.PropertyDelegate;
import buildcraft.api.transport.pipe.IFlowItems;
import buildcraft.transport.BCTransportScreens;

import buildcraft.lib.gui.ContainerPipe;
import buildcraft.lib.gui.slot.SlotPhantom;
import buildcraft.lib.tile.item.ItemHandlerSimple;

import buildcraft.transport.pipe.behaviour.PipeBehaviourWoodDiamond;
import buildcraft.transport.pipe.behaviour.PipeBehaviourWoodDiamond.FilterMode;

public class ContainerDiamondWoodPipe extends ContainerPipe {

    public final PipeBehaviourWoodDiamond behaviour;
    private final ItemHandlerSimple filterInv;

    public ContainerDiamondWoodPipe(PlayerEntity player, int syncId, PipeBehaviourWoodDiamond behaviour) {
        super(BCTransportScreens.DIAMOND_WOOD, player, syncId, behaviour.pipe.getHolder());
        this.behaviour = behaviour;
        this.filterInv = behaviour.filters;
        if (!player.getWorld().isClient) behaviour.pipe.getHolder().onPlayerOpen(player);
        addProperties(new PropertyDelegate() {
            public int size() { return 3; }
            public int get(int index) {
                return switch (index) { case 0 -> behaviour.filterMode.ordinal(); case 1 -> behaviour.currentFilter; default -> behaviour.filterValid ? 1 : 0; };
            }
            public void set(int index, int value) {
                switch (index) {
                    case 0 -> behaviour.filterMode = FilterMode.get(value);
                    case 1 -> behaviour.currentFilter = Math.floorMod(value, filterInv.getSlots());
                    case 2 -> behaviour.filterValid = value != 0;
                }
            }
        });

        addFullPlayerInventory(79);

        for (int i = 0; i < 9; i++) {
            addSlot(new SlotPhantom(filterInv, i, 8 + i * 18, 18));
        }
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        behaviour.pipe.getHolder().onPlayerClose(player);
    }

    @Override public boolean onButtonClick(PlayerEntity player, int id) {
        if (player.getWorld().isClient || !canUse(player) || id < 0 || id >= FilterMode.values().length) return false;
        var mode = FilterMode.get(id);
        if (mode == FilterMode.ROUND_ROBIN && !(behaviour.pipe.getFlow() instanceof IFlowItems)) return false;
        behaviour.setFilterMode(mode);
        sendContentUpdates();
        return true;
    }
}
