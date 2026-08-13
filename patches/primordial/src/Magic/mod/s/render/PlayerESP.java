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
import org.lwjgl.opengl.GL20;
import pisi.unitedmeows.eventapi.event.listener.Listener;

public class PlayerESP
extends Module {
    private final EnumValue<Mode> mode = new EnumValue<Mode>("Mode", (Module)this, Mode.class, "ESP render style.");
    private final ColorAlphaValue color = new ColorAlphaValue("Color", (Module)this, new Color(255, 255, 255, 255), "ESP color and transparency.");
    private final BoolValue throughArmor = new BoolValue("ThroughArmor", this, true, "Outline the armor as well, instead of the bare player model.", () -> this.mode.getValue() == Mode.Outline || this.mode.getValue() == Mode.Minecraft);
    private final BoolValue glow = new BoolValue("Glow", this, false, "Soft glow bleeding outwards from the outline.", () -> this.mode.getValue() == Mode.Outline);
    private final NumberValue<Float> glowLength = new NumberValue<Float>("GlowLength", this, Float.valueOf(4.0f), Float.valueOf(0.0f), Float.valueOf(20.0f), Float.valueOf(0.05f), "Glow size and strength around the outline.", () -> this.mode.getValue() == Mode.Outline && this.glow.getValue().booleanValue());

    private static final Color HURT_COLOR = new Color(255, 50, 10, 255);
    private static final Color FRIEND_COLOR = new Color(255, 255, 255, 255);

    /** Outline thickness in pixels, measured outwards from the silhouette. */
    private static final float OUTLINE_WIDTH = 2.0f;
    /** Pixels of glow per unit of the GlowLength slider. */
    private static final float GLOW_PIXELS_PER_UNIT = 6.0f;
    /** Alpha below this counts as "nothing painted here" for both the alpha test and the shaders. */
    private static final float ALPHA_CUTOFF = 0.02f;

    /**
     * Draws a target as a flat stamp of one colour: the skin decides only what is kept, the alpha test
     * lives in the shader so the second layer disappears exactly where the game itself draws nothing.
     * Every fragment then writes the same colour and the same alpha, so no part of the model can ever
     * become an edge of its own - only the border of the whole shape can.
     */
    private static final String SILHOUETTE_VERTEX_SHADER = "#version 120\nvoid main(){gl_Position=gl_ModelViewProjectionMatrix*gl_Vertex;gl_TexCoord[0]=gl_MultiTexCoord0;}\n";
    private static final String SILHOUETTE_FRAGMENT_SHADER = "#version 120\nuniform sampler2D tex;\nuniform vec4 col;\nvoid main(){if(texture2D(tex,gl_TexCoord[0].xy).a<0.1)discard;gl_FragColor=col;}\n";

    private static final String QUAD_VERTEX_SHADER = "#version 120\nvarying vec2 uv;\nvoid main(){gl_Position=gl_ModelViewProjectionMatrix*gl_Vertex;uv=gl_MultiTexCoord0.xy;}\n";

    /**
     * Separable gaussian. The horizontal pass reads the full resolution silhouette and averages two rows
     * while it writes into the half resolution buffer, so thin players do not fall between the rows.
     */
    private static final String BLUR_FRAGMENT_SHADER = "#version 120\nuniform sampler2D tex;\nuniform vec2 dir;\nuniform vec2 extra;\nuniform float radius;\nuniform float gain;\nuniform float shape;\nvarying vec2 uv;\nvoid main(){vec4 sum=vec4(0.0);float wsum=0.0;for(int i=-16;i<=16;i++){float fi=float(i)/16.0;float w=exp(-2.2*fi*fi);vec2 p=uv+dir*(fi*radius);sum+=(texture2D(tex,p)+texture2D(tex,p+extra))*(0.5*w);wsum+=w;}sum/=wsum;if(shape<0.5){gl_FragColor=sum;return;}float a=clamp(sum.a*gain,0.0,1.0);vec3 rgb=sum.a>0.0001?sum.rgb/sum.a:vec3(1.0);gl_FragColor=vec4(rgb,a);}\n";

    /**
     * Turns the merged silhouette into the outline: everything the silhouette covers is dropped, the ring
     * of pixels next to it becomes the line, and the blurred copy fills the rest with the glow. Because the
     * line comes from the silhouette and not from the polygon edges, players that overlap on screen share
     * one contour and the second skin layer cannot produce a line of its own.
     */
    private static final String OUTLINE_FRAGMENT_SHADER = "#version 120\nuniform sampler2D mask;\nuniform sampler2D glowTex;\nuniform vec2 texel;\nuniform float width;\nuniform float espAlpha;\nuniform float glowOn;\nvarying vec2 uv;\nvoid main(){if(texture2D(mask,uv).a>0.02)discard;float best=1000.0;vec3 col=vec3(1.0);for(int i=0;i<12;i++){float ang=0.5235988*float(i);vec2 d=vec2(cos(ang),sin(ang));for(int k=1;k<=2;k++){float r=width*float(k)*0.5;vec4 s=texture2D(mask,uv+d*(r*texel));if(s.a>0.02&&r<best){best=r;col=s.rgb;}}}float outline=best<999.0?clamp(width+0.5-best,0.0,1.0):0.0;vec4 g=glowOn>0.5?texture2D(glowTex,uv):vec4(0.0);float a=max(outline,g.a*(1.0-outline))*espAlpha;if(a<=0.002)discard;gl_FragColor=vec4(outline>0.0?col:g.rgb,a);}\n";

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
    private Framebuffer maskBuffer;
    private Framebuffer glowBufferA;
    private Framebuffer glowBufferB;
    private int blurProgram;
    private int blurTexLoc = -1;
    private int blurDirLoc = -1;
    private int blurExtraLoc = -1;
    private int blurRadiusLoc = -1;
    private int blurGainLoc = -1;
    private int blurShapeLoc = -1;
    private int outlineProgram;
    private int outlineMaskLoc = -1;
    private int outlineGlowLoc = -1;
    private int outlineTexelLoc = -1;
    private int outlineWidthLoc = -1;
    private int outlineAlphaLoc = -1;
    private int outlineGlowOnLoc = -1;
    private int silhouetteProgram;
    private int silhouetteTexLoc = -1;
    private int silhouetteColLoc = -1;
    private boolean silhouetteFailed;
    private boolean resourcesFailed;
    private static volatile PlayerESP instance;
    private static volatile String compositeGlState;
    private static volatile boolean sawFramebuffer;
    private static volatile boolean sawShader;
    private static volatile boolean hookRan;
    private static volatile boolean batchActive;
    private static volatile boolean armorMaskWritten;

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
        this.releaseResources();
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
     * The module that owns the silhouette being drawn: the Minecraft mode hooks into the vanilla entity
     * outline shader, the Outline mode only owns the entity render while its own batch is running.
     */
    private static PlayerESP outlineOwner() {
        PlayerESP playerESP = PlayerESP.active(Mode.Minecraft);
        if (playerESP != null) {
            return playerESP;
        }
        return batchActive ? PlayerESP.active(Mode.Outline) : null;
    }

    public static int outlineColorOverride(EntityLivingBase entityLivingBase) {
        PlayerESP playerESP = PlayerESP.outlineOwner();
        if (playerESP == null) {
            return 0;
        }
        try {
            Color color = playerESP.espColor(entityLivingBase);
            // The Outline mode applies the transparency when it composites, the silhouette stays opaque.
            return batchActive ? PlayerESP.withAlpha(color, 255).getRGB() : color.getRGB();
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
     * Puts the silhouette shader in front of the model while the outline is being built. The colour is
     * flat and the alpha is constant, so armor layers - which set their own colour and switch blending
     * back on for the enchantment glint - cannot repaint or fade any part of the shape.
     *
     * @return true when the caller must leave texturing enabled.
     */
    public static boolean outlineSkinAlphaBegin(EntityLivingBase entityLivingBase) {
        PlayerESP playerESP = PlayerESP.outlineOwner();
        if (playerESP == null || !playerESP.ensureSilhouetteProgram()) {
            return false;
        }
        try {
            int n = PlayerESP.outlineColorOverride(entityLivingBase);
            GL20.glUseProgram(playerESP.silhouetteProgram);
            GL20.glUniform1i(playerESP.silhouetteTexLoc, 0);
            GL20.glUniform4f(playerESP.silhouetteColLoc, (float)(n >> 16 & 0xFF) / 255.0f, (float)(n >> 8 & 0xFF) / 255.0f, (float)(n & 0xFF) / 255.0f, (float)(n >> 24 & 0xFF) / 255.0f);
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GlStateManager.enableTexture2D();
            // The glint blends additively on top of whatever it covers. Leaving the state manager
            // convinced blending is on keeps its enableBlend() from reaching the driver, so every
            // fragment of the silhouette is a plain write.
            GlStateManager.enableBlend();
            GL11.glDisable(3042);
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
            GL20.glUseProgram(0);
            GlStateManager.disableBlend();
        }
        catch (Throwable throwable) {
            // empty catch block
        }
    }

    private boolean ensureSilhouetteProgram() {
        if (this.silhouetteProgram != 0) {
            return true;
        }
        if (this.silhouetteFailed) {
            return false;
        }
        try {
            if (!OpenGlHelper.shadersSupported) {
                this.silhouetteFailed = true;
                return false;
            }
            this.silhouetteProgram = PlayerESP.compileProgram(SILHOUETTE_VERTEX_SHADER, SILHOUETTE_FRAGMENT_SHADER, "silhouette");
            if (this.silhouetteProgram == 0) {
                this.silhouetteFailed = true;
                return false;
            }
            this.silhouetteTexLoc = GL20.glGetUniformLocation(this.silhouetteProgram, "tex");
            this.silhouetteColLoc = GL20.glGetUniformLocation(this.silhouetteProgram, "col");
            return true;
        }
        catch (Throwable throwable) {
            this.silhouetteFailed = true;
            return false;
        }
    }

    /** With ThroughArmor on the armor is part of the outlined shape, so it joins the silhouette. */
    public static boolean outlineArmorLayersWanted(EntityLivingBase entityLivingBase) {
        PlayerESP playerESP = PlayerESP.outlineOwner();
        if (playerESP == null || entityLivingBase == null || !playerESP.throughArmor.getValue().booleanValue()) {
            return false;
        }
        return !(entityLivingBase instanceof EntityPlayer) || !((EntityPlayer)entityLivingBase).isSpectator();
    }

    /**
     * Draws the armor layers into the silhouette. Layers restore neither the depth function nor the blend
     * state after the enchantment glint, so both are put back for the entities rendered afterwards.
     */
    public static void renderOutlineArmorLayers(List<?> list, EntityLivingBase entityLivingBase, float f, float f2, float f3, float f4, float f5, float f6, float f7) {
        if (list == null || entityLivingBase == null) {
            return;
        }
        int n = 515;
        boolean bl = false;
        try {
            n = GL11.glGetInteger(2932);
            bl = GL11.glIsEnabled(3042);
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        try {
            for (Object obj : list) {
                if (!(obj instanceof LayerArmorBase)) continue;
                try {
                    ((LayerArmorBase)obj).doRenderLayer(entityLivingBase, f, f2, f3, f4, f5, f6, f7);
                }
                catch (Throwable throwable) {}
            }
        }
        finally {
            GlStateManager.depthFunc(n);
            if (bl) {
                GlStateManager.enableBlend();
            } else {
                GlStateManager.disableBlend();
            }
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
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

    /** With ThroughArmor off the armor hides the outline, which is what the stencil mask is for. */
    static boolean armorMaskWanted(EntityLivingBase entityLivingBase) {
        PlayerESP playerESP = instance;
        if (playerESP == null || entityLivingBase == null) {
            return false;
        }
        try {
            if (!playerESP.isEnabled() || playerESP.throughArmor.getValue().booleanValue()) {
                return false;
            }
            Mode mode = (Mode)((Object)playerESP.mode.getValue());
            if (mode != Mode.Minecraft && mode != Mode.Outline) {
                return false;
            }
            if (!(entityLivingBase instanceof EntityPlayer) || ((EntityPlayer)entityLivingBase).isSpectator()) {
                return false;
            }
            return mode != Mode.Minecraft || PlayerESP.vanillaOutlineHook(sawFramebuffer, sawShader);
        }
        catch (Throwable throwable) {
            return false;
        }
    }

    public static boolean armorMasksVanillaOutline(EntityLivingBase entityLivingBase) {
        return PlayerESP.armorMaskWanted(entityLivingBase);
    }

    /** Stamps the armor into the stencil while the entity is drawn normally, depth tested and all. */
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

    private static void clearArmorMask() {
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

    public static void afterOutlineComposite() {
        PlayerESP.clearArmorMask();
    }

    /*
     * ------------------------------------------------------------------------------------------------
     * Outline mode
     *
     * Every target is drawn once into an offscreen silhouette, and the line is then derived from that
     * silhouette in screen space. Overlapping players therefore share a single contour, and the outline
     * follows exactly what the alpha test kept - both skin layers - instead of tracing polygon edges.
     * ------------------------------------------------------------------------------------------------
     */
    private void renderOutlineMode(float partialTicks, Frustum frustum) {
        List<EntityPlayer> list = this.collectTargets(frustum);
        if (list.isEmpty()) {
            return;
        }
        RenderManager renderManager = this.mc.getRenderManager();
        if (renderManager == null) {
            return;
        }
        int n = 0;
        try {
            n = GL11.glGetInteger(36006);
        }
        catch (Throwable throwable) {
            return;
        }
        if (!this.ensureResources()) {
            return;
        }
        int n2 = this.mc.displayWidth;
        int n3 = this.mc.displayHeight;
        float f = this.glow.getValue() != false ? Math.max(0.0f, ((Float)this.glowLength.getValue()).floatValue()) * GLOW_PIXELS_PER_UNIT : 0.0f;
        boolean bl = f > 0.5f;
        batchActive = true;
        renderManager.setRenderOutlines(true);
        try {
            this.beginEspState();

            // Merged silhouette of every target, color per player, alpha straight from the skin.
            this.maskBuffer.framebufferClear();
            this.maskBuffer.bindFramebuffer(true);
            this.renderTargets(renderManager, list, partialTicks);

            if (bl) {
                this.blurSilhouette(f);
            }

            // Derive the line (and the glow) from the silhouette and blend it over the world.
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, n);
            GlStateManager.viewport(0, 0, n2, n3);
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 1);
            GlStateManager.disableAlpha();
            GlStateManager.enableTexture2D();
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            boolean bl2 = armorMaskWritten;
            if (bl2) {
                GL11.glEnable(2960);
                GL11.glStencilMask(0);
                GL11.glStencilFunc(517, 1, 255);
                GL11.glStencilOp(7680, 7680, 7680);
            }
            GL20.glUseProgram(this.outlineProgram);
            GL20.glUniform1i(this.outlineMaskLoc, 0);
            GL20.glUniform1i(this.outlineGlowLoc, 1);
            GL20.glUniform2f(this.outlineTexelLoc, 1.0f / (float)this.maskBuffer.framebufferWidth, 1.0f / (float)this.maskBuffer.framebufferHeight);
            GL20.glUniform1f(this.outlineWidthLoc, OUTLINE_WIDTH);
            GL20.glUniform1f(this.outlineAlphaLoc, (float)this.color.getValue().getAlpha() / 255.0f);
            GL20.glUniform1f(this.outlineGlowOnLoc, bl ? 1.0f : 0.0f);
            // The glow rides on the lightmap unit; put back whatever the world had bound there.
            GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
            int n4 = GL11.glGetInteger(32873);
            GlStateManager.bindTexture(bl ? this.glowBufferB.framebufferTexture : this.maskBuffer.framebufferTexture);
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GlStateManager.bindTexture(this.maskBuffer.framebufferTexture);
            PlayerESP.drawTexturedQuad(n2, n3);
            GL20.glUseProgram(0);
            GlStateManager.bindTexture(0);
            GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
            GlStateManager.bindTexture(n4);
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
            if (bl2) {
                PlayerESP.clearArmorMask();
            }
        }
        catch (Throwable throwable) {
            this.resourcesFailed = true;
        }
        finally {
            batchActive = false;
            renderManager.setRenderOutlines(false);
            try {
                GL20.glUseProgram(0);
                OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, n);
            }
            catch (Throwable throwable) {
                // empty catch block
            }
            this.endEspState(n2, n3);
        }
    }

    /** Half resolution separable blur of the silhouette, the source the glow is read from. */
    private void blurSilhouette(float f) {
        int n = this.glowBufferA.framebufferWidth;
        int n2 = this.glowBufferA.framebufferHeight;
        GL20.glUseProgram(this.blurProgram);
        GL20.glUniform1i(this.blurTexLoc, 0);
        GlStateManager.disableAlpha();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);

        // horizontal, full resolution source, premultiplied color kept for the second pass
        this.glowBufferA.bindFramebuffer(true);
        GL20.glUniform2f(this.blurDirLoc, 1.0f / (float)this.maskBuffer.framebufferWidth, 0.0f);
        GL20.glUniform2f(this.blurExtraLoc, 0.0f, 1.0f / (float)this.maskBuffer.framebufferHeight);
        GL20.glUniform1f(this.blurRadiusLoc, f);
        GL20.glUniform1f(this.blurShapeLoc, 0.0f);
        GlStateManager.bindTexture(this.maskBuffer.framebufferTexture);
        PlayerESP.drawTexturedQuad(n, n2);

        // vertical, half resolution source, unpremultiplies and lifts the falloff
        this.glowBufferB.bindFramebuffer(true);
        GL20.glUniform2f(this.blurDirLoc, 0.0f, 1.0f / (float)n2);
        GL20.glUniform2f(this.blurExtraLoc, 0.0f, 0.0f);
        GL20.glUniform1f(this.blurRadiusLoc, f * 0.5f);
        GL20.glUniform1f(this.blurGainLoc, 1.7f);
        GL20.glUniform1f(this.blurShapeLoc, 1.0f);
        GlStateManager.bindTexture(this.glowBufferA.framebufferTexture);
        PlayerESP.drawTexturedQuad(n, n2);
        GL20.glUseProgram(0);
        GlStateManager.bindTexture(0);
    }

    private List<EntityPlayer> collectTargets(Frustum frustum) {
        ArrayList<EntityPlayer> arrayList = new ArrayList<EntityPlayer>();
        try {
            for (EntityPlayer entityPlayer : ClientUtils.getPlayers()) {
                if (entityPlayer == null || entityPlayer == this.mc.thePlayer || entityPlayer.isDead || !entityPlayer.isEntityAlive() || entityPlayer.isInvisible() || entityPlayer.isSpectator()) continue;
                if (frustum != null) {
                    try {
                        AxisAlignedBB axisAlignedBB = entityPlayer.getEntityBoundingBox().expand(2.0, 2.0, 2.0);
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

    /** State the silhouette is drawn with: no depth, no lighting, no blending, alpha tested. */
    private void beginEspState() {
        GlStateManager.disableLighting();
        GlStateManager.disableFog();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(516, ALPHA_CUTOFF);
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.colorMask(true, true, true, true);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private void endEspState(int n, int n2) {
        try {
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.depthFunc(515);
        GlStateManager.disableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(516, 0.1f);
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.viewport(0, 0, n, n2);
        RenderHelper.disableStandardItemLighting();
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

    private boolean ensureResources() {
        if (this.resourcesFailed) {
            return false;
        }
        try {
            if (!OpenGlHelper.isFramebufferEnabled() || !OpenGlHelper.shadersSupported || Config.isShaders()) {
                this.resourcesFailed = true;
                ClientUtils.debug((Object)"PlayerESP: Outline mode needs framebuffers and shader support, they are unavailable right now.");
                return false;
            }
            int n = Math.max(1, this.mc.displayWidth);
            int n2 = Math.max(1, this.mc.displayHeight);
            if (this.maskBuffer == null || this.maskBuffer.framebufferWidth != n || this.maskBuffer.framebufferHeight != n2) {
                this.deleteBuffers();
                this.maskBuffer = PlayerESP.createBuffer(n, n2, 9728);
                this.glowBufferA = PlayerESP.createBuffer(Math.max(1, n / 2), Math.max(1, n2 / 2), 9729);
                this.glowBufferB = PlayerESP.createBuffer(Math.max(1, n / 2), Math.max(1, n2 / 2), 9729);
            }
            if (this.blurProgram == 0) {
                this.blurProgram = PlayerESP.compileProgram(QUAD_VERTEX_SHADER, BLUR_FRAGMENT_SHADER, "blur");
                if (this.blurProgram == 0) {
                    this.resourcesFailed = true;
                    return false;
                }
                this.blurTexLoc = GL20.glGetUniformLocation(this.blurProgram, "tex");
                this.blurDirLoc = GL20.glGetUniformLocation(this.blurProgram, "dir");
                this.blurExtraLoc = GL20.glGetUniformLocation(this.blurProgram, "extra");
                this.blurRadiusLoc = GL20.glGetUniformLocation(this.blurProgram, "radius");
                this.blurGainLoc = GL20.glGetUniformLocation(this.blurProgram, "gain");
                this.blurShapeLoc = GL20.glGetUniformLocation(this.blurProgram, "shape");
            }
            if (this.outlineProgram == 0) {
                this.outlineProgram = PlayerESP.compileProgram(QUAD_VERTEX_SHADER, OUTLINE_FRAGMENT_SHADER, "outline");
                if (this.outlineProgram == 0) {
                    this.resourcesFailed = true;
                    return false;
                }
                this.outlineMaskLoc = GL20.glGetUniformLocation(this.outlineProgram, "mask");
                this.outlineGlowLoc = GL20.glGetUniformLocation(this.outlineProgram, "glowTex");
                this.outlineTexelLoc = GL20.glGetUniformLocation(this.outlineProgram, "texel");
                this.outlineWidthLoc = GL20.glGetUniformLocation(this.outlineProgram, "width");
                this.outlineAlphaLoc = GL20.glGetUniformLocation(this.outlineProgram, "espAlpha");
                this.outlineGlowOnLoc = GL20.glGetUniformLocation(this.outlineProgram, "glowOn");
            }
            return this.maskBuffer != null && this.glowBufferA != null && this.glowBufferB != null;
        }
        catch (Throwable throwable) {
            this.resourcesFailed = true;
            return false;
        }
    }

    private static Framebuffer createBuffer(int n, int n2, int n3) {
        Framebuffer framebuffer = new Framebuffer(n, n2, false);
        framebuffer.setFramebufferColor(0.0f, 0.0f, 0.0f, 0.0f);
        framebuffer.setFramebufferFilter(n3);
        return framebuffer;
    }

    private void deleteBuffers() {
        Framebuffer[] framebufferArray = new Framebuffer[]{this.maskBuffer, this.glowBufferA, this.glowBufferB};
        for (Framebuffer framebuffer : framebufferArray) {
            try {
                if (framebuffer == null) continue;
                framebuffer.deleteFramebuffer();
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }
        this.maskBuffer = null;
        this.glowBufferA = null;
        this.glowBufferB = null;
    }

    private void releaseResources() {
        this.deleteBuffers();
        try {
            if (this.blurProgram != 0) {
                GL20.glDeleteProgram(this.blurProgram);
            }
            if (this.outlineProgram != 0) {
                GL20.glDeleteProgram(this.outlineProgram);
            }
            if (this.silhouetteProgram != 0) {
                GL20.glDeleteProgram(this.silhouetteProgram);
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        this.blurProgram = 0;
        this.outlineProgram = 0;
        this.silhouetteProgram = 0;
        this.silhouetteFailed = false;
        this.resourcesFailed = false;
    }

    private static int compileProgram(String string, String string2, String string3) {
        int n = GL20.glCreateShader(35633);
        GL20.glShaderSource(n, string);
        GL20.glCompileShader(n);
        if (GL20.glGetShaderi(n, 35713) == 0) {
            System.err.println("[PlayerESP] " + string3 + " vertex shader: " + GL20.glGetShaderInfoLog(n, 4096));
            GL20.glDeleteShader(n);
            return 0;
        }
        int n2 = GL20.glCreateShader(35632);
        GL20.glShaderSource(n2, string2);
        GL20.glCompileShader(n2);
        if (GL20.glGetShaderi(n2, 35713) == 0) {
            System.err.println("[PlayerESP] " + string3 + " fragment shader: " + GL20.glGetShaderInfoLog(n2, 4096));
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
            System.err.println("[PlayerESP] " + string3 + " program link: " + GL20.glGetProgramInfoLog(n3, 4096));
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
