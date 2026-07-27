package com.example.examplemod.client.manager;

import com.example.examplemod.client.api.module.Module;
import com.example.examplemod.client.module.impl.misc.AutoReconnect;
import com.example.examplemod.client.module.impl.world.Waypoints;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.util.text.StringTextComponent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Minimal client-side ("dot") command system, e.g. {@code .toggle fullbright}.
 * Handled entirely before the message reaches chat, see
 * {@link com.example.examplemod.client.hook.ForgeEventBridge}.
 */
public final class CommandManager {

    private final ModuleManager moduleManager;
    private final FriendManager friendManager;
    private final NotificationManager notificationManager;
    private final Map<String, Registered> commands = new LinkedHashMap<>();

    private static final UUID LOCAL_FEEDBACK_UUID = new UUID(0L, 0L);

    private String prefix = ".";

    public CommandManager(ModuleManager moduleManager, FriendManager friendManager, NotificationManager notificationManager) {
        this.moduleManager = moduleManager;
        this.friendManager = friendManager;
        this.notificationManager = notificationManager;
        registerDefaults();
    }

    private void registerDefaults() {
        register("help", "help", args -> {
            for (Registered registered : commands.values()) {
                sendFeedback(prefix + registered.usage);
            }
        });

        register("toggle", "toggle <module>", args -> {
            if (args.length < 1) {
                sendFeedback("Usage: " + prefix + "toggle <module>");
                return;
            }
            Module module = moduleManager.getByName(args[0]);
            if (module == null) {
                notificationManager.error("Unknown module: " + args[0]);
                return;
            }
            module.toggle();
            notificationManager.info(module.getName() + (module.isEnabled() ? " enabled" : " disabled"));
        });

        register("prefix", "prefix <symbol>", args -> {
            if (args.length < 1 || args[0].length() != 1) {
                sendFeedback("Usage: " + prefix + "prefix <single character>");
                return;
            }
            prefix = args[0];
            notificationManager.success("Prefix set to '" + prefix + "'");
        });

        register("friend", "friend <add|remove|list> [name]", args -> {
            if (args.length < 1) {
                sendFeedback("Usage: " + prefix + "friend <add|remove|list> [name]");
                return;
            }
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "add":
                    if (args.length < 2) {
                        sendFeedback("Usage: " + prefix + "friend add <name>");
                        break;
                    }
                    friendManager.add(args[1]);
                    notificationManager.success(args[1] + " added to friends");
                    break;
                case "remove":
                    if (args.length < 2) {
                        sendFeedback("Usage: " + prefix + "friend remove <name>");
                        break;
                    }
                    friendManager.remove(args[1]);
                    notificationManager.success(args[1] + " removed from friends");
                    break;
                case "list":
                    if (friendManager.getFriends().isEmpty()) {
                        sendFeedback("No friends added");
                    } else {
                        sendFeedback("Friends: " + String.join(", ", friendManager.getFriends()));
                    }
                    break;
                default:
                    sendFeedback("Usage: " + prefix + "friend <add|remove|list> [name]");
            }
        });

        register("waypoint", "waypoint <add|remove|list> [name]", args -> {
            Waypoints waypoints = moduleManager.get(Waypoints.class);
            if (waypoints == null || args.length < 1) {
                sendFeedback("Usage: " + prefix + "waypoint <add|remove|list> [name]");
                return;
            }
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "add":
                    if (args.length < 2) {
                        sendFeedback("Usage: " + prefix + "waypoint add <name>");
                        break;
                    }
                    ClientPlayerEntity player = Minecraft.getInstance().player;
                    if (player != null) {
                        waypoints.add(args[1], player.blockPosition());
                        notificationManager.success("Waypoint '" + args[1] + "' added");
                    }
                    break;
                case "remove":
                    if (args.length < 2) {
                        sendFeedback("Usage: " + prefix + "waypoint remove <name>");
                        break;
                    }
                    notificationManager.info(waypoints.remove(args[1])
                            ? "Waypoint '" + args[1] + "' removed" : "No such waypoint");
                    break;
                case "list":
                    if (waypoints.getWaypoints().isEmpty()) {
                        sendFeedback("No waypoints added");
                    } else {
                        for (Waypoints.Waypoint waypoint : waypoints.getWaypoints()) {
                            sendFeedback(waypoint.name + " @ " + waypoint.pos.getX() + ", "
                                    + waypoint.pos.getY() + ", " + waypoint.pos.getZ());
                        }
                    }
                    break;
                default:
                    sendFeedback("Usage: " + prefix + "waypoint <add|remove|list> [name]");
            }
        });

        register("reconnect", "reconnect", args -> {
            AutoReconnect autoReconnect = moduleManager.get(AutoReconnect.class);
            if (autoReconnect == null || !autoReconnect.reconnect()) {
                notificationManager.error("No previous server to reconnect to");
            }
        });
    }

    public void register(String name, String usage, Consumer<String[]> executor) {
        commands.put(name.toLowerCase(Locale.ROOT), new Registered(name, usage, executor));
    }

    /**
     * @return true if {@code message} was a recognised client command and should
     * not be sent to the server / shown in chat as-is.
     */
    public boolean handle(String message) {
        if (message == null || prefix.isEmpty() || !message.startsWith(prefix)) {
            return false;
        }
        String[] parts = message.substring(prefix.length()).trim().split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) {
            return false;
        }
        Registered registered = commands.get(parts[0].toLowerCase(Locale.ROOT));
        if (registered == null) {
            notificationManager.error("Unknown command: " + parts[0]);
            return true;
        }
        registered.executor.accept(Arrays.copyOfRange(parts, 1, parts.length));
        return true;
    }

    public List<Registered> getCommands() {
        return new ArrayList<>(commands.values());
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    private void sendFeedback(String text) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.sendMessage(new StringTextComponent(text), LOCAL_FEEDBACK_UUID);
        }
    }

    public static final class Registered {
        public final String name;
        public final String usage;
        final Consumer<String[]> executor;

        private Registered(String name, String usage, Consumer<String[]> executor) {
            this.name = name;
            this.usage = usage;
            this.executor = executor;
        }
    }
}
