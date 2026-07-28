package com.example.examplemod.client.api.setting;

import java.util.Arrays;
import java.util.List;

/** A single choice out of a fixed list of named modes, rendered as a click-to-cycle toggle. */
public class ModeSetting extends Setting<String> {

    private final List<String> modes;

    public ModeSetting(String name, String description, String defaultMode, String... modes) {
        super(name, description, defaultMode);
        this.modes = Arrays.asList(modes);
        if (!this.modes.contains(defaultMode)) {
            throw new IllegalArgumentException("Default mode '" + defaultMode + "' is not in " + this.modes);
        }
    }

    public ModeSetting(String name, String defaultMode, String... modes) {
        this(name, "", defaultMode, modes);
    }

    public List<String> getModes() {
        return modes;
    }

    public void cycle() {
        int next = (modes.indexOf(get()) + 1) % modes.size();
        set(modes.get(next));
    }

    public void previous() {
        int prev = (modes.indexOf(get()) - 1 + modes.size()) % modes.size();
        set(modes.get(prev));
    }

    public boolean is(String mode) {
        return get().equalsIgnoreCase(mode);
    }
}
