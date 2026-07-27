package com.example.examplemod.client.api.setting;

/** A simple on/off switch, rendered as a checkbox. */
public class BooleanSetting extends Setting<Boolean> {

    public BooleanSetting(String name, String description, boolean defaultValue) {
        super(name, description, defaultValue);
    }

    public BooleanSetting(String name, boolean defaultValue) {
        this(name, "", defaultValue);
    }

    public void toggle() {
        set(!get());
    }
}
