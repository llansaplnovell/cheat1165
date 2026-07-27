package com.example.examplemod.client.hud;

import com.mojang.blaze3d.matrix.MatrixStack;

/**
 * A single draggable HUD overlay piece (coordinates, fps, armor, ...).
 * Position is stored as scaled-GUI-resolution pixels from the top-left corner.
 */
public abstract class HudElement {

    private final String name;
    private float x;
    private float y;
    private boolean enabled;

    protected HudElement(String name, float defaultX, float defaultY, boolean enabledByDefault) {
        this.name = name;
        this.x = defaultX;
        this.y = defaultY;
        this.enabled = enabledByDefault;
    }

    /** Draws the element with its top-left corner at ({@link #getX()}, {@link #getY()}). */
    public abstract void render(MatrixStack matrixStack, float partialTicks);

    /** Rough footprint used by the HUD editor for drag handles and overlap avoidance. */
    public abstract int getWidth();

    public abstract int getHeight();

    public String getName() {
        return name;
    }

    public float getX() {
        return x;
    }

    public void setX(float x) {
        this.x = x;
    }

    public float getY() {
        return y;
    }

    public void setY(float y) {
        this.y = y;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
