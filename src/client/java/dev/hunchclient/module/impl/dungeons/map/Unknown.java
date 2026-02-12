package dev.hunchclient.module.impl.dungeons.map;

/**
 * Represents an unknown/empty tile on the dungeon map
 * 1:1 Port from noamm's Unknown.kt
 */
public class Unknown implements Tile {
    private final int x;
    private final int z;
    private RoomState state;

    public Unknown(int x, int z) {
        this.x = x;
        this.z = z;
        this.state = RoomState.UNDISCOVERED;
    }

    @Override
    public int getX() {
        return x;
    }

    @Override
    public int getZ() {
        return z;
    }

    @Override
    public RoomState getState() {
        return state;
    }

    @Override
    public void setState(RoomState state) {
        this.state = state;
    }

    @Override
    public int getColor() {
        return 0xFF_000000; // Black for unknown
    }

    @Override
    public String toString() {
        return String.format("Unknown{pos=(%d, %d)}", x, z);
    }
}
