/*
 * PlayerESP for Magic (aka "primordial") — MC 1.8.9
 *
 * Original implementation written from scratch for this client's own
 * architecture (Module / Category / EnumValue / BoolValue / ColorValue,
 * pisi.unitedmeows.eventapi Listener<EventRender3D>, FriendManager).
 *
 * Drop this file into: src/main/java/Magic/mod/s/render/PlayerESP.java
 * of the real Magic project tree. Modules.loadModules() scans
 * Magic.mod.s.* on the classpath and instantiates it automatically —
 * no manual registration needed.
 */
package Magic.mod.s.render;

import Magic.ink.event.s.EventRender3D;
import Magic.mod.Category;
import Magic.mod.Module;
import Magic.mod.value.values.BoolValue;
import Magic.mod.value.values.ColorValue;
import Magic.mod.value.values.EnumValue;
import Magic.utils.Friend.FriendManager;
import Magic.utils.player.ClientUtils;
import java.awt.Color;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import org.lwjgl.opengl.GL11;
import pisi.unitedmeows.eventapi.event.listener.Listener;

public class PlayerESP extends Module {

    private final EnumValue<Mode> mode = new EnumValue<Mode>("Mode", this, Mode.class, "ESP render style.");
    private final BoolValue throughWalls = new BoolValue("ThroughWalls", this, true, "Ignore depth test, see through blocks.");
    private final BoolValue healthColor = new BoolValue("HealthColor", this, true, "Tint box from white to the base color as health drops.");
    private final BoolValue highlightFriends = new BoolValue("Friends", this, true, "Draw friends in a separate color.");
    private final ColorValue baseColor = new ColorValue("Color", this, new Color(255, 60, 60), "Color used for non-friend players.");

    private static final Color FRIEND_COLOR = new Color(80, 190, 255);

    private final Listener<EventRender3D> onRender3D = new Listener<EventRender3D>(event -> {
        if (this.mc.theWorld == null || this.mc.thePlayer == null) {
            return;
        }
        float partialTicks = event.partialTicks();
        for (EntityPlayer player : ClientUtils.getPlayers()) {
            if (player == this.mc.thePlayer || player.isDead || player.isInvisible() || player.getHealth() <= 0.0f) {
                continue;
            }
            Color color = this.colorFor(player);
            AxisAlignedBB box = this.interpolatedBox(player, partialTicks);
            if (this.mode.getValue() == Mode.Fill) {
                this.drawFilledBox(box, color);
            } else {
                this.drawOutlineBox(box, color);
            }
        }
        GlStateManager.resetColor();
    });

    public PlayerESP() {
        super("PlayerESP", 0, Category.Render, "Highlights other players, optionally through terrain.");
    }

    @Override
    public void onSuffixChange() {
        this.setSuffix(this.mode.getValue().enumName());
        super.onSuffixChange();
    }

    private Color colorFor(EntityPlayer player) {
        if (this.highlightFriends.getValue() && FriendManager.isFriend(player.getName())) {
            return FRIEND_COLOR;
        }
        Color base = this.baseColor.getValue();
        if (!this.healthColor.getValue()) {
            return base;
        }
        float progress = Math.max(0.0f, Math.min(player.getHealth() / player.getMaxHealth(), 1.0f));
        int r = this.lerp(255, base.getRed(), progress);
        int g = this.lerp(40, base.getGreen(), progress);
        int b = this.lerp(40, base.getBlue(), progress);
        return new Color(r, g, b);
    }

    private AxisAlignedBB interpolatedBox(EntityPlayer player, float partialTicks) {
        double x = player.lastTickPosX + (player.posX - player.lastTickPosX) * (double) partialTicks - this.mc.getRenderManager().renderPosX;
        double y = player.lastTickPosY + (player.posY - player.lastTickPosY) * (double) partialTicks - this.mc.getRenderManager().renderPosY;
        double z = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * (double) partialTicks - this.mc.getRenderManager().renderPosZ;
        double halfWidth = player.width / 2.0;
        return new AxisAlignedBB(x - halfWidth, y, z - halfWidth, x + halfWidth, y + player.height, z + halfWidth);
    }

    private void drawOutlineBox(AxisAlignedBB bb, Color color) {
        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableTexture2D();
        GL11.glLineWidth(1.5f);
        if (this.throughWalls.getValue()) {
            GlStateManager.disableDepth();
        }
        GlStateManager.color((float) color.getRed() / 255.0f, (float) color.getGreen() / 255.0f, (float) color.getBlue() / 255.0f, 1.0f);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        wr.begin(3, DefaultVertexFormats.POSITION);
        wr.pos(bb.minX, bb.minY, bb.minZ).endVertex();
        wr.pos(bb.maxX, bb.minY, bb.minZ).endVertex();
        wr.pos(bb.maxX, bb.minY, bb.maxZ).endVertex();
        wr.pos(bb.minX, bb.minY, bb.maxZ).endVertex();
        wr.pos(bb.minX, bb.minY, bb.minZ).endVertex();
        tessellator.draw();
        wr.begin(3, DefaultVertexFormats.POSITION);
        wr.pos(bb.minX, bb.maxY, bb.minZ).endVertex();
        wr.pos(bb.maxX, bb.maxY, bb.minZ).endVertex();
        wr.pos(bb.maxX, bb.maxY, bb.maxZ).endVertex();
        wr.pos(bb.minX, bb.maxY, bb.maxZ).endVertex();
        wr.pos(bb.minX, bb.maxY, bb.minZ).endVertex();
        tessellator.draw();
        wr.begin(1, DefaultVertexFormats.POSITION);
        wr.pos(bb.minX, bb.minY, bb.minZ).endVertex();
        wr.pos(bb.minX, bb.maxY, bb.minZ).endVertex();
        wr.pos(bb.maxX, bb.minY, bb.minZ).endVertex();
        wr.pos(bb.maxX, bb.maxY, bb.minZ).endVertex();
        wr.pos(bb.maxX, bb.minY, bb.maxZ).endVertex();
        wr.pos(bb.maxX, bb.maxY, bb.maxZ).endVertex();
        wr.pos(bb.minX, bb.minY, bb.maxZ).endVertex();
        wr.pos(bb.minX, bb.maxY, bb.maxZ).endVertex();
        tessellator.draw();
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private void drawFilledBox(AxisAlignedBB bb, Color color) {
        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableTexture2D();
        GlStateManager.disableCull();
        if (this.throughWalls.getValue()) {
            GlStateManager.disableDepth();
        }
        GlStateManager.color((float) color.getRed() / 255.0f, (float) color.getGreen() / 255.0f, (float) color.getBlue() / 255.0f, 0.35f);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        wr.begin(7, DefaultVertexFormats.POSITION);
        this.quad(wr, bb.minX, bb.minY, bb.minZ, bb.minX, bb.maxY, bb.minZ, bb.minX, bb.maxY, bb.maxZ, bb.minX, bb.minY, bb.maxZ);
        this.quad(wr, bb.maxX, bb.minY, bb.maxZ, bb.maxX, bb.maxY, bb.maxZ, bb.maxX, bb.maxY, bb.minZ, bb.maxX, bb.minY, bb.minZ);
        this.quad(wr, bb.minX, bb.minY, bb.minZ, bb.maxX, bb.minY, bb.minZ, bb.maxX, bb.minY, bb.maxZ, bb.minX, bb.minY, bb.maxZ);
        this.quad(wr, bb.minX, bb.maxY, bb.maxZ, bb.maxX, bb.maxY, bb.maxZ, bb.maxX, bb.maxY, bb.minZ, bb.minX, bb.maxY, bb.minZ);
        this.quad(wr, bb.minX, bb.minY, bb.minZ, bb.minX, bb.maxY, bb.minZ, bb.maxX, bb.maxY, bb.minZ, bb.maxX, bb.minY, bb.minZ);
        this.quad(wr, bb.maxX, bb.minY, bb.maxZ, bb.maxX, bb.maxY, bb.maxZ, bb.minX, bb.maxY, bb.maxZ, bb.minX, bb.minY, bb.maxZ);
        tessellator.draw();
        GlStateManager.enableCull();
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.popMatrix();
    }

    private void quad(WorldRenderer wr, double x1, double y1, double z1, double x2, double y2, double z2, double x3, double y3, double z3, double x4, double y4, double z4) {
        wr.pos(x1, y1, z1).endVertex();
        wr.pos(x2, y2, z2).endVertex();
        wr.pos(x3, y3, z3).endVertex();
        wr.pos(x4, y4, z4).endVertex();
    }

    private int lerp(int from, int to, float progress) {
        return (int) ((float) from + (float) (to - from) * progress);
    }

    public static enum Mode {
        Box("Box"),
        Fill("Fill");

        private String enumName;

        private Mode(String enumName) {
            this.enumName = enumName;
        }

        public String enumName() {
            return this.enumName;
        }
    }
}
