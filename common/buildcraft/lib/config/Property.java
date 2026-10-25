/* Copyright (c) 2026 FelixMantra. Licensed under the Mozilla Public License, v. 2.0. */
package buildcraft.lib.config;

import java.util.Arrays;
import java.util.Objects;

/** A typed setting with defaults, bounds and restart metadata. Lists are stored without delimiter loss. */
public final class Property {
    public enum Type { STRING('S'), INTEGER('I'), BOOLEAN('B'), DOUBLE('D'), COLOR('C'), MOD_ID('M');
        final char code;
        Type(char code) { this.code = code; }
        static Type fromCode(char code) {
            for (Type type : values()) if (type.code == code) return type;
            throw new IllegalArgumentException("Unknown config type: " + code);
        }
    }

    private String name;
    private Type type;
    private boolean list;
    private String[] values, defaults;
    private String comment = "", languageKey = "";
    private String[] validValues;
    private boolean changed, mcRestart, worldRestart, loaded;
    private double min = -Double.MAX_VALUE, max = Double.MAX_VALUE;

    public Property(String name, String value, Type type) { this(name, new String[] {value}, type, false); }
    public Property(String name, String[] values, Type type) { this(name, values, type, true); }
    private Property(String name, String[] values, Type type, boolean list) {
        this.name = Objects.requireNonNull(name);
        this.type = Objects.requireNonNull(type);
        this.list = list;
        this.values = checked(values);
        this.defaults = this.values.clone();
    }

    private static String[] checked(String[] values) {
        String[] copy = values.clone();
        for (String value : copy) {
            Objects.requireNonNull(value, "Config value");
            if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
                throw new IllegalArgumentException("Config values must fit on one line");
            }
        }
        return copy;
    }

    void configure(Property schema) {
        defaults = schema.defaults.clone();
        if (type != schema.type || list != schema.list) {
            type = schema.type;
            list = schema.list;
            setValues(defaults);
            changed = true;
        }
    }
    boolean readValues(Property loaded) {
        boolean repairedType = type != loaded.type || list != loaded.list;
        values = repairedType ? defaults.clone() : loaded.values.clone();
        this.loaded = true;
        changed = false;
        return repairedType;
    }
    void markLoaded() { loaded = true; }
    boolean wasLoaded() { return loaded; }
    void rename(String name) { this.name = name; changed = true; }
    void markClean() { changed = false; }
    String[] rawValues() { return values.clone(); }
    Type valueType() { return type; }
    public boolean isList() { return list; }
    public boolean hasChanged() { return changed; }
    public String getName() { return name; }
    public String getType() { return type.name(); }
    public String getDefault() { return defaults.length == 0 ? "" : defaults[0]; }
    public String getString() { return values.length == 0 ? "" : values[0]; }
    public String[] getStringList() { return values.clone(); }

    public boolean getBoolean() { return getBoolean(Boolean.parseBoolean(getDefault())); }
    public boolean getBoolean(boolean fallback) {
        String value = getString();
        if (value.equalsIgnoreCase("true")) return true;
        if (value.equalsIgnoreCase("false")) return false;
        set(fallback);
        return fallback;
    }
    public int getInt() { return getInt(parseInt(getDefault(), 0)); }
    public int getInt(int fallback) {
        int value = (int) Math.max(min, Math.min(max, parseInt(getString(), fallback)));
        set(value);
        return value;
    }
    public double getDouble() { return getDouble(parseDouble(getDefault(), 0)); }
    public double getDouble(double fallback) {
        double value = Math.max(min, Math.min(max, parseDouble(getString(), fallback)));
        set(value);
        return value;
    }
    private static int parseInt(String value, int fallback) {
        try { return Integer.parseInt(value.trim()); } catch (NumberFormatException e) { return fallback; }
    }
    private static double parseDouble(String value, double fallback) {
        try {
            double parsed = Double.parseDouble(value.trim());
            return Double.isFinite(parsed) ? parsed : fallback;
        } catch (NumberFormatException e) { return fallback; }
    }
    public int[] getIntList() {
        int[] result = new int[values.length];
        try {
            for (int i = 0; i < values.length; i++) result[i] = Integer.parseInt(values[i].trim());
            return result;
        } catch (NumberFormatException e) {
            result = new int[defaults.length];
            for (int i = 0; i < defaults.length; i++) result[i] = Integer.parseInt(defaults[i]);
            setValues(defaults);
            return result;
        }
    }
    private void setValues(String[] replacement) {
        String[] copy = checked(replacement);
        if (!Arrays.equals(values, copy)) { values = copy; changed = true; }
    }
    public void set(String value) { setValues(new String[] {value}); }
    public void set(boolean value) { set(Boolean.toString(value)); }
    public void set(int value) { set(Integer.toString(value)); }
    public void set(double value) { set(Double.toString(value)); }
    public void set(String[] values) { setValues(values); }
    public void set(int[] values) { setValues(Arrays.stream(values).mapToObj(Integer::toString).toArray(String[]::new)); }
    public void setValue(String value) { set(value); }
    public void setToDefault() { setValues(defaults); }
    public void setDefaultValue(String value) { defaults = checked(new String[] {value}); }
    public void setDefaultValue(boolean value) { setDefaultValue(Boolean.toString(value)); }
    public void setDefaultValue(int value) { setDefaultValue(Integer.toString(value)); }
    public void setDefaultValue(double value) { setDefaultValue(Double.toString(value)); }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = Objects.requireNonNull(comment); }
    public String[] getValidValues() { return validValues == null ? null : validValues.clone(); }
    public void setValidValues(String[] values) { validValues = values.clone(); }
    public void setValueEntries(String[] values) { setValidValues(values); }
    public boolean requiresMcRestart() { return mcRestart; }
    public boolean requiresWorldRestart() { return worldRestart; }
    public void setRequiresMcRestart(boolean value) { mcRestart = value; }
    public void setRequiresWorldRestart(boolean value) { worldRestart = value; }
    public Property setMinValue(int value) { return setMinValue((double) value); }
    public Property setMaxValue(int value) { return setMaxValue((double) value); }
    public Property setMinValue(double value) { min = value; return this; }
    public Property setMaxValue(double value) { max = value; return this; }
    public int getMinValue() { return (int) min; }
    public int getMaxValue() { return (int) max; }
    public Property setLanguageKey(String key) { languageKey = key; return this; }
    public String getLanguageKey() { return languageKey; }
    public double doubleValue() { return getDouble(); }
    public int intValue() { return getInt(); }
    public boolean booleanValue() { return getBoolean(); }
}
