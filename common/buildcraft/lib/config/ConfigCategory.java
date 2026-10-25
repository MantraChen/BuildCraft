/* Copyright (c) 2026 FelixMantra. Licensed under the Mozilla Public License, v. 2.0. */
package buildcraft.lib.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ConfigCategory {
    private final String name;
    public final ConfigCategory parent;
    private final Map<String, Property> properties = new LinkedHashMap<>();
    private String comment = "", languageKey = "";
    private boolean showInGui = true, changed;

    public ConfigCategory(String name) { this(name, null); }
    ConfigCategory(String name, ConfigCategory parent) { this.name = name; this.parent = parent; }
    public String getName() { return name; }
    public String getQualifiedName() { return parent == null ? name : parent.getQualifiedName() + "." + name; }
    public boolean containsKey(String key) { return properties.containsKey(key); }
    public Property get(String key) { return properties.get(key); }
    public void put(String key, Property property) { properties.put(key, property); changed = true; }
    Property remove(String key) { Property removed = properties.remove(key); changed |= removed != null; return removed; }
    public Collection<Property> values() { return Collections.unmodifiableCollection(properties.values()); }
    public List<Property> getOrderedValues() { return new ArrayList<>(properties.values()); }
    public boolean isChild() { return parent != null; }
    public void setComment(String value) { comment = value; }
    public String getComment() { return comment; }
    public void setLanguageKey(String value) { languageKey = value; }
    public String getLanguagekey() { return languageKey; }
    public void setShowInGui(boolean value) { showInGui = value; }
    public boolean showInGui() { return showInGui; }
    boolean hasChanged() { return changed || properties.values().stream().anyMatch(Property::hasChanged); }
    void markClean() { changed = false; properties.values().forEach(Property::markClean); }
}
