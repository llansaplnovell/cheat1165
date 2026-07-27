package com.example.examplemod.client.api.module;

/**
 * How a module's keybind is interpreted by {@link com.example.examplemod.client.hook.InputHook}.
 */
public enum BindType {
    /** Pressing the key flips {@code enabled}. */
    TOGGLE,
    /** The module is enabled only while the key is held down. */
    HOLD
}
