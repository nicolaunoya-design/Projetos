package com.ultracore.module;

import org.bukkit.Material;

/** Uma opção configurável de um módulo, editável pelo painel. */
public final class Setting {

    public enum Type { BOOLEAN, INTEGER, DOUBLE, TEXT, LIST }

    private final String key;
    private final Type type;
    private final Object defaultValue;
    private final Material icon;
    private final String name;
    private final String description;
    private final double min;
    private final double max;
    private final double step;

    public Setting(String key, Type type, Object defaultValue, Material icon, String name, String description,
                   double min, double max, double step) {
        this.key = key;
        this.type = type;
        this.defaultValue = defaultValue;
        this.icon = icon;
        this.name = name;
        this.description = description;
        this.min = min;
        this.max = max;
        this.step = step;
    }

    public String getKey() { return key; }
    public Type getType() { return type; }
    public Object getDefaultValue() { return defaultValue; }
    public Material getIcon() { return icon; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public double getMin() { return min; }
    public double getMax() { return max; }
    public double getStep() { return step; }
}
