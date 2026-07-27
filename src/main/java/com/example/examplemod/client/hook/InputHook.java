package com.example.examplemod.client.hook;

import com.example.examplemod.client.Client;
import com.example.examplemod.client.api.event.EventBus;
import com.example.examplemod.client.api.module.BindType;
import com.example.examplemod.client.api.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.InputEvent.KeyInputEvent;
import net.minecraftforge.client.event.InputEvent.MouseInputEvent;
import net.minecraftforge.client.event.InputEvent.MouseScrollEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Translates raw Forge input events into {@link com.example.examplemod.client.event.InputEvent}s
 * and drives module keybinds. Registered once from {@link Client#initialize()}.
 */
public final class InputHook {

    public void register() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onKey(KeyInputEvent event) {
        com.example.examplemod.client.event.InputEvent internal = EventBus.getInstance().post(
                new com.example.examplemod.client.event.InputEvent(
                        com.example.examplemod.client.event.InputEvent.Type.KEY,
                        event.getKey(), event.getAction(), event.getModifiers()));
        if (!internal.isCancelled()) {
            handleBind(event.getKey(), event.getAction());
        }
    }

    @SubscribeEvent
    public void onMouseButton(MouseInputEvent event) {
        com.example.examplemod.client.event.InputEvent internal = EventBus.getInstance().post(
                new com.example.examplemod.client.event.InputEvent(
                        com.example.examplemod.client.event.InputEvent.Type.MOUSE_BUTTON,
                        event.getButton(), event.getAction(), event.getMods()));
        if (!internal.isCancelled()) {
            handleBind(event.getButton(), event.getAction());
        }
    }

    @SubscribeEvent
    public void onMouseScroll(MouseScrollEvent event) {
        int direction = (int) Math.signum(event.getScrollDelta());
        EventBus.getInstance().post(new com.example.examplemod.client.event.InputEvent(
                com.example.examplemod.client.event.InputEvent.Type.SCROLL, 0, direction, 0));
    }

    private void handleBind(int code, int action) {
        if (Minecraft.getInstance().screen != null) {
            return;
        }
        for (Module module : Client.getInstance().getModuleManager().getModules()) {
            if (module.getKeybind() == GLFW.GLFW_KEY_UNKNOWN || module.getKeybind() != code) {
                continue;
            }
            if (module.getBindType() == BindType.TOGGLE) {
                if (action == GLFW.GLFW_PRESS) {
                    module.toggle();
                }
            } else {
                if (action == GLFW.GLFW_PRESS) {
                    module.setEnabled(true);
                } else if (action == GLFW.GLFW_RELEASE) {
                    module.setEnabled(false);
                }
            }
        }
    }
}
