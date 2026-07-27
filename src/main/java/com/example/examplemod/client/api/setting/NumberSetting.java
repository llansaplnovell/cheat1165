package com.example.examplemod.client.api.setting;

/** A bounded numeric value, rendered as a slider. */
public class NumberSetting extends Setting<Double> {

    private final double min;
    private final double max;
    private final double step;

    public NumberSetting(String name, String description, double defaultValue, double min, double max, double step) {
        super(name, description, clamp(defaultValue, min, max));
        this.min = min;
        this.max = max;
        this.step = step;
    }

    public NumberSetting(String name, double defaultValue, double min, double max, double step) {
        this(name, "", defaultValue, min, max, step);
    }

    @Override
    public void set(Double value) {
        super.set(clamp(value, min, max));
    }

    public double getMin() {
        return min;
    }

    public double getMax() {
        return max;
    }

    public double getStep() {
        return step;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
