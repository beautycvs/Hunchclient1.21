package dev.hunchclient.module.impl.dungeons.map;

/**
 * Room state enum - ordinal matters! (progression order)
 * 1:1 Port from noamm's RoomState.kt
 *
 * Order: FAILED < GREEN < CLEARED < DISCOVERED < UNOPENED < UNDISCOVERED
 */
public enum RoomState {
    FAILED,       // Puzzle failed
    GREEN,        // Fully cleared (green checkmark)
    CLEARED,      // Room cleared
    DISCOVERED,   // Room discovered (on map)
    UNOPENED,     // Room exists but door is closed (gray on map)
    UNDISCOVERED  // Room not discovered yet (not on map)
}
