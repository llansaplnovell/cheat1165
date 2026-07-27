package com.example.examplemod.client.hud.impl;

import com.example.examplemod.client.hud.HudElement;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.potion.EffectInstance;

public class PotionHud extends HudElement {

    public PotionHud() {
        super("Potion Effects", 4, 74, true);
    }

    @Override
    public void render(MatrixStack matrixStack, float partialTicks) {
        ClientPlayerEntity player = Minecraft.getInstance().player;
        if (player == null || player.getActiveEffects().isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        int y = (int) getY();
        for (EffectInstance effect : player.getActiveEffects()) {
            String text = effect.getEffect().getDisplayName().getString() + " " + toRoman(effect.getAmplifier() + 1)
                    + " (" + formatDuration(effect.getDuration()) + ")";
            int width = minecraft.font.width(text) + 8;
            AbstractGui.fill(matrixStack, (int) getX(), y, (int) getX() + width, y + 12, 0x90101014);
            minecraft.font.drawShadow(matrixStack, text, getX() + 4, y + 2, 0xFFFFFFFF);
            y += 14;
        }
    }

    private static String formatDuration(int ticks) {
        int seconds = ticks / 20;
        return (seconds / 60) + ":" + String.format("%02d", seconds % 60);
    }

    private static String toRoman(int amplifier) {
        switch (amplifier) {
            case 1: return "I";
            case 2: return "II";
            case 3: return "III";
            case 4: return "IV";
            default: return String.valueOf(amplifier);
        }
    }

    @Override
    public int getWidth() {
        return 120;
    }

    @Override
    public int getHeight() {
        return 12;
    }
}
