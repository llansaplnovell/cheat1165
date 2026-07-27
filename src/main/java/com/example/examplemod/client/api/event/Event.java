package com.example.examplemod.client.api.event;

/**
 * Base type for every event dispatched on the internal {@link EventBus}.
 * Forge events are bridged into this hierarchy so that modules never depend
 * directly on Forge/FML types.
 */
public class Event {

    private boolean handled;

    public boolean isHandled() {
        return handled;
    }

    public void setHandled(boolean handled) {
        this.handled = handled;
    }
}
