package dev.hunchclient.module.impl.dungeons.devmap;


public class MapEnums {

    /**
     * Room types with priority for rendering
     */
    public enum RoomType {
        BLOOD(0),
        ENTRANCE(10),
        PUZZLE(20),
        RARE(30),
        YELLOW(40),
        TRAP(50),
        UNKNOWN(60),
        FAIRY(70),
        NORMAL(80);

        public final int priority;

        RoomType(int priority) {
            this.priority = priority;
        }

        public static RoomType byName(String name) {
            if (name == null) return UNKNOWN;
            try {
                return valueOf(name.toUpperCase());
            } catch (IllegalArgumentException e) {
                return UNKNOWN;
            }
        }
    }

    /**
     * Checkmark types for room completion status
     */
    public enum CheckmarkType {
        NONE,
        WHITE,
        GREEN,
        FAILED,
        UNEXPLORED
    }

    /**
     * Door types
     */
    public enum DoorType {
        NORMAL,
        WITHER,
        BLOOD,
        ENTRANCE
    }

    /**
     * Room shapes
     */
    public enum ShapeType {
        UNKNOWN,
        SHAPE_1X1,
        SHAPE_1X2,
        SHAPE_1X3,
        SHAPE_1X4,
        SHAPE_2X2,
        SHAPE_L
    }

    /**
     * Clear types for miniboss detection
     */
    public enum ClearType {
        MOB,
        MINIBOSS,
        OTHER
    }

    /**
     * Floor types with dungeon dimensions
     */
    public enum FloorType {
        NONE(0, false, "", 0, 0, "", 1.0, 600),

        ENTRANCE(0, false, "E", 4, 4, "Entrance", 0.3, 1200),

        F1(1, false, "F1", 4, 5, "F1", 0.3, 600),
        F2(2, false, "F2", 5, 5, "F2", 0.4, 600),
        F3(3, false, "F3", 5, 5, "F3", 0.5, 600),
        F4(4, false, "F4", 6, 5, "F4", 0.6, 720),
        F5(5, false, "F5", 6, 6, "F5", 0.7, 600),
        F6(6, false, "F6", 6, 6, "F6", 0.85, 720),
        F7(7, false, "F7", 6, 6, "F7", 1.0, 840),

        M1(1, true, "M1", 4, 5, "M1", 1.0, 480),
        M2(2, true, "M2", 5, 5, "M2", 1.0, 480),
        M3(3, true, "M3", 5, 5, "M3", 1.0, 480),
        M4(4, true, "M4", 6, 5, "M4", 1.0, 480),
        M5(5, true, "M5", 6, 6, "M5", 1.0, 480),
        M6(6, true, "M6", 6, 6, "M6", 1.0, 480),
        M7(7, true, "M7", 6, 6, "M7", 1.0, 900);

        public final int floorNum;
        public final boolean masterMode;
        public final String shortName;
        public final int roomsW;
        public final int roomsH;
        public final String longName;
        public final double requiredPercent;
        public final int requiredSpeed;
        public final int maxDim;

        FloorType(int floorNum, boolean masterMode, String shortName, int roomsW, int roomsH,
                  String longName, double requiredPercent, int requiredSpeed) {
            this.floorNum = floorNum;
            this.masterMode = masterMode;
            this.shortName = shortName;
            this.roomsW = roomsW;
            this.roomsH = roomsH;
            this.longName = longName;
            this.requiredPercent = requiredPercent;
            this.requiredSpeed = requiredSpeed;
            this.maxDim = Math.max(roomsW, roomsH);
        }

        public static FloorType fromName(String name) {
            if (name == null) return NONE;
            for (FloorType floor : values()) {
                if (floor.shortName.equalsIgnoreCase(name)) {
                    return floor;
                }
            }
            return NONE;
        }
    }

    /**
     * Map colors for reading the vanilla map
     */
    public enum MapColor {
        EMPTY((byte) 0),

        CHECK_WHITE((byte) 34),
        CHECK_GREEN((byte) 30),
        CHECK_FAIL((byte) 18),
        CHECK_UNKNOWN((byte) 119),

        ROOM_ENTRANCE((byte) 30),
        ROOM_NORMAL((byte) 63),
        ROOM_UNOPENED((byte) 85),
        ROOM_TRAP((byte) 62),
        ROOM_BOSS((byte) 74),
        ROOM_PUZZLE((byte) 66),
        ROOM_FAIRY((byte) 82),
        ROOM_BLOOD((byte) 18),

        DOOR_WITHER((byte) 119),
        DOOR_BLOOD((byte) 18);

        public final byte color;

        MapColor(byte color) {
            this.color = color;
        }
    }

    /**
     * Dungeon map render colors
     */
    public enum DungeonMapColor {
        ROOM_ENTRANCE,
        ROOM_NORMAL,
        ROOM_MINIBOSS,
        ROOM_FAIRY,
        ROOM_BLOOD,
        ROOM_PUZZLE,
        ROOM_TRAP,
        ROOM_YELLOW,
        ROOM_RARE,
        ROOM_UNKNOWN,
        BACKGROUND,

        DOOR_ENTRANCE,
        DOOR_WITHER,
        DOOR_BLOOD
    }

    /**
     * Icon/text alignment in rooms
     */
    public enum RoomInfoAlignment {
        TOP_LEFT("Top Left"),
        TOP_RIGHT("Top Right"),
        BOTTOM_LEFT("Bottom Left"),
        BOTTOM_RIGHT("Bottom Right"),
        CENTER("Center");

        public final String displayName;

        RoomInfoAlignment(String displayName) {
            this.displayName = displayName;
        }

        public static RoomInfoAlignment fromName(String name) {
            if (name == null) return CENTER;
            for (RoomInfoAlignment align : values()) {
                if (align.displayName.equalsIgnoreCase(name)) {
                    return align;
                }
            }
            return CENTER;
        }
    }
}
