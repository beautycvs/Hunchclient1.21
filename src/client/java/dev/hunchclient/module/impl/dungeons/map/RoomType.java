package dev.hunchclient.module.impl.dungeons.map;

import org.jetbrains.annotations.Nullable;

/**
 * Types of dungeon rooms
 * 1:1 Port from noamm's RoomType.kt
 */
public enum RoomType {
    BLOOD(18, 0xFF_FF0000),      // Red
    CHAMPION(82, 0xFF_FF00FF),   // Purple/Magenta (miniboss)
    ENTRANCE(30, 0xFF_00FF00),   // Green
    FAIRY(114, 0xFF_FF69B4),     // Pink
    NORMAL(63, 0xFF_8B4513),     // Brown
    PUZZLE(66, 0xFF_FF8C00),     // Orange
    RARE(74, 0xFF_FFFFFF),       // White
    TRAP(62, 0xFF_FFA500),       // Orange
    UNKNOWN(0, 0xFF_808080);     // Gray

    private final int mapColor;
    private final int displayColor;

    RoomType(int mapColor, int displayColor) {
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
     * Get room type from map color byte
     */
    @Nullable
    public static RoomType fromMapColor(int color) {
        for (RoomType type : values()) {
            if (type.mapColor == color) {
                return type;
            }
        }
        return null;
    }
}
