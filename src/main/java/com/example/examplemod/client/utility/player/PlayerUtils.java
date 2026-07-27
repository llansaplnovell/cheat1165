package com.example.examplemod.client.utility.player;

import net.minecraft.entity.player.PlayerEntity;

/** Common player-state checks shared across modules. */
public final class PlayerUtils {

    private PlayerUtils() {
    }

    public static boolean isUsingItem(PlayerEntity player) {
        return player.isUsingItem();
    }

    public static boolean isHungry(PlayerEntity player, int threshold) {
        return player.getFoodData().getFoodLevel() <= threshold;
    }

    public static boolean isFullHealth(PlayerEntity player) {
        return player.getHealth() >= player.getMaxHealth();
    }
}
