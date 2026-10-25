/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 *
 * Ported to Fabric 1.20.1 by R.Chen (https://github.com/MantraChen).
 */

package buildcraft.transport.pipe;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import buildcraft.transport.item.ItemPipeHolder;
import buildcraft.lib.registry.RegistryConfig;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.collect.ImmutableList;

import net.minecraft.item.Item;
import net.minecraft.util.Identifier;

import buildcraft.api.core.InvalidInputDataException;
import buildcraft.api.transport.pipe.IItemPipe;
import buildcraft.api.transport.pipe.IPipeRegistry;
import buildcraft.api.transport.pipe.PipeDefinition;

public enum PipeRegistry implements IPipeRegistry {
    INSTANCE;

    private final Map<Identifier, PipeDefinition> definitions = new HashMap<>();
    private final Map<PipeDefinition, IItemPipe> pipeItems = new IdentityHashMap<>();

    @Override
    public void registerPipe(PipeDefinition definition) {
        PipeDefinition previous = definitions.putIfAbsent(definition.identifier, definition);
        if (previous != null && previous != definition) throw new IllegalArgumentException("Duplicate pipe definition: " + definition.identifier);
    }

    @Override
    public void setItemForPipe(PipeDefinition definition, @Nullable IItemPipe item) {
        if (definition == null) {
            throw new NullPointerException("definition");
        }
        if (item == null) {
            pipeItems.remove(definition);
        } else {
            pipeItems.put(definition, item);
        }
    }

    @Override
    public ItemPipeHolder createItemForPipe(PipeDefinition definition) {
        return createUnnamedItemForPipe(definition, item -> {});
    }

    @Override
    public ItemPipeHolder createUnnamedItemForPipe(PipeDefinition definition, Consumer<Item> postCreate) {
        java.util.Objects.requireNonNull(definition, "definition");
        var id = new Identifier(definition.identifier.getNamespace(), "pipe_" + definition.identifier.getPath());
        if (!RegistryConfig.isEnabled("pipes", id, "item.buildcraft_pipe_" + definition.identifier.getPath() + ".name")) return null;
        if (pipeItems.containsKey(definition)) throw new IllegalArgumentException("Pipe already has an item: " + definition.identifier);
        var item = new ItemPipeHolder(definition);
        postCreate.accept(item);
        if (Registries.ITEM.getKey(item).isEmpty()) {
            if (Registries.ITEM.containsId(id)) throw new IllegalArgumentException("Duplicate pipe item: " + id);
            Registry.register(Registries.ITEM, id, item);
        }
        if (definitions.get(definition.identifier) == definition) setItemForPipe(definition, item);
        ItemPipeHolder.items.add(item);
        return item;
    }

    @Override
    public IItemPipe getItemForPipe(PipeDefinition definition) {
        return pipeItems.get(definition);
    }

    @Override
    @Nullable
    public PipeDefinition getDefinition(Identifier identifier) {
        return definitions.get(identifier);
    }

    @Nonnull
    public PipeDefinition loadDefinition(String identifier) throws InvalidInputDataException {
        PipeDefinition def = getDefinition(new Identifier(identifier));
        if (def == null) {
            throw new InvalidInputDataException("Unknown pipe definition " + identifier);
        }
        return def;
    }

    @Override
    public Iterable<PipeDefinition> getAllRegisteredPipes() {
        return ImmutableList.copyOf(definitions.values());
    }
}
