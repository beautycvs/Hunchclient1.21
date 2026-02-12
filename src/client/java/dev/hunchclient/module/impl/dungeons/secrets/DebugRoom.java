

package dev.hunchclient.module.impl.dungeons.secrets;

import org.jetbrains.annotations.NotNull;
import org.joml.Vector2ic;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Debug room helper - Simplified from Skyblocker
 * Used for testing room matching with specific room data
 */
public class DebugRoom extends Room {

    private DebugRoom(@NotNull Type type, @NotNull Vector2ic... physicalPositions) {
        super(type, physicalPositions);
    }

    /**
     * Creates a debug room with a single possible room match
     * Used for testing specific rooms
     */
    public static DebugRoom ofSinglePossibleRoom(
            Room.Type type,
            Vector2ic[] physicalPositions,
            String roomName,
            int[] roomData,
            Room.Direction direction) {

        DebugRoom room = new DebugRoom(type, physicalPositions);

        // Override the room data to only include the specific room we're testing
        room.roomsData = Map.of(roomName, roomData);

        // Set up single possible room with specific direction
        room.possibleRooms = List.of(
            org.apache.commons.lang3.tuple.MutableTriple.of(
                direction,
                physicalPositions[0],
                List.of(roomName)
            )
        );

        return room;
    }

    /**
     * Overloaded version for single position
     */
    public static DebugRoom ofSinglePossibleRoom(
            Room.Type type,
            Vector2ic physicalPosition,
            String roomName,
            int[] roomData,
            Room.Direction direction) {
        return ofSinglePossibleRoom(type, new Vector2ic[]{physicalPosition}, roomName, roomData, direction);
    }
}
