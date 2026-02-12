package dev.hunchclient.module.impl.dungeons.map;

/**
 * Represents a door between rooms
 * 1:1 Port from noamm's Door.kt
 */
public class Door implements Tile {
    private final int x;
    private final int z;
    private DoorType type;
    private boolean opened;
    private RoomState state;

    public Door(int x, int z, DoorType type) {
        this.x = x;
        this.z = z;
        this.type = type;
        this.opened = false;
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
        return type.getDisplayColor();
    }

    public DoorType getType() {
        return type;
    }

    public void setType(DoorType type) {
        this.type = type;
    }

    public boolean isOpened() {
        return opened;
    }

    public void setOpened(boolean opened) {
        this.opened = opened;
    }

    @Override
    public String toString() {
        return String.format("Door{type=%s, pos=(%d, %d), opened=%s, state=%s}",
                type, x, z, opened, state);
    }
}
