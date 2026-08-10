import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.util.CheckClassAdapter;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Inserts a call to net/minecraft/client/gui/ServerIconFix.sanitize(ServerData) at the top of
 * ServerListEntryNormal.prepareServerIcon() and adds the ServerIconFix class to the jar.
 *
 * Usage: PatchJar <input.jar> <output.jar> <compiled-classes-dir>
 */
public final class PatchJar {
    private static final String TARGET = "net/minecraft/client/gui/ServerListEntryNormal";
    private static final String TARGET_ENTRY = TARGET + ".class";
    private static final String FIX = "net/minecraft/client/gui/ServerIconFix";
    private static final String FIX_ENTRY = FIX + ".class";
    private static final String SERVER_DATA = "Lnet/minecraft/client/multiplayer/ServerData;";

    public static void main(String[] args) throws Exception {
        Path in = Paths.get(args[0]);
        Path out = Paths.get(args[1]);
        Path classesDir = Paths.get(args[2]);

        byte[] fixClass = Files.readAllBytes(classesDir.resolve(FIX_ENTRY));

        byte[] original;
        try (ZipFile zip = new ZipFile(in.toFile())) {
            original = readAll(zip.getInputStream(zip.getEntry(TARGET_ENTRY)));
        }
        byte[] patched = patch(original);
        verify(patched, in);

        int copied = 0;
        int skipped = 0;
        try (ZipFile zip = new ZipFile(in.toFile());
             ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(out))) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (isSignatureFile(name)) {
                    System.out.println("dropping stale signature file: " + name);
                    skipped++;
                    continue;
                }
                if (name.equals(FIX_ENTRY)) {
                    skipped++;
                    continue; // written below
                }
                byte[] data = entry.isDirectory() ? new byte[0] : readAll(zip.getInputStream(entry));
                if (name.equals(TARGET_ENTRY)) {
                    data = patched;
                }
                write(zos, name, entry.getMethod(), data);
                copied++;
            }
            write(zos, FIX_ENTRY, ZipEntry.DEFLATED, fixClass);
        }
        System.out.println("copied " + copied + " entries, skipped " + skipped
                + ", patched " + TARGET_ENTRY + ", added " + FIX_ENTRY);
        System.out.println("output: " + out.toAbsolutePath() + " ("
                + new File(out.toString()).length() + " bytes)");
    }

    private static byte[] patch(byte[] original) {
        ClassReader cr = new ClassReader(original);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        MethodNode target = null;
        for (MethodNode mn : cn.methods) {
            if (mn.name.equals("prepareServerIcon") && mn.desc.equals("()V")) {
                target = mn;
            }
        }
        if (target == null) {
            throw new IllegalStateException("prepareServerIcon()V not found in " + TARGET);
        }

        String serverField = null;
        for (org.objectweb.asm.tree.FieldNode fn : cn.fields) {
            if (fn.desc.equals(SERVER_DATA)) {
                if (serverField != null) {
                    throw new IllegalStateException("ambiguous ServerData field: "
                            + serverField + " / " + fn.name);
                }
                serverField = fn.name;
            }
        }
        if (serverField == null) {
            throw new IllegalStateException("no ServerData field found in " + TARGET);
        }
        System.out.println("ServerData field: " + serverField);

        InsnList prologue = new InsnList();
        prologue.add(new VarInsnNode(Opcodes.ALOAD, 0));
        prologue.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, serverField, SERVER_DATA));
        prologue.add(new MethodInsnNode(Opcodes.INVOKESTATIC, FIX, "sanitize",
                "(" + SERVER_DATA + ")V", false));
        target.instructions.insertBefore(target.instructions.getFirst(), prologue);

        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        return cw.toByteArray();
    }

    /** Full dataflow verification of the patched class against the rest of the jar. */
    private static void verify(byte[] patched, Path jar) throws Exception {
        try (URLClassLoader loader = new URLClassLoader(
                new URL[]{jar.toUri().toURL()}, PatchJar.class.getClassLoader())) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            CheckClassAdapter.verify(new ClassReader(patched), loader, false, pw);
            String report = sw.toString();
            if (report.contains("AnalyzerException") || report.contains("Exception")) {
                throw new IllegalStateException("verification failed:\n" + report);
            }
        }
        System.out.println("bytecode verification: OK");
    }

    private static boolean isSignatureFile(String name) {
        String upper = name.toUpperCase();
        return upper.startsWith("META-INF/")
                && (upper.endsWith(".SF") || upper.endsWith(".DSA") || upper.endsWith(".RSA")
                    || upper.endsWith(".EC"));
    }

    private static void write(ZipOutputStream zos, String name, int method, byte[] data)
            throws Exception {
        ZipEntry entry = new ZipEntry(name);
        entry.setMethod(method == ZipEntry.STORED ? ZipEntry.STORED : ZipEntry.DEFLATED);
        if (entry.getMethod() == ZipEntry.STORED) {
            CRC32 crc = new CRC32();
            crc.update(data);
            entry.setSize(data.length);
            entry.setCompressedSize(data.length);
            entry.setCrc(crc.getValue());
        }
        zos.putNextEntry(entry);
        zos.write(data);
        zos.closeEntry();
    }

    private static byte[] readAll(InputStream in) throws Exception {
        try (InputStream stream = in) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int read;
            while ((read = stream.read(buf)) != -1) {
                bos.write(buf, 0, read);
            }
            return bos.toByteArray();
        }
    }
}
