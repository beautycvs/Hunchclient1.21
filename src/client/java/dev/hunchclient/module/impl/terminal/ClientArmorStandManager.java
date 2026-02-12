package dev.hunchclient.module.impl.terminal;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Manages server-backed armor stands for terminal positions when running in singleplayer.
 */
public class ClientArmorStandManager {
    // All 17 terminal positions across all 4 phases (for cleanup fallback)
    private static final Vec3[] DEFAULT_TERMINAL_POSITIONS = new Vec3[] {
        // Phase 1 (4 terminals)
        new Vec3(109, 113, 73),
        new Vec3(109, 119, 79),
        new Vec3(91, 112, 92),
        new Vec3(91, 122, 101),
        // Phase 2 (5 terminals)
        new Vec3(68, 109, 123),
        new Vec3(59, 120, 124),
        new Vec3(47, 109, 123),
        new Vec3(40, 124, 124),
        new Vec3(39, 108, 141),
        // Phase 3 (4 terminals)
        new Vec3(-1, 109, 112),
        new Vec3(-1, 119, 93),
        new Vec3(17, 123, 93),
        new Vec3(-1, 109, 77),
        // Phase 4 (4 terminals)
        new Vec3(41, 109, 31),
        new Vec3(44, 121, 31),
        new Vec3(67, 109, 31),
        new Vec3(72, 115, 46)
    };
    // INCREASED radius for more aggressive cleanup - armor stands can spawn slightly offset
    private static final double CLEANUP_RADIUS = 5.0;
    private static final double CLEANUP_HEIGHT = 6.0;

    private final TerminalManager terminalManager;
    private final Map<UUID, TerminalPosition> standToTerminal = new HashMap<>();
    private ResourceKey<Level> activeWorldKey;

    public ClientArmorStandManager(TerminalManager terminalManager) {
        this.terminalManager = terminalManager;
    }

    /**
     * Spawn armor stands in the integrated server world so they are fully interactive.
     */
    public void spawnArmorStands() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !mc.isLocalServer()) {
            return;
        }

        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            return;
        }

        ResourceKey<Level> worldKey = mc.player.level().dimension();

        server.executeIfPossible(() -> {
            ServerLevel serverWorld = server.getLevel(worldKey);
            if (serverWorld == null) {
                return;
            }

            // IMPORTANT: Always clean up old stands on world join to prevent duplicates after restart
            removeExistingStands(serverWorld);
            standToTerminal.clear();

            for (TerminalPosition terminal : terminalManager.getTerminals()) {
                ArmorStand armorStand = new ArmorStand(EntityType.ARMOR_STAND, serverWorld);

                Vec3 position = terminal.getPosition();

                // DYNAMICALLY find command block near terminal position
                Vec3 commandBlockPos = findCommandBlockNear(serverWorld, position);

                Vec3 spawnPos;
                if (commandBlockPos == null) {
                    // Fallback: spawn at terminal position if no command block found
                    spawnPos = new Vec3(position.x, position.y - 0.5, position.z);
                } else {
                    // Spawn 1 block in the ROOM (towards terminal, away from wall)
                    // Command block is in the wall, armor stand should be in front of it
                    double dx = position.x - commandBlockPos.x;
                    double dz = position.z - commandBlockPos.z;

                    // Move 1 block towards terminal on PRIMARY axis only (X or Z, not both)
                    double offsetX = 0;
                    double offsetZ = 0;

                    // Use the axis with larger difference
                    if (Math.abs(dx) > Math.abs(dz)) {
                        offsetX = Math.signum(dx);
                    } else if (Math.abs(dz) > 0) {
                        offsetZ = Math.signum(dz);
                    } else {
                        // Same position on both axes, use X as default
                        offsetX = Math.signum(dx);
                    }

                    spawnPos = new Vec3(
                        commandBlockPos.x + offsetX,
                        commandBlockPos.y,
                        commandBlockPos.z + offsetZ
                    );
                }

                // Spawn at calculated position
                armorStand.snapTo(spawnPos.x, spawnPos.y, spawnPos.z, 0.0f, 0.0f);
                applyStandFlags(armorStand);
                armorStand.setCustomName(Component.literal("Terminal Inactive"));
                armorStand.setCustomNameVisible(true);

                if (serverWorld.addFreshEntity(armorStand)) {
                    standToTerminal.put(armorStand.getUUID(), terminal);
                }
            }

            activeWorldKey = worldKey;
        });
    }

    /**
     * Remove all armor stands previously spawned by the manager.
     */
    public void clearArmorStands() {
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null || activeWorldKey == null) {
            standToTerminal.clear();
            activeWorldKey = null;
            return;
        }

        server.executeIfPossible(() -> {
            ServerLevel serverWorld = server.getLevel(activeWorldKey);
            if (serverWorld != null) {
                removeExistingStands(serverWorld);
            }
            standToTerminal.clear();
            activeWorldKey = null;
        });
    }

    /**
     * Update armor stand nametags when terminals complete or reset.
     */
    public void updateArmorStands() {
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null || activeWorldKey == null) {
            return;
        }

        server.execute(() -> {
            ServerLevel serverWorld = server.getLevel(activeWorldKey);
            if (serverWorld == null) {
                return;
            }

            standToTerminal.forEach((uuid, terminal) -> {
                Entity entity = serverWorld.getEntity(uuid);
                if (entity instanceof ArmorStand armorStand) {
                    String label = terminal.isCompleted() ? "Terminal Active" : "Terminal Inactive";
                    armorStand.setCustomName(Component.literal(label));
                    armorStand.setCustomNameVisible(true);
                }
            });
        });
    }

    /**
     * Check if armor stands are currently active.
     */
    public boolean hasArmorStands() {
        return !standToTerminal.isEmpty();
    }

    /**
     * Map a clicked entity back to its terminal, if we spawned it.
     */
    public TerminalPosition getTerminalForEntity(Entity entity) {
        return standToTerminal.get(entity.getUUID());
    }

    private void applyStandFlags(ArmorStand armorStand) {
        armorStand.setInvisible(true);
        armorStand.setNoGravity(true);
        armorStand.setInvulnerable(true);
        armorStand.setSilent(true);

        SynchedEntityData tracker = armorStand.getEntityData();
        byte flags = tracker.get(ArmorStand.DATA_CLIENT_FLAGS);
        // KEEP IT NORMAL SIZE (not small) for bigger hitbox
        flags &= ~ArmorStand.CLIENT_FLAG_SMALL;
        // DISABLE MARKER to make it solid and clickable
        flags &= ~ArmorStand.CLIENT_FLAG_MARKER;
        flags |= ArmorStand.CLIENT_FLAG_NO_BASEPLATE;
        tracker.set(ArmorStand.DATA_CLIENT_FLAGS, flags);

        // CRITICAL: Increase bounding box for easier clicking (4x4x4 blocks instead of default)
        AABB largerBox = new AABB(-2.0, -1.0, -2.0, 2.0, 3.0, 2.0);
        armorStand.setBoundingBox(largerBox.move(armorStand.getX(), armorStand.getY(), armorStand.getZ()));
    }

    private void removeExistingStands(ServerLevel serverWorld) {
        System.out.println("[ArmorStandManager] Removing existing terminal armor stands...");
        int removedCount = 0;

        // Remove stands we spawned previously via UUID tracking
        for (UUID uuid : standToTerminal.keySet()) {
            Entity entity = serverWorld.getEntity(uuid);
            if (entity != null) {
                entity.remove(RemovalReason.DISCARDED);
                removedCount++;
            }
        }

        // ALWAYS use ALL default positions for cleanup (not just active terminals)
        // This ensures we clean up ALL terminals from ALL phases
        Set<Vec3> cleanupPositions = new HashSet<>(Arrays.asList(DEFAULT_TERMINAL_POSITIONS));

        // Also add currently active terminal positions
        for (TerminalPosition terminal : terminalManager.getTerminals()) {
            cleanupPositions.add(terminal.getPosition());
        }

        // Sweep each position for stray armor stands that look like terminals
        for (Vec3 basePos : cleanupPositions) {
            AABB searchBox = AABB.ofSize(basePos.add(0, 1, 0), CLEANUP_RADIUS, CLEANUP_HEIGHT, CLEANUP_RADIUS);
            List<ArmorStand> strayStands = serverWorld.getEntities(
                EntityType.ARMOR_STAND,
                searchBox,
                stand -> {
                    // Remove ANY invisible armor stand with custom name near terminal positions
                    // This catches stands even if names were changed/corrupted
                    if (stand.isInvisible()) {
                        Component customName = stand.getCustomName();
                        if (customName != null) {
                            String name = customName.getString();
                            return name.contains("Terminal") || name.contains("Inactive") || name.contains("Active");
                        }
                        // Also remove invisible stands without names that might be ours
                        return true;
                    }
                    return false;
                }
            );

            for (ArmorStand stand : strayStands) {
                stand.remove(RemovalReason.DISCARDED);
                removedCount++;
            }
        }

        System.out.println("[ArmorStandManager] Removed " + removedCount + " terminal armor stands");
    }

    /**
     * Dynamically find command block near terminal position
     * Searches in a radius around the terminal for any command block type
     * @param serverWorld The server world to search in
     * @param terminalPos The terminal position
     * @return Vec3d of command block center, or null if not found
     */
    private Vec3 findCommandBlockNear(ServerLevel serverWorld, Vec3 terminalPos) {
        int searchRadius = 5; // Search 5 blocks in each direction
        int searchHeight = 10; // Search 10 blocks vertically (down and up)

        BlockPos terminalBlock = BlockPos.containing(terminalPos);

        // Search downwards first (command blocks are usually below terminals)
        for (int yOffset = 0; yOffset <= searchHeight; yOffset++) {
            for (int xOffset = -searchRadius; xOffset <= searchRadius; xOffset++) {
                for (int zOffset = -searchRadius; zOffset <= searchRadius; zOffset++) {
                    BlockPos checkPos = terminalBlock.offset(xOffset, -yOffset, zOffset);
                    net.minecraft.world.level.block.state.BlockState state = serverWorld.getBlockState(checkPos);

                    // Check if it's any type of command block
                    if (state.is(net.minecraft.world.level.block.Blocks.COMMAND_BLOCK) ||
                        state.is(net.minecraft.world.level.block.Blocks.CHAIN_COMMAND_BLOCK) ||
                        state.is(net.minecraft.world.level.block.Blocks.REPEATING_COMMAND_BLOCK)) {

                        // Return center of command block for armor stand spawn
                        return new Vec3(checkPos.getX() + 0.5, checkPos.getY(), checkPos.getZ() + 0.5);
                    }
                }
            }
        }

        // If not found below, search above (just in case)
        for (int yOffset = 1; yOffset <= 3; yOffset++) {
            for (int xOffset = -searchRadius; xOffset <= searchRadius; xOffset++) {
                for (int zOffset = -searchRadius; zOffset <= searchRadius; zOffset++) {
                    BlockPos checkPos = terminalBlock.offset(xOffset, yOffset, zOffset);
                    net.minecraft.world.level.block.state.BlockState state = serverWorld.getBlockState(checkPos);

                    if (state.is(net.minecraft.world.level.block.Blocks.COMMAND_BLOCK) ||
                        state.is(net.minecraft.world.level.block.Blocks.CHAIN_COMMAND_BLOCK) ||
                        state.is(net.minecraft.world.level.block.Blocks.REPEATING_COMMAND_BLOCK)) {

                        return new Vec3(checkPos.getX() + 0.5, checkPos.getY(), checkPos.getZ() + 0.5);
                    }
                }
            }
        }

        return null; // No command block found
    }

    /**
     * Force cleanup all terminal armor stands in current world.
     * Useful for manual cleanup after crashes or restarts.
     * This does a GLOBAL search, not just around terminal positions.
     */
    public void forceCleanupAllTerminalStands() {
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null || mc.level == null) {
            System.out.println("[ArmorStandManager] Cannot force cleanup - server or world is null");
            return;
        }

        ResourceKey<Level> worldKey = mc.level.dimension();

        // Use executeSync to ensure cleanup completes BEFORE returning
        // This prevents race conditions where new stands spawn before old ones are removed
        server.executeIfPossible(() -> {
            ServerLevel serverWorld = server.getLevel(worldKey);
            if (serverWorld == null) {
                return;
            }

            System.out.println("[ArmorStandManager] FORCE cleanup - searching ALL armor stands in world...");
            int removedCount = 0;

            // First do normal cleanup around terminal positions
            removeExistingStands(serverWorld);

            // Then do a GLOBAL search for any terminal-named armor stands anywhere in world
            // This catches stands that might have been spawned at wrong positions
            for (Entity entity : serverWorld.getAllEntities()) {
                if (entity instanceof ArmorStand stand) {
                    Component customName = stand.getCustomName();
                    if (customName != null) {
                        String name = customName.getString();
                        if (name.contains("Terminal") || name.contains("Inactive") || name.contains("Active")) {
                            stand.remove(RemovalReason.DISCARDED);
                            removedCount++;
                        }
                    }
                }
            }

            standToTerminal.clear();
            activeWorldKey = null;
            System.out.println("[ArmorStandManager] FORCE cleanup complete - removed " + removedCount + " additional stands");
        });
    }
}
