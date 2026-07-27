package com.example.examplemod.client.event;

import com.example.examplemod.client.api.event.Event;
import net.minecraft.client.world.ClientWorld;

import javax.annotation.Nullable;

/** Fired when the client joins, leaves or switches dimension/world. Modules use it to reset per-world state. */
public class WorldChangeEvent extends Event {

    @Nullable
    private final ClientWorld oldWorld;
    @Nullable
    private final ClientWorld newWorld;

    public WorldChangeEvent(@Nullable ClientWorld oldWorld, @Nullable ClientWorld newWorld) {
        this.oldWorld = oldWorld;
        this.newWorld = newWorld;
    }

    @Nullable
    public ClientWorld getOldWorld() {
        return oldWorld;
    }

    @Nullable
    public ClientWorld getNewWorld() {
        return newWorld;
    }
}
