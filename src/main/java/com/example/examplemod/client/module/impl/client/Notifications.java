package com.example.examplemod.client.module.impl.client;

import com.example.examplemod.client.Client;
import com.example.examplemod.client.api.module.Category;
import com.example.examplemod.client.api.module.Module;
import com.example.examplemod.client.api.module.ModuleInfo;

@ModuleInfo(name = "Notifications", description = "Toast pop-ups for command feedback and connection events", category = Category.CLIENT)
public class Notifications extends Module {

    public Notifications() {
        setEnabled(true);
    }

    @Override
    protected void onEnable() {
        Client.getInstance().getNotificationManager().setVisible(true);
    }

    @Override
    protected void onDisable() {
        Client.getInstance().getNotificationManager().setVisible(false);
    }
}
