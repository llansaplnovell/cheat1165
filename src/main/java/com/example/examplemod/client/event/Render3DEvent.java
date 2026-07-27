package com.example.examplemod.client.event;

import com.example.examplemod.client.api.event.Event;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.renderer.IRenderTypeBuffer;

/** Fired once per frame while the world is rendered, for world-space overlays (outlines, waypoints, ...). */
public class Render3DEvent extends Event {

    private final MatrixStack matrixStack;
    private final IRenderTypeBuffer.Impl buffer;
    private final float partialTicks;

    public Render3DEvent(MatrixStack matrixStack, IRenderTypeBuffer.Impl buffer, float partialTicks) {
        this.matrixStack = matrixStack;
        this.buffer = buffer;
        this.partialTicks = partialTicks;
    }

    public MatrixStack getMatrixStack() {
        return matrixStack;
    }

    public IRenderTypeBuffer.Impl getBuffer() {
        return buffer;
    }

    public float getPartialTicks() {
        return partialTicks;
    }
}
