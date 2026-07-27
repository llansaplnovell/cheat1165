package com.example.examplemod.client.config;

import java.util.LinkedHashMap;
import java.util.Map;

/** Persisted state of a single module: on/off, its keybind, and every setting flattened to a string. */
public class ModuleEntry {

    public String name;
    public boolean enabled;
    public int keybind;
    public Map<String, String> settings = new LinkedHashMap<>();
}
