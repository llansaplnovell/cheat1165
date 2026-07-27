package com.example.examplemod.client.api.module;

import com.example.examplemod.client.api.event.EventBus;
import com.example.examplemod.client.api.setting.Setting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base class for every feature of the client. A module is a self-contained
 * unit that:
 * <ul>
 *     <li>declares its identity via the {@link ModuleInfo} annotation,</li>
 *     <li>exposes user-configurable {@link Setting}s,</li>
 *     <li>subscribes itself to {@link EventBus} only while enabled.</li>
 * </ul>
 * Concrete modules live under {@code module.impl.*} and typically only
 * override {@link #onEnable()}, {@link #onDisable()} and add
 * {@link com.example.examplemod.client.api.event.EventHandler} methods.
 */
public abstract class Module {

    private final String name;
    private final String description;
    private final Category category;
    private final List<Setting<?>> settings = new ArrayList<>();

    private int keybind;
    private BindType bindType;
    private boolean enabled;

    protected Module() {
        ModuleInfo info = getClass().getAnnotation(ModuleInfo.class);
        if (info == null) {
            throw new IllegalStateException(getClass().getName() + " is missing @ModuleInfo");
        }
        this.name = info.name();
        this.description = info.description();
        this.category = info.category();
        this.keybind = info.defaultKey();
        this.bindType = info.bindType();
    }

    /**
     * Toggles {@link #enabled} and fires {@link #onEnable()}/{@link #onDisable()}.
     * Safe to call from input handling, commands or the ClickGUI alike.
     */
    public final void toggle() {
        setEnabled(!enabled);
    }

    public final void setEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return;
        }
        this.enabled = enabled;
        if (enabled) {
            EventBus.getInstance().register(this);
            onEnable();
        } else {
            onDisable();
            EventBus.getInstance().unregister(this);
        }
    }

    /** Called once, right after this module starts listening for events. */
    protected void onEnable() {
    }

    /** Called once, right before this module stops listening for events. */
    protected void onDisable() {
    }

    protected final <T extends Setting<?>> T register(T setting) {
        settings.add(setting);
        return setting;
    }

    public final boolean isEnabled() {
        return enabled;
    }

    public final String getName() {
        return name;
    }

    public final String getDescription() {
        return description;
    }

    public final Category getCategory() {
        return category;
    }

    public final int getKeybind() {
        return keybind;
    }

    public final void setKeybind(int keybind) {
        this.keybind = keybind;
    }

    public final BindType getBindType() {
        return bindType;
    }

    public final void setBindType(BindType bindType) {
        this.bindType = bindType;
    }

    public final List<Setting<?>> getSettings() {
        return Collections.unmodifiableList(settings);
    }

    @Override
    public String toString() {
        return name;
    }
}
