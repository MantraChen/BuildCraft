/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.marker;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.storage.WorldSavedData;

import buildcraft.api.core.BCLog;

import buildcraft.lib.misc.NBTUtilBC;

public abstract class MarkerSavedData<S extends MarkerSubCache<C>, C extends MarkerConnection<C>> extends WorldSavedData {
    protected static final boolean DEBUG_FULL = MarkerSubCache.DEBUG_FULL;

    protected final List<BlockPos> markerPositions = new ArrayList<>();
    protected final List<List<BlockPos>> markerConnections = new ArrayList<>();
    private S subCache;

    public MarkerSavedData(String name) {
        super(name);
    }

    @Override
    public void readFromNBT(NbtCompound nbt) {
        markerPositions.clear();
        markerConnections.clear();

        NbtList positions=nbt.get("positions") instanceof NbtList list?list:new NbtList();
        var known=new java.util.HashSet<BlockPos>();
        for (int i=0;i<Math.min(1_048_576,positions.size());i++) {
            var position=NBTUtilBC.readBlockPos(positions.get(i));
            if (position!=null && known.add(position)) markerPositions.add(position);
        }
        NbtList connections=nbt.getList("connections",net.minecraft.nbt.NbtElement.LIST_TYPE);
        var claimed=new java.util.HashSet<BlockPos>();
        for (int i=0;i<Math.min(32767,connections.size());i++) {
            if (!(connections.get(i) instanceof NbtList entries) || entries.size()<2 || entries.size()>32767) continue;
            List<BlockPos> inner=new ArrayList<>();boolean valid=true;
            for (var entry:entries) {
                var position=NBTUtilBC.readBlockPos(entry);
                if (position==null || !known.contains(position) || claimed.contains(position)) {valid=false;break;}
                inner.add(position);
            }
            if (valid && new java.util.HashSet<>(inner).size()>=2) {markerConnections.add(inner);claimed.addAll(inner);}
        }
        if (DEBUG_FULL) {
            BCLog.logger.info("[lib.marker.full] Reading from NBT (" + mapName + ")");
            BCLog.logger.info("[lib.marker.full]  - Positions:");
            for (BlockPos pos : markerPositions) {
                BCLog.logger.info("[lib.marker.full]   - " + pos);
            }
            BCLog.logger.info("[lib.marker.full]  - Connections:");
            for (List<BlockPos> list : markerConnections) {
                BCLog.logger.info("[lib.marker.full]   - Single Connection:");
                for (BlockPos pos : list) {
                    BCLog.logger.info("[lib.marker.full]     - " + pos);
                }
            }
        }
    }

    @Override
    public NbtCompound writeToNBT(NbtCompound nbt) {
        markerPositions.clear();
        markerConnections.clear();

        markerPositions.addAll(subCache.getAllMarkers());
        for (C connection : subCache.getConnections()) {
            markerConnections.add(new ArrayList<>(connection.getMarkerPositions()));
        }

        NbtList positionList = new NbtList();
        for (BlockPos p : markerPositions) {
            positionList.add(NBTUtilBC.writeBlockPos(p));
        }
        nbt.put("positions", positionList);

        NbtList connectionList = new NbtList();
        for (List<BlockPos> connection : markerConnections) {
            NbtList inner = new NbtList();
            for (BlockPos p : connection) {
                inner.add(NBTUtilBC.writeBlockPos(p));
            }
            connectionList.add(inner);
        }
        nbt.put("connections", connectionList);

        if (DEBUG_FULL) {
            BCLog.logger.info("[lib.marker.full] Writing to NBT (" + mapName + ")");
            BCLog.logger.info("[lib.marker.full]  - Positions:");
            for (BlockPos pos : markerPositions) {
                BCLog.logger.info("[lib.marker.full]   - " + pos);
            }
            BCLog.logger.info("[lib.marker.full]  - Connections:");
            for (List<BlockPos> list : markerConnections) {
                BCLog.logger.info("[lib.marker.full]   - Single Connection:");
                for (BlockPos pos : list) {
                    BCLog.logger.info("[lib.marker.full]     - " + pos);
                }
            }
        }

        return nbt;
    }

    @Override
    public boolean isDirty() {
        return true;
    }

    public final void setCache(S subCache) {
        this.subCache = subCache;
    }
}
