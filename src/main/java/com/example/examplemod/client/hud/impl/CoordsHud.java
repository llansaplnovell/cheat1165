package com.example.examplemod.client.hud.impl;

import com.example.examplemod.client.hud.HudElement;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.entity.player.ClientPlayerEntity;

public class CoordsHud extends HudElement {

    public CoordsHud() {
        super("Coordinates", 4, 20, true);
    }

    @Override
    public void render(MatrixStack matrixStack, float partialTicks) {
        ClientPlayerEntity player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        String text = String.format("X: %.1f Y: %.1f Z: %.1f",
                player.getX(), player.getY(), player.getZ());
        Minecraft minecraft = Minecraft.getInstance();
        int width = minecraft.font.width(text) + 8;
        AbstractGui.fill(matrixStack, (int) getX(), (int) getY(), (int) getX() + width, (int) getY() + 12, 0x90101014);
        minecraft.font.drawShadow(matrixStack, text, getX() + 4, getY() + 2, 0xFFFFFFFF);
    }

    @Override
    public int getWidth() {
        return 90;
    }

    @Override
    public int getHeight() {
        return 12;
    }
}
