/*
 * Standalone tool (NOT part of the mod build) — applies the two renderer
 * patches PlayerESP's "Minecraft" and "Outline" modes need, mirroring the
 * ones baked into Peter's client (ed.java / fZ.java). See PLAYERESP_NOTES.md.
 *
 * Usage:
 *   javac -cp <exploded primordial.jar classes dir> PatchMagic.java
 *   java  -cp .:<same classes dir> PatchMagic <classes dir> <output dir>
 *
 * Then:
 *   jar uf primordial.jar -C <output dir> net/minecraft/client/renderer/RenderGlobal.class
 *   jar uf primordial.jar -C <output dir> net/minecraft/client/renderer/entity/RendererLivingEntity.class
 *
 * Javassist already ships inside primordial.jar (top-level `javassist`
 * package), so no extra dependency is needed — just point ClassPool at the
 * exploded jar.
 */
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import javassist.expr.NewExpr;

public class PatchMagic {

    private static final String ESP = "Magic.mod.s.render.PlayerESP";

    public static void main(String[] args) throws Exception {
        String classesDir = args[0];
        String outDir = args[1];

        ClassPool pool = ClassPool.getDefault();
        pool.insertClassPath(classesDir);

        patchRenderGlobal(pool, outDir);
        patchRendererLivingEntity(pool, outDir);
        System.out.println("Both patches written to " + outDir);
    }

    /**
     * Both patches rewrite call sites, so running them on a class that already
     * carries them nests the injection inside itself: the four extra
     * renderModel() draws are themselves renderModel() call sites, and a
     * second pass wraps every one of them again (2 sites become 10, and each
     * of those gets its own stencil block). The result still verifies and
     * still loads — it just renders the silhouette dozens of times per entity —
     * so nothing downstream would catch it.
     *
     * The tell is unambiguous and needs no marker of our own: a stock renderer
     * class has no reason to mention PlayerESP in its constant pool.
     *
     * If this fires, the input is a jar that was patched before. Re-run
     * against a pristine primordial.jar — the patch is not incremental, one
     * run produces the finished class.
     */
    private static void refuseIfAlreadyPatched(CtClass target) {
        if (target.getRefClasses() != null && target.getRefClasses().contains(ESP)) {
            throw new IllegalStateException(target.getName() + " already references " + ESP
                    + " — this class has been patched before. Patching it again would nest the"
                    + " injection inside itself. Start from a pristine primordial.jar.");
        }
    }

    /**
     * "Minecraft" mode — Peter patches ed.java's isRenderEntityOutlines()
     * equivalent to short-circuit to true, reusing vanilla's own
     * entityOutlineFramebuffer/entityOutlineShader spectator-glow pass.
     *
     * Two conditions are ANDed in here that Peter's version does not have,
     * both load-bearing for THIS client rather than optional hardening:
     *
     *  - OptiFine's own guard. Peter's client has no OptiFine; Magic does,
     *    and OptiFine gates this same pass behind
     *    !(isFastRender() || isShaders() || isAntialiasing()) because the
     *    framebuffer rebind is incompatible with those. Bypassing it turns
     *    the screen black on world entry, which is exactly the bug an
     *    earlier version of this patch shipped.
     *  - The framebuffer/shader null-checks vanilla's own branch performs.
     *    Everything downstream of a `true` return here dereferences those
     *    fields without checking (framebufferClear/bindFramebuffer/
     *    framebufferRenderExt), so returning true before they exist NPEs
     *    once per frame inside the render loop.
     */
    private static void patchRenderGlobal(ClassPool pool, String outDir) throws Exception {
        CtClass renderGlobal = pool.get("net.minecraft.client.renderer.RenderGlobal");
        refuseIfAlreadyPatched(renderGlobal);
        CtMethod method = renderGlobal.getDeclaredMethod("isRenderEntityOutlines");
        // The two private fields are passed in rather than tested here, so the
        // module side can report which precondition failed instead of the mode
        // just doing nothing. The OptiFine guard lives on that side too now.
        method.insertBefore(
                "if (" + ESP + ".vanillaOutlineHook(this.entityOutlineFramebuffer != null,"
                        + " this.entityOutlineShader != null)) { return true; }");
        // The composite step sets its blend state through GlStateManager,
        // whose cache this client desyncs with raw GL11 calls elsewhere; when
        // it does, enableBlend() no-ops and the outline buffer is blitted
        // opaque over the whole frame. Force the real state first.
        CtMethod composite = renderGlobal.getDeclaredMethod("renderEntityOutlineFramebuffer");
        composite.insertBefore(ESP + ".forceOutlineBlend();");

        // Load the outline chain from our own resource names.
        //
        // The stock path, shaders/post/entity_outline.json, resolves to
        // something other than this jar in the field — confirmed at runtime by
        // reading it back through the game's own resource manager, which
        // returned a copy without our edits. FallbackResourceManager lets the
        // last resource pack win, and the classpath can hold another jar with
        // the same asset, so patching those files in place is not reliable.
        // Whatever shadows them, it does not know these names.
        //
        // The shaders behind them are the stock ones. The copies that were
        // being loaded force alpha to 1.0 in blur and blit, which makes the
        // outline buffer fully opaque, so compositing it replaces the frame
        // instead of blending onto it — the black world with flat silhouettes.
        CtMethod makeShader = renderGlobal.getDeclaredMethod("makeEntityOutlineShader");
        makeShader.instrument(new ExprEditor() {
            @Override
            public void edit(NewExpr expr) throws CannotCompileException {
                if (!"net.minecraft.util.ResourceLocation".equals(expr.getClassName())) {
                    return;
                }
                expr.replace("{ $_ = new net.minecraft.util.ResourceLocation(\"shaders/post/magic_esp_outline.json\"); }");
            }
        });

        renderGlobal.writeFile(outDir);
        System.out.println("Patched RenderGlobal.isRenderEntityOutlines() + renderEntityOutlineFramebuffer()");
    }

    /**
     * "Outline" mode — Peter patches fZ.java (RendererLivingEntity) so that,
     * in doRender's non-renderOutlines branch, the model is drawn through a
     * 4-pass GL_STENCIL_TEST silhouette before the normal draw:
     *
     *     renderModel();  stencilSetup();
     *     renderModel();  stencilFillPass();
     *     renderModel();  stencilOutlinePass();
     *     applyOutlineColor(entity); outlineDrawState();
     *     renderModel();  stencilTeardown();
     *     renderModel();   <- the original call proceeds
     *
     * Rather than trying to splice that into the middle of a method body, we
     * rewrite the renderModel() call site itself: same instruction order,
     * same five draws, and the !renderOutlines guard reproduces Peter's
     * placement inside the else-branch (the vanilla-outline branch calls
     * renderModel too, and must not be touched).
     *
     * PlayerESP's ThroughArmor setting adds a second possible placement for
     * that same block and one addition to the vanilla-outline branch:
     *
     *  - Outline, ThroughArmor on (default): the block runs after
     *    renderLayers() rather than before it. The silhouette is identical —
     *    still four passes over the bare model — but armor is no longer drawn
     *    on top of it, so the outline stays visible across the armor. Exactly
     *    one of the two placements runs; the module decides which.
     *  - Minecraft, ThroughArmor off: the armor is drawn into the
     *    renderOutlines branch too, so it joins the silhouette written to the
     *    entity outline framebuffer and the glow wraps it instead of crossing
     *    it. On (default) leaves that branch exactly as vanilla has it.
     */
    private static void patchRendererLivingEntity(ClassPool pool, String outDir) throws Exception {
        CtClass renderer = pool.get("net.minecraft.client.renderer.entity.RendererLivingEntity");
        refuseIfAlreadyPatched(renderer);
        CtMethod doRender = renderer.getDeclaredMethod("doRender");
        final int[] patchedModel = {0};
        final int[] patchedLayers = {0};
        doRender.instrument(new ExprEditor() {
            @Override
            public void edit(MethodCall call) throws CannotCompileException {
                if ("renderModel".equals(call.getMethodName())) {
                    patchedModel[0]++;
                    call.replace(
                            "{ if (!$0.renderOutlines && " + ESP + ".stencilOutlineBeforeLayers($1)) {"
                                    + stencilBlock("$proceed($$);")
                                    + " }"
                                    + " $proceed($$);"
                                    // Minecraft mode, ThroughArmor off: this is
                                    // the pass that fills the entity outline
                                    // framebuffer, and vanilla puts only the
                                    // bare model in it. Adding the armor puts
                                    // it into the silhouette, so the outline
                                    // wraps it instead of crossing it. Armor
                                    // only, and not through renderLayers() —
                                    // see renderOutlineArmor() for why the
                                    // other layers wreck this buffer.
                                    + " if ($0.renderOutlines && " + ESP + ".armorInVanillaOutline($1)) {"
                                    + "   " + ESP + ".renderOutlineArmor($0.layerRenderers, $1, $2, $3, "
                                    + ESP + ".partialTicks(), $4, $5, $6, $7);"
                                    + " } }");
                    return;
                }
                if (!"renderLayers".equals(call.getMethodName())) {
                    return;
                }
                // Outline mode, ThroughArmor on (the default): the identical
                // stencil block, only moved to after the layers so armor no
                // longer paints over the outline. renderLayers() is called
                // exactly once in doRender(), inside the !renderOutlines
                // branch, so this lands where the block used to run.
                //
                // renderLayers(entity, limbSwing, limbSwingAmount,
                //              partialTicks, ageInTicks, netHeadYaw,
                //              headPitch, scale)
                // renderModel (entity, limbSwing, limbSwingAmount,
                //              ageInTicks, netHeadYaw, headPitch, scale)
                // i.e. renderModel takes the same arguments minus $4.
                patchedLayers[0]++;
                call.replace(
                        "{ $proceed($$);"
                                + " if (!$0.renderOutlines && " + ESP + ".stencilOutlineOverLayers($1)) {"
                                + stencilBlock("$0.renderModel($1, $2, $3, $5, $6, $7, $8);")
                                + " } }");
            }
        });
        if (patchedModel[0] == 0) {
            throw new IllegalStateException("no renderModel() call found in doRender() — refusing to write a no-op patch");
        }
        if (patchedLayers[0] != 1) {
            throw new IllegalStateException("expected exactly 1 renderLayers() call in doRender(), found "
                    + patchedLayers[0] + " — refusing to write a patch whose placement is not the one reasoned about");
        }
        renderer.writeFile(outDir);
        System.out.println("Patched RendererLivingEntity.doRender() (" + patchedModel[0] + " renderModel call site(s), "
                + patchedLayers[0] + " renderLayers call site)");
    }

    /**
     * Peter's 4-pass stencil silhouette. The draw call is passed in because
     * the two placements reach the model differently: at the renderModel()
     * call site it is that call itself ($proceed), and after renderLayers()
     * it has to be spelled out.
     */
    private static String stencilBlock(String drawModel) {
        return "   " + drawModel
                + "   " + ESP + ".stencilSetup();"
                + "   " + drawModel
                + "   " + ESP + ".stencilFillPass();"
                + "   " + drawModel
                + "   " + ESP + ".stencilOutlinePass();"
                + "   " + ESP + ".applyOutlineColor($1);"
                + "   " + ESP + ".outlineDrawState();"
                + "   " + drawModel
                + "   " + ESP + ".stencilTeardown();";
    }
}
