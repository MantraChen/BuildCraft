/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 *
 * Ported to Fabric 1.20.1 by R.Chen (https://github.com/MantraChen).
 */
package buildcraft.silicon.plug;

import java.io.IOException;

import net.fabricmc.api.EnvType;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.Box;

import buildcraft.api.transport.IWireEmitter;
import buildcraft.api.transport.pipe.IPipeHolder;
import buildcraft.api.transport.pipe.IPipeHolder.PipeMessageReceiver;
import buildcraft.api.transport.pluggable.PipePluggable;
import buildcraft.api.transport.pluggable.PluggableDefinition;

import buildcraft.lib.net.IPayloadWriter;
import buildcraft.lib.net.PacketBufferBC;

import buildcraft.silicon.gate.GateLogic;
import buildcraft.silicon.gate.GateVariant;
import buildcraft.transport.pipe.PluggableHolder;

/** Native gate attachment, persisted logic and server-validated screen. Remake by FelixMantra. */
public class PluggableGate extends PipePluggable implements IWireEmitter {

    public final GateLogic logic;

    // Manual constructor (called by the specific item pluggable gate code)
    public PluggableGate(PluggableDefinition def, IPipeHolder holder, net.minecraft.util.math.Direction side,
                         GateVariant variant) {
        super(def, holder, side);
        logic = new GateLogic(this, variant);
    }

    // Saving + Loading
    public PluggableGate(PluggableDefinition def, IPipeHolder holder, net.minecraft.util.math.Direction side,
                         NbtCompound nbt) {
        super(def, holder, side);
        logic = new GateLogic(this, nbt.getCompound("data"));
    }

    @Override
    public NbtCompound writeToNbt() {
        NbtCompound nbt = super.writeToNbt();
        nbt.put("data", logic.writeToNbt());
        return nbt;
    }

    // Networking
    public PluggableGate(PluggableDefinition def, IPipeHolder holder, net.minecraft.util.math.Direction side,
                         PacketByteBuf buffer) {
        super(def, holder, side);
        logic = new GateLogic(this, PacketBufferBC.asPacketBufferBc(buffer));
    }

    @Override
    public void writeCreationPayload(PacketByteBuf buffer) {
        logic.writeCreationToBuf(PacketBufferBC.asPacketBufferBc(buffer));
    }

    public void sendMessage(IPayloadWriter writer) {
        PipeMessageReceiver to = PipeMessageReceiver.PLUGGABLES[side.ordinal()];
        holder.sendMessage(to, (buffer) -> {
            /* The pluggable holder receives this message and requires the ID '1' (UPDATE) to forward the message onto
             * ourselves */
            buffer.writeByte(PluggableHolder.ID_UPDATE_PLUG);
            writer.write(PacketBufferBC.asPacketBufferBc(buffer));
        });
    }

    public void sendGuiMessage(IPayloadWriter writer) {
        PipeMessageReceiver to = PipeMessageReceiver.PLUGGABLES[side.ordinal()];
        holder.sendGuiMessage(to, (buffer) -> {
            /* The pluggable holder receives this message and requires the ID '1' (UPDATE) to forward the message onto
             * ourselves */
            buffer.writeByte(PluggableHolder.ID_UPDATE_PLUG);
            writer.write(PacketBufferBC.asPacketBufferBc(buffer));
        });
    }

    @Override
    public void writePayload(PacketByteBuf buffer, EnvType side) {
        throw new Error("All messages must have an ID, and we can't just write a payload directly!");
    }

    @Override
    public void readPayload(PacketByteBuf b, EnvType side, Object ctx) throws IOException {
        logic.readPayload(PacketBufferBC.asPacketBufferBc(b), side, ctx);
    }

    // PipePluggable

    @Override
    public Box getBoundingBox() {
        return switch (side) {
            case DOWN -> new Box(5/16.0, 2/16.0, 5/16.0, 11/16.0, 4/16.0, 11/16.0);
            case UP -> new Box(5/16.0, 12/16.0, 5/16.0, 11/16.0, 14/16.0, 11/16.0);
            case NORTH -> new Box(5/16.0, 5/16.0, 2/16.0, 11/16.0, 11/16.0, 4/16.0);
            case SOUTH -> new Box(5/16.0, 5/16.0, 12/16.0, 11/16.0, 11/16.0, 14/16.0);
            case WEST -> new Box(2/16.0, 5/16.0, 5/16.0, 4/16.0, 11/16.0, 11/16.0);
            case EAST -> new Box(12/16.0, 5/16.0, 5/16.0, 14/16.0, 11/16.0, 11/16.0);
        };
    }

    @Override public net.minecraft.item.ItemStack getPickStack() {
        return buildcraft.silicon.BCSiliconItems.plugGate.getStack(logic.variant);
    }

    @Override public boolean onPluggableActivate(net.minecraft.entity.player.PlayerEntity player, net.minecraft.util.hit.HitResult hit, float x, float y, float z) {
        if (!holder.canPlayerInteract(player)) return false;
        buildcraft.silicon.BCSiliconScreens.open(player, this);
        return true;
    }

    @Override
    public boolean isBlocking() {
        return true;
    }

    // IWireEmitter

    @Override
    public boolean isEmitting(DyeColor colour) {
        return logic.isEmitting(colour);
    }

    @Override
    public void emitWire(DyeColor colour) {
        logic.emitWire(colour);
    }

    // Gate tick

    @Override
    public void onTick() {
        logic.onTick();
    }
}
