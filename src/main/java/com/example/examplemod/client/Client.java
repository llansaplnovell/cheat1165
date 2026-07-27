package com.example.examplemod.client;

import com.example.examplemod.client.api.event.EventBus;
import com.example.examplemod.client.hook.ForgeEventBridge;
import com.example.examplemod.client.hook.InputHook;
import com.example.examplemod.client.hud.HudManager;
import com.example.examplemod.client.manager.CommandManager;
import com.example.examplemod.client.manager.ConfigManager;
import com.example.examplemod.client.manager.FriendManager;
import com.example.examplemod.client.manager.ModuleManager;
import com.example.examplemod.client.manager.NotificationManager;

/**
 * Single entry point / service locator for the whole client. Every manager
 * is created lazily inside {@link #initialize()} - called once from
 * {@code FMLClientSetupEvent} - rather than in field initializers, so that
 * {@link #getInstance()} is already valid by the time module constructors
 * (which run while {@link ModuleManager} builds itself) start calling back
 * into it.
 */
public final class Client {

    private static final Client INSTANCE = new Client();

    private FriendManager friendManager;
    private NotificationManager notificationManager;
    private HudManager hudManager;
    private ModuleManager moduleManager;
    private CommandManager commandManager;
    private ConfigManager configManager;

    private Client() {
    }

    public static Client getInstance() {
        return INSTANCE;
    }

    public void initialize() {
        friendManager = new FriendManager();
        notificationManager = new NotificationManager();
        hudManager = new HudManager();
        moduleManager = new ModuleManager();
        commandManager = new CommandManager(moduleManager, friendManager, notificationManager);
        configManager = new ConfigManager(moduleManager, friendManager, commandManager, hudManager);

        EventBus.getInstance().register(notificationManager);
        EventBus.getInstance().register(hudManager);
        new ForgeEventBridge().register();
        new InputHook().register();

        configManager.load();
    }

    public void shutdown() {
        configManager.save();
    }

    public ModuleManager getModuleManager() {
        return moduleManager;
    }

    public FriendManager getFriendManager() {
        return friendManager;
    }

    public NotificationManager getNotificationManager() {
        return notificationManager;
    }

    public HudManager getHudManager() {
        return hudManager;
    }

    public CommandManager getCommandManager() {
        return commandManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }
}
