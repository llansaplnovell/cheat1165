package com.example.examplemod.client.api.event;

/**
 * An event whose default outcome can be suppressed by a listener.
 * The dispatcher (see {@link EventBus#post(Event)}) is expected to check
 * {@link #isCancelled()} after every listener call and honour it.
 */
public class CancellableEvent extends Event {

    private boolean cancelled;

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    public void cancel() {
        setCancelled(true);
    }
}
