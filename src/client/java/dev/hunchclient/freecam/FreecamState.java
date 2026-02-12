package dev.hunchclient.freecam;

import net.minecraft.client.player.LocalPlayer;

/**
 * Stores the player's saved position/state for freecam.
 * Used by FreecamLocalPlayerMixin to send fake AFK packets.
 */
public class FreecamState {

    private static double savedX, savedY, savedZ;
    private static float savedYaw, savedPitch;
    private static boolean savedOnGround;
    private static boolean savedHorizontalCollision;

    /**
     * Save the player's current position when freecam is enabled.
     */
    public static void savePosition(LocalPlayer player) {
        savedX = player.getX();
        savedY = player.getY();
        savedZ = player.getZ();
        savedYaw = player.getYRot();
        savedPitch = player.getXRot();
        savedOnGround = player.onGround();
        savedHorizontalCollision = player.horizontalCollision;
    }

    /**
     * Freeze the player's rotation to saved values.
     * Call this every tick to prevent rotation changes from triggering BadPacketsR.
     */
    public static void freezeRotation(LocalPlayer player) {
        player.setYRot(savedYaw);
        player.setXRot(savedPitch);
        player.yRotO = savedYaw;
        player.xRotO = savedPitch;
        player.setYHeadRot(savedYaw);
        player.yHeadRotO = savedYaw;
        player.yBodyRot = savedYaw;
        player.yBodyRotO = savedYaw;
    }

    public static double getSavedX() { return savedX; }
    public static double getSavedY() { return savedY; }
    public static double getSavedZ() { return savedZ; }
    public static float getSavedYaw() { return savedYaw; }
    public static float getSavedPitch() { return savedPitch; }
    public static boolean isSavedOnGround() { return savedOnGround; }
    public static boolean isSavedHorizontalCollision() { return savedHorizontalCollision; }
}
