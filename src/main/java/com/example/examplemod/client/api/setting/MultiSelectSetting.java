package com.example.examplemod.client.api.setting;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Several independent on/off choices out of a fixed list, rendered as a checkbox dropdown. */
public class MultiSelectSetting extends Setting<Set<String>> {

    private final List<String> options;

    public MultiSelectSetting(String name, String description, List<String> options, String... defaultSelected) {
        super(name, description, new LinkedHashSet<>(Arrays.asList(defaultSelected)));
        this.options = options;
    }

    public List<String> getOptions() {
        return options;
    }

    public boolean isSelected(String option) {
        return get().contains(option);
    }

    public void toggleOption(String option) {
        Set<String> current = get();
        if (!current.remove(option)) {
            current.add(option);
        }
    }
}
