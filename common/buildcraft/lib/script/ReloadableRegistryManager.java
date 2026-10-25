package buildcraft.lib.script;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import buildcraft.api.registry.BuildCraftRegistryManager;
import buildcraft.api.registry.EventBuildCraftReload;
import buildcraft.api.registry.IReloadableRegistry;
import buildcraft.api.registry.IReloadableRegistry.PackType;
import buildcraft.api.registry.IReloadableRegistryManager;
import buildcraft.api.registry.IScriptableRegistry;

import buildcraft.lib.misc.JsonUtil;

public enum ReloadableRegistryManager implements IReloadableRegistryManager, SimpleSynchronousResourceReloadListener {
    DATA_PACKS(PackType.DATA_PACK),
    RESOURCE_PACKS(PackType.RESOURCE_PACK);

    private static boolean isLoadingAll;

    private final PackType sourceType;
    private final BiMap<String, IReloadableRegistry<?>> registries = HashBiMap.create();
    private boolean isReloading;
    private int reloadCount = 0;
    private ResourceManager resourceManager;

    ReloadableRegistryManager(PackType sourceType) {
        this.sourceType = sourceType;
    }

    @Override public Identifier getFabricId() {
        return new Identifier("buildcraft", "scripts/" + sourceType.prefix);
    }

    @Override public void reload(ResourceManager manager) {
        resourceManager = manager;
        reloadAll();
    }

    ResourceManager getResourceManager() { return resourceManager; }

    public static void loadAll() {
        try {
            isLoadingAll = true;

            DATA_PACKS.reloadAll();
            if (BuildCraftRegistryManager.managerResourcePacks != null) {
                RESOURCE_PACKS.reloadAll();
            }

        } finally {
            isLoadingAll = false;
        }
    }

    @Override
    public PackType getType() {
        return sourceType;
    }

    @Override
    public boolean isLoadingAll() {
        return isLoadingAll;
    }

    /** Reloads every registry. Generally calling this is a very bad idea if you don't also call other reload events. */
    public void reloadAll() {
        reload(new HashSet<>(registries.values()));
    }

    @Override
    public void reload(IReloadableRegistry<?> registry) {
        reload(Collections.singleton(registry));
    }

    @Override
    public void reload(IReloadableRegistry<?>... all) {
        reload(new HashSet<>(Arrays.asList(all)));
    }

    @Override
    public void reload(Set<IReloadableRegistry<?>> set) {
        if (isInReload()) {
            throw new IllegalStateException("Cannot reload while we are reloading!");
        }
        set = Set.copyOf(set);
        for (IReloadableRegistry<?> registry : set) {
            if (registry.getManager() != this) throw new IllegalArgumentException("Registry belongs to another manager");
        }
        List<Runnable> rollback = set.stream().map(ReloadableRegistryManager::saveEntries).toList();
        try {
            isReloading = true;
            RegistryReloadEvents.EVENT.invoker().onReload(new EventBuildCraftReload.BeforeClear(this, set));
            set.forEach(registry -> registry.getReloadableEntryMap().clear());

            RegistryReloadEvents.EVENT.invoker().onReload(new EventBuildCraftReload.PreLoad(this, set));

            GsonBuilder builder = new GsonBuilder();
            // register our own types here, so that others can replace them
            JsonUtil.registerTypeAdaptors(builder);

            RegistryReloadEvents.EVENT.invoker().onReload(new EventBuildCraftReload.PopulateGson(this, set, builder));
            Gson gson = builder.create();

            for (IReloadableRegistry<?> registry : set) {
                if (registry instanceof ScriptableRegistry<?>) {
                    ((ScriptableRegistry<?>) registry).loadScripts(gson);
                }
            }

            RegistryReloadEvents.EVENT.invoker().onReload(new EventBuildCraftReload.PostLoad(this, set));
        } catch (RuntimeException | Error failure) {
            rollback.forEach(Runnable::run);
            throw failure;
        } finally {
            reloadCount++;
            isReloading = false;
        }
        RegistryReloadEvents.EVENT.invoker().onReload(new EventBuildCraftReload.FinishLoad(this, set));
    }

    private static <T> Runnable saveEntries(IReloadableRegistry<T> registry) {
        Map<Identifier, T> before = new HashMap<>(registry.getReloadableEntryMap());
        return () -> {
            registry.getReloadableEntryMap().clear();
            registry.getReloadableEntryMap().putAll(before);
        };
    }

    @Override
    public boolean isInReload() {
        return isReloading;
    }

    @Override
    public int getReloadCount() {
        return reloadCount;
    }

    @Override
    public Map<String, IReloadableRegistry<?>> getAllRegistries() {
        return Collections.unmodifiableMap(registries);
    }

    @Override
    public <R> IReloadableRegistry<R> createRegistry(String name) {
        SimpleReloadableRegistry<R> registry = new SimpleReloadableRegistry<>(this);
        validateName(name);
        registries.put(name, registry);
        return registry;
    }

    @Override
    public <R> IScriptableRegistry<R> createScriptableRegistry(String entryPath) {
        ScriptableRegistry<R> registry = new ScriptableRegistry<>(this, entryPath);
        registerRegistry(registry);
        return registry;
    }

    @Override
    public void registerRegistry(String entryType, IScriptableRegistry<?> registry) {
        validateName(entryType);
        if (registry.getManager() != this) throw new IllegalArgumentException("Registry belongs to another manager");
        registries.put(entryType, registry);
    }

    private void validateName(String entryType) {
        if (entryType.indexOf(':') != -1) {
            throw new IllegalArgumentException(
                "The entry type must be a valid resource path! (so it must not contain a colon)");
        }
        if (registries.containsKey(entryType)) throw new IllegalArgumentException("Duplicate registry: " + entryType);
    }
}
