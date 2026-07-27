package com.example.examplemod.client.api.setting;

import java.util.function.BooleanSupplier;

/**
 * A single configurable value belonging to a
 * {@link com.example.examplemod.client.api.module.Module}. Rendered generically
 * by the ClickGUI ({@code gui.component}) based on its concrete subtype, and
 * persisted generically by {@link com.example.examplemod.client.manager.ConfigManager}.
 *
 * @param <T> the boxed value type this setting stores.
 */
public class Setting<T> {

    private final String name;
    private final String description;
    private final T defaultValue;
    private T value;
    private BooleanSupplier visible = () -> true;

    public Setting(String name, String description, T defaultValue) {
        this.name = name;
        this.description = description;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public T get() {
        return value;
    }

    public void set(T value) {
        this.value = value;
    }

    public T getDefaultValue() {
        return defaultValue;
    }

    public void reset() {
        this.value = defaultValue;
    }

    /**
     * Lets a setting hide itself in the ClickGUI depending on a sibling
     * setting, e.g. an "Outline width" slider only shown while "Outline" is on.
     */
    public Setting<T> visibleIf(BooleanSupplier predicate) {
        this.visible = predicate;
        return this;
    }

    public boolean isVisible() {
        return visible.getAsBoolean();
    }
}
