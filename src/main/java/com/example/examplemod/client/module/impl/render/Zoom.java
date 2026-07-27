package com.example.examplemod.client.module.impl.render;

import com.example.examplemod.client.api.module.BindType;
import com.example.examplemod.client.api.module.Category;
import com.example.examplemod.client.api.module.Module;
import com.example.examplemod.client.api.module.ModuleInfo;
import com.example.examplemod.client.api.setting.NumberSetting;
import net.minecraft.client.GameSettings;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

@ModuleInfo(name = "Zoom", description = "Narrows the FOV while the key is held", category = Category.RENDER,
        defaultKey = GLFW.GLFW_KEY_C, bindType = BindType.HOLD)
public class Zoom extends Module {

    private final NumberSetting factor = register(new NumberSetting("Factor", 0.25, 0.05, 0.9, 0.05));

    private double previousFov = -1;

    @Override
    protected void onEnable() {
        GameSettings settings = Minecraft.getInstance().options;
        previousFov = settings.fov;
        settings.fov = previousFov * factor.get();
    }

    @Override
    protected void onDisable() {
        if (previousFov >= 0) {
            Minecraft.getInstance().options.fov = previousFov;
        }
    }
}
