package dev.hunchclient.module.impl.dungeons;

import dev.hunchclient.module.impl.dungeons.map.*;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * Global state manager for dungeon map data
 * 1:1 Port from noamm's DungeonInfo.kt
 *
 * Holds the master 11x11 grid of all dungeon tiles
 */
public class DungeonState {
    private static final Logger LOGGER = LoggerFactory.getLogger(DungeonState.class);

    // Master dungeon grid (11x11 = 121 tiles)
    private final Tile[] dungeonList = new Tile[121];

    // Unique rooms (for 2x2+ room tracking)
    private final Set<UniqueRoom> uniqueRooms = new HashSet<>();

    // Statistics
    private int roomCount = 0;
    private int witherDoors = 0;
    private int cryptCount = 0;
    private int secretCount = 0;
    private int keys = 0;

    // Map data reference
    @Nullable
    private MapItemSavedData dungeonMap = null;

    public DungeonState() {
        reset();
    }

    /**
     * Initialize dungeon list with Unknown tiles
     */
    public void reset() {
        for (int i = 0; i < 121; i++) {
            dungeonList[i] = new Unknown(0, 0);
        }

        uniqueRooms.clear();
        roomCount = 0;
        witherDoors = 0;
        cryptCount = 0;
        secretCount = 0;
        keys = 0;
        dungeonMap = null;

        LOGGER.info("[DungeonState] Reset");
    }

    /**
     * Add a unique room (for 2x2+ rooms)
     */
    public void addUniqueRoom(UniqueRoom uniqueRoom) {
        uniqueRooms.add(uniqueRoom);
    }

    /**
     * Find or create unique room for a room tile
     */
    public UniqueRoom getOrCreateUniqueRoom(int column, int row, Room room) {
        String roomName = room.getData().getName();

        // Find existing unique room with this name
        for (UniqueRoom uniqueRoom : uniqueRooms) {
            if (uniqueRoom.getName().equals(roomName)) {
                uniqueRoom.addTile(column, row, room);
                return uniqueRoom;
            }
        }

        // Create new unique room
        UniqueRoom newUniqueRoom = new UniqueRoom(column, row, room);
        uniqueRooms.add(newUniqueRoom);
        return newUniqueRoom;
    }

    // Getters and setters

    public Tile[] getDungeonList() {
        return dungeonList;
    }

    public Tile getTile(int index) {
        if (index < 0 || index >= 121) {
            return new Unknown(0, 0);
        }
        return dungeonList[index];
    }

    public Tile getTile(int x, int z) {
        return getTile(z * 11 + x);
    }

    public void setTile(int index, Tile tile) {
        if (index >= 0 && index < 121) {
            dungeonList[index] = tile;
        }
    }

    public void setTile(int x, int z, Tile tile) {
        setTile(z * 11 + x, tile);
    }

    public Set<UniqueRoom> getUniqueRooms() {
        return uniqueRooms;
    }

    public int getRoomCount() {
        return roomCount;
    }

    public void setRoomCount(int roomCount) {
        this.roomCount = roomCount;
    }

    public int getWitherDoors() {
        return witherDoors;
    }

    public void setWitherDoors(int witherDoors) {
        this.witherDoors = witherDoors;
    }

    public void incrementWitherDoors() {
        this.witherDoors++;
    }

    public int getCryptCount() {
        return cryptCount;
    }

    public void setCryptCount(int cryptCount) {
        this.cryptCount = cryptCount;
    }

    public int getSecretCount() {
        return secretCount;
    }

    public void setSecretCount(int secretCount) {
        this.secretCount = secretCount;
    }

    public int getKeys() {
        return keys;
    }

    public void setKeys(int keys) {
        this.keys = keys;
    }

    @Nullable
    public MapItemSavedData getDungeonMap() {
        return dungeonMap;
    }

    public void setDungeonMap(@Nullable MapItemSavedData dungeonMap) {
        this.dungeonMap = dungeonMap;
    }

    /**
     * Get statistics as string
     */
    public String getStats() {
        return String.format("Rooms: %d | Wither Doors: %d | Secrets: %d | Crypts: %d | Keys: %d",
                roomCount, witherDoors, secretCount, cryptCount, keys);
    }

    /**
     * Print debug info about the dungeon
     */
    public void printDebug() {
        LOGGER.info("=== Dungeon State ===");
        LOGGER.info(getStats());
        LOGGER.info("Unique Rooms: {}", uniqueRooms.size());

        // Print grid
        for (int z = 0; z <= 10; z++) {
            StringBuilder row = new StringBuilder();
            for (int x = 0; x <= 10; x++) {
                Tile tile = getTile(x, z);
                if (tile instanceof Room) {
                    Room room = (Room) tile;
                    if (room.getData().getName().equals("Unknown")) {
                        row.append("? ");
                    } else {
                        row.append("R ");
                    }
                } else if (tile instanceof Door) {
                    row.append("D ");
                } else {
                    row.append(". ");
                }
            }
            LOGGER.info(row.toString());
        }
    }
}
