/* Copyright (c) 2017 SpaceToad and the BuildCraft team. MPL-2.0. */
package buildcraft.builders.snapshot;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import buildcraft.lib.net.MessageManager;

@Environment(EnvType.CLIENT)
public final class ClientSnapshots {
    public static final ClientSnapshots INSTANCE = new ClientSnapshots();
    private final Cache<Snapshot.Key, Optional<Snapshot>> snapshots = CacheBuilder.newBuilder()
        .maximumSize(32).expireAfterAccess(2, TimeUnit.MINUTES).build();
    private final Map<Snapshot.Key, Long> pending = new HashMap<>();
    public Snapshot getSnapshot(Snapshot.Key requested) {
        if (requested == null || requested.hash.length != 32) return null;
        Snapshot.Key key = new Snapshot.Key(new Snapshot.Key(), requested.hash.clone());
        Optional<Snapshot> found = snapshots.getIfPresent(key);
        if (found != null) return found.orElse(null);
        if (pending.size() < 4 && !pending.containsKey(key)) {
            pending.put(key, System.nanoTime());
            MessageManager.sendToServer(new MessageSnapshotRequest(key));
        }
        return null;
    }
    public void accept(MessageSnapshotResponse message) throws java.io.IOException {
        if (pending.remove(message.key) != null) snapshots.put(message.key, Optional.ofNullable(message.snapshot()));
    }
    public void onClientTick() {
        long now = System.nanoTime();
        pending.entrySet().removeIf(entry -> now - entry.getValue() > 10_000_000_000L);
    }
    public void clear() { snapshots.invalidateAll(); pending.clear(); }
}
