package com.example.examplemod.client.module.impl.player;

import com.example.examplemod.client.Client;
import com.example.examplemod.client.api.event.EventHandler;
import com.example.examplemod.client.api.module.Category;
import com.example.examplemod.client.api.module.Module;
import com.example.examplemod.client.api.module.ModuleInfo;
import com.example.examplemod.client.event.UpdateEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.AbstractClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;

import java.util.HashSet;
import java.util.Set;

/** Toasts once when a player on the friend list ({@link com.example.examplemod.client.manager.FriendManager}) comes into view. */
@ModuleInfo(name = "Friend Highlight", description = "Notifies when a friend enters render distance", category = Category.PLAYER)
public class FriendHighlight extends Module {

    private final Set<String> nearby = new HashSet<>();

    @Override
    protected void onDisable() {
        nearby.clear();
    }

    @EventHandler
    public void onUpdate(UpdateEvent event) {
        ClientWorld world = Minecraft.getInstance().level;
        if (world == null) {
            return;
        }
        Set<String> current = new HashSet<>();
        for (AbstractClientPlayerEntity player : world.players()) {
            String name = player.getGameProfile().getName();
            if (!Client.getInstance().getFriendManager().isFriend(name)) {
                continue;
            }
            current.add(name);
            if (nearby.add(name)) {
                Client.getInstance().getNotificationManager().info(name + " is nearby");
            }
        }
        nearby.retainAll(current);
    }
}
