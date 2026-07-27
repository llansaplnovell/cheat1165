package com.example.examplemod.client.event;

import com.example.examplemod.client.api.event.CancellableEvent;
import net.minecraft.entity.Entity;

/** Fired right before the local player attacks {@link #getTarget()}. Cancelling stops the swing. */
public class AttackEvent extends CancellableEvent {

    private final Entity target;

    public AttackEvent(Entity target) {
        this.target = target;
    }

    public Entity getTarget() {
        return target;
    }
}
