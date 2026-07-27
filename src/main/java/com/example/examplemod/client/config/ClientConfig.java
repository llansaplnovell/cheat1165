package com.example.examplemod.client.config;

import java.util.ArrayList;
import java.util.List;

/** Root JSON schema persisted by {@link com.example.examplemod.client.manager.ConfigManager}. */
public class ClientConfig {

    public String prefix = ".";
    public List<String> friends = new ArrayList<>();
    public List<ModuleEntry> modules = new ArrayList<>();
    public List<HudEntry> hud = new ArrayList<>();
}
