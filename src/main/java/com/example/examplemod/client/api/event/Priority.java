package com.example.examplemod.client.api.event;

/**
 * Determines the order in which listeners for the same event type run.
 * Higher priority listeners run first, so they can mutate state (or cancel
 * the event) before lower priority listeners observe it.
 */
public enum Priority {

    HIGHEST(1000),
    HIGH(500),
    NORMAL(0),
    LOW(-500),
    LOWEST(-1000);

    private final int weight;

    Priority(int weight) {
        this.weight = weight;
    }

    public int getWeight() {
        return weight;
    }
}
