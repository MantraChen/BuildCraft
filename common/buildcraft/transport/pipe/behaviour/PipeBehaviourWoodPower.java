/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 *
 * Ported to Fabric 1.20.1 by R.Chen (https://github.com/MantraChen).
 */

package buildcraft.transport.pipe.behaviour;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.Direction;

import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.PipeBehaviour;

public class PipeBehaviourWoodPower extends PipeBehaviour {

    public PipeBehaviourWoodPower(IPipe pipe) {
        super(pipe);
    }

    public PipeBehaviourWoodPower(IPipe pipe, NbtCompound nbt) {
        super(pipe, nbt);
    }

    @Override
    public boolean canConnect(Direction face, PipeBehaviour other) {
        return !(other instanceof PipeBehaviourWoodPower);
    }

    @Override
    public int getTextureIndex(Direction face) {
        if (face == null || !pipe.isConnected(face) || pipe.getConnectedPipe(face) != null) return 0;
        var world = pipe.getHolder().getPipeWorld(); var pos = pipe.getHolder().getPipePos().offset(face);
        if (world == null || !world.isChunkLoaded(pos)) return 0;
        if (pipe.getFlow() instanceof buildcraft.transport.pipe.flow.PipeFlowRedstoneFlux) {
            var storage = team.reborn.energy.api.EnergyStorage.SIDED.find(world, pos, face.getOpposite());
            return storage != null && storage.supportsInsertion() ? 0 : 1;
        }
        var connector = buildcraft.api.mj.MjAPI.CONNECTOR.find(world, pos, face.getOpposite());
        return connector instanceof buildcraft.api.mj.IMjReceiver receiver && receiver.canReceive() ? 0 : 1;
    }
}
