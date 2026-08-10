import com.google.common.base.Charsets;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.base64.Base64;
import net.minecraft.client.gui.ServerIconFix;

import java.lang.reflect.Method;
import java.util.Locale;

/** Reproduces the reported crash against the jar's own netty, then checks the guard catches it. */
public final class TestFix {
    public static void main(String[] args) throws Exception {
        // A 1x1 PNG, valid Base64 - the shape of a well-formed favicon payload.
        String good = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk"
                + "YPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==";

        // The crash report says "bad Base64 input character at 1: 191 (decimal)". Byte 191 (0xBF)
        // at index 1 of the UTF-8 encoding is what U+FFFD (EF BF BD) - the replacement character
        // binary favicon data turns into when it is run through a charset conversion - looks like.
        String corrupt = "�" + good;
        byte[] utf8 = corrupt.getBytes("UTF-8");
        System.out.printf("corrupt icon first bytes: %d, %d, %d%n",
                utf8[0] & 0xFF, utf8[1] & 0xFF, utf8[2] & 0xFF);

        System.out.println("-- netty decode, unpatched path --");
        check("valid icon", good, true);
        check("corrupt icon", corrupt, false);
        check("data URI icon", "data:image/png;base64," + good, false);

        System.out.println("-- ServerIconFix guard --");
        expect("valid icon accepted", ServerIconFix.isDecodable(good), true);
        expect("corrupt icon rejected", ServerIconFix.isDecodable(corrupt), false);
        expect("data URI stripped then accepted",
                ServerIconFix.isDecodable(ServerIconFix.stripDataUri("data:image/png;base64," + good)),
                true);
        expect("valid icon left untouched",
                ServerIconFix.stripDataUri(good).equals(good), true);
        expect("wrapped/whitespaced icon accepted",
                ServerIconFix.isDecodable(good.substring(0, 20) + "\n  " + good.substring(20)), true);
        expect("empty icon accepted (handled by vanilla try/catch)",
                ServerIconFix.isDecodable(""), true);

        // The guard must reject exactly what netty rejects, for every 16-bit char.
        for (char c = 0; c < 0xFFFF; c++) {
            String candidate = good + c;
            boolean nettyOk = decodes(candidate, false);
            boolean guardOk = ServerIconFix.isDecodable(candidate);
            if (guardOk && !nettyOk) {
                throw new IllegalStateException("guard accepts char netty rejects: " + (int) c);
            }
        }
        System.out.println("OK: guard never accepts an icon netty would throw on");

        // Sanity: the patched call site really nulls a corrupt icon on a real ServerData.
        Class<?> serverDataClass = Class.forName("net.minecraft.client.multiplayer.ServerData");
        Object server = serverDataClass.getConstructor(String.class, String.class, boolean.class)
                .newInstance("test", "127.0.0.1", false);
        Method setter = serverDataClass.getMethod("setBase64EncodedIconData", String.class);
        Method getter = serverDataClass.getMethod("getBase64EncodedIconData");
        setter.invoke(server, corrupt);
        ServerIconFix.sanitize((net.minecraft.client.multiplayer.ServerData) server);
        expect("corrupt icon cleared on ServerData", getter.invoke(server) == null, true);
        setter.invoke(server, good);
        ServerIconFix.sanitize((net.minecraft.client.multiplayer.ServerData) server);
        expect("valid icon kept on ServerData", good.equals(getter.invoke(server)), true);

        System.out.println("all checks passed");
    }

    private static void check(String label, String icon, boolean expectOk) {
        boolean ok = decodes(icon, true);
        System.out.printf("  %-16s decodes=%s%n", label, ok);
        expect(label + " netty behaviour", ok, expectOk);
    }

    private static boolean decodes(String icon, boolean verbose) {
        ByteBuf buf = Unpooled.copiedBuffer(icon, Charsets.UTF_8);
        try {
            Base64.decode(buf).release();
            return true;
        } catch (RuntimeException e) {
            if (verbose) {
                System.out.println("    " + e.getClass().getName() + ": " + e.getMessage());
            }
            return false;
        } finally {
            buf.release();
        }
    }

    private static void expect(String what, boolean actual, boolean expected) {
        if (actual != expected) {
            throw new IllegalStateException("FAILED: " + what.toLowerCase(Locale.ROOT)
                    + " (expected " + expected + ", got " + actual + ")");
        }
    }
}
