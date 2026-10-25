/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.robotics.zone;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import javax.vecmath.Point2i;

import com.google.common.collect.ImmutableList;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;

import buildcraft.api.core.IZone;

import buildcraft.lib.misc.NBTUtilBC;

public class ZonePlan implements IZone {
    public static final int MAX_CHUNKS = 4096;
    private final HashMap<ChunkPos, ZoneChunk> chunkMapping = new HashMap<>();

    public ZonePlan() {}

    public ZonePlan(ZonePlan old) {
        for (ChunkPos chunkPos : old.chunkMapping.keySet()) {
            chunkMapping.put(chunkPos, new ZoneChunk(old.chunkMapping.get(chunkPos)));
        }
    }

    public boolean get(int x, int z) {
        int xChunk = x >> 4;
        int zChunk = z >> 4;
        ChunkPos chunkId = new ChunkPos(xChunk, zChunk);
        ZoneChunk property;

        if (!chunkMapping.containsKey(chunkId)) {
            return false;
        } else {
            property = chunkMapping.get(chunkId);
            return property.get(x & 0xF, z & 0xF);
        }
    }

    public void set(int x, int z, boolean val) {
        int xChunk = x >> 4;
        int zChunk = z >> 4;
        ChunkPos chunkId = new ChunkPos(xChunk, zChunk);
        ZoneChunk property;

        if (!chunkMapping.containsKey(chunkId)) {
            if (val) {
                if (chunkMapping.size() >= MAX_CHUNKS) throw new IllegalArgumentException("Zone has too many chunks");
                property = new ZoneChunk();
                chunkMapping.put(chunkId, property);
            } else {
                return;
            }
        } else {
            property = chunkMapping.get(chunkId);
        }

        property.set(x & 0xF, z & 0xF, val);

        if (property.isEmpty()) {
            chunkMapping.remove(chunkId);
        }
    }

    public List<Point2i> getAll() {
        ImmutableList.Builder<Point2i> builder = ImmutableList.builder();
        chunkMapping.forEach((chunkPos, zoneChunk) -> {
            zoneChunk.getAll().forEach(p -> builder.add(
                new Point2i(p.x + chunkPos.getStartX(), p.y + chunkPos.getStartZ())));
        });
        return builder.build();
    }

    public ZonePlan getWithOffset(int offsetX, int offsetY) {
        ZonePlan zonePlan = new ZonePlan();
        getAll().forEach(p -> zonePlan.set(Math.addExact(p.x, offsetX), Math.addExact(p.y, offsetY), true));
        return zonePlan;
    }

    public boolean hasChunk(ChunkPos chunkPos) {
        return chunkMapping.containsKey(chunkPos);
    }

    public Set<ChunkPos> getChunkPoses() {
        return chunkMapping.keySet();
    }

    public HashMap<ChunkPos, ZoneChunk> getChunkMapping() {
        return chunkMapping;
    }

    public void writeToNBT(NbtCompound nbt) {
        nbt.put(
                "chunkMapping",
                NBTUtilBC.writeCompoundList(
                        chunkMapping.entrySet().stream()
                                .map(entry -> {
                                    NbtCompound zoneChunkTag = new NbtCompound();
                                    entry.getValue().writeToNBT(zoneChunkTag);
                                    zoneChunkTag.putInt("chunkX", entry.getKey().x);
                                    zoneChunkTag.putInt("chunkZ", entry.getKey().z);
                                    return zoneChunkTag;
                                })
                )
        );
    }

    public void readFromNBT(NbtCompound nbt) {
        var list = nbt.get("chunkMapping");
        if (list != null && (!(list instanceof net.minecraft.nbt.NbtList entries)
            || entries.size() > MAX_CHUNKS || !entries.isEmpty() && entries.getHeldType() != net.minecraft.nbt.NbtElement.COMPOUND_TYPE))
            throw new IllegalArgumentException("Invalid zone chunk list");
        var replacement = new HashMap<ChunkPos, ZoneChunk>();
        NBTUtilBC.readCompoundList(list)
                .forEach(zoneChunkTag -> {
                    ZoneChunk chunk = new ZoneChunk();
                    chunk.readFromNBT(zoneChunkTag);
                    var pos = checkedChunk(zoneChunkTag.getInt("chunkX"), zoneChunkTag.getInt("chunkZ"));
                    if (replacement.put(pos, chunk) != null) throw new IllegalArgumentException("Duplicate zone chunk");
                });
        replacement.values().removeIf(ZoneChunk::isEmpty);
        chunkMapping.clear();
        chunkMapping.putAll(replacement);
    }

    @Override
    public double distanceTo(BlockPos index) {
        return Math.sqrt(distanceToSquared(index));
    }

    @Override
    public double distanceToSquared(BlockPos index) {
        double maxSqrDistance = Double.MAX_VALUE;

        for (Map.Entry<ChunkPos, ZoneChunk> e : chunkMapping.entrySet()) {
            for (var cell : e.getValue().getAll()) {
                double dx = (double) e.getKey().getStartX() + cell.x - index.getX();
                double dz = (double) e.getKey().getStartZ() + cell.y - index.getZ();
                maxSqrDistance = Math.min(maxSqrDistance, dx * dx + dz * dz);
                if (maxSqrDistance == 0) return 0;
            }
        }

        return maxSqrDistance;
    }

    @Override
    public boolean contains(Vec3d point) {
        int xBlock = (int) Math.floor(point.x);
        int zBlock = (int) Math.floor(point.z);

        return get(xBlock, zBlock);
    }

    @Override
    public BlockPos getRandomBlockPos(Random rand) {
        if (chunkMapping.size() == 0) {
            return null;
        }

        int chunkId = rand.nextInt(chunkMapping.size());

        for (Map.Entry<ChunkPos, ZoneChunk> e : chunkMapping.entrySet()) {
            if (chunkId == 0) {
                BlockPos i = e.getValue().getRandomBlockPos(rand);
                int x = (e.getKey().x << 4) + i.getX();
                int z = (e.getKey().z << 4) + i.getZ();

                return new BlockPos(x, i.getY(), z);
            }

            chunkId--;
        }

        return null;
    }

    public ZonePlan readFromByteBuf(PacketByteBuf buf) {
        int size = buf.readInt();
        if (size < 0 || size > MAX_CHUNKS) throw new IllegalArgumentException("Invalid zone chunk count");
        var replacement = new HashMap<ChunkPos, ZoneChunk>();
        for (int i = 0; i < size; i++) {
            ChunkPos key = checkedChunk(buf.readInt(), buf.readInt());
            ZoneChunk value = new ZoneChunk();
            value.readFromByteBuf(buf);
            if (replacement.put(key, value) != null) throw new IllegalArgumentException("Duplicate zone chunk");
        }
        replacement.values().removeIf(ZoneChunk::isEmpty);
        chunkMapping.clear();
        chunkMapping.putAll(replacement);
        return this;
    }

    private static ChunkPos checkedChunk(int x, int z) {
        if (x < (Integer.MIN_VALUE >> 4) || x > (Integer.MAX_VALUE >> 4)
            || z < (Integer.MIN_VALUE >> 4) || z > (Integer.MAX_VALUE >> 4))
            throw new IllegalArgumentException("Zone chunk coordinate overflow");
        return new ChunkPos(x, z);
    }

    public void writeToByteBuf(PacketByteBuf buf) {
        buf.writeInt(chunkMapping.size());
        for (Map.Entry<ChunkPos, ZoneChunk> e : chunkMapping.entrySet()) {
            buf.writeInt(e.getKey().x);
            buf.writeInt(e.getKey().z);
            e.getValue().writeToByteBuf(buf);
        }
    }
}
