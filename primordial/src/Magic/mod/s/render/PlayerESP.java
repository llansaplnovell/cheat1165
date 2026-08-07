package Magic.mod.s.render;

import Magic.ink.event.s.EventRender3D;
import Magic.mod.Category;
import Magic.mod.Module;
import Magic.mod.value.values.BoolValue;
import Magic.mod.value.values.ColorAlphaValue;
import Magic.mod.value.values.EnumValue;
import Magic.utils.Friend.FriendManager;
import Magic.utils.player.ClientUtils;
import Magic.utils.render.StencilUtil;
import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.layers.LayerArmorBase;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ResourceLocation;
import optifine.Config;
import org.lwjgl.opengl.GL11;
import pisi.unitedmeows.eventapi.event.listener.Listener;

public class PlayerESP extends Module {

    private final EnumValue<Mode> mode = new EnumValue<Mode>("Mode", this, Mode.class, "ESP render style.");
    /**
     * One color for every mode - Minecraft, Outline, Corner, Box and Other all read
     * it, alpha included. Hurt and friend highlights keep their own hue but inherit
     * the alpha picked here.
     */
    private final ColorAlphaValue color = new ColorAlphaValue("Color", this, new Color(255, 255, 255, 255), "ESP color and transparency.");
    private final BoolValue throughArmor = new BoolValue("ThroughArmor", this, true, "Show the outline through armor.", () -> this.mode.getValue() == Mode.Outline || this.mode.getValue() == Mode.Minecraft);

    private static final Color HURT_COLOR = new Color(255, 50, 10, 255);
    private static final Color FRIEND_COLOR = new Color(255, 255, 255, 255);
    private static final Color OUTLINE_HURT_COLOR = new Color(255, 10, 10, 255);
    private static final Color OUTLINE_COLOR = new Color(255, 255, 255, 255);

    private final Listener<EventRender3D> onRender3D = new Listener<EventRender3D>(eventRender3D -> {
        if (this.mc.theWorld == null || this.mc.thePlayer == null) {
            return;
        }
        Mode mode = this.mode.getValue();
        if (mode != Mode.Corner && mode != Mode.Box && mode != Mode.Other) {
            return;
        }
        float f = eventRender3D.partialTicks();
        try {
            for (EntityPlayer entityPlayer : ClientUtils.getPlayers()) {
                if (entityPlayer == this.mc.thePlayer || entityPlayer.isDead || !entityPlayer.isEntityAlive()) continue;
                try {
                    switch (mode) {
                        case Corner: {
                            this.renderCorner(entityPlayer, f);
                            break;
                        }
                        case Box: {
                            this.renderBox(entityPlayer);
                            break;
                        }
                        case Other: {
                            this.renderOther(entityPlayer, f);
                            break;
                        }
                        default: {
                            break;
                        }
                    }
                } catch (Exception exception) {
                }
            }
        } finally {
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        }
    });

    private Mode reportedMode;
    private static volatile PlayerESP instance;
    private static volatile String compositeGlState;
    private static volatile boolean sawFramebuffer;
    private static volatile boolean sawShader;
    private static volatile boolean hookRan;
    private static volatile boolean armorMaskWritten;

    public PlayerESP() {
        super("PlayerESP", 0, Category.Render, "Highlights players (Minecraft/Outline/Corner/Box/Other).");
        instance = this;
    }

    @Override
    public void onEnable() {
        super.onEnable();
        this.reportedMode = this.mode.getValue();
        this.warnIfVanillaOutlineBlocked();
    }

    @Override
    public void onSuffixChange() {
        this.setSuffix(this.mode.getValue().enumName());
        super.onSuffixChange();
        if (this.mode.getValue() != this.reportedMode) {
            this.reportedMode = this.mode.getValue();
            this.warnIfVanillaOutlineBlocked();
        }
    }

    /** Color used for a given entity, honoring hurt/friend highlights and the alpha. */
    private Color espColor(EntityLivingBase entity) {
        Color base = this.color.getValue();
        if (entity != null && entity.hurtTime > 0) {
            return PlayerESP.withAlpha(HURT_COLOR, base.getAlpha());
        }
        if (entity instanceof EntityPlayer && FriendManager.isFriend(((EntityPlayer) entity).getName())) {
            return PlayerESP.withAlpha(FRIEND_COLOR, base.getAlpha());
        }
        return base;
    }

    private static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    /**
     * Color the vanilla entity-outline pass (Mode.Minecraft) should use, as ARGB, or
     * 0 when this module is not driving that pass right now.
     *
     * <p>Called from the patched {@code RendererLivingEntity.setScoreTeamColor} - see
     * {@code patches/RendererLivingEntity.setScoreTeamColor.md}.
     */
    public static int outlineColorOverride(EntityLivingBase entity) {
        PlayerESP playerESP = PlayerESP.active(Mode.Minecraft);
        if (playerESP == null) {
            return 0;
        }
        try {
            return playerESP.espColor(entity).getRGB();
        } catch (Throwable throwable) {
            return 0;
        }
    }

    private static PlayerESP active(Mode mode) {
        PlayerESP playerESP = instance;
        try {
            if (playerESP != null && playerESP.isEnabled() && playerESP.mode.getValue() == mode) {
                return playerESP;
            }
        } catch (Throwable throwable) {
            // empty catch block
        }
        return null;
    }

    private static String vanillaOutlineBlocker() {
        try {
            if (Config.isFastRender()) {
                return "Fast Render";
            }
            if (Config.isShaders()) {
                return "shaders";
            }
            if (Config.isAntialiasing()) {
                return "Antialiasing";
            }
        } catch (Throwable throwable) {
            // empty catch block
        }
        return null;
    }

    private void warnIfVanillaOutlineBlocked() {
        try {
            if (!this.isEnabled() || this.mode.getValue() != Mode.Minecraft) {
                return;
            }
            Minecraft minecraft = Minecraft.getMinecraft();
            if (!sawShader || !sawFramebuffer) {
                try {
                    minecraft.renderGlobal.makeEntityOutlineShader();
                } catch (Throwable throwable) {
                    // empty catch block
                }
            }
            String string = PlayerESP.vanillaOutlineBlocker();
            if (!OpenGlHelper.shadersSupported) {
                ClientUtils.debug((Object) "PlayerESP: Minecraft mode needs shader support, which this driver reports as unavailable. Use Outline instead.");
            } else if (string != null) {
                ClientUtils.debug((Object) ("PlayerESP: Minecraft mode is unavailable while OptiFine " + string + " is on \u2014 turn it off, or use Outline instead."));
            } else if (!OpenGlHelper.isFramebufferEnabled()) {
                ClientUtils.debug((Object) "PlayerESP: Minecraft mode needs framebuffers, which are off right now (Video Settings -> FBO). Use Outline instead.");
            } else if (!(!hookRan || sawFramebuffer && sawShader)) {
                ClientUtils.debug((Object) "PlayerESP: Minecraft mode could not build the entity outline shader (check the log for \"Failed to load shader\"). Use Outline instead.");
            } else {
                ClientUtils.debug((Object) "PlayerESP: Minecraft mode active.");
            }
            ClientUtils.debug((Object) ("PlayerESP state: fboEnabled=" + OpenGlHelper.isFramebufferEnabled() + " chain=" + PlayerESP.loadedOutlineChain() + " outlineShader=" + sawShader + " outlineFbo=" + sawFramebuffer + " hookRan=" + hookRan));
            ClientUtils.debug((Object) ("PlayerESP composite: " + compositeGlState + " | mainFbo=" + (minecraft.getFramebuffer() == null ? "null" : String.valueOf(minecraft.getFramebuffer().framebufferObject)) + " display=" + minecraft.displayWidth + "x" + minecraft.displayHeight));
        } catch (Throwable throwable) {
            // empty catch block
        }
    }

    public static void forceOutlineBlend() {
        if (!PlayerESP.vanillaOutlineHook(sawFramebuffer, sawShader)) {
            return;
        }
        try {
            compositeGlState = "blend=" + GL11.glIsEnabled(3042) + " src=" + GL11.glGetInteger(3041) + " dst=" + GL11.glGetInteger(3040) + " alphaTest=" + GL11.glIsEnabled(3008) + " depthTest=" + GL11.glIsEnabled(2929) + " boundFbo=" + GL11.glGetInteger(36006);
            GL11.glEnable(3042);
            GL11.glBlendFunc(770, 771);
            GL11.glDisable(3008);
            if (armorMaskWritten) {
                GL11.glEnable(2960);
                GL11.glStencilMask(0);
                GL11.glStencilFunc(517, 1, 255);
                GL11.glStencilOp(7680, 7680, 7680);
            }
        } catch (Throwable throwable) {
            compositeGlState = "sample failed: " + throwable;
        }
    }

    private static String loadedOutlineChain() {
        InputStream inputStream = null;
        try {
            inputStream = Minecraft.getMinecraft().getResourceManager().getResource(new ResourceLocation("shaders/post/magic_esp_outline.json")).getInputStream();
            byte[] byArray = new byte[8192];
            int n = 0;
            int n2;
            while (n < byArray.length && (n2 = inputStream.read(byArray, n, byArray.length - n)) > 0) {
                n += n2;
            }
            String string = new String(byArray, 0, n, "UTF-8");
            return string.contains("magic_esp_blur") ? "ours" : "NOT-OURS";
        } catch (Throwable throwable) {
            return "unreadable(" + throwable.getClass().getSimpleName() + ")";
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (IOException iOException) {
                // empty catch block
            }
        }
    }

    public static boolean vanillaOutlineHook(boolean bl, boolean bl2) {
        sawFramebuffer = bl;
        sawShader = bl2;
        hookRan = true;
        if (PlayerESP.active(Mode.Minecraft) == null) {
            return false;
        }
        if (!bl || !bl2) {
            return false;
        }
        try {
            if (!OpenGlHelper.isFramebufferEnabled()) {
                return false;
            }
        } catch (Throwable throwable) {
            return false;
        }
        return PlayerESP.vanillaOutlineBlocker() == null;
    }

    public static boolean wantsStencilOutline(EntityLivingBase entityLivingBase) {
        if (entityLivingBase == null || PlayerESP.active(Mode.Outline) == null) {
            return false;
        }
        return entityLivingBase != Minecraft.getMinecraft().thePlayer;
    }

    public static boolean stencilOutlineBeforeLayers(EntityLivingBase entityLivingBase) {
        PlayerESP playerESP = PlayerESP.active(Mode.Outline);
        return playerESP != null && !playerESP.throughArmor.getValue().booleanValue() && PlayerESP.wantsStencilOutline(entityLivingBase);
    }

    public static boolean stencilOutlineOverLayers(EntityLivingBase entityLivingBase) {
        PlayerESP playerESP = PlayerESP.active(Mode.Outline);
        return playerESP != null && playerESP.throughArmor.getValue().booleanValue() && PlayerESP.wantsStencilOutline(entityLivingBase);
    }

    public static boolean armorMasksVanillaOutline(EntityLivingBase entityLivingBase) {
        return PlayerESP.armorMaskWanted(entityLivingBase) && PlayerESP.vanillaOutlineHook(sawFramebuffer, sawShader);
    }

    static boolean armorMaskWanted(EntityLivingBase entityLivingBase) {
        PlayerESP playerESP = PlayerESP.active(Mode.Minecraft);
        if (playerESP == null || playerESP.throughArmor.getValue().booleanValue()) {
            return false;
        }
        return !(entityLivingBase instanceof EntityPlayer) || !((EntityPlayer) entityLivingBase).isSpectator();
    }

    public static void markArmorMask(List<?> list, EntityLivingBase entityLivingBase, float f, float f2, float f3, float f4, float f5, float f6, float f7) {
        if (list == null || entityLivingBase == null) {
            return;
        }
        try {
            StencilUtil.checkSetupFBO(Minecraft.getMinecraft().getFramebuffer());
            GL11.glEnable(2960);
            GL11.glStencilMask(255);
            GL11.glStencilFunc(519, 1, 255);
            GL11.glStencilOp(7680, 7680, 7681);
            GL11.glColorMask(false, false, false, false);
            GlStateManager.depthMask(false);
            for (Object obj : list) {
                if (!(obj instanceof LayerArmorBase)) continue;
                try {
                    ((LayerArmorBase<?>) obj).doRenderLayer(entityLivingBase, f, f2, f3, f4, f5, f6, f7);
                } catch (Throwable throwable) {
                }
            }
            armorMaskWritten = true;
        } catch (Throwable throwable) {
        } finally {
            GL11.glColorMask(true, true, true, true);
            GL11.glStencilMask(255);
            GL11.glDisable(2960);
            GlStateManager.depthMask(true);
        }
    }

    public static void afterOutlineComposite() {
        try {
            GL11.glStencilMask(255);
            GL11.glStencilFunc(519, 0, 255);
            GL11.glStencilOp(7680, 7680, 7680);
            GL11.glDisable(2960);
            if (armorMaskWritten) {
                GL11.glClearStencil(0);
                GL11.glClear(1024);
                armorMaskWritten = false;
            }
        } catch (Throwable throwable) {
            // empty catch block
        }
    }

    public static void stencilSetup() {
        StencilUtil.checkSetupFBO(Minecraft.getMinecraft().getFramebuffer());
        GL11.glPushAttrib(1048575);
        GL11.glDisable(3008);
        GL11.glDisable(3553);
        GL11.glDisable(2896);
        GL11.glEnable(3042);
        GL11.glBlendFunc(770, 771);
        GL11.glLineWidth(2.0f);
        GL11.glEnable(2848);
        GL11.glEnable(2960);
        GL11.glClear(1024);
        GL11.glClearStencil(15);
        GL11.glStencilFunc(512, 1, 15);
        GL11.glStencilOp(7681, 7681, 7681);
        GL11.glPolygonMode(1032, 6913);
    }

    public static void stencilFillPass() {
        GL11.glStencilFunc(512, 0, 15);
        GL11.glStencilOp(7681, 7681, 7681);
        GL11.glPolygonMode(1032, 6914);
    }

    public static void stencilOutlinePass() {
        GL11.glStencilFunc(514, 1, 15);
        GL11.glStencilOp(7680, 7680, 7680);
        GL11.glPolygonMode(1032, 6913);
    }

    /** Outline mode color - driven by the Color value, hurt players stay red. */
    public static void applyOutlineColor(EntityLivingBase entityLivingBase) {
        Color color;
        PlayerESP playerESP = instance;
        if (playerESP == null) {
            color = entityLivingBase != null && entityLivingBase.hurtTime > 0 ? OUTLINE_HURT_COLOR : OUTLINE_COLOR;
        } else if (entityLivingBase != null && entityLivingBase.hurtTime > 0) {
            color = PlayerESP.withAlpha(OUTLINE_HURT_COLOR, playerESP.color.getValue().getAlpha());
        } else {
            color = playerESP.espColor(entityLivingBase);
        }
        GL11.glColor4d((double) color.getRed() / 255.0, (double) color.getGreen() / 255.0, (double) color.getBlue() / 255.0, (double) color.getAlpha() / 255.0);
    }

    public static void outlineDrawState() {
        GL11.glDepthMask(false);
        GL11.glDisable(2929);
        GL11.glEnable(10754);
        GL11.glPolygonOffset(1.0f, -2000000.0f);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0f, 240.0f);
    }

    public static void stencilTeardown() {
        GL11.glPolygonOffset(1.0f, 2000000.0f);
        GL11.glDisable(10754);
        GL11.glEnable(2929);
        GL11.glDepthMask(true);
        GL11.glDisable(2960);
        GL11.glDisable(2848);
        GL11.glHint(3154, 4352);
        GL11.glEnable(3042);
        GL11.glEnable(2896);
        GL11.glEnable(3553);
        GL11.glEnable(3008);
        GL11.glPopAttrib();
    }

    private void renderCorner(EntityPlayer entityPlayer, float f) {
        double[] dArray = this.interpolatedRenderPos(entityPlayer, f);
        this.drawCornerBadge(dArray[0], dArray[1], dArray[2], this.espColor(entityPlayer).getRGB());
    }

    private void renderBox(EntityPlayer entityPlayer) {
        double d = entityPlayer.posX - 0.5 - this.mc.getRenderManager().renderPosX;
        double d2 = entityPlayer.posY - this.mc.getRenderManager().renderPosY;
        double d3 = entityPlayer.posZ - 0.5 - this.mc.getRenderManager().renderPosZ;
        AxisAlignedBB axisAlignedBB = new AxisAlignedBB(d + 0.3, d2, d3 + 0.3, d + 0.8, d2 + 1.9, d3 + 0.8);
        this.drawBox(axisAlignedBB, this.espColor(entityPlayer));
    }

    private void renderOther(EntityPlayer entityPlayer, float f) {
        double[] dArray = this.interpolatedRenderPos(entityPlayer, f);
        Color color = this.espColor(entityPlayer);
        int borderColor = PlayerESP.withAlpha(Color.WHITE, color.getAlpha()).getRGB();
        this.drawOtherBadge(dArray[0], dArray[1], dArray[2], borderColor, color.getRGB());
    }

    private double[] interpolatedRenderPos(EntityPlayer entityPlayer, float f) {
        double d = entityPlayer.lastTickPosX + (entityPlayer.posX - entityPlayer.lastTickPosX) * (double) f - this.mc.getRenderManager().renderPosX;
        double d2 = entityPlayer.lastTickPosY + (entityPlayer.posY - entityPlayer.lastTickPosY) * (double) f - this.mc.getRenderManager().renderPosY;
        double d3 = entityPlayer.lastTickPosZ + (entityPlayer.posZ - entityPlayer.lastTickPosZ) * (double) f - this.mc.getRenderManager().renderPosZ;
        return new double[]{d, d2, d3};
    }

    private void drawBox(AxisAlignedBB axisAlignedBB, Color color) {
        GlStateManager.pushMatrix();
        try {
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.disableTexture2D();
            GlStateManager.disableDepth();
            GlStateManager.depthMask(true);
            GL11.glLineWidth(1.0f);
            GlStateManager.color((float) color.getRed() / 255.0f, (float) color.getGreen() / 255.0f, (float) color.getBlue() / 255.0f, (float) color.getAlpha() / 255.0f);
            Tessellator tessellator = Tessellator.getInstance();
            WorldRenderer worldRenderer = tessellator.getWorldRenderer();
            worldRenderer.begin(3, DefaultVertexFormats.POSITION);
            worldRenderer.pos(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.minZ).endVertex();
            worldRenderer.pos(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.minZ).endVertex();
            worldRenderer.pos(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.maxZ).endVertex();
            worldRenderer.pos(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.maxZ).endVertex();
            worldRenderer.pos(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.minZ).endVertex();
            tessellator.draw();
            worldRenderer.begin(3, DefaultVertexFormats.POSITION);
            worldRenderer.pos(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.minZ).endVertex();
            worldRenderer.pos(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.minZ).endVertex();
            worldRenderer.pos(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.maxZ).endVertex();
            worldRenderer.pos(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.maxZ).endVertex();
            worldRenderer.pos(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.minZ).endVertex();
            tessellator.draw();
            worldRenderer.begin(1, DefaultVertexFormats.POSITION);
            worldRenderer.pos(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.minZ).endVertex();
            worldRenderer.pos(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.minZ).endVertex();
            worldRenderer.pos(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.minZ).endVertex();
            worldRenderer.pos(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.minZ).endVertex();
            worldRenderer.pos(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.maxZ).endVertex();
            worldRenderer.pos(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.maxZ).endVertex();
            worldRenderer.pos(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.maxZ).endVertex();
            worldRenderer.pos(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.maxZ).endVertex();
            tessellator.draw();
        } finally {
            GlStateManager.depthMask(true);
            GlStateManager.enableDepth();
            GlStateManager.enableTexture2D();
            GlStateManager.disableBlend();
            GlStateManager.popMatrix();
        }
    }

    private void beginBadge(double d, double d2, double d3) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(d, d2, d3);
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

    private static void drawRect(double d, double d2, double d3, double d4, int n) {
        double d5;
        if (d < d3) {
            d5 = d;
            d = d3;
            d3 = d5;
        }
        if (d2 < d4) {
            d5 = d2;
            d2 = d4;
            d4 = d5;
        }
        float f = (float) (n >> 24 & 0xFF) / 255.0f;
        float f2 = (float) (n >> 16 & 0xFF) / 255.0f;
        float f3 = (float) (n >> 8 & 0xFF) / 255.0f;
        float f4 = (float) (n & 0xFF) / 255.0f;
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldRenderer = tessellator.getWorldRenderer();
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(f2, f3, f4, f);
        worldRenderer.begin(7, DefaultVertexFormats.POSITION);
        worldRenderer.pos(d, d4, 0.0).endVertex();
        worldRenderer.pos(d3, d4, 0.0).endVertex();
        worldRenderer.pos(d3, d2, 0.0).endVertex();
        worldRenderer.pos(d, d2, 0.0).endVertex();
        tessellator.draw();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    private static void border(float f, float f2, float f3, float f4, int n) {
        PlayerESP.drawRect(f, f2, f3, f2 + 0.5f, n);
        PlayerESP.drawRect(f, f2 + 0.5f, f + 0.5f, f4, n);
        PlayerESP.drawRect(f3 - 0.5f, f2 + 0.5f, f3, f4 - 0.5f, n);
        PlayerESP.drawRect(f + 0.5f, f4 - 0.5f, f3, f4, n);
    }

    private void drawCornerBadge(double d, double d2, double d3, int n) {
        this.beginBadge(d, d2, d3);
        try {
            PlayerESP.border(4.0f, -21.0f, 7.0f, -20.5f, n);
            PlayerESP.border(-7.0f, -21.0f, -4.0f, -20.5f, n);
            PlayerESP.border(6.5f, -21.0f, 7.0f, -18.5f, n);
            PlayerESP.border(-7.0f, -21.0f, -6.5f, -18.5f, n);
            PlayerESP.border(-7.0f, 2.0f, -4.0f, 2.5f, n);
            PlayerESP.border(4.0f, 2.0f, 7.0f, 2.5f, n);
            PlayerESP.border(-7.0f, -0.5f, -6.5f, 2.5f, n);
            PlayerESP.border(6.5f, -0.5f, 7.0f, 2.5f, n);
            int n2 = -16777216;
            PlayerESP.drawRect(7.0, -21.0, 7.3f, -18.5, n2);
            PlayerESP.drawRect(6.2f, -20.5, 6.5, -18.5, n2);
            PlayerESP.drawRect(6.5, -18.8f, 7.0, -18.5, n2);
            PlayerESP.drawRect(-7.3f, -21.0, -7.0, -18.5, n2);
            PlayerESP.drawRect(-6.5, -20.5, -6.2f, -18.5, n2);
            PlayerESP.drawRect(-7.0, -18.8f, -6.5, -18.5, n2);
            PlayerESP.drawRect(4.0, -21.3f, 7.3f, -21.0, n2);
            PlayerESP.drawRect(4.0, -20.5, 6.3f, -20.2f, n2);
            PlayerESP.drawRect(4.0, -21.3f, 3.8f, -20.2f, n2);
            PlayerESP.drawRect(-7.3f, -21.3f, -4.0, -21.0, n2);
            PlayerESP.drawRect(-6.5, -20.5, -4.0, -20.2f, n2);
            PlayerESP.drawRect(-4.0, -21.3f, -3.8f, -20.2f, n2);
            PlayerESP.drawRect(-7.0, 2.5, -4.0, 2.8f, n2);
            PlayerESP.drawRect(-6.5, 1.8f, -4.0, 2.1f, n2);
            PlayerESP.drawRect(-7.0, -0.5, -6.5, -0.2f, n2);
            PlayerESP.drawRect(4.0, 2.5, 7.0, 2.8f, n2);
            PlayerESP.drawRect(4.0, 1.8f, 6.5, 2.1f, n2);
            PlayerESP.drawRect(4.0, 1.8f, 3.8f, 2.8f, n2);
            PlayerESP.drawRect(-7.3f, -0.5, -7.0, 2.8f, n2);
            PlayerESP.drawRect(-6.5, -0.5, -6.2f, 2.1f, n2);
            PlayerESP.drawRect(-3.8f, 1.8f, -4.0, 2.8f, n2);
            PlayerESP.drawRect(7.0, -0.5, 7.3f, 2.8f, n2);
            PlayerESP.drawRect(6.5, -0.5, 6.2f, 2.1f, n2);
            PlayerESP.drawRect(6.5, -0.5, 7.0, -0.2f, n2);
        } finally {
            this.endBadge();
        }
    }

    private void drawOtherBadge(double d, double d2, double d3, int n, int n2) {
        this.beginBadge(d, d2, d3);
        try {
            PlayerESP.border(-7.0f, -21.0f, 7.0f, -20.5f, n);
            PlayerESP.border(-7.0f, 2.0f, 7.0f, 2.5f, n);
            PlayerESP.border(6.5f, -0.5f, 7.0f, 2.5f, n);
            PlayerESP.border(6.5f, -21.0f, 7.0f, -18.5f, n);
            PlayerESP.drawRect(-8.5, -17.0, -8.0, -2.0, n2);
        } finally {
            this.endBadge();
        }
    }

    static {
        compositeGlState = "not sampled";
    }

    public static enum Mode {
        Minecraft("Minecraft"),
        Outline("Outline"),
        Corner("Corner"),
        Box("Box"),
        Other("Other");

        private String enumName;

        private Mode(String string2) {
            this.enumName = string2;
        }

        public String enumName() {
            return this.enumName;
        }
    }
}
