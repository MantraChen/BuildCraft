/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 *
 * Ported to Fabric 1.20.1 by R.Chen (https://github.com/MantraChen).
 */

package buildcraft.transport.pipe.behaviour;

import java.io.IOException;
import java.util.EnumMap;
import java.util.EnumSet;
import net.fabricmc.api.EnvType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.DyeColor;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Direction;
import buildcraft.api.core.EnumPipePart;
import buildcraft.api.transport.IItemPluggable;
import buildcraft.api.transport.pipe.*;
import buildcraft.api.transport.pipe.IPipeHolder.PipeMessageReceiver;
import buildcraft.lib.misc.EntityUtil;
import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.lib.misc.StackUtil;
import buildcraft.lib.net.PacketBufferBC;
import buildcraft.lib.tile.item.ItemHandlerSimple;
import buildcraft.transport.BCTransportGuis;
import buildcraft.transport.BCTransportStatements;
import buildcraft.transport.statements.ActionExtractionPreset;

/** Gate-selected item filters and paint. Remake by FelixMantra. */
public class PipeBehaviourEmzuli extends PipeBehaviourWood {

    public enum SlotIndex {
        SQUARE(DyeColor.RED),
        CIRCLE(DyeColor.GREEN),
        TRIANGLE(DyeColor.BLUE),
        CROSS(DyeColor.YELLOW);

        public static final SlotIndex[] VALUES = values();

        public final DyeColor colour;

        SlotIndex(DyeColor colour) {
            this.colour = colour;
        }

        public SlotIndex next() { return VALUES[(ordinal() + 1) % VALUES.length]; }
    }

    public final ItemHandlerSimple invFilters = new ItemHandlerSimple(4, this::filterChanged);
    public final EnumMap<SlotIndex, DyeColor> slotColours = new EnumMap<>(SlotIndex.class);
    private final EnumSet<SlotIndex> activeSlots = EnumSet.noneOf(SlotIndex.class);
    private final byte[] activatedTtl = new byte[SlotIndex.VALUES.length];
    private SlotIndex currentSlot;
    private record Cursor(SlotIndex slot) {}
    private final net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant<Cursor> cursorSnapshots=new net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant<>() {
        @Override protected Cursor createSnapshot(){return new Cursor(currentSlot);}
        @Override protected void readSnapshot(Cursor state){currentSlot=state.slot();}
        @Override protected void onFinalCommit(){changed();}
    };

    public PipeBehaviourEmzuli(IPipe pipe) {
        super(pipe);
    }

    public PipeBehaviourEmzuli(IPipe pipe, NbtCompound nbt) {
        super(pipe, nbt);
        invFilters.deserializeNBT(nbt.getCompound("Filters"));
        if (nbt.contains("activeSlots")) activeSlots.addAll(NBTUtilBC.readEnumSet(nbt.get("activeSlots"), SlotIndex.class));
        currentSlot = NBTUtilBC.readEnum(nbt.get("currentSlot"), SlotIndex.class);
        byte[] savedTtl = nbt.getByteArray("activeTtl");
        for (var slot : SlotIndex.VALUES) {
            int i = slot.ordinal(), colour = nbt.getByte("slotColors[" + i + "]");
            if (colour > 0 && colour <= 16) slotColours.put(slot, DyeColor.byId(colour - 1));
            if (activeSlots.contains(slot)) {
                activatedTtl[i] = (byte) (i < savedTtl.length ? Math.max(0, Math.min(2, savedTtl[i])) : 2);
                if (activatedTtl[i] == 0) activeSlots.remove(slot);
            }
        }
        if (!usable(currentSlot)) currentSlot = nextSlot(currentSlot);
    }

    @Override public NbtCompound writeToNbt() {
        var nbt = super.writeToNbt();
        nbt.put("Filters", invFilters.serializeNBT());
        nbt.put("activeSlots", NBTUtilBC.writeEnumSet(activeSlots, SlotIndex.class));
        nbt.put("currentSlot", NBTUtilBC.writeEnum(currentSlot));
        nbt.putByteArray("activeTtl", activatedTtl.clone());
        for (var slot : SlotIndex.VALUES) {
            var colour = slotColours.get(slot);
            nbt.putByte("slotColors[" + slot.ordinal() + "]", (byte) (colour == null ? 0 : colour.getId() + 1));
        }
        return nbt;
    }

    @Override public void writePayload(PacketByteBuf buffer, EnvType side) {
        var data = PacketBufferBC.asPacketBufferBc(buffer);
        super.writePayload(data, side);
        if (side == EnvType.SERVER) {
            for (var slot : SlotIndex.VALUES) {
                var colour = slotColours.get(slot);
                data.writeByte(colour == null ? 0 : colour.getId() + 1);
            }
            data.writeByte(activeMask());
            data.writeByte(currentSlot == null ? 0 : currentSlot.ordinal() + 1);
        }
    }

    @Override public void readPayload(PacketByteBuf buffer, EnvType side, Object ctx) throws IOException {
        var data = PacketBufferBC.asPacketBufferBc(buffer);
        super.readPayload(data, side, ctx);
        if (side == EnvType.CLIENT) {
            slotColours.clear();
            for (var slot : SlotIndex.VALUES) {
                int colour = data.readUnsignedByte();
                if (colour > 16) throw new IOException("Invalid Emzuli paint: " + colour);
                if (colour > 0) slotColours.put(slot, DyeColor.byId(colour - 1));
            }
            int mask = data.readUnsignedByte(), current = data.readUnsignedByte();
            if (mask > 15 || current > 4) throw new IOException("Invalid Emzuli slot state");
            activeSlots.clear();
            for (var slot : SlotIndex.VALUES) if ((mask & 1 << slot.ordinal()) != 0) activeSlots.add(slot);
            currentSlot = current == 0 ? null : SlotIndex.VALUES[current - 1];
        }
    }

    private boolean usable(SlotIndex slot) {
        return slot != null && activeSlots.contains(slot) && !invFilters.getStackInSlot(slot.ordinal()).isEmpty();
    }

    private SlotIndex nextSlot(SlotIndex after) {
        var slot = after == null ? SlotIndex.CROSS : after;
        for (int i = 0; i < SlotIndex.VALUES.length; i++) {
            slot = slot.next();
            if (usable(slot)) return slot;
        }
        return null;
    }

    @Override protected int extractItems(IFlowItems flow, Direction dir, int count, boolean simulate) {
        var selected = usable(currentSlot) ? currentSlot : nextSlot(currentSlot);
        if (selected == null) return 0;
        var filter = invFilters.getStackInSlot(selected.ordinal());
        int extracted = flow.tryExtractItems(count, dir, slotColours.get(selected), stack -> StackUtil.matchesStackOrList(filter, stack), simulate);
        if (extracted > 0 && !simulate) {
            var transaction=net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.getCurrentUnsafe();
            if(transaction!=null)cursorSnapshots.updateSnapshots(transaction);
            currentSlot = nextSlot(selected);
            if(transaction==null)changed();
        }
        return extracted;
    }

    @Override public void onTick() {
        super.onTick();
        if (pipe.getHolder().getPipeWorld().isClient) return;
        boolean expired = false;
        for (var slot : SlotIndex.VALUES) {
            int i = slot.ordinal();
            if (activatedTtl[i] > 0) activatedTtl[i]--;
            if (activatedTtl[i] == 0) expired |= activeSlots.remove(slot);
        }
        if (!usable(currentSlot)) currentSlot = nextSlot(currentSlot);
        if (expired) changed();
    }

    @PipeEventHandler public void addExtractionActions(PipeEventStatement.AddActionInternal event) {
        java.util.Collections.addAll(event.actions, BCTransportStatements.ACTION_EXTRACTION_PRESET);
    }

    @PipeEventHandler public void onExtractionAction(PipeEventActionActivate event) {
        if (pipe.getHolder().getPipeWorld().isClient || !(event.action instanceof ActionExtractionPreset preset)) return;
        boolean added = activeSlots.add(preset.index);
        activatedTtl[preset.index.ordinal()] = 2;
        var previous = currentSlot;
        if (!usable(currentSlot)) currentSlot = nextSlot(currentSlot);
        if (added || previous != currentSlot) changed();
    }

    public SlotIndex getCurrentSlot() { return currentSlot; }
    public EnumSet<SlotIndex> getActiveSlots() { return activeSlots.clone(); }
    public int activeMask() { int mask = 0; for (var slot : activeSlots) mask |= 1 << slot.ordinal(); return mask; }

    public void setColour(SlotIndex slot, DyeColor colour) {
        if (slotColours.get(slot) == colour) return;
        if (colour == null) slotColours.remove(slot); else slotColours.put(slot, colour);
        changed();
    }

    private void filterChanged(Object handler, int slot, ItemStack before, ItemStack after) {
        if (!usable(currentSlot)) currentSlot = nextSlot(currentSlot);
        changed();
    }

    private void changed() {
        var world = pipe.getHolder().getPipeWorld();
        if (world != null && !world.isClient) {
            world.markDirty(pipe.getHolder().getPipePos());
            pipe.getHolder().scheduleNetworkGuiUpdate(PipeMessageReceiver.BEHAVIOUR);
        }
    }

    @Override public boolean onPipeActivate(PlayerEntity player, HitResult trace, float x, float y, float z, EnumPipePart part) {
        if (EntityUtil.getWrenchHand(player) != null) return super.onPipeActivate(player, trace, x, y, z, part);
        if (!pipe.getHolder().canPlayerInteract(player) || player.getMainHandStack().getItem() instanceof IItemPluggable) return false;
        if (!player.getWorld().isClient) BCTransportGuis.PIPE_EMZULI.openGui(player, pipe.getHolder().getPipePos());
        return true;
    }
}
