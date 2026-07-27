package com.example.examplemod.client.event;

import com.example.examplemod.client.api.event.Event;

/**
 * Fired before the client turns raw keyboard state into a movement input for
 * the current tick. Listeners can freely rewrite the fields; the modified
 * values are what actually reach the player's movement code afterwards.
 */
public class MotionEvent extends Event {

    private float forward;
    private float strafe;
    private boolean jumping;
    private boolean sneaking;
    private boolean sprinting;

    public MotionEvent(float forward, float strafe, boolean jumping, boolean sneaking, boolean sprinting) {
        this.forward = forward;
        this.strafe = strafe;
        this.jumping = jumping;
        this.sneaking = sneaking;
        this.sprinting = sprinting;
    }

    public float getForward() {
        return forward;
    }

    public void setForward(float forward) {
        this.forward = forward;
    }

    public float getStrafe() {
        return strafe;
    }

    public void setStrafe(float strafe) {
        this.strafe = strafe;
    }

    public boolean isJumping() {
        return jumping;
    }

    public void setJumping(boolean jumping) {
        this.jumping = jumping;
    }

    public boolean isSneaking() {
        return sneaking;
    }

    public void setSneaking(boolean sneaking) {
        this.sneaking = sneaking;
    }

    public boolean isSprinting() {
        return sprinting;
    }

    public void setSprinting(boolean sprinting) {
        this.sprinting = sprinting;
    }
}
