package com.example.examplemod.client.gui.animation;

/** Standard easing curves, input and output both in {@code [0, 1]}. */
public final class Easing {

    private Easing() {
    }

    public static float linear(float t) {
        return t;
    }

    public static float easeOutQuad(float t) {
        return 1 - (1 - t) * (1 - t);
    }

    public static float easeInOutQuad(float t) {
        return t < 0.5f ? 2 * t * t : 1 - (float) Math.pow(-2 * t + 2, 2) / 2;
    }

    public static float easeOutCubic(float t) {
        return 1 - (float) Math.pow(1 - t, 3);
    }
}
