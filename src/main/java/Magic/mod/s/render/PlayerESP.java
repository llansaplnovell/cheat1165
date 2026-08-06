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
 * THE DELIBERATE DIFFERENCES FROM PETER (and why)
 * -----------------------------------------------
 * 1. ThroughArmor. Peter had one setting, the mode combo, and both outline
 * modes were stuck with whatever their placement in the render happened to
 * give: Outline's silhouette is drawn before the armor layers and so gets
 * painted over by them, while Minecraft's is composited over the finished
 * frame and so is never hidden at all. That is now a setting, on by default
 * for both, which does change Outline's out-of-the-box look. See
 * stencilOutlineOverLayers() and renderOutlineArmor().
 *
 * 2. Peter's isRenderEntityOutlines() equivalent returns true *unconditionally*
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
import Magic.mod.value.values.BoolValue;
import Magic.mod.value.values.EnumValue;
import Magic.utils.Friend.FriendManager;
import Magic.utils.player.ClientUtils;
import Magic.utils.render.StencilUtil;
import java.awt.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.layers.LayerArmorBase;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import org.lwjgl.opengl.GL11;
import pisi.unitedmeows.eventapi.event.listener.Listener;

public class PlayerESP extends Module {

    /** Peter's PlayerESP had exactly one setting: the ESPModes combo. */
    private final EnumValue<Mode> mode = new EnumValue<Mode>("Mode", this, Mode.class, "ESP render style.");

    /**
     * Whether the outline is visible over armor. Only the two modes that
     * outline the real model can answer that question, so the Condition keeps
     * it hidden in the ClickGUI for Corner/Box/Other (ModulePanel and
     * ValuePanel both skip values whose isOpen() is false).
     *
     * on (default) — the outline shows through armor, in both modes
     * off          — armor covers (Outline) / shapes (Minecraft) the outline
     *
     * Default on because that is what the mode is for; note this *changes*
     * Outline's out-of-the-box look, which used to be the "off" behaviour,
     * and leaves Minecraft's alone, which was already the "on" behaviour.
     *
     * The two modes need different mechanisms because they build their
     * silhouette at different points — see stencilOutlineOverLayers() and
     * renderOutlineArmor().
     */
    private final BoolValue throughArmor = new BoolValue("ThroughArmor", this, true,
            "Show the outline through armor.",
            () -> this.mode.getValue() == Mode.Outline || this.mode.getValue() == Mode.Minecraft);

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

    /**
     * Mode the chat report below was last written for. Every value change
     * routes through onSuffixChange() — with Mode the only setting that used
     * to mean "the mode changed", but ThroughArmor goes through it too, and
     * the report is a three-line dump nobody wants repeated on every click.
     */
    private Mode reportedMode;

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

    /**
     * Minecraft mode rides vanilla's entity-outline post pass, which has four
     * independent preconditions, any of which silently disables it. Rather
     * than guess, report all of them, and try to build the shader first in
     * case it simply was never created (makeEntityOutlineShader is public and
     * idempotent — startGame calls it once, before any resource reload).
     */
    private void warnIfVanillaOutlineBlocked() {
        try {
            if (!this.isEnabled() || this.mode.getValue() != Mode.Minecraft) {
                return;
            }
            Minecraft mc = Minecraft.getMinecraft();
            if (!sawShader || !sawFramebuffer) {
                try {
                    mc.renderGlobal.makeEntityOutlineShader();
                } catch (Throwable ignored) {
                }
            }
            String blocker = vanillaOutlineBlocker();
            if (!OpenGlHelper.shadersSupported) {
                ClientUtils.debug("PlayerESP: Minecraft mode needs shader support, which this "
                        + "driver reports as unavailable. Use Outline instead.");
            } else if (blocker != null) {
                ClientUtils.debug("PlayerESP: Minecraft mode is unavailable while OptiFine "
                        + blocker + " is on — turn it off, or use Outline instead.");
            } else if (!OpenGlHelper.isFramebufferEnabled()) {
                ClientUtils.debug("PlayerESP: Minecraft mode needs framebuffers, which are off "
                        + "right now (Video Settings -> FBO). Use Outline instead.");
            } else if (hookRan && (!sawFramebuffer || !sawShader)) {
                ClientUtils.debug("PlayerESP: Minecraft mode could not build the entity outline "
                        + "shader (check the log for \"Failed to load shader\"). Use Outline instead.");
            } else {
                ClientUtils.debug("PlayerESP: Minecraft mode active.");
            }
            // Full state dump. The composite line is the interesting one now:
            // it reports the real GL state sampled at blit time plus which
            // framebuffer was bound, so we stop inferring from screenshots.
            ClientUtils.debug("PlayerESP state: fboEnabled=" + OpenGlHelper.isFramebufferEnabled()
                    + " chain=" + loadedOutlineChain()
                    + " outlineShader=" + sawShader + " outlineFbo=" + sawFramebuffer
                    + " hookRan=" + hookRan);
            ClientUtils.debug("PlayerESP composite: " + compositeGlState
                    + " | mainFbo=" + (mc.getFramebuffer() == null ? "null" : String.valueOf(mc.getFramebuffer().framebufferObject))
                    + " display=" + mc.displayWidth + "x" + mc.displayHeight);
        } catch (Throwable ignored) {
        }
    }

    // ================= hooks used by the two renderer patches =================

    /**
     * Called at the top of the patched RenderGlobal.renderEntityOutlineFramebuffer().
     *
     * That method sets up its blend state through GlStateManager, which
     * caches: enableBlend() does nothing if the cache already believes blend
     * is on. This client drives a lot of GL through raw GL11 calls that never
     * update that cache, so by the time the outline is composited the cache
     * and the real GL state can disagree — and then the blit runs with
     * blending off and *replaces* the frame instead of compositing onto it.
     * That is the black-world-with-flat-silhouettes picture: what you see is
     * the outline buffer's raw rgb, opaque, over everything.
     *
     * Setting it with raw GL11 here forces the real state regardless of what
     * the cache thinks. Only touched while this mode is actually running.
     */
    /** GL state observed at composite time, captured before we override it. */
    private static volatile String compositeGlState = "not sampled";

    public static void forceOutlineBlend() {
        // Same decision the gate makes, so the GL state is only forced on
        // frames that actually composite — otherwise this would leave blend
        // on and the alpha test off for the rest of the frame.
        if (!vanillaOutlineHook(sawFramebuffer, sawShader)) {
            return;
        }
        try {
            compositeGlState = "blend=" + GL11.glIsEnabled(GL11.GL_BLEND)
                    + " src=" + GL11.glGetInteger(GL11.GL_BLEND_SRC)
                    + " dst=" + GL11.glGetInteger(GL11.GL_BLEND_DST)
                    + " alphaTest=" + GL11.glIsEnabled(GL11.GL_ALPHA_TEST)
                    + " depthTest=" + GL11.glIsEnabled(GL11.GL_DEPTH_TEST)
                    + " boundFbo=" + GL11.glGetInteger(36006); // GL_FRAMEBUFFER_BINDING
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
        } catch (Throwable t) {
            compositeGlState = "sample failed: " + t;
        }
    }

    /**
     * Whether the entity_outline chain the game actually loaded is the patched
     * one. Read through the resource manager, i.e. exactly the path the game
     * itself takes, so an external assets dir or a resource pack overriding
     * the jar's copy shows up here instead of being invisible.
     */
    private static String loadedOutlineChain() {
        java.io.InputStream in = null;
        try {
            in = Minecraft.getMinecraft().getResourceManager()
                    .getResource(new net.minecraft.util.ResourceLocation("shaders/post/magic_esp_outline.json"))
                    .getInputStream();
            byte[] buf = new byte[8192];
            int n, off = 0;
            while (off < buf.length && (n = in.read(buf, off, buf.length - off)) > 0) {
                off += n;
            }
            String json = new String(buf, 0, off, "UTF-8");
            return json.contains("magic_esp_blur") ? "ours" : "NOT-OURS";
        } catch (Throwable t) {
            return "unreadable(" + t.getClass().getSimpleName() + ")";
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (java.io.IOException ignored) {
            }
        }
    }

    /** Last framebuffer/shader state seen by the hook, for diagnostics. */
    private static volatile boolean sawFramebuffer;
    private static volatile boolean sawShader;
    private static volatile boolean hookRan;

    /**
     * Called from the patched RenderGlobal.isRenderEntityOutlines(), which
     * passes in its own two private fields. Taking them as arguments instead
     * of testing them inside the patch means this side can also *report*
     * which precondition failed — the mode used to fail completely silently.
     */
    public static boolean vanillaOutlineHook(boolean hasFramebuffer, boolean hasShader) {
        sawFramebuffer = hasFramebuffer;
        sawShader = hasShader;
        hookRan = true;
        if (active(Mode.Minecraft) == null) {
            return false;
        }
        // Everything downstream of a true return dereferences those two
        // fields without checking, so a true here without them is an NPE
        // once per frame inside the render loop.
        if (!hasFramebuffer || !hasShader) {
            return false;
        }
        // The authoritative precondition, and the one whose absence produced
        // the black-world/white-player artifact. Framebuffer.bindFramebuffer()
        // is a no-op when this is false — but framebufferClear() calls it and
        // then runs GlStateManager.clear() unconditionally, so the outline
        // pass ends up clearing the real screen and drawing the players
        // straight onto it with depthFunc(GL_ALWAYS). Checking OptiFine's
        // three Config flags separately was not enough: this also covers
        // framebufferSupported and the fboEnable video setting.
        try {
            if (!OpenGlHelper.isFramebufferEnabled()) {
                return false;
            }
        } catch (Throwable ignored) {
            return false;
        }
        return vanillaOutlineBlocker() == null;
    }

    /**
     * Whether Outline mode wants a silhouette around this entity at all —
     * the shared half of the two hooks below, which only differ in where
     * doRender() draws it. Mirrors Peter's `entity instanceof nH &&
     * entity != f.jF`: every living entity except your own player, not only
     * players.
     */
    public static boolean wantsStencilOutline(EntityLivingBase entity) {
        if (entity == null || active(Mode.Outline) == null) {
            return false;
        }
        return entity != Minecraft.getMinecraft().thePlayer;
    }

    // ------------------------- ThroughArmor plumbing -------------------------
    //
    // Where the stencil block is placed inside doRender() is the whole trick
    // for Outline mode, so wantsStencilOutline() is split into the two call
    // sites the patch can put it at. Exactly one of them is ever true.

    /**
     * Outline mode, ThroughArmor OFF — draw the silhouette at the original
     * spot, i.e. at the renderModel() call, before the layers.
     *
     * outlineDrawState() draws with GL_DEPTH_TEST off and glDepthMask(false),
     * so the outline writes no depth of its own; armor is a layer drawn
     * afterwards with depth testing on and simply paints over it. That is why
     * this placement means "armor covers the outline" — reproduced unchanged,
     * so switching the setting off is byte-for-byte the old behaviour.
     */
    public static boolean stencilOutlineBeforeLayers(EntityLivingBase entity) {
        PlayerESP module = active(Mode.Outline);
        return module != null && !module.throughArmor.getValue() && wantsStencilOutline(entity);
    }

    /**
     * Outline mode, ThroughArmor ON (default) — draw the very same silhouette,
     * unchanged, but after renderLayers() instead of before it.
     *
     * Nothing about the outline itself changes: the same four renderModel()
     * passes over the bare body, the same stencil ops, the same colour. Only
     * the armor is no longer painted on top of it afterwards, so the line
     * stays visible across the armor. Drawing the layers a second time up
     * front would achieve the same picture, but it renders every layer twice
     * per entity (and blends the enchantment glint twice); moving the block
     * costs nothing.
     */
    public static boolean stencilOutlineOverLayers(EntityLivingBase entity) {
        PlayerESP module = active(Mode.Outline);
        return module != null && module.throughArmor.getValue() && wantsStencilOutline(entity);
    }

    /**
     * Minecraft mode, ThroughArmor OFF — add the armor to the entity outline
     * silhouette for this entity.
     *
     * This mode does not draw anything itself: it rides vanilla's spectator
     * glow pass, which composites its buffer over the finished frame, so its
     * outline is always on top and the only question is what is in the
     * silhouette. Vanilla puts the bare model there and nothing else, which is
     * precisely why the glow runs across armor by default — so ThroughArmor ON
     * is the untouched vanilla path, and only OFF has to do anything.
     */
    public static boolean armorInVanillaOutline(EntityLivingBase entity) {
        PlayerESP module = active(Mode.Minecraft);
        if (module == null || module.throughArmor.getValue()) {
            return false;
        }
        // Vanilla's own renderLayers() call carries this guard; keep it, so a
        // spectator does not get armor drawn for them here and nowhere else.
        return !(entity instanceof EntityPlayer) || !((EntityPlayer) entity).isSpectator();
    }

    /**
     * Puts the armor into the entity outline framebuffer, and nothing else.
     *
     * Deliberately not renderLayers(): that draws every layer, and two of them
     * ruin this buffer. magic_esp_edge.fsh finds edges by comparing the ALPHA
     * of neighbouring texels, so the silhouette only reads as an outline while
     * its alpha is uniform — one flat region on a cleared buffer, edges just at
     * its border. LayerHeldItem and LayerCustomHead both go through
     * RenderItem, which turns texturing back on (setScoreTeamColor had it off),
     * and a textured draw writes the texture's alpha: holes and soft edges all
     * over the inside of the figure, every one of which the shader then draws.
     * That is the glow painted across the player instead of around them. The
     * held item would also become part of the silhouette, which is not what
     * "same outline, just not through armor" means anyway.
     *
     * So: armor layers only, and every fragment of them forced to one flat
     * opaque colour — see beginArmorSilhouette(), which does that in a way the
     * layer cannot override.
     */
    public static void renderOutlineArmor(java.util.List<?> layers, EntityLivingBase entity,
                                          float limbSwing, float limbSwingAmount, float partialTicks,
                                          float ageInTicks, float netHeadYaw, float headPitch, float scale) {
        if (layers == null || entity == null) {
            return;
        }
        try {
            beginArmorSilhouette();
            for (Object layer : layers) {
                if (!(layer instanceof LayerArmorBase)) {
                    continue;
                }
                try {
                    ((LayerArmorBase<?>) layer).doRenderLayer(entity, limbSwing, limbSwingAmount,
                            partialTicks, ageInTicks, netHeadYaw, headPitch, scale);
                } catch (Throwable ignored) {
                    // One bad layer must not take down the outline pass: the
                    // rest of it still has entities to draw.
                }
            }
        } catch (Throwable ignored) {
        } finally {
            endArmorSilhouette();
        }
    }

    /** Scratch for reading GL_CURRENT_COLOR — LWJGL wants 16 floats of room. */
    private static final java.nio.FloatBuffer COLOR_QUERY = GLAllocation.createDirectFloatBuffer(16);
    /** The colour every armor fragment is forced to. */
    private static final java.nio.FloatBuffer SILHOUETTE_COLOR = GLAllocation.createDirectFloatBuffer(4);

    private static boolean savedBlend;
    private static boolean savedAlphaTest;
    private static boolean savedTexture2D;

    /**
     * Forces the armor draw to put out one flat opaque colour per fragment,
     * whatever LayerArmorBase does inside.
     *
     * Merely turning texturing off is not enough, and that is what the first
     * attempt got wrong. LayerArmorBase sets its own glColor per piece (the dye
     * colour for leather), and for enchanted armor it then draws the glint over
     * the same model in purple (0.5, 0.25, 0.8) with additive blending — which
     * is exactly the purple that showed up in the outline, and why the result
     * did not read as a silhouette any more.
     *
     * So instead of trying to stop the layer from choosing colours, this makes
     * the choice irrelevant: texturing ON, but with the texture environment set
     * to REPLACE from GL_CONSTANT for both RGB and alpha. Every fragment then
     * comes out as the constant, no matter what texture is bound, what glColor
     * the layer set, or what the glint blends on top — the glint ends up
     * drawing the same colour it is drawing over. This is the same mechanism
     * 1.9 added as GlStateManager.enableOutlineMode() for this exact job.
     *
     * The constant is read from GL_CURRENT_COLOR, which at this point is still
     * the colour setScoreTeamColor() set and the body was drawn with, so armor
     * and body land in the buffer as one region rather than two.
     *
     * Raw GL, not GlStateManager, because a no-op is the failure mode here: the
     * client drives plenty of GL directly and its cache can already disagree
     * with the driver (forceOutlineBlend exists for that reason). What is
     * changed is read back first and restored exactly.
     */
    private static void beginArmorSilhouette() {
        savedBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        savedAlphaTest = GL11.glIsEnabled(3008);            // GL_ALPHA_TEST
        savedTexture2D = GL11.glIsEnabled(3553);            // GL_TEXTURE_2D

        SILHOUETTE_COLOR.clear();
        try {
            COLOR_QUERY.clear();
            GL11.glGetFloat(2816, COLOR_QUERY);             // GL_CURRENT_COLOR
            SILHOUETTE_COLOR.put(COLOR_QUERY.get(0)).put(COLOR_QUERY.get(1)).put(COLOR_QUERY.get(2));
        } catch (Throwable ignored) {
            SILHOUETTE_COLOR.clear();
            SILHOUETTE_COLOR.put(1.0f).put(1.0f).put(1.0f);
        }
        SILHOUETTE_COLOR.put(1.0f);                         // opaque, always
        SILHOUETTE_COLOR.flip();

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(3008);                               // GL_ALPHA_TEST — nothing may be cut out
        // The lightmap unit is off for this whole pass (setScoreTeamColor), so
        // there is nothing there to modulate what unit 0 produces.
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GL11.glEnable(3553);                                // texturing must be on for the env to apply
        GL11.glTexEnvi(8960, 8704, OpenGlHelper.GL_COMBINE); // GL_TEXTURE_ENV, GL_TEXTURE_ENV_MODE
        GL11.glTexEnvi(8960, OpenGlHelper.GL_COMBINE_RGB, 7681);              // GL_REPLACE
        GL11.glTexEnvi(8960, OpenGlHelper.GL_SOURCE0_RGB, OpenGlHelper.GL_CONSTANT);
        GL11.glTexEnvi(8960, OpenGlHelper.GL_OPERAND0_RGB, 768);              // GL_SRC_COLOR
        GL11.glTexEnvi(8960, OpenGlHelper.GL_COMBINE_ALPHA, 7681);            // GL_REPLACE
        GL11.glTexEnvi(8960, OpenGlHelper.GL_SOURCE0_ALPHA, OpenGlHelper.GL_CONSTANT);
        GL11.glTexEnvi(8960, OpenGlHelper.GL_OPERAND0_ALPHA, 770);            // GL_SRC_ALPHA
        GL11.glTexEnv(8960, 8705, SILHOUETTE_COLOR);        // GL_TEXTURE_ENV_COLOR
    }

    /**
     * Puts back the texture environment MC runs with everywhere else — this is
     * unsetBrightness()'s own restore, i.e. the game's definition of "normal" —
     * and then the enable flags that were read in begin.
     *
     * Depth goes back through GlStateManager on purpose: the glint path changes
     * it through GlStateManager too (GL_EQUAL/GL_LEQUAL, depthMask), so the
     * cache believes GL_LEQUAL by now. The outline pass has more entities to
     * draw and RenderGlobal set GL_ALWAYS for it; setting it any other way
     * would leave the cache and the driver disagreeing, and the next
     * GlStateManager.depthFunc() would then be the no-op that sticks.
     */
    private static void endArmorSilhouette() {
        GL11.glTexEnvi(8960, 8704, OpenGlHelper.GL_COMBINE);
        GL11.glTexEnvi(8960, OpenGlHelper.GL_COMBINE_RGB, 8448);              // GL_MODULATE
        GL11.glTexEnvi(8960, OpenGlHelper.GL_SOURCE0_RGB, OpenGlHelper.defaultTexUnit);
        GL11.glTexEnvi(8960, OpenGlHelper.GL_SOURCE1_RGB, OpenGlHelper.GL_PRIMARY_COLOR);
        GL11.glTexEnvi(8960, OpenGlHelper.GL_OPERAND0_RGB, 768);
        GL11.glTexEnvi(8960, OpenGlHelper.GL_OPERAND1_RGB, 768);
        GL11.glTexEnvi(8960, OpenGlHelper.GL_COMBINE_ALPHA, 8448);
        GL11.glTexEnvi(8960, OpenGlHelper.GL_SOURCE0_ALPHA, OpenGlHelper.defaultTexUnit);
        GL11.glTexEnvi(8960, OpenGlHelper.GL_SOURCE1_ALPHA, OpenGlHelper.GL_PRIMARY_COLOR);
        GL11.glTexEnvi(8960, OpenGlHelper.GL_OPERAND0_ALPHA, 770);
        GL11.glTexEnvi(8960, OpenGlHelper.GL_OPERAND1_ALPHA, 770);

        if (!savedTexture2D) {
            GL11.glDisable(3553);
        }
        if (savedAlphaTest) {
            GL11.glEnable(3008);
        }
        if (savedBlend) {
            GL11.glEnable(GL11.GL_BLEND);
        }
        GlStateManager.depthMask(true);
        GlStateManager.depthFunc(519);          // GL_ALWAYS, as RenderGlobal set for this pass
    }

    /**
     * renderLayers() needs partialTicks, which is a doRender() parameter and
     * therefore out of scope at the renderModel() call site the patch rewrites
     * (the class carries no LocalVariableTable, so javassist cannot name it).
     *
     * This is the same float and not an approximation of it: runGameLoop()
     * renders via entityRenderer.func_181560_a(this.timer.renderPartialTicks),
     * and nothing writes the field again for the duration of that call, so
     * every partialTicks handed down the render path is this value.
     */
    public static float partialTicks() {
        try {
            return Minecraft.getMinecraft().timer.renderPartialTicks;
        } catch (Throwable ignored) {
            return 1.0f;
        }
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
