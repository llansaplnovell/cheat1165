package com.example.examplemod.client.module.impl.misc;

import com.example.examplemod.client.Client;
import com.example.examplemod.client.api.event.EventHandler;
import com.example.examplemod.client.api.module.Category;
import com.example.examplemod.client.api.module.Module;
import com.example.examplemod.client.api.module.ModuleInfo;
import com.example.examplemod.client.event.WorldChangeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.gui.screen.ConnectingScreen;
import net.minecraft.client.gui.screen.MainMenuScreen;

/**
 * Remembers the last multiplayer server so it can be rejoined with
 * {@code .reconnect} (see {@link com.example.examplemod.client.manager.CommandManager})
 * instead of navigating the multiplayer menu again after a disconnect.
 */
@ModuleInfo(name = "Auto Reconnect", description = "Remembers the last server for quick rejoining", category = Category.MISC)
public class AutoReconnect extends Module {

    private ServerData lastServer;

    public AutoReconnect() {
        setEnabled(true);
    }

    @EventHandler
    public void onWorldChange(WorldChangeEvent event) {
        if (event.getNewWorld() == null) {
            return;
        }
        ServerData data = Minecraft.getInstance().getCurrentServer();
        if (data != null) {
            lastServer = data;
        }
    }

    public boolean reconnect() {
        if (lastServer == null) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new ConnectingScreen(new MainMenuScreen(), minecraft, lastServer));
        return true;
    }
}
