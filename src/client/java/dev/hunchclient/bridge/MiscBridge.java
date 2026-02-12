package dev.hunchclient.bridge;

import net.minecraft.world.phys.Vec3;

/**
 * Bridge for miscellaneous utilities referenced by mixins.
 * Methods added as needed during mixin refactoring.
 */
public final class MiscBridge {

    private static volatile boolean packetDebugEnabled = false;
    private static Object limboMusicManager;

    private MiscBridge() {}

    // === PacketDebugger ===

    public static void setPacketDebugEnabled(boolean enabled) {
        packetDebugEnabled = enabled;
    }

    public static boolean isPacketDebugEnabled() {
        return packetDebugEnabled;
    }

    // === LimboMusicManager ===

    public static void setLimboMusicManager(Object manager) {
        limboMusicManager = manager;
    }

    public static Object getLimboMusicManager() {
        return limboMusicManager;
    }

    /** Start playing limbo music (delegates to LimboMusicManager). */
    public static void startLimboMusic() {
        dev.hunchclient.util.LimboMusicManager.getInstance().startLimboMusic();
    }

    /** Check if the limbo sound event is registered. */
    public static boolean hasLimboSound() {
        return dev.hunchclient.util.TitleMusicRegistry.LIMBO_SOUND != null;
    }

    // === WindowsMediaControl ===

    /** Restart the SMTC Reader process (delegates to WindowsMediaControl.restart()). */
    public static void restartMediaControl() {
        dev.hunchclient.module.impl.misc.WindowsMediaControl.restart();
    }

    // === DungeonManager ===

    /** Notify DungeonManager of a bat death sound (delegates to DungeonManager.onBatDeathSound()). */
    public static void onBatDeathSound(Vec3 soundPos) {
        dev.hunchclient.module.impl.dungeons.secrets.DungeonManager.onBatDeathSound(soundPos);
    }
}
