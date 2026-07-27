package com.example.examplemod.client.manager;

import com.example.examplemod.client.api.module.Category;
import com.example.examplemod.client.api.module.Module;
import com.example.examplemod.client.module.impl.client.ClickGui;
import com.example.examplemod.client.module.impl.client.Notifications;
import com.example.examplemod.client.module.impl.misc.AutoReconnect;
import com.example.examplemod.client.module.impl.misc.ChatFilter;
import com.example.examplemod.client.module.impl.player.AutoSprint;
import com.example.examplemod.client.module.impl.player.FriendHighlight;
import com.example.examplemod.client.module.impl.render.Fullbright;
import com.example.examplemod.client.module.impl.render.Hud;
import com.example.examplemod.client.module.impl.render.ViewBobbing;
import com.example.examplemod.client.module.impl.render.Zoom;
import com.example.examplemod.client.module.impl.world.Waypoints;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Owns the lifecycle of every {@link Module}. This is the single place new
 * modules need to be plugged into - add the implementation under
 * {@code module.impl.<category>} and list an instance in {@link #registerDefaults()}.
 */
public final class ModuleManager {

    private final List<Module> modules = new ArrayList<>();

    public ModuleManager() {
        registerDefaults();
    }

    private void registerDefaults() {
        // client
        register(new ClickGui());
        register(new Notifications());
        // render
        register(new Hud());
        register(new Fullbright());
        register(new Zoom());
        register(new ViewBobbing());
        // world
        register(new Waypoints());
        // player
        register(new AutoSprint());
        register(new FriendHighlight());
        // misc
        register(new AutoReconnect());
        register(new ChatFilter());
    }

    private void register(Module module) {
        modules.add(module);
    }

    public List<Module> getModules() {
        return Collections.unmodifiableList(modules);
    }

    public List<Module> getModules(Category category) {
        List<Module> result = new ArrayList<>();
        for (Module module : modules) {
            if (module.getCategory() == category) {
                result.add(module);
            }
        }
        return result;
    }

    public Module getByName(String name) {
        for (Module module : modules) {
            if (module.getName().toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT))) {
                return module;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public <T extends Module> T get(Class<T> type) {
        for (Module module : modules) {
            if (type.isInstance(module)) {
                return (T) module;
            }
        }
        return null;
    }
}
