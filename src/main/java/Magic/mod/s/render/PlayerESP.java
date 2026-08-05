/*
 * PlayerESP for Magic (aka "primordial") — MC 1.8.x
 *
 * Faithful port of the Peter-client PlayerESP. All five ESPModes are
 * reproduced, including the two that are NOT implemented inside the module
 * itself. Source mapping (Peter -> here):
 *
 *   Ob0110               -> this class (the PlayerESP module)
 *   Ob0110.Ob0216()       -> onRender3D listener body (Corner/Box/Other)
 *   Ob0110.Ob0186()       -> drawBox()          (Box mode)
 *   Ob0183.ModSpeed(x,y,z,color)        -> drawCornerBadge()  (Corner mode)
 *   Ob0183.ModSpeed(x,y,z,color,color2) -> drawOtherBadge()   (Other mode)
 *   Ob0183.ModSpeed(dX,dY,dW,dH,color)  -> border()
 *   Ob0183.ModFullBright()-> stencilSetup()      \
 *   Ob0183.Ob0033()       -> stencilFillPass()    | Outline mode, driven by
 *   Ob0183.Ob0272()       -> stencilOutlinePass() | the RendererLivingEntity
 *   Ob0183.Ob0171()       -> outlineDrawState()   | patch (see below)
 *   Ob0183.Ob0123()       -> stencilTeardown()   /
 *   Ob0183.Ob0254()       -> StencilUtil.checkSetupFBO() (Magic already has
 *                            this exact helper — same DEPTH24_STENCIL8
 *                            renderbuffer re-attach Peter's Ob0254 did)
 *   entity.atq            -> EntityLivingBase.hurtTime
 *   entity.rx()           -> EntityLivingBase.isEntityAlive()
 *   Ob0106.Ob0219()       -> Magic.getClientColor()  (client accent color)
 *   Ob0115.Ob0263()       -> FriendManager.isFriend()
 *
 * WHERE THE TWO NON-MODULE MODES LIVE
 * -----------------------------------
 * Peter implements "Outline" and "Minecraft" by patching vanilla renderer
 * classes, not in Ob0110. Both patches are reproduced by tools/PatchMagic.java:
 *
 *   - "Outline"  -> fZ.java (= RendererLivingEntity here). Inside doRender's
 *     non-renderOutlines branch, right after setDoRenderBrightness, a
 *     4-pass GL_STENCIL_TEST silhouette is drawn around the real model:
 *         renderModel(); stencilSetup();
 *         renderModel(); stencilFillPass();
 *         renderModel(); stencilOutlinePass();
 *         applyOutlineColor(); outlineDrawState();
 *         renderModel(); stencilTeardown();
 *     then the normal renderModel() call proceeds. 1:1 with Peter, including
 *     that it covers every EntityLivingBase except your own player (Peter
 *     checks `entity instanceof nH && entity != f.jF`, not "is a player").
 *
 *   - "Minecraft" -> ed.java (= RenderGlobal here). Peter ORs his check into
 *     isRenderEntityOutlines(), reusing vanilla's own spectator-glow pass
 *     (entityOutlineFramebuffer/entityOutlineShader, driven by
 *     shaders/post/entity_outline.json) instead of drawing anything itself.
 *
 * THE ONE DELIBERATE DIFFERENCE FROM PETER (and why)
 * --------------------------------------------------
 * Peter's isRenderEntityOutlines() equivalent returns true *unconditionally*
 * for Minecraft mode. His client has no OptiFine, so that is safe there.
 * Magic ships OptiFine, whose isRenderEntityOutlines() carries an extra
 * guard — !(Config.isFastRender() || isShaders() || isAntialiasing()) —
 * precisely because that framebuffer pass is incompatible with those
 * features: forcing it on rebinds mc.getFramebuffer() mid-pipeline and the
 * screen goes black. So the patch here keeps OptiFine's guard instead of
 * bypassing it. Same visual result wherever the effect can legally run,
 * and it degrades to "mode does nothing" instead of a black screen when
 * FastRender/Shaders/AA are on.
 */
package Magic.mod.s.render;

import Magic.Magic;
import Magic.ink.event.s.EventRender3D;
import Magic.mod.Category;
import Magic.mod.Module;
import Magic.mod.value.values.EnumValue;
import Magic.utils.Friend.FriendManager;
import Magic.utils.player.ClientUtils;
import Magic.utils.render.StencilUtil;
import java.awt.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import org.lwjgl.opengl.GL11;
import pisi.unitedmeows.eventapi.event.listener.Listener;

public class PlayerESP extends Module {

    /** Peter's PlayerESP had exactly one setting: the ESPModes combo. */
    private final EnumValue<Mode> mode = new EnumValue<Mode>("Mode", this, Mode.class, "ESP render style.");

    private static final Color HURT_COLOR = new Color(255, 50, 10, 255);
    private static final Color FRIEND_COLOR = new Color(255, 255, 255, 255);
    /** Outline mode uses its own, slightly different red — as in the original. */
    private static final Color OUTLINE_HURT_COLOR = new Color(255, 10, 10, 255);
    private static final Color OUTLINE_COLOR = new Color(255, 255, 255, 255);

    private final Listener<EventRender3D> onRender3D = new Listener<EventRender3D>(event -> {
        if (this.mc.theWorld == null || this.mc.thePlayer == null) {
            return;
        }
        Mode m = this.mode.getValue();
        // Minecraft and Outline draw nothing from here — they're driven by the
        // RenderGlobal / RendererLivingEntity patches, exactly as in Peter.
        if (m != Mode.Corner && m != Mode.Box && m != Mode.Other) {
            return;
        }
        float partialTicks = event.partialTicks();
        try {
            for (EntityPlayer player : ClientUtils.getPlayers()) {
                if (player == this.mc.thePlayer || player.isDead || !player.isEntityAlive()) {
                    continue;
                }
                // Caught per-entity: this runs on the shared BasicEventSystem,
                // which doesn't guard listener.call() at all, so an throw here
                // would take down every other module's EventRender3D listener
                // (NameTags etc.) for the rest of the frame, not just ours.
                try {
                    switch (m) {
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
                } catch (Exception ignored) {
                }
            }
        } finally {
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        }
    });

    public PlayerESP() {
        super("PlayerESP", 0, Category.Render, "Highlights players (Minecraft/Outline/Corner/Box/Other).");
        instance = this;
    }

    @Override
    public void onEnable() {
        super.onEnable();
        this.warnIfVanillaOutlineBlocked();
    }

    @Override
    public void onSuffixChange() {
        this.setSuffix(this.mode.getValue().enumName());
        super.onSuffixChange();
        this.warnIfVanillaOutlineBlocked();
    }

    /**
     * Set from the constructor. Modules.loadModules() creates exactly one
     * instance, and holding it directly keeps the two renderer patches off
     * Modules.get() — that lookup is keyed by Class identity and would
     * silently return null under a launcher with a separate classloader,
     * leaving the mode dead with no error anywhere.
     */
    private static volatile PlayerESP instance;

    private static PlayerESP active(Mode wanted) {
        PlayerESP module = instance;
        try {
            if (module != null && module.isEnabled() && module.mode.getValue() == wanted) {
                return module;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * Which OptiFine feature, if any, makes the vanilla entity-outline pass
     * unusable right now. Peter's client has no OptiFine so it never had to
     * care; here it is the difference between the mode working and a black
     * screen, so it is worth telling the user about instead of failing mute.
     */
    private static String vanillaOutlineBlocker() {
        try {
            if (optifine.Config.isFastRender()) {
                return "Fast Render";
            }
            if (optifine.Config.isShaders()) {
                return "shaders";
            }
            if (optifine.Config.isAntialiasing()) {
                return "Antialiasing";
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private void warnIfVanillaOutlineBlocked() {
        try {
            if (!this.isEnabled() || this.mode.getValue() != Mode.Minecraft) {
                return;
            }
            String blocker = vanillaOutlineBlocker();
            if (blocker != null) {
                ClientUtils.debug("PlayerESP: Minecraft mode is unavailable while OptiFine "
                        + blocker + " is on — turn it off, or use Outline instead.");
            }
        } catch (Throwable ignored) {
        }
    }

    // ================= hooks used by the two renderer patches =================

    /**
     * Called from the patched RenderGlobal.isRenderEntityOutlines().
     * The caller ANDs this with OptiFine's own compatibility guard and the
     * framebuffer/shader null-checks — see the header note.
     */
    public static boolean wantsVanillaOutline() {
        return active(Mode.Minecraft) != null;
    }

    /**
     * Called from the patched RendererLivingEntity.doRender().
     * Mirrors Peter's `entity instanceof nH && entity != f.jF` — every living
     * entity except your own player, not only players.
     */
    public static boolean wantsStencilOutline(EntityLivingBase entity) {
        if (entity == null || active(Mode.Outline) == null) {
            return false;
        }
        return entity != Minecraft.getMinecraft().thePlayer;
    }

    // ---- Ob0183.ModFullBright() ----
    public static void stencilSetup() {
        StencilUtil.checkSetupFBO(Minecraft.getMinecraft().getFramebuffer()); // Ob0254
        GL11.glPushAttrib(1048575);             // GL_ALL_ATTRIB_BITS
        GL11.glDisable(3008);                   // GL_ALPHA_TEST
        GL11.glDisable(3553);                   // GL_TEXTURE_2D
        GL11.glDisable(2896);                   // GL_LIGHTING
        GL11.glEnable(3042);                    // GL_BLEND
        GL11.glBlendFunc(770, 771);
        GL11.glLineWidth(2.0f);
        GL11.glEnable(2848);                    // GL_LINE_SMOOTH
        GL11.glEnable(2960);                    // GL_STENCIL_TEST
        GL11.glClear(1024);                     // GL_STENCIL_BUFFER_BIT
        GL11.glClearStencil(15);
        GL11.glStencilFunc(512, 1, 15);         // GL_NEVER, ref 1
        GL11.glStencilOp(7681, 7681, 7681);     // GL_REPLACE
        GL11.glPolygonMode(1032, 6913);         // GL_FRONT_AND_BACK, GL_LINE
    }

    // ---- Ob0183.Ob0033() ----
    public static void stencilFillPass() {
        GL11.glStencilFunc(512, 0, 15);         // GL_NEVER, ref 0
        GL11.glStencilOp(7681, 7681, 7681);     // GL_REPLACE
        GL11.glPolygonMode(1032, 6914);         // GL_FILL
    }

    // ---- Ob0183.Ob0272() ----
    public static void stencilOutlinePass() {
        GL11.glStencilFunc(514, 1, 15);         // GL_EQUAL, ref 1
        GL11.glStencilOp(7680, 7680, 7680);     // GL_KEEP
        GL11.glPolygonMode(1032, 6913);         // GL_LINE
    }

    /** Peter: hurtTime > 0 ? rgba(255,10,10) : white. Note the 10 vs Corner's 50. */
    public static void applyOutlineColor(EntityLivingBase entity) {
        Color color = entity != null && entity.hurtTime > 0 ? OUTLINE_HURT_COLOR : OUTLINE_COLOR;
        GL11.glColor4d((double) color.getRed() / 255.0, (double) color.getGreen() / 255.0,
                (double) color.getBlue() / 255.0, (double) color.getAlpha() / 255.0);
    }

    // ---- Ob0183.Ob0171() ----
    public static void outlineDrawState() {
        GL11.glDepthMask(false);
        GL11.glDisable(2929);                   // GL_DEPTH_TEST
        GL11.glEnable(10754);                   // GL_POLYGON_OFFSET_LINE
        GL11.glPolygonOffset(1.0f, -2000000.0f);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0f, 240.0f);
    }

    // ---- Ob0183.Ob0123() ----
    public static void stencilTeardown() {
        GL11.glPolygonOffset(1.0f, 2000000.0f);
        GL11.glDisable(10754);                  // GL_POLYGON_OFFSET_LINE
        GL11.glEnable(2929);                    // GL_DEPTH_TEST
        GL11.glDepthMask(true);
        GL11.glDisable(2960);                   // GL_STENCIL_TEST
        GL11.glDisable(2848);                   // GL_LINE_SMOOTH
        GL11.glHint(3154, 4352);                // GL_LINE_SMOOTH_HINT, GL_DONT_CARE
        GL11.glEnable(3042);                    // GL_BLEND
        GL11.glEnable(2896);                    // GL_LIGHTING
        GL11.glEnable(3553);                    // GL_TEXTURE_2D
        GL11.glEnable(3008);                    // GL_ALPHA_TEST
        GL11.glPopAttrib();
    }

    // ============================ module-side modes ============================

    // ---- Corner mode : Ob0110's "Corner" branch ----
    // Friend -> white, hurt flash -> orange-red, else the client accent color.
    // Uses the interpolated render position (tickX/tickY/tickZ in the source).
    private void renderCorner(EntityPlayer player, float partialTicks) {
        double[] pos = this.interpolatedRenderPos(player, partialTicks);
        this.drawCornerBadge(pos[0], pos[1], pos[2], this.cornerColor(player).getRGB());
    }

    private Color cornerColor(EntityPlayer player) {
        if (player.hurtTime > 0) {
            return HURT_COLOR;
        }
        if (FriendManager.isFriend(player.getName())) {
            return FRIEND_COLOR;
        }
        return Magic.getClientColor();
    }

    // ---- Box mode : Ob0110.Ob0186() ----
    // Uses the entity's RAW (non-interpolated) position, same as the original —
    // Box reads entity.aqZ/ara/arb directly rather than lastTickPos+partialTicks,
    // so unlike Corner/Other it jitters slightly between ticks. Straight port,
    // not a bug introduced here. The box is also narrower than the real hitbox
    // (0.5 vs the player's 0.6), also as in the source. And in the original both
    // the friend and non-friend branch resolved to the same literal white
    // (isFriend ? white : white) — reproduced rather than "fixed".
    private void renderBox(EntityPlayer player) {
        double x = player.posX - 0.5 - this.mc.getRenderManager().renderPosX;
        double y = player.posY - this.mc.getRenderManager().renderPosY;
        double z = player.posZ - 0.5 - this.mc.getRenderManager().renderPosZ;
        AxisAlignedBB box = new AxisAlignedBB(x + 0.3, y, z + 0.3, x + 0.8, y + 1.9, z + 0.8);
        this.drawBox(box, player.hurtTime > 0 ? HURT_COLOR : FRIEND_COLOR);
    }

    // ---- Other mode : Ob0110's "Other" branch ----
    // This branch never consulted FriendManager in the original either — only
    // hurt-flash vs. the accent color. Kept exactly that way.
    private void renderOther(EntityPlayer player, float partialTicks) {
        double[] pos = this.interpolatedRenderPos(player, partialTicks);
        int accent = (player.hurtTime > 0 ? HURT_COLOR : Magic.getClientColor()).getRGB();
        this.drawOtherBadge(pos[0], pos[1], pos[2], -1, accent);
    }

    private double[] interpolatedRenderPos(EntityPlayer player, float partialTicks) {
        double x = player.lastTickPosX + (player.posX - player.lastTickPosX) * (double) partialTicks - this.mc.getRenderManager().renderPosX;
        double y = player.lastTickPosY + (player.posY - player.lastTickPosY) * (double) partialTicks - this.mc.getRenderManager().renderPosY;
        double z = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * (double) partialTicks - this.mc.getRenderManager().renderPosZ;
        return new double[]{x, y, z};
    }

    // ---- Box wireframe : the drawing half of Ob0110.Ob0186(), i.e. the vanilla
    // RenderGlobal.drawSelectionBoundingBox algorithm it reached through
    // ed.Ob0151(new vr(...)). Depth test is unconditionally off, exactly like
    // the source — Box always renders through terrain, there was no toggle. ----
    private void drawBox(AxisAlignedBB bb, Color color) {
        GlStateManager.pushMatrix();
        try {
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
        } finally {
            GlStateManager.depthMask(true);
            GlStateManager.enableDepth();
            GlStateManager.enableTexture2D();
            GlStateManager.disableBlend();
            GlStateManager.popMatrix();
        }
    }

    // ---- Billboard transform shared by Corner/Other, straight from Ob0183's
    // pushMatrix/translate/rotate(-fy.Vo)/scale(-0.1,-0.1,0.1) sequence —
    // yaw-only billboard (no pitch rotate in the source), mirrored scale on
    // X/Y exactly as written, depth test off unconditionally. ----
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

    /**
     * Ob0183's (double,double,double,double,int) rect primitive: vanilla
     * Gui.drawRect's exact algorithm — same swap, same colour unpacking,
     * same POSITION quad — but in double precision.
     *
     * This has to be double. These badges are drawn in a 0.1-scaled
     * billboard space where nearly every coordinate is fractional (7.3,
     * -20.5, 6.2, -18.8 …). Routing them through the int-typed
     * Gui.drawRect truncates each one, which is what made Corner's black
     * outline land on the wrong pixels and collapsed several of Other's
     * edges to zero width or height so they vanished entirely
     * (e.g. 6.5→6 and -0.5+0.5=0.0→0 gives a rect of height 0).
     */
    private static void drawRect(double left, double top, double right, double bottom, int color) {
        double swap;
        if (left < right) {
            swap = left;
            left = right;
            right = swap;
        }
        if (top < bottom) {
            swap = top;
            top = bottom;
            bottom = swap;
        }
        float a = (float) (color >> 24 & 0xFF) / 255.0f;
        float r = (float) (color >> 16 & 0xFF) / 255.0f;
        float g = (float) (color >> 8 & 0xFF) / 255.0f;
        float b = (float) (color & 0xFF) / 255.0f;
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(r, g, b, a);
        wr.begin(7, DefaultVertexFormats.POSITION);
        wr.pos(left, bottom, 0.0).endVertex();
        wr.pos(right, bottom, 0.0).endVertex();
        wr.pos(right, top, 0.0).endVertex();
        wr.pos(left, top, 0.0).endVertex();
        tessellator.draw();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    // border(): Ob0183.ModSpeed(drawX, drawY, drawWidth, drawHeight, color) —
    // 4 thin edge rects framing a box. drawWidth/drawHeight are the box's
    // right/bottom edge, not a width/height, exactly as named in the source.
    private static void border(float drawX, float drawY, float drawWidth, float drawHeight, int color) {
        drawRect(drawX, drawY, drawWidth, drawY + 0.5f, color);
        drawRect(drawX, drawY + 0.5f, drawX + 0.5f, drawHeight, color);
        drawRect(drawWidth - 0.5f, drawY + 0.5f, drawWidth, drawHeight - 0.5f, color);
        drawRect(drawX + 0.5f, drawHeight - 0.5f, drawWidth, drawHeight, color);
    }

    // ---- Corner badge : Ob0183.ModSpeed(double posX, posY, posZ, int color) ----
    // The 32-rect double-bracket icon — two vertical corner brackets (left
    // [-7,-4], right [4,7]) plus a 1px black outline drawn as thin rects on
    // top. Every coordinate below is copied verbatim from the decompile.
    private void drawCornerBadge(double x, double y, double z, int color) {
        this.beginBadge(x, y, z);
        try {
            border(4.0f, -21.0f, 7.0f, -20.5f, color);
            border(-7.0f, -21.0f, -4.0f, -20.5f, color);
            border(6.5f, -21.0f, 7.0f, -18.5f, color);
            border(-7.0f, -21.0f, -6.5f, -18.5f, color);
            border(-7.0f, 2.0f, -4.0f, 2.5f, color);
            border(4.0f, 2.0f, 7.0f, 2.5f, color);
            border(-7.0f, -0.5f, -6.5f, 2.5f, color);
            border(6.5f, -0.5f, 7.0f, 2.5f, color);
            int black = 0xFF000000;
            drawRect(7.0f, -21.0f, 7.3f, -18.5f, black);
            drawRect(6.2f, -20.5f, 6.5f, -18.5f, black);
            drawRect(6.5f, -18.8f, 7.0f, -18.5f, black);
            drawRect(-7.3f, -21.0f, -7.0f, -18.5f, black);
            drawRect(-6.5f, -20.5f, -6.2f, -18.5f, black);
            drawRect(-7.0f, -18.8f, -6.5f, -18.5f, black);
            drawRect(4.0f, -21.3f, 7.3f, -21.0f, black);
            drawRect(4.0f, -20.5f, 6.3f, -20.2f, black);
            drawRect(4.0f, -21.3f, 3.8f, -20.2f, black);
            drawRect(-7.3f, -21.3f, -4.0f, -21.0f, black);
            drawRect(-6.5f, -20.5f, -4.0f, -20.2f, black);
            drawRect(-4.0f, -21.3f, -3.8f, -20.2f, black);
            drawRect(-7.0f, 2.5f, -4.0f, 2.8f, black);
            drawRect(-6.5f, 1.8f, -4.0f, 2.1f, black);
            drawRect(-7.0f, -0.5f, -6.5f, -0.2f, black);
            drawRect(4.0f, 2.5f, 7.0f, 2.8f, black);
            drawRect(4.0f, 1.8f, 6.5f, 2.1f, black);
            drawRect(4.0f, 1.8f, 3.8f, 2.8f, black);
            drawRect(-7.3f, -0.5f, -7.0f, 2.8f, black);
            drawRect(-6.5f, -0.5f, -6.2f, 2.1f, black);
            drawRect(-3.8f, 1.8f, -4.0f, 2.8f, black);
            drawRect(7.0f, -0.5f, 7.3f, 2.8f, black);
            drawRect(6.5f, -0.5f, 6.2f, 2.1f, black);
            drawRect(6.5f, -0.5f, 7.0f, -0.2f, black);
        } finally {
            this.endBadge();
        }
    }

    // ---- Other badge : Ob0183.ModSpeed(posX, posY, posZ, color, color2) ----
    // 4-edge frame in `color` (white, -1) plus one filled accent rect in
    // `color2` (the per-player hurt/accent color).
    private void drawOtherBadge(double x, double y, double z, int color, int color2) {
        this.beginBadge(x, y, z);
        try {
            border(-7.0f, -21.0f, 7.0f, -20.5f, color);
            border(-7.0f, 2.0f, 7.0f, 2.5f, color);
            border(6.5f, -0.5f, 7.0f, 2.5f, color);
            border(6.5f, -21.0f, 7.0f, -18.5f, color);
            drawRect(-8.5f, -17.0f, -8.0f, -2.0f, color2);
        } finally {
            this.endBadge();
        }
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
