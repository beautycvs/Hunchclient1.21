/*
* Secret Routes Mod - Secret Route Waypoints for Hypixel Skyblock Dungeons
 * Copyright 2025 yourboykyle & R-aMcC
 *
 * <DO NOT REMOVE THIS COPYRIGHT NOTICE>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package dev.hunchclient.module.impl.dungeons.secrets;

import dev.hunchclient.HunchClient;
import dev.hunchclient.render.RenderContext;
import dev.hunchclient.util.Renderable;
import dev.hunchclient.util.Scheduler;
import dev.hunchclient.util.Tickable;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.ints.IntRBTreeSet;
import it.unimi.dsi.fastutil.ints.IntSortedSet;
import it.unimi.dsi.fastutil.ints.IntSortedSets;
import org.apache.commons.lang3.tuple.MutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2i;
import org.joml.Vector2ic;

import java.util.*;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.arguments.StringRepresentableArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.Vec3;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Dungeon Room - handles room detection, matching, and waypoint management. */
public class Room implements Tickable, Renderable {
    private static final Pattern SECRET_INDEX = Pattern.compile("^(\\d+)");
    private static final Pattern SECRETS = Pattern.compile("§7(\\d{1,2})/(\\d{1,2}) Secrets");
    private static final String LOCKED_CHEST = "That chest is locked!";
    // BAT detection ranges - same as Skyblocker
    private static final double BAT_SOUND_PLAYER_RANGE_SQ = 30.0 * 30.0; // 30 blocks from player (can hear sound)
    private static final double BAT_SOUND_MAX_DISTANCE_SQ = 16.0 * 16.0; // 16 blocks from waypoint (same as Skyblocker)
    protected static final float[] RED_COLOR_COMPONENTS = {1, 0, 0};
    protected static final float[] GREEN_COLOR_COMPONENTS = {0, 1, 0};

    @NotNull
    private final Type type;
    @NotNull
    final Set<Vector2ic> segments;
    protected boolean greenChecked = false;
    private List<SecretWaypoint> superboomSecrets = Collections.emptyList();
    private List<SecretWaypoint> stonkSecrets = Collections.emptyList();

    @NotNull
    private final Shape shape;
    protected Map<String, int[]> roomsData;
    protected List<MutableTriple<Direction, Vector2ic, List<String>>> possibleRooms;
    private Set<BlockPos> checkedBlocks = new HashSet<>();
    protected CompletableFuture<Void> findRoom;
    private int doubleCheckBlocks;
    protected MatchState matchState = MatchState.MATCHING;
    private Table<Integer, BlockPos, SecretWaypoint> secretWaypoints;
    private Table<Integer, BlockPos, MinePoint> minePoints; // NEW: For minepoints
    private String name;
    private Direction direction;
    private Vector2ic physicalCornerPos;

    protected List<Tickable> tickables = new ArrayList<>();
    protected List<Renderable> renderables = new ArrayList<>();
    private BlockPos lastChestSecret;
    private long lastChestSecretTime;

    // NEW: Secret route manager for progressive waypoints
    private SecretRouteManager secretRouteManager;

    public Room(@NotNull Type type, @NotNull Vector2ic... physicalPositions) {
        this.type = type;
        segments = Set.of(physicalPositions);
        IntSortedSet segmentsX = IntSortedSets.unmodifiable(new IntRBTreeSet(segments.stream().mapToInt(Vector2ic::x).toArray()));
        IntSortedSet segmentsY = IntSortedSets.unmodifiable(new IntRBTreeSet(segments.stream().mapToInt(Vector2ic::y).toArray()));
        shape = getShape(segmentsX, segmentsY);
        roomsData = DungeonManager.ROOMS_DATA.getOrDefault("catacombs", Collections.emptyMap()).getOrDefault(shape.shape.toLowerCase(Locale.ENGLISH), Collections.emptyMap());
        possibleRooms = getPossibleRooms(segmentsX, segmentsY);

        // Rooms that don't need scanning (ENTRANCE, MINIBOSS, FAIRY, BLOOD) should be immediately matched
        if (!type.needsScanning()) {
            matchState = MatchState.MATCHED;
            name = type.toString().toLowerCase(Locale.ENGLISH); // Use lowercase for consistency with other room names

            // Calculate proper direction based on possible directions
            Direction[] possibleDirections = getPossibleDirections(segmentsX, segmentsY);
            direction = possibleDirections.length > 0 ? possibleDirections[0] : Direction.NW;

            // Calculate physical corner position based on direction
            physicalCornerPos = DungeonMapUtils.getPhysicalCornerPos(direction, segmentsX, segmentsY);

            // Load custom minepoints for non-scanning rooms (like ENTRANCE)
            minePoints = HashBasedTable.create();
            DungeonManager.getCustomMinepoints(name).values().forEach(customPoint -> {
                // If the saved direction doesn't match current direction, rotate the coordinates
                BlockPos relativePos = customPoint.relativePos();
                if (customPoint.direction() != direction) {
                    relativePos = rotateRelativePos(relativePos, customPoint.direction(), direction);
                }
                BlockPos actualPos = DungeonMapUtils.relativeToActual(direction, physicalCornerPos, relativePos);
                MinePoint minePoint = new MinePoint(customPoint.index(), relativePos, customPoint.name(), actualPos, true);
                minePoints.put(customPoint.index(), actualPos, minePoint);
            });
        }
    }

    @NotNull
    public Type getType() {
        return type;
    }

    public boolean isMatched() {
        return matchState == MatchState.DOUBLE_CHECKING || matchState == MatchState.MATCHED;
    }

    public String getName() {
        return name;
    }

    public Direction getDirection() {
        return direction;
    }

    /**
     * Sets the entrance direction based on Mort's position relative to the player.
     * Mort always faces the player at spawn, so we can determine the direction from his position.
     */
    public void setDirectionFromMort(Vec3 mortPos, Vec3 playerPos) {
        if (type != Type.ENTRANCE) {
            return; // Only for entrance
        }

        // Calculate the direction from player to Mort
        double dx = mortPos.x - playerPos.x;
        double dz = mortPos.z - playerPos.z;

        // Determine which axis has larger difference
        Direction newDirection;
        if (Math.abs(dx) > Math.abs(dz)) {
            // X-axis dominant
            newDirection = dx > 0 ? Direction.NE : Direction.SW;
        } else {
            // Z-axis dominant
            newDirection = dz > 0 ? Direction.SE : Direction.NW;
        }

        // If direction changed, recalculate corner position and reload minepoints
        if (newDirection != direction) {
            direction = newDirection;

            // Recalculate physical corner based on new direction
            IntSortedSet segmentsX = IntSortedSets.unmodifiable(new IntRBTreeSet(segments.stream().mapToInt(Vector2ic::x).toArray()));
            IntSortedSet segmentsY = IntSortedSets.unmodifiable(new IntRBTreeSet(segments.stream().mapToInt(Vector2ic::y).toArray()));
            physicalCornerPos = DungeonMapUtils.getPhysicalCornerPos(direction, segmentsX, segmentsY);

            // Reload minepoints with correct direction
            minePoints.clear();
            DungeonManager.getCustomMinepoints(name).values().forEach(customPoint -> {
                BlockPos relativePos = customPoint.relativePos();
                if (customPoint.direction() != direction) {
                    relativePos = rotateRelativePos(relativePos, customPoint.direction(), direction);
                }
                BlockPos actualPos = DungeonMapUtils.relativeToActual(direction, physicalCornerPos, relativePos);
                MinePoint minePoint = new MinePoint(customPoint.index(), relativePos, customPoint.name(), actualPos, true);
                minePoints.put(customPoint.index(), actualPos, minePoint);
            });
        }
    }

    public Vector2ic getPhysicalCornerPos() {
        return physicalCornerPos;
    }

    @Override
    public String toString() {
        return "Room{type=%s, segments=%s, shape=%s, matchState=%s, name=%s, direction=%s, physicalCornerPos=%s}".formatted(type, Arrays.toString(segments.toArray()), shape, matchState, name, direction, physicalCornerPos);
    }

    @NotNull
    private Shape getShape(IntSortedSet segmentsX, IntSortedSet segmentsY) {
        return switch (type) {
            case PUZZLE -> Shape.PUZZLE;
            case TRAP -> Shape.TRAP;
            default -> switch (segments.size()) {
                case 1 -> Shape.ONE_BY_ONE;
                case 2 -> Shape.ONE_BY_TWO;
                case 3 -> segmentsX.size() == 2 && segmentsY.size() == 2 ? Shape.L_SHAPE : Shape.ONE_BY_THREE;
                case 4 -> segmentsX.size() == 2 && segmentsY.size() == 2 ? Shape.TWO_BY_TWO : Shape.ONE_BY_FOUR;
                default -> throw new IllegalArgumentException("There are no matching room shapes with this set of physical positions: " + Arrays.toString(segments.toArray()));
            };
        };
    }

    private List<MutableTriple<Direction, Vector2ic, List<String>>> getPossibleRooms(IntSortedSet segmentsX, IntSortedSet segmentsY) {
        List<String> possibleDirectionRooms = new ArrayList<>(roomsData.keySet());
        List<MutableTriple<Direction, Vector2ic, List<String>>> possibleRooms = new ArrayList<>();
        for (Direction direction : getPossibleDirections(segmentsX, segmentsY)) {
            possibleRooms.add(MutableTriple.of(direction, DungeonMapUtils.getPhysicalCornerPos(direction, segmentsX, segmentsY), possibleDirectionRooms));
        }
        return possibleRooms;
    }

    @NotNull
    private Direction[] getPossibleDirections(IntSortedSet segmentsX, IntSortedSet segmentsY) {
        return switch (shape) {
            case ONE_BY_ONE, TWO_BY_TWO, PUZZLE, TRAP -> Direction.values();
            case ONE_BY_TWO, ONE_BY_THREE, ONE_BY_FOUR -> {
                if (segmentsX.size() > 1 && segmentsY.size() == 1) {
                    yield new Direction[]{Direction.NW, Direction.SE};
                } else if (segmentsX.size() == 1 && segmentsY.size() > 1) {
                    yield new Direction[]{Direction.NE, Direction.SW};
                }
                throw new IllegalArgumentException("Shape " + shape.shape + " does not match segments: " + Arrays.toString(segments.toArray()));
            }
            case L_SHAPE -> {
                if (!segments.contains(new Vector2i(segmentsX.firstInt(), segmentsY.firstInt()))) {
                    yield new Direction[]{Direction.SW};
                } else if (!segments.contains(new Vector2i(segmentsX.firstInt(), segmentsY.lastInt()))) {
                    yield new Direction[]{Direction.SE};
                } else if (!segments.contains(new Vector2i(segmentsX.lastInt(), segmentsY.firstInt()))) {
                    yield new Direction[]{Direction.NW};
                } else if (!segments.contains(new Vector2i(segmentsX.lastInt(), segmentsY.lastInt()))) {
                    yield new Direction[]{Direction.NE};
                }
                throw new IllegalArgumentException("Shape " + shape.shape + " does not match segments: " + Arrays.toString(segments.toArray()));
            }
        };
    }

    public <T extends Tickable & Renderable> void addSubProcess(T process) {
        tickables.add(process);
        renderables.add(process);
    }

    @Override
    public void tick(Minecraft client) {
        if (client.level == null) {
            return;
        }

        for (Tickable tickable : tickables) {
            tickable.tick(client);
        }

        // Room scanning and matching
        if (!type.needsScanning() || matchState != MatchState.MATCHING && matchState != MatchState.DOUBLE_CHECKING || !DungeonManager.isRoomsLoaded() || findRoom != null && !findRoom.isDone()) {
            return;
        }
        LocalPlayer player = client.player;
        if (player == null) {
            return;
        }
        findRoom = CompletableFuture.runAsync(() -> {
            // PERFORMANCE: Reduced scan range from 15 to 8 blocks (70% fewer blocks: 3,179 vs 10,571)
            // Still sufficient for room detection while being much faster
            for (BlockPos pos : BlockPos.betweenClosed(player.blockPosition().offset(-8, -5, -8), player.blockPosition().offset(8, 5, 8))) {
                if (segments.contains(DungeonMapUtils.getPhysicalRoomPos(pos)) && notInDoorway(pos) && checkedBlocks.add(pos) && checkBlock(client.level, pos)) {
                    break;
                }
            }
        }).exceptionally(e -> {
            HunchClient.LOGGER.error("[Dungeon Secrets] Encountered an unknown exception while matching room {}", this, e);
            return null;
        });
    }

    private static boolean notInDoorway(BlockPos pos) {
        if (pos.getY() < 66 || pos.getY() > 73) {
            return true;
        }
        int x = Math.floorMod(pos.getX() - 8, 32);
        int z = Math.floorMod(pos.getZ() - 8, 32);
        return (x < 13 || x > 17 || z > 2 && z < 28) && (z < 13 || z > 17 || x > 2 && x < 28);
    }

    protected boolean checkBlock(ClientLevel world, BlockPos pos) {
        byte id = DungeonManager.NUMERIC_ID.getByte(BuiltInRegistries.BLOCK.getKey(world.getBlockState(pos).getBlock()).toString());
        if (id == 0) {
            return false;
        }
        for (MutableTriple<Direction, Vector2ic, List<String>> directionRooms : possibleRooms) {
            int block = posIdToInt(DungeonMapUtils.actualToRelative(directionRooms.getLeft(), directionRooms.getMiddle(), pos), id);
            List<String> possibleDirectionRooms = new ArrayList<>();
            for (String room : directionRooms.getRight()) {
                if (Arrays.binarySearch(roomsData.get(room), block) >= 0) {
                    possibleDirectionRooms.add(room);
                }
            }
            directionRooms.setRight(possibleDirectionRooms);
        }

        int matchingRoomsSize = possibleRooms.stream().map(Triple::getRight).mapToInt(Collection::size).sum();
        if (matchingRoomsSize == 0) synchronized (this) {
            matchState = MatchState.FAILED;
            HunchClient.LOGGER.warn("[Dungeon Secrets] No dungeon room matched after checking {} block(s) including double checking {} block(s)", checkedBlocks.size(), doubleCheckBlocks);
            Scheduler.INSTANCE.schedule(() -> matchState = MatchState.MATCHING, 50);
            reset();
            return true;
        }
        else if (matchingRoomsSize == 1) {
            if (matchState == MatchState.MATCHING) {
                Triple<Direction, Vector2ic, List<String>> directionRoom = possibleRooms.stream().filter(directionRooms -> directionRooms.getRight().size() == 1).findAny().orElseThrow();
                name = directionRoom.getRight().getFirst();
                direction = directionRoom.getLeft();
                physicalCornerPos = directionRoom.getMiddle();
                HunchClient.LOGGER.info("[Dungeon Secrets] Room {} matched after checking {} block(s), starting double checking", name, checkedBlocks.size());

                // Send chat notification
                net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
                if (client.player != null) {
                    try {
                        client.player.displayClientMessage(net.minecraft.network.chat.Component.literal("§a[DungeonMap] >> Room detected: §e" + name + " §7(direction: " + direction + ")"), false);
                    } catch (Exception e) {
                        HunchClient.LOGGER.error("[Dungeon Secrets] Failed to send room detection message", e);
                    }
                }

                roomMatched();
                return false;
            } else if (matchState == MatchState.DOUBLE_CHECKING && ++doubleCheckBlocks >= 10) {
                matchState = MatchState.MATCHED;
                HunchClient.LOGGER.info("[Dungeon Secrets] Room {} confirmed after checking {} block(s) including double checking {} block(s)", name, checkedBlocks.size(), doubleCheckBlocks);

                // Send confirmation notification
                net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
                if (client.player != null) {
                    int minepointCount = minePoints != null ? minePoints.size() : 0;
                    if (minepointCount > 0) {
                        try {
                            client.player.displayClientMessage(net.minecraft.network.chat.Component.literal("§a[DungeonMap] Loaded §e" + minepointCount + " §aminepoints!"), false);
                        } catch (Exception e) {
                            HunchClient.LOGGER.error("[Dungeon Secrets] Failed to send minepoints message", e);
                        }
                    }
                }

                discard();
                return true;
            }
            return false;
        } else {
            HunchClient.LOGGER.debug("[Dungeon Secrets] {} room(s) remaining after checking {} block(s)", matchingRoomsSize, checkedBlocks.size());
            return false;
        }
    }

    protected int posIdToInt(BlockPos pos, byte id) {
        return pos.getX() << 24 | pos.getY() << 16 | pos.getZ() << 8 | id;
    }

    private void roomMatched() {
        secretWaypoints = HashBasedTable.create();
        minePoints = HashBasedTable.create(); // NEW: Initialize minepoints table

        // Load secret waypoints
        JsonArray secretWaypointsJson = DungeonManager.getRoomWaypoints(name);
        if (secretWaypointsJson != null) {
            for (JsonElement waypointElement : secretWaypointsJson) {
                JsonObject waypoint = waypointElement.getAsJsonObject();
                String secretName = waypoint.get("secretName").getAsString();
                Matcher secretIndexMatcher = SECRET_INDEX.matcher(secretName);
                int secretIndex = secretIndexMatcher.find() ? Integer.parseInt(secretIndexMatcher.group(1)) : 0;
                BlockPos pos = DungeonMapUtils.relativeToActual(direction, physicalCornerPos, waypoint);
                secretWaypoints.put(secretIndex, pos, new SecretWaypoint(secretIndex, waypoint, secretName, pos));
            }
            superboomSecrets = secretWaypoints.values().stream()
                .filter(sw -> sw.category == SecretWaypoint.Category.SUPERBOOM)
                .collect(Collectors.toUnmodifiableList());
            stonkSecrets = secretWaypoints.values().stream()
                .filter(sw -> sw.category == SecretWaypoint.Category.STONK)
                .collect(Collectors.toUnmodifiableList());
        } else {
            superboomSecrets = Collections.emptyList();
            stonkSecrets = Collections.emptyList();
        }

        // NEW: Load mine points
        JsonArray minePointsJson = DungeonManager.getRoomMinePoints(name);
        if (minePointsJson != null) {
            for (JsonElement pointElement : minePointsJson) {
                JsonObject point = pointElement.getAsJsonObject();
                String pointName = point.get("secretName").getAsString(); // Reuse secretName format
                Matcher indexMatcher = SECRET_INDEX.matcher(pointName);
                int pointIndex = indexMatcher.find() ? Integer.parseInt(indexMatcher.group(1)) : 0;
                BlockPos relativePos = new BlockPos(point.get("x").getAsInt(), point.get("y").getAsInt(), point.get("z").getAsInt());
                BlockPos pos = DungeonMapUtils.relativeToActual(direction, physicalCornerPos, relativePos);
                minePoints.put(pointIndex, pos, new MinePoint(pointIndex, point, pointName, pos, relativePos));
            }
        }

        // Load custom minepoints (relative positions stored client-side)
        DungeonManager.getCustomMinepoints(name).values().forEach(customPoint -> {
            // If the saved direction doesn't match current direction, rotate the coordinates
            BlockPos relativePos = customPoint.relativePos();
            if (customPoint.direction() != direction) {
                relativePos = rotateRelativePos(relativePos, customPoint.direction(), direction);
            }
            BlockPos actualPos = DungeonMapUtils.relativeToActual(direction, physicalCornerPos, relativePos);
            MinePoint minePoint = new MinePoint(customPoint.index(), relativePos, customPoint.name(), actualPos, true);
            minePoints.put(customPoint.index(), actualPos, minePoint);
        });

        DungeonManager.getCustomWaypoints(name).values().forEach(this::addCustomWaypoint);

        // NEW: Load secret routes
        secretRouteManager = new SecretRouteManager(this);
        JsonArray routesJson = DungeonManager.getRoomRoutes(name);
        if (routesJson != null) {
            secretRouteManager.loadRouteFromJson(routesJson);
            addSubProcess(secretRouteManager);
            HunchClient.LOGGER.info("[Room] Loaded secret route for room {}", name);
        }

        matchState = MatchState.DOUBLE_CHECKING;
    }

    private void addCustomWaypoint(SecretWaypoint relativeWaypoint) {
        SecretWaypoint actualWaypoint = relativeWaypoint.relativeToActual(this);
        secretWaypoints.put(actualWaypoint.secretIndex, actualWaypoint.pos, actualWaypoint);
    }

    protected void reset() {
        IntSortedSet segmentsX = IntSortedSets.unmodifiable(new IntRBTreeSet(segments.stream().mapToInt(Vector2ic::x).toArray()));
        IntSortedSet segmentsY = IntSortedSets.unmodifiable(new IntRBTreeSet(segments.stream().mapToInt(Vector2ic::y).toArray()));
        possibleRooms = getPossibleRooms(segmentsX, segmentsY);
        checkedBlocks = new HashSet<>();
        doubleCheckBlocks = 0;
        secretWaypoints = null;
        minePoints = null;
        secretRouteManager = null;
        name = null;
        direction = null;
        physicalCornerPos = null;
        superboomSecrets = Collections.emptyList();
        stonkSecrets = Collections.emptyList();
    }

    private void discard() {
        roomsData = null;
        possibleRooms = null;
        checkedBlocks = null;
        doubleCheckBlocks = 0;
    }

    public BlockPos actualToRelative(BlockPos pos) {
        return DungeonMapUtils.actualToRelative(direction, physicalCornerPos, pos);
    }

    public Vec3 actualToRelative(Vec3 pos) {
        return DungeonMapUtils.actualToRelative(direction, physicalCornerPos, pos);
    }

    public BlockPos relativeToActual(BlockPos pos) {
        return DungeonMapUtils.relativeToActual(direction, physicalCornerPos, pos);
    }

    public Vec3 relativeToActual(Vec3 pos) {
        return DungeonMapUtils.relativeToActual(direction, physicalCornerPos, pos);
    }

    @Override
    public void extractRendering(RenderContext context) {
        for (Renderable renderable : renderables) {
            renderable.extractRendering(context);
        }

        synchronized (this) {
            // Render secret waypoints if enabled
            if (isMatched() && secretWaypoints != null) {
                for (SecretWaypoint secretWaypoint : secretWaypoints.values()) {
                    if (secretWaypoint.shouldRender()) {
                        secretWaypoint.extractRendering(context);
                    }
                }
            }

            // NEW: Render mine points
            if (isMatched() && minePoints != null) {
                for (MinePoint minePoint : minePoints.values()) {
                    if (minePoint.shouldRender()) {
                        minePoint.extractRendering(context);
                    }
                }
            }
        }
    }

    protected void onChatMessage(String message) {
        if (LOCKED_CHEST.equals(message) && lastChestSecretTime + 1000 > System.currentTimeMillis() && lastChestSecret != null) {
            // Handle locked chest
        }
    }

    protected void onUseBlock(Level world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if ((state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST)) && lastChestSecretTime + 1000 < System.currentTimeMillis() || state.is(Blocks.PLAYER_HEAD) || state.is(Blocks.PLAYER_WALL_HEAD)) {
            if (secretWaypoints != null) {
                secretWaypoints.column(pos).values().stream().filter(SecretWaypoint::needsInteraction).findAny()
                        .ifPresent(SecretWaypoint::setFound);
            }
            if (state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST)) {
                lastChestSecret = pos;
                lastChestSecretTime = System.currentTimeMillis();
            }
        } else if (state.is(Blocks.LEVER)) {
            if (secretWaypoints != null) {
                secretWaypoints.column(pos).values().stream().filter(SecretWaypoint::isLever).forEach(SecretWaypoint::setFound);
            }
        }

        if (secretRouteManager != null) {
            secretRouteManager.handleBlockInteract(pos);
        }
    }

    protected void onItemPickup(ItemEntity itemEntity) {
        if (secretWaypoints == null) return;
        if (SecretWaypoint.SECRET_ITEMS.stream().noneMatch(itemEntity.getItem().getHoverName().getString()::contains)) {
            return;
        }
        secretWaypoints.values().stream().filter(SecretWaypoint::needsItemPickup)
                .min(Comparator.comparingDouble(SecretWaypoint.getSquaredDistanceToFunction(itemEntity)))
                .filter(SecretWaypoint.getRangePredicate(itemEntity))
                .ifPresent(SecretWaypoint::setFound);
    }

    protected void onBatRemoved(AmbientCreature bat) {
        if (secretWaypoints == null) return;

        // Ignore fake bats (e.g., Spirit Sceptre) by requiring vanilla bat type
        if (bat.getType() != EntityType.BAT) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }

        // Only consider bats that die within 5 blocks of the player (limit Sceptre chain kills)
        if (!new Vec3(bat.getX(), bat.getY(), bat.getZ()).closerThan(new Vec3(client.player.getX(), client.player.getY(), client.player.getZ()), 5.0)) {
            return;
        }

        secretWaypoints.values().stream()
            .filter(SecretWaypoint::isBat)
            .min(Comparator.comparingDouble(SecretWaypoint.getSquaredDistanceToFunction(bat)))
            .filter(SecretWaypoint.getRangePredicate(bat))
            .ifPresent(secret -> {
                secret.setFound();
                if (secretRouteManager != null) {
                    secretRouteManager.advanceIfCurrent(secret.pos);
                }
            });
    }

    protected void onBatDeathSound(Vec3 soundPos) {
        if (secretWaypoints == null) return;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }

        if (soundPos.distanceToSqr(new Vec3(client.player.getX(), client.player.getY(), client.player.getZ())) > BAT_SOUND_PLAYER_RANGE_SQ) {
            return;
        }

        secretWaypoints.values().stream()
            .filter(SecretWaypoint::isBat)
            .min(Comparator.comparingDouble(secret -> secret.centerPos.distanceToSqr(soundPos)))
            .ifPresent(secret -> {
                double distanceSq = secret.centerPos.distanceToSqr(soundPos);
                if (distanceSq > BAT_SOUND_MAX_DISTANCE_SQ) {
                    return;
                }
                if (secret.isFound()) {
                    return;
                }
                secret.setFound();
                if (secretRouteManager != null) {
                    secretRouteManager.advanceIfCurrent(secret.pos);
                }
            });
    }

    protected boolean markSecrets(int secretIndex, boolean found) {
        if (secretWaypoints == null) return false;
        Map<BlockPos, SecretWaypoint> secret = secretWaypoints.row(secretIndex);
        if (secret.isEmpty()) {
            return false;
        } else {
            secret.values().forEach(found ? SecretWaypoint::setFound : SecretWaypoint::setMissing);
            return true;
        }
    }

    protected void markAllSecrets(boolean found) {
        if (secretWaypoints == null) return;
        secretWaypoints.values().forEach(found ? SecretWaypoint::setFound : SecretWaypoint::setMissing);
    }

    protected int getSecretCount() {
        return secretWaypoints != null ? secretWaypoints.rowMap().size() : 0;
    }

    public Collection<SecretWaypoint> getSecretWaypoints() {
        return secretWaypoints != null ? secretWaypoints.values() : Collections.emptyList();
    }

    public List<SecretWaypoint> getSuperboomSecrets() {
        return superboomSecrets;
    }

    public List<SecretWaypoint> getStonkSecrets() {
        return stonkSecrets;
    }

    // NEW: MinePoint management
    public Collection<MinePoint> getMinePoints() {
        return minePoints != null ? minePoints.values() : Collections.emptyList();
    }

    public void addMinePoint(int index, BlockPos relativePos, String name) {
        if (minePoints == null) minePoints = HashBasedTable.create();
        BlockPos actualPos = relativeToActual(relativePos);
        MinePoint point = new MinePoint(index, relativePos, name, actualPos, true);
        minePoints.put(index, actualPos, point);
        HunchClient.LOGGER.info("[Room] Added mine point {} at {}", name, actualPos);
    }

    public void removeMinePoint(BlockPos relativePos) {
        if (minePoints == null) {
            return;
        }
        BlockPos actualPos = relativeToActual(relativePos);
        minePoints.column(actualPos).clear();
    }

    // NEW: Get secret route manager
    @Nullable
    public SecretRouteManager getSecretRouteManager() {
        return secretRouteManager;
    }

    // Enums
    public enum Type {
        ENTRANCE(MapColor.PLANT.getPackedId(MapColor.Brightness.HIGH)),
        ROOM(MapColor.COLOR_ORANGE.getPackedId(MapColor.Brightness.LOWEST)),
        PUZZLE(MapColor.COLOR_MAGENTA.getPackedId(MapColor.Brightness.HIGH)),
        TRAP(MapColor.COLOR_ORANGE.getPackedId(MapColor.Brightness.HIGH)),
        MINIBOSS(MapColor.COLOR_YELLOW.getPackedId(MapColor.Brightness.HIGH)),
        FAIRY(MapColor.COLOR_PINK.getPackedId(MapColor.Brightness.HIGH)),
        BLOOD(MapColor.FIRE.getPackedId(MapColor.Brightness.HIGH)),
        UNKNOWN(MapColor.COLOR_GRAY.getPackedId(MapColor.Brightness.NORMAL));

        final byte color;

        Type(byte color) {
            this.color = color;
        }

        private boolean needsScanning() {
            return switch (this) {
                case ROOM, PUZZLE, TRAP -> true;
                default -> false;
            };
        }
    }

    protected enum Shape {
        ONE_BY_ONE("1x1"),
        ONE_BY_TWO("1x2"),
        ONE_BY_THREE("1x3"),
        ONE_BY_FOUR("1x4"),
        L_SHAPE("L-shape"),
        TWO_BY_TWO("2x2"),
        PUZZLE("puzzle"),
        TRAP("trap");

        final String shape;

        Shape(String shape) {
            this.shape = shape;
        }

        @Override
        public String toString() {
            return shape;
        }
    }

    /**
     * Rotates relative coordinates from one direction to another.
     * This is needed when minepoints were saved with a different room direction.
     *
     * @param pos The original relative position
     * @param fromDir The direction the coordinates were saved in
     * @param toDir The target direction to rotate to
     * @return The rotated relative position
     */
    private static BlockPos rotateRelativePos(BlockPos pos, Direction fromDir, Direction toDir) {
        if (fromDir == toDir) {
            return pos; // No rotation needed
        }

        // Calculate rotation steps (each step is 90 degrees clockwise)
        int rotationSteps = getRotationSteps(fromDir, toDir);

        BlockPos rotated = pos;
        for (int i = 0; i < rotationSteps; i++) {
            rotated = rotateClockwise90(rotated);
        }

        return rotated;
    }

    /**
     * Calculates how many 90-degree clockwise rotations are needed to go from one direction to another.
     */
    private static int getRotationSteps(Direction from, Direction to) {
        // Order: NW(0) -> NE(1) -> SE(2) -> SW(3) -> NW(0)
        int fromIndex = switch (from) {
            case NW -> 0;
            case NE -> 1;
            case SE -> 2;
            case SW -> 3;
        };
        int toIndex = switch (to) {
            case NW -> 0;
            case NE -> 1;
            case SE -> 2;
            case SW -> 3;
        };
        return (toIndex - fromIndex + 4) % 4;
    }

    /**
     * Rotates a relative position 90 degrees clockwise around the room center.
     * For a 32x32 room, the transformation is: (x, y, z) -> (31-z, y, x)
     */
    private static BlockPos rotateClockwise90(BlockPos pos) {
        // For a 32x32 room (0-31 range), rotating 90° clockwise:
        // (x, z) -> (z, 31-x)
        int newX = pos.getZ();
        int newZ = 31 - pos.getX();
        return new BlockPos(newX, pos.getY(), newZ);
    }

    public enum Direction implements StringRepresentable {
        NW("northwest"), NE("northeast"), SW("southwest"), SE("southeast");
        private static final Codec<Direction> CODEC = StringRepresentable.fromEnum(Direction::values);
        private final String name;

        Direction(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        static class DirectionArgumentType extends StringRepresentableArgument<Direction> {
            DirectionArgumentType() {
                super(CODEC, Direction::values);
            }

            static DirectionArgumentType direction() {
                return new DirectionArgumentType();
            }
        }
    }

    protected enum MatchState {
        MATCHING, DOUBLE_CHECKING, MATCHED, FAILED
    }
}
