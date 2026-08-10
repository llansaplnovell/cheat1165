import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * End-to-end check against the patched jar: give a ServerListEntryNormal a corrupt icon and call
 * prepareServerIcon(). Unpatched this throws IllegalArgumentException from netty (the reported
 * crash). Patched it must clear the icon instead - it then trips over the null Minecraft instance
 * this harness cannot provide, which is the expected proof that the Base64 decode was never reached.
 */
public final class TestPatchedClass {
    public static void main(String[] args) throws Exception {
        String good = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk"
                + "YPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==";
        String corrupt = "�" + good;

        Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        Unsafe unsafe = (Unsafe) theUnsafe.get(null);

        Class<?> entryClass = Class.forName("net.minecraft.client.gui.ServerListEntryNormal");
        Class<?> serverDataClass = Class.forName("net.minecraft.client.multiplayer.ServerData");
        Object server = serverDataClass.getConstructor(String.class, String.class, boolean.class)
                .newInstance("test", "127.0.0.1", false);
        serverDataClass.getMethod("setBase64EncodedIconData", String.class).invoke(server, corrupt);

        Object entry = unsafe.allocateInstance(entryClass);
        Field serverField = entryClass.getDeclaredField("field_148301_e");
        unsafe.putObject(entry, unsafe.objectFieldOffset(serverField), server);

        Method prepare = entryClass.getDeclaredMethod("prepareServerIcon");
        prepare.setAccessible(true);

        Throwable thrown = null;
        try {
            prepare.invoke(entry);
        } catch (InvocationTargetException e) {
            thrown = e.getCause();
        }
        System.out.println("prepareServerIcon threw: " + thrown);

        if (thrown instanceof IllegalArgumentException
                && String.valueOf(thrown.getMessage()).contains("Base64")) {
            throw new IllegalStateException("FAILED: still crashing on the Base64 decode");
        }
        Object iconAfter = serverDataClass.getMethod("getBase64EncodedIconData").invoke(server);
        if (iconAfter != null) {
            throw new IllegalStateException("FAILED: corrupt icon was not cleared: " + iconAfter);
        }
        if (!(thrown instanceof NullPointerException)) {
            throw new IllegalStateException("FAILED: unexpected throwable " + thrown);
        }
        System.out.println("OK: corrupt icon cleared, Base64 decode never reached "
                + "(NPE is this harness's missing Minecraft instance, not the icon)");
    }
}
