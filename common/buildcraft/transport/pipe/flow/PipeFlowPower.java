/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 *
 * Ported to Fabric 1.20.1 by R.Chen (https://github.com/MantraChen).
 */

package buildcraft.transport.pipe.flow;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.function.ToLongFunction;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.fabricmc.api.EnvType;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Direction.AxisDirection;
import net.minecraft.util.math.Vec3d;

import buildcraft.api.core.EnumPipePart;
import buildcraft.api.mj.IMjConnector;
import buildcraft.api.mj.IMjReceiver;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.tiles.IDebuggable;
import buildcraft.api.transport.pipe.IFlowPower;
import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.IPipe.ConnectedType;
import buildcraft.api.transport.pipe.PipeApi;
import buildcraft.api.transport.pipe.PipeApi.PowerTransferInfo;
import buildcraft.api.transport.pipe.PipeEventPower;
import buildcraft.api.transport.pipe.PipeFlow;

import buildcraft.lib.misc.LocaleUtil;
import buildcraft.lib.misc.MathUtil;
import buildcraft.lib.misc.VecUtil;
import buildcraft.lib.misc.data.AverageInt;
import buildcraft.lib.net.PacketBufferBC;
import buildcraft.lib.tile.TileBC_Neptune.NetSide;

public class PipeFlowPower extends PipeFlow implements IFlowPower, IDebuggable {
    private static final long MAX_RATE = Long.MAX_VALUE / 32;
    public static final int NET_POWER_AMOUNTS = 2;

    public Vec3d clientDisplayFlowCentre = VecUtil.VEC_HALF;
    public Vec3d clientDisplayFlowCentreLast = VecUtil.VEC_HALF;
    public long clientLastDisplayTime = 0;

    private long maxPower = -1;
    private long powerLoss = -1;
    private long powerResistance = -1;
    private boolean disabled = false;

    private long currentWorldTime = Long.MIN_VALUE, lastTick = Long.MIN_VALUE;
    private long capacity;

    private boolean isReceiver = false;
    private final EnumMap<Direction, Section> sections;

    public PipeFlowPower(IPipe pipe) {
        super(pipe);
        sections = new EnumMap<>(Direction.class);
        for (Direction face : Direction.values()) {
            sections.put(face, new Section(face));
        }
    }

    public PipeFlowPower(IPipe pipe, NbtCompound nbt) {
        super(pipe, nbt);
        isReceiver = nbt.getBoolean("isReceiver");
        sections = new EnumMap<>(Direction.class);
        for (Direction face : Direction.values()) {
            Section section = new Section(face);
            sections.put(face, section);
            long limit = MathUtil.clamp(PipeApi.getPowerTransferInfo(pipe.getDefinition()).transferPerTick, 1, MAX_RATE);
            NbtCompound saved = nbt.getCompound(face.getName());
            section.internalPower = MathUtil.clamp(saved.getLong("stored"), 0, limit);
            section.internalNextPower = MathUtil.clamp(saved.getLong("incoming"), 0, limit - section.internalPower);
        }
    }

    @Override
    public NbtCompound writeToNbt() {
        NbtCompound nbt = super.writeToNbt();
        nbt.putBoolean("isReceiver", isReceiver);
        nbt.putInt("power_format", 1);
        for (Section section : sections.values()) {
            NbtCompound saved = new NbtCompound();
            saved.putLong("stored", section.internalPower);
            saved.putLong("incoming", section.internalNextPower);
            nbt.put(section.side.getName(), saved);
        }
        return nbt;
    }

    @Override
    public void writePayload(int id, PacketByteBuf bufIn, EnvType side) {
        super.writePayload(id, bufIn, side);
        PacketBufferBC buffer = PacketBufferBC.asPacketBufferBc(bufIn);
        if (side == EnvType.SERVER) {
            if (id == NET_POWER_AMOUNTS || id == NET_ID_FULL_STATE) {
                for (Direction face : Direction.values()) {
                    Section s = sections.get(face);
                    buffer.writeInt(s.displayPower);
                    buffer.writeEnumValue(s.displayFlow);
                }
            }
        }
    }

    @Override
    public void readPayload(int id, PacketByteBuf bufIn, EnvType side) throws IOException {
        super.readPayload(id, bufIn, side);
        PacketBufferBC buffer = PacketBufferBC.asPacketBufferBc(bufIn);
        if (side == EnvType.CLIENT) {
            if (id == NET_POWER_AMOUNTS || id == NET_ID_FULL_STATE) {
                int[] amounts = new int[6]; EnumFlow[] flows = new EnumFlow[6];
                for (int i = 0; i < 6; i++) {
                    amounts[i] = buffer.readInt();
                    int direction = buffer.readFixedBits(2);
                    if (amounts[i] < 0 || amounts[i] > MjAPI.MJ || direction >= EnumFlow.values().length) throw new IOException("Invalid MJ pipe snapshot");
                    flows[i] = EnumFlow.values()[direction];
                }
                for (Direction face : Direction.values()) {
                    Section s = sections.get(face);
                    s.displayPower = amounts[face.ordinal()]; s.displayFlow = flows[face.ordinal()];
                }
            }
        }
    }

    @Override
    public boolean canConnect(Direction face, PipeFlow other) {
        return other instanceof PipeFlowPower;
    }

    @Override
    public boolean canConnect(Direction face, BlockEntity oTile) {
        if (maxPower == -1) reconfigure();
        IMjConnector connector = neighbour(face);
        return connector != null && connector.canConnect(sections.get(face));
    }

    @Override
    public void reconfigure() {
        PipeEventPower.Configure configure = new PipeEventPower.Configure(pipe.getHolder(), this);
        PowerTransferInfo pti = PipeApi.getPowerTransferInfo(pipe.getDefinition());
        configure.setReceiver(pti.isReceiver);
        configure.setMaxPower(pti.transferPerTick);
        configure.setPowerLoss(pti.lossPerTick);
        configure.setPowerResistance(pti.resistancePerTick);
        pipe.getHolder().fireEvent(configure);
        isReceiver = configure.isReceiver();
        maxPower = MathUtil.clamp(configure.getMaxPower(), 1, MAX_RATE);
        capacity = MathUtil.clamp(pti.transferPerTick, 1, MAX_RATE);
        disabled = configure.isTransferDisabled();
        powerLoss = MathUtil.clamp(configure.getPowerLoss(), -1, capacity);
        powerResistance = MathUtil.clamp(configure.getPowerResistance(), -1, MjAPI.MJ);

        if (powerLoss < 0) {
            if (powerResistance < 0) {
                // 1% resistance
                powerResistance = MjAPI.MJ / 100;
            }
            powerLoss = multiplyDivide(capacity, powerResistance, MjAPI.MJ);
        } else if (powerResistance < 0) {
            powerResistance = multiplyDivide(powerLoss, MjAPI.MJ, capacity);
        }
    }

    @Override
    public long tryExtractPower(long maxExtracted, Direction from) {
        if (maxPower == -1) reconfigure();
        if (maxExtracted <= 0 || from == null || !server() || !isReceiver || disabled || !open(from) || !pipe.isConnected(from)) return 0;
        Section section = sections.get(from);
        IMjConnector connector = neighbour(from);
        if (!(connector instanceof buildcraft.api.mj.IMjPassiveProvider provider) || !provider.canConnect(section)) return 0;
        long limit = Math.min(maxExtracted, section.room());
        if (limit <= 0) return 0;
        long offered = MathUtil.clamp(provider.extractPower(0, limit, true), 0, limit);
        if (offered <= 0) return 0;
        long extracted = MathUtil.clamp(provider.extractPower(0, offered, false), 0, offered);
        section.receivePowerInternal(extracted);
        return extracted;
    }

    private static long multiplyDivide(long a, long b, long denominator) {
        return BigInteger.valueOf(a).multiply(BigInteger.valueOf(b)).divide(BigInteger.valueOf(denominator)).longValueExact();
    }
    private boolean live() {
        var holder = pipe.getHolder(); var world = holder.getPipeWorld();
        return world != null && !holder.getPipeTile().isRemoved() && world.isChunkLoaded(holder.getPipePos())
            && world.getBlockEntity(holder.getPipePos()) == holder.getPipeTile() && holder.getPipe() == pipe;
    }
    private boolean server() { return live() && !pipe.getHolder().getPipeWorld().isClient; }
    private boolean open(Direction side) {
        var plug = pipe.getHolder().getPluggable(side);
        return plug == null || !plug.isBlocking();
    }
    private IMjConnector neighbour(Direction side) {
        var world = pipe.getHolder().getPipeWorld(); var pos = pipe.getHolder().getPipePos().offset(side);
        return world != null && world.isChunkLoaded(pos) ? MjAPI.CONNECTOR.find(world, pos, side.getOpposite()) : null;
    }
    public long getStoredPower() {
        long total = 0; for (Section s : sections.values()) total += s.internalPower + s.internalNextPower;
        return total;
    }

    @Override
    public boolean onFlowActivate(PlayerEntity player, HitResult trace, float hitX, float hitY, float hitZ,
        EnumPipePart part) {
        return super.onFlowActivate(player, trace, hitX, hitY, hitZ, part);
    }

    public Section getSection(Direction side) {
        return sections.get(side);
    }

    @Override
    public IMjConnector getMjConnector(Direction facing) {
        if (maxPower == -1) reconfigure();
        return sections.get(facing);
    }

    @Override
    public void getDebugInfo(List<String> left, List<String> right, Direction side) {
        left.add("maxPower = " + LocaleUtil.localizeMj(maxPower));
        left.add("isReceiver = " + isReceiver);
        left.add(
            "internalPower = " + arrayToString(s -> s.internalPower) + " <- " + arrayToString(s -> s.internalNextPower)
        );
        left.add("- powerQuery: " + arrayToString(s -> s.powerQuery) + " <- " + arrayToString(s -> s.nextPowerQuery));
        left.add(
            "- power: IN " + arrayToString(s -> s.debugPowerInput) + ", OUT " + arrayToString(s -> s.debugPowerOutput)
        );
        left.add("- power: OFFERED " + arrayToString(s -> s.debugPowerOffered));
    }

    private String arrayToString(ToLongFunction<Section> getter) {
        long[] arr = new long[6];
        for (Direction face : Direction.values()) {
            arr[face.ordinal()] = getter.applyAsLong(sections.get(face)) / MjAPI.MJ;
        }
        return Arrays.toString(arr);
    }

    @Override
    public void onTick() {
        if (maxPower == -1) {
            reconfigure();
        }
        if (pipe.getHolder().getPipeWorld() == null) return;
        if (pipe.getHolder().getPipeWorld().isClient) {
            clientDisplayFlowCentreLast = clientDisplayFlowCentre;
            for (Direction face : Direction.values()) {
                Section s = sections.get(face);
                s.clientDisplayFlowLast = s.clientDisplayFlow;
                double diff = s.displayFlow.value * 2.4 * face.getDirection().offset();
                s.clientDisplayFlow += 16 + diff;
                s.clientDisplayFlow %= 16;

                double cVal = VecUtil.getValue(clientDisplayFlowCentre, face.getAxis());
                cVal += 16 + diff / 2;
                cVal %= 16;
                clientDisplayFlowCentre = VecUtil.replaceValue(clientDisplayFlowCentre, face.getAxis(), cVal);
            }
            return;
        }

        if (!server() || lastTick == pipe.getHolder().getPipeWorld().getTime()) return;
        lastTick = pipe.getHolder().getPipeWorld().getTime();
        long storedBefore = getStoredPower();

        EnumFlow[] lastFlows = new EnumFlow[6];
        int[] lastDisplayPower = new int[6];

        for (Direction face : Direction.values()) {
            Section s = sections.get(face);
            int i = face.ordinal();
            lastFlows[i] = s.displayFlow;
            lastDisplayPower[i] = s.displayPower;
            s.displayFlow = EnumFlow.STATIONARY;
        }

        step();

        if (!disabled) for (Section s : sections.values()) {
            long loss = switch (buildcraft.transport.BCTransportConfig.lossMode) {
                case LOSSLESS -> 0;
                case ABSOLUTE -> powerLoss;
                case PERCENTAGE -> multiplyDivide(s.internalPower, powerResistance, MjAPI.MJ);
            };
            s.internalPower -= Math.min(s.internalPower, loss);
        }

        for (Direction face : Direction.values()) {
            Section s = sections.get(face);
            if (!disabled && s.internalPower > 0) {
                long totalPowerQuery = 0;
                for (Direction face2 : Direction.values()) {
                    if (face != face2 && pipe.isConnected(face2)) {
                        totalPowerQuery += sections.get(face2).powerQuery;
                    }
                }

                boolean returnPower = false;
                if (totalPowerQuery <= 0 && s.powerQuery > 0 && pipe.isConnected(face)) {
                    totalPowerQuery = s.powerQuery;
                    returnPower = true;
                }

                if (totalPowerQuery > 0) {
                    long unusedPowerQuery = totalPowerQuery;
                    for (Direction face2 : Direction.values()) {
                        if (face == face2 && !returnPower) {
                            continue;
                        }
                        Section s2 = sections.get(face2);
                        if (s2.powerQuery > 0 && pipe.isConnected(face2)) {
                            long watts = Math.min(
                                BigInteger.valueOf(s.internalPower).multiply(BigInteger.valueOf(s2.powerQuery)).divide(
                                    BigInteger.valueOf(unusedPowerQuery)
                                ).longValue(), s.internalPower
                            );
                            unusedPowerQuery -= s2.powerQuery;
                            watts = Math.min(watts, Math.max(0, maxPower - s2.sent));
                            if (watts <= 0) continue;
                            IPipe neighbour = pipe.getConnectedPipe(face2);
                            long leftover = watts;
                            if (
                                neighbour != null && neighbour.getFlow() instanceof PipeFlowPower && neighbour
                                    .isConnected(face2.getOpposite())
                            ) {
                                PipeFlowPower oFlow = (PipeFlowPower) neighbour.getFlow();
                                leftover = oFlow.sections.get(face2.getOpposite()).receivePowerInternal(watts);
                            } else {
                                IMjReceiver receiver = getReceiver(face2);
                                if (receiver != null && receiver.canReceive()) {
                                    leftover = MathUtil.clamp(receiver.receivePower(watts, false), 0, watts);
                                }
                            }
                            long used = watts - leftover;
                            s.internalPower -= used;
                            s2.sent += used;
                            s2.debugPowerOutput += used;

                            s.powerAverage.push(used);
                            s2.powerAverage.push(used);

                            if (used > 0) { s.displayFlow = EnumFlow.OUT; s2.displayFlow = EnumFlow.IN; }
                        }
                    }
                }
            }
        }
        // Render compute goes here
        for (Section s : sections.values()) {
            s.powerAverage.tick();
            double value = Math.min(1, s.powerAverage.getAverage() / maxPower);
            value = Math.sqrt(value);
            s.displayPower = (int) (value * MjAPI.MJ);
        }

        // Compute the tiles requesting power that are not power pipes
        for (Direction face : Direction.values()) {
            if (disabled || pipe.getConnectedType(face) != ConnectedType.TILE) {
                continue;
            }
            IMjReceiver recv = getReceiver(face);
            if (recv != null && recv.canReceive()) {
                long requested = recv.getPowerRequested();
                if (requested > 0) {
                    requestPower(face, requested);
                }
            }
        }

        // Sum the amount of power requested on each side
        long[] transferQuery = new long[6];
        for (Direction face : Direction.values()) {
            if (!pipe.isConnected(face)) {
                continue;
            }
            long query = 0;
            for (Direction face2 : Direction.values()) {
                if (face != face2 && pipe.isConnected(face2)) {
                    query += sections.get(face2).powerQuery;
                }
            }
            transferQuery[face.ordinal()] = Math.min(Math.min(maxPower, query), sections.get(face).room());
        }

        // Transfer requested power to neighbouring pipes
        for (Direction face : Direction.values()) {
            if (disabled) {
                continue;
            }
            if (transferQuery[face.ordinal()] <= 0 || !pipe.isConnected(face)) {
                continue;
            }
            IPipe oPipe = pipe.getConnectedPipe(face);
            if (oPipe == null || !oPipe.isConnected(face.getOpposite()) || !(oPipe.getFlow() instanceof PipeFlowPower)) {
                continue;
            }
            PipeFlowPower oFlow = (PipeFlowPower) oPipe.getFlow();
            oFlow.requestPower(face.getOpposite(), transferQuery[face.ordinal()]);
        }
        // Networking
        boolean didChange = false;
        for (Direction face : Direction.values()) {
            Section s = sections.get(face);
            int i = face.ordinal();
            if (lastFlows[i] != s.displayFlow || lastDisplayPower[i] != s.displayPower) {
                didChange = true;
                break;
            }
        }

        // if (tracker.markTimeIfDelay(pipe.getHolder().getPipeWorld())) {
        if (didChange) {
            sendPayload(NET_POWER_AMOUNTS);
        }
        if (storedBefore != getStoredPower()) pipe.getHolder().getPipeTile().markDirty();

    }

    private IMjReceiver getReceiver(Direction side) {
        return MjAPI.getReceiver(sections.get(side), pipe.getHolder().getMjConnectorFromPipe(side));
    }

    private void step() {
        long now = pipe.getHolder().getPipeWorld().getTime();
        if (currentWorldTime != now) {
            currentWorldTime = now;
            sections.values().forEach(Section::step);
        }
    }

    private void requestPower(Direction from, long amount) {
        if (maxPower == -1) reconfigure();
        if (disabled || !server() || !open(from) || !pipe.isConnected(from) || amount <= 0) return;
        step();

        Section s = sections.get(from);
        if (pipe.getBehaviour() instanceof IPipeTransportPowerHook) {
            amount = ((IPipeTransportPowerHook) pipe.getBehaviour()).requestPower(from, amount);
        }
        s.nextPowerQuery += MathUtil.clamp(amount, 0, maxPower - s.nextPowerQuery);
    }

    public long getPowerRequested(@Nullable Direction side) {
        if (maxPower == -1) reconfigure();
        if (disabled) return 0;
        long req = 0;
        for (Direction face : Direction.values()) {
            if ((side == null || face != side) && pipe.isConnected(face)) {
                req += sections.get(face).powerQuery;
            }
        }
        return Math.min(req, maxPower);
    }

    public double getMaxTransferForRender(float partialTicks) {
        if (maxPower == -1) reconfigure();
        return maxPower / (double) MjAPI.MJ;
    }

    public class Section implements IMjReceiver {
        public final Direction side;

        public final AverageInt clientDisplayAverage = new AverageInt(10);
        public double clientDisplayFlow, clientDisplayFlowLast;

        /** Range: 0 to {@link MjAPI#MJ} */
        public int displayPower;
        public EnumFlow displayFlow = EnumFlow.STATIONARY;
        public long nextPowerQuery;
        public long internalNextPower;
        public final buildcraft.lib.misc.data.AverageLong powerAverage = new buildcraft.lib.misc.data.AverageLong(1);

        long powerQuery;
        long internalPower;
        private long received, sent;

        private long room() {
            if (maxPower == -1) reconfigure();
            long used = currentWorldTime == pipe.getHolder().getPipeWorld().getTime() ? received : 0;
            return Math.max(0, Math.min(capacity - internalPower - internalNextPower, maxPower - used));
        }

        /** Debugging fields */
        long debugPowerInput, debugPowerOutput, debugPowerOffered;

        public Section(Direction side) {
            this.side = side;
            clientDisplayFlow = (side.getDirection() == AxisDirection.POSITIVE ? 7 : 1) / 8.0;
        }

        void step() {
            powerQuery = nextPowerQuery;
            nextPowerQuery = 0;

            internalPower += internalNextPower;
            internalNextPower = 0;
            received = sent = 0;
        }

        @Override
        public boolean canConnect(@Nonnull IMjConnector other) {
            return true;
        }

        @Override
        public long getPowerRequested() {
            return canReceive() ? Math.min(room(), PipeFlowPower.this.getPowerRequested(side)) : 0;
        }

        long receivePowerInternal(long sent) {
            if (maxPower == -1) reconfigure();
            if (sent > 0 && !disabled && server() && open(side)) {
                long accepted = Math.min(sent, room());
                if (accepted <= 0) return sent;
                PipeFlowPower.this.step();
                debugPowerOffered += accepted;
                internalNextPower += accepted;
                received += accepted;
                pipe.getHolder().getPipeTile().markDirty();
                return sent - accepted;
            }
            return sent;
        }

        @Override
        public long receivePower(long microJoules, boolean simulate) {
            if (microJoules > 0 && canReceive() && server()) {
                if (!simulate) {
                    return this.receivePowerInternal(microJoules);
                }
                return microJoules - Math.min(microJoules, room());
            }
            return microJoules;
        }

        @Override
        public boolean canReceive() {
            if (maxPower == -1) reconfigure();
            return isReceiver && !disabled && live() && open(side);
        }
    }

    public enum EnumFlow {
        IN(-1),
        OUT(1),
        STATIONARY(0);

        public final int value;

        private EnumFlow(int value) {
            this.value = value;
        }
    }
}
