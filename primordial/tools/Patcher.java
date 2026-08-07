import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Rewrites the single GlStateManager.color(FFFF)V call inside
 * RendererLivingEntity.setScoreTeamColor into
 * PlayerESP.outlineTeamColor(FFFFLnet/minecraft/entity/EntityLivingBase;)V,
 * pushing the method's entity argument (local 1) as the extra parameter.
 */
public final class Patcher {

    private static final String TARGET_METHOD = "setScoreTeamColor";
    private static final String GL_OWNER = "net/minecraft/client/renderer/GlStateManager";
    private static final String ESP_OWNER = "Magic/mod/s/render/PlayerESP";
    private static int patched;

    public static void main(String[] args) throws IOException {
        Path in = Paths.get(args[0]);
        Path out = Paths.get(args[1]);
        ClassReader reader = new ClassReader(Files.readAllBytes(in));
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!TARGET_METHOD.equals(name) || !"(Lnet/minecraft/entity/EntityLivingBase;)Z".equals(descriptor)) {
                    return mv;
                }
                return new MethodVisitor(Opcodes.ASM9, mv) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                        if (opcode == Opcodes.INVOKESTATIC && GL_OWNER.equals(owner) && "color".equals(name) && "(FFFF)V".equals(descriptor)) {
                            super.visitVarInsn(Opcodes.ALOAD, 1);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, ESP_OWNER, "outlineTeamColor", "(FFFFLnet/minecraft/entity/EntityLivingBase;)V", false);
                            ++patched;
                            return;
                        }
                        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                    }
                };
            }
        }, 0);
        if (patched != 1) {
            throw new IllegalStateException("expected exactly one GlStateManager.color call, patched " + patched);
        }
        Files.write(out, writer.toByteArray());
        System.out.println("patched " + patched + " call site -> " + out);
    }
}
