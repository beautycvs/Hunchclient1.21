package dev.hunchclient.module.impl.dungeons.map;

import org.jetbrains.annotations.Nullable;

/**
 * Types of doors between rooms
 * 1:1 Port from noamm's DoorType.kt
 */
public enum DoorType {
    BLOOD(18, 0xFF_FF0000),      // Blood door (red)
    ENTRANCE(30, 0xFF_00FF00),   // Entrance door (green)
    NORMAL(0, 0xFF_8B4513),      // Normal door (brown)
    WITHER(119, 0xFF_000000);    // Wither door (black)

    private final int mapColor;
    private final int displayColor;

    DoorType(int mapColor, int displayColor) {
        this.mapColor = mapColor;
        this.displayColor = displayColor;
    }

    public int getMapColor() {
        return mapColor;
    }

    public int getDisplayColor() {
        return displayColor;
    }

    /**
     * Get door type from map color byte
     */
    @Nullable
    public static DoorType fromMapColor(int color) {
        for (DoorType type : values()) {
            if (type.mapColor == color) {
                return type;
            }
        }
        return null;
    }
}
