package com.example.examplemod.client.api.setting;

/**
 * A secondary keybind owned by a setting rather than the module itself,
 * e.g. a "panic key" bound independently of the module's own toggle key.
 */
public class KeySetting extends Setting<Integer> {

    public static final int UNBOUND = -1;

    public KeySetting(String name, String description, int defaultKey) {
        super(name, description, defaultKey);
    }

    public KeySetting(String name, int defaultKey) {
        this(name, "", defaultKey);
    }

    public boolean isBound() {
        return get() != UNBOUND;
    }
}
