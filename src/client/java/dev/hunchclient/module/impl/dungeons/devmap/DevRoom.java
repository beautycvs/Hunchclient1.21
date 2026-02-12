package dev.hunchclient.module.impl.dungeons.devmap;

import dev.hunchclient.module.impl.dungeons.devmap.Coordinates.*;
import dev.hunchclient.module.impl.dungeons.devmap.MapEnums.*;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

import static dev.hunchclient.module.impl.dungeons.devmap.Coordinates.HALF_ROOM_SIZE;

public class DevRoom {

    private static final Minecraft mc = Minecraft.getInstance();

    // Room offsets for rotation detection
    private static final WorldPosition[] ROOM_OFFSETS = {
        new WorldPosition(-HALF_ROOM_SIZE, -HALF_ROOM_SIZE),
        new WorldPosition(HALF_ROOM_SIZE, -HALF_ROOM_SIZE),
        new WorldPosition(HALF_ROOM_SIZE, HALF_ROOM_SIZE),
        new WorldPosition(-HALF_ROOM_SIZE, HALF_ROOM_SIZE)
    };

    // Components that make up this room
    private final List<WorldComponentPosition> comps;
    private int height;

    // Room identification
    private List<Integer> cores = new ArrayList<>();
    private boolean explored = false;
    @Nullable
    private String name = null;
    private WorldPosition corner = WorldPosition.EMPTY;
    private int rotation = -1;

    // Room properties
    private RoomType type = RoomType.UNKNOWN;
    private CheckmarkType checkmark = CheckmarkType.UNEXPLORED;
    private ShapeType shape = ShapeType.SHAPE_1X1;
    private int totalSecrets = 0;
    private int secretsCompleted = -1;
    private ClearType clearType = ClearType.MOB;

    // Connected doors
    private final Set<DevDoor> doors = new HashSet<>();

    public DevRoom(List<WorldComponentPosition> comps, int height) {
        this.comps = new ArrayList<>(comps);
        this.height = height;
    }

    /**
     * Update room data after changes
     */
    public void update() {
        // Sort components
        comps.sort((a, b) -> {
            int cmp = Integer.compare(a.cz, b.cz);
            if (cmp != 0) return cmp;
            return Integer.compare(a.cx, b.cx);
        });

        scan();
        updateShape();

        corner = WorldPosition.EMPTY;
        rotation = -1;
    }

    /**
     * Scan room for identification
     */
    public DevRoom scan() {
        checkmark = CheckmarkType.UNEXPLORED;

        for (WorldComponentPosition comp : comps) {
            int x = comp.wx;
            int z = comp.wz;

            if (!isChunkLoaded(x, z)) continue;

            if (height == 0) {
                height = getHighestY(x, z);
            }

            // Try to identify room from core hash
            int coreHash = DevScanner.hashCeil(x, z, false);
            loadFromCore(coreHash);
        }

        return this;
    }

    private void loadFromCore(int coreHash) {
        DevScanner.RoomData roomData = DevScanner.findRoomByCore(coreHash);
        if (roomData != null) {
            loadFromData(roomData);
        }
    }

    private void loadFromData(DevScanner.RoomData data) {
        this.cores = new ArrayList<>(data.cores);
        this.name = data.name;
        this.type = RoomType.byName(data.type);
        this.clearType = data.clear == null ? ClearType.MOB : switch (data.clear) {
            case "mob" -> ClearType.MOB;
            case "miniboss" -> ClearType.MINIBOSS;
            default -> ClearType.OTHER;
        };
        this.totalSecrets = data.secrets;
    }

    /**
     * Add a component to this room
     */
    public DevRoom addComponent(ComponentPosition comp, boolean shouldUpdate) {
        // Check if already exists
        for (WorldComponentPosition existing : comps) {
            if (existing.toComponent().equals(comp)) {
                return this;
            }
        }

        comps.add(comp.withWorld());

        if (shouldUpdate) {
            update();
        }

        return this;
    }

    public DevRoom addComponent(ComponentPosition comp) {
        return addComponent(comp, true);
    }

    public DevRoom addComponents(List<ComponentPosition> comps) {
        for (ComponentPosition comp : comps) {
            addComponent(comp, false);
        }
        update();
        return this;
    }

    /**
     * Find the room rotation by looking for blue terracotta
     */
    public void findRotation() {
        if (height == 0) return;

        if (type == RoomType.FAIRY) {
            if (!comps.isEmpty()) {
                int x = comps.get(0).wx;
                int z = comps.get(0).wz;
                rotation = 0;
                corner = new WorldPosition(x - HALF_ROOM_SIZE, z - HALF_ROOM_SIZE);
            }
            return;
        }

        for (WorldComponentPosition comp : comps) {
            int x = comp.wx;
            int z = comp.wz;

            for (int idx = 0; idx < ROOM_OFFSETS.length; idx++) {
                WorldPosition offset = ROOM_OFFSETS[idx];
                WorldPosition pos = new WorldPosition(x + offset.x, z + offset.z);
                int nx = pos.x;
                int nz = pos.z;

                if (!isChunkLoaded(nx, nz)) continue;

                if (isBlockAt(nx, height, nz, Blocks.BLUE_TERRACOTTA)) {
                    rotation = idx * 90;
                    corner = pos;
                    return;
                }
            }
        }
    }

    /**
     * Update the room shape based on components
     */
    private void updateShape() {
        int size = comps.size();
        if (size == 0 || size > 4) {
            shape = ShapeType.UNKNOWN;
            return;
        }

        // Count distinct X and Z coordinates
        Set<Integer> distinctX = new HashSet<>();
        Set<Integer> distinctZ = new HashSet<>();
        for (WorldComponentPosition comp : comps) {
            distinctX.add(comp.cx);
            distinctZ.add(comp.cz);
        }

        int distX = distinctX.size();
        int distZ = distinctZ.size();

        if (size == 1) {
            shape = ShapeType.SHAPE_1X1;
        } else if (size == 2) {
            shape = ShapeType.SHAPE_1X2;
        } else if (size == 4) {
            if (distX == 1 || distZ == 1) {
                shape = ShapeType.SHAPE_1X4;
            } else {
                shape = ShapeType.SHAPE_2X2;
            }
        } else if (size == 3) {
            if (distX == size || distZ == size) {
                shape = ShapeType.SHAPE_1X3;
            } else {
                shape = ShapeType.SHAPE_L;
            }
        } else {
            shape = ShapeType.UNKNOWN;
        }
    }

    // Helper methods

    private boolean isChunkLoaded(int x, int z) {
        if (mc.level == null) return false;
        return mc.level.hasChunk(x >> 4, z >> 4);
    }

    private int getHighestY(int x, int z) {
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

    private boolean isBlockAt(int x, int y, int z, net.minecraft.world.level.block.Block block) {
        if (mc.level == null) return false;
        return mc.level.getBlockState(new BlockPos(x, y, z)).is(block);
    }

    // Getters and setters

    public List<WorldComponentPosition> getComps() {
        return comps;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public List<Integer> getCores() {
        return cores;
    }

    public boolean isExplored() {
        return explored;
    }

    public void setExplored(boolean explored) {
        this.explored = explored;
        // Don't auto-set checkmark - let vanilla map colors determine checkmarks
    }

    @Nullable
    public String getName() {
        return name;
    }

    public void setName(@Nullable String name) {
        this.name = name;
    }

    public WorldPosition getCorner() {
        return corner;
    }

    public int getRotation() {
        return rotation;
    }

    public RoomType getType() {
        return type;
    }

    public void setType(RoomType type) {
        this.type = type;
    }

    public CheckmarkType getCheckmark() {
        return checkmark;
    }

    public void setCheckmark(CheckmarkType checkmark) {
        this.checkmark = checkmark;
    }

    public ShapeType getShape() {
        return shape;
    }

    public int getTotalSecrets() {
        return totalSecrets;
    }

    public void setTotalSecrets(int totalSecrets) {
        this.totalSecrets = totalSecrets;
    }

    public int getSecretsCompleted() {
        return secretsCompleted;
    }

    public void setSecretsCompleted(int secretsCompleted) {
        this.secretsCompleted = secretsCompleted;
        // Set checkmark to GREEN when all secrets are found
        if (secretsCompleted >= 0 && totalSecrets > 0 && secretsCompleted >= totalSecrets) {
            checkmark = CheckmarkType.GREEN;
        }
    }

    public ClearType getClearType() {
        return clearType;
    }

    public Set<DevDoor> getDoors() {
        return doors;
    }

    @Override
    public String toString() {
        return "DevRoom[name=\"" + name + "\", type=\"" + type +
               "\", rotation=\"" + rotation + "\", shape=\"" + shape +
               "\", checkmark=\"" + checkmark + "\"]";
    }
}
