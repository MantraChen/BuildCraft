/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.snapshot;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;

import org.apache.commons.lang3.tuple.Pair;

import net.minecraft.world.World;

import net.fabricmc.loader.api.FabricLoader;

import buildcraft.lib.tile.TileBC_Neptune.NetSide;

import buildcraft.lib.misc.data.SingleCache;
import buildcraft.lib.nbt.NbtSquisher;

public class GlobalSavedDataSnapshots {
    private static final String SNAPSHOT_FILE_EXTENSION = ".bcnbt";
    private static final Map<NetSide, GlobalSavedDataSnapshots> INSTANCES = new EnumMap<>(NetSide.class);
    private final LoadingCache<Snapshot.Key, Optional<Snapshot>> snapshotsCache = CacheBuilder.newBuilder()
        .expireAfterAccess(10, TimeUnit.MINUTES)
        .build(CacheLoader.from(key -> Optional.ofNullable(readSnapshot(key)).map(Pair::getLeft)));
    private final SingleCache<List<Snapshot.Key>> listCache = new SingleCache<>(
        this::readList,
        1,
        TimeUnit.SECONDS
    );
    private final File snapshotsFile;

    private GlobalSavedDataSnapshots(NetSide side) {
        snapshotsFile = new File(
            FabricLoader.getInstance().getGameDir().toFile(),
            "snapshots-" + side.name().toLowerCase(Locale.ROOT)
        );
        // Create the directory inside the fallible write operation. A missing or blocked
        // path must leave the machine's input intact instead of crashing its world tick.
    }

    public static void reInit(NetSide side) {
        INSTANCES.put(side, new GlobalSavedDataSnapshots(side));
    }

    public static GlobalSavedDataSnapshots get(NetSide side) {
        if (!INSTANCES.containsKey(side)) {
            INSTANCES.put(side, new GlobalSavedDataSnapshots(side));
        }
        return INSTANCES.get(side);
    }

    public static GlobalSavedDataSnapshots get(World world) {
        return get(world.isClient ? NetSide.CLIENT : NetSide.SERVER);
    }

    private Pair<Snapshot, File> readSnapshot(Snapshot.Key key) {
        File[] files = snapshotsFile.listFiles();
        if (files != null) {
            for (File snapshotFile : files) {
                if (snapshotFile.getName().startsWith(key.toString()) &&
                    snapshotFile.getName().endsWith(SNAPSHOT_FILE_EXTENSION)) {
                    try (FileInputStream fileInputStream = new FileInputStream(snapshotFile)) {
                        Snapshot snapshot = Snapshot.readFromNBT(NbtSquisher.expand(fileInputStream));
                        if (Objects.equals(snapshot.key, key)) {
                            return Pair.of(snapshot, snapshotFile);
                        }
                    } catch (IOException | RuntimeException e) {
                        new IOException("Failed to read the snapshot " + snapshotFile, e).printStackTrace();
                    }
                }
            }
        }
        return null;
    }

    private List<Snapshot.Key> readList() {
        ImmutableList.Builder<Snapshot.Key> listBuilder = ImmutableList.builder();
        File[] files = snapshotsFile.listFiles();
        if (files != null) {
            for (File snapshotFile : files) {
                if (snapshotFile.getName().endsWith(SNAPSHOT_FILE_EXTENSION)) {
                    try (FileInputStream fileInputStream = new FileInputStream(snapshotFile)) {
                        Snapshot snapshot = Snapshot.readFromNBT(NbtSquisher.expand(fileInputStream));
                        if (snapshotFile.getName().startsWith(snapshot.key.toString())) {
                            listBuilder.add(snapshot.key);
                        }
                    } catch (IOException | RuntimeException io) {
                        new IOException("Failed to read the snapshot " + snapshotFile, io).printStackTrace();
                    }
                }
            }
        }
        return listBuilder.build();
    }

    public boolean addSnapshot(Snapshot snapshot) {
        File snapshotFile = new File(
            snapshotsFile,
            snapshot.key.toString() + SNAPSHOT_FILE_EXTENSION
        );
        java.nio.file.Path temporary = null;
        try {
            java.nio.file.Files.createDirectories(snapshotsFile.toPath());
            // Existing entries must be readable; a truncated file must not consume another blank blueprint.
            if (snapshotFile.exists()) {
                if (readSnapshot(snapshot.key) == null) return false;
            } else {
                temporary = java.nio.file.Files.createTempFile(snapshotsFile.toPath(), "snapshot-", ".tmp");
                try (FileOutputStream stream = new FileOutputStream(temporary.toFile())) {
                    // The compressor closes its output; retain the descriptor until the final fsync.
                    NbtSquisher.squishVanilla(Snapshot.writeToNBT(snapshot), new java.io.FilterOutputStream(stream) {
                        @Override public void close() throws IOException { flush(); }
                    });
                    stream.getFD().sync();
                }
                try { java.nio.file.Files.move(temporary, snapshotFile.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE); }
                catch (java.nio.file.AtomicMoveNotSupportedException unsupported) { java.nio.file.Files.move(temporary, snapshotFile.toPath()); }
            }
        } catch (IOException | RuntimeException error) {
            new IOException("Failed to write the snapshot file: " + snapshotFile, error).printStackTrace();
            return false;
        } finally {
            if (temporary != null) try { java.nio.file.Files.deleteIfExists(temporary); } catch (IOException ignored) { }
        }
        snapshotsCache.invalidate(snapshot.key);
        listCache.clear();
        return true;
    }

    public void removeSnapshot(Snapshot.Key key) {
        Optional.ofNullable(readSnapshot(key)).map(Pair::getRight).ifPresent(snapshotFile -> {
            if (!snapshotFile.delete()) {
                new IOException("Failed to read the snapshot file: " + snapshotFile).printStackTrace();
            }
            snapshotsCache.invalidate(key);
        });
        listCache.clear();
    }

    @Nullable
    public Snapshot getSnapshot(@Nullable Snapshot.Key key) {
        if (key == null) return null;
        return snapshotsCache.getUnchecked(key).orElse(null);
    }

    public List<Snapshot.Key> getList() {
        return listCache.get();
    }
}
