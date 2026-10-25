/* Copyright (c) 2026 FelixMantra. Licensed under the Mozilla Public License, v. 2.0. */
package buildcraft.lib.config;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** BuildCraft's file-backed configuration. Reads the historical typed .cfg syntax without Forge. */
public final class Configuration {
    public static final String CATEGORY_GENERAL = "general";
    private static final String NAME = "(?:\"(?:[^\"\\\\]|\\\\.)*\"|[^=<>\\{\\}]+?)";
    private static final Pattern ENTRY = Pattern.compile("^([SIBDCM]):(" + NAME + ")\\s*(=|<)(.*)$");
    private final File file;
    private final Map<String, ConfigCategory> categories = new LinkedHashMap<>();
    private boolean changed;

    public Configuration(File file) { this.file = file; load(); }
    public Configuration(File file, String version) { this(file); }

    private Property bind(String category, Property schema) {
        ConfigCategory cat = getCategory(category);
        Property property = cat.get(schema.getName());
        if (property == null) { cat.put(schema.getName(), schema); return schema; }
        property.configure(schema);
        return property;
    }
    public Property get(String category, String key, boolean value) { return bind(category, new Property(key, Boolean.toString(value), Property.Type.BOOLEAN)); }
    public Property get(String category, String key, int value) { return bind(category, new Property(key, Integer.toString(value), Property.Type.INTEGER)); }
    public Property get(String category, String key, double value) { return bind(category, new Property(key, Double.toString(value), Property.Type.DOUBLE)); }
    public Property get(String category, String key, String value) { return bind(category, new Property(key, value, Property.Type.STRING)); }
    public Property get(String category, String key, String[] values) { return bind(category, new Property(key, values, Property.Type.STRING)); }
    public Property get(String category, String key, int[] values) {
        return bind(category, new Property(key, Arrays.stream(values).mapToObj(Integer::toString).toArray(String[]::new), Property.Type.INTEGER));
    }
    private Property comment(Property property, String comment) { property.setComment(comment); return property; }
    public Property get(String c, String k, boolean v, String comment) { return comment(get(c, k, v), comment); }
    public Property get(String c, String k, int v, String comment) { return comment(get(c, k, v), comment); }
    public Property get(String c, String k, double v, String comment) { return comment(get(c, k, v), comment); }
    public Property get(String c, String k, String v, String comment) { return comment(get(c, k, v), comment); }
    public Property get(String c, String k, String[] v, String comment) { return comment(get(c, k, v), comment); }
    public Property get(String c, String k, int[] v, String comment) { return comment(get(c, k, v), comment); }

    private static ConfigCategory category(Map<String, ConfigCategory> map, String qualifiedName) {
        ConfigCategory found = map.get(qualifiedName);
        if (found != null) return found;
        int split = qualifiedName.lastIndexOf('.');
        ConfigCategory parent = split < 0 ? null : category(map, qualifiedName.substring(0, split));
        String name = qualifiedName.substring(split + 1);
        if (name.isBlank()) throw new IllegalArgumentException("Empty config category");
        ConfigCategory created = new ConfigCategory(name, parent);
        map.put(qualifiedName, created);
        return created;
    }
    public ConfigCategory getCategory(String name) {
        if (!categories.containsKey(name)) changed = true;
        return category(categories, name);
    }
    public boolean hasCategory(String name) { return categories.containsKey(name); }
    public List<String> getCategoryNames() { return new ArrayList<>(categories.keySet()); }
    public boolean contains(String category, String key) { return hasCategory(category) && categories.get(category).containsKey(key); }
    public void removeCategory(ConfigCategory category) {
        String name = category.getQualifiedName();
        changed |= categories.keySet().removeIf(k -> k.equals(name) || k.startsWith(name + "."));
    }
    public void moveProperty(String from, String key, String to) {
        if (from.equals(to) || !contains(from, key)) return;
        Property old = getCategory(from).remove(key);
        Property target = getCategory(to).get(key);
        if (target == null) getCategory(to).put(key, old);
        else if (!target.wasLoaded()) target.readValues(old);
    }
    public void renameProperty(String category, String oldKey, String newKey) {
        if (oldKey.equals(newKey) || !contains(category, oldKey)) return;
        Property property = getCategory(category).remove(oldKey);
        Property target = getCategory(category).get(newKey);
        if (target != null) {
            if (!target.wasLoaded()) target.readValues(property);
            return;
        }
        property.rename(newKey);
        getCategory(category).put(newKey, property);
    }
    public boolean hasChanged() { return changed || categories.values().stream().anyMatch(ConfigCategory::hasChanged); }
    public File getConfigFile() { return file; }

    public void load() {
        if (!file.exists()) return;
        try {
            Map<String, ConfigCategory> loaded = parse(Files.readAllLines(file.toPath(), StandardCharsets.UTF_8));
            // Keep already-bound Property objects alive so reload listeners read fresh values.
            changed = false;
            for (ConfigCategory existing : categories.values()) {
                ConfigCategory incoming = loaded.get(existing.getQualifiedName());
                for (Property property : existing.values()) {
                    if (incoming == null || !incoming.containsKey(property.getName())) {
                        property.setToDefault();
                        changed = true;
                    } else changed |= property.readValues(incoming.get(property.getName()));
                }
                existing.markClean();
            }
            for (ConfigCategory incoming : loaded.values()) {
                ConfigCategory target = category(categories, incoming.getQualifiedName());
                for (Property property : incoming.values()) {
                    if (!target.containsKey(property.getName())) target.put(property.getName(), property);
                }
                target.markClean();
            }
        } catch (IOException e) { throw new UncheckedIOException("Cannot read config " + file, e); }
    }

    private Map<String, ConfigCategory> parse(List<String> lines) {
        Map<String, ConfigCategory> result = new LinkedHashMap<>();
        Deque<String> nesting = new ArrayDeque<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("~CONFIG_VERSION:")) continue;
            if (line.equals("}")) {
                if (nesting.isEmpty()) throw invalid(i, "Unexpected category end");
                nesting.removeLast();
                continue;
            }
            if (line.endsWith("{") && !line.matches("^[SIBDCM]:.*")) {
                nesting.addLast(unquote(line.substring(0, line.length() - 1).strip()));
                category(result, String.join(".", nesting));
                continue;
            }
            Matcher entry = ENTRY.matcher(line);
            if (!entry.matches() || nesting.isEmpty()) throw invalid(i, "Expected a typed setting or category");
            String name = unquote(entry.group(2).strip());
            Property.Type type = Property.Type.fromCode(entry.group(1).charAt(0));
            Property property;
            if (entry.group(3).equals("=")) property = new Property(name, unquote(entry.group(4)), type);
            else {
                if (!entry.group(4).isBlank()) throw invalid(i, "Unexpected text after list start");
                List<String> values = new ArrayList<>();
                boolean ended = false;
                while (++i < lines.size()) {
                    String value = lines.get(i).strip();
                    if (value.equals(">")) { ended = true; break; }
                    // Quoted values preserve whitespace, empty strings, '#' and '>' literally.
                    if (value.isEmpty() || value.startsWith("#")) continue;
                    values.add(unquote(value));
                }
                if (!ended) throw invalid(i, "Unterminated list");
                property = new Property(name, values.toArray(String[]::new), type);
            }
            ConfigCategory cat = category(result, String.join(".", nesting));
            property.markLoaded();
            if (cat.containsKey(name)) throw invalid(i, "Duplicate setting " + name);
            cat.put(name, property);
        }
        if (!nesting.isEmpty()) throw invalid(lines.size(), "Unterminated category");
        return result;
    }
    private IllegalArgumentException invalid(int line, String detail) {
        return new IllegalArgumentException(file + ":" + (line + 1) + ": " + detail);
    }
    private static String unquote(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            StringBuilder result = new StringBuilder();
            for (int i = 1; i < value.length() - 1; i++) {
                char c = value.charAt(i);
                if (c == '\\' && i + 1 < value.length() - 1) c = value.charAt(++i);
                result.append(c);
            }
            return result.toString();
        }
        return value;
    }
    private static String quote(String value) { return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }

    public void save() {
        Path destination = file.toPath().toAbsolutePath();
        Path temporary = null;
        try {
            Files.createDirectories(destination.getParent());
            StringBuilder output = new StringBuilder("# BuildCraft configuration. Remake by FelixMantra.\n\n");
            for (ConfigCategory category : categories.values()) if (category.parent == null) writeCategory(output, category, "");
            temporary = Files.createTempFile(destination.getParent(), file.getName() + ".", ".tmp");
            Files.writeString(temporary, output, StandardCharsets.UTF_8);
            try { Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING); }
            changed = false;
            categories.values().forEach(ConfigCategory::markClean);
        } catch (IOException e) { throw new UncheckedIOException("Cannot save config " + file, e); }
        finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
        }
    }
    private void writeCategory(StringBuilder output, ConfigCategory category, String indent) {
        writeComment(output, category.getComment(), indent);
        output.append(indent).append(quote(category.getName())).append(" {\n");
        for (Property property : category.values()) {
            writeComment(output, property.getComment(), indent + "    ");
            output.append(indent).append("    ").append(property.valueType().code).append(':').append(quote(property.getName()));
            if (property.isList()) {
                output.append(" <\n");
                for (String value : property.rawValues()) output.append(indent).append("        ").append(quote(value)).append('\n');
                output.append(indent).append("    >\n");
            } else output.append('=').append(property.valueType() == Property.Type.STRING
                ? quote(property.getString()) : property.getString()).append('\n');
        }
        for (ConfigCategory child : categories.values()) if (child.parent == category) writeCategory(output, child, indent + "    ");
        output.append(indent).append("}\n\n");
    }
    private static void writeComment(StringBuilder output, String comment, String indent) {
        if (!comment.isEmpty()) for (String line : comment.split("\\R")) output.append(indent).append("# ").append(line).append('\n');
    }
}
