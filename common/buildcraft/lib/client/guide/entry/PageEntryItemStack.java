/* Copyright (c) 2017 SpaceToad and the BuildCraft team. MPL-2.0. */
package buildcraft.lib.client.guide.entry;

import java.util.*;
import com.google.gson.*;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.profiler.Profiler;
import buildcraft.api.registry.IScriptableRegistry.OptionallyDisabled;
import buildcraft.lib.client.guide.*;
import buildcraft.lib.client.guide.data.JsonTypeTags;
import buildcraft.lib.client.guide.loader.*;
import buildcraft.lib.client.guide.parts.GuidePart;
import buildcraft.lib.client.guide.parts.contents.*;
import buildcraft.lib.gui.*;
import buildcraft.lib.misc.GuiUtil;
import buildcraft.lib.misc.ItemStackKey;

public class PageEntryItemStack extends PageValueType<ItemStackValueFilter> {
    public static final PageEntryItemStack INSTANCE = new PageEntryItemStack();
    private static final JsonTypeTags TAGS = new JsonTypeTags("buildcraft.guide.contents.item_stacks");
    @Override public Class<ItemStackValueFilter> getEntryClass() { return ItemStackValueFilter.class; }
    @Override protected boolean isValid(ItemStackValueFilter filter) { return !filter.stack.baseStack.isEmpty(); }
    @Override public String getTitle(ItemStackValueFilter filter) { return filter.stack.baseStack.getName().getString(); }
    @Override public List<String> getTooltip(ItemStackValueFilter filter) { return GuiUtil.getFormattedTooltip(filter.stack.baseStack); }
    @Override public ISimpleDrawable createDrawable(ItemStackValueFilter filter) { return new GuiStack(filter.stack.baseStack); }
    @Override public Object getBasicValue(ItemStackValueFilter filter) { return filter.stack.baseStack.getItem(); }
    @Override public boolean matches(ItemStackValueFilter filter, Object value) {
        if (value instanceof ItemStackValueFilter other) value = other.stack.baseStack;
        if (value instanceof ItemStackKey key) value = key.baseStack;
        if (!(value instanceof ItemStack stack) || stack.isEmpty()) return false;
        ItemStack base = filter.stack.baseStack;
        return base.isOf(stack.getItem()) && (!filter.matchMeta || base.getDamage() == stack.getDamage())
            && (!filter.matchNbt || Objects.equals(base.getNbt(), stack.getNbt()));
    }
    @Override public void iterateAllDefault(IEntryLinkConsumer consumer, Profiler profiler) {
        Map<Item, List<ItemStack>> variants = new LinkedHashMap<>();
        for (ItemStack stack : ItemGroups.getSearchGroup().getSearchTabStacks())
            variants.computeIfAbsent(stack.getItem(), item -> new ArrayList<>()).add(stack);
        for (Item item : Registries.ITEM) {
            if (item == Items.AIR || !GuideManager.INSTANCE.objectsAdded.add(item)) continue;
            for (ItemStack stack : variants.getOrDefault(item, List.of(item.getDefaultStack()))) {
                if (!stack.isEmpty()) consumer.addChild(TAGS, PageLinkItemStack.create(false, stack.copy(), profiler));
            }
        }
    }
    @Override public OptionallyDisabled<PageLink> createLink(String text, Profiler profiler) {
        var parsed = MarkdownPageLoader.parseItemStack(text);
        return parsed.isPresent() ? new OptionallyDisabled<>(PageLinkItemStack.create(true, parsed.get(), profiler))
            : new OptionallyDisabled<>(parsed.getDisabledReason());
    }
    @Override public OptionallyDisabled<PageEntry<ItemStackValueFilter>> deserialize(Identifier id, JsonObject json,
            JsonDeserializationContext context) {
        if (!json.has("stack")) throw new JsonSyntaxException("Missing stack for " + id);
        JsonElement element = json.get("stack");
        String specification;
        if (element.isJsonPrimitive()) specification = element.getAsString();
        else {
            JsonObject object = element.getAsJsonObject();
            specification = object.get("item").getAsString() + ",1," + (object.has("damage") ? object.get("damage").getAsInt() : 0);
            if (object.has("nbt")) specification += "," + (object.get("nbt").isJsonPrimitive()
                ? object.get("nbt").getAsString() : object.get("nbt").toString());
        }
        var parsed = MarkdownPageLoader.parseItemStack(specification);
        if (!parsed.isPresent()) return new OptionallyDisabled<>(parsed.getDisabledReason());
        ItemStack stack = parsed.get();
        stack.setCount(1);
        var filter = new ItemStackValueFilter(new ItemStackKey(stack), stack.isDamageable(), stack.hasNbt());
        return new OptionallyDisabled<>(new PageEntry<>(this, id, json, filter));
    }
    @Override public void addPageEntries(ItemStackValueFilter filter, GuiGuide gui, List<GuidePart> parts) {
        XmlPageLoader.appendAllCrafting(filter.stack.baseStack, parts, gui, net.minecraft.util.profiler.DummyProfiler.INSTANCE);
    }
}
