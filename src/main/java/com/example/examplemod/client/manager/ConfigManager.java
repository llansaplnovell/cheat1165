package com.example.examplemod.client.manager;

import com.example.examplemod.client.api.module.Module;
import com.example.examplemod.client.api.setting.BooleanSetting;
import com.example.examplemod.client.api.setting.ColorSetting;
import com.example.examplemod.client.api.setting.KeySetting;
import com.example.examplemod.client.api.setting.ModeSetting;
import com.example.examplemod.client.api.setting.MultiSelectSetting;
import com.example.examplemod.client.api.setting.NumberSetting;
import com.example.examplemod.client.api.setting.Setting;
import com.example.examplemod.client.api.setting.StringSetting;
import com.example.examplemod.client.config.ClientConfig;
import com.example.examplemod.client.config.HudEntry;
import com.example.examplemod.client.config.ModuleEntry;
import com.example.examplemod.client.hud.HudElement;
import com.example.examplemod.client.hud.HudManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;

/**
 * Reads/writes {@code config/examplemod_client.json}: module enabled state and
 * settings, HUD element positions, the friend list and the command prefix.
 * Every value is flattened to a string so this class never needs to know
 * about new {@link Setting} subtypes beyond the ones handled below.
 */
public final class ConfigManager {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final String FILE_NAME = "examplemod_client.json";

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final ModuleManager moduleManager;
    private final FriendManager friendManager;
    private final CommandManager commandManager;
    private final HudManager hudManager;

    public ConfigManager(ModuleManager moduleManager, FriendManager friendManager,
                          CommandManager commandManager, HudManager hudManager) {
        this.moduleManager = moduleManager;
        this.friendManager = friendManager;
        this.commandManager = commandManager;
        this.hudManager = hudManager;
    }

    private Path getConfigPath() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
    }

    public void save() {
        ClientConfig config = new ClientConfig();
        config.prefix = commandManager.getPrefix();
        config.friends.addAll(friendManager.getFriends());

        for (Module module : moduleManager.getModules()) {
            ModuleEntry entry = new ModuleEntry();
            entry.name = module.getName();
            entry.enabled = module.isEnabled();
            entry.keybind = module.getKeybind();
            for (Setting<?> setting : module.getSettings()) {
                entry.settings.put(setting.getName(), serialize(setting));
            }
            config.modules.add(entry);
        }

        for (HudElement element : hudManager.getElements()) {
            HudEntry entry = new HudEntry();
            entry.name = element.getName();
            entry.x = element.getX();
            entry.y = element.getY();
            entry.enabled = element.isEnabled();
            config.hud.add(entry);
        }

        try (FileWriter writer = new FileWriter(getConfigPath().toFile())) {
            gson.toJson(config, writer);
        } catch (IOException e) {
            LOGGER.error("Failed to save client config", e);
        }
    }

    public void load() {
        Path path = getConfigPath();
        if (!path.toFile().exists()) {
            return;
        }
        ClientConfig config;
        try (FileReader reader = new FileReader(path.toFile())) {
            config = gson.fromJson(reader, ClientConfig.class);
        } catch (IOException e) {
            LOGGER.error("Failed to load client config", e);
            return;
        }
        if (config == null) {
            return;
        }

        if (config.prefix != null) {
            commandManager.setPrefix(config.prefix);
        }
        friendManager.loadAll(config.friends);

        for (ModuleEntry entry : config.modules) {
            Module module = moduleManager.getByName(entry.name);
            if (module == null) {
                continue;
            }
            module.setKeybind(entry.keybind);
            for (Setting<?> setting : module.getSettings()) {
                String raw = entry.settings.get(setting.getName());
                if (raw != null) {
                    deserialize(setting, raw);
                }
            }
            module.setEnabled(entry.enabled);
        }

        for (HudEntry entry : config.hud) {
            HudElement element = hudManager.getByName(entry.name);
            if (element == null) {
                continue;
            }
            element.setX(entry.x);
            element.setY(entry.y);
            element.setEnabled(entry.enabled);
        }
    }

    private static String serialize(Setting<?> setting) {
        if (setting instanceof ColorSetting) {
            ColorSetting color = (ColorSetting) setting;
            return color.get() + ":" + color.isRainbow() + ":" + color.getRainbowSpeed();
        }
        if (setting instanceof MultiSelectSetting) {
            return String.join(",", ((MultiSelectSetting) setting).get());
        }
        return String.valueOf(setting.get());
    }

    @SuppressWarnings("unchecked")
    private static void deserialize(Setting<?> setting, String raw) {
        if (setting instanceof BooleanSetting) {
            ((BooleanSetting) setting).set(Boolean.parseBoolean(raw));
        } else if (setting instanceof NumberSetting) {
            ((NumberSetting) setting).set(Double.parseDouble(raw));
        } else if (setting instanceof ModeSetting) {
            ModeSetting mode = (ModeSetting) setting;
            if (mode.getModes().contains(raw)) {
                mode.set(raw);
            }
        } else if (setting instanceof MultiSelectSetting) {
            MultiSelectSetting multi = (MultiSelectSetting) setting;
            multi.get().clear();
            if (!raw.isEmpty()) {
                multi.get().addAll(new LinkedHashSet<>(Arrays.asList(raw.split(","))));
            }
        } else if (setting instanceof ColorSetting) {
            ColorSetting color = (ColorSetting) setting;
            String[] parts = raw.split(":");
            if (parts.length == 3) {
                color.set(Integer.parseInt(parts[0]));
                color.setRainbow(Boolean.parseBoolean(parts[1]));
                color.setRainbowSpeed(Double.parseDouble(parts[2]));
            }
        } else if (setting instanceof StringSetting) {
            ((StringSetting) setting).set(raw);
        } else if (setting instanceof KeySetting) {
            ((KeySetting) setting).set(Integer.parseInt(raw));
        }
    }
}
