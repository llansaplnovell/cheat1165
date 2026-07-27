package com.example.examplemod.client.module.impl.client;

import com.example.examplemod.client.api.module.Category;
import com.example.examplemod.client.api.module.Module;
import com.example.examplemod.client.api.module.ModuleInfo;
import com.example.examplemod.client.gui.ClickGuiScreen;
import net.minecraft.client.Minecraft;

@ModuleInfo(name = "ClickGUI", description = "Opens the module list screen", category = Category.CLIENT, defaultKey = 344)
public class ClickGui extends Module {

    @Override
    protected void onEnable() {
        Minecraft.getInstance().setScreen(new ClickGuiScreen());
        // momentary action, not a persistent state: flip back off immediately after opening the screen
        setEnabled(false);
    }
}
