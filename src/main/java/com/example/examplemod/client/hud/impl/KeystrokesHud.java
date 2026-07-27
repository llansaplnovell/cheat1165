package com.example.examplemod.client.hud.impl;

import com.example.examplemod.client.hud.HudElement;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.GameSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.settings.KeyBinding;

public class KeystrokesHud extends HudElement {

    private static final int KEY_SIZE = 18;

    public KeystrokesHud() {
        super("Keystrokes", 4, 96, false);
    }

    @Override
    public void render(MatrixStack matrixStack, float partialTicks) {
        GameSettings settings = Minecraft.getInstance().options;
        int baseX = (int) getX();
        int baseY = (int) getY();

        drawKey(matrixStack, "W", baseX + KEY_SIZE, baseY, settings.keyUp);
        drawKey(matrixStack, "A", baseX, baseY + KEY_SIZE, settings.keyLeft);
        drawKey(matrixStack, "S", baseX + KEY_SIZE, baseY + KEY_SIZE, settings.keyDown);
        drawKey(matrixStack, "D", baseX + KEY_SIZE * 2, baseY + KEY_SIZE, settings.keyRight);
        drawKey(matrixStack, "", baseX + KEY_SIZE, baseY + KEY_SIZE * 2, settings.keyJump);
    }

    private void drawKey(MatrixStack matrixStack, String label, int x, int y, KeyBinding binding) {
        boolean down = binding.isDown();
        int background = down ? 0xC0FFFFFF : 0x90101014;
        int textColor = down ? 0xFF101014 : 0xFFFFFFFF;
        AbstractGui.fill(matrixStack, x, y, x + KEY_SIZE - 1, y + KEY_SIZE - 1, background);
        Minecraft minecraft = Minecraft.getInstance();
        int textWidth = minecraft.font.width(label);
        minecraft.font.drawShadow(matrixStack, label,
                x + (KEY_SIZE - 1 - textWidth) / 2f, y + 5, textColor);
    }

    @Override
    public int getWidth() {
        return KEY_SIZE * 3;
    }

    @Override
    public int getHeight() {
        return KEY_SIZE * 3;
    }
}
