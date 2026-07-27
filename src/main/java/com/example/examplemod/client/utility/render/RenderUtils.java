package com.example.examplemod.client.utility.render;

/** Small rendering helpers shared by HUD elements and the ClickGUI. */
public final class RenderUtils {

    private RenderUtils() {
    }

    /** Linearly interpolates between two ARGB colors. */
    public static int lerpColor(int from, int to, float progress) {
        float clamped = Math.max(0, Math.min(1, progress));
        int a = lerpChannel(from >> 24, to >> 24, clamped);
        int r = lerpChannel(from >> 16, to >> 16, clamped);
        int g = lerpChannel(from >> 8, to >> 8, clamped);
        int b = lerpChannel(from, to, clamped);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int lerpChannel(int from, int to, float progress) {
        int a = from & 0xFF;
        int b = to & 0xFF;
        return a + Math.round((b - a) * progress);
    }

    public static int withAlpha(int argb, int alpha) {
        return (argb & 0xFFFFFF) | ((alpha & 0xFF) << 24);
    }
}
