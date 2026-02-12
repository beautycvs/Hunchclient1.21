package dev.hunchclient.util;

/**
 * Packet debugging utility
 * Used to toggle packet logging from commands
 */
public class PacketDebugger {
    private static boolean debugEnabled = false;

    public static boolean isDebugEnabled() {
        return debugEnabled;
    }

    public static void setDebugEnabled(boolean enabled) {
        debugEnabled = enabled;
    }
}
