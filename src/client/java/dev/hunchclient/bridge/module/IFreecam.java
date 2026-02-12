package dev.hunchclient.bridge.module;

/**
 * Bridge interface for FreecamModule - called by mixins.
 * Uses Object return type for FreeCamera to avoid direct dependency on freecam package.
 */
public interface IFreecam {
    boolean isFreecamEnabled();
    boolean isPlayerControlEnabled();
    boolean isActiveAndNotPlayerControl();
    Object getFreeCamera();
    void turnFreeCamera(double yaw, double pitch);
}
