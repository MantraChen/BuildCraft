/*
 * Copyright (c) 2026 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * Remake by FelixMantra
 */
package buildcraft.builders.snapshot;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.mj.MjBattery;
import buildcraft.lib.misc.BlockUtil;
import buildcraft.lib.misc.FakePlayerProvider;

/** Bounded, server-authoritative work shared by templates and blueprints.
 * Items remain in the inventory until placement, so queued work owns only MJ.
 * A failed placement must leave unconsumed stacks in PlaceTask.required for refund.
 */
public abstract class BuildingExecutor {
    public static final int MAX_VOLUME = 1_048_576;
    private static final int CHECKS_PER_TICK = 32, MAX_TASKS = 16;
    private static final long POWER_PER_TICK = 256 * MjAPI.MJ;
    private static final byte UNKNOWN = 0, CORRECT = 1, BREAK = 2, PLACE = 3, BLOCKED = 4;
    private byte[] states = new byte[0];
    private final Map<Integer, Work> tasks = new LinkedHashMap<>();
    private int cursor, unknown, blocked;
    public int leftToBreak, leftToPlace;

    protected abstract World workWorld();
    protected abstract MjBattery workBattery();
    protected abstract GameProfile workOwner();
    protected abstract BlockPos workOrigin();
    protected abstract boolean excavates();
    protected abstract int workSize();
    protected abstract BlockPos workPosition(int index);
    protected abstract NbtCompound workIdentity();
    protected abstract boolean isAir(BlockPos pos);
    protected abstract boolean isBlockCorrect(BlockPos pos);
    protected abstract boolean canPlace(BlockPos pos);
    protected abstract boolean isReadyToPlace(BlockPos pos);
    protected abstract boolean hasEnoughToPlaceItems(BlockPos pos);
    protected abstract List<ItemStack> getToPlaceItems(BlockPos pos);
    protected abstract boolean doPlaceTask(PlaceTask task);
    protected abstract void cancelPlaceTask(PlaceTask task);
    protected abstract void acceptDrops(List<ItemStack> drops);
    protected abstract void workChanged();

    public final void updateSnapshot() {
        cancelAllTasks();
        int size = workSize();
        if (size < 0 || size > MAX_VOLUME) throw new IllegalArgumentException("Invalid build volume: " + size);
        states = new byte[size];
        cursor = 0;
        unknown = size;
        blocked = leftToBreak = leftToPlace = 0;
    }

    public final void cancelAllTasks() {
        for (Work task : tasks.values()) refundPower(task);
        tasks.clear();
    }

    /** No cached material verdicts: adding resources takes effect on the next tick. */
    public final void resourcesChanged() { workChanged(); }

    public final boolean isBuildFinished() {
        return states.length > 0 && unknown == 0 && blocked == 0
            && leftToBreak == 0 && leftToPlace == 0 && tasks.isEmpty();
    }

    public final boolean tick() {
        if (!(workWorld() instanceof ServerWorld world) || states.length == 0) return false;
        for (int i = 0; i < Math.min(CHECKS_PER_TICK, states.length); i++) {
            check(cursor);
            cursor = (cursor + 1) % states.length;
        }
        long budget = POWER_PER_TICK;
        for (var iterator = tasks.entrySet().iterator(); iterator.hasNext();) {
            var entry = iterator.next();
            Work task = entry.getValue();
            if (!accessible(task.pos)) continue;
            byte state = classify(task.pos);
            setState(entry.getKey(), state);
            if (state != (task.breaking ? BREAK : PLACE)) {
                refundPower(task);
                iterator.remove();
                continue;
            }
            // Finish the survey before placing, then clear obstructions first.
            if (!task.breaking && (!isReadyToPlace(task.pos) || !hasEnoughToPlaceItems(task.pos))) {
                refundPower(task);
                iterator.remove();
                continue;
            }
            if (!task.breaking && (unknown > 0 || (excavates() && leftToBreak > 0))) continue;
            var player = FakePlayerProvider.INSTANCE.getFakePlayer(world, workOwner(), workOrigin());
            if (!world.canPlayerModifyAt(player, task.pos)) continue;
            long target = targetPower(task);
            if (task.power > target) {
                workBattery().addPower(task.power - target, false);
                task.power = target;
                workChanged();
            }
            long received = workBattery().extractPower(0, Math.min(target - task.power, budget));
            task.power += received;
            budget -= received;
            if (received > 0) workChanged();
            if (task.power < target) continue;
            boolean completed;
            if (task.breaking) {
                var drops = BlockUtil.breakBlockAndGetDrops(world, task.pos, new ItemStack(Items.DIAMOND_PICKAXE),
                    workOwner(), false);
                completed = drops.isPresent();
                drops.ifPresent(this::acceptDrops);
            } else {
                List<ItemStack> items = getToPlaceItems(task.pos);
                if (items == null) continue;
                PlaceTask place = new PlaceTask(task.pos, items);
                completed = doPlaceTask(place);
                if (!completed) cancelPlaceTask(place);
            }
            if (!completed) refundPower(task);
            iterator.remove();
            setState(entry.getKey(), classify(task.pos));
            workChanged();
        }
        return isBuildFinished();
    }

    private boolean accessible(BlockPos pos) {
        World world = workWorld();
        return world.isInBuildLimit(pos) && world.getWorldBorder().contains(pos) && world.isChunkLoaded(pos);
    }

    private byte classify(BlockPos pos) {
        if (!accessible(pos)) return UNKNOWN;
        World world = workWorld();
        boolean air = world.isAir(pos);
        if (isAir(pos)) {
            if (air || !excavates()) return CORRECT;
        } else {
            if (isBlockCorrect(pos)) return CORRECT;
            if (canPlace(pos)) return PLACE;
        }
        // Fluid removal needs the fluid schematic's transfer path, not fake-player harvesting.
        if (!excavates() || world.getBlockState(pos).getHardness(world, pos) < 0
            || !world.getFluidState(pos).isEmpty() || pos.equals(workOrigin())) return BLOCKED;
        return BREAK;
    }

    private void check(int index) {
        BlockPos pos = workPosition(index);
        byte state = classify(pos);
        setState(index, state);
        if (state == PLACE && (!isReadyToPlace(pos) || !hasEnoughToPlaceItems(pos))) return;
        if ((state == BREAK || state == PLACE) && tasks.size() < MAX_TASKS && !tasks.containsKey(index)) {
            // A full queue of placements must never prevent an obstruction from being cleared.
            tasks.put(index, new Work(pos.toImmutable(), state == BREAK, 0));
        } else if (state == BREAK && !tasks.containsKey(index)) {
            var iterator = tasks.entrySet().iterator();
            while (iterator.hasNext()) {
                Work candidate = iterator.next().getValue();
                if (!candidate.breaking) {
                    refundPower(candidate);
                    iterator.remove();
                    tasks.put(index, new Work(pos.toImmutable(), true, 0));
                    break;
                }
            }
        }
    }

    private void setState(int index, byte next) {
        byte previous = states[index];
        if (previous == next) return;
        if (previous == UNKNOWN) unknown--;
        if (previous == BREAK) leftToBreak--;
        if (previous == PLACE) leftToPlace--;
        if (previous == BLOCKED) blocked--;
        states[index] = next;
        if (next == UNKNOWN) unknown++;
        if (next == BREAK) leftToBreak++;
        if (next == PLACE) leftToPlace++;
        if (next == BLOCKED) blocked++;
    }

    private long targetPower(Work task) {
        return task.breaking ? Math.max(1, BlockUtil.computeBlockBreakPower(workWorld(), task.pos))
            : Math.max(MjAPI.MJ, (long) (Math.sqrt(task.pos.getSquaredDistance(workOrigin())) * 10 * MjAPI.MJ));
    }

    private void refundPower(Work task) {
        if (task.power != 0) {
            workBattery().addPower(task.power, false);
            task.power = 0;
            workChanged();
        }
    }

    public final NbtCompound serializeNBT() {
        NbtCompound nbt = new NbtCompound();
        nbt.putInt("version", 1);
        nbt.put("plan", workIdentity());
        NbtList list = new NbtList();
        tasks.forEach((index, task) -> {
            NbtCompound entry = new NbtCompound();
            entry.putInt("index", index);
            entry.putLong("pos", task.pos.asLong());
            entry.putBoolean("breaking", task.breaking);
            entry.putLong("power", task.power);
            list.add(entry);
        });
        nbt.put("work", list);
        return nbt;
    }

    /** Loading replaces state, rather than cancelling/refunding it. Repeated reads cannot mint energy. */
    public final void deserializeNBT(NbtCompound nbt) {
        tasks.clear();
        updateSnapshot();
        if (nbt.getInt("version") != 1 || !workIdentity().equals(nbt.getCompound("plan"))) return;
        NbtList list = nbt.getList("work", 10);
        for (int i = 0; i < Math.min(MAX_TASKS, list.size()); i++) {
            NbtCompound entry = list.getCompound(i);
            int index = entry.getInt("index");
            if (index < 0 || index >= states.length || tasks.containsKey(index)) continue;
            BlockPos pos = BlockPos.fromLong(entry.getLong("pos"));
            if (!pos.equals(workPosition(index))) continue;
            Work task = new Work(pos, entry.getBoolean("breaking"), 0);
            // Never probe an unloaded chunk during NBT loading.
            long maximum = task.breaking ? 16 * MjAPI.MJ
                : Math.max(MjAPI.MJ, (long) (Math.sqrt(pos.getSquaredDistance(workOrigin())) * 10 * MjAPI.MJ));
            task.power = Math.min(Math.max(0, entry.getLong("power")), maximum);
            tasks.put(index, task);
        }
    }

    public final List<BlockPos> activePositions() {
        return tasks.values().stream().map(task -> task.pos).toList();
    }

    private static final class Work {
        final BlockPos pos;
        final boolean breaking;
        long power;
        Work(BlockPos pos, boolean breaking, long power) {
            this.pos = pos; this.breaking = breaking; this.power = power;
        }
    }

    public static class PlaceTask {
        public final BlockPos pos;
        public final List<ItemStack> required;
        public PlaceTask(BlockPos pos, List<ItemStack> required) {
            this.pos = pos.toImmutable();
            this.required = new ArrayList<>(required);
        }
    }
}
