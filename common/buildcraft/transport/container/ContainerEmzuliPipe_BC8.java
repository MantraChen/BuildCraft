/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * SPDX-License-Identifier: MPL-2.0
 * Ported to Fabric 1.20.1 by R.Chen (https://github.com/MantraChen).
 */
package buildcraft.transport.container;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.util.DyeColor;
import buildcraft.lib.gui.ContainerPipe;
import buildcraft.lib.gui.slot.SlotPhantom;
import buildcraft.transport.BCTransportScreens;
import buildcraft.transport.pipe.behaviour.PipeBehaviourEmzuli;
import buildcraft.transport.pipe.behaviour.PipeBehaviourEmzuli.SlotIndex;

/** Native paint controls with server-authoritative preset status. Remake by FelixMantra. */
public class ContainerEmzuliPipe_BC8 extends ContainerPipe {
    public final PipeBehaviourEmzuli behaviour;
    private final int[] clientState = new int[6];
    private final PropertyDelegate properties = new PropertyDelegate() {
        public int size() { return 6; }
        public int get(int index) {
            if (player.getWorld().isClient) return clientState[index];
            if (index < 4) {
                var colour = behaviour.slotColours.get(SlotIndex.VALUES[index]);
                return colour == null ? 0 : colour.getId() + 1;
            }
            if (index == 4) return behaviour.activeMask();
            var current = behaviour.getCurrentSlot();
            return current == null ? 0 : current.ordinal() + 1;
        }
        public void set(int index, int value) { clientState[index] = value; }
    };

    public ContainerEmzuliPipe_BC8(PlayerEntity player, int syncId, PipeBehaviourEmzuli behaviour) {
        super(BCTransportScreens.EMZULI, player, syncId, behaviour.pipe.getHolder());
        this.behaviour = behaviour;
        if (!player.getWorld().isClient) behaviour.pipe.getHolder().onPlayerOpen(player);
        addProperties(properties);
        addFullPlayerInventory(84);
        addSlot(new SlotPhantom(behaviour.invFilters, 0, 25, 21));
        addSlot(new SlotPhantom(behaviour.invFilters, 1, 25, 49));
        addSlot(new SlotPhantom(behaviour.invFilters, 2, 134, 21));
        addSlot(new SlotPhantom(behaviour.invFilters, 3, 134, 49));
    }

    public DyeColor getColour(SlotIndex slot) {
        int value = properties.get(slot.ordinal());
        return value > 0 && value <= 16 ? DyeColor.byId(value - 1) : null;
    }
    public boolean isActive(SlotIndex slot) { return (properties.get(4) & 1 << slot.ordinal()) != 0; }
    public boolean isCurrent(SlotIndex slot) { return properties.get(5) == slot.ordinal() + 1; }

    @Override public boolean onButtonClick(PlayerEntity player, int id) {
        if (player.getWorld().isClient || !canUse(player) || id < 0 || id >= 4 * 17) return false;
        int colour = id % 17;
        behaviour.setColour(SlotIndex.VALUES[id / 17], colour == 0 ? null : DyeColor.byId(colour - 1));
        sendContentUpdates();
        return true;
    }

    @Override public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        behaviour.pipe.getHolder().onPlayerClose(player);
    }
}
