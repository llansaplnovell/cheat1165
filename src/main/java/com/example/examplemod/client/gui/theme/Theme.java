package com.example.examplemod.client.gui.theme;

/** Palette used by every ClickGUI component. Swap {@link ThemeManager#getCurrent()} to reskin the whole GUI. */
public class Theme {

    private final int panelBackground;
    private final int panelHeader;
    private final int rowBackground;
    private final int rowHovered;
    private final int accent;
    private final int textPrimary;
    private final int textSecondary;

    public Theme(int panelBackground, int panelHeader, int rowBackground, int rowHovered,
                 int accent, int textPrimary, int textSecondary) {
        this.panelBackground = panelBackground;
        this.panelHeader = panelHeader;
        this.rowBackground = rowBackground;
        this.rowHovered = rowHovered;
        this.accent = accent;
        this.textPrimary = textPrimary;
        this.textSecondary = textSecondary;
    }

    public static Theme darkDefault() {
        return new Theme(0xE6121216, 0xF01A1A20, 0xC01C1C22, 0xC0242430, 0xFF5865F2, 0xFFFFFFFF, 0xFFA0A0AC);
    }

    public int getPanelBackground() {
        return panelBackground;
    }

    public int getPanelHeader() {
        return panelHeader;
    }

    public int getRowBackground() {
        return rowBackground;
    }

    public int getRowHovered() {
        return rowHovered;
    }

    public int getAccent() {
        return accent;
    }

    public int getTextPrimary() {
        return textPrimary;
    }

    public int getTextSecondary() {
        return textSecondary;
    }
}
