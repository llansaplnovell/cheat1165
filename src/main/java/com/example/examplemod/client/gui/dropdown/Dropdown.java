package com.example.examplemod.client.gui.dropdown;

import com.example.examplemod.client.gui.component.Component;
import com.example.examplemod.client.gui.theme.Theme;
import com.example.examplemod.client.gui.theme.ThemeManager;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Expandable list of options, backing both {@code ModeSetting} and {@code MultiSelectSetting} rows. */
public class Dropdown extends Component {

    private static final int OPTION_HEIGHT = 14;

    private final List<String> options;
    private final Predicate<String> isSelected;
    private final Consumer<String> onSelect;
    private boolean open;

    public Dropdown(List<String> options, float x, float y, float width, Predicate<String> isSelected, Consumer<String> onSelect) {
        super(x, y, width, OPTION_HEIGHT);
        this.options = options;
        this.isSelected = isSelected;
        this.onSelect = onSelect;
    }

    public boolean isOpen() {
        return open;
    }

    public void setOpen(boolean open) {
        this.open = open;
    }

    public float getExpandedHeight() {
        return open ? options.size() * OPTION_HEIGHT : 0;
    }

    @Override
    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        if (!open) {
            return;
        }
        Theme theme = ThemeManager.getCurrent();
        int rowY = (int) y;
        for (String option : options) {
            boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= rowY && mouseY <= rowY + OPTION_HEIGHT;
            AbstractGui.fill(matrixStack, (int) x, rowY, (int) (x + width), rowY + OPTION_HEIGHT,
                    hovered ? theme.getRowHovered() : theme.getPanelHeader());
            int color = isSelected.test(option) ? theme.getAccent() : theme.getTextSecondary();
            Minecraft.getInstance().font.drawShadow(matrixStack, option, x + 4, rowY + 3, color);
            rowY += OPTION_HEIGHT;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!open) {
            return false;
        }
        int rowY = (int) y;
        for (String option : options) {
            if (mouseX >= x && mouseX <= x + width && mouseY >= rowY && mouseY <= rowY + OPTION_HEIGHT) {
                onSelect.accept(option);
                return true;
            }
            rowY += OPTION_HEIGHT;
        }
        return false;
    }
}
