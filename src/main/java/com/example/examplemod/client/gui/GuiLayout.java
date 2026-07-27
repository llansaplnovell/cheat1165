package com.example.examplemod.client.gui;

/** Default column layout for {@link ClickGuiScreen}: one panel per category, laid out left to right. */
public final class GuiLayout {

    private static final float PANEL_WIDTH = 120f;
    private static final float MARGIN = 8f;

    private GuiLayout() {
    }

    public static float xFor(int columnIndex) {
        return MARGIN + columnIndex * (PANEL_WIDTH + MARGIN);
    }

    public static float getStartY() {
        return MARGIN;
    }

    public static float getPanelWidth() {
        return PANEL_WIDTH;
    }
}
