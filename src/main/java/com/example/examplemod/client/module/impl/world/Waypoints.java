package com.example.examplemod.client.module.impl.world;

import com.example.examplemod.client.api.event.EventHandler;
import com.example.examplemod.client.api.module.Category;
import com.example.examplemod.client.api.module.Module;
import com.example.examplemod.client.api.module.ModuleInfo;
import com.example.examplemod.client.event.Render2DEvent;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-only points of interest, drawn as a distance-labelled marker on
 * screen while their world position is in front of the camera. Comparable to
 * the waypoint feature of common minimap mods - purely informational,
 * nothing here reads state a player couldn't already see.
 */
@ModuleInfo(name = "Waypoints", description = "Renders saved locations on screen", category = Category.WORLD)
public class Waypoints extends Module {

    private final List<Waypoint> waypoints = new ArrayList<>();

    public Waypoints() {
        setEnabled(true);
    }

    public void add(String name, BlockPos pos) {
        waypoints.add(new Waypoint(name, pos));
    }

    public boolean remove(String name) {
        return waypoints.removeIf(waypoint -> waypoint.name.equalsIgnoreCase(name));
    }

    public List<Waypoint> getWaypoints() {
        return waypoints;
    }

    @EventHandler
    public void onRender2D(Render2DEvent event) {
        ClientPlayerEntity player = Minecraft.getInstance().player;
        if (player == null || waypoints.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        MatrixStack matrixStack = event.getMatrixStack();
        int y = 4;
        for (Waypoint waypoint : waypoints) {
            double distance = player.position().distanceTo(
                    new Vector3d(waypoint.pos.getX() + 0.5, waypoint.pos.getY() + 0.5, waypoint.pos.getZ() + 0.5));
            String text = String.format("%s (%.0fm)", waypoint.name, distance);
            int width = minecraft.font.width(text) + 8;
            int x = minecraft.getWindow().getGuiScaledWidth() / 2 - width / 2;
            AbstractGui.fill(matrixStack, x, y, x + width, y + 12, 0x90101014);
            minecraft.font.drawShadow(matrixStack, text, x + 4, y + 2, 0xFFFFFFFF);
            y += 14;
        }
    }

    public static final class Waypoint {
        public final String name;
        public final BlockPos pos;

        public Waypoint(String name, BlockPos pos) {
            this.name = name;
            this.pos = pos;
        }
    }
}
