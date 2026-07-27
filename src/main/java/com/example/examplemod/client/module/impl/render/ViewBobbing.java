package com.example.examplemod.client.module.impl.render;

import com.example.examplemod.client.api.module.Category;
import com.example.examplemod.client.api.module.Module;
import com.example.examplemod.client.api.module.ModuleInfo;
import net.minecraft.client.Minecraft;

@ModuleInfo(name = "No View Bobbing", description = "Disables the walk camera bob", category = Category.RENDER)
public class ViewBobbing extends Module {

    private boolean previousValue;

    @Override
    protected void onEnable() {
        previousValue = Minecraft.getInstance().options.bobView;
        Minecraft.getInstance().options.bobView = false;
    }

    @Override
    protected void onDisable() {
        Minecraft.getInstance().options.bobView = previousValue;
    }
}
