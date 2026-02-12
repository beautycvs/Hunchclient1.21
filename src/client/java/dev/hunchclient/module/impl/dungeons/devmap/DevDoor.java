package dev.hunchclient.module.impl.dungeons.devmap;

import dev.hunchclient.module.impl.dungeons.devmap.Coordinates.*;
import dev.hunchclient.module.impl.dungeons.devmap.MapEnums.*;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;


public class DevDoor {

    private static final Minecraft mc = Minecraft.getInstance();

    private final WorldComponentPosition comp;
    private int rotation = -1;
    private boolean opened = false;
    private DoorType type = DoorType.NORMAL;
    private final Set<DevRoom> rooms = new HashSet<>();

    // Computed room component positions
    public final ComponentPosition roomComp1;
    public final ComponentPosition roomComp2;

    // Special flag for fairy room doors that flash
    private boolean fairyDoorFlashing = false;

    public DevDoor(WorldComponentPosition comp) {
        this.comp = comp;

        int cx = comp.cx;
        int cz = comp.cz;

        // Calculate which rooms this door connects
        if ((cx & 1) == 1) {
            // Horizontal door (connects rooms left and right)
            roomComp1 = new ComponentPosition((cx - 1) >> 1, cz >> 1);
            roomComp2 = new ComponentPosition((cx + 1) >> 1, cz >> 1);
        } else {
            // Vertical door (connects rooms above and below)
            roomComp1 = new ComponentPosition(cx >> 1, (cz - 1) >> 1);
            roomComp2 = new ComponentPosition(cx >> 1, (cz + 1) >> 1);
        }
    }

    /**
     * Check the door state in the world
     */
    public void check() {
        if (opened) return;

        int x = comp.wx;
        int z = comp.wz;

        if (!isChunkLoaded(x, z)) return;

        Block blockAt = getBlockAt(x, 69, z);
        if (blockAt == null) return;

        // Door is opened if it's air or barrier
        opened = blockAt == Blocks.AIR || blockAt == Blocks.BARRIER;

        // Determine door type from block
        if (isInfestedBlock(blockAt)) {
            type = DoorType.ENTRANCE;
        } else if (blockAt == Blocks.COAL_BLOCK) {
            type = DoorType.WITHER;
        } else if (blockAt == Blocks.RED_TERRACOTTA) {
            type = DoorType.BLOOD;
        } else {
            type = DoorType.NORMAL;
        }

        // Handle fairy door flashing
        if (opened && fairyDoorFlashing && !areAllRoomsExplored()) {
            type = DoorType.WITHER;
        }
    }

    private boolean isInfestedBlock(Block block) {
        return block == Blocks.INFESTED_COBBLESTONE ||
               block == Blocks.INFESTED_CHISELED_STONE_BRICKS ||
               block == Blocks.INFESTED_CRACKED_STONE_BRICKS ||
               block == Blocks.INFESTED_DEEPSLATE ||
               block == Blocks.INFESTED_MOSSY_STONE_BRICKS ||
               block == Blocks.INFESTED_STONE ||
               block == Blocks.INFESTED_STONE_BRICKS;
    }

    private boolean areAllRoomsExplored() {
        for (DevRoom room : rooms) {
            if (!room.isExplored()) return false;
        }
        return true;
    }

    // Helper methods

    private boolean isChunkLoaded(int x, int z) {
        if (mc.level == null) return false;
        return mc.level.hasChunk(x >> 4, z >> 4);
    }

    private Block getBlockAt(int x, int y, int z) {
        if (mc.level == null) return null;
        return mc.level.getBlockState(new BlockPos(x, y, z)).getBlock();
    }

    // Getters and setters

    public WorldComponentPosition getComp() {
        return comp;
    }

    public int getRotation() {
        return rotation;
    }

    public void setRotation(int rotation) {
        this.rotation = rotation;
    }

    public boolean isOpened() {
        return opened;
    }

    public void setOpened(boolean opened) {
        this.opened = opened;
    }

    public DoorType getType() {
        return type;
    }

    public void setType(DoorType type) {
        this.type = type;
    }

    public Set<DevRoom> getRooms() {
        return rooms;
    }

    public boolean isFairyDoorFlashing() {
        return fairyDoorFlashing;
    }

    public void setFairyDoorFlashing(boolean fairyDoorFlashing) {
        this.fairyDoorFlashing = fairyDoorFlashing;
    }

    @Override
    public String toString() {
        return "DevDoor[type=\"" + type + "\", rotation=\"" + rotation + "\", opened=\"" + opened + "\"]";
    }
}
