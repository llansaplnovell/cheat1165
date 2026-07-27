package com.example.examplemod.client.api.setting;

/** An ARGB color, optionally animated as a rainbow, rendered as a color picker. */
public class ColorSetting extends Setting<Integer> {

    private boolean rainbow;
    private double rainbowSpeed = 1.0;

    public ColorSetting(String name, String description, int defaultArgb) {
        super(name, description, defaultArgb);
    }

    public ColorSetting(String name, int defaultArgb) {
        this(name, "", defaultArgb);
    }

    public boolean isRainbow() {
        return rainbow;
    }

    public void setRainbow(boolean rainbow) {
        this.rainbow = rainbow;
    }

    public double getRainbowSpeed() {
        return rainbowSpeed;
    }

    public void setRainbowSpeed(double rainbowSpeed) {
        this.rainbowSpeed = rainbowSpeed;
    }

    /** Resolves the effective color, cycling through hues when {@link #isRainbow()}. */
    public int resolve() {
        if (!rainbow) {
            return get();
        }
        float hue = (float) ((System.currentTimeMillis() % (10000 / rainbowSpeed)) / (10000 / rainbowSpeed));
        int rgb = java.awt.Color.HSBtoRGB(hue, 0.8f, 1.0f);
        int alpha = (get() >> 24) & 0xFF;
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }
}
