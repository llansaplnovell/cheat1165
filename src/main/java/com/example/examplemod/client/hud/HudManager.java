package com.example.examplemod.client.hud;

import com.example.examplemod.client.api.event.EventHandler;
import com.example.examplemod.client.event.Render2DEvent;
import com.example.examplemod.client.hud.impl.ArmorHud;
import com.example.examplemod.client.hud.impl.CoordsHud;
import com.example.examplemod.client.hud.impl.FpsHud;
import com.example.examplemod.client.hud.impl.KeystrokesHud;
import com.example.examplemod.client.hud.impl.PotionHud;
import com.example.examplemod.client.hud.impl.WatermarkHud;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Owns every {@link HudElement} and draws them each frame. Independent from
 * the module system's enable/disable lifecycle: elements are always eligible
 * to render, gated only by their own {@link HudElement#isEnabled()} flag and
 * this manager's master {@link #isVisible()} switch (toggled by the
 * {@code render.Hud} module).
 */
public final class HudManager {

    private final List<HudElement> elements = new ArrayList<>();
    private boolean visible = true;

    public HudManager() {
        registerDefaults();
    }

    private void registerDefaults() {
        register(new WatermarkHud());
        register(new CoordsHud());
        register(new FpsHud());
        register(new ArmorHud());
        register(new PotionHud());
        register(new KeystrokesHud());
    }

    private void register(HudElement element) {
        elements.add(element);
    }

    public List<HudElement> getElements() {
        return Collections.unmodifiableList(elements);
    }

    public HudElement getByName(String name) {
        for (HudElement element : elements) {
            if (element.getName().toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT))) {
                return element;
            }
        }
        return null;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    @EventHandler
    public void onRender2D(Render2DEvent event) {
        if (!visible) {
            return;
        }
        for (HudElement element : elements) {
            if (element.isEnabled()) {
                element.render(event.getMatrixStack(), event.getPartialTicks());
            }
        }
    }
}
