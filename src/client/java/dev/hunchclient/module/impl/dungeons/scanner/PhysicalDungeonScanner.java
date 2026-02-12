package dev.hunchclient.module.impl.dungeons.scanner;

import dev.hunchclient.module.impl.dungeons.map.*;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * Physical world scanner - scans loaded chunks to find ALL rooms
 * 1:1 Port from noamm's DungeonScanner.kt
 *
 * This is the KEY to showing rooms before they're opened!
 * Instead of only reading map data, we physically scan the world chunks.
 */
public class PhysicalDungeonScanner {
    private static final Logger LOGGER = LoggerFactory.getLogger(PhysicalDungeonScanner.class);

    // Constants from noamm
    public static final int ROOM_SIZE = 32;
    public static final int START_X = -185;
    public static final int START_Z = -185;

    private static final int HALF_ROOM_SIZE = (int) Math.floor((ROOM_SIZE - 1.0) / 2.0);

    // Clay block corner offsets for rotation detection
    private static final List<CornerOffset> CLAY_BLOCK_CORNERS = List.of(
        new CornerOffset(-HALF_ROOM_SIZE, -HALF_ROOM_SIZE),
        new CornerOffset(HALF_ROOM_SIZE, -HALF_ROOM_SIZE),
        new CornerOffset(HALF_ROOM_SIZE, HALF_ROOM_SIZE),
        new CornerOffset(-HALF_ROOM_SIZE, HALF_ROOM_SIZE)
    );

    private long lastScanTime = 0;
    private boolean isScanning = false;
    private boolean hasScanned = false;

    /**
     * Check if we should scan now
     * Scanner runs periodically (every 250ms) until rooms stop changing
     */
    public boolean shouldScan() {
        return !isScanning &&
               (System.currentTimeMillis() - lastScanTime >= 250);
    }

    /**
     * Scan the entire 11x11 dungeon grid
     * Returns the scanned dungeon list
     */
    public Tile[] scan(Tile[] dungeonList) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return dungeonList;
        }

        isScanning = true;
        boolean allChunksLoaded = true;

        LOGGER.info("[PhysicalDungeonScanner] Starting physical world scan (11x11 grid)...");
        int roomsFound = 0;
        int chunksNotLoaded = 0;

        // Scan 11x11 grid
        for (int x = 0; x <= 10; x++) {
            for (int z = 0; z <= 10; z++) {
                // Translate grid index to world position
                int xPos = START_X + x * (ROOM_SIZE >> 1);
                int zPos = START_Z + z * (ROOM_SIZE >> 1);

                // Check if chunk is loaded
                ChunkPos chunkPos = new ChunkPos(xPos >> 4, zPos >> 4);
                ChunkAccess chunk = mc.level.getChunk(chunkPos.x, chunkPos.z);

                if (chunk == null) {
                    allChunksLoaded = false;
                    continue;
                }

                // Skip if room already scanned
                int index = x + z * 11;
                Tile existingTile = dungeonList[index];
                if (existingTile != null && !(existingTile instanceof Unknown)) {
                    if (existingTile instanceof Room) {
                        Room room = (Room) existingTile;
                        // Update highest block for rotation
                        Integer highestBlock = ScanUtils.getHighestBlockAt(xPos, zPos);
                        room.setHighestBlock(highestBlock);
                        findRotation(room);
                    }
                    continue;
                }

                // Scan this position
                Tile scannedTile = scanRoom(xPos, zPos, z, x, dungeonList);
                if (scannedTile != null) {
                    dungeonList[index] = scannedTile;
                }
            }
        }

        if (allChunksLoaded) {
            // Count ACTUAL rooms (not Unknown rooms)
            int roomCount = 0;
            int unknownCount = 0;
            for (Tile tile : dungeonList) {
                if (tile instanceof Room) {
                    Room room = (Room) tile;
                    if (!room.isSeparator()) {
                        if (!room.getData().getName().equals("Unknown")) {
                            roomCount++;
                        } else {
                            unknownCount++;
                        }
                    }
                }
            }
            LOGGER.info("[PhysicalDungeonScanner] Scan complete! Found {} identified rooms, {} unknown rooms", roomCount, unknownCount);
            hasScanned = true;
        }

        lastScanTime = System.currentTimeMillis();
        isScanning = false;

        return dungeonList;
    }

    /**
     * Scan a specific room position
     */
    @Nullable
    private Tile scanRoom(int x, int z, int row, int column, Tile[] dungeonList) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;

        // Get height at position
        ChunkAccess chunk = mc.level.getChunk(x >> 4, z >> 4);
        if (chunk == null) return null;

        // In 1.21, use heightmap
        int height = chunk.getHeight();
        if (height == 0) return null;

        boolean rowEven = (row & 1) == 0;
        boolean columnEven = (column & 1) == 0;

        // Scanning a room (even row AND even column)
        if (rowEven && columnEven) {
            int roomCore = ScanUtils.getCore(x, z);
            RoomData roomData = ScanUtils.getRoomData(roomCore);

            if (roomData == null) {
                // Unknown room, but we know it exists
                LOGGER.info("[PhysicalDungeonScanner] Unknown room at ({},{}) - core hash: {} - Room DB size: {}",
                    x, z, roomCore, ScanUtils.getRoomList().size());
                roomData = RoomData.createUnknown(RoomType.NORMAL);
            } else {
                LOGGER.info("[PhysicalDungeonScanner] ✓ Matched room '{}' at ({},{}) - core hash: {}",
                    roomData.getName(), x, z, roomCore);
            }

            Room room = new Room(x, z, roomData);
            room.setCore(roomCore);
            room.setHighestBlock(height);
            findRotation(room);

            // Add to unique room (will be done later in integration phase)
            // room.addToUnique(row, column);

            return room;
        }

        // Center "block" of a 2x2 room (odd row AND odd column)
        if (!rowEven && !columnEven) {
            Tile adjacentTile = dungeonList[(column - 1) + (row - 1) * 11];
            if (adjacentTile instanceof Room) {
                Room adjacentRoom = (Room) adjacentTile;
                Room separator = new Room(x, z, adjacentRoom.getData());
                separator.setSeparator(true);
                // separator.addToUnique(row, column);
                return separator;
            }
            return null;
        }

        // Doorway between rooms (height == 74 or 82)
        // Old trap has single block at 82
        if (height == 74 || height == 82) {
            // Find door type from block at Y=69
            Block blockAt69 = ScanUtils.getBlockAt(x, 69, z);
            DoorType doorType;

            if (blockAt69 == Blocks.COAL_BLOCK) {
                doorType = DoorType.WITHER;
                // TODO: Increment wither door count
            } else if (blockAt69 == Blocks.INFESTED_STONE) {  // monster_egg in 1.8.9
                doorType = DoorType.ENTRANCE;
            } else if (blockAt69 == Blocks.TERRACOTTA) {  // stained_hardened_clay
                doorType = DoorType.BLOOD;
            } else {
                doorType = DoorType.NORMAL;
            }

            return new Door(x, z, doorType);
        }

        // Connection between large rooms
        Tile adjacentTile = dungeonList[rowEven ? row * 11 + column - 1 : (row - 1) * 11 + column];
        if (!(adjacentTile instanceof Room)) {
            return null;
        }

        Room adjacentRoom = (Room) adjacentTile;
        if (adjacentRoom.getData().getType() == RoomType.ENTRANCE) {
            return new Door(x, z, DoorType.ENTRANCE);
        } else {
            Room separator = new Room(x, z, adjacentRoom.getData());
            separator.setSeparator(true);
            return separator;
        }
    }

    /**
     * Find rotation of a room by looking for clay blocks at corners
     * 1:1 Port from noamm's Room.findRotation()
     */
    public void findRotation(Room room) {
        if (room.getRotation() != null) {
            return;
        }

        if (room.getHighestBlock() == null) {
            Integer highestBlock = ScanUtils.getHighestBlockAt(room.getX(), room.getZ());
            room.setHighestBlock(highestBlock);
            return;
        }

        // Fairy rooms have no rotation
        if (room.getData().getType() == RoomType.FAIRY) {
            room.setRotation(0);
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        // Check clay blocks at corners
        int highestY = room.getHighestBlock();
        for (int i = 0; i < CLAY_BLOCK_CORNERS.size(); i++) {
            CornerOffset offset = CLAY_BLOCK_CORNERS.get(i);
            int checkX = room.getX() + offset.x;
            int checkZ = room.getZ() + offset.z;
            BlockPos checkPos = new BlockPos(checkX, highestY, checkZ);

            Block block = mc.level.getBlockState(checkPos).getBlock();

            // In 1.8.9: Block ID 159 = stained_hardened_clay, Metadata 11 = blue
            // In 1.21: BLUE_TERRACOTTA
            if (block == Blocks.BLUE_TERRACOTTA) {
                room.setRotation(i * 90);
                return;
            }
        }
    }

    /**
     * Reset scanner state
     */
    public void reset() {
        hasScanned = false;
        isScanning = false;
        lastScanTime = 0;
    }

    public boolean hasScanned() {
        return hasScanned;
    }

    /**
     * Corner offset for rotation detection
     */
    private static class CornerOffset {
        final int x;
        final int z;

        CornerOffset(int x, int z) {
            this.x = x;
            this.z = z;
        }
    }
}
