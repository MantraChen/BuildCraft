/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 */
package buildcraft.lib.net.cache;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.function.Consumer;
import java.util.function.Supplier;
import buildcraft.lib.net.PacketBufferBC;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;

/**
 * Session-local object IDs. Server/client views belong to their respective game threads.
 * Stored values and returned values are copied so mutable stacks cannot corrupt hash keys.
 */
public abstract class NetworkedObjectCache<T> {
    protected final T defaultObject;
    private final Int2ObjectMap<T> serverObjects = new Int2ObjectOpenHashMap<>();
    private final Object2IntMap<T> serverIds = createObject2IntMap();
    private final Int2ObjectMap<Link> clientObjects = new Int2ObjectOpenHashMap<>();
    private final Queue<Integer> clientUnknowns = new ArrayDeque<>();
    private final ServerView serverView = new ServerView();
    private final ClientView clientView = new ClientView();
    private int nextServerId;

    protected NetworkedObjectCache(T defaultObject) {
        this.defaultObject = defaultObject;
        serverIds.defaultReturnValue(-1);
    }

    protected abstract Object2IntMap<T> createObject2IntMap();
    protected abstract T copyOf(T object);
    protected abstract void writeObject(T object, PacketBufferBC buffer);
    protected abstract T readObject(PacketBufferBC buffer) throws IOException;
    protected String getCacheName() { return getClass().getSimpleName(); }

    public ServerView server() { return serverView; }
    public ClientView client() { return clientView; }

    public final class ServerView {
        private ServerView() {}
        public int store(T object) {
            java.util.Objects.requireNonNull(object, "cached object");
            int existing = serverIds.getInt(object);
            if (existing >= 0) return existing;
            if (nextServerId == Integer.MAX_VALUE) throw new IllegalStateException("Object cache exhausted");
            T copy = copyOf(object);
            int id = nextServerId++;
            serverIds.put(copy, id);
            serverObjects.put(id, copy);
            return id;
        }
        public int getId(T object) { return serverIds.getInt(object); }
    }

    public final class ClientView {
        private ClientView() {}
        public Link retrieve(int id) {
            if (id < -1) throw new IllegalArgumentException("Invalid object ID: " + id);
            Link link = clientObjects.get(id);
            if (link == null) {
                link = new Link(id);
                clientObjects.put(id, link);
                if (id >= 0) clientUnknowns.add(id);
                else link.actual = copyOf(defaultObject);
            }
            return link;
        }
    }

    public final class Link implements Supplier<T> {
        private final int id;
        private T actual;
        private Link(int id) { this.id = id; }
        @Override public T get() { return copyOf(actual == null ? defaultObject : actual); }
        public boolean hasBeenReceived() { return actual != null; }
    }

    void writeObjectServer(int id, PacketBufferBC buffer) {
        T object = serverObjects.get(id);
        if (object == null) throw new IllegalArgumentException("Unknown object ID: " + id);
        writeObject(object, buffer);
    }

    void readObjectClient(int id, PacketBufferBC buffer) throws IOException {
        T object = readObject(buffer);
        if (buffer.isReadable()) throw new IOException("Trailing cached-object data");
        Link link = clientObjects.get(id);
        if (link != null) link.actual = copyOf(object);
    }

    /** Sends one bounded batch per tick; repeated retrievals share a single request. */
    void onClientWorldTick(Consumer<MessageObjectCacheRequest> sender) {
        if (clientUnknowns.isEmpty()) return;
        int[] ids = clientUnknowns.stream().limit(MessageObjectCacheRequest.MAX_IDS).mapToInt(Integer::intValue).toArray();
        sender.accept(new MessageObjectCacheRequest(this, ids));
        for (int ignored : ids) clientUnknowns.remove();
    }

    void onClientJoinServer() {
        // Invalidate suppliers still held by a closing world before dropping the lookup map.
        for (Link link : clientObjects.values()) link.actual = null;
        clientObjects.clear();
        clientUnknowns.clear();
    }

    void onServerStopped() {
        serverObjects.clear();
        serverIds.clear();
        nextServerId = 0;
    }
}
