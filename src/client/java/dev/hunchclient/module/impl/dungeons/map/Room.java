package dev.hunchclient.module.impl.dungeons.map;

import org.jetbrains.annotations.Nullable;

/**
 * Represents a dungeon room with position, data, rotation, and state
 * 1:1 Port from noamm's Room.kt
 */
public class Room implements Tile {
    private final int x;
    private final int z;
    private RoomData data;
    private int core;
    private boolean isSeparator;  // True for 2x2 room separator tiles
    @Nullable
    private Integer rotation;     // 0, 90, 180, or 270
    @Nullable
    private Integer highestBlock; // Highest block Y for rotation detection
    @Nullable
    private UniqueRoom uniqueRoom; // Parent unique room for 2x2+ rooms
    private RoomState state;

    public Room(int x, int z, RoomData data) {
        this.x = x;
        this.z = z;
        this.data = data;
        this.core = 0;
        this.isSeparator = false;
        this.rotation = null;
        this.highestBlock = null;
        this.uniqueRoom = null;
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
    public void setState(RoomState newState) {
        // Only update state for main room of UniqueRoom
        if (uniqueRoom != null && uniqueRoom.getMainRoom() != this) {
            return;
        }

        RoomState oldState = this.state;
        if (oldState == newState) {
            return;
        }

        if ("Unknown".equals(data.getName())) {
            return;
        }

        this.state = newState;

        // TODO: Trigger event for room state change
        // EventDispatcher.postAndCatch(DungeonEvent.RoomEvent.onStateChange(this, oldState, newState));
    }

    @Override
    public int getColor() {
        // UNOPENED rooms get special color
        if (state == RoomState.UNOPENED) {
            return 0xFF_808080; // Gray for unopened
        }

        // Otherwise use type color
        return data.getType().getDisplayColor();
    }

    public RoomData getData() {
        return data;
    }

    public void setData(RoomData data) {
        this.data = data;
    }

    public int getCore() {
        return core;
    }

    public void setCore(int core) {
        this.core = core;
    }

    public boolean isSeparator() {
        return isSeparator;
    }

    public void setSeparator(boolean separator) {
        isSeparator = separator;
    }

    @Nullable
    public Integer getRotation() {
        return rotation;
    }

    public void setRotation(@Nullable Integer rotation) {
        this.rotation = rotation;
    }

    @Nullable
    public Integer getHighestBlock() {
        return highestBlock;
    }

    public void setHighestBlock(@Nullable Integer highestBlock) {
        this.highestBlock = highestBlock;
    }

    @Nullable
    public UniqueRoom getUniqueRoom() {
        return uniqueRoom;
    }

    public void setUniqueRoom(@Nullable UniqueRoom uniqueRoom) {
        this.uniqueRoom = uniqueRoom;
    }

    @Override
    public String toString() {
        return String.format("Room{name='%s', pos=(%d, %d), state=%s, rotation=%d}",
                data.getName(), x, z, state, rotation != null ? rotation : -1);
    }
}
