package com.example.examplemod.client.hud.impl;

import com.example.examplemod.client.hud.HudElement;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;

/** Self-contained frame counter; avoids depending on vanilla's private debug-screen FPS tracker. */
public class FpsHud extends HudElement {

    private int frames;
    private int lastFps;
    private long windowStart = System.currentTimeMillis();

    public FpsHud() {
        super("FPS", 4, 36, true);
    }

    @Override
    public void render(MatrixStack matrixStack, float partialTicks) {
        frames++;
        long now = System.currentTimeMillis();
        if (now - windowStart >= 1000) {
            lastFps = frames;
            frames = 0;
            windowStart = now;
        }

        String text = lastFps + " fps";
        Minecraft minecraft = Minecraft.getInstance();
        int width = minecraft.font.width(text) + 8;
        AbstractGui.fill(matrixStack, (int) getX(), (int) getY(), (int) getX() + width, (int) getY() + 12, 0x90101014);
        minecraft.font.drawShadow(matrixStack, text, getX() + 4, getY() + 2, 0xFFFFFFFF);
    }

    @Override
    public int getWidth() {
        return 50;
    }

    @Override
    public int getHeight() {
        return 12;
    }
}
