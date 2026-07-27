package com.example.examplemod.client.gui.theme;

public final class ThemeManager {

    private static Theme current = Theme.darkDefault();

    private ThemeManager() {
    }

    public static Theme getCurrent() {
        return current;
    }

    public static void setCurrent(Theme theme) {
        current = theme;
    }
}
