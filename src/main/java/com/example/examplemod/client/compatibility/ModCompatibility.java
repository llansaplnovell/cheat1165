package com.example.examplemod.client.compatibility;

import net.minecraftforge.fml.ModList;

/**
 * Small feature-detection helpers so modules can adapt instead of assuming a
 * vanilla-only, no-other-mods environment (e.g. skip a render tweak that
 * would fight with a shader pack).
 */
public final class ModCompatibility {

    private ModCompatibility() {
    }

    public static boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    public static boolean isOptifineInstalled() {
        try {
            Class.forName("optifine.Installer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
