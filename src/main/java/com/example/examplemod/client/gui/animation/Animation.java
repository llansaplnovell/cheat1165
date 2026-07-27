package com.example.examplemod.client.gui.animation;

import java.util.function.Function;

/**
 * A single float that eases towards a target value over real time, e.g. a
 * dropdown's open height or a tab's hover highlight. Frame-rate independent:
 * call {@link #get()} every render, it advances itself based on
 * {@link System#currentTimeMillis()}.
 */
public class Animation {

    private final long durationMillis;
    private final Function<Float, Float> easing;

    private float start;
    private float target;
    private long startTime;

    public Animation(long durationMillis, Function<Float, Float> easing) {
        this.durationMillis = durationMillis;
        this.easing = easing;
    }

    public Animation(long durationMillis) {
        this(durationMillis, Easing::easeOutQuad);
    }

    public void setTarget(float target) {
        if (this.target == target) {
            return;
        }
        this.start = get();
        this.target = target;
        this.startTime = System.currentTimeMillis();
    }

    public float get() {
        if (durationMillis <= 0) {
            return target;
        }
        float progress = (System.currentTimeMillis() - startTime) / (float) durationMillis;
        if (progress >= 1) {
            return target;
        }
        return start + (target - start) * easing.apply(Math.max(0, progress));
    }

    public boolean isAnimating() {
        return get() != target;
    }
}
