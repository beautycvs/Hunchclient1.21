package dev.hunchclient.module.impl.dungeons.map;

/**
 * Base interface for all dungeon map elements (rooms, doors, unknown spaces)
 * 1:1 Port from noamm's Tile.kt
 */
public interface Tile {
    /**
     * X coordinate in the world
     */
    int getX();

    /**
     * Z coordinate in the world
     */
    int getZ();

    /**
     * Current state of this tile
     */
    RoomState getState();

    /**
     * Set the state of this tile
     */
    void setState(RoomState state);

    /**
     * Get the color for rendering this tile on the map
     */
    int getColor();
}
