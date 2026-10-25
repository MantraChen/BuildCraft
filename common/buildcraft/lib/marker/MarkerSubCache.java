/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.marker;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.collect.ImmutableList;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import buildcraft.api.core.BCDebugging;
import buildcraft.api.core.BCLog;

import buildcraft.lib.client.render.laser.LaserData_BC8.LaserType;
import buildcraft.lib.net.MessageManager;
import buildcraft.lib.net.MessageMarker;
import buildcraft.lib.tile.TileMarker;

public abstract class MarkerSubCache<C extends MarkerConnection<C>> {
    public static final boolean DEBUG_FULL = BCDebugging.shouldDebugComplex("lib.marker.full");

    public final int cacheId;
    public final World world;
    public final boolean isServer;
    private final Map<BlockPos, C> posToConnection = new ConcurrentHashMap<>();
    private final Map<C, Set<BlockPos>> connectionToPos = new ConcurrentHashMap<>();
    private final Map<BlockPos, Optional<TileMarker<C>>> tileCache = new ConcurrentHashMap<>();

    public MarkerSubCache(World world, int cacheId) {
        this.isServer = !world.isClient;
        this.world = world;
        this.cacheId = cacheId;
    }

    public void onPlayerJoinWorld(ServerPlayerEntity player) {
        if (isServer) {// Sanity Check
            // Send ALL loaded markers
            if (!tileCache.isEmpty()) {
                List<BlockPos> all = new ArrayList<>(tileCache.keySet());
                for (int start = 0; start < all.size(); start += MessageMarker.MAX_POSITIONS) {
                    MessageManager.sendTo(new MessageMarker(world.getRegistryKey().getValue(), cacheId, true, false,
                        all.subList(start, Math.min(all.size(), start + MessageMarker.MAX_POSITIONS))), player);
                }
            }
            // Send ALL connections.
            for (C connection : connectionToPos.keySet()) {
                MessageManager.sendTo(new MessageMarker(world.getRegistryKey().getValue(), cacheId, true, true,
                    connection.getMarkerPositions()), player);
            }
        }
    }

    public boolean hasLoadedOrUnloadedMarker(BlockPos pos) {
        return tileCache.containsKey(pos);
    }

    @Nullable
    public TileMarker<C> getMarker(BlockPos pos) {
        Optional<TileMarker<C>> op = tileCache.get(pos);
        if (op == null) {
            return null;
        } else {
            return op.orElse(null);
        }
    }

    public void loadMarker(BlockPos pos, @Nullable TileMarker<C> marker) {
        boolean did = tileCache.containsKey(pos);
        tileCache.put(pos, Optional.ofNullable(marker));
        if (DEBUG_FULL) {
            BCLog.logger.info("[lib.marker.full] Set a marker at " + pos + " as " + marker);
        }
        if (isServer && !did) {
            MessageManager.sendToDimension(new MessageMarker(world.getRegistryKey().getValue(), cacheId, true, false,
                List.of(pos)), (net.minecraft.server.world.ServerWorld) world);
        }
    }

    public void unloadMarker(BlockPos pos) {
        loadMarker(pos, null);
    }

    public void removeMarker(BlockPos pos) {
        if (DEBUG_FULL) {
            BCLog.logger.info("[lib.marker.full] Removed a marker at " + pos);
        }
        tileCache.remove(pos);
        C connection = getConnection(pos);
        if (connection != null) {
            connection.removeMarker(pos);
            refreshConnection(connection);
        }
        if (isServer) {
            MessageManager.sendToDimension(new MessageMarker(world.getRegistryKey().getValue(), cacheId, false, false,
                List.of(pos)), (net.minecraft.server.world.ServerWorld) world);
        }
    }

    public ImmutableList<BlockPos> getAllMarkers() {
        return ImmutableList.copyOf(tileCache.keySet());
    }

    @Nullable
    public C getConnection(BlockPos pos) {
        return posToConnection.get(pos);
    }

    public void destroyConnection(@Nullable C connection) {
        if (connection == null) {
            return;
        }
        Set<BlockPos> set = connectionToPos.remove(connection);
        if (set != null) {
            deinitConnection(set);
        }

        if (DEBUG_FULL) {
            validateAllConnections();
        }
    }

    public void addConnection(@Nonnull C connection) {
        Set<BlockPos> lastSeen = new HashSet<>(connection.getMarkerPositions());
        initConnection(connection, lastSeen);
        if (DEBUG_FULL) {
            validateAllConnections();
        }
    }

    public void refreshConnection(@Nonnull C connection) {
        Set<BlockPos> lastSeen = connectionToPos.get(connection);
        if (DEBUG_FULL) {
            BCLog.logger.info("[lib.marker.full] Refreshing a connection");
            BCLog.logger.info("[lib.marker.full]    - Old = " + lastSeen);
            BCLog.logger.info("[lib.marker.full]    - New = " + connection.getMarkerPositions());
        }
        if (lastSeen == null) {
            // Why did you call this?
            addConnection(connection);
        } else {
            Set<BlockPos> invalid = new HashSet<>(lastSeen);
            lastSeen = new HashSet<>(connection.getMarkerPositions());
            invalid.removeAll(lastSeen);
            deinitConnection(invalid);
            initConnection(connection, lastSeen);
            if (lastSeen.isEmpty()) {
                connectionToPos.remove(connection);
            }
        }

        if (DEBUG_FULL) {
            validateAllConnections();
        }
    }

    private void validateAllConnections() {
        final String logStart = "[lib.marker.full][" + cacheId + "]";

        Set<C> visited = new HashSet<>();
        Set<BlockPos> visitedPos = new HashSet<>();

        for (Entry<C, Set<BlockPos>> entry : connectionToPos.entrySet()) {
            C con = entry.getKey();
            Set<BlockPos> positions = entry.getValue();
            Set<BlockPos> actual = new HashSet<>(con.getMarkerPositions());
            if (!positions.equals(actual)) {
                BCLog.logger.warn(logStart + " Positions differed!");
                List<BlockPos> total = new ArrayList<>();
                total.addAll(positions);
                total.addAll(actual);
                for (BlockPos p : total) {
                    String s = "(";
                    s += positions.contains(p) ? "R" : "_";
                    s += actual.contains(p) ? "S" : "_";
                    BCLog.logger.warn(logStart + "  - " + p + " " + s + ")");
                }
            }
            for (BlockPos p : positions) {
                if (visitedPos.contains(p)) {
                    BCLog.logger.warn(logStart + " Duplicate block positions!" + p + " - " + con);
                }
                visitedPos.add(p);
            }
            visited.add(con);
        }

        for (Entry<BlockPos, C> entry : posToConnection.entrySet()) {
            C connection = entry.getValue();
            BlockPos p = entry.getKey();
            if (!visited.contains(connection)) {
                BCLog.logger.warn(logStart + " Unknown connection " + connection + "(" + p + ")");
            }
            if (!visitedPos.contains(p)) {
                BCLog.logger.warn(logStart + " Unknown Position " + p + " (" + connection + ")");
            }
        }
    }

    private void deinitConnection(Set<BlockPos> set) {
        if (DEBUG_FULL) {
            BCLog.logger.info("[lib.marker.full] Tearing down all connections in " + set);
        }
        for (BlockPos p : set) {
            posToConnection.remove(p);
        }
        if (isServer && set.size() > 0) {
            MessageManager.sendToDimension(new MessageMarker(world.getRegistryKey().getValue(), cacheId, false, true,
                set), (net.minecraft.server.world.ServerWorld) world);
        }
    }

    private void initConnection(C connection, Set<BlockPos> lastSeen) {
        if (DEBUG_FULL) {
            BCLog.logger.info("[lib.marker.full] Setting up a connection with " + lastSeen);
        }
        if (lastSeen.size() < 2) {
            connectionToPos.remove(connection);
            for (BlockPos p : lastSeen) {
                posToConnection.remove(p);
            }
            return;
        }

        connectionToPos.put(connection, lastSeen);
        for (BlockPos p : lastSeen) {
            posToConnection.put(p, connection);
        }
        if (isServer && lastSeen.size() > 0) {
            MessageManager.sendToDimension(new MessageMarker(world.getRegistryKey().getValue(), cacheId, true, true,
                connection.getMarkerPositions()), (net.minecraft.server.world.ServerWorld) world);
        }
    }

    public ImmutableList<C> getConnections() {
        return ImmutableList.copyOf(connectionToPos.keySet());
    }

    public abstract boolean tryConnect(BlockPos from, BlockPos to);

    /** Checks if {@link #tryConnect(BlockPos, BlockPos)} would succeed at this time. */
    public abstract boolean canConnect(BlockPos a, BlockPos b);

    public abstract ImmutableList<BlockPos> getValidConnections(BlockPos from);

    @Environment(EnvType.CLIENT)
    public abstract LaserType getPossibleLaserType();

    @Environment(EnvType.CLIENT)
    public final void handleMessageMain(MessageMarker message) {
        if (handleMessage(message)) {
            return;
        }
        if (!message.connection) {
            List<BlockPos> positions = message.positions;
            if (message.add) {
                for (BlockPos p : positions) {
                    if (!hasLoadedOrUnloadedMarker(p)) {
                        loadMarker(p, null);
                    }
                }
            } else {
                for (BlockPos p : positions) {
                    removeMarker(p);
                }
            }
        }
    }

    @Environment(EnvType.CLIENT)
    protected abstract boolean handleMessage(MessageMarker message);
}
