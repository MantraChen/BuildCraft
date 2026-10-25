/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 *
 * Ported to Fabric 1.20.1 by R.Chen (https://github.com/MantraChen).
 */

package buildcraft.transport.pipe.behaviour;

import java.io.IOException;
import net.fabricmc.api.EnvType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Direction;
import buildcraft.api.core.EnumPipePart;
import buildcraft.api.transport.IItemPluggable;
import buildcraft.api.transport.pipe.IFlowItems;
import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.IPipeHolder.PipeMessageReceiver;
import buildcraft.lib.misc.EntityUtil;
import buildcraft.lib.misc.StackUtil;
import buildcraft.lib.net.PacketBufferBC;
import buildcraft.lib.tile.item.ItemHandlerSimple;
import buildcraft.transport.BCTransportGuis;

/** Filtered, one-item-per-pulse extraction. Remake by FelixMantra. */
public class PipeBehaviourWoodDiamond extends PipeBehaviourWood {

    public enum FilterMode {
        WHITE_LIST, BLACK_LIST, ROUND_ROBIN;

        public static FilterMode get(int index) {
            FilterMode[] values = values();
            return (index >= 0 && index < values.length) ? values[index] : WHITE_LIST;
        }
    }

    public final ItemHandlerSimple filters = new ItemHandlerSimple(9, this::onSlotChanged);
    public FilterMode filterMode = FilterMode.WHITE_LIST;
    public int currentFilter;
    public boolean filterValid;
    private final net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant<Integer> cursorSnapshots=new net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant<>() {
        @Override protected Integer createSnapshot(){return currentFilter;}
        @Override protected void readSnapshot(Integer cursor){currentFilter=cursor;}
        @Override protected void onFinalCommit(){changed();}
    };

    public PipeBehaviourWoodDiamond(IPipe pipe) {
        super(pipe);
    }

    public PipeBehaviourWoodDiamond(IPipe pipe, NbtCompound nbt) {
        super(pipe, nbt);
        filters.deserializeNBT(nbt.getCompound("filters"));
        filterMode = FilterMode.get(nbt.getByte("mode"));
        currentFilter = Math.floorMod(nbt.getByte("currentFilter"), filters.getSlots());
        normalizeFilter();
    }

    @Override public NbtCompound writeToNbt() {
        var nbt = super.writeToNbt();
        nbt.put("filters", filters.serializeNBT());
        nbt.putByte("mode", (byte) filterMode.ordinal());
        nbt.putByte("currentFilter", (byte) currentFilter);
        return nbt;
    }

    @Override public void writePayload(PacketByteBuf buffer, EnvType side) {
        var data = PacketBufferBC.asPacketBufferBc(buffer);
        super.writePayload(data, side);
        if (side == EnvType.SERVER) {
            data.writeByte(filterMode.ordinal());
            data.writeByte(currentFilter);
            data.writeBoolean(filterValid);
        }
    }

    @Override public void readPayload(PacketByteBuf buffer, EnvType side, Object ctx) throws IOException {
        var data = PacketBufferBC.asPacketBufferBc(buffer);
        super.readPayload(data, side, ctx);
        if (side == EnvType.CLIENT) {
            filterMode = FilterMode.get(data.readUnsignedByte());
            currentFilter = data.readUnsignedByte() % filters.getSlots();
            filterValid = data.readBoolean();
        }
    }

    @Override public boolean onPipeActivate(PlayerEntity player, HitResult trace, float x, float y, float z, EnumPipePart part) {
        if (EntityUtil.getWrenchHand(player) != null) return super.onPipeActivate(player, trace, x, y, z, part);
        if (!pipe.getHolder().canPlayerInteract(player) || player.getMainHandStack().getItem() instanceof IItemPluggable) return false;
        if (!player.getWorld().isClient) BCTransportGuis.PIPE_DIAMOND_WOOD.openGui(player, pipe.getHolder().getPipePos());
        return true;
    }

    public void setFilterMode(FilterMode mode) {
        if (filterMode == mode) return;
        filterMode = mode;
        changed();
    }

    private void onSlotChanged(Object handler, int slot, ItemStack before, ItemStack after) {
        normalizeFilter();
        changed();
    }

    private void normalizeFilter() {
        if (filters.getStackInSlot(currentFilter).isEmpty()) currentFilter = nextFilter(currentFilter);
        filterValid = !filters.getStackInSlot(currentFilter).isEmpty();
    }

    private int nextFilter(int start) {
        for (int offset = 1; offset <= filters.getSlots(); offset++) {
            int slot = (start + offset) % filters.getSlots();
            if (!filters.getStackInSlot(slot).isEmpty()) return slot;
        }
        return start;
    }

    private boolean matches(ItemStack stack) {
        if (filterMode == FilterMode.ROUND_ROBIN) return StackUtil.matchesStackOrList(filters.getStackInSlot(currentFilter), stack);
        boolean any = false, match = false;
        for (var filter : filters.stacks) {
            if (filter.isEmpty()) continue;
            any = true;
            match |= StackUtil.matchesStackOrList(filter, stack);
        }
        return filterMode == FilterMode.WHITE_LIST ? !any || match : !match;
    }

    @Override protected int extractItems(IFlowItems flow, Direction dir, int count, boolean simulate) {
        // A power query must never change the round-robin cursor, inventory, or travelling items.
        int extracted = flow.tryExtractItems(1, dir, null, this::matches, simulate);
        if (extracted > 0 && !simulate && filterMode == FilterMode.ROUND_ROBIN) {
            var transaction=net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.getCurrentUnsafe();
            if(transaction!=null)cursorSnapshots.updateSnapshots(transaction);
            currentFilter = nextFilter(currentFilter);
            if(transaction==null)changed();
        }
        return extracted;
    }

    @Override protected long extractFluid(buildcraft.api.transport.pipe.IFlowFluid flow, Direction dir, long droplets, boolean simulate) {
        if (filterMode == FilterMode.ROUND_ROBIN) return 0;
        var samples = new java.util.HashSet<net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant>();
        for (var filter : filters.stacks) samples.addAll(FluidPipeFilters.samples(filter));
        return flow.tryExtractFluidAdv(droplets, dir, resource -> filterMode == FilterMode.WHITE_LIST
            ? samples.isEmpty() || samples.contains(resource) : !samples.contains(resource), simulate);
    }

    private void changed() {
        var world = pipe.getHolder().getPipeWorld();
        if (world != null && !world.isClient) {
            world.markDirty(pipe.getHolder().getPipePos());
            pipe.getHolder().scheduleNetworkGuiUpdate(PipeMessageReceiver.BEHAVIOUR);
        }
    }
}
