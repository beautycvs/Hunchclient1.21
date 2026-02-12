package dev.hunchclient.module.impl.dungeons;

/**
 * Dungeon class enum with colors matching Skyblocker
 */
public enum DungeonClass {
    UNKNOWN("Unknown", 0xFFAAAAAA),
    HEALER("Healer", 0xFF820dd1),
    MAGE("Mage", 0xFF36c6e3),
    BERSERK("Berserk", 0xFFfa5b16),
    ARCHER("Archer", 0xFFed240e),
    TANK("Tank", 0xFF138717);

    private final String displayName;
    private final int color; // ARGB color

    DungeonClass(String displayName, int color) {
        this.displayName = displayName;
        this.color = color;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getColor() {
        return color;
    }

    /**
     * Parse dungeon class from string (from tab list)
     */
    public static DungeonClass from(String name) {
        if (name == null) return UNKNOWN;

        for (DungeonClass dungeonClass : values()) {
            if (dungeonClass.displayName.equalsIgnoreCase(name)) {
                return dungeonClass;
            }
        }
        return UNKNOWN;
    }
}
