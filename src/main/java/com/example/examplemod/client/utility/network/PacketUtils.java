package com.example.examplemod.client.utility.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.IPacket;

/** Thin helpers around sending packets outside the normal gameplay flow (commands, modules). */
public final class PacketUtils {

    private PacketUtils() {
    }

    public static void send(IPacket<?> packet) {
        if (Minecraft.getInstance().getConnection() != null) {
            Minecraft.getInstance().getConnection().send(packet);
        }
    }

    public static String describe(IPacket<?> packet) {
        return packet.getClass().getSimpleName();
    }
}
