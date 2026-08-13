package Magic.mod.s.render;

import Magic.ink.event.s.EventRender3D;
import Magic.mod.Category;
import Magic.mod.Module;
import Magic.mod.value.values.BoolValue;
import Magic.mod.value.values.ColorAlphaValue;
import Magic.mod.value.values.EnumValue;
import Magic.mod.value.values.NumberValue;
import Magic.utils.Friend.FriendManager;
import Magic.utils.player.ClientUtils;
import Magic.utils.render.StencilUtil;
import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.layers.LayerArmorBase;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ResourceLocation;
import optifine.Config;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import pisi.unitedmeows.eventapi.event.listener.Listener;

public class PlayerESP
extends Module {
    private final EnumValue<Mode> mode = new EnumValue<Mode>("Mode", (Module)this, Mode.class, "ESP render style.");
    private final ColorAlphaValue color = new ColorAlphaValue("Color", (Module)this, new Color(255, 255, 255, 255), "ESP color and transparency.");
    private final BoolValue throughArmor = new BoolValue("ThroughArmor", this, true, "Show the outline through armor.", () -> this.mode.getValue() == Mode.Outline || this.mode.getValue() == Mode.Minecraft);
    private final BoolValue glow = new BoolValue("Glow", this, false, "Soft glow bleeding outwards from the outline.", () -> this.mode.getValue() == Mode.Outline);
    private final NumberValue<Float> glowLength = new NumberValue<Float>("GlowLength", this, Float.valueOf(10.0f), Float.valueOf(2.0f), Float.valueOf(40.0f), Float.valueOf(1.0f), "How far the glow reaches past the outline, in pixels.", () -> this.mode.getValue() == Mode.Outline && this.glow.getValue().booleanValue());

    private static final Color HURT_COLOR = new Color(255, 50, 10, 255);
    private static final Color FRIEND_COLOR = new Color(255, 255, 255, 255);

    /** No batched outline pass is running. */
    private static final int PASS_NONE = 0;
    /** Filling the stencil with the merged silhouette of every target. */
    private static final int PASS_MASK = 1;
    /** Drawing the outline itself, clipped to everything outside the silhouette. */
    private static final int PASS_OUTLINE = 2;
    /** Drawing the silhouette into the offscreen buffer the glow is blurred from. */
    private static final int PASS_GLOW = 3;

    /** Stencil value marking "this pixel is covered by a target", i.e. no outline / glow here. */
    private static final int STENCIL_INSIDE = 8;
    /** Width of the outline in pixels. Half of the line lands inside the model and is masked away. */
    private static final float OUTLINE_LINE_WIDTH = 2.0f;
    /** The blur takes 12 samples to each side; the count is baked into the shader source below. */

    private static final String GLOW_VERTEX_SHADER = "#version 120\nvarying vec2 uv;\nvoid main(){gl_Position=gl_ModelViewProjectionMatrix*gl_Vertex;uv=gl_MultiTexCoord0.xy;}\n";
    private static final String GLOW_FRAGMENT_SHADER = "#version 120\nuniform sampler2D tex;\nuniform vec2 dir;\nuniform float radius;\nuniform float power;\nuniform float gain;\nuniform float shape;\nvarying vec2 uv;\nvoid main(){vec4 sum=vec4(0.0);float wsum=0.0;for(int i=-12;i<=12;i++){float fi=float(i)/12.0;float w=exp(-2.2*fi*fi);sum+=texture2D(tex,uv+dir*(fi*radius))*w;wsum+=w;}sum/=wsum;if(shape<0.5){gl_FragColor=sum;return;}float a=clamp(pow(clamp(sum.a,0.0,1.0),power)*gain,0.0,1.0);vec3 rgb=sum.a>0.0001?sum.rgb/sum.a:vec3(1.0);gl_FragColor=vec4(rgb,a);}\n";

    private final Listener<EventRender3D> onRender3D = new Listener<EventRender3D>(eventRender3D -> {
        if (this.mc.theWorld == null || this.mc.thePlayer == null) {
            return;
        }
        Mode mode = (Mode)((Object)((Object)this.mode.getValue()));
        float f = eventRender3D.partialTicks();
        if (mode == Mode.Outline) {
            this.renderOutlineMode(f, eventRender3D.camera());
            return;
        }
        if (mode != Mode.Corner && mode != Mode.Box) {
            return;
        }
        try {
            for (EntityPlayer entityPlayer : ClientUtils.getPlayers()) {
                if (entityPlayer == this.mc.thePlayer || entityPlayer.isDead || !entityPlayer.isEntityAlive()) continue;
                try {
                    if (mode == Mode.Corner) {
                        this.renderCorner(entityPlayer, f);
                        continue;
                    }
                    this.renderBox(entityPlayer);
                }
                catch (Exception exception) {}
            }
        }
        finally {
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        }
    });

    private Mode reportedMode;
    private Framebuffer glowBufferA;
    private Framebuffer glowBufferB;
    private int glowProgram;
    private int glowTexLoc = -1;
    private int glowDirLoc = -1;
    private int glowRadiusLoc = -1;
    private int glowPowerLoc = -1;
    private int glowGainLoc = -1;
    private int glowShapeLoc = -1;
    private boolean glowFailed;
    private static volatile PlayerESP instance;
    private static volatile String compositeGlState;
    private static volatile boolean sawFramebuffer;
    private static volatile boolean sawShader;
    private static volatile boolean hookRan;
    private static volatile boolean armorMaskWritten;
    private static volatile int batchPass = 0;

    public PlayerESP() {
        super("PlayerESP", 0, Category.Render, "Highlights players (Minecraft/Outline/Corner/Box).");
        instance = this;
    }

    @Override
    public void onEnable() {
        super.onEnable();
        this.reportedMode = (Mode)((Object)this.mode.getValue());
        this.warnIfVanillaOutlineBlocked();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        this.releaseGlowResources();
    }

    @Override
    public void onSuffixChange() {
        this.setSuffix(((Mode)((Object)this.mode.getValue())).enumName());
        super.onSuffixChange();
        if (this.mode.getValue() != this.reportedMode) {
            this.reportedMode = (Mode)((Object)this.mode.getValue());
            this.warnIfVanillaOutlineBlocked();
        }
    }

    private Color espColor(EntityLivingBase entityLivingBase) {
        Color color = this.color.getValue();
        if (entityLivingBase != null && entityLivingBase.hurtTime > 0) {
            return PlayerESP.withAlpha(HURT_COLOR, color.getAlpha());
        }
        if (entityLivingBase instanceof EntityPlayer && FriendManager.isFriend(((EntityPlayer)entityLivingBase).getName())) {
            return PlayerESP.withAlpha(FRIEND_COLOR, color.getAlpha());
        }
        return color;
    }

    private static Color withAlpha(Color color, int n) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), n);
    }

    /**
     * The module that owns the outline currently being drawn: the Minecraft mode hooks into the vanilla
     * entity outline shader, the Outline mode only owns the entity render while its own batch is running.
     */
    private static PlayerESP outlineOwner() {
        PlayerESP playerESP = PlayerESP.active(Mode.Minecraft);
        if (playerESP != null) {
            return playerESP;
        }
        return batchPass != PASS_NONE ? PlayerESP.active(Mode.Outline) : null;
    }

    public static int outlineColorOverride(EntityLivingBase entityLivingBase) {
        PlayerESP playerESP = PlayerESP.outlineOwner();
        if (playerESP == null) {
            return 0;
        }
        try {
            Color color = playerESP.espColor(entityLivingBase);
            if (batchPass == PASS_MASK || batchPass == PASS_GLOW) {
                return PlayerESP.withAlpha(color, 255).getRGB();
            }
            return color.getRGB();
        }
        catch (Throwable throwable) {
            return 0;
        }
    }

    public static void outlineTeamColor(float f, float f2, float f3, float f4, EntityLivingBase entityLivingBase) {
        int n = PlayerESP.outlineColorOverride(entityLivingBase);
        if (n != 0) {
            f = (float)(n >> 16 & 0xFF) / 255.0f;
            f2 = (float)(n >> 8 & 0xFF) / 255.0f;
            f3 = (float)(n & 0xFF) / 255.0f;
            f4 = (float)(n >> 24 & 0xFF) / 255.0f;
        }
        GlStateManager.color(f, f2, f3, f4);
    }

    /**
     * Keeps the skin texture bound while the silhouette is drawn, with a texture environment that takes the
     * colour from glColor and only the alpha from the skin. Transparent pixels of the second skin layer are
     * then dropped by the alpha test, so the silhouette hugs the first layer where the second one is empty
     * and steps out over the second layer where it is painted.
     *
     * @return true when the caller must leave texturing enabled.
     */
    public static boolean outlineSkinAlphaBegin(EntityLivingBase entityLivingBase) {
        if (PlayerESP.outlineOwner() == null) {
            return false;
        }
        try {
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GlStateManager.enableTexture2D();
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL13.GL_COMBINE);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_COMBINE_RGB, GL11.GL_REPLACE);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE0_RGB, GL13.GL_PRIMARY_COLOR);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND0_RGB, GL11.GL_SRC_COLOR);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_COMBINE_ALPHA, GL11.GL_MODULATE);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE0_ALPHA, GL11.GL_TEXTURE);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND0_ALPHA, GL11.GL_SRC_ALPHA);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE1_ALPHA, GL13.GL_PRIMARY_COLOR);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND1_ALPHA, GL11.GL_SRC_ALPHA);
            GlStateManager.enableAlpha();
            GlStateManager.alphaFunc(516, 0.02f);
            return true;
        }
        catch (Throwable throwable) {
            return false;
        }
    }

    public static void outlineSkinAlphaEnd() {
        if (PlayerESP.outlineOwner() == null) {
            return;
        }
        try {
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
            GlStateManager.alphaFunc(516, 0.1f);
        }
        catch (Throwable throwable) {
            // empty catch block
        }
    }

    /** Armour has to join the silhouette while ThroughArmor is off, so the outline stays behind it. */
    public static boolean outlineArmorMaskWanted(EntityLivingBase entityLivingBase) {
        if (batchPass != PASS_MASK || entityLivingBase == null) {
            return false;
        }
        PlayerESP playerESP = PlayerESP.active(Mode.Outline);
        if (playerESP == null || playerESP.throughArmor.getValue().booleanValue()) {
            return false;
        }
        return !(entityLivingBase instanceof EntityPlayer) || !((EntityPlayer)entityLivingBase).isSpectator();
    }

    public static void renderArmorMaskLayers(List<?> list, EntityLivingBase entityLivingBase, float f, float f2, float f3, float f4, float f5, float f6, float f7) {
        if (list == null || entityLivingBase == null) {
            return;
        }
        for (Object obj : list) {
            if (!(obj instanceof LayerArmorBase)) continue;
            try {
                ((LayerArmorBase)obj).doRenderLayer(entityLivingBase, f, f2, f3, f4, f5, f6, f7);
            }
            catch (Throwable throwable) {}
        }
    }

    private static PlayerESP active(Mode mode) {
        PlayerESP playerESP = instance;
        try {
            if (playerESP != null && playerESP.isEnabled() && playerESP.mode.getValue() == mode) {
                return playerESP;
            }
        }
        catch (Throwable throwable) {
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
        }
        catch (Throwable throwable) {
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
                }
                catch (Throwable throwable) {
                    // empty catch block
                }
            }
            String string = PlayerESP.vanillaOutlineBlocker();
            if (!OpenGlHelper.shadersSupported) {
                ClientUtils.debug((Object)"PlayerESP: Minecraft mode needs shader support, which this driver reports as unavailable. Use Outline instead.");
            } else if (string != null) {
                ClientUtils.debug((Object)("PlayerESP: Minecraft mode is unavailable while OptiFine " + string + " is on — turn it off, or use Outline instead."));
            } else if (!OpenGlHelper.isFramebufferEnabled()) {
                ClientUtils.debug((Object)"PlayerESP: Minecraft mode needs framebuffers, which are off right now (Video Settings -> FBO). Use Outline instead.");
            } else if (!(!hookRan || sawFramebuffer && sawShader)) {
                ClientUtils.debug((Object)"PlayerESP: Minecraft mode could not build the entity outline shader (check the log for \"Failed to load shader\"). Use Outline instead.");
            } else {
                ClientUtils.debug((Object)"PlayerESP: Minecraft mode active.");
            }
            ClientUtils.debug((Object)("PlayerESP state: fboEnabled=" + OpenGlHelper.isFramebufferEnabled() + " chain=" + PlayerESP.loadedOutlineChain() + " outlineShader=" + sawShader + " outlineFbo=" + sawFramebuffer + " hookRan=" + hookRan));
            ClientUtils.debug((Object)("PlayerESP composite: " + compositeGlState + " | mainFbo=" + (minecraft.getFramebuffer() == null ? "null" : String.valueOf(minecraft.getFramebuffer().framebufferObject)) + " display=" + minecraft.displayWidth + "x" + minecraft.displayHeight));
        }
        catch (Throwable throwable) {
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
        }
        catch (Throwable throwable) {
            compositeGlState = "sample failed: " + throwable;
        }
    }

    private static String loadedOutlineChain() {
        InputStream inputStream = null;
        try {
            int n;
            int n2;
            inputStream = Minecraft.getMinecraft().getResourceManager().getResource(new ResourceLocation("shaders/post/magic_esp_outline.json")).getInputStream();
            byte[] byArray = new byte[8192];
            for (n = 0; n < byArray.length && (n2 = inputStream.read(byArray, n, byArray.length - n)) > 0; n += n2) {
            }
            String string = new String(byArray, 0, n, "UTF-8");
            return string.contains("magic_esp_blur") ? "ours" : "NOT-OURS";
        }
        catch (Throwable throwable) {
            return "unreadable(" + throwable.getClass().getSimpleName() + ")";
        }
        finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            }
            catch (IOException iOException) {}
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
        }
        catch (Throwable throwable) {
            return false;
        }
        return PlayerESP.vanillaOutlineBlocker() == null;
    }

    public static boolean armorMasksVanillaOutline(EntityLivingBase entityLivingBase) {
        return PlayerESP.armorMaskWanted(entityLivingBase) && PlayerESP.vanillaOutlineHook(sawFramebuffer, sawShader);
    }

    static boolean armorMaskWanted(EntityLivingBase entityLivingBase) {
        PlayerESP playerESP = PlayerESP.active(Mode.Minecraft);
        if (playerESP == null || playerESP.throughArmor.getValue().booleanValue()) {
            return false;
        }
        return !(entityLivingBase instanceof EntityPlayer) || !((EntityPlayer)entityLivingBase).isSpectator();
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
                    ((LayerArmorBase)obj).doRenderLayer(entityLivingBase, f, f2, f3, f4, f5, f6, f7);
                }
                catch (Throwable throwable) {}
            }
            armorMaskWritten = true;
        }
        catch (Throwable throwable) {
        }
        finally {
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
        }
        catch (Throwable throwable) {
            // empty catch block
        }
    }

    /*
     * ------------------------------------------------------------------------------------------------
     * Outline mode
     *
     * Every target is drawn in one batch instead of once per entity, so the silhouettes of players that
     * overlap on screen share a single stencil and the outline follows the border of the whole group
     * rather than tracing each player separately.
     * ------------------------------------------------------------------------------------------------
     */
    private void renderOutlineMode(float partialTicks, Frustum frustum) {
        List<EntityPlayer> list = this.collectTargets(frustum);
        if (list.isEmpty()) {
            return;
        }
        Minecraft minecraft = this.mc;
        RenderManager renderManager = minecraft.getRenderManager();
        if (renderManager == null) {
            return;
        }
        try {
            StencilUtil.checkSetupFBO(minecraft.getFramebuffer());
        }
        catch (Throwable throwable) {
            return;
        }
        boolean bl = this.glow.getValue().booleanValue();
        renderManager.setRenderOutlines(true);
        try {
            this.beginEspState();
            GL11.glEnable(2960);
            GL11.glStencilMask(255);
            GL11.glClearStencil(0);
            GL11.glClear(1024);

            // Silhouette of every target, written to the stencil only.
            batchPass = PASS_MASK;
            GL11.glPolygonMode(1032, 6914);
            GL11.glStencilFunc(512, STENCIL_INSIDE, 255);
            GL11.glStencilOp(7681, 7680, 7680);
            GlStateManager.colorMask(false, false, false, false);
            this.renderTargets(renderManager, list, partialTicks);
            GlStateManager.colorMask(true, true, true, true);

            if (bl) {
                this.renderGlowLayer(renderManager, list, partialTicks);
            }

            // Outline: a thick wireframe clipped to the pixels the silhouette does not cover, so only the
            // half of the line that sits outside the merged shape survives.
            batchPass = PASS_OUTLINE;
            this.beginEspState();
            GL11.glEnable(2960);
            GL11.glStencilMask(255);
            GL11.glStencilFunc(514, 0, 255);
            GL11.glStencilOp(7680, 7680, 7682);
            GL11.glPolygonMode(1032, 6913);
            GL11.glLineWidth(OUTLINE_LINE_WIDTH);
            GL11.glEnable(2848);
            this.renderTargets(renderManager, list, partialTicks);
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        finally {
            batchPass = PASS_NONE;
            renderManager.setRenderOutlines(false);
            this.endEspState();
        }
    }

    private List<EntityPlayer> collectTargets(Frustum frustum) {
        ArrayList<EntityPlayer> arrayList = new ArrayList<EntityPlayer>();
        try {
            for (EntityPlayer entityPlayer : ClientUtils.getPlayers()) {
                if (entityPlayer == null || entityPlayer == this.mc.thePlayer || entityPlayer.isDead || !entityPlayer.isEntityAlive() || entityPlayer.isInvisible() || entityPlayer.isSpectator()) continue;
                if (frustum != null) {
                    try {
                        AxisAlignedBB axisAlignedBB = entityPlayer.getEntityBoundingBox().expand(0.6, 0.6, 0.6);
                        if (!entityPlayer.ignoreFrustumCheck && !frustum.isBoundingBoxInFrustum(axisAlignedBB)) continue;
                    }
                    catch (Throwable throwable) {
                        // empty catch block
                    }
                }
                arrayList.add(entityPlayer);
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        return arrayList;
    }

    private void renderTargets(RenderManager renderManager, List<EntityPlayer> list, float f) {
        for (EntityPlayer entityPlayer : list) {
            try {
                renderManager.renderEntitySimple(entityPlayer, f);
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }
    }

    /** State shared by every pass of the batch: no depth, no lighting, blended, alpha tested. */
    private void beginEspState() {
        GlStateManager.disableLighting();
        GlStateManager.disableFog();
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(516, 0.02f);
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private void endEspState() {
        try {
            GL11.glStencilMask(255);
            GL11.glStencilFunc(519, 0, 255);
            GL11.glStencilOp(7680, 7680, 7680);
            GL11.glClearStencil(0);
            GL11.glClear(1024);
            GL11.glDisable(2960);
            GL11.glPolygonMode(1032, 6914);
            GL11.glLineWidth(1.0f);
            GL11.glDisable(2848);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        GlStateManager.colorMask(true, true, true, true);
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.depthFunc(515);
        GlStateManager.disableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(516, 0.1f);
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.viewport(0, 0, this.mc.displayWidth, this.mc.displayHeight);
        RenderHelper.disableStandardItemLighting();
    }

    /*
     * ------------------------------------------------------------------------------------------------
     * Glow
     *
     * The merged silhouette is drawn once into a half resolution buffer, blurred with a separable
     * gaussian and blended back over the world. The stencil written by the mask pass keeps it strictly
     * outside the players - nothing is shaded underneath the models.
     * ------------------------------------------------------------------------------------------------
     */
    private void renderGlowLayer(RenderManager renderManager, List<EntityPlayer> list, float partialTicks) {
        // Read the target the world is being drawn into first: building a framebuffer below unbinds it.
        int n3 = GL11.glGetInteger(36006);
        if (!this.ensureGlowResources()) {
            return;
        }
        // Creating a framebuffer also turns the depth test back on, so restore what the batch needs.
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        int n = this.mc.displayWidth;
        int n2 = this.mc.displayHeight;
        try {
            int n4 = this.glowBufferA.framebufferWidth;
            int n5 = this.glowBufferA.framebufferHeight;
            float f = Math.max(1.0f, ((Float)this.glowLength.getValue()).floatValue() * 0.5f);

            batchPass = PASS_GLOW;
            GL11.glDisable(2960);
            GlStateManager.disableBlend();
            this.glowBufferA.framebufferClear();
            this.glowBufferA.bindFramebuffer(true);
            GL11.glPolygonMode(1032, 6914);
            this.renderTargets(renderManager, list, partialTicks);

            GL20.glUseProgram(this.glowProgram);
            GL20.glUniform1i(this.glowTexLoc, 0);
            GL20.glUniform1f(this.glowRadiusLoc, f);
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GlStateManager.disableAlpha();
            GlStateManager.disableBlend();
            GlStateManager.enableTexture2D();
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);

            // horizontal, keeps the colour premultiplied for the second pass
            this.glowBufferB.bindFramebuffer(true);
            GL20.glUniform2f(this.glowDirLoc, 1.0f / (float)n4, 0.0f);
            GL20.glUniform1f(this.glowShapeLoc, 0.0f);
            this.glowBufferA.bindFramebufferTexture();
            PlayerESP.drawTexturedQuad(n4, n5);

            // vertical, unpremultiplies and shapes the falloff
            this.glowBufferA.bindFramebuffer(true);
            GL20.glUniform2f(this.glowDirLoc, 0.0f, 1.0f / (float)n5);
            GL20.glUniform1f(this.glowShapeLoc, 1.0f);
            GL20.glUniform1f(this.glowPowerLoc, 1.35f);
            GL20.glUniform1f(this.glowGainLoc, 1.45f);
            this.glowBufferB.bindFramebufferTexture();
            PlayerESP.drawTexturedQuad(n4, n5);
            GL20.glUseProgram(0);

            // blend it over the world, everywhere the silhouette did not claim
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, n3);
            GlStateManager.viewport(0, 0, n, n2);
            GL11.glEnable(2960);
            GL11.glStencilMask(0);
            GL11.glStencilFunc(514, 0, 255);
            GL11.glStencilOp(7680, 7680, 7680);
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 1);
            GlStateManager.disableAlpha();
            GlStateManager.color(1.0f, 1.0f, 1.0f, (float)this.color.getValue().getAlpha() / 255.0f);
            this.glowBufferA.bindFramebufferTexture();
            PlayerESP.drawTexturedQuad(n, n2);
            this.glowBufferA.unbindFramebufferTexture();
        }
        catch (Throwable throwable) {
            this.glowFailed = true;
        }
        finally {
            batchPass = PASS_MASK;
            try {
                GL20.glUseProgram(0);
                OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, n3);
                GlStateManager.viewport(0, 0, n, n2);
                GL11.glStencilMask(255);
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }
    }

    private static void drawTexturedQuad(double d, double d2) {
        GlStateManager.matrixMode(5889);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        GlStateManager.ortho(0.0, d, d2, 0.0, 1000.0, 3000.0);
        GlStateManager.matrixMode(5888);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        GlStateManager.translate(0.0f, 0.0f, -2000.0f);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldRenderer = tessellator.getWorldRenderer();
        worldRenderer.begin(7, DefaultVertexFormats.POSITION_TEX);
        worldRenderer.pos(0.0, d2, 0.0).tex(0.0, 0.0).endVertex();
        worldRenderer.pos(d, d2, 0.0).tex(1.0, 0.0).endVertex();
        worldRenderer.pos(d, 0.0, 0.0).tex(1.0, 1.0).endVertex();
        worldRenderer.pos(0.0, 0.0, 0.0).tex(0.0, 1.0).endVertex();
        tessellator.draw();
        GlStateManager.matrixMode(5889);
        GlStateManager.popMatrix();
        GlStateManager.matrixMode(5888);
        GlStateManager.popMatrix();
    }

    private boolean ensureGlowResources() {
        if (this.glowFailed) {
            return false;
        }
        try {
            if (!OpenGlHelper.isFramebufferEnabled() || !OpenGlHelper.shadersSupported || Config.isShaders()) {
                this.glowFailed = true;
                ClientUtils.debug((Object)"PlayerESP: Glow needs framebuffers and shader support, they are unavailable right now.");
                return false;
            }
            int n = Math.max(1, this.mc.displayWidth / 2);
            int n2 = Math.max(1, this.mc.displayHeight / 2);
            if (this.glowBufferA == null || this.glowBufferB == null || this.glowBufferA.framebufferWidth != n || this.glowBufferA.framebufferHeight != n2) {
                this.deleteGlowBuffers();
                this.glowBufferA = PlayerESP.createGlowBuffer(n, n2);
                this.glowBufferB = PlayerESP.createGlowBuffer(n, n2);
            }
            if (this.glowProgram == 0) {
                this.glowProgram = PlayerESP.compileProgram(GLOW_VERTEX_SHADER, GLOW_FRAGMENT_SHADER);
                if (this.glowProgram == 0) {
                    this.glowFailed = true;
                    ClientUtils.debug((Object)"PlayerESP: Glow shader failed to compile, see the log for details.");
                    return false;
                }
                this.glowTexLoc = GL20.glGetUniformLocation(this.glowProgram, "tex");
                this.glowDirLoc = GL20.glGetUniformLocation(this.glowProgram, "dir");
                this.glowRadiusLoc = GL20.glGetUniformLocation(this.glowProgram, "radius");
                this.glowPowerLoc = GL20.glGetUniformLocation(this.glowProgram, "power");
                this.glowGainLoc = GL20.glGetUniformLocation(this.glowProgram, "gain");
                this.glowShapeLoc = GL20.glGetUniformLocation(this.glowProgram, "shape");
            }
            return this.glowBufferA != null && this.glowBufferB != null;
        }
        catch (Throwable throwable) {
            this.glowFailed = true;
            return false;
        }
    }

    private static Framebuffer createGlowBuffer(int n, int n2) {
        Framebuffer framebuffer = new Framebuffer(n, n2, false);
        framebuffer.setFramebufferColor(0.0f, 0.0f, 0.0f, 0.0f);
        framebuffer.setFramebufferFilter(9729);
        return framebuffer;
    }

    private void deleteGlowBuffers() {
        try {
            if (this.glowBufferA != null) {
                this.glowBufferA.deleteFramebuffer();
            }
            if (this.glowBufferB != null) {
                this.glowBufferB.deleteFramebuffer();
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        this.glowBufferA = null;
        this.glowBufferB = null;
    }

    private void releaseGlowResources() {
        this.deleteGlowBuffers();
        try {
            if (this.glowProgram != 0) {
                GL20.glDeleteProgram(this.glowProgram);
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        this.glowProgram = 0;
        this.glowFailed = false;
    }

    private static int compileProgram(String string, String string2) {
        int n = GL20.glCreateShader(35633);
        GL20.glShaderSource(n, string);
        GL20.glCompileShader(n);
        if (GL20.glGetShaderi(n, 35713) == 0) {
            System.err.println("[PlayerESP] Glow vertex shader: " + GL20.glGetShaderInfoLog(n, 4096));
            GL20.glDeleteShader(n);
            return 0;
        }
        int n2 = GL20.glCreateShader(35632);
        GL20.glShaderSource(n2, string2);
        GL20.glCompileShader(n2);
        if (GL20.glGetShaderi(n2, 35713) == 0) {
            System.err.println("[PlayerESP] Glow fragment shader: " + GL20.glGetShaderInfoLog(n2, 4096));
            GL20.glDeleteShader(n);
            GL20.glDeleteShader(n2);
            return 0;
        }
        int n3 = GL20.glCreateProgram();
        GL20.glAttachShader(n3, n);
        GL20.glAttachShader(n3, n2);
        GL20.glLinkProgram(n3);
        boolean bl = GL20.glGetProgrami(n3, 35714) != 0;
        GL20.glDeleteShader(n);
        GL20.glDeleteShader(n2);
        if (!bl) {
            System.err.println("[PlayerESP] Glow program link: " + GL20.glGetProgramInfoLog(n3, 4096));
            GL20.glDeleteProgram(n3);
            return 0;
        }
        return n3;
    }

    /*
     * ------------------------------------------------------------------------------------------------
     * Corner / Box
     * ------------------------------------------------------------------------------------------------
     */
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

    private double[] interpolatedRenderPos(EntityPlayer entityPlayer, float f) {
        double d = entityPlayer.lastTickPosX + (entityPlayer.posX - entityPlayer.lastTickPosX) * (double)f - this.mc.getRenderManager().renderPosX;
        double d2 = entityPlayer.lastTickPosY + (entityPlayer.posY - entityPlayer.lastTickPosY) * (double)f - this.mc.getRenderManager().renderPosY;
        double d3 = entityPlayer.lastTickPosZ + (entityPlayer.posZ - entityPlayer.lastTickPosZ) * (double)f - this.mc.getRenderManager().renderPosZ;
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
            GlStateManager.color((float)color.getRed() / 255.0f, (float)color.getGreen() / 255.0f, (float)color.getBlue() / 255.0f, (float)color.getAlpha() / 255.0f);
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
        }
        finally {
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
        float f = (float)(n >> 24 & 0xFF) / 255.0f;
        float f2 = (float)(n >> 16 & 0xFF) / 255.0f;
        float f3 = (float)(n >> 8 & 0xFF) / 255.0f;
        float f4 = (float)(n & 0xFF) / 255.0f;
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
        }
        finally {
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
        Box("Box");

        private String enumName;

        private Mode(String string2) {
            this.enumName = string2;
        }

        public String enumName() {
            return this.enumName;
        }
    }
}
