package com.example.examplemod.client.gui.component;

import com.example.examplemod.client.api.module.Module;
import com.example.examplemod.client.api.setting.Setting;
import com.example.examplemod.client.gui.animation.Animation;
import com.example.examplemod.client.gui.theme.Theme;
import com.example.examplemod.client.gui.theme.ThemeManager;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.gui.FontRenderer;

import java.util.ArrayList;
import java.util.List;

/** One row of the ClickGUI: a module's name, its enabled state, and an optional expandable settings list. */
public class ModuleButton extends Component {

    private static final int ROW_HEIGHT = 16;
    private static final int ARROW_ZONE = 16;

    private final Module module;
    private final List<SettingRow> settingRows = new ArrayList<>();
    private final Animation expandAnimation = new Animation(150);
    private boolean expanded;

    public ModuleButton(Module module, float x, float y, float width) {
        super(x, y, width, ROW_HEIGHT);
        this.module = module;
        float rowY = y + ROW_HEIGHT;
        for (Setting<?> setting : module.getSettings()) {
            SettingRow row = new SettingRow(setting, x, rowY, width);
            settingRows.add(row);
            rowY += row.getHeight();
        }
    }

    public Module getModule() {
        return module;
    }

    @Override
    public float getHeight() {
        return ROW_HEIGHT + expandAnimation.get();
    }

    @Override
    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        Theme theme = ThemeManager.getCurrent();
        FontRenderer font = Minecraft.getInstance().font;
        boolean hovered = isHovered(mouseX, mouseY);

        AbstractGui.fill(matrixStack, (int) x, (int) y, (int) (x + width), (int) (y + ROW_HEIGHT),
                hovered ? theme.getRowHovered() : theme.getRowBackground());
        if (module.isEnabled()) {
            AbstractGui.fill(matrixStack, (int) x, (int) y, (int) x + 2, (int) (y + ROW_HEIGHT), theme.getAccent());
        }
        font.drawShadow(matrixStack, module.getName(), x + 6, y + 4,
                module.isEnabled() ? theme.getTextPrimary() : theme.getTextSecondary());

        if (!settingRows.isEmpty()) {
            String arrow = expanded ? "-" : "+";
            font.drawShadow(matrixStack, arrow, x + width - 10, y + 4, theme.getTextSecondary());
        }

        float expandHeight = expandAnimation.get();
        if (expandHeight > 0.5f) {
            float rowY = y + ROW_HEIGHT;
            for (SettingRow row : settingRows) {
                if (!row.getSetting().isVisible()) {
                    continue;
                }
                row.setPosition(x, rowY);
                if (rowY < y + ROW_HEIGHT + expandHeight) {
                    row.render(matrixStack, mouseX, mouseY, partialTicks);
                }
                rowY += row.getHeight();
            }
        }
    }

    @Override
    public boolean isHovered(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + ROW_HEIGHT;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isHovered(mouseX, mouseY)) {
            if (!settingRows.isEmpty() && mouseX >= x + width - ARROW_ZONE) {
                expanded = !expanded;
                expandAnimation.setTarget(expanded ? computeSettingsHeight() : 0);
            } else {
                module.toggle();
            }
            return true;
        }
        if (expanded) {
            for (SettingRow row : settingRows) {
                if (row.getSetting().isVisible() && row.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!expanded) {
            return false;
        }
        boolean handled = false;
        for (SettingRow row : settingRows) {
            handled |= row.mouseReleased(mouseX, mouseY, button);
        }
        return handled;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!expanded) {
            return false;
        }
        boolean handled = false;
        for (SettingRow row : settingRows) {
            handled |= row.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        return handled;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!expanded) {
            return false;
        }
        for (SettingRow row : settingRows) {
            if (row.getSetting().isVisible() && row.mouseScrolled(mouseX, mouseY, delta)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!expanded) {
            return false;
        }
        for (SettingRow row : settingRows) {
            if (row.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        if (!expanded) {
            return false;
        }
        for (SettingRow row : settingRows) {
            if (row.charTyped(character, modifiers)) {
                return true;
            }
        }
        return false;
    }

    private float computeSettingsHeight() {
        float total = 0;
        for (SettingRow row : settingRows) {
            if (row.getSetting().isVisible()) {
                total += row.getHeight();
            }
        }
        return total;
    }
}
