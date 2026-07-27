package com.example.examplemod.client.gui.component;

import com.example.examplemod.client.api.setting.BooleanSetting;
import com.example.examplemod.client.api.setting.ColorSetting;
import com.example.examplemod.client.api.setting.KeySetting;
import com.example.examplemod.client.api.setting.ModeSetting;
import com.example.examplemod.client.api.setting.MultiSelectSetting;
import com.example.examplemod.client.api.setting.NumberSetting;
import com.example.examplemod.client.api.setting.Setting;
import com.example.examplemod.client.api.setting.StringSetting;
import com.example.examplemod.client.gui.dropdown.Dropdown;
import com.example.examplemod.client.gui.theme.Theme;
import com.example.examplemod.client.gui.theme.ThemeManager;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.gui.FontRenderer;
import org.lwjgl.glfw.GLFW;

/** Renders and drives a single {@link Setting}, picking its widget by the setting's concrete type. */
public class SettingRow extends Component {

    public static final int HEIGHT = 16;

    private final Setting<?> setting;
    private final Dropdown dropdown;

    private boolean draggingSlider;
    private boolean editingText;
    private boolean listeningForKey;

    public SettingRow(Setting<?> setting, float x, float y, float width) {
        super(x, y, width, HEIGHT);
        this.setting = setting;
        this.dropdown = createDropdown();
    }

    private Dropdown createDropdown() {
        if (setting instanceof ModeSetting) {
            ModeSetting mode = (ModeSetting) setting;
            return new Dropdown(mode.getModes(), x, y + HEIGHT, width, mode::is, mode::set);
        }
        if (setting instanceof MultiSelectSetting) {
            MultiSelectSetting multi = (MultiSelectSetting) setting;
            return new Dropdown(multi.getOptions(), x, y + HEIGHT, width, multi::isSelected, multi::toggleOption);
        }
        return null;
    }

    public Setting<?> getSetting() {
        return setting;
    }

    @Override
    public float getHeight() {
        return HEIGHT + (dropdown != null ? dropdown.getExpandedHeight() : 0);
    }

    @Override
    public void setPosition(float x, float y) {
        super.setPosition(x, y);
        if (dropdown != null) {
            dropdown.setPosition(x, y + HEIGHT);
        }
    }

    @Override
    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        Theme theme = ThemeManager.getCurrent();
        FontRenderer font = Minecraft.getInstance().font;
        boolean hovered = isHovered(mouseX, mouseY);
        AbstractGui.fill(matrixStack, (int) x, (int) y, (int) (x + width), (int) (y + HEIGHT),
                hovered ? theme.getRowHovered() : theme.getRowBackground());
        font.drawShadow(matrixStack, setting.getName(), x + 12, y + 4, theme.getTextSecondary());

        if (setting instanceof BooleanSetting) {
            renderBoolean(matrixStack, theme, (BooleanSetting) setting);
        } else if (setting instanceof NumberSetting) {
            renderNumber(matrixStack, font, theme, (NumberSetting) setting);
        } else if (setting instanceof ModeSetting) {
            font.drawShadow(matrixStack, ((ModeSetting) setting).get(),
                    x + width - font.width(((ModeSetting) setting).get()) - 6, y + 4, theme.getAccent());
        } else if (setting instanceof MultiSelectSetting) {
            String label = ((MultiSelectSetting) setting).get().size() + " selected";
            font.drawShadow(matrixStack, label, x + width - font.width(label) - 6, y + 4, theme.getAccent());
        } else if (setting instanceof StringSetting) {
            String text = editingText ? ((StringSetting) setting).get() + "_" : ((StringSetting) setting).get();
            font.drawShadow(matrixStack, text, x + width - font.width(text) - 6, y + 4, theme.getTextPrimary());
        } else if (setting instanceof ColorSetting) {
            ColorSetting color = (ColorSetting) setting;
            AbstractGui.fill(matrixStack, (int) (x + width - 20), (int) (y + 3), (int) (x + width - 6), (int) (y + 13), color.resolve());
        } else if (setting instanceof KeySetting) {
            KeySetting key = (KeySetting) setting;
            String label = listeningForKey ? "> _ <" : (key.isBound() ? keyName(key.get()) : "NONE");
            font.drawShadow(matrixStack, label, x + width - font.width(label) - 6, y + 4, theme.getAccent());
        }

        if (dropdown != null) {
            dropdown.render(matrixStack, mouseX, mouseY, partialTicks);
        }
    }

    private void renderBoolean(MatrixStack matrixStack, Theme theme, BooleanSetting boolSetting) {
        int boxX = (int) (x + width - 16);
        int boxY = (int) (y + 3);
        AbstractGui.fill(matrixStack, boxX, boxY, boxX + 10, boxY + 10,
                boolSetting.get() ? theme.getAccent() : theme.getPanelHeader());
    }

    private void renderNumber(MatrixStack matrixStack, FontRenderer font, Theme theme, NumberSetting number) {
        String value = String.format("%.2f", number.get());
        font.drawShadow(matrixStack, value, x + width - font.width(value) - 6, y + 4, theme.getTextPrimary());
        int barX = (int) (x + 12);
        int barY = (int) (y + HEIGHT - 3);
        int barWidth = (int) (width - 24);
        AbstractGui.fill(matrixStack, barX, barY, barX + barWidth, barY + 1, theme.getPanelHeader());
        float progress = (float) ((number.get() - number.getMin()) / (number.getMax() - number.getMin()));
        AbstractGui.fill(matrixStack, barX, barY, (int) (barX + barWidth * progress), barY + 1, theme.getAccent());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (dropdown != null && dropdown.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (!isHovered(mouseX, mouseY)) {
            return false;
        }
        if (setting instanceof BooleanSetting) {
            ((BooleanSetting) setting).toggle();
        } else if (setting instanceof NumberSetting) {
            draggingSlider = true;
            updateSlider(mouseX);
        } else if (setting instanceof ModeSetting || setting instanceof MultiSelectSetting) {
            dropdown.setOpen(!dropdown.isOpen());
        } else if (setting instanceof StringSetting) {
            editingText = !editingText;
        } else if (setting instanceof ColorSetting) {
            ColorSetting color = (ColorSetting) setting;
            if (button == 1) {
                color.setRainbow(!color.isRainbow());
            } else {
                color.set(nextPresetColor(color.get()));
            }
        } else if (setting instanceof KeySetting) {
            listeningForKey = true;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean was = draggingSlider;
        draggingSlider = false;
        return was;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingSlider && setting instanceof NumberSetting) {
            updateSlider(mouseX);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!isHovered(mouseX, mouseY) || !(setting instanceof NumberSetting)) {
            return false;
        }
        NumberSetting number = (NumberSetting) setting;
        number.set(number.get() + Math.signum(delta) * number.getStep());
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (listeningForKey && setting instanceof KeySetting) {
            ((KeySetting) setting).set(keyCode == GLFW.GLFW_KEY_ESCAPE ? KeySetting.UNBOUND : keyCode);
            listeningForKey = false;
            return true;
        }
        if (editingText && setting instanceof StringSetting) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                StringSetting string = (StringSetting) setting;
                if (!string.get().isEmpty()) {
                    string.set(string.get().substring(0, string.get().length() - 1));
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_ESCAPE) {
                editingText = false;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        if (editingText && setting instanceof StringSetting) {
            StringSetting string = (StringSetting) setting;
            string.set(string.get() + character);
            return true;
        }
        return false;
    }

    private void updateSlider(double mouseX) {
        NumberSetting number = (NumberSetting) setting;
        float barX = x + 12;
        float barWidth = width - 24;
        double progress = Math.max(0, Math.min(1, (mouseX - barX) / barWidth));
        double stepped = Math.round((number.getMin() + progress * (number.getMax() - number.getMin())) / number.getStep()) * number.getStep();
        number.set(stepped);
    }

    private static int nextPresetColor(int current) {
        int[] presets = {0xFFFFFFFF, 0xFFEF4444, 0xFFF59E0B, 0xFF22C55E, 0xFF3B82F6, 0xFF8B5CF6};
        for (int i = 0; i < presets.length; i++) {
            if (presets[i] == current) {
                return presets[(i + 1) % presets.length];
            }
        }
        return presets[0];
    }

    private static String keyName(int keyCode) {
        String name = GLFW.glfwGetKeyName(keyCode, 0);
        return name != null ? name.toUpperCase(java.util.Locale.ROOT) : ("KEY_" + keyCode);
    }
}
