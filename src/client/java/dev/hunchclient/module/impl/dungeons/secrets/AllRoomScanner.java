package dev.hunchclient.module.impl.dungeons.secrets;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2i;
import org.joml.Vector2ic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * AllRoomScanner - Scans the entire dungeon map to find ALL rooms
 * This includes rooms that haven't been explored yet (gray on map)
 */
public class AllRoomScanner {
    private static final Logger LOGGER = LoggerFactory.getLogger(AllRoomScanner.class);

    /**
     * Information about a discovered room from the map
     */
    public static class RoomInfo {
        public final Vector2ic mapPosition;           // Top-left corner on map
        public final Vector2ic physicalPosition;      // Physical northwest corner
        public final Room.Type roomType;
        public final Vector2ic[] mapSegments;         // All map segments for multi-segment rooms
        public final Vector2ic[] physicalSegments;    // All physical segments
        public final boolean discovered;              // Whether player has explored this room
        public final int estimatedSize;               // 1x1, 1x2, etc.

        public RoomInfo(Vector2ic mapPosition, Vector2ic physicalPosition, Room.Type roomType,
                       Vector2ic[] mapSegments, Vector2ic[] physicalSegments, boolean discovered, int estimatedSize) {
            this.mapPosition = mapPosition;
            this.physicalPosition = physicalPosition;
            this.roomType = roomType;
            this.mapSegments = mapSegments;
            this.physicalSegments = physicalSegments;
            this.discovered = discovered;
            this.estimatedSize = estimatedSize;
        }

        @Override
        public String toString() {
            return String.format("RoomInfo{type=%s, mapPos=%s, physicalPos=%s, discovered=%s, segments=%d}",
                roomType, mapPosition, physicalPosition, discovered, mapSegments.length);
        }
    }

    private final List<RoomInfo> allRooms = new ArrayList<>();
    private final Set<Vector2ic> scannedMapPositions = new HashSet<>();
    private boolean scanComplete = false;
    private CompletableFuture<Void> scanTask = null;

    /**
     * Start scanning the entire map asynchronously
     */
    public CompletableFuture<Void> scanMap(@NotNull MapItemSavedData map, @NotNull Vector2ic mapEntrancePos,
                                           int mapRoomSize, @NotNull Vector2ic physicalEntrancePos) {
        if (scanTask != null && !scanTask.isDone()) {
            return scanTask; // Already scanning
        }

        scanTask = CompletableFuture.runAsync(() -> {
            long startTime = System.currentTimeMillis();
            allRooms.clear();
            scannedMapPositions.clear();
            scanComplete = false;

            try {
                performFullScan(map, mapEntrancePos, mapRoomSize, physicalEntrancePos);
                scanComplete = true;
                long elapsed = System.currentTimeMillis() - startTime;
                LOGGER.info("[AllRoomScanner] Scan complete! Found {} rooms in {} ms", allRooms.size(), elapsed);
            } catch (Exception e) {
                LOGGER.error("[AllRoomScanner] Error during map scan", e);
            }
        });

        return scanTask;
    }

    /**
     * Main scanning logic - iterates through the entire 128x128 map grid
     */
    private void performFullScan(MapItemSavedData map, Vector2ic mapEntrancePos, int mapRoomSize, Vector2ic physicalEntrancePos) {
        int mapRoomSizeWithGap = mapRoomSize + 4;

        // Calculate the grid offset based on entrance position
        Vector2i nwMostRoom = DungeonMapUtils.getMapPosForNWMostRoom(mapEntrancePos, mapRoomSize);

        LOGGER.info("[AllRoomScanner] Starting ILLEGAL scan (show all grid positions). NW-most room at {}, room size {}", nwMostRoom, mapRoomSize);

        // Scan the entire potential dungeon area (usually 6x6 or 7x7 grid)
        // ILLEGAL MODE: Assume ALL grid positions are rooms (like IllegalMap by Bloom)
        for (int mapY = nwMostRoom.y; mapY < 128; mapY += mapRoomSizeWithGap) {
            for (int mapX = nwMostRoom.x; mapX < 128; mapX += mapRoomSizeWithGap) {
                Vector2ic mapPos = new Vector2i(mapX, mapY);

                // Skip if we already processed this position
                if (scannedMapPositions.contains(mapPos)) {
                    continue;
                }

                // Check what's at this map position
                Room.Type roomType = DungeonMapUtils.getRoomType(map, mapPos);

                if (roomType != null && roomType != Room.Type.UNKNOWN) {
                    // Found a discovered room
                    processDiscoveredRoom(map, mapPos, mapRoomSize, physicalEntrancePos, mapEntrancePos, roomType);
                } else {
                    // ILLEGAL MODE: Mark ALL grid positions as undiscovered rooms
                    // No validation needed - just assume there's a room here
                    processUndiscoveredRoom(map, mapPos, mapRoomSize, physicalEntrancePos, mapEntrancePos);
                }
            }
        }
    }

    /**
     * Process a room that's visible on the map (player has been near it)
     */
    private void processDiscoveredRoom(MapItemSavedData map, Vector2ic mapPos, int mapRoomSize,
                                      Vector2ic physicalEntrancePos, Vector2ic mapEntrancePos, Room.Type roomType) {
        scannedMapPositions.add(mapPos);

        // Get all connected segments for this room (handles 1x2, L-shape, etc.)
        Vector2ic[] mapSegments = DungeonMapUtils.getRoomSegments(map, mapPos, mapRoomSize, roomType.color);

        // Mark all segments as scanned
        for (Vector2ic segment : mapSegments) {
            scannedMapPositions.add(segment);
        }

        // Convert to physical coordinates
        Vector2ic[] physicalSegments = DungeonMapUtils.getPhysicalPosFromMap(
            mapEntrancePos, mapRoomSize, physicalEntrancePos, mapSegments.clone()
        );

        // Primary physical position is the first segment
        Vector2ic physicalPos = physicalSegments[0];

        RoomInfo roomInfo = new RoomInfo(
            mapPos,
            physicalPos,
            roomType,
            mapSegments,
            physicalSegments,
            true, // discovered
            mapSegments.length
        );

        allRooms.add(roomInfo);
        LOGGER.debug("[AllRoomScanner] Found discovered room: {}", roomInfo);
    }

    /**
     * Process a potential undiscovered room (gray/black on map but in valid grid position)
     */
    private void processUndiscoveredRoom(MapItemSavedData map, Vector2ic mapPos, int mapRoomSize,
                                        Vector2ic physicalEntrancePos, Vector2ic mapEntrancePos) {
        scannedMapPositions.add(mapPos);

        // For undiscovered rooms, we assume it's a single segment (we can't see connected rooms yet)
        Vector2ic[] mapSegments = new Vector2ic[]{ mapPos };

        // Convert to physical coordinates
        Vector2ic physicalPos = DungeonMapUtils.getPhysicalPosFromMap(
            mapEntrancePos, mapRoomSize, physicalEntrancePos, mapPos
        );

        Vector2ic[] physicalSegments = new Vector2ic[]{ physicalPos };

        // We don't know the exact type yet, mark as UNKNOWN
        RoomInfo roomInfo = new RoomInfo(
            mapPos,
            physicalPos,
            Room.Type.UNKNOWN,
            mapSegments,
            physicalSegments,
            false, // not discovered
            1 // assume 1x1 until discovered
        );

        allRooms.add(roomInfo);
        LOGGER.debug("[AllRoomScanner] Found undiscovered room: {}", roomInfo);
    }

    /**
     * Check if a position could be a room based on surrounding context
     * This helps filter out false positives in the grid
     */
    private boolean isPotentialRoom(MapItemSavedData map, Vector2ic mapPos, int mapRoomSize) {
        // Check if there are nearby discovered rooms (within 1-2 grid cells)
        int mapRoomSizeWithGap = mapRoomSize + 4;

        // Look in all 8 directions (and center)
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                Vector2ic checkPos = new Vector2i(
                    mapPos.x() + dx * mapRoomSizeWithGap,
                    mapPos.y() + dy * mapRoomSizeWithGap
                );

                Room.Type type = DungeonMapUtils.getRoomType(map, checkPos);
                if (type != null && type != Room.Type.UNKNOWN) {
                    return true; // There's a discovered room nearby, so this could be real
                }
            }
        }

        return false; // No nearby rooms, probably just empty space
    }

    /**
     * Get all discovered rooms
     */
    public List<RoomInfo> getAllRooms() {
        return new ArrayList<>(allRooms);
    }

    /**
     * Get only undiscovered rooms
     */
    public List<RoomInfo> getUndiscoveredRooms() {
        return allRooms.stream()
            .filter(room -> !room.discovered)
            .toList();
    }

    /**
     * Get only discovered rooms
     */
    public List<RoomInfo> getDiscoveredRooms() {
        return allRooms.stream()
            .filter(room -> room.discovered)
            .toList();
    }

    /**
     * Check if scan is complete
     */
    public boolean isScanComplete() {
        return scanComplete && (scanTask == null || scanTask.isDone());
    }

    /**
     * Get room at a specific physical position
     */
    @Nullable
    public RoomInfo getRoomAt(Vector2ic physicalPos) {
        for (RoomInfo room : allRooms) {
            for (Vector2ic segment : room.physicalSegments) {
                if (segment.equals(physicalPos)) {
                    return room;
                }
            }
        }
        return null;
    }

    /**
     * Get statistics about the scan
     */
    public String getStats() {
        long discovered = allRooms.stream().filter(r -> r.discovered).count();
        long undiscovered = allRooms.stream().filter(r -> !r.discovered).count();

        return String.format("Total: %d | Discovered: %d | Undiscovered: %d | Complete: %s",
            allRooms.size(), discovered, undiscovered, scanComplete);
    }

    /**
     * Reset the scanner
     */
    public void reset() {
        allRooms.clear();
        scannedMapPositions.clear();
        scanComplete = false;
        scanTask = null;
    }
}
