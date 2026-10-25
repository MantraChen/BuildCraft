/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team.
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 */
package buildcraft.lib.client.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourceReloadListenerKeys;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import buildcraft.api.core.BCDebugging;
import buildcraft.lib.misc.data.ModelVariableData;

/** Reloads custom geometry after Minecraft has rebuilt its atlases. */
@Environment(EnvType.CLIENT)
public final class ModelHolderRegistry {
    public static final boolean DEBUG = BCDebugging.shouldDebugLog("lib.model.holder");
    static final List<ModelHolder> HOLDERS = new ArrayList<>();
    private static boolean registered;

    public static void registerReloadListener() {
        if (registered) return;
        registered = true;
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(
            new SimpleSynchronousResourceReloadListener() {
                @Override public Identifier getFabricId() { return new Identifier("buildcraftlib", "variable_models"); }
                @Override public Collection<Identifier> getFabricDependencies() {
                    return List.of(ResourceReloadListenerKeys.MODELS);
                }
                @Override public void reload(ResourceManager resources) {
                    // Atlas source JSON includes the legacy texture directories before this phase.
                    onTextureStitchPre(new HashSet<>());
                    onModelBake();
                }
            });
    }

    public static void onTextureStitchPre(Set<Identifier> toStitch) {
        for (ModelHolder holder : HOLDERS) holder.onTextureStitchPre(toStitch);
    }

    public static void onModelBake() {
        for (ModelHolder holder : HOLDERS) holder.onModelBake();
        ModelVariableData.onModelBake();
    }
}
