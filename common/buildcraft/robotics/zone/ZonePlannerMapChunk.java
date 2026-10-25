/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.robotics.zone;

import javax.annotation.Nullable;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

public class ZonePlannerMapChunk {
    private final MapColourData[][] data = new MapColourData[16][16];

    public ZonePlannerMapChunk(World world, ZonePlannerMapChunkKey key) {
        if (!world.getRegistryKey().getValue().equals(key.dimension)
            || !world.isChunkLoaded(key.chunkPos.x, key.chunkPos.z))
            throw new IllegalArgumentException("Map sampling requires a loaded chunk in the requested dimension");
        WorldChunk chunk = world.getChunk(key.chunkPos.x, key.chunkPos.z);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                // Scan down from the max height value of a chunk until we find a block
                for (int y = Math.min(world.getTopY() - 1, key.level * ZonePlannerMapChunkKey.LEVEL_HEIGHT);
                    y >= world.getBottomY(); y--) {
                    BlockPos pos = new BlockPos(key.chunkPos.getStartX() + x, y, key.chunkPos.getStartZ() + z);
                    var mapColor = chunk.getBlockState(pos).getMapColor(world, pos);
                    if (mapColor != net.minecraft.block.MapColor.CLEAR) {
                        int colour = mapColor.color;
                        data[x][z] = new MapColourData(y, colour);
                        break;
                    }
                }
            }
        }
    }

    public ZonePlannerMapChunk(PacketByteBuf buffer) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                if (buffer.readBoolean()) {
                    int posY = buffer.readInt();
                    if (posY < -2032 || posY > 2031) throw new IllegalArgumentException("Map height out of range");
                    int colour = buffer.readInt();
                    data[x][z] = new MapColourData(posY, colour);
                }
            }
        }
    }

    public void write(PacketByteBuf buffer) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                MapColourData colour = data[x][z];
                buffer.writeBoolean(colour != null);
                if (colour != null) {
                    buffer.writeInt(colour.posY);
                    buffer.writeInt(colour.colour);
                }
            }
        }
    }

    public int getColour(int x, int z) {
        MapColourData col = getData(x, z);
        return col == null ? -1 : col.colour;
    }

    @Nullable
    public MapColourData getData(int x, int z) {
        return data[x & 15][z & 15];
    }

    public static final class MapColourData {
        public final int posY;
        public final int colour;

        public MapColourData(int posY, int colour) {
            this.posY = posY;
            this.colour = colour;
        }
    }
}
