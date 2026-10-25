/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 *
 * Ported to Fabric 1.20.1 by R.Chen (https://github.com/MantraChen).
 */
package buildcraft.transport.wire;

import java.io.IOException;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

// Yarn 1.20.1 renames:
//   DyeColor → DyeColor   Direction → Direction   NbtCompound → NbtCompound
//   BlockEntity   → BlockEntity   world.isClient → world.isClient   getTileEntity → getBlockEntity
//   @Environment(EnvType.CLIENT) → @Environment(EnvType.CLIENT)   Side → EnvType   MessageContext → Object (stub)
//   DyeColor.byId(i) → DyeColor.byId(i)   color.getId() → color.getId()
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.Direction;

import buildcraft.api.transport.EnumWirePart;
import buildcraft.api.transport.IWireManager;
import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.IPipeHolder;

import buildcraft.lib.net.PacketBufferBC;
import buildcraft.lib.tile.TileBC_Neptune.NetSide;

public class WireManager implements IWireManager {
    private final IPipeHolder holder;
    public final Map<EnumWirePart, DyeColor> parts = new EnumMap<>(EnumWirePart.class);
    public final Set<EnumWirePart> poweredClient = EnumSet.noneOf(EnumWirePart.class);
    public final Map<EnumWireBetween, DyeColor> betweens = new EnumMap<>(EnumWireBetween.class);
    public boolean initialised = false;
    // TODO: Wire connections to adjacent blocks

    public WireManager(IPipeHolder holder) {
        this.holder = holder;
    }

    public WorldSavedDataWireSystems getWireSystems() {
        return WorldSavedDataWireSystems.get(holder.getPipeWorld());
    }

    @Override
    public IPipeHolder getHolder() {
        return holder;
    }

    public void invalidate() {
        if (holder.getPipeWorld() != null && !holder.getPipeWorld().isClient) getWireSystems().unregister(holder);
        initialised = false;
    }

    public void validate() { initialised = false; }

    public void tick() {
        if (!initialised && holder.getPipeWorld() != null) {
            initialised = true;
            if (!holder.getPipeWorld().isClient) getWireSystems().register(holder);
            updateBetweens(false);
        }
    }

    private void changed() {
        if (holder.getPipeWorld() == null) return;
        if (!holder.getPipeWorld().isClient) {
            getWireSystems().register(holder);
            getWireSystems().markStructureChanged();
            holder.getPipeTile().markDirty();
            holder.scheduleNetworkUpdate(IPipeHolder.PipeMessageReceiver.WIRES);
        }
        updateBetweens(false);
    }

    @Override public boolean addPart(EnumWirePart part, DyeColor colour) {
        if (part == null || colour == null || parts.containsKey(part)) return false;
        parts.put(part, colour); changed(); return true;
    }
    @Override public DyeColor removePart(EnumWirePart part) {
        DyeColor color = parts.remove(part);
        if (color != null) changed();
        return color;
    }
    public void removeParts(Collection<EnumWirePart> toRemove) {
        if (parts.keySet().removeAll(toRemove)) changed();
    }

    private final Set<EnumWirePart> lastPowered = EnumSet.noneOf(EnumWirePart.class);
    public void syncPower() {
        Set<EnumWirePart> powered = EnumSet.noneOf(EnumWirePart.class);
        for (var part : parts.keySet()) if (isPowered(part)) powered.add(part);
        if (!lastPowered.equals(powered)) {
            lastPowered.clear(); lastPowered.addAll(powered);
            holder.scheduleNetworkUpdate(IPipeHolder.PipeMessageReceiver.WIRES);
        }
    }

    @Override
    public void updateBetweens(boolean recursive) {
        var previous = new java.util.HashMap<>(betweens);
        betweens.clear();
        if (holder.getPipeWorld() == null) return;
        parts.forEach((part, color) -> {
            for (EnumWireBetween between : EnumWireBetween.VALUES) {
                EnumWirePart[] betweenParts = between.parts;
                if (between.to == null) {
                    if ((betweenParts[0] == part && getColorOfPart(betweenParts[1]) == color)
                        || (betweenParts[1] == part && getColorOfPart(betweenParts[0]) == color)) {
                        betweens.put(between, color);
                    }
                } else if (WireSystem.canWireConnect(holder, between.to)) {
                    IPipe pipe = holder.getNeighbourPipe(between.to);
                    if (pipe != null) {
                        IWireManager wireManager = pipe.getHolder().getWireManager();
                        if (betweenParts[0] == part && wireManager.getColorOfPart(betweenParts[1]) == color) {
                            betweens.put(between, color);
                        }
                    }
                }
            }
        });

        if (!previous.equals(betweens)) {
            if (holder.getPipeWorld().isClient) holder.scheduleRenderUpdate();
            else holder.scheduleNetworkUpdate(IPipeHolder.PipeMessageReceiver.WIRES);
        }

        if (!recursive) {
            for (Direction side : Direction.values()) {
                BlockEntity tile = holder.getPipeWorld().isChunkLoaded(holder.getPipePos().offset(side))
                    ? holder.getPipeWorld().getBlockEntity(holder.getPipePos().offset(side)) : null;
                if (tile instanceof IPipeHolder) {
                    ((IPipeHolder) tile).getWireManager().updateBetweens(true);
                }
            }
        }
    }

    @Override
    public DyeColor getColorOfPart(EnumWirePart part) {
        return parts.get(part);
    }

    @Override
    public boolean hasPartOfColor(DyeColor color) {
        return parts.values().contains(color);
    }

    @Override
    public boolean isPowered(EnumWirePart part) {
        if (holder.getPipeWorld() == null || holder.getPipeWorld().isClient) {
            return poweredClient.contains(part);
        } else {
            WorldSavedDataWireSystems wireSystems = this.getWireSystems();
            List<WireSystem> wireSystemsWithElement = wireSystems.getWireSystemsWithElementAsReadOnlyList(new WireSystem.WireElement(holder.getPipePos(), part));
            if (!wireSystemsWithElement.isEmpty()) {
                for (WireSystem wireSystem : wireSystemsWithElement) {
                    Boolean powered = wireSystems.wireSystems.get(wireSystem);
                    if (powered != null && powered) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    @Override
    public boolean isAnyPowered(DyeColor color) {
        if (!this.parts.isEmpty()) {
            for (Map.Entry<EnumWirePart, DyeColor> partColor : this.parts.entrySet()) {
                if (partColor.getValue() == color && this.isPowered(partColor.getKey())) {
                    return true;
                }
            }
        }
        return false;
    }

    public NbtCompound writeToNbt() {
        NbtCompound nbt = new NbtCompound();
        int[] wiresArray = new int[parts.size() * 2];
        int[] i = { 0 };
        parts.forEach((part, color) -> {
            wiresArray[i[0]] = part.ordinal();
            wiresArray[i[0] + 1] = color.getId();
            i[0] += 2;
        });
        nbt.putIntArray("parts", wiresArray);
        return nbt;
    }

    public void readFromNbt(NbtCompound nbt) {
        parts.clear(); poweredClient.clear(); lastPowered.clear(); initialised = false;
        int[] wiresArray = nbt.getIntArray("parts");
        for (int i = 0; i + 1 < wiresArray.length; i += 2) {
            int part = wiresArray[i], color = wiresArray[i+1];
            if (part >= 0 && part < EnumWirePart.VALUES.length && color >= 0 && color < 16)
                parts.put(EnumWirePart.VALUES[part], DyeColor.byId(color));
        }
        if (holder.getPipeWorld() != null && !holder.getPipeWorld().isClient) getWireSystems().markStructureChanged();
    }

    public void writePayload(PacketBufferBC buffer, EnvType side) {
        if (side != EnvType.SERVER) return;
        buffer.writeByte(parts.size());
        int powered = 0;
        for (var entry : parts.entrySet()) {
            buffer.writeByte(entry.getKey().ordinal() << 4 | entry.getValue().getId());
            if (isPowered(entry.getKey())) powered |= 1 << entry.getKey().ordinal();
        }
        buffer.writeByte(powered);
    }

    public void readPayload(PacketBufferBC buffer, EnvType side, Object ctx) throws IOException {
        if (side != EnvType.CLIENT) return;
        var received = new EnumMap<EnumWirePart,DyeColor>(EnumWirePart.class);
        int count = buffer.readUnsignedByte();
        if (count > 8) throw new IOException("Too many pipe wires");
        for (int i = 0; i < count; i++) {
            int packed = buffer.readUnsignedByte(), ordinal = packed >> 4;
            if (ordinal >= 8 || received.put(EnumWirePart.VALUES[ordinal],DyeColor.byId(packed & 15)) != null)
                throw new IOException("Invalid or duplicate pipe wire part");
        }
        int powered = buffer.readUnsignedByte();
        parts.clear(); parts.putAll(received); poweredClient.clear();
        for (var part : parts.keySet()) if ((powered & 1 << part.ordinal()) != 0) poweredClient.add(part);
        updateBetweens(false);
    }
}
