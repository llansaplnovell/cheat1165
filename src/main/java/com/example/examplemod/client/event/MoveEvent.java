package com.example.examplemod.client.event;

import com.example.examplemod.client.api.event.CancellableEvent;

/**
 * Fired with the delta the local player is about to move by this tick.
 * Cancelling zeroes the movement for the tick.
 */
public class MoveEvent extends CancellableEvent {

    private double x;
    private double y;
    private double z;

    public MoveEvent(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public double getZ() {
        return z;
    }

    public void setZ(double z) {
        this.z = z;
    }
}
