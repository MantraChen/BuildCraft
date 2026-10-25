/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team.
 * This Source Code Form is subject to the Mozilla Public License, v. 2.0.
 */
package buildcraft.lib.client.model;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import com.google.gson.JsonSyntaxException;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

/** Tracks active parent loads, while allowing different branches to share a parent. */
public final class ResourceLoaderContext {
    @FunctionalInterface
    public interface ResourceOpener { InputStream open(Identifier location) throws IOException; }

    private final ResourceOpener resources;
    private final Deque<Identifier> active = new ArrayDeque<>();

    public ResourceLoaderContext() {
        this(location -> MinecraftClient.getInstance().getResourceManager().getResourceOrThrow(location).getInputStream());
    }
    public ResourceLoaderContext(ResourceOpener resources) { this.resources = resources; }

    public InputStreamReader startLoading(Identifier location) throws IOException {
        if (active.contains(location)) throw new JsonSyntaxException("Cyclic model parent: " + location + " in " + active);
        if (active.size() >= 64) throw new JsonSyntaxException("Model parent nesting exceeds 64 at " + location);
        InputStream stream = resources.open(location);
        active.push(location);
        return new InputStreamReader(stream, StandardCharsets.UTF_8) {
            private boolean closed;
            @Override public void close() throws IOException {
                if (closed) return;
                closed = true;
                try { super.close(); }
                finally {
                    if (!active.pop().equals(location)) throw new IllegalStateException("Model resources closed out of order");
                }
            }
        };
    }
}
