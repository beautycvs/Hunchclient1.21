package dev.hunchclient.bridge;

import dev.hunchclient.freecam.FreecamState;

public final class FreecamBridge {

    private static volatile boolean active = false;

    private FreecamBridge() {}

    public static void setActive(boolean value) {
        active = value;
    }

    public static boolean isActive() {
        return active;
    }

    public static double getSavedX() { return FreecamState.getSavedX(); }
    public static double getSavedY() { return FreecamState.getSavedY(); }
    public static double getSavedZ() { return FreecamState.getSavedZ(); }
    public static float getSavedYaw() { return FreecamState.getSavedYaw(); }
    public static float getSavedPitch() { return FreecamState.getSavedPitch(); }
    public static boolean isSavedOnGround() { return FreecamState.isSavedOnGround(); }
    public static boolean isSavedHorizontalCollision() { return FreecamState.isSavedHorizontalCollision(); }
}
