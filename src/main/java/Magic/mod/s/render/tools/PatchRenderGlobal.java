/*
 * Standalone tool (NOT part of the mod build) — reproduces the one
 * bytecode patch PlayerESP's "Minecraft" mode needs. See PLAYERESP_NOTES.md.
 *
 * Usage:
 *   javac -cp <exploded primordial.jar classes dir> PatchRenderGlobal.java
 *   java  -cp .:<same classes dir> PatchRenderGlobal <classes dir> <output dir>
 *
 * Then `jar uf primordial.jar -C <output dir> net/minecraft/client/renderer/RenderGlobal.class`
 * to drop the patched class back into the real jar. Javassist ships inside
 * primordial.jar already (top-level `javassist` package), so no extra
 * dependency is needed — just point ClassPool at the exploded jar.
 */
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;

public class PatchRenderGlobal {
    public static void main(String[] args) throws Exception {
        String classesDir = args[0];
        String outDir = args[1];

        ClassPool pool = ClassPool.getDefault();
        pool.insertClassPath(classesDir);

        CtClass renderGlobal = pool.get("net.minecraft.client.renderer.RenderGlobal");
        CtMethod method = renderGlobal.getDeclaredMethod("isRenderEntityOutlines");
        method.insertBefore("if (Magic.mod.s.render.PlayerESP.wantsVanillaOutline()) { return true; }");
        renderGlobal.writeFile(outDir);

        System.out.println("Patched isRenderEntityOutlines(), wrote class to " + outDir);
    }
}
