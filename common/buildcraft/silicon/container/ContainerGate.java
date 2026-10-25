/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0
 * Native gate controls. Remake by FelixMantra.
 */
package buildcraft.silicon.container;

import java.util.List;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import buildcraft.lib.gui.ContainerBC_Neptune;
import buildcraft.lib.net.PacketBufferBC;
import buildcraft.lib.statement.*;
import buildcraft.api.statements.StatementMouseClick;
import buildcraft.silicon.BCSiliconScreens;
import buildcraft.silicon.gate.*;
import buildcraft.silicon.plug.PluggableGate;

/** Only indexes and mouse gestures cross from client to server; statements and samples come from server state. */
public class ContainerGate extends ContainerBC_Neptune {
    private static final buildcraft.lib.misc.data.IdAllocator GATE_IDS = IDS.makeChild("gate");
    public static final int NET_EDIT = GATE_IDS.allocId("EDIT");
    public static final int SELECT_BASE = 100, PARAM_BASE = 20000, CHOICES = 1024;
    public final PluggableGate gate;
    private final buildcraft.api.transport.pipe.IPipe openedPipe;
    public List<TriggerWrapper> triggers = List.of();
    public List<ActionWrapper> actions = List.of();
    public final int rows, columnWidth, panelWidth, inventoryY;

    public ContainerGate(PlayerEntity player, int syncId, PluggableGate gate) {
        super(BCSiliconScreens.GATE, player, syncId);
        this.gate = gate;
        openedPipe = gate.holder.getPipe();
        var variant = gate.logic.variant;
        rows = Math.min(4, variant.numSlots);
        columnWidth = 18 * (2 + variant.numTriggerArgs + variant.numActionArgs) + 20;
        panelWidth = Math.max(176, 16 + columnWidth * (variant.numSlots > 4 ? 2 : 1));
        inventoryY = 46 + rows * 22;
        addFullPlayerInventory((panelWidth - 162) / 2, inventoryY);
        if (!player.getWorld().isClient) {
            triggers = List.copyOf(gate.logic.getAllValidTriggers());
            actions = List.copyOf(gate.logic.getAllValidActions());
            if (triggers.size() >= CHOICES || actions.size() >= CHOICES) throw new IllegalStateException("Too many gate statements");
            gate.holder.onPlayerOpen(player);
        }
    }

    @Override public boolean canUse(PlayerEntity user) {
        return openedPipe != null && gate.holder.getPipe() == openedPipe && gate.holder.getPluggable(gate.side) == gate
            && gate.holder.canPlayerInteract(user);
    }

    public void writeOpeningData(PacketByteBuf data) {
        data.writeNbt(gate.logic.writeToNbt());
        for (int row = 0; row < gate.logic.statements.length; row++) data.writeByte((gate.logic.triggerOn[row] ? 1 : 0) | (gate.logic.actionOn[row] ? 2 : 0));
        data.writeBoolean(gate.logic.isOn);
        var buffer = PacketBufferBC.asPacketBufferBc(data);
        buffer.writeVarInt(triggers.size());
        for (var trigger : triggers) TriggerType.INSTANCE.writeToBuffer(buffer, trigger);
        buffer.writeVarInt(actions.size());
        for (var action : actions) ActionType.INSTANCE.writeToBuffer(buffer, action);
    }

    public void readOpeningData(PacketByteBuf data) {
        var tag = data.readNbt();
        if (tag == null || !new GateVariant(tag.getCompound("variant")).equals(gate.logic.variant))
            throw new IllegalArgumentException("Gate variant changed while opening screen");
        gate.logic.readConfigData(tag);
        for (int row = 0; row < gate.logic.statements.length; row++) {
            int state = data.readUnsignedByte();
            gate.logic.triggerOn[row] = (state & 1) != 0;
            gate.logic.actionOn[row] = (state & 2) != 0;
        }
        gate.logic.isOn = data.readBoolean();
        var buffer = PacketBufferBC.asPacketBufferBc(data);
        try {
            int count = boundedCount(buffer);
            var triggers = new java.util.ArrayList<TriggerWrapper>(count);
            for (int i = 0; i < count; i++) triggers.add(TriggerType.INSTANCE.readFromBuffer(buffer));
            this.triggers = List.copyOf(triggers);
            count = boundedCount(buffer);
            var actions = new java.util.ArrayList<ActionWrapper>(count);
            for (int i = 0; i < count; i++) actions.add(ActionType.INSTANCE.readFromBuffer(buffer));
            this.actions = List.copyOf(actions);
        } catch (java.io.IOException e) { throw new IllegalArgumentException("Invalid gate choices", e); }
    }
    private static int boundedCount(PacketByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count >= CHOICES) throw new IllegalArgumentException("Invalid gate choice count");
        return count;
    }
    public List<? extends StatementWrapper> choices(boolean action) { return action ? actions : triggers; }
    public FullStatement<? extends StatementWrapper> statement(int row, boolean action) {
        return action ? gate.logic.statements[row].action : gate.logic.statements[row].trigger;
    }
    public static int selectionId(int row, boolean action, int choice) {
        return SELECT_BASE + (row * 2 + (action ? 1 : 0)) * CHOICES + choice + 1;
    }
    public static int parameterId(int row, boolean action, int param, int button, boolean shift) {
        return PARAM_BASE + ((row * 2 + (action ? 1 : 0)) * 3 + param) * 6 + button * 2 + (shift ? 1 : 0);
    }

    @Override public buildcraft.lib.misc.data.IdAllocator getIdAllocator() { return GATE_IDS; }
    @Override public void readMessage(int id, PacketBufferBC buffer, boolean isClient, Object context) throws java.io.IOException {
        if (id == NET_EDIT) {
            if (!isClient && canUse(player)) onButtonClick(player, buffer.readVarInt());
        } else super.readMessage(id, buffer, isClient, context);
    }

    @Override public boolean onButtonClick(PlayerEntity user, int id) {
        if (player.getWorld().isClient || !canUse(user) || id < 0) return false;
        if (id < gate.logic.connections.length) {
            gate.logic.connections[id] = !gate.logic.connections[id];
            gate.holder.getPipeTile().markDirty();
            gate.logic.sendResolveData();
            return true;
        }
        if (id >= SELECT_BASE && id < SELECT_BASE + 16 * CHOICES) {
            int encoded = id - SELECT_BASE, row = encoded / CHOICES / 2, choice = encoded % CHOICES - 1;
            boolean action = (encoded / CHOICES % 2) != 0;
            if (row >= gate.logic.statements.length || choice >= choices(action).size()) return false;
            if (action) {
                var selected = choice < 0 ? null : actions.get(choice);
                if (selected != null && !gate.logic.getAllValidActions().contains(selected)) return false;
                gate.logic.statements[row].action.set(selected);
            } else {
                var selected = choice < 0 ? null : triggers.get(choice);
                if (selected != null && !gate.logic.getAllValidTriggers().contains(selected)) return false;
                gate.logic.statements[row].trigger.set(selected);
            }
            statement(row, action).postSetFromGui(-1);
            return true;
        }
        if (id >= PARAM_BASE && id < PARAM_BASE + 16 * 3 * 6) {
            int encoded = id - PARAM_BASE, cell = encoded / 6, row = cell / 3 / 2, param = cell % 3;
            boolean action = (cell / 3 % 2) != 0;
            if (row >= gate.logic.statements.length) return false;
            var full = statement(row, action);
            var statement = full.get();
            if (statement == null || param >= full.getParamCount() || param >= statement.maxParameters()) return false;
            var current = full.get(param);
            if (current == null) current = statement.createParameter(param);
            if (current == null) return false;
            var next = current.onClick(gate.logic, statement.delegate, getCursorStack().copy(),
                new StatementMouseClick(encoded % 6 / 2, encoded % 2 != 0));
            if (next == null) {
                var possible = current.getPossible(gate.logic);
                if (possible == null || possible.length == 0) return false;
                int index = java.util.Arrays.asList(possible).indexOf(current);
                next = possible[Math.floorMod(index + (encoded % 6 / 2 == 1 ? -1 : 1), possible.length)];
            }
            if (!full.canSet(param, next)) return false;
            full.set(param, next);
            full.postSetFromGui(param);
            return true;
        }
        return false;
    }

    @Override public void onClosed(PlayerEntity user) {
        super.onClosed(user);
        if (!user.getWorld().isClient) gate.holder.onPlayerClose(user);
    }
}
