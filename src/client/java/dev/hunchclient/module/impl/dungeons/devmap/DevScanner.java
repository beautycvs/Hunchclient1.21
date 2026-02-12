package dev.hunchclient.module.impl.dungeons.devmap;

import com.google.gson.Gson;
import dev.hunchclient.module.impl.dungeons.devmap.Coordinates.*;
import dev.hunchclient.module.impl.dungeons.devmap.MapEnums.*;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;


public class DevScanner {

    private static final Minecraft mc = Minecraft.getInstance();

    // Room database
    private static List<RoomData> roomsData = new ArrayList<>();
    private static boolean roomsLoaded = false;

    // Current dungeon state
    public static int lastIdx = -1;
    @Nullable
    public static DevRoom currentRoom = null;
    public static DevRoom[] rooms = new DevRoom[36];
    public static DevDoor[] doors = new DevDoor[60];
    public static List<WorldComponentPosition> availablePos = new ArrayList<>();

    // Legacy block registry for room identification
    private static Map<String, Integer> legacyBlockIds = new HashMap<>();

    /**
     * Initialize the scanner
     */
    public static void init() {
        loadRoomsData();
        loadLegacyBlockIds();
        reset();
    }

    /**
     * Load room data from JSON
     */
    private static void loadRoomsData() {
        if (roomsLoaded) return;

        try {
            InputStream stream = DevScanner.class.getResourceAsStream("/assets/hunchclient/dungeons/rooms.json");
            if (stream != null) {
                RoomData[] data = new Gson().fromJson(new InputStreamReader(stream), RoomData[].class);
                roomsData = Arrays.asList(data);
                roomsLoaded = true;
                System.out.println("[DevScanner] Loaded " + roomsData.size() + " room definitions");
            } else {
                System.err.println("[DevScanner] Could not find rooms.json");
            }
        } catch (Exception e) {
            System.err.println("[DevScanner] Error loading rooms.json: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Load legacy block IDs for room hashing
     */
    private static void loadLegacyBlockIds() {
        try {
            InputStream stream = DevScanner.class.getResourceAsStream("/assets/hunchclient/dungeons/legacy_blocks.json");
            if (stream != null) {
                @SuppressWarnings("unchecked")
                Map<String, Double> data = new Gson().fromJson(new InputStreamReader(stream), Map.class);
                for (Map.Entry<String, Double> entry : data.entrySet()) {
                    legacyBlockIds.put(entry.getKey(), entry.getValue().intValue());
                }
                System.out.println("[DevScanner] Loaded " + legacyBlockIds.size() + " legacy block IDs");
            }
        } catch (Exception e) {
            System.err.println("[DevScanner] Error loading legacy_blocks.json: " + e.getMessage());
        }
    }

    /**
     * Find a room by its core hash
     */
    @Nullable
    public static RoomData findRoomByCore(int coreHash) {
        for (RoomData room : roomsData) {
            if (room.cores.contains(coreHash)) {
                return room;
            }
        }
        return null;
    }

    /**
     * Hash the ceiling blocks at a position for room identification
     */
    public static int hashCeil(int x, int z, boolean debug) {
        if (mc.level == null) return 0;

        StringBuilder str = new StringBuilder();

        for (int y = 140; y >= 12; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            var state = mc.level.getBlockState(pos);
            Block block = state.getBlock();

            Integer blockId = getLegacyId(state, debug);
            if (blockId == null) continue;

            // Skip iron bars and chests
            if (block == Blocks.IRON_BARS || block == Blocks.CHEST) {
                str.append("0");
                continue;
            }

            str.append(blockId);
        }

        return str.toString().hashCode();
    }

    @Nullable
    private static Integer getLegacyId(net.minecraft.world.level.block.state.BlockState state, boolean debug) {
        Block block = state.getBlock();
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        String registryName = id.toString();

        // Handle fluids
        if (!state.getFluidState().isEmpty()) {
            if (state.getFluidState().isSource()) {
                if (block == Blocks.WATER) return 9;
                if (block == Blocks.LAVA) return 11;
            } else {
                if (block == Blocks.WATER) return 8;
                if (block == Blocks.LAVA) return 10;
            }
        }

        // Handle slabs
        if (block instanceof SlabBlock) {
            SlabType slabType = state.getValue(SlabBlock.TYPE);
            registryName += "[type=" + slabType.getSerializedName() + "]";
        }

        Integer result = legacyBlockIds.get(registryName);

        if (debug && result == null) {
            System.out.println("[DevScanner] Unknown block: " + registryName);
        }

        return result;
    }

    /**
     * Get the highest Y coordinate at a position
     */
    public static int getHighestY(int x, int z) {
        if (mc.level == null) return -1;

        for (int y = 256; y >= 0; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            var state = mc.level.getBlockState(pos);
            if (!state.isAir() && !state.is(Blocks.GOLD_BLOCK)) {
                return y;
            }
        }
        return 0;
    }

    /**
     * Reset the scanner state
     */
    public static void reset() {
        Arrays.fill(rooms, null);
        Arrays.fill(doors, null);
        lastIdx = -1;
        currentRoom = null;
        availablePos = findAvailablePos();
    }

    /**
     * Soft reset - refresh availablePos for positions where rooms/doors are null.
     * This is like toggling the module but keeps found rooms.
     * Call this periodically to discover new rooms.
     */
    public static void softReset() {
        if (mc.level == null) return;

        // Rebuild availablePos for all positions that don't have rooms/doors yet
        availablePos.clear();

        // Add room positions where room is null
        for (int z = 0; z <= 10; z += 2) {
            for (int x = 0; x <= 10; x += 2) {
                ComponentPosition comp = new ComponentPosition(x, z);
                int idx = comp.getRoomIdx();
                if (idx >= 0 && idx < 36 && rooms[idx] == null) {
                    availablePos.add(comp.withWorld());
                }
            }
        }

        // Add door positions where door is null
        for (int z = 0; z <= 10; z++) {
            for (int x = 0; x <= 10; x++) {
                if (((x & 1) ^ (z & 1)) != 1) continue; // Skip non-door positions
                ComponentPosition comp = new ComponentPosition(x, z);
                int idx = comp.getDoorIdx();
                if (idx >= 0 && idx < 60 && doors[idx] == null) {
                    availablePos.add(comp.withWorld());
                }
            }
        }

        DevMapState.getInstance().invalidate();
    }

    private static List<WorldComponentPosition> findAvailablePos() {
        List<WorldComponentPosition> pos = new ArrayList<>();

        for (int z = 0; z <= 10; z++) {
            for (int x = 0; x <= 10; x++) {
                // Skip positions that are neither rooms nor doors
                if (x % 2 != 0 && z % 2 != 0) continue;

                pos.add(new ComponentPosition(x, z).withWorld());
            }
        }

        return pos;
    }

    /**
     * Add a room to the scanner
     */
    public static void addRoom(ComponentPosition comp, DevRoom room, boolean force) {
        int idx = comp.getRoomIdx();
        if (idx < 0 || idx >= 36) return;

        if (!force && rooms[idx] != null) {
            DevRoom existing = rooms[idx];
            if (room.getName() == null) {
                mergeRooms(existing, room);
            } else {
                mergeRooms(room, existing);
            }
            return;
        }

        rooms[idx] = room;

        // Connect to neighboring doors
        for (ComponentPosition doorComp : comp.getNeighboringDoors()) {
            int doorIdx = doorComp.getDoorIdx();
            if (doorIdx >= 0 && doorIdx < 60 && doors[doorIdx] != null) {
                doors[doorIdx].getRooms().add(room);
                room.getDoors().add(doors[doorIdx]);
            }
        }
    }

    public static void addRoom(ComponentPosition comp, DevRoom room) {
        addRoom(comp, room, false);
    }

    /**
     * Add a door to the scanner
     */
    public static void addDoor(DevDoor door) {
        ComponentPosition comp = door.getComp().toComponent();
        int idx = comp.getDoorIdx();
        if (idx < 0 || idx >= 60) return;

        doors[idx] = door;

        // Connect to neighboring rooms
        for (ComponentPosition roomComp : comp.getNeighboringRooms()) {
            int roomIdx = roomComp.getRoomIdx();
            if (roomIdx >= 0 && roomIdx < 36 && rooms[roomIdx] != null) {
                rooms[roomIdx].getDoors().add(door);
                door.getRooms().add(rooms[roomIdx]);
            }
        }
    }

    /**
     * Merge two rooms together
     */
    public static boolean mergeRooms(ComponentPosition comp1, ComponentPosition comp2) {
        int i1 = comp1.getRoomIdx();
        int i2 = comp2.getRoomIdx();
        DevRoom r1 = rooms[i1];
        DevRoom r2 = rooms[i2];

        if (r1 != null && r2 != null) {
            if (i1 < i2) {
                mergeRooms(r1, r2);
            } else {
                mergeRooms(r2, r1);
            }
            return true;
        }

        if (r1 == null && r2 == null) return false;

        DevRoom room;
        ComponentPosition comp;
        if (r1 == null) {
            room = r2;
            comp = comp1;
        } else {
            room = r1;
            comp = comp2;
        }

        room.addComponent(comp);
        addRoom(comp, room);
        return true;
    }

    /**
     * Merge room2 into room1
     */
    public static void mergeRooms(DevRoom room1, DevRoom room2) {
        if (room1 == room2) return;

        for (WorldComponentPosition comp : room2.getComps()) {
            ComponentPosition c = comp.toComponent();
            room1.addComponent(c, false);
            addRoom(c, room1, true);
        }

        room1.update();
        if (room2.isExplored()) {
            room1.setExplored(true);
        }

        // Remove room2 from its doors
        for (DevDoor door : room2.getDoors()) {
            door.getRooms().remove(room2);
        }
    }

    /**
     * Scan the dungeon
     */
    public static void scan() {
        if (availablePos.isEmpty()) return;
        if (mc.level == null || mc.player == null) return;

        int startLen = availablePos.size();
        List<WorldComponentPosition> toRemove = new ArrayList<>();

        for (WorldComponentPosition pos : availablePos) {
            int wx = pos.wx;
            int wz = pos.wz;
            ComponentPosition comp = pos.toComponent();

            if (!isChunkLoaded(wx, wz)) continue;

            toRemove.add(pos);

            int roofHeight = getHighestY(wx, wz);
            if (roofHeight < 0) continue;

            // Door scan
            if (comp.isValidDoor()) {
                if (roofHeight != 0 && roofHeight < 85) {
                    DevDoor door = new DevDoor(pos);
                    if (comp.z % 2 == 1) {
                        door.setRotation(0);
                    }
                    addDoor(door);
                }
                continue;
            }

            if (roofHeight <= 0) continue;

            // Room scan
            List<WorldComponentPosition> compList = new ArrayList<>();
            compList.add(pos);
            DevRoom room = new DevRoom(compList, roofHeight).scan();

            if (room.getType() == RoomType.ENTRANCE) {
                room.setExplored(true);
                room.setCheckmark(CheckmarkType.NONE);
            }

            addRoom(comp, room);

            // Check neighbors for room merging
            for (ComponentPosition.Neighbor neighbor : comp.getNeighbors()) {
                WorldComponentPosition doorPos = neighbor.door.withWorld();
                int nx = doorPos.wx;
                int nz = doorPos.wz;

                var heightBlock = getBlockAt(nx, roofHeight, nz);
                var aboveBlock = getBlockAt(nx, roofHeight + 1, nz);

                if (heightBlock == null) continue;

                boolean heightEmpty = heightBlock == Blocks.AIR;
                boolean aboveEmpty = aboveBlock == null || aboveBlock == Blocks.AIR;

                if (room.getType() == RoomType.ENTRANCE && !heightEmpty) {
                    var block76 = getBlockAt(nx, 76, nz);
                    if (block76 != null && block76 != Blocks.AIR) {
                        int doorIdx = neighbor.door.getDoorIdx();
                        if (doorIdx >= 0 && doorIdx < 60) {
                            DevDoor door = new DevDoor(doorPos);
                            door.setType(DoorType.ENTRANCE);
                            addDoor(door);
                        }
                    }
                    continue;
                }

                if (heightEmpty || !aboveEmpty) continue;

                int neighborIdx = neighbor.room.getRoomIdx();
                if (neighborIdx < 0 || neighborIdx >= 36) continue;

                DevRoom neighborRoom = rooms[neighborIdx];
                if (neighborRoom == null) {
                    room.addComponent(neighbor.room);
                    addRoom(neighbor.room, room);
                    continue;
                }

                if (neighborRoom.getType() == RoomType.ENTRANCE || neighborRoom == room) continue;

                mergeRooms(neighborRoom, room);
                room = neighborRoom;
            }
        }

        availablePos.removeAll(toRemove);

        if (availablePos.size() != startLen) {
            // Trigger map redraw
            DevMapState.getInstance().invalidate();
        }
    }

    /**
     * Check door states in the world
     */
    public static void checkDoorStates() {
        for (DevDoor door : doors) {
            if (door == null || door.isOpened()) continue;
            door.check();
        }
    }

    /**
     * Check room rotations
     */
    public static void checkRoomRotations() {
        for (DevRoom room : rooms) {
            if (room == null || room.getRotation() != -1) continue;
            room.findRotation();
        }
    }

    // Helper methods

    private static boolean isChunkLoaded(int x, int z) {
        if (mc.level == null) return false;
        return mc.level.hasChunk(x >> 4, z >> 4);
    }

    @Nullable
    private static Block getBlockAt(int x, int y, int z) {
        if (mc.level == null) return null;
        return mc.level.getBlockState(new BlockPos(x, y, z)).getBlock();
    }

    /**
     * Room data from JSON
     */
    public static class RoomData {
        public String name;
        public String type;
        public int secrets;
        public List<Integer> cores;
        public int trappedChests;
        public int roomID;
        public String clear;
        public int crypts;
        public Integer clearScore;
        public Integer secretScore;
        public String shape;

        @Override
        public String toString() {
            return "RoomData[name=\"" + name + "\", type=\"" + type + "\", secrets=" + secrets + "]";
        }
    }
}
