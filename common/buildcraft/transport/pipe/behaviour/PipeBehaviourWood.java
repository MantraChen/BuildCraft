/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 *
 * Ported to Fabric 1.20.1 by R.Chen (https://github.com/MantraChen).
 */

package buildcraft.transport.pipe.behaviour;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.Direction;

import buildcraft.api.mj.IMjConnector;
import buildcraft.api.mj.IMjRedstoneReceiver;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.tiles.IDebuggable;
import buildcraft.api.transport.pipe.IFlowFluid;
import buildcraft.api.transport.pipe.IFlowItems;
import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.IPipe.ConnectedType;
import buildcraft.api.transport.pipe.PipeBehaviour;
import buildcraft.api.transport.pipe.PipeEventFluid;
import buildcraft.api.transport.pipe.PipeEventHandler;
import buildcraft.api.transport.pipe.PipeFaceTex;

// BCTransportConfig inlined below — BCTransportConfig has Forge deps and is not yet in libLeaf.

public class PipeBehaviourWood extends PipeBehaviourDirectional implements IMjRedstoneReceiver, IDebuggable {

    private static final PipeFaceTex TEX_CLEAR = PipeFaceTex.get(0);
    private static final PipeFaceTex TEX_FILLED = PipeFaceTex.get(1);

    public PipeBehaviourWood(IPipe pipe) {
        super(pipe);
    }

    public PipeBehaviourWood(IPipe pipe, NbtCompound nbt) {
        super(pipe, nbt);
    }

    @Override
    public PipeFaceTex getTextureData(Direction face) {
        return (face != null && face == getCurrentDir()) ? TEX_FILLED : TEX_CLEAR;
    }

    @Override
    public boolean canConnect(Direction face, PipeBehaviour other) {
        return !(other instanceof PipeBehaviourWood);
    }

    @Override
    protected boolean canFaceDirection(Direction dir) {
        return dir != null && pipe.getConnectedType(dir) == ConnectedType.TILE;
    }

    @PipeEventHandler
    public void fluidSideCheck(PipeEventFluid.SideCheck sideCheck) {
        if (currentDir.face != null) {
            sideCheck.disallow(currentDir.face);
        }
    }

    protected long extract(long power, boolean simulate) {
        if (power > 0) {
            long itemCost=buildcraft.transport.BCTransportConfig.itemExtractionCost();
            long fluidCost=buildcraft.transport.BCTransportConfig.millibucketExtractionCost();
            if (pipe.getFlow() instanceof IFlowItems) {
                IFlowItems flow = (IFlowItems) pipe.getFlow();
                int maxItems = (int)Math.min(64,power/itemCost);
                if (maxItems > 0) {
                    int extracted = extractItems(flow, getCurrentDir(), maxItems, simulate);
                    if (extracted > 0) {
                        return power - extracted * itemCost;
                    }
                }
            } else if (pipe.getFlow() instanceof IFlowFluid) {
                // A bounded extraction request cannot overflow even for an oversized energy offer.
                long capped=Math.min(power,fluidCost*1000*1000);
                long droplets = capped / fluidCost * 81 + capped % fluidCost * 81 / fluidCost;
                long extracted = extractFluid((IFlowFluid) pipe.getFlow(), getCurrentDir(), droplets, simulate);
                return power - (extracted * fluidCost + 80) / 81;
            }
        }
        return power;
    }

    protected int extractItems(IFlowItems flow, Direction dir, int count, boolean simulate) {
        // StackFilter.ALL inlined — StackFilter (Forge TileEntityFurnace dep) not yet in libLeaf.
        return flow.tryExtractItems(count, dir, null, stack -> true, simulate);
    }

    protected long extractFluid(IFlowFluid flow, Direction dir, long droplets, boolean simulate) {
        return flow.tryExtractFluid(droplets, dir, null, simulate);
    }

    // IMjRedstoneReceiver

    @Override
    public IMjConnector getMjConnector(Direction facing) {
        return this;
    }

    @Override
    public boolean canConnect(@Nonnull IMjConnector other) {
        return true;
    }

    @Override
    public long getPowerRequested() {
        final long power = 512 * MjAPI.MJ;
        return power - extract(power, true);
    }

    @Override
    public long receivePower(long microJoules, boolean simulate) {
        return extract(microJoules, simulate);
    }

    @Override
    public void getDebugInfo(List<String> left, List<String> right, Direction side) {
        left.add("Facing = " + currentDir);
    }
}
