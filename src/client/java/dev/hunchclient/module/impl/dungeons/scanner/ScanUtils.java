package dev.hunchclient.module.impl.dungeons.scanner;

import dev.hunchclient.module.impl.dungeons.map.*;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Utility methods for scanning dungeon rooms
 * 1:1 Port from noamm's ScanUtils.kt
 */
public class ScanUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScanUtils.class);

    // Room database (loaded from Skytils rooms.json)
    private static final List<RoomData> roomList = new ArrayList<>();

    @Nullable
    private static Room currentRoom = null;

    @Nullable
    private static Room lastKnownRoom = null;

    /**
     * Get room data by core hash
     */
    @Nullable
    public static RoomData getRoomData(int hash) {
        for (RoomData data : roomList) {
            if (data.getCores().contains(hash)) {
                return data;
            }
        }
        return null;
    }

    /**
     * Get room list (for adding loaded data)
     */
    public static List<RoomData> getRoomList() {
        return roomList;
    }

    /**
     * Calculate room component (grid position) from world position
     * Translates world coords to 11x11 grid coords
     */
    public static GridPos getRoomComponent(BlockPos pos) {
        int gx = (int) Math.floor((pos.getX() + 200 + 0.5) / 32.0);
        int gz = (int) Math.floor((pos.getZ() + 200 + 0.5) / 32.0);
        return new GridPos(gx, gz);
    }

    /**
     * Get room corner from grid position
     */
    public static WorldPos getRoomCorner(GridPos gridPos) {
        int x = -200 + gridPos.x * 32;
        int z = -200 + gridPos.z * 32;
        return new WorldPos(x, z);
    }

    /**
     * Get room center from grid position
     */
    public static WorldPos getRoomCenter(GridPos gridPos) {
        WorldPos corner = getRoomCorner(gridPos);
        return new WorldPos(corner.x + 15, corner.z + 15);
    }

    /**
     * Get room center at a position
     */
    public static BlockPos getRoomCenterAt(BlockPos pos) {
        GridPos gridPos = getRoomComponent(pos);
        WorldPos center = getRoomCenter(gridPos);
        return new BlockPos(center.x, 0, center.z);
    }

    /**
     * Calculate core hash from block IDs at a position
     * Scans Y=140 down to Y=12, hashing block IDs
     */
    public static int getCore(int x, int z) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return 0;

        StringBuilder sb = new StringBuilder(150);
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (int y = 140; y >= 12; y--) {
            mutablePos.set(x, y, z);
            BlockState state = mc.level.getBlockState(mutablePos);
            Block block = state.getBlock();

            // Skip these blocks (like in noamm: 5=planks, 54=chest, 146=trapped_chest)
            if (block == Blocks.OAK_PLANKS || block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST) {
                continue;
            }

            // Get block ID (use registry ID in 1.21)
            int blockId = Block.getId(state);
            sb.append(blockId);
        }

        return sb.toString().hashCode();
    }

    /**
     * Get highest block Y at position (for rotation detection)
     */
    @Nullable
    public static Integer getHighestBlockAt(int x, int z) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;

        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (int y = 255; y >= 0; y--) {
            mutablePos.set(x, y, z);
            BlockState state = mc.level.getBlockState(mutablePos);
            Block block = state.getBlock();

            // Skip air and wool
            if (block == Blocks.AIR || block == Blocks.WHITE_WOOL ||
                block == Blocks.ORANGE_WOOL || block == Blocks.MAGENTA_WOOL ||
                block == Blocks.LIGHT_BLUE_WOOL || block == Blocks.YELLOW_WOOL ||
                block == Blocks.LIME_WOOL || block == Blocks.PINK_WOOL ||
                block == Blocks.GRAY_WOOL || block == Blocks.LIGHT_GRAY_WOOL ||
                block == Blocks.CYAN_WOOL || block == Blocks.PURPLE_WOOL ||
                block == Blocks.BLUE_WOOL || block == Blocks.BROWN_WOOL ||
                block == Blocks.GREEN_WOOL || block == Blocks.RED_WOOL ||
                block == Blocks.BLACK_WOOL) {
                continue;
            }

            return y;
        }

        return null;
    }

    /**
     * Get block at position
     */
    public static Block getBlockAt(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return Blocks.AIR;
        return mc.level.getBlockState(pos).getBlock();
    }

    /**
     * Get block at coordinates
     */
    public static Block getBlockAt(int x, int y, int z) {
        return getBlockAt(new BlockPos(x, y, z));
    }

    @Nullable
    public static Room getCurrentRoom() {
        return currentRoom;
    }

    public static void setCurrentRoom(@Nullable Room room) {
        currentRoom = room;
    }

    @Nullable
    public static Room getLastKnownRoom() {
        return lastKnownRoom;
    }

    public static void setLastKnownRoom(@Nullable Room room) {
        lastKnownRoom = room;
    }

    /**
     * Grid position (11x11 grid)
     */
    public static class GridPos {
        public final int x;
        public final int z;

        public GridPos(int x, int z) {
            this.x = x;
            this.z = z;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            GridPos gridPos = (GridPos) o;
            return x == gridPos.x && z == gridPos.z;
        }

        @Override
        public int hashCode() {
            return 31 * x + z;
        }

        @Override
        public String toString() {
            return String.format("GridPos(%d, %d)", x, z);
        }
    }

    /**
     * World position
     */
    public static class WorldPos {
        public final int x;
        public final int z;

        public WorldPos(int x, int z) {
            this.x = x;
            this.z = z;
        }

        @Override
        public String toString() {
            return String.format("WorldPos(%d, %d)", x, z);
        }
    }
}
