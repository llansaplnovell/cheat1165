package com.example.examplemod.client.gui;

import com.example.examplemod.client.Client;
import com.example.examplemod.client.api.module.Category;
import com.example.examplemod.client.api.module.Module;
import com.example.examplemod.client.gui.component.Panel;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.text.StringTextComponent;

import java.util.ArrayList;
import java.util.List;

/** The module-list screen opened by the {@code client.ClickGui} module. One {@link Panel} per {@link Category}. */
public class ClickGuiScreen extends Screen {

    private final List<Panel> panels = new ArrayList<>();

    public ClickGuiScreen() {
        super(new StringTextComponent("Client"));
    }

    @Override
    protected void init() {
        panels.clear();
        Category[] categories = Category.values();
        for (int i = 0; i < categories.length; i++) {
            List<Module> modules = Client.getInstance().getModuleManager().getModules(categories[i]);
            if (modules.isEmpty()) {
                continue;
            }
            panels.add(new Panel(categories[i], modules, GuiLayout.xFor(panels.size()), GuiLayout.getStartY(), GuiLayout.getPanelWidth()));
        }
    }

    @Override
    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        for (Panel panel : panels) {
            panel.render(matrixStack, mouseX, mouseY, partialTicks);
        }
        super.render(matrixStack, mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (Panel panel : panels) {
            if (panel.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean handled = false;
        for (Panel panel : panels) {
            handled |= panel.mouseReleased(mouseX, mouseY, button);
        }
        return handled || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        for (Panel panel : panels) {
            if (panel.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        for (Panel panel : panels) {
            if (panel.mouseScrolled(mouseX, mouseY, delta)) {
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (Panel panel : panels) {
            if (panel.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        for (Panel panel : panels) {
            if (panel.charTyped(character, modifiers)) {
                return true;
            }
        }
        return super.charTyped(character, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
