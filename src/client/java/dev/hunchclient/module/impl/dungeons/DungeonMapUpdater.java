package dev.hunchclient.module.impl.dungeons;

import dev.hunchclient.module.impl.dungeons.map.*;
import dev.hunchclient.module.impl.dungeons.parser.MapColorParser;
import dev.hunchclient.module.impl.dungeons.scanner.ScanUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Updates room states by syncing Physical Scanner and Map Parser data
 * 1:1 Port from noamm's MapUpdater.kt
 *
 * This is where the magic happens:
 * - Physical scanner finds rooms before they're opened
 * - Map parser detects state changes (UNOPENED → DISCOVERED → CLEARED → GREEN)
 * - This class syncs the two sources, updating room states
 */
public class DungeonMapUpdater {
    private static final Logger LOGGER = LoggerFactory.getLogger(DungeonMapUpdater.class);

    private final MapColorParser mapColorParser;

    public DungeonMapUpdater(MapColorParser mapColorParser) {
        this.mapColorParser = mapColorParser;
    }

    /**
     * Update all rooms by syncing physical scan and map data
     */
    public void updateRooms(MapItemSavedData mapData, Tile[] dungeonList) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }

        // Don't update in boss room
        // TODO: Check if in boss room

        // Update map color parser
        mapColorParser.updateMap(mapData);

        // Debug: Log state updates
        int unopenedCount = 0;

        // Sync all tiles (11x11 grid)
        for (int x = 0; x <= 10; x++) {
            for (int z = 0; z <= 10; z++) {
                int index = z * 11 + x;
                Tile physicalTile = dungeonList[index];
                Tile mapTile = mapColorParser.getTile(x, z);

                // If physical scanner hasn't found this yet, use map data
                if (physicalTile instanceof Unknown) {
                    dungeonList[index] = mapTile;

                    // If map shows a room, try to connect it to adjacent known rooms
                    if (mapTile instanceof Room) {
                        Room mapRoom = (Room) mapTile;
                        // TODO: Connect to adjacent rooms for large room detection
                    }
                    continue;
                }

                // Sync state: map state has priority if it's "better" (lower ordinal = better)
                if (mapTile.getState().ordinal() < physicalTile.getState().ordinal()) {
                    RoomState oldState = physicalTile.getState();
                    RoomState newState = mapTile.getState();
                    physicalTile.setState(newState);

                    // Debug log state changes to UNOPENED
                    if (newState == RoomState.UNOPENED && physicalTile instanceof Room) {
                        unopenedCount++;
                        LOGGER.debug("[DungeonMapUpdater] Set room at ({},{}) to UNOPENED (was: {})",
                            x, z, oldState);
                    }
                }

                // Update door types (e.g., wither door detection)
                if (mapTile instanceof Door && physicalTile instanceof Door) {
                    Door mapDoor = (Door) mapTile;
                    Door physicalDoor = (Door) physicalTile;

                    // Wither door detection from map
                    if (mapDoor.getType() == DoorType.WITHER && physicalDoor.getType() != DoorType.WITHER) {
                        physicalDoor.setType(mapDoor.getType());
                    }
                }

                // Update door opened state
                if (physicalTile instanceof Door) {
                    Door door = (Door) physicalTile;
                    DoorType doorType = door.getType();

                    // Check special doors (entrance, wither, blood)
                    if (doorType == DoorType.ENTRANCE || doorType == DoorType.WITHER || doorType == DoorType.BLOOD) {
                        // If map shows wither door, it's closed
                        if (mapTile instanceof Door && ((Door) mapTile).getType() == DoorType.WITHER) {
                            door.setOpened(false);
                        }
                        // Otherwise check physical block
                        else if (!door.isOpened()) {
                            ChunkPos chunkPos = new ChunkPos(door.getX() >> 4, door.getZ() >> 4);
                            ChunkAccess chunk = mc.level.getChunk(chunkPos.x, chunkPos.z);

                            if (chunk != null) {
                                BlockPos doorPos = new BlockPos(door.getX(), 69, door.getZ());
                                if (mc.level.getBlockState(doorPos).getBlock() == Blocks.AIR) {
                                    door.setOpened(true);
                                }
                            }
                            // If chunk not loaded but map shows discovered, door is opened
                            else if (mapTile instanceof Door && mapTile.getState() == RoomState.DISCOVERED) {
                                // Special handling for blood door
                                if (doorType == DoorType.BLOOD) {
                                    // TODO: Check if blood room is cleared
                                } else {
                                    door.setOpened(true);
                                }
                            }
                        }
                    }
                }
            }
        }

        // Debug log if we found unopened rooms
        if (unopenedCount > 0) {
            LOGGER.info("[DungeonMapUpdater] Updated {} rooms to UNOPENED state", unopenedCount);
        }
    }

    /**
     * Get connected rooms (for large room detection)
     * BFS to find all connected room tiles
     */
    public java.util.List<Room> getConnectedRooms(int arrayX, int arrayY, Tile[] dungeonList) {
        java.util.List<Room> connected = new java.util.ArrayList<>();
        java.util.Queue<Room> queue = new java.util.LinkedList<>();

        Tile startTile = dungeonList[arrayY * 11 + arrayX];
        if (!(startTile instanceof Room)) {
            return connected;
        }

        queue.add((Room) startTile);

        while (!queue.isEmpty()) {
            Room current = queue.poll();
            if (connected.contains(current)) {
                continue;
            }
            connected.add(current);

            // Check 4 adjacent tiles (N, S, E, W)
            int[][] directions = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
            for (int[] dir : directions) {
                int newX = arrayX + dir[0];
                int newY = arrayY + dir[1];

                if (newX < 0 || newX > 10 || newY < 0 || newY > 10) {
                    continue;
                }

                Tile adjacentTile = dungeonList[newY * 11 + newX];
                if (adjacentTile instanceof Room && !connected.contains(adjacentTile)) {
                    queue.add((Room) adjacentTile);
                }
            }
        }

        return connected;
    }
}
