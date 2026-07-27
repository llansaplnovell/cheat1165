package com.example.examplemod.client.event;

import com.example.examplemod.client.api.event.Event;
import com.mojang.blaze3d.matrix.MatrixStack;

/** Fired once per frame, after vanilla HUD rendering, for HUD elements and the ClickGUI overlay. */
public class Render2DEvent extends Event {

    private final MatrixStack matrixStack;
    private final float partialTicks;

    public Render2DEvent(MatrixStack matrixStack, float partialTicks) {
        this.matrixStack = matrixStack;
        this.partialTicks = partialTicks;
    }

    public MatrixStack getMatrixStack() {
        return matrixStack;
    }

    public float getPartialTicks() {
        return partialTicks;
    }
}
