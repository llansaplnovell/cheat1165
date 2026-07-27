package com.example.examplemod.client.gui.component;

import com.example.examplemod.client.api.module.Category;
import com.example.examplemod.client.api.module.Module;
import com.example.examplemod.client.gui.theme.Theme;
import com.example.examplemod.client.gui.theme.ThemeManager;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;

import java.util.ArrayList;
import java.util.List;

/** A draggable, collapsible column listing every module of one {@link Category}. */
public class Panel extends Component {

    private static final int HEADER_HEIGHT = 18;

    private final Category category;
    private final List<ModuleButton> buttons = new ArrayList<>();
    private boolean collapsed;
    private boolean dragging;
    private double dragOffsetX;
    private double dragOffsetY;

    public Panel(Category category, List<Module> modules, float x, float y, float width) {
        super(x, y, width, HEADER_HEIGHT);
        this.category = category;
        layoutButtons(modules);
    }

    private void layoutButtons(List<Module> modules) {
        float rowY = y + HEADER_HEIGHT;
        for (Module module : modules) {
            ModuleButton button = new ModuleButton(module, x, rowY, width);
            buttons.add(button);
            rowY += button.getHeight();
        }
    }

    public Category getCategory() {
        return category;
    }

    @Override
    public float getHeight() {
        if (collapsed) {
            return HEADER_HEIGHT;
        }
        float total = HEADER_HEIGHT;
        for (ModuleButton button : buttons) {
            total += button.getHeight();
        }
        return total;
    }

    @Override
    public void setPosition(float x, float y) {
        super.setPosition(x, y);
        relayout();
    }

    private void relayout() {
        float rowY = y + HEADER_HEIGHT;
        for (ModuleButton button : buttons) {
            button.setPosition(x, rowY);
            rowY += button.getHeight();
        }
    }

    @Override
    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        Theme theme = ThemeManager.getCurrent();
        AbstractGui.fill(matrixStack, (int) x, (int) y, (int) (x + width), (int) (y + HEADER_HEIGHT), theme.getPanelHeader());
        Minecraft.getInstance().font.drawShadow(matrixStack, category.getDisplayName(), x + 4, y + 5, theme.getTextPrimary());

        if (collapsed) {
            return;
        }
        float bottom = y + getHeight();
        AbstractGui.fill(matrixStack, (int) x, (int) (y + HEADER_HEIGHT), (int) (x + width), (int) bottom, theme.getPanelBackground());
        for (ModuleButton button : buttons) {
            button.render(matrixStack, mouseX, mouseY, partialTicks);
        }
    }

    private boolean isHeaderHovered(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + HEADER_HEIGHT;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isHeaderHovered(mouseX, mouseY)) {
            if (button == 1) {
                collapsed = !collapsed;
            } else {
                dragging = true;
                dragOffsetX = mouseX - x;
                dragOffsetY = mouseY - y;
            }
            return true;
        }
        if (!collapsed) {
            for (ModuleButton moduleButton : buttons) {
                if (moduleButton.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        boolean handled = false;
        for (ModuleButton moduleButton : buttons) {
            handled |= moduleButton.mouseReleased(mouseX, mouseY, button);
        }
        return handled;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging) {
            setPosition((float) (mouseX - dragOffsetX), (float) (mouseY - dragOffsetY));
            return true;
        }
        boolean handled = false;
        for (ModuleButton moduleButton : buttons) {
            handled |= moduleButton.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        return handled;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        for (ModuleButton moduleButton : buttons) {
            if (moduleButton.mouseScrolled(mouseX, mouseY, delta)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (ModuleButton moduleButton : buttons) {
            if (moduleButton.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        for (ModuleButton moduleButton : buttons) {
            if (moduleButton.charTyped(character, modifiers)) {
                return true;
            }
        }
        return false;
    }
}
