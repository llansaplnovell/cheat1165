package com.example.examplemod.client.event;

import com.example.examplemod.client.api.event.Event;

/**
 * Fired once per client tick, but only while a player and world are loaded.
 * Most gameplay-facing modules (movement, combat helpers) should listen to
 * this instead of the lower-level {@link TickEvent}.
 */
public class UpdateEvent extends Event {
}
