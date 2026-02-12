package dev.hunchclient.module.impl.dungeons.parser;

import dev.hunchclient.module.impl.dungeons.map.*;
import dev.hunchclient.module.impl.dungeons.scanner.PhysicalDungeonScanner;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parses map colors from the hotbar dungeon map to detect room states
 * 1:1 Port from noamm's HotbarMapColorParser.kt
 *
 * This reads the map item's color data (128x128 bytes) and translates it to room states:
 * - Color 85/119 = UNOPENED (room exists but door closed)
 * - Color 34 = CLEARED
 * - Color 30 = GREEN
 * - Color 18 = DISCOVERED/FAILED
 */
public class MapColorParser {
    private static final Logger LOGGER = LoggerFactory.getLogger(MapColorParser.class);

    // Map color data (11x11 grid sampling from 128x128 map)
    private byte[] centerColors = new byte[121];
    private byte[] sideColors = new byte[121];
    private Tile[] cachedTiles = new Tile[121];

    // Map dimensions (calibrated based on map size)
    private int halfRoom = 8;      // mapRoomSize / 2
    private int halfTile = 10;     // halfRoom + 2 (gap)
    private int quarterRoom = 4;   // halfRoom / 2
    private int startX = 13;       // startCorner.x + halfRoom
    private int startY = 13;       // startCorner.y + halfRoom

    // Map room size (16 or 18, depends on dungeon)
    private int mapRoomSize = 16;

    /**
     * Calibrate the parser for current map size
     */
    public void calibrate(int mapRoomSize) {
        this.mapRoomSize = mapRoomSize;
        this.halfRoom = mapRoomSize / 2;
        this.halfTile = halfRoom + 2;
        this.quarterRoom = halfRoom / 2;
        this.startX = 5 + halfRoom;  // Assuming startCorner = (5, 5)
        this.startY = 5 + halfRoom;

        // Reset caches
        centerColors = new byte[121];
        sideColors = new byte[121];
        cachedTiles = new Tile[121];

        LOGGER.debug("[MapColorParser] Calibrated: mapRoomSize={}, halfRoom={}, halfTile={}",
                    mapRoomSize, halfRoom, halfTile);
    }

    /**
     * Update map color data from MapState
     * Samples the 128x128 map into 11x11 grid
     */
    public void updateMap(MapItemSavedData mapData) {
        if (mapData == null || mapData.colors == null) {
            return;
        }

        // Reset cached tiles
        cachedTiles = new Tile[121];

        // Sample 11x11 grid from 128x128 map
        for (int y = 0; y <= 10; y++) {
            for (int x = 0; x <= 10; x++) {
                int mapX = startX + x * halfTile;
                int mapY = startY + y * halfTile;

                // Bounds check
                if (mapX >= 128 || mapY >= 128) {
                    continue;
                }

                // Sample center color
                int centerIndex = mapY * 128 + mapX;
                centerColors[y * 11 + x] = mapData.colors[centerIndex];

                // Sample side color (for room type detection)
                int sideIndex;
                boolean isRoom = (x % 2 == 0) && (y % 2 == 0);

                if (isRoom) {
                    // For rooms, sample top-left corner
                    int topX = mapX - halfRoom;
                    int topY = mapY - halfRoom;
                    sideIndex = topY * 128 + topX;
                } else {
                    // For corridors/doors
                    boolean horizontal = (y % 2 == 1);
                    if (horizontal) {
                        sideIndex = mapY * 128 + (mapX - 4);
                    } else {
                        sideIndex = (mapY - 4) * 128 + mapX;
                    }
                }

                if (sideIndex >= 0 && sideIndex < mapData.colors.length) {
                    sideColors[y * 11 + x] = mapData.colors[sideIndex];
                }
            }
        }
    }

    /**
     * Get tile at grid position (lazy-cached)
     */
    public Tile getTile(int arrayX, int arrayY) {
        int index = arrayY * 11 + arrayX;
        if (index < 0 || index >= 121) {
            return new Unknown(0, 0);
        }

        // Return cached if available
        Tile cached = cachedTiles[index];
        if (cached != null) {
            return cached;
        }

        // Calculate world position
        int xPos = PhysicalDungeonScanner.START_X + arrayX * (PhysicalDungeonScanner.ROOM_SIZE >> 1);
        int zPos = PhysicalDungeonScanner.START_Z + arrayY * (PhysicalDungeonScanner.ROOM_SIZE >> 1);

        // Scan tile from colors
        Tile scanned = scanTile(arrayX, arrayY, xPos, zPos);
        cachedTiles[index] = scanned;

        return scanned;
    }

    /**
     * Scan a tile from map colors and determine its type and state
     */
    private Tile scanTile(int arrayX, int arrayY, int worldX, int worldZ) {
        int index = arrayY * 11 + arrayX;
        int centerColor = centerColors[index] & 0xFF;
        int sideColor = sideColors[index] & 0xFF;

        // No data = unknown
        if (centerColor == 0) {
            return new Unknown(worldX, worldZ);
        }

        boolean rowEven = (arrayY % 2 == 0);
        boolean columnEven = (arrayX % 2 == 0);

        // Room tile (even row AND even column)
        if (rowEven && columnEven) {
            RoomType type = RoomType.fromMapColor(sideColor);
            if (type == null) {
                return new Unknown(worldX, worldZ);
            }

            Room room = new Room(worldX, worldZ, RoomData.createUnknown(type));

            // Determine state from center color
            RoomState state = RoomState.UNDISCOVERED;

            if (centerColor == 18) {
                // Red color
                state = (type == RoomType.BLOOD) ? RoomState.DISCOVERED :
                        (type == RoomType.PUZZLE) ? RoomState.FAILED :
                        RoomState.UNDISCOVERED;
            } else if (centerColor == 30) {
                // Green color
                state = (type == RoomType.ENTRANCE) ? RoomState.DISCOVERED : RoomState.GREEN;
            } else if (centerColor == 34) {
                // Cleared
                state = RoomState.CLEARED;
            } else if (centerColor == 85 || centerColor == 119) {
                // Gray/Dark = UNOPENED (this is the key!)
                state = RoomState.UNOPENED;
            } else {
                // Default discovered
                state = RoomState.DISCOVERED;
            }

            room.setState(state);
            return room;
        }

        // Door or connector (odd row OR odd column)
        if (sideColor == 0) {
            // It's a door
            DoorType doorType = DoorType.fromMapColor(centerColor);
            if (doorType == null) {
                return new Unknown(worldX, worldZ);
            }

            Door door = new Door(worldX, worldZ, doorType);
            door.setState(centerColor == 85 ? RoomState.UNOPENED : RoomState.DISCOVERED);
            return door;
        } else {
            // It's a room separator (for large rooms)
            RoomType type = RoomType.fromMapColor(sideColor);
            if (type == null) {
                return new Unknown(worldX, worldZ);
            }

            Room room = new Room(worldX, worldZ, RoomData.createUnknown(type));
            room.setState(RoomState.DISCOVERED);
            room.setSeparator(true);
            return room;
        }
    }

    /**
     * Reset the parser
     */
    public void reset() {
        centerColors = new byte[121];
        sideColors = new byte[121];
        cachedTiles = new Tile[121];
    }
}
