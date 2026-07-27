package com.example.examplemod.client.module.impl.render;

import com.example.examplemod.client.api.module.Category;
import com.example.examplemod.client.api.module.Module;
import com.example.examplemod.client.api.module.ModuleInfo;
import net.minecraft.client.GameSettings;
import net.minecraft.client.Minecraft;

@ModuleInfo(name = "Fullbright", description = "Maxes out brightness while enabled", category = Category.RENDER)
public class Fullbright extends Module {

    private double previousGamma;

    @Override
    protected void onEnable() {
        GameSettings settings = Minecraft.getInstance().options;
        previousGamma = settings.gamma;
        settings.gamma = 100.0D;
    }

    @Override
    protected void onDisable() {
        Minecraft.getInstance().options.gamma = previousGamma;
    }
}
