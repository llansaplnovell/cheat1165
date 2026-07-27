package com.example.examplemod.client.hud.impl;

import com.example.examplemod.client.hud.HudElement;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;

/**
 * Top-left brand tag. Change {@link #BRAND} once the project has a real name;
 * every other HUD element takes its style cues from this one.
 */
public class WatermarkHud extends HudElement {

    private static final String BRAND = "ExampleMod";

    public WatermarkHud() {
        super("Watermark", 4, 4, true);
    }

    @Override
    public void render(MatrixStack matrixStack, float partialTicks) {
        Minecraft minecraft = Minecraft.getInstance();
        String text = BRAND;
        int width = minecraft.font.width(text) + 8;
        AbstractGui.fill(matrixStack, (int) getX(), (int) getY(), (int) getX() + width, (int) getY() + 12, 0x90101014);
        minecraft.font.drawShadow(matrixStack, text, getX() + 4, getY() + 2, 0xFFFFFFFF);
    }

    @Override
    public int getWidth() {
        return Minecraft.getInstance().font.width(BRAND) + 8;
    }

    @Override
    public int getHeight() {
        return 12;
    }
}
