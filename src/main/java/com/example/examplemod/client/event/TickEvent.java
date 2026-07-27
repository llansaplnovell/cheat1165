package com.example.examplemod.client.event;

import com.example.examplemod.client.api.event.Event;

/** Fired every raw client tick, mirroring Forge's own client tick event. */
public class TickEvent extends Event {

    public enum Phase {
        START, END
    }

    private final Phase phase;

    public TickEvent(Phase phase) {
        this.phase = phase;
    }

    public Phase getPhase() {
        return phase;
    }
}
