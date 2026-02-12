package dev.hunchclient.module.impl.dungeons.map;

import java.util.ArrayList;
import java.util.List;

/**
 * Groups connected room tiles into a single unique room (handles 2x2+ rooms)
 * 1:1 Port from noamm's UniqueRoom.kt
 */
public class UniqueRoom {
    private final String name;
    private final Room mainRoom;  // Primary room tile
    private final List<RoomTile> tiles;  // All tiles (including separators)

    /**
     * Represents a room tile with its grid position
     */
    public static class RoomTile {
        public final int column;
        public final int row;
        public final Room room;

        public RoomTile(int column, int row, Room room) {
            this.column = column;
            this.row = row;
            this.room = room;
        }

        public int getX() {
            return room.getX();
        }

        public int getZ() {
            return room.getZ();
        }
    }

    public UniqueRoom(int column, int row, Room mainRoom) {
        this.name = mainRoom.getData().getName();
        this.mainRoom = mainRoom;
        this.tiles = new ArrayList<>();
        addTile(column, row, mainRoom);
    }

    /**
     * Add a tile to this unique room
     */
    public void addTile(int column, int row, Room room) {
        tiles.add(new RoomTile(column, row, room));
        room.setUniqueRoom(this);
    }

    public String getName() {
        return name;
    }

    public Room getMainRoom() {
        return mainRoom;
    }

    public List<RoomTile> getTiles() {
        return tiles;
    }

    public int getSize() {
        return tiles.size();
    }

    @Override
    public String toString() {
        return String.format("UniqueRoom{name='%s', tiles=%d, mainRoom=%s}",
                name, tiles.size(), mainRoom);
    }
}
