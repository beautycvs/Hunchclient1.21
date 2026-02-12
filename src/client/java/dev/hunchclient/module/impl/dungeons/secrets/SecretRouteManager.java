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
import dev.hunchclient.module.impl.dungeons.SecretRoutesModule;
import dev.hunchclient.render.RenderContext;
import dev.hunchclient.util.Renderable;
import dev.hunchclient.util.Tickable;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Secret Route Manager - Progressive waypoint system with pathfinding
 *
 * Features:
 * - Loads secret routes from JSON (Skyblocker + SecretRoutes format)
 * - Progressive waypoint system (waypoint disappears when completed, next appears)
 * - Pathfinding visualization with lines/particles
 * - Through-wall waypoint rendering (Skyblocker style)
 * - Interaction detection (chest, item, bat, lever)
 */
public class SecretRouteManager implements Tickable, Renderable {

    private static final double PATH_SPACING = 0.5d;
    private static final double MAX_VISIBLE_PATH_LENGTH = 28.0d;

    private final Room room;
    private final List<SecretRouteWaypoint> routeWaypoints = new ArrayList<>();
    private int currentWaypointIndex = 0;
    private boolean routeActive = false;
    private boolean autoAdvanceEnabled = true;
    private double autoAdvanceRangeSq = 4.0;
    private boolean renderWaypointBox = true;
    private boolean renderWaypointLabel = true;
    private boolean renderPathLines = true;
    private boolean renderPathNodes = true;
    private boolean renderEtherwarps = true;
    private boolean lastStopManual = false;
    private boolean routeCompleted = false;

    // NEW: Pathfinding & rendering settings
    private boolean usePathfinding = true;
    private boolean useParticles = false;
    private PathParticleRenderer.ParticleRenderMode particleMode = PathParticleRenderer.ParticleRenderMode.GLOW;
    private float lineThickness = 3.0f;

    public SecretRouteManager(Room room) {
        this.room = room;
    }

    /**
     * Load route data from SecretRoutes JSON format
     */
    public void loadRouteFromJson(JsonArray routeJson) {
        this.routeWaypoints.clear();
        this.currentWaypointIndex = 0;
        this.routeActive = false;

        if (routeJson == null || routeJson.isEmpty()) {
            return;
        }
        this.routeCompleted = false;
        this.lastStopManual = false;

        // Parse each secret in the route
        int index = 0;
        for (JsonElement secretElement : routeJson) {
            if (!secretElement.isJsonObject()) continue;
            JsonObject secretObj = secretElement.getAsJsonObject();

            // Get path locations
            List<BlockPos> pathLocations = new ArrayList<>();
            if (secretObj.has("locations")) {
                JsonArray locations = secretObj.getAsJsonArray("locations");
                for (JsonElement locElement : locations) {
                    if (!locElement.isJsonArray()) continue;
                    JsonArray loc = locElement.getAsJsonArray();
                    if (loc.size() >= 3) {
                        BlockPos relativePos = new BlockPos(
                            loc.get(0).getAsInt(),
                            loc.get(1).getAsInt(),
                            loc.get(2).getAsInt()
                        );
                        BlockPos actualPos = room.relativeToActual(relativePos);
                        pathLocations.add(actualPos);
                    }
                }
            }

            // Get secret location
            BlockPos secretPos = null;
            SecretWaypoint.Category secretCategory = SecretWaypoint.Category.DEFAULT;
            if (secretObj.has("secret")) {
                JsonObject secret = secretObj.getAsJsonObject("secret");
                if (secret.has("location")) {
                    JsonArray loc = secret.getAsJsonArray("location");
                    if (loc.size() >= 3) {
                        BlockPos relativePos = new BlockPos(
                            loc.get(0).getAsInt(),
                            loc.get(1).getAsInt(),
                            loc.get(2).getAsInt()
                        );
                        secretPos = room.relativeToActual(relativePos);
                    }
                }
                if (secret.has("type")) {
                    String type = secret.get("type").getAsString();
                    secretCategory = parseCategoryFromType(type);
                }
            }

            // Get etherwarp locations
            List<BlockPos> etherwarps = new ArrayList<>();
            if (secretObj.has("etherwarps")) {
                JsonArray etherwarpArray = secretObj.getAsJsonArray("etherwarps");
                for (JsonElement ethElement : etherwarpArray) {
                    if (!ethElement.isJsonArray()) continue;
                    JsonArray loc = ethElement.getAsJsonArray();
                    if (loc.size() >= 3) {
                        BlockPos relativePos = new BlockPos(
                            loc.get(0).getAsInt(),
                            loc.get(1).getAsInt(),
                            loc.get(2).getAsInt()
                        );
                        BlockPos actualPos = room.relativeToActual(relativePos);
                        etherwarps.add(actualPos);
                    }
                }
            }

            // Collect helper markers (superboom/stonk/interact)
            List<BlockPos> superboomMarkers = parseMarkerLocations(secretObj, "tnts");
            List<BlockPos> stonkMarkers = parseMarkerLocations(secretObj, "mines");
            List<BlockPos> interactMarkers = parseMarkerLocations(secretObj, "interacts");

            // NEW: Parse ender pearl locations and angles
            List<Vec3> enderPearlLocations = new ArrayList<>();
            List<float[]> enderPearlAngles = new ArrayList<>();

            if (secretObj.has("enderpearls")) {
                JsonArray pearlsArray = secretObj.getAsJsonArray("enderpearls");
                for (JsonElement pearlElement : pearlsArray) {
                    if (!pearlElement.isJsonArray()) continue;
                    JsonArray pearlLoc = pearlElement.getAsJsonArray();
                    if (pearlLoc.size() >= 3) {
                        // Pearl locations are stored as absolute world coordinates
                        enderPearlLocations.add(new Vec3(
                            pearlLoc.get(0).getAsDouble(),
                            pearlLoc.get(1).getAsDouble(),
                            pearlLoc.get(2).getAsDouble()
                        ));
                    }
                }
            }

            if (secretObj.has("enderpearlangles")) {
                JsonArray anglesArray = secretObj.getAsJsonArray("enderpearlangles");
                for (JsonElement angleElement : anglesArray) {
                    if (!angleElement.isJsonArray()) continue;
                    JsonArray angles = angleElement.getAsJsonArray();
                    if (angles.size() >= 2) {
                        float pitch = angles.get(0).getAsFloat();
                        float yaw = angles.get(1).getAsFloat();
                        enderPearlAngles.add(new float[]{pitch, yaw});
                    }
                }
            }

            if (secretPos != null) {
                SecretRouteWaypoint waypoint = new SecretRouteWaypoint(
                    index,
                    secretPos,
                    secretCategory,
                    pathLocations,
                    etherwarps,
                    superboomMarkers,
                    stonkMarkers,
                    interactMarkers,
                    enderPearlLocations,
                    enderPearlAngles
                );
                routeWaypoints.add(waypoint);
                index++;
            } else if (!pathLocations.isEmpty()) {
                // Allow legacy SecretRoutes entries missing explicit secret data by treating last path node as secret.
                BlockPos fallbackSecret = pathLocations.get(pathLocations.size() - 1);
                SecretRouteWaypoint waypoint = new SecretRouteWaypoint(
                    index,
                    fallbackSecret,
                    secretCategory,
                    pathLocations,
                    etherwarps,
                    superboomMarkers,
                    stonkMarkers,
                    interactMarkers,
                    enderPearlLocations,
                    enderPearlAngles
                );
                routeWaypoints.add(waypoint);
                index++;
            }
        }

        HunchClient.LOGGER.info("[SecretRoute] Loaded {} waypoints for room {}",
            routeWaypoints.size(), room.getName());
    }

    /**
     * Start the route
     */
    public void startRoute() {
        if (routeWaypoints.isEmpty()) {
            HunchClient.LOGGER.warn("[SecretRoute] Cannot start route - no waypoints loaded");
            return;
        }
        routeActive = true;
        currentWaypointIndex = 0;
        routeCompleted = false;
        lastStopManual = false;
        HunchClient.LOGGER.info("[SecretRoute] Started route with {} waypoints", routeWaypoints.size());
    }

    /**
     * Stop the route
     */
    public void stopRoute() {
        stopRoute(false);
    }

    /**
     * Stop the route with manual flag
     */
    public void stopRoute(boolean manualStop) {
        routeActive = false;
        currentWaypointIndex = 0;
        routeCompleted = !manualStop;
        lastStopManual = manualStop;
        HunchClient.LOGGER.info("[SecretRoute] Stopped route{}", manualStop ? " (manual)" : "");
    }

    /**
     * Skip to next waypoint
     */
    public void nextWaypoint() {
        if (!routeActive || routeWaypoints.isEmpty()) return;

        if (currentWaypointIndex < routeWaypoints.size() - 1) {
            currentWaypointIndex++;
            HunchClient.LOGGER.info("[SecretRoute] Advanced to waypoint {}/{}",
                currentWaypointIndex + 1, routeWaypoints.size());
        } else {
            HunchClient.LOGGER.info("[SecretRoute] Route completed!");
            stopRoute(false);
        }
    }

    /**
     * Go to previous waypoint
     */
    public void previousWaypoint() {
        if (!routeActive || routeWaypoints.isEmpty()) return;

        if (currentWaypointIndex > 0) {
            currentWaypointIndex--;
            HunchClient.LOGGER.info("[SecretRoute] Went back to waypoint {}/{}",
                currentWaypointIndex + 1, routeWaypoints.size());
        }
    }

    /**
     * Get current waypoint
     */
    @Nullable
    public SecretRouteWaypoint getCurrentWaypoint() {
        if (!routeActive || routeWaypoints.isEmpty() || currentWaypointIndex >= routeWaypoints.size()) {
            return null;
        }
        return routeWaypoints.get(currentWaypointIndex);
    }

    /**
     * Check if player is near current secret (auto-advance)
     */
    private void checkAutoAdvance(Minecraft client) {
        if (client.player == null) return;
        if (!autoAdvanceEnabled) return;

        SecretRouteWaypoint current = getCurrentWaypoint();
        if (current == null) return;

        // Auto-advance for ITEM pickups and EXITROUTE (proximity-only waypoints)
        if (!current.category.needsItemPickup() && current.category != SecretWaypoint.Category.EXITROUTE) {
            return;
        }

        Vec3 playerPos = new Vec3(client.player.getX(), client.player.getY(), client.player.getZ());
        double distanceSquared = playerPos.distanceToSqr(Vec3.atCenterOf(current.secretPos));

        // Auto-advance if within 2 blocks of secret
        if (distanceSquared < autoAdvanceRangeSq) {
            nextWaypoint();
        }
    }

    @Override
    public void tick(Minecraft client) {
        if (!routeActive || client.player == null || client.level == null) return;

        checkAutoAdvance(client);

        // Spawn particles every tick if particle mode is enabled
        if (useParticles) {
            SecretRouteWaypoint current = getCurrentWaypoint();
            if (current != null) {
                Vec3 playerPos = new Vec3(client.player.getX(), client.player.getY(), client.player.getZ());
                List<Vec3> pathPoints = buildRenderablePath(playerPos, current, client.level);

                if (pathPoints != null && pathPoints.size() >= 2) {
                    float[] pathColor = resolveWaypointColor(current);
                    PathParticleRenderer.renderPathParticles(pathPoints, pathColor, particleMode);
                }
            }
        }
    }

    @Override
    public void extractRendering(RenderContext context) {
        if (!routeActive) return;

        SecretRouteWaypoint current = getCurrentWaypoint();
        if (current == null) return;

        // Render waypoint at secret location
        renderWaypoint(context, current);

        // Render path lines
        renderPath(context, current);

        // Render etherwarp locations
        renderEtherwarps(context, current);
    }

    /**
     * Render the secret waypoint with Skyblocker-style through-wall rendering
     */
    private void renderWaypoint(RenderContext context, SecretRouteWaypoint waypoint) {
        // Get color from module (fall back to category defaults)
        SecretRoutesModule module = SecretRoutesModule.getInstance();
        float[] color = module != null ? module.getColorForCategory(waypoint.category) : waypoint.category.colorComponents;
        float alpha = color.length > 3 ? color[3] : 1.0f;
        float[] rgb = Arrays.copyOf(color, 3);

        // Render box at secret location
        BlockPos pos = waypoint.secretPos;
        Vec3 center = Vec3.atCenterOf(pos);
        AABB box = new AABB(pos);

        if (renderWaypointBox) {
            context.submitFilledBox(box, rgb, 0.4f * alpha, true);
            context.submitOutlinedBox(box, rgb, 2.0f, true);
        }

        if (renderWaypointLabel) {
            String label = String.format("§e%d/%d §f%s",
                currentWaypointIndex + 1,
                routeWaypoints.size(),
                waypoint.category.toString());

            Vec3 labelPos = new Vec3(center.x, center.y + 1.0, center.z);
            context.submitText(label, labelPos, true, true);
        }
    }

    /**
     * Render path lines in the world (not starting from player)
     * Lines show the calculated route, particles are rendered in tick()
     */
    private void renderPath(RenderContext context, SecretRouteWaypoint waypoint) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;

        // Only render lines here (particles are rendered in tick())
        if (!renderPathLines || useParticles) return;

        Vec3 playerPos = new Vec3(client.player.getX(), client.player.getY(), client.player.getZ());
        List<Vec3> pathPoints = buildRenderablePath(playerPos, waypoint, client.level);

        if (pathPoints == null || pathPoints.size() < 2) return;

        float[] pathColor = resolveWaypointColor(waypoint);

        // Render ONLY the calculated path as 3D lines in the world
        // Do NOT connect to player - just show the route itself
        for (int i = 0; i < pathPoints.size() - 1; i++) {
            Vec3 from = pathPoints.get(i);
            Vec3 to = pathPoints.get(i + 1);
            context.submitLine(from, to, pathColor, 1.0f, lineThickness, true);
        }

        // Optionally render path nodes (only for old-style predefined paths)
        if (renderPathNodes && !waypoint.pathLocations.isEmpty()) {
            for (BlockPos pos : waypoint.pathLocations) {
                AABB waypointBox = new AABB(
                    pos.getX() + 0.4, pos.getY() + 0.4, pos.getZ() + 0.4,
                    pos.getX() + 0.6, pos.getY() + 0.6, pos.getZ() + 0.6
                );
                context.submitFilledBox(waypointBox, pathColor, 0.8f, true);
            }
        }
    }

    /**
     * Render etherwarp locations
     */
    private void renderEtherwarps(RenderContext context, SecretRouteWaypoint waypoint) {
        if (waypoint.etherwarpLocations.isEmpty()) return;
        if (!renderEtherwarps) return;

        // Purple color for etherwarp
        float[] ethColor = {0.6f, 0.0f, 1.0f};

        for (BlockPos pos : waypoint.etherwarpLocations) {
            AABB box = new AABB(pos);

            // Render box
            context.submitFilledBox(box, ethColor, 0.3f, true);
            context.submitOutlinedBox(box, ethColor, 2.0f, true);

            // Label
            Vec3 center = Vec3.atCenterOf(pos);
            Vec3 labelPos = new Vec3(center.x, center.y + 0.5, center.z);
            context.submitText("§dEtherwarp", labelPos, true, true);
        }
    }

    /**
     * Parse category from SecretRoutes type string
     */
    private SecretWaypoint.Category parseCategoryFromType(String type) {
        return switch (type.toLowerCase()) {
            case "interact", "chest" -> SecretWaypoint.Category.CHEST;
            case "item" -> SecretWaypoint.Category.ITEM;
            case "bat" -> SecretWaypoint.Category.BAT;
            case "wither" -> SecretWaypoint.Category.WITHER;
            case "exitroute" -> SecretWaypoint.Category.EXITROUTE;
            default -> SecretWaypoint.Category.DEFAULT;
        };
    }

    public boolean isRouteActive() {
        return routeActive;
    }

    public int getCurrentIndex() {
        return currentWaypointIndex;
    }

    public int getTotalWaypoints() {
        return routeWaypoints.size();
    }

    public boolean wasRouteCompleted() {
        return routeCompleted;
    }

    public boolean wasManuallyStopped() {
        return lastStopManual;
    }

    public void clearManualStopFlag() {
        lastStopManual = false;
    }

    public void setAutoAdvanceEnabled(boolean enabled) {
        this.autoAdvanceEnabled = enabled;
    }

    public void setAutoAdvanceRange(double radius) {
        double clamped = Math.max(0.5d, Math.min(8.0d, radius));
        this.autoAdvanceRangeSq = clamped * clamped;
    }

    public void setRenderWaypointBox(boolean renderWaypointBox) {
        this.renderWaypointBox = renderWaypointBox;
    }

    public void setRenderWaypointLabel(boolean renderWaypointLabel) {
        this.renderWaypointLabel = renderWaypointLabel;
    }

    public void setRenderPathLines(boolean renderPathLines) {
        this.renderPathLines = renderPathLines;
    }

    public void setRenderPathNodes(boolean renderPathNodes) {
        this.renderPathNodes = renderPathNodes;
    }

    public void setRenderEtherwarps(boolean renderEtherwarps) {
        this.renderEtherwarps = renderEtherwarps;
    }

    public void setUsePathfinding(boolean usePathfinding) {
        this.usePathfinding = usePathfinding;
    }

    public void setUseParticles(boolean useParticles) {
        this.useParticles = useParticles;
    }

    public void setParticleMode(PathParticleRenderer.ParticleRenderMode particleMode) {
        this.particleMode = particleMode;
    }

    public void setLineThickness(float lineThickness) {
        this.lineThickness = lineThickness;
    }

    public boolean handleBlockInteract(BlockPos pos) {
        if (!routeActive) return false;
        SecretRouteWaypoint current = getCurrentWaypoint();
        if (current == null) return false;

        if (current.secretPos.equals(pos) || current.pathLocations.contains(pos)) {
            nextWaypoint();
            return true;
        }
        return false;
    }

    public void advanceIfCurrent(BlockPos pos) {
        if (!routeActive) return;
        SecretRouteWaypoint current = getCurrentWaypoint();
        if (current == null) return;

        // Exact match for secret or path locations
        if (current.secretPos.equals(pos) || current.pathLocations.contains(pos)) {
            HunchClient.LOGGER.info("[SecretRoute] Exact match - advancing waypoint");
            nextWaypoint();
            return;
        }

        // For BAT secrets: Use range-based check (bats can be slightly off from waypoint position)
        if (current.category == SecretWaypoint.Category.BAT) {
            Vec3 batPos = Vec3.atCenterOf(pos);
            Vec3 secretPos = Vec3.atCenterOf(current.secretPos);
            double distanceSquared = batPos.distanceToSqr(secretPos);

            // Use same range as Skyblocker: 16 blocks (256.0 squared)
            final double BAT_RANGE_SQ = 16.0 * 16.0;

            HunchClient.LOGGER.info("[SecretRoute] BAT check - batPos={}, secretPos={}, dist²={}, inRange={}",
                batPos, secretPos, distanceSquared, distanceSquared <= BAT_RANGE_SQ);

            // Advance if bat is within 16 blocks of the waypoint (same as Skyblocker)
            if (distanceSquared <= BAT_RANGE_SQ) {
                HunchClient.LOGGER.info("[SecretRoute] BAT in range - advancing waypoint");
                nextWaypoint();
            }
        } else {
            HunchClient.LOGGER.info("[SecretRoute] Current waypoint category={}, pos={}, not advancing for BAT",
                current.category, pos);
        }
    }

    public boolean shouldRenderWaypointBox() {
        return renderWaypointBox;
    }

    public boolean shouldRenderWaypointLabel() {
        return renderWaypointLabel;
    }

    public boolean shouldRenderPathLines() {
        return renderPathLines;
    }

    public boolean shouldRenderPathNodes() {
        return renderPathNodes;
    }

    public boolean shouldRenderEtherwarps() {
        return renderEtherwarps;
    }

    private List<Vec3> buildRenderablePath(Vec3 playerPos, SecretRouteWaypoint waypoint, ClientLevel world) {
        Vec3 secretPos = Vec3.atCenterOf(waypoint.secretPos);
        List<Vec3> rawPoints = new ArrayList<>();

        if (!waypoint.pathLocations.isEmpty()) {
            int firstUnreachedIndex = waypoint.pathLocations.size();
            for (int i = 0; i < waypoint.pathLocations.size(); i++) {
                Vec3 nodeCenter = Vec3.atCenterOf(waypoint.pathLocations.get(i));
                if (playerPos.distanceToSqr(nodeCenter) > autoAdvanceRangeSq) {
                    firstUnreachedIndex = i;
                    break;
                }
            }

            int startIndex;
            if (firstUnreachedIndex == waypoint.pathLocations.size()) {
                startIndex = Math.max(0, waypoint.pathLocations.size() - 1);
            } else {
                startIndex = Math.max(0, firstUnreachedIndex - 1);
            }

            for (int i = startIndex; i < waypoint.pathLocations.size(); i++) {
                rawPoints.add(Vec3.atCenterOf(waypoint.pathLocations.get(i)));
            }

            if (!rawPoints.isEmpty() && !rawPoints.get(rawPoints.size() - 1).equals(secretPos)) {
                rawPoints.add(secretPos);
            }

            if (rawPoints.size() >= 2) {
                return densifyAndClamp(rawPoints);
            }
        }

        if (usePathfinding && world != null) {
            List<Vec3> path = DungeonPathfinder.findPath(playerPos, secretPos, world);
            if (path != null && path.size() >= 2) {
                int startIndex = 1; // skip the player position
                while (startIndex < path.size() && playerPos.distanceToSqr(path.get(startIndex)) <= autoAdvanceRangeSq) {
                    startIndex++;
                }
                startIndex = Math.max(1, startIndex - 1);

                for (int i = startIndex; i < path.size(); i++) {
                    rawPoints.add(path.get(i));
                }

                if (rawPoints.size() >= 2) {
                    return densifyAndClamp(rawPoints);
                }
            }
        }

        Vec3 direction = secretPos.subtract(playerPos);
        if (direction.lengthSqr() < 1.0E-4) {
            return List.of();
        }
        Vec3 startPoint = playerPos.add(direction.normalize().scale(Math.min(PATH_SPACING, direction.length())));
        rawPoints.add(startPoint);
        rawPoints.add(secretPos);
        return densifyAndClamp(rawPoints);
    }

    private float[] resolveWaypointColor(SecretRouteWaypoint waypoint) {
        SecretRoutesModule module = SecretRoutesModule.getInstance();
        float[] baseColor = waypoint.category != null ? waypoint.category.colorComponents : new float[]{0.0f, 1.0f, 0.0f};
        float[] configured = module != null ? module.getColorForCategory(waypoint.category) : null;
        float[] source = configured != null ? configured : baseColor;

        float r = source.length > 0 ? source[0] : 1.0f;
        float g = source.length > 1 ? source[1] : 1.0f;
        float b = source.length > 2 ? source[2] : 1.0f;

        return new float[]{r, g, b};
    }

    private List<BlockPos> parseMarkerLocations(JsonObject secretObj, String key) {
        List<BlockPos> result = new ArrayList<>();
        if (!secretObj.has(key)) {
            return result;
        }

        JsonArray markerArray = secretObj.getAsJsonArray(key);
        for (JsonElement markerElement : markerArray) {
            if (!markerElement.isJsonArray()) {
                continue;
            }
            JsonArray loc = markerElement.getAsJsonArray();
            if (loc.size() < 3) {
                continue;
            }
            BlockPos relativePos = new BlockPos(
                loc.get(0).getAsInt(),
                loc.get(1).getAsInt(),
                loc.get(2).getAsInt()
            );
            result.add(room.relativeToActual(relativePos));
        }
        return result;
    }

    private List<Vec3> densifyAndClamp(List<Vec3> basePoints) {
        if (basePoints == null || basePoints.size() < 2) {
            return basePoints;
        }

        List<Vec3> densified = new ArrayList<>();
        Vec3 previous = basePoints.get(0);
        densified.add(previous);

        for (int i = 1; i < basePoints.size(); i++) {
            Vec3 current = basePoints.get(i);
            double distance = previous.distanceTo(current);
            if (distance < 1.0E-4) {
                continue;
            }
            if (distance <= PATH_SPACING) {
                densified.add(current);
                previous = current;
                continue;
            }

            Vec3 direction = current.subtract(previous).normalize();
            int steps = Math.max(1, (int) Math.ceil(distance / PATH_SPACING));
            for (int step = 1; step <= steps; step++) {
                double travelled = Math.min(step * PATH_SPACING, distance);
                Vec3 point = previous.add(direction.scale(travelled));
                densified.add(point);
            }
            previous = current;
        }

        if (MAX_VISIBLE_PATH_LENGTH <= 0) {
            return densified;
        }

        List<Vec3> clamped = new ArrayList<>();
        clamped.add(densified.get(0));
        double accumulated = 0.0;

        for (int i = 1; i < densified.size(); i++) {
            Vec3 prev = densified.get(i - 1);
            Vec3 curr = densified.get(i);
            double segment = prev.distanceTo(curr);

            if (accumulated + segment <= MAX_VISIBLE_PATH_LENGTH) {
                clamped.add(curr);
                accumulated += segment;
                continue;
            }

            double remaining = MAX_VISIBLE_PATH_LENGTH - accumulated;
            if (remaining > 0.001) {
                Vec3 dir = curr.subtract(prev).normalize();
                Vec3 cappedPoint = prev.add(dir.scale(remaining));
                clamped.add(cappedPoint);
            }
            break;
        }

        return clamped;
    }

    /**
     * Represents a single waypoint in the secret route
     */
    public static class SecretRouteWaypoint {
        public final int index;
        public final BlockPos secretPos;
        public final SecretWaypoint.Category category;
        public final List<BlockPos> pathLocations;
        public final List<BlockPos> etherwarpLocations;
        public final List<BlockPos> superboomLocations;
        public final List<BlockPos> stonkLocations;
        public final List<BlockPos> interactLocations;
        public final List<AABB> pathNodeBoxes;
        public final List<Vec3> pathLabelPositions;
        public final List<AABB> etherwarpBoxes;
        public final List<Vec3> etherwarpLabelPositions;
        public final List<AABB> superboomBoxes;
        public final List<Vec3> superboomLabelPositions;
        public final List<AABB> stonkBoxes;
        public final List<Vec3> stonkLabelPositions;
        public final List<AABB> interactBoxes;
        public final List<Vec3> interactLabelPositions;
        // NEW: Ender pearl throw locations and angles
        public final List<Vec3> enderPearlLocations;
        public final List<float[]> enderPearlAngles; // Each float[] contains [pitch, yaw]

        public SecretRouteWaypoint(int index, BlockPos secretPos, SecretWaypoint.Category category,
                                   List<BlockPos> pathLocations, List<BlockPos> etherwarpLocations,
                                   List<BlockPos> superboomLocations, List<BlockPos> stonkLocations,
                                   List<BlockPos> interactLocations, List<Vec3> enderPearlLocations,
                                   List<float[]> enderPearlAngles) {
            this.index = index;
            this.secretPos = secretPos;
            this.category = category;
            this.pathLocations = new ArrayList<>(pathLocations);
            this.etherwarpLocations = new ArrayList<>(etherwarpLocations);
            this.superboomLocations = new ArrayList<>(superboomLocations);
            this.stonkLocations = new ArrayList<>(stonkLocations);
            this.interactLocations = new ArrayList<>(interactLocations);
            this.pathNodeBoxes = new ArrayList<>(pathLocations.size());
            this.pathLabelPositions = new ArrayList<>(pathLocations.size());
            for (BlockPos pathPos : pathLocations) {
                this.pathNodeBoxes.add(createSizedBox(pathPos, 0.53));
                this.pathLabelPositions.add(Vec3.atCenterOf(pathPos).add(0.0, 0.8, 0.0));
            }

            this.etherwarpBoxes = new ArrayList<>(etherwarpLocations.size());
            this.etherwarpLabelPositions = new ArrayList<>(etherwarpLocations.size());
            for (BlockPos warpPos : etherwarpLocations) {
                this.etherwarpBoxes.add(createSizedBox(warpPos, 0.56));
                this.etherwarpLabelPositions.add(Vec3.atCenterOf(warpPos).add(0.0, 0.95, 0.0));
            }

            this.superboomBoxes = new ArrayList<>(superboomLocations.size());
            this.superboomLabelPositions = new ArrayList<>(superboomLocations.size());
            for (BlockPos boomPos : superboomLocations) {
                this.superboomBoxes.add(createSizedBox(boomPos, 0.56));
                this.superboomLabelPositions.add(Vec3.atCenterOf(boomPos).add(0.0, 0.95, 0.0));
            }

            this.stonkBoxes = new ArrayList<>(stonkLocations.size());
            this.stonkLabelPositions = new ArrayList<>(stonkLocations.size());
            for (BlockPos stonkPos : stonkLocations) {
                this.stonkBoxes.add(createSizedBox(stonkPos, 0.56));
                this.stonkLabelPositions.add(Vec3.atCenterOf(stonkPos).add(0.0, 0.95, 0.0));
            }

            this.interactBoxes = new ArrayList<>(interactLocations.size());
            this.interactLabelPositions = new ArrayList<>(interactLocations.size());
            for (BlockPos interactPos : interactLocations) {
                this.interactBoxes.add(createSizedBox(interactPos, 0.53));
                this.interactLabelPositions.add(Vec3.atCenterOf(interactPos).add(0.0, 0.9, 0.0));
            }

            // NEW: Initialize ender pearl locations and angles
            this.enderPearlLocations = enderPearlLocations != null ? new ArrayList<>(enderPearlLocations) : new ArrayList<>();
            this.enderPearlAngles = enderPearlAngles != null ? new ArrayList<>(enderPearlAngles) : new ArrayList<>();
        }
    }

    private static AABB createSizedBox(BlockPos pos, double halfSize) {
        double minX = pos.getX() + 0.5 - halfSize;
        double minY = pos.getY() + 0.5 - halfSize;
        double minZ = pos.getZ() + 0.5 - halfSize;
        double maxX = pos.getX() + 0.5 + halfSize;
        double maxY = pos.getY() + 0.5 + halfSize;
        double maxZ = pos.getZ() + 0.5 + halfSize;
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
