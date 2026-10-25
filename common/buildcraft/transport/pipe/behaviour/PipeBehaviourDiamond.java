/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 *
 * Ported to Fabric 1.20.1 by R.Chen (https://github.com/MantraChen).
 */

package buildcraft.transport.pipe.behaviour;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Direction;

import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.PipeBehaviour;

import buildcraft.lib.tile.item.ItemHandlerSimple;

public abstract class PipeBehaviourDiamond extends PipeBehaviour {

    public static final int FILTERS_PER_SIDE = 9;

    public static final Identifier ADVANCEMENT_NEED_LIST = new Identifier("buildcrafttransport", "too_many_pipe_filters");
    public final ItemHandlerSimple filters = new ItemHandlerSimple(FILTERS_PER_SIDE * 6, this::onFilterSlotChange);

    public PipeBehaviourDiamond(IPipe pipe) {
        super(pipe);
    }

    public PipeBehaviourDiamond(IPipe pipe, NbtCompound nbt) {
        super(pipe, nbt);
        filters.deserializeNBT(nbt.getCompound("filters"));
    }

    @Override public NbtCompound writeToNbt() {
        var nbt = super.writeToNbt();
        nbt.put("filters", filters.serializeNBT());
        return nbt;
    }

    protected void onFilterSlotChange(Object handler, int slot, ItemStack before, ItemStack after) {
        if (!(pipe.getHolder().getPipeWorld() instanceof ServerWorld world)) return;
        world.markDirty(pipe.getHolder().getPipePos());
        int first = slot / FILTERS_PER_SIDE * FILTERS_PER_SIDE, used = 0;
        for (int i = first; i < first + FILTERS_PER_SIDE; i++) if (!filters.getStackInSlot(i).isEmpty()) used++;
        if (used >= FILTERS_PER_SIDE - 2) {
            var owner = pipe.getHolder().getOwner();
            var player = owner == null || owner.getId() == null ? null : world.getServer().getPlayerManager().getPlayer(owner.getId());
            if (player != null) buildcraft.lib.misc.AdvancementUtil.unlockAdvancement(player, ADVANCEMENT_NEED_LIST);
        }
    }

    @Override public int getTextureIndex(Direction face) { return face == null ? 0 : face.ordinal() + 1; }

    @Override public boolean onPipeActivate(PlayerEntity player, HitResult trace, float x, float y, float z,
            buildcraft.api.core.EnumPipePart part) {
        if (!pipe.getHolder().canPlayerInteract(player)) return false;
        if (!player.getWorld().isClient) buildcraft.transport.BCTransportGuis.PIPE_DIAMOND.openGui(player, pipe.getHolder().getPipePos());
        return true;
    }
}
