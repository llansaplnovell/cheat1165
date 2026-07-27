package com.example.examplemod.client.manager;

import com.example.examplemod.client.api.event.EventHandler;
import com.example.examplemod.client.event.Render2DEvent;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.gui.FontRenderer;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/**
 * Small toast-style notification queue (module errors, command feedback,
 * connection events, ...), rendered in the top-right corner.
 */
public final class NotificationManager {

    public enum Type {
        INFO(0xFF3B82F6),
        SUCCESS(0xFF22C55E),
        WARNING(0xFFF59E0B),
        ERROR(0xFFEF4444);

        final int color;

        Type(int color) {
            this.color = color;
        }
    }

    private static final long LIFETIME_MS = 3000L;
    private static final int MAX_VISIBLE = 5;

    private final Deque<Notification> notifications = new ArrayDeque<>();
    private boolean visible = true;

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public void notify(String message, Type type) {
        notifications.addFirst(new Notification(message, type, System.currentTimeMillis()));
        while (notifications.size() > MAX_VISIBLE) {
            notifications.removeLast();
        }
    }

    public void info(String message) {
        notify(message, Type.INFO);
    }

    public void success(String message) {
        notify(message, Type.SUCCESS);
    }

    public void warning(String message) {
        notify(message, Type.WARNING);
    }

    public void error(String message) {
        notify(message, Type.ERROR);
    }

    @EventHandler
    public void onRender2D(Render2DEvent event) {
        if (!visible) {
            return;
        }
        long now = System.currentTimeMillis();
        notifications.removeIf(n -> now - n.createdAt > LIFETIME_MS);

        Minecraft minecraft = Minecraft.getInstance();
        FontRenderer font = minecraft.font;
        MatrixStack matrixStack = event.getMatrixStack();

        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int y = 4;
        int i = 0;
        for (Iterator<Notification> it = notifications.iterator(); it.hasNext(); i++) {
            Notification notification = it.next();
            int width = font.width(notification.message) + 12;
            int x = screenWidth - width - 4;
            AbstractGui.fill(matrixStack, x, y, x + width, y + 14, 0xC0101014);
            AbstractGui.fill(matrixStack, x, y, x + 2, y + 14, notification.type.color);
            font.drawShadow(matrixStack, notification.message, x + 6, y + 3, 0xFFFFFFFF);
            y += 16;
        }
    }

    private static final class Notification {
        private final String message;
        private final Type type;
        private final long createdAt;

        private Notification(String message, Type type, long createdAt) {
            this.message = message;
            this.type = type;
            this.createdAt = createdAt;
        }
    }
}
