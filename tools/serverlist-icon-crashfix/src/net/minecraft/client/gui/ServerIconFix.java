package net.minecraft.client.gui;

import net.minecraft.client.multiplayer.ServerData;

/**
 * Guards {@code ServerListEntryNormal.prepareServerIcon()} against server icons that are not
 * valid Base64.
 *
 * <p>Vanilla 1.8.8 calls {@code Base64.decode(...)} outside of its own try/catch, so a server
 * whose favicon contains a character outside the Base64 alphabet makes netty throw
 * {@code IllegalArgumentException: bad Base64 input character}, which escapes through
 * {@code drawEntry} and crashes the multiplayer screen. Nulling the icon before the decode turns
 * that crash into the "unknown server" placeholder, and because the caller saves the server list
 * right afterwards the bad icon is also removed from servers.dat.
 */
public final class ServerIconFix {
    private static final String DATA_URI_PREFIX = "data:image/png;base64,";

    private ServerIconFix() {
    }

    /** Invoked at the top of {@code ServerListEntryNormal.prepareServerIcon()}. */
    public static void sanitize(ServerData server) {
        if (server == null) {
            return;
        }

        String icon;
        try {
            icon = server.getBase64EncodedIconData();
        } catch (Throwable t) {
            return;
        }
        if (icon == null) {
            return;
        }

        String stripped = stripDataUri(icon);
        if (!isDecodable(stripped)) {
            server.setBase64EncodedIconData(null);
        } else if (!stripped.equals(icon)) {
            server.setBase64EncodedIconData(stripped);
        }
    }

    /** Some servers send the favicon as a full data URI; the prefix itself is not Base64. */
    public static String stripDataUri(String icon) {
        String s = icon;
        if (s.regionMatches(true, 0, DATA_URI_PREFIX, 0, DATA_URI_PREFIX.length())) {
            s = s.substring(DATA_URI_PREFIX.length());
        }
        return s;
    }

    /**
     * Mirrors netty's {@code Base64.decode} character rules: whitespace is skipped, '=' ends the
     * payload, and anything else outside the standard alphabet makes it throw. Malformed PNG data
     * is deliberately not rejected here - the game already handles that in its own try/catch.
     */
    public static boolean isDecodable(String icon) {
        for (int i = 0; i < icon.length(); i++) {
            char c = icon.charAt(i);
            if (c == '\n' || c == '\r' || c == ' ' || c == '\t' || c == '=') {
                continue;
            }
            boolean base64 = (c >= 'A' && c <= 'Z')
                    || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '+'
                    || c == '/';
            if (!base64) {
                return false;
            }
        }
        return true;
    }
}
