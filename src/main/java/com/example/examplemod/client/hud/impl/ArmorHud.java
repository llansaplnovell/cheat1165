package com.example.examplemod.client.hud.impl;

import com.example.examplemod.client.hud.HudElement;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;

public class ArmorHud extends HudElement {

    private static final int SLOT_SIZE = 18;

    public ArmorHud() {
        super("Armor", 4, 52, true);
    }

    @Override
    public void render(MatrixStack matrixStack, float partialTicks) {
        ClientPlayerEntity player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        NonNullList<ItemStack> armor = player.inventory.armor;
        for (int i = 0; i < armor.size(); i++) {
            ItemStack stack = armor.get(armor.size() - 1 - i);
            if (stack.isEmpty()) {
                continue;
            }
            int x = (int) getX() + i * SLOT_SIZE;
            int y = (int) getY();
            Minecraft.getInstance().getItemRenderer().renderGuiItem(stack, x, y);
            Minecraft.getInstance().getItemRenderer().renderGuiItemDecorations(
                    Minecraft.getInstance().font, stack, x, y);
        }
    }

    @Override
    public int getWidth() {
        return SLOT_SIZE * 4;
    }

    @Override
    public int getHeight() {
        return SLOT_SIZE;
    }
}
