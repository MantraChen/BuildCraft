/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0
 * Native Fabric transport remake by FelixMantra. */
package buildcraft.transport.pipe.flow;

import java.io.IOException;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.storage.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.*;
import buildcraft.api.core.EnumPipePart;
import buildcraft.api.core.SafeTimeTracker;
import buildcraft.api.tiles.IDebuggable;
import buildcraft.api.transport.pipe.*;
import buildcraft.lib.net.cache.BuildCraftObjectCaches;
import buildcraft.transport.BCTransportStatements;

/** Seven delayed sections, with native transactional boundaries. All amounts are exact droplets. */
public class PipeFlowFluids extends PipeFlow implements IFlowFluid, IDebuggable {
    private static final int DIRECTION_COOLDOWN = 60;
    public static final int NET_FLUID_AMOUNTS = 2;
    public static final double FLOW_MULTIPLIER = 0.016;
    private final PipeApi.FluidTransferInfo info = PipeApi.getFluidTransferInfo(pipe.getDefinition());
    private final int rate = Math.multiplyExact(info.transferPerTick, 81);
    public final int capacity = Math.max((int) FluidConstants.BUCKET, Math.multiplyExact(rate, 10));
    private final int delay = Math.max(1, Math.min(1200, (int) info.transferDelayMultiplier));
    private final EnumMap<EnumPipePart, Section> sections = new EnumMap<>(EnumPipePart.class);
    private FluidVariant currentFluid, lastSentFluid;
    private final SafeTimeTracker tracker = new SafeTimeTracker(10, 4);
    private long lastMessage, lastMessageMinus1;
    private Supplier<FluidVariant> clientFluid;

    private record SectionState(int amount, int time, int direction, int[] incoming) {}
    private record FlowState(FluidVariant fluid, List<SectionState> sections) {}
    private final SnapshotParticipant<FlowState> snapshots = new SnapshotParticipant<>() {
        @Override protected FlowState createSnapshot() {
            var states = new ArrayList<SectionState>();
            for (var s : sections.values()) states.add(new SectionState(s.amount, s.time, s.direction, s.incoming.clone()));
            return new FlowState(currentFluid, states);
        }
        @Override protected void readSnapshot(FlowState state) {
            currentFluid = state.fluid();
            int index = 0;
            for (var s : sections.values()) {
                var saved = state.sections().get(index++);
                s.amount = saved.amount(); s.time = saved.time(); s.direction = saved.direction();
                s.incoming = saved.incoming().clone(); s.pending = Arrays.stream(s.incoming).sum();
            }
        }
        @Override protected void onFinalCommit() { pipe.getHolder().getPipeTile().markDirty(); }
    };

    public PipeFlowFluids(IPipe pipe) {
        super(pipe);
        if (rate <= 0) throw new IllegalArgumentException("Fluid pipe rate must be positive");
        for (var part : EnumPipePart.VALUES) sections.put(part, new Section(part));
    }

    public PipeFlowFluids(IPipe pipe, NbtCompound nbt) {
        this(pipe);
        if (nbt.contains("fluid")) {
            var fluid = FluidVariant.fromNbt(nbt.getCompound("fluid"));
            if (!fluid.isBlank()) currentFluid = fluid;
        }
        if (currentFluid != null) for (var part : EnumPipePart.VALUES)
            sections.get(part).readNbt(nbt.getCompound("tank[" + part.getIndex() + "]"), nbt.getInt("fluid_format") == 1);
        clearIfEmpty();
    }

    @Override public NbtCompound writeToNbt() {
        var nbt = super.writeToNbt();
        nbt.putInt("fluid_format", 1);
        if (currentFluid != null) {
            nbt.put("fluid", currentFluid.toNbt());
            for (var part : EnumPipePart.VALUES) nbt.put("tank[" + part.getIndex() + "]", sections.get(part).writeNbt());
        }
        return nbt;
    }

    public long getAmount() { return sections.values().stream().mapToLong(s -> s.amount).sum(); }
    public long getAmount(@Nullable Direction side) { return sections.get(EnumPipePart.fromFacing(side)).amount; }
    public FluidVariant getFluid() { return currentFluid == null ? FluidVariant.blank() : currentFluid; }
    public boolean doesContainFluid() { return getAmount() > 0; }
    @Override public void addDrops(List<net.minecraft.item.ItemStack> drops, int fortune) {
        var shards = net.minecraft.util.collection.DefaultedList.<net.minecraft.item.ItemStack>of();
        if (currentFluid != null) buildcraft.api.items.FluidItemDrops.addFluidDrops(shards,
            buildcraft.lib.compat.FluidStackBC.of(currentFluid, getAmount()));
        drops.addAll(shards);
    }
    public Storage<FluidVariant> getStorage(Direction side) { return side == null ? null : sections.get(EnumPipePart.fromFacing(side)); }

    private boolean live() {
        var holder = pipe.getHolder(); var world = holder.getPipeWorld();
        return world != null && !world.isClient && !holder.getPipeTile().isRemoved()
            && world.isChunkLoaded(holder.getPipePos()) && world.getBlockEntity(holder.getPipePos()) == holder.getPipeTile()
            && holder.getPipe() == pipe;
    }

    private Storage<FluidVariant> neighbour(Direction side, boolean connected) {
        if (side == null || !live() || connected && !pipe.isConnected(side)) return null;
        var plug = pipe.getHolder().getPluggable(side);
        if (plug != null && plug.isBlocking()) return null;
        var world = pipe.getHolder().getPipeWorld(); var pos = pipe.getHolder().getPipePos().offset(side);
        if (!world.isChunkLoaded(pos)) return null;
        return FluidStorage.SIDED.find(world, pos, side.getOpposite());
    }

    @Override public boolean canConnect(Direction face, PipeFlow other) { return other instanceof IFlowFluid; }
    @Override public boolean canConnect(Direction face, BlockEntity other) { return neighbour(face, false) != null; }

    @PipeEventHandler public static void addTriggers(PipeEventStatement.AddTriggerInternal event) {
        event.triggers.add(BCTransportStatements.TRIGGER_FLUIDS_TRAVERSING);
    }

    @Override public long tryExtractFluid(long droplets, Direction from, @Nullable FluidVariant filter, boolean simulate) {
        return tryExtractFluidAdv(droplets, from, resource -> filter == null || filter.equals(resource), simulate);
    }

    @Override public long tryExtractFluidAdv(long droplets, Direction from, Predicate<FluidVariant> filter, boolean simulate) {
        if (droplets <= 0 || from == null) return 0;
        var source = neighbour(from, true);
        if (source == null || !source.supportsExtraction()) return 0;
        var side = sections.get(EnumPipePart.fromFacing(from)); var centre = sections.get(EnumPipePart.CENTER);
        long available = Math.min(droplets, (long) capacity * 2 - side.amount - centre.amount);
        if (available <= 0) return 0;
        Set<FluidVariant> tried = new HashSet<>();
        for (var view : source) {
            var resource = view.getResource();
            if (resource.isBlank() || !tried.add(resource) || !filter.test(resource)
                    || currentFluid != null && !currentFluid.equals(resource)) continue;
            try (var tx = Transaction.openNested(Transaction.getCurrentUnsafe())) {
                long extracted = source.extract(resource, available, tx);
                if (extracted <= 0) continue;
                snapshots.updateSnapshots(tx);
                if (currentFluid == null) currentFluid = resource;
                int first = side.fill((int) Math.min(extracted, capacity), false);
                int second = centre.fill((int) (extracted - first), false);
                if ((long) first + second != extracted) throw new IllegalStateException("Fluid extraction exceeded pipe capacity");
                side.direction = -DIRECTION_COOLDOWN;
                if (!simulate) tx.commit();
                return extracted;
            }
        }
        return 0;
    }

    @Override public long insertFluidsForce(FluidVariant resource, long amount, @Nullable Direction from, boolean simulate) {
        StoragePreconditions.notBlankNotNegative(resource, amount);
        if (amount == 0 || !live() || currentFluid != null && !currentFluid.equals(resource)) return 0;
        var centre = sections.get(EnumPipePart.CENTER);
        int accepted = (int) Math.min(amount, centre.maxFill());
        if (accepted <= 0) return 0;
        try (var tx = Transaction.openOuter()) {
            snapshots.updateSnapshots(tx); currentFluid = resource;
            centre.fill(accepted, true);
            if (from != null) sections.get(EnumPipePart.fromFacing(from)).direction = -DIRECTION_COOLDOWN;
            if (!simulate) tx.commit();
        }
        return accepted;
    }

    @Override public FluidVolume extractFluidsForce(long min, long max, @Nullable Direction side, boolean simulate) {
        if (min < 0 || max < min) throw new IllegalArgumentException("Invalid fluid extraction range");
        if (!live() || currentFluid == null) return null;
        var section = sections.get(EnumPipePart.fromFacing(side));
        int amount = (int) Math.min(max, section.amount);
        if (amount <= 0 || amount < min) return null;
        var result = new FluidVolume(currentFluid, amount);
        if (!simulate) try (var tx = Transaction.openOuter()) {
            snapshots.updateSnapshots(tx);
            section.amount -= amount;
            // Force extraction may take travelling fluid; remove the corresponding delay credits.
            int remove = Math.max(0, section.pending - section.amount);
            for (int i = 1; i <= delay && remove > 0; i++) {
                int slot = (section.time + i) % delay, taken = Math.min(remove, section.incoming[slot]);
                section.incoming[slot] -= taken; section.pending -= taken; remove -= taken;
            }
            clearIfEmpty(); tx.commit();
        }
        return result;
    }

    private void clearIfEmpty() {
        if (getAmount() == 0) {
            currentFluid = null;
            for (var s : sections.values()) { Arrays.fill(s.incoming, 0); s.pending = 0; }
        }
    }

    @Override public void onTick() {
        var world = pipe.getHolder().getPipeWorld();
        if (world.isClient) { for (var s : sections.values()) s.tickClient(); return; }
        if (currentFluid != null) {
            for (var s : sections.values()) {
                s.time = (s.time + 1) % delay; s.pending -= s.incoming[s.time]; s.incoming[s.time] = 0;
            }
            moveFromPipe(); moveFromCenter(); moveToCenter(); clearIfEmpty();
            pipe.getHolder().getPipeTile().markDirty();
        }
        boolean changed = !Objects.equals(lastSentFluid, currentFluid);
        for (var s : sections.values()) {
            s.direction -= Integer.signum(s.direction);
            changed |= s.amount != s.lastSentAmount || Integer.signum(s.direction) != s.lastSentDirection;
        }
        if (changed && tracker.markTimeIfDelay(world)) sendPayload(NET_FLUID_AMOUNTS);
    }

    private void moveFromPipe() {
        for (var part : EnumPipePart.FACES) {
            var section = sections.get(part);
            int offered = section.maxDrain();
            if (section.direction < 0 || offered <= 0) continue;
            var check = new PipeEventFluid.SideCheck(pipe.getHolder(), this, currentFluid);
            check.disallowAllExcept(part.face); pipe.getHolder().fireEvent(check);
            if (!check.isAllowed(part.face)) continue;
            var target = neighbour(part.face, true);
            if (target == null) continue;
            try (var tx = Transaction.openOuter()) {
                int moved = (int) target.insert(currentFluid, offered, tx);
                if (moved <= 0) continue;
                snapshots.updateSnapshots(tx);
                section.amount -= moved; section.direction = DIRECTION_COOLDOWN;
                tx.commit();
            }
        }
    }

    private void moveFromCenter() {
        var centre = sections.get(EnumPipePart.CENTER);
        int left = centre.maxDrain();
        if (left <= 0) return;
        var check = new PipeEventFluid.SideCheck(pipe.getHolder(), this, currentFluid);
        for (var face : Direction.values()) {
            var section = sections.get(EnumPipePart.fromFacing(face));
            var target = neighbour(face, true);
            boolean accepts = false;
            if (section.direction >= 0 && section.maxFill() > 0 && target != null) try (var tx = Transaction.openOuter()) {
                accepts = target.insert(currentFluid, Math.min(left, section.maxFill()), tx) > 0;
            }
            if (!accepts) check.disallow(face);
        }
        pipe.getHolder().fireEvent(check);
        var choices = new ArrayList<>(check.getOrder()); Collections.shuffle(choices);
        for (int i = 0; i < choices.size() && left > 0; i++) {
            var section = sections.get(EnumPipePart.fromFacing(choices.get(i)));
            int offered = (left + choices.size() - i - 1) / (choices.size() - i);
            int moved = section.fill(offered, true);
            centre.amount -= moved; left -= moved;
            if (moved > 0) section.direction = DIRECTION_COOLDOWN;
        }
    }

    private void moveToCenter() {
        var centre = sections.get(EnumPipePart.CENTER);
        int left = centre.maxFill();
        if (left <= 0) return;
        int[] offered = new int[6];
        for (var part : EnumPipePart.FACES) {
            var section = sections.get(part);
            if (section.direction <= 0) offered[part.getIndex()] = section.maxDrain();
        }
        var pre = new PipeEventFluid.PreMoveToCentre(pipe.getHolder(), this, currentFluid, left, offered.clone(), offered);
        pipe.getHolder().fireEvent(pre);
        var choices = new ArrayList<EnumPipePart>();
        for (var part : EnumPipePart.FACES) if (offered[part.getIndex()] > 0) choices.add(part);
        Collections.shuffle(choices);
        int[] leaving = new int[6];
        for (int i = 0; i < choices.size() && left > 0; i++) {
            int index = choices.get(i).getIndex();
            leaving[index] = Math.min(offered[index], (left + choices.size() - i - 1) / (choices.size() - i));
            left -= leaving[index];
        }
        int[] entering = leaving.clone();
        pipe.getHolder().fireEvent(new PipeEventFluid.OnMoveToCentre(pipe.getHolder(), this, currentFluid, leaving, entering));
        for (var part : EnumPipePart.FACES) {
            int i = part.getIndex(); var section = sections.get(part);
            if (leaving[i] <= 0) continue;
            section.amount -= leaving[i]; section.direction = -DIRECTION_COOLDOWN;
            if (centre.fill(entering[i], true) != entering[i]) throw new IllegalStateException("Fluid routing exceeded centre capacity");
        }
    }

    @Override public void writePayload(int id, PacketByteBuf buffer, EnvType side) {
        if (side != EnvType.SERVER || id < 0 || id > NET_FLUID_AMOUNTS) return;
        buffer.writeBoolean(currentFluid != null);
        if (currentFluid != null) buffer.writeVarInt(BuildCraftObjectCaches.storeFluid(currentFluid));
        for (var part : EnumPipePart.VALUES) {
            var s = sections.get(part);
            buffer.writeVarInt(s.amount); buffer.writeByte(Integer.signum(s.direction) + 1);
            if (id != NET_ID_FULL_STATE) { s.lastSentAmount = s.amount; s.lastSentDirection = Integer.signum(s.direction); }
        }
        if (id != NET_ID_FULL_STATE) lastSentFluid = currentFluid;
    }

    @Override public void readPayload(int id, PacketByteBuf buffer, EnvType side) throws IOException {
        if (side != EnvType.CLIENT || id < 0 || id > NET_FLUID_AMOUNTS) return;
        boolean hasFluid = buffer.readBoolean();
        int fluidId = hasFluid ? buffer.readVarInt() : 0;
        if (fluidId < 0) throw new IOException("Negative pipe fluid cache ID");
        int[] amounts = new int[7], directions = new int[7];
        for (var part : EnumPipePart.VALUES) {
            int i = part.getIndex(); amounts[i] = buffer.readVarInt(); directions[i] = buffer.readUnsignedByte();
            if (amounts[i] < 0 || amounts[i] > capacity || directions[i] > 2 || !hasFluid && amounts[i] != 0)
                throw new IOException("Invalid fluid pipe section snapshot");
        }
        clientFluid = hasFluid ? BuildCraftObjectCaches.retrieveFluid(fluidId) : null;
        for (var part : EnumPipePart.VALUES) {
            var s = sections.get(part); s.target = amounts[part.getIndex()];
            s.direction = (directions[part.getIndex()] - 1) * DIRECTION_COOLDOWN;
            if (id == NET_ID_FULL_STATE) s.clientAmountLast = s.clientAmountThis = s.target;
        }
        lastMessageMinus1 = lastMessage;
        var world = pipe.getHolder().getPipeWorld(); lastMessage = world == null ? 0 : world.getTime();
    }

    @Environment(EnvType.CLIENT) public FluidVariant getFluidStackForRender() { return clientFluid == null ? null : clientFluid.get(); }
    @Environment(EnvType.CLIENT) public double[] getAmountsForRender(float partialTicks) {
        double[] result = new double[7];
        for (var part : EnumPipePart.VALUES) { var s = sections.get(part); result[part.getIndex()] = s.clientAmountLast * (1-partialTicks) + s.clientAmountThis * partialTicks; }
        return result;
    }
    @Environment(EnvType.CLIENT) public Vec3d[] getOffsetsForRender(float partialTicks) {
        Vec3d[] result = new Vec3d[7];
        for (var part : EnumPipePart.VALUES) { var s = sections.get(part); result[part.getIndex()] = s.offsetLast.multiply(1-partialTicks).add(s.offset.multiply(partialTicks)); }
        return result;
    }
    @Override public void getDebugInfo(List<String> left, List<String> right, Direction side) {
        left.add("Fluid = " + getFluid());
        for (var part : EnumPipePart.VALUES) { var s = sections.get(part); left.add(part + " = " + s.amount + " droplets (" + s.pending + " travelling), direction=" + s.direction); }
    }

    private final class Section implements Storage<FluidVariant>, StorageView<FluidVariant> {
        final EnumPipePart part;
        int amount, time, pending, direction, lastSentAmount, lastSentDirection;
        int[] incoming = new int[delay];
        int target, clientAmountThis, clientAmountLast;
        Vec3d offset = Vec3d.ZERO, offsetLast = Vec3d.ZERO;
        Section(EnumPipePart part) { this.part = part; }
        int maxFill() { return Math.max(0, Math.min(capacity-amount, rate-incoming[time])); }
        int maxDrain() { return Math.max(0, Math.min(amount-pending, rate)); }
        int fill(int offered, boolean limited) {
            int filled = Math.max(0, Math.min(offered, limited ? maxFill() : capacity-amount));
            incoming[time] += filled; pending += filled; amount += filled;
            return filled;
        }
        @Override public long insert(FluidVariant resource, long max, TransactionContext tx) {
            StoragePreconditions.notBlankNotNegative(resource, max);
            if (max == 0 || !live() || direction > 0 || !pipe.isConnected(part.face)
                || currentFluid != null && !currentFluid.equals(resource)) return 0;
            var plug = pipe.getHolder().getPluggable(part.face);
            if (plug != null && plug.isBlocking()) return 0;
            var event = new PipeEventFluid.TryInsert(pipe.getHolder(), PipeFlowFluids.this, part.face, resource, max);
            pipe.getHolder().fireEvent(event);
            if (event.isCanceled()) return 0;
            int accepted = (int) Math.min(max, maxFill());
            if (accepted <= 0) return 0;
            snapshots.updateSnapshots(tx); currentFluid = resource;
            fill(accepted, true); direction = -DIRECTION_COOLDOWN;
            return accepted;
        }
        @Override public long extract(FluidVariant resource, long max, TransactionContext tx) { StoragePreconditions.notBlankNotNegative(resource, max); return 0; }
        @Override public boolean supportsExtraction() { return false; }
        @Override public boolean isResourceBlank() { return !live() || amount == 0 || currentFluid == null; }
        @Override public FluidVariant getResource() { return isResourceBlank() ? FluidVariant.blank() : currentFluid; }
        @Override public long getAmount() { return live() ? amount : 0; }
        @Override public long getCapacity() { return live() ? capacity : 0; }
        @Override public Iterator<StorageView<FluidVariant>> iterator() { return Collections.<StorageView<FluidVariant>>singleton(this).iterator(); }
        NbtCompound writeNbt() {
            var nbt = new NbtCompound(); nbt.putInt("capacity", amount); nbt.putInt("time", time); nbt.putInt("ticksInDirection", direction);
            for (int i=0;i<delay;i++) nbt.putInt("in["+i+"]", incoming[i]);
            return nbt;
        }
        void readNbt(NbtCompound nbt, boolean droplets) {
            amount = savedAmount(nbt.getLong("capacity"), droplets, capacity);
            time = Math.floorMod(nbt.getInt("time"), delay);
            direction = MathHelper.clamp(nbt.getInt("ticksInDirection"), -DIRECTION_COOLDOWN, DIRECTION_COOLDOWN);
            for (int i=0;i<delay;i++) { incoming[i] = savedAmount(nbt.getLong("in["+i+"]"), droplets, amount-pending); pending += incoming[i]; }
        }
        void tickClient() {
            clientAmountLast = clientAmountThis;
            long span = Math.max(1, Math.min(60, lastMessage-lastMessageMinus1));
            int delta = target-clientAmountThis;
            clientAmountThis += Math.abs(delta) < span ? delta : delta/(int)span;
            offsetLast = offset;
            Vec3d movement = Vec3d.ZERO;
            if (part.face != null) movement = Vec3d.of(part.face.getVector()).multiply(Integer.signum(direction));
            else for (var face : EnumPipePart.FACES) movement = movement.add(Vec3d.of(face.face.getVector()).multiply(Integer.signum(sections.get(face).direction)));
            offset = offset.add(movement.multiply(-FLOW_MULTIPLIER));
            double dx = offset.x >= 0.5 ? -1 : offset.x <= -0.5 ? 1 : 0;
            double dy = offset.y >= 0.5 ? -1 : offset.y <= -0.5 ? 1 : 0;
            double dz = offset.z >= 0.5 ? -1 : offset.z <= -0.5 ? 1 : 0;
            offset = offset.add(dx,dy,dz); offsetLast = offsetLast.add(dx,dy,dz);
        }
    }

    private static int savedAmount(long amount, boolean droplets, int maximum) {
        if (amount <= 0) return 0;
        return (int) Math.min(maximum, Math.min(amount, maximum) * (droplets ? 1L : 81L));
    }
}
