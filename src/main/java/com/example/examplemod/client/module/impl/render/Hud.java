package com.example.examplemod.client.module.impl.render;

import com.example.examplemod.client.Client;
import com.example.examplemod.client.api.module.Category;
import com.example.examplemod.client.api.module.Module;
import com.example.examplemod.client.api.module.ModuleInfo;

@ModuleInfo(name = "HUD", description = "Master switch for every HUD element", category = Category.RENDER)
public class Hud extends Module {

    public Hud() {
        setEnabled(true);
    }

    @Override
    protected void onEnable() {
        Client.getInstance().getHudManager().setVisible(true);
    }

    @Override
    protected void onDisable() {
        Client.getInstance().getHudManager().setVisible(false);
    }
}
