package com.example.examplemod.client.module.impl.player;

import com.example.examplemod.client.api.event.EventHandler;
import com.example.examplemod.client.api.module.Category;
import com.example.examplemod.client.api.module.Module;
import com.example.examplemod.client.api.module.ModuleInfo;
import com.example.examplemod.client.event.MotionEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;

/**
 * Keeps the player sprinting forward without holding the sprint key, exactly
 * like manually double-tapping forward every tick would. Purely a client-side
 * input convenience: the server sees the same sprint state it would if the
 * key were held for real.
 */
@ModuleInfo(name = "Auto Sprint", description = "Sprints automatically while moving forward", category = Category.PLAYER)
public class AutoSprint extends Module {

    @EventHandler
    public void onMotion(MotionEvent event) {
        ClientPlayerEntity player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        if (event.getForward() > 0 && !event.isSneaking() && !player.isUsingItem()
                && player.getFoodData().getFoodLevel() > 6) {
            event.setSprinting(true);
        }
    }
}
