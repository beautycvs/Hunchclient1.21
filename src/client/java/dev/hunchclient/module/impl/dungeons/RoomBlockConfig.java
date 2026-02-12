package dev.hunchclient.module.impl.dungeons;

import dev.hunchclient.HunchClient;
import dev.hunchclient.module.impl.dungeons.secrets.Room;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.*;
import java.util.*;
import net.minecraft.core.BlockPos;

/**
 * Config for room-specific blocks with coordinate transformation
 * ONE BIG CONFIG FILE - all rooms in one place
 */
public class RoomBlockConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File("config/hunchclient/room_blocks.json");

    // Inner class to store blocks WITH direction info
    public static class RoomBlockData {
        public String direction; // NW, NE, SE, SW
        public List<BlockPos> blocks;

        public RoomBlockData(String direction) {
            this.direction = direction;
            this.blocks = new ArrayList<>();
        }
    }

    // Map: RoomID → RoomBlockData (with direction + blocks)
    private Map<String, RoomBlockData> roomBlocks = new HashMap<>();

    public static RoomBlockConfig load() {
        if (!CONFIG_FILE.exists()) {
            HunchClient.LOGGER.info("[RoomBlockConfig] No config found, creating new");
            return new RoomBlockConfig();
        }

        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            RoomBlockConfig config = new RoomBlockConfig();

            if (json.has("rooms")) {
                JsonObject roomsObj = json.getAsJsonObject("rooms");

                for (String roomID : roomsObj.keySet()) {
                    JsonObject roomObj = roomsObj.getAsJsonObject(roomID);

                    // Get direction (or default to NW for old configs)
                    String direction = roomObj.has("direction") ? roomObj.get("direction").getAsString() : "NW";
                    RoomBlockData data = new RoomBlockData(direction);

                    // Get blocks
                    if (roomObj.has("blocks")) {
                        JsonArray blocksArray = roomObj.getAsJsonArray("blocks");
                        for (int i = 0; i < blocksArray.size(); i++) {
                            JsonObject blockObj = blocksArray.get(i).getAsJsonObject();
                            int x = blockObj.get("x").getAsInt();
                            int y = blockObj.get("y").getAsInt();
                            int z = blockObj.get("z").getAsInt();
                            data.blocks.add(new BlockPos(x, y, z));
                        }
                    }

                    config.roomBlocks.put(roomID, data);
                }
            }

            HunchClient.LOGGER.info("[RoomBlockConfig] Loaded {} rooms with blocks", config.roomBlocks.size());
            return config;

        } catch (Exception e) {
            HunchClient.LOGGER.error("[RoomBlockConfig] Failed to load config", e);
            return new RoomBlockConfig();
        }
    }

    public void save() {
        try {
            CONFIG_FILE.getParentFile().mkdirs();

            JsonObject json = new JsonObject();
            JsonObject roomsObj = new JsonObject();

            for (Map.Entry<String, RoomBlockData> entry : roomBlocks.entrySet()) {
                String roomID = entry.getKey();
                RoomBlockData data = entry.getValue();

                JsonObject roomObj = new JsonObject();
                roomObj.addProperty("direction", data.direction);

                JsonArray blocksArray = new JsonArray();
                for (BlockPos pos : data.blocks) {
                    JsonObject blockObj = new JsonObject();
                    blockObj.addProperty("x", pos.getX());
                    blockObj.addProperty("y", pos.getY());
                    blockObj.addProperty("z", pos.getZ());
                    blocksArray.add(blockObj);
                }

                roomObj.add("blocks", blocksArray);
                roomsObj.add(roomID, roomObj);
            }

            json.add("rooms", roomsObj);

            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                GSON.toJson(json, writer);
            }

            HunchClient.LOGGER.info("[RoomBlockConfig] Saved {} rooms", roomBlocks.size());

        } catch (Exception e) {
            HunchClient.LOGGER.error("[RoomBlockConfig] Failed to save config", e);
        }
    }

    /**
     * Add a block to a room (automatically transforms world → relative)
     * Uses RoomID from scoreboard as key!
     */
    public void addBlock(String roomID, Room room, BlockPos worldPos) {
        if (roomID == null || roomID.isEmpty()) {
            HunchClient.LOGGER.warn("[RoomBlockConfig] Cannot add block - no RoomID from scoreboard!");
            return;
        }

        if (room == null || room.getDirection() == null) {
            HunchClient.LOGGER.warn("[RoomBlockConfig] Cannot add block - room has no direction");
            return;
        }

        // Transform world → relative
        BlockPos relativePos = room.actualToRelative(worldPos);
        if (relativePos == null) {
            HunchClient.LOGGER.warn("[RoomBlockConfig] Failed to transform coordinates");
            return;
        }

        // Get or create data for this RoomID
        RoomBlockData data = roomBlocks.get(roomID);
        if (data == null) {
            // First block for this room - save the direction!
            data = new RoomBlockData(room.getDirection().name());
            roomBlocks.put(roomID, data);
            HunchClient.LOGGER.info("[RoomBlockConfig] Created new room entry '{}' with direction {}", roomID, room.getDirection());
        } else if (!data.direction.equals(room.getDirection().name())) {
            // Direction mismatch warning!
            HunchClient.LOGGER.warn("[RoomBlockConfig] Direction mismatch for RoomID '{}': saved={}, current={}",
                roomID, data.direction, room.getDirection());
        }

        // Add if not already there
        if (!data.blocks.contains(relativePos)) {
            data.blocks.add(relativePos);
            HunchClient.LOGGER.info("[RoomBlockConfig] Added block to RoomID '{}' ({}): {} → {} (relative)",
                roomID, data.direction, worldPos, relativePos);
        }
    }

    /**
     * Remove a block from a room (automatically transforms world → relative)
     */
    public void removeBlock(String roomID, Room room, BlockPos worldPos) {
        if (roomID == null || room == null || room.getDirection() == null) {
            return;
        }

        BlockPos relativePos = room.actualToRelative(worldPos);
        if (relativePos == null) return;

        RoomBlockData data = roomBlocks.get(roomID);
        if (data != null && data.blocks.remove(relativePos)) {
            HunchClient.LOGGER.info("[RoomBlockConfig] Removed block from RoomID '{}': {}", roomID, relativePos);

            // Remove room entry if empty
            if (data.blocks.isEmpty()) {
                roomBlocks.remove(roomID);
            }
        }
    }

    /**
     * Get all blocks for current room (automatically transforms relative → world)
     * Uses RoomID from scoreboard!
     */
    public List<BlockPos> getBlocksForRoom(String roomID, Room room) {
        if (roomID == null || roomID.isEmpty() || room == null || room.getDirection() == null) {
            return new ArrayList<>();
        }

        RoomBlockData data = roomBlocks.get(roomID);
        if (data == null || data.blocks.isEmpty()) {
            return new ArrayList<>();
        }

        // Check if directions match
        if (!data.direction.equals(room.getDirection().name())) {
            HunchClient.LOGGER.warn("[RoomBlockConfig] Direction mismatch for RoomID '{}': saved={}, current={}. Blocks may be incorrect!",
                roomID, data.direction, room.getDirection());
        }

        // Transform all relative → world
        List<BlockPos> worldBlocks = new ArrayList<>();
        for (BlockPos relativePos : data.blocks) {
            BlockPos worldPos = room.relativeToActual(relativePos);
            if (worldPos != null) {
                worldBlocks.add(worldPos);
            }
        }

        HunchClient.LOGGER.info("[RoomBlockConfig] Loaded {} blocks for RoomID '{}' with direction {} (transformed to world coords)",
            worldBlocks.size(), roomID, data.direction);

        return worldBlocks;
    }

    /**
     * Clear all blocks for a specific room
     */
    public void clearRoom(String roomName) {
        if (roomBlocks.remove(roomName) != null) {
            HunchClient.LOGGER.info("[RoomBlockConfig] Cleared room '{}'", roomName);
        }
    }

    /**
     * Clear ALL rooms
     */
    public void clearAll() {
        roomBlocks.clear();
        HunchClient.LOGGER.info("[RoomBlockConfig] Cleared all rooms");
    }

    /**
     * Get statistics
     */
    public int getRoomCount() {
        return roomBlocks.size();
    }

    public int getTotalBlockCount() {
        return roomBlocks.values().stream().mapToInt(data -> data.blocks.size()).sum();
    }

    public Map<String, Integer> getRoomBlockCounts() {
        Map<String, Integer> counts = new HashMap<>();
        for (Map.Entry<String, RoomBlockData> entry : roomBlocks.entrySet()) {
            counts.put(entry.getKey(), entry.getValue().blocks.size());
        }
        return counts;
    }

    /**
     * Check if room has blocks
     */
    public boolean hasBlocks(String roomID) {
        RoomBlockData data = roomBlocks.get(roomID);
        return data != null && !data.blocks.isEmpty();
    }
}
