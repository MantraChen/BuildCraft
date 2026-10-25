/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * You can obtain a copy at https://mozilla.org/MPL/2.0/.
 */
package buildcraft.lib.client.guide;

import java.util.*;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceReloadListenerKeys;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.profiler.DummyProfiler;
import buildcraft.api.core.BCLog;
import buildcraft.lib.client.guide.data.JsonTypeTags;
import buildcraft.lib.client.guide.entry.*;
import buildcraft.lib.client.guide.loader.*;
import buildcraft.lib.client.guide.parts.*;
import buildcraft.lib.client.guide.parts.contents.*;
import buildcraft.lib.guide.*;
import buildcraft.lib.misc.ItemStackKey;
import buildcraft.lib.misc.LocaleUtil;
import buildcraft.lib.misc.search.*;
import buildcraft.lib.script.ReloadableRegistryManager;

/** Loads translated pages after script registries, then rebuilds the contents/search index. */
public enum GuideManager implements SimpleSynchronousResourceReloadListener {
    INSTANCE;
    public static final String DEFAULT_LANG = "en_us";
    public static final boolean DEBUG = Boolean.getBoolean("buildcraft.guide.debug");
    public static final Map<String, IPageLoader> PAGE_LOADERS = new LinkedHashMap<>();
    public static final GuideContentsData BOOK_ALL_DATA = new GuideContentsData(null);
    static { PAGE_LOADERS.put("md", MarkdownPageLoader.INSTANCE); }
    private final Map<Identifier, GuidePageFactory> pages = new HashMap<>();
    private final Map<ItemStackKey, GuidePageFactory> generatedPages = new LinkedHashMap<>(16, 0.75f, true);
    private final Map<GuideBook, Map<TypeOrder, ContentsNode>> contents = new HashMap<>();
    public final Set<Object> objectsAdded = new HashSet<>();
    public ISuffixArray<PageLink> quickSearcher = new SimpleSuffixArray<>();
    private final Set<PageLink> indexed = new HashSet<>();
    private Object recipeGeneration;
    private int generation;
    /** Native RecipeManager replaces its map on server synchronization, including /reload. */
    public void tickRecipes() {
        var client=MinecraftClient.getInstance();var handler=client.getNetworkHandler();
        if(client.world==null||handler==null){recipeGeneration=null;return;}
        Object current=((buildcraft.lib.recipe.RecipeGeneration)handler.getRecipeManager()).buildcraft$recipeGeneration();
        if(current!=recipeGeneration)reload();
    }
    public int getGeneration() { return generation; }

    @Override public Identifier getFabricId() { return new Identifier("buildcraftlib", "guide"); }
    @Override public Collection<Identifier> getFabricDependencies() {
        return List.of(ReloadableRegistryManager.RESOURCE_PACKS.getFabricId(), ResourceReloadListenerKeys.LANGUAGES,
            ResourceReloadListenerKeys.MODELS);
    }
    public void reload() { reload(MinecraftClient.getInstance().getResourceManager()); }
    @Override public void reload(ResourceManager resources) {
        var handler=MinecraftClient.getInstance().getNetworkHandler();
        recipeGeneration=handler==null?null:((buildcraft.lib.recipe.RecipeGeneration)handler.getRecipeManager()).buildcraft$recipeGeneration();
        pages.clear();
        generatedPages.clear();
        objectsAdded.clear();
        contents.clear();
        indexed.clear();
        quickSearcher = new SimpleSuffixArray<>();
        loadLanguage(resources, DEFAULT_LANG);
        String language = MinecraftClient.getInstance().getLanguageManager().getLanguage();
        if (!DEFAULT_LANG.equals(language)) loadLanguage(resources, language);
        Map<GuideBook, Set<String>> domains = new HashMap<>();
        domains.put(null, new HashSet<>());
        createBook(null);
        for (GuideBook book : GuideBookRegistry.INSTANCE.getAllEntries()) {
            createBook(book);
            domains.put(book, new HashSet<>());
        }
        for (var entry : GuidePageRegistry.INSTANCE.getReloadableEntryMap().entrySet()) {
            PageEntry<?> value = entry.getValue();
            domains.get(null).add(value.typeTags.domain);
            GuideBook book = GuideBookRegistry.INSTANCE.getBook(value.book.toString());
            if (book != null && domains.containsKey(book)) domains.get(book).add(value.typeTags.domain);
            GuidePageFactory factory = pages.get(entry.getKey());
            if (factory == null) continue;
            objectsAdded.add(value.getBasicValue());
            var icon = value.createDrawable();
            var link = new PageLinkNormal(new PageLine(icon, icon, 2, value.title, true), true, value.getTooltip(), factory);
            addChild(value.book, value.typeTags, link);
        }
        for (var entry : domains.entrySet()) {
            (entry.getKey() == null ? BOOK_ALL_DATA : entry.getKey().data).generate(entry.getValue());
        }
        ContentsNode others = new ContentsNode(LocaleUtil.localize("buildcraft.guide.contents.all_group"), 0);
        for (var bookEntry : contents.entrySet()) {
            if (bookEntry.getKey() == null || bookEntry.getKey().appendAllEntries)
                bookEntry.getValue().values().forEach(root -> root.addChild(others));
        }
        for (PageValueType<?> type : GuidePageRegistry.INSTANCE.types.values()) {
            type.iterateAllDefault((tags, link) -> {
                index(link);
                String title = LocaleUtil.localize(tags.type);
                ContentsNode node = (ContentsNode) others.getChild(title);
                if (node == null) { node = new ContentsNode(title, 1); others.addChild(node); }
                node.addChild(link);
            }, DummyProfiler.INSTANCE);
        }
        for (var map : contents.values()) map.values().forEach(ContentsNode::sort);
        quickSearcher.generate(DummyProfiler.INSTANCE);
        generation++;
        BCLog.logger.info("[lib.guide] Loaded {} guide pages and {} search entries", pages.size(), indexed.size());
    }
    private void loadLanguage(ResourceManager resources, String language) {
        for (var entry : GuidePageRegistry.INSTANCE.getReloadableEntryMap().entrySet()) {
            Identifier id = entry.getKey();
            for (var loader : PAGE_LOADERS.entrySet()) {
                Identifier path = new Identifier(id.getNamespace(), "compat/buildcraft/guide/" + language + "/"
                    + id.getPath() + "." + loader.getKey());
                var resource = resources.getResource(path);
                if (resource.isEmpty()) continue;
                try (var input = resource.get().getInputStream()) {
                    GuidePageFactory page = loader.getValue().loadPage(input, id, entry.getValue(), DummyProfiler.INSTANCE);
                    if (page != null) pages.put(id, page);
                    break;
                } catch (java.io.IOException | RuntimeException error) {
                    BCLog.logger.warn("[lib.guide] Cannot load {}", path, error);
                }
            }
        }
    }
    private void createBook(GuideBook book) {
        Map<TypeOrder, ContentsNode> orders = new LinkedHashMap<>();
        for (TypeOrder order : GuiGuide.SORTING_TYPES) orders.put(order, new ContentsNode("root", -1));
        contents.put(book, orders);
    }
    private void index(PageLink link) {
        if (indexed.add(link)) quickSearcher.add(link, link.getSearchName());
    }
    private void addChild(Identifier book, JsonTypeTags tags, PageLink link) {
        index(link);
        for (var bookEntry : contents.entrySet()) {
            if (bookEntry.getKey() != null && !bookEntry.getKey().name.equals(book)) continue;
            for (var orderEntry : bookEntry.getValue().entrySet()) {
                ContentsNode node = orderEntry.getValue();
                String[] path = tags.getOrdered(orderEntry.getKey());
                for (int depth = 0; depth < path.length; depth++) {
                    String title = LocaleUtil.localize(path[depth]);
                    ContentsNode child = (ContentsNode) node.getChild(title);
                    if (child == null) { child = new ContentsNode(title, depth); node.addChild(child); }
                    node = child;
                }
                node.addChild(link);
            }
        }
    }
    public GuidePageFactory getFactoryFor(Identifier id) { return pages.get(id); }
    public GuidePageFactory getFactoryFor(Object value) {
        if (value instanceof ItemStackValueFilter filter) value = filter.stack.baseStack;
        if (value instanceof ItemStackKey key) value = key.baseStack;
        if (value instanceof ItemStack stack) return getPageFor(stack);
        return pages.get(getEntryFor(value));
    }
    public static Identifier getEntryFor(Object value) {
        for (var entry : GuidePageRegistry.INSTANCE.getReloadableEntryMap().entrySet())
            if (entry.getValue().matches(value)) return entry.getKey();
        return null;
    }
    public GuidePageFactory getPageFor(ItemStack stack) {
        GuidePageFactory page = pages.get(getEntryFor(stack));
        if (page != null) return page;
        ItemStack copy = stack.copyWithCount(1);
        ItemStackKey key = new ItemStackKey(copy);
        page = generatedPages.computeIfAbsent(key, ignored -> GuidePageStandInRecipes.createFactory(copy));
        if (generatedPages.size() > 512) generatedPages.remove(generatedPages.keySet().iterator().next());
        return page;
    }
    public ContentsNodeGui getGuiContents(GuiGuide gui, GuidePageContents page, TypeOrder order) {
        Map<TypeOrder, ContentsNode> map = contents.get(gui.book);
        if (map == null) { createBook(gui.book); map = contents.get(gui.book); }
        ContentsNode node = map.get(order);
        node.resetVisibility();
        return new ContentsNodeGui(gui, node);
    }
}
