package com.example.examplemod.client.api.module;

/**
 * Grouping used by the module list, HUD array-list and ClickGUI tab bar.
 * Mirrors the {@code module/impl/*} sub-packages.
 */
public enum Category {
    COMBAT("Combat"),
    MOVEMENT("Movement"),
    RENDER("Render"),
    PLAYER("Player"),
    WORLD("World"),
    CLIENT("Client"),
    MISC("Misc");

    private final String displayName;

    Category(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
