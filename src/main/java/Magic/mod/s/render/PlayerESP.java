/*
 * PlayerESP for Magic (aka "primordial") — MC 1.8.9
 *
 * Faithful port of the Peter-client PlayerESP (Ob0110.java + Ob0183.java
 * render helpers, decompiled/deobfuscated). This is a literal translation
 * of that logic onto Magic's own APIs (Module/Category/EnumValue/BoolValue/
 * ColorValue, EventRender3D via pisi.unitedmeows.eventapi Listener,
 * FriendManager) — not a reinterpretation. Every mode, every magic number
 * and every color-selection quirk from the original is reproduced on
 * purpose; see the per-mode notes below for exactly what was kept as-is.
 *
 * Drop this file into: src/main/java/Magic/mod/s/render/PlayerESP.java
 * of the real Magic project tree. Modules.loadModules() scans
 * Magic.mod.s.* on the classpath and instantiates it automatically —
 * no manual registration needed.
 *
 * Source mapping (Peter -> here):
 *   Ob0110              -> this class (the PlayerESP module itself)
 *   Ob0110.Ob0216()      -> onRender3D listener body
 *   Ob0110.Ob0186()      -> drawBox() (Box mode)
 *   Ob0183.ModSpeed(x,y,z,color)         -> drawCornerBadge()  (Corner mode)
 *   Ob0183.ModSpeed(x,y,z,color,color2)  -> drawOtherBadge()   (Other mode)
 *   Ob0183.ModSpeed(drawX,drawY,drawW,drawH,color) -> border()
 *   Ob0183's underlying (double,double,double,double,int) rect primitive
 *     resolves to the vanilla Gui.drawRect(left,top,right,bottom,color)
 *     algorithm (same disableTexture/blend/tessellator quad sequence) —
 *     so it's called directly here instead of re-implementing it.
 *   entity.atq  -> EntityLivingBase.hurtTime (public vanilla field)
 *   entity.rx() -> EntityLivingBase.isEntityAlive()
 *   Ob0106.Ob0219() (client accent color) -> baseColor ColorValue
 *   Ob0115.Ob0263()  -> FriendManager.isFriend()
 *
 * "Minecraft" and "Outline" were NOT dead code in the original, unlike I
 * first assumed from only reading Ob0110.java — they're implemented by
 * patching vanilla renderer classes directly, outside the PlayerESP
 * module's own draw method:
 *
 *   - "Outline" is patched into fZ.java (RendererLivingEntity in Magic's
 *     MCP names): PlayerESP.isEnabled() && ESPModes=="Outline" gates a
 *     multi-pass GL_STENCIL_TEST silhouette render of the real 3D model
 *     (confirmed by decoding the obfuscated string-decrypt calls with the
 *     exact same char-shift algorithm used throughout that codebase —
 *     decodes to "ESPModes"/"Outline").
 *   - "Minecraft" is patched into ed.java (RenderGlobal): a method
 *     (decoded strings: "ESPModes"/"Minecraft") ORs into vanilla's own
 *     isRenderEntityOutlines() spectator-glow gate, reusing Minecraft's
 *     native entityOutlineFramebuffer/entityOutlineShader post-process
 *     pass (shaders/post/entity_outline.json) instead of a custom draw.
 *
 * Magic's own net.minecraft.client.renderer.RenderGlobal /
 * RendererLivingEntity are still 100% stock — that whole vanilla
 * spectator-outline pipeline (framebuffer, shader, EntityPlayer-only
 * filtering, per-entity team-color tint via the `renderOutlines` flag)
 * is intact and unpatched. Two ways to reach the same effect here:
 *
 *   - "Outline" is done below in pure module code (renderOutline()), via
 *     the public RenderManager.renderEntityStatic() API + a standard
 *     2-pass stencil-silhouette technique. This reproduces the same
 *     *effect* as Peter's stencil calls, but isn't a byte-for-byte mirror
 *     of them — I don't have a fully confirmed mapping for Ob0183's exact
 *     glStencilFunc/glStencilOp constants the way I do for Corner/Box/
 *     Other, so this is a from-scratch (but standard, well-understood)
 *     implementation of the same silhouette-outline algorithm.
 *   - "Minecraft" genuinely needs one small patch to Magic's own compiled
 *     RenderGlobal.class: isRenderEntityOutlines() has to also return
 *     true when wantsVanillaOutline() (below) is true. That one method is
 *     the only piece I can't add by just dropping in a new .java file —
 *     it requires a targeted bytecode patch (e.g. via Javassist) to the
 *     already-compiled vanilla class, the same category of change Peter's
 *     jar has baked in. See PLAYERESP_NOTES.md for the exact patch.
 */
package Magic.mod.s.render;

import Magic.ink.event.s.EventRender3D;
import Magic.mod.Category;
import Magic.mod.Module;
import Magic.mod.Modules;
import Magic.mod.value.values.ColorValue;
import Magic.mod.value.values.EnumValue;
import Magic.utils.Friend.FriendManager;
import Magic.utils.player.ClientUtils;
import java.awt.Color;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import org.lwjgl.opengl.GL11;
import pisi.unitedmeows.eventapi.event.listener.Listener;

public class PlayerESP extends Module {

    /** Same five options as the original ESPModes combo, same order. */
    private final EnumValue<Mode> mode = new EnumValue<Mode>("Mode", this, Mode.class, "ESP render style.");
    private final ColorValue baseColor = new ColorValue("Color", this, new Color(255, 60, 60), "Client-accent replacement (was Ob0106.Ob0219()).");

    private static final Color HURT_COLOR = new Color(255, 50, 10, 255);
    private static final Color FRIEND_COLOR = new Color(255, 255, 255, 255);

    private final Listener<EventRender3D> onRender3D = new Listener<EventRender3D>(event -> {
        if (this.mc.theWorld == null || this.mc.thePlayer == null) {
            return;
        }
        Mode m = this.mode.getValue();
        // "Minecraft" draws nothing here — it works by making vanilla's own
        // RenderGlobal.isRenderEntityOutlines() return true (see
        // wantsVanillaOutline() + PLAYERESP_NOTES.md), not via a draw call.
        if (m == Mode.Minecraft) {
            return;
        }
        float partialTicks = event.partialTicks();
        for (EntityPlayer player : ClientUtils.getPlayers()) {
            if (player == this.mc.thePlayer || player.isDead || !player.isEntityAlive()) {
                continue;
            }
            switch (m) {
                case Outline:
                    this.renderOutline(player, partialTicks);
                    break;
                case Corner:
                    this.renderCorner(player, partialTicks);
                    break;
                case Box:
                    this.renderBox(player);
                    break;
                case Other:
                    this.renderOther(player, partialTicks);
                    break;
                default:
            }
        }
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    });

    /**
     * Called from the small patch to RenderGlobal.isRenderEntityOutlines()
     * (see PLAYERESP_NOTES.md) — lets "Minecraft" mode reuse vanilla's own
     * entityOutlineFramebuffer/entityOutlineShader spectator-glow pass
     * instead of a custom draw call, exactly like the original did.
     */
    public static boolean wantsVanillaOutline() {
        PlayerESP module = Modules.get(PlayerESP.class);
        return module != null && module.isEnabled() && module.mode.getValue() == Mode.Minecraft;
    }

    public PlayerESP() {
        super("PlayerESP", 0, Category.Render, "Highlights other players (Corner/Box/Other, ported from Peter's PlayerESP).");
    }

    @Override
    public void onSuffixChange() {
        this.setSuffix(this.mode.getValue().enumName());
        super.onSuffixChange();
    }

    // ---- Corner mode : Ob0110's "Corner" branch + Ob0183.ModSpeed(x,y,z,color) ----
    // Friend -> white, hurt flash -> orange-red, else baseColor. Uses the
    // *interpolated* render position (matches tickX/tickY/tickZ in the source).
    private void renderCorner(EntityPlayer player, float partialTicks) {
        double[] pos = this.interpolatedRenderPos(player, partialTicks);
        int color = this.cornerColor(player).getRGB();
        this.drawCornerBadge(pos[0], pos[1], pos[2], color);
    }

    private Color cornerColor(EntityPlayer player) {
        if (player.hurtTime > 0) {
            return HURT_COLOR;
        }
        if (FriendManager.isFriend(player.getName())) {
            return FRIEND_COLOR;
        }
        return this.baseColor.getValue();
    }

    // ---- Box mode : Ob0110.Ob0186() ----
    // Uses the entity's RAW (non-interpolated) position, same as the
    // original — Box literally reads entity.aqZ/ara/arb directly instead of
    // lastTickPos+partialTicks, so unlike Corner/Other it will jitter a
    // little between ticks. That's a straight port of that behavior, not a
    // bug I introduced. Box is a narrower box than the real hitbox
    // (0.5 wide instead of the player's 0.6) — also as in the source.
    // NOTE: in the original, both the friend and non-friend branch of Box
    // resolved to the same literal white color (Ob0115.Ob0263(...) ? white :
    // white) — reproduced as-is below instead of "fixing" it to use
    // baseColor, since the ask this time is a faithful port.
    private void renderBox(EntityPlayer player) {
        double x = player.posX - 0.5 - this.mc.getRenderManager().renderPosX;
        double y = player.posY - this.mc.getRenderManager().renderPosY;
        double z = player.posZ - 0.5 - this.mc.getRenderManager().renderPosZ;
        AxisAlignedBB box = new AxisAlignedBB(x + 0.3, y, z + 0.3, x + 0.8, y + 1.9, z + 0.8);
        Color color = player.hurtTime > 0 ? HURT_COLOR : FRIEND_COLOR;
        this.drawBox(box, color);
    }

    // ---- Other mode : Ob0110's "Other" branch + Ob0183.ModSpeed(x,y,z,color,color2) ----
    // Note this branch never checked FriendManager in the original either —
    // only hurt-flash vs. baseColor. Kept exactly that way.
    private void renderOther(EntityPlayer player, float partialTicks) {
        double[] pos = this.interpolatedRenderPos(player, partialTicks);
        int accent = (player.hurtTime > 0 ? HURT_COLOR : this.baseColor.getValue()).getRGB();
        this.drawOtherBadge(pos[0], pos[1], pos[2], -1, accent);
    }

    // ---- Outline mode : reproduces the fZ.java multi-pass GL_STENCIL_TEST
    // silhouette effect using only public vanilla API, since Magic's own
    // RendererLivingEntity hasn't been patched the way Peter's fZ.java was
    // (see the class-level notes above and PLAYERESP_NOTES.md). Pass 1
    // renders the real model into the stencil buffer only (color writes
    // masked off); pass 2 renders a slightly enlarged copy with the
    // stencil test inverted, so only the outline fringe outside the
    // original silhouette gets drawn, in our color, with depth test off
    // (through walls, same as every other mode here). ----
    private void renderOutline(EntityPlayer player, float partialTicks) {
        Color color = player.hurtTime > 0 ? HURT_COLOR
                : FriendManager.isFriend(player.getName()) ? FRIEND_COLOR
                : this.baseColor.getValue();
        double[] pos = this.interpolatedRenderPos(player, partialTicks);

        GlStateManager.pushMatrix();
        GL11.glEnable(GL11.GL_STENCIL_TEST);
        GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
        GlStateManager.disableDepth();

        GL11.glColorMask(false, false, false, false);
        GL11.glStencilFunc(GL11.GL_ALWAYS, 1, 0xFF);
        GL11.glStencilOp(GL11.GL_REPLACE, GL11.GL_REPLACE, GL11.GL_REPLACE);
        this.mc.getRenderManager().renderEntityStatic(player, partialTicks, false);

        GL11.glColorMask(true, true, true, true);
        GL11.glStencilFunc(GL11.GL_NOTEQUAL, 1, 0xFF);
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.color((float) color.getRed() / 255.0f, (float) color.getGreen() / 255.0f, (float) color.getBlue() / 255.0f, 1.0f);
        double pivotY = pos[1] + player.height / 2.0;
        GlStateManager.pushMatrix();
        GlStateManager.translate(pos[0], pivotY, pos[2]);
        GlStateManager.scale(1.06f, 1.06f, 1.06f);
        GlStateManager.translate(-pos[0], -pivotY, -pos[2]);
        this.mc.getRenderManager().renderEntityStatic(player, partialTicks, false);
        GlStateManager.popMatrix();

        GlStateManager.enableTexture2D();
        GlStateManager.enableLighting();
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        GlStateManager.enableDepth();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.popMatrix();
    }

    private double[] interpolatedRenderPos(EntityPlayer player, float partialTicks) {
        double x = player.lastTickPosX + (player.posX - player.lastTickPosX) * (double) partialTicks - this.mc.getRenderManager().renderPosX;
        double y = player.lastTickPosY + (player.posY - player.lastTickPosY) * (double) partialTicks - this.mc.getRenderManager().renderPosY;
        double z = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * (double) partialTicks - this.mc.getRenderManager().renderPosZ;
        return new double[]{x, y, z};
    }

    // ---- Box wireframe : Ob0110.Ob0186() drawing half, i.e. the vanilla
    // RenderGlobal.drawSelectionBoundingBox algorithm the original called
    // through ed.Ob0151(new vr(...)). Depth test is unconditionally disabled
    // here, exactly like the source — Box always renders through terrain,
    // there was no toggle for it. ----
    private void drawBox(AxisAlignedBB bb, Color color) {
        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(true);
        GL11.glLineWidth(1.0f);
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
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    // ---- Billboard transform shared by Corner/Other, straight from
    // Ob0183's pushMatrix/translate/rotate(-fy.Vo)/scale(-0.1,-0.1,0.1)
    // sequence — yaw-only billboard (no pitch rotate in the source),
    // mirrored scale on X/Y exactly as written. Depth test is disabled here
    // too, unconditionally, matching the source. ----
    private void beginBadge(double worldX, double worldY, double worldZ) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(worldX, worldY, worldZ);
        GL11.glNormal3f(0.0f, 0.0f, 0.0f);
        GlStateManager.rotate(-this.mc.getRenderManager().playerViewY, 0.0f, 1.0f, 0.0f);
        GlStateManager.scale(-0.1f, -0.1f, 0.1f);
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableTexture2D();
        GlStateManager.depthMask(true);
    }

    private void endBadge() {
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.enableDepth();
        GlStateManager.enableLighting();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.popMatrix();
    }

    // border(): Ob0183.ModSpeed(drawX, drawY, drawWidth, drawHeight, color) —
    // 4 thin edge rects framing a box. drawWidth/drawHeight are the box's
    // right/bottom edge, not a width/height, exactly as named in the source.
    private void border(float drawX, float drawY, float drawWidth, float drawHeight, int color) {
        Gui.drawRect((int) drawX, (int) drawY, (int) drawWidth, (int) (drawY + 0.5f), color);
        Gui.drawRect((int) drawX, (int) (drawY + 0.5f), (int) (drawX + 0.5f), (int) drawHeight, color);
        Gui.drawRect((int) (drawWidth - 0.5f), (int) (drawY + 0.5f), (int) drawWidth, (int) (drawHeight - 0.5f), color);
        Gui.drawRect((int) (drawX + 0.5f), (int) (drawHeight - 0.5f), (int) drawWidth, (int) drawHeight, color);
    }

    // ---- Corner badge : Ob0183.ModSpeed(double posX, posY, posZ, int color) ----
    // The 32-rect outlined double-bracket icon — two vertical corner
    // brackets (left [-7,-4] and right [4,7]) with a 1px black outline drawn
    // as thin rects on top, giving the classic "corner ESP" look. Every
    // coordinate below is copied verbatim from the decompile.
    private void drawCornerBadge(double x, double y, double z, int color) {
        this.beginBadge(x, y, z);
        this.border(4.0f, -21.0f, 7.0f, -20.5f, color);
        this.border(-7.0f, -21.0f, -4.0f, -20.5f, color);
        this.border(6.5f, -21.0f, 7.0f, -18.5f, color);
        this.border(-7.0f, -21.0f, -6.5f, -18.5f, color);
        this.border(-7.0f, 2.0f, -4.0f, 2.5f, color);
        this.border(4.0f, 2.0f, 7.0f, 2.5f, color);
        this.border(-7.0f, -0.5f, -6.5f, 2.5f, color);
        this.border(6.5f, -0.5f, 7.0f, 2.5f, color);
        int black = 0xFF000000;
        Gui.drawRect((int) 7.0f, (int) -21.0f, (int) 7.3f, (int) -18.5f, black);
        Gui.drawRect((int) 6.2f, (int) -20.5f, (int) 6.5f, (int) -18.5f, black);
        Gui.drawRect((int) 6.5f, (int) -18.8f, (int) 7.0f, (int) -18.5f, black);
        Gui.drawRect((int) -7.3f, (int) -21.0f, (int) -7.0f, (int) -18.5f, black);
        Gui.drawRect((int) -6.5f, (int) -20.5f, (int) -6.2f, (int) -18.5f, black);
        Gui.drawRect((int) -7.0f, (int) -18.8f, (int) -6.5f, (int) -18.5f, black);
        Gui.drawRect((int) 4.0f, (int) -21.3f, (int) 7.3f, (int) -21.0f, black);
        Gui.drawRect((int) 4.0f, (int) -20.5f, (int) 6.3f, (int) -20.2f, black);
        Gui.drawRect((int) 4.0f, (int) -21.3f, (int) 3.8f, (int) -20.2f, black);
        Gui.drawRect((int) -7.3f, (int) -21.3f, (int) -4.0f, (int) -21.0f, black);
        Gui.drawRect((int) -6.5f, (int) -20.5f, (int) -4.0f, (int) -20.2f, black);
        Gui.drawRect((int) -4.0f, (int) -21.3f, (int) -3.8f, (int) -20.2f, black);
        Gui.drawRect((int) -7.0f, (int) 2.5f, (int) -4.0f, (int) 2.8f, black);
        Gui.drawRect((int) -6.5f, (int) 1.8f, (int) -4.0f, (int) 2.1f, black);
        Gui.drawRect((int) -7.0f, (int) -0.5f, (int) -6.5f, (int) -0.2f, black);
        Gui.drawRect((int) 4.0f, (int) 2.5f, (int) 7.0f, (int) 2.8f, black);
        Gui.drawRect((int) 4.0f, (int) 1.8f, (int) 6.5f, (int) 2.1f, black);
        Gui.drawRect((int) 4.0f, (int) 1.8f, (int) 3.8f, (int) 2.8f, black);
        Gui.drawRect((int) -7.3f, (int) -0.5f, (int) -7.0f, (int) 2.8f, black);
        Gui.drawRect((int) -6.5f, (int) -0.5f, (int) -6.2f, (int) 2.1f, black);
        Gui.drawRect((int) -3.8f, (int) 1.8f, (int) -4.0f, (int) 2.8f, black);
        Gui.drawRect((int) 7.0f, (int) -0.5f, (int) 7.3f, (int) 2.8f, black);
        Gui.drawRect((int) 6.5f, (int) -0.5f, (int) 6.2f, (int) 2.1f, black);
        Gui.drawRect((int) 6.5f, (int) -0.5f, (int) 7.0f, (int) -0.2f, black);
        this.endBadge();
    }

    // ---- Other badge : Ob0183.ModSpeed(posX, posY, posZ, color, color2) ----
    // Simple 4-edge frame in `color` (white, -1) plus one filled accent rect
    // in `color2` (the per-player hurt/base color) — a plain framed marker,
    // simpler than Corner's double-bracket icon.
    private void drawOtherBadge(double x, double y, double z, int color, int color2) {
        this.beginBadge(x, y, z);
        this.border(-7.0f, -21.0f, 7.0f, -20.5f, color);
        this.border(-7.0f, 2.0f, 7.0f, 2.5f, color);
        this.border(6.5f, -0.5f, 7.0f, 2.5f, color);
        this.border(6.5f, -21.0f, 7.0f, -18.5f, color);
        Gui.drawRect((int) -8.5f, (int) -17.0f, (int) -8.0f, (int) -2.0f, color2);
        this.endBadge();
    }

    public static enum Mode {
        Minecraft("Minecraft"),
        Outline("Outline"),
        Corner("Corner"),
        Box("Box"),
        Other("Other");

        private String enumName;

        private Mode(String enumName) {
            this.enumName = enumName;
        }

        public String enumName() {
            return this.enumName;
        }
    }
}
