package com.example.examplemod.client.event;

import com.example.examplemod.client.api.event.CancellableEvent;

/** Fired for raw keyboard/mouse activity, ahead of vanilla keybind handling. */
public class InputEvent extends CancellableEvent {

    public enum Type {
        KEY, MOUSE_BUTTON, SCROLL
    }

    private final Type type;
    private final int code;
    private final int action;
    private final int mods;

    public InputEvent(Type type, int code, int action, int mods) {
        this.type = type;
        this.code = code;
        this.action = action;
        this.mods = mods;
    }

    public Type getType() {
        return type;
    }

    /** GLFW key or mouse button code, depending on {@link #getType()}. */
    public int getCode() {
        return code;
    }

    /** GLFW action (press/release/repeat), or scroll direction sign for {@link Type#SCROLL}. */
    public int getAction() {
        return action;
    }

    public int getMods() {
        return mods;
    }
}
