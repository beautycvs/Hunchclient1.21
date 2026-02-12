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
package dev.hunchclient.module.impl.dungeons;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.hunchclient.module.ConfigurableModule;
import dev.hunchclient.module.Module;
import dev.hunchclient.module.SettingsProvider;
import dev.hunchclient.module.impl.dungeons.secrets.DungeonManager;
import dev.hunchclient.module.impl.dungeons.secrets.PathParticleRenderer;
import dev.hunchclient.module.impl.dungeons.secrets.Room;
import dev.hunchclient.module.impl.dungeons.secrets.SecretRouteManager;
import dev.hunchclient.module.impl.dungeons.secrets.SecretRouteManager.SecretRouteWaypoint;
import dev.hunchclient.module.impl.dungeons.secrets.SecretWaypoint;
import dev.hunchclient.module.setting.CheckboxSetting;
import dev.hunchclient.module.setting.ColorPickerSetting;
import dev.hunchclient.module.setting.DropdownSetting;
import dev.hunchclient.module.setting.ModuleSetting;
import dev.hunchclient.module.setting.SliderSetting;
import dev.hunchclient.render.GlowESPRenderer;
import dev.hunchclient.render.primitive.PrimitiveCollector;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;

/**
 * Secret Routes Module - Progressive waypoint system with pathfinding
 *
 * Features:
 * - Start/stop secret routes in current room
 * - Next/previous waypoint navigation
 * - Through-wall waypoint rendering (Skyblocker style)
 * - Pathfinding visualization with lines
 * - Auto-advance on secret collection (configurable)
 *
 * Keybinds:
 * - R: Start/stop route
 * - N: Next waypoint
 * - B: Previous waypoint
 */
public class SecretRoutesModule extends Module implements ConfigurableModule, SettingsProvider {

    private static SecretRoutesModule INSTANCE;
    private static boolean rendererRegistered;

    private static final Minecraft MC = Minecraft.getInstance();

    private static KeyMapping startStopKey;
    private static KeyMapping nextWaypointKey;
    private static KeyMapping previousWaypointKey;
    private static boolean keybindingsRegistered;

    private boolean autoStartRoutes = false;
    private boolean autoRestartAfterCompletion = false;
    private boolean autoAdvanceEnabled = true;
    private float autoAdvanceRadius = 2.0f;
    private boolean showWaypointBox = true;
    private boolean showWaypointLabel = true;
    private boolean showPathLines = true;
    private boolean showPathNodes = false; // Path nodes disabled by default - we use pathfinding now
    private boolean showEtherwarpMarkers = true;
    private boolean showChatMessages = true;
    private boolean rainbowMode = false; // SKYHANNI STYLE: Rainbow/Chroma colors!

    // ESP RENDERING MODES
    private ESPMode waypointESPMode = ESPMode.FILLED_OUTLINE; // Main waypoint box ESP mode
    private ESPMode markerESPMode = ESPMode.OUTLINE; // AOTV, Stonk, Superboom marker ESP mode

    // NEW PATHFINDING & RENDERING SETTINGS
    private boolean usePathfinding = true; // Use A* pathfinding instead of simple lines
    private boolean useParticles = false; // Use 3D particles instead of lines
    private PathParticleRenderer.ParticleRenderMode particleMode = PathParticleRenderer.ParticleRenderMode.GLOW;
    private float lineThickness = 6.0f; // Line thickness (1.0 - 10.0) - thicker for better visibility

    private final EnumMap<SecretWaypoint.Category, float[]> categoryColors = new EnumMap<>(SecretWaypoint.Category.class);

    private Room lastAutoStartRoom;
    private boolean autoStartTriggered;

    public SecretRoutesModule() {
        super("SecretRoutes", "Progressive waypoint system for dungeon secrets", Category.DUNGEONS, true);
        INSTANCE = this;
        ensureKeybindingsRegistered();
        if (!rendererRegistered) {
            dev.hunchclient.render.WorldRenderExtractionCallback.EVENT.register(this::render);
            net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents.BEFORE_DEBUG_RENDER.register(this::renderLabels);
            rendererRegistered = true;
        }

        for (dev.hunchclient.module.impl.dungeons.secrets.SecretWaypoint.Category category : dev.hunchclient.module.impl.dungeons.secrets.SecretWaypoint.Category.values()) {
            categoryColors.put(category, createColorArray(category.colorComponents, 1.0f));
        }
    }

    @Override
    protected void onEnable() {
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
    }

    @Override
    protected void onDisable() {
        Room currentRoom = DungeonManager.getCurrentRoom();
        if (currentRoom != null) {
            SecretRouteManager routeManager = currentRoom.getSecretRouteManager();
            if (routeManager != null && routeManager.isRouteActive()) {
                routeManager.stopRoute(false);
            }
        }
        lastAutoStartRoom = null;
        autoStartTriggered = false;
    }

    private static void ensureKeybindingsRegistered() {
        if (keybindingsRegistered) {
            return;
        }

        if (startStopKey == null) {
            startStopKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "Start/Stop Secret Route",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                KeyMapping.Category.MISC
            ));
        }

        if (nextWaypointKey == null) {
            nextWaypointKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "Next Waypoint",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_N,
                KeyMapping.Category.MISC
            ));
        }

        if (previousWaypointKey == null) {
            previousWaypointKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "Previous Waypoint",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_B,
                KeyMapping.Category.MISC
            ));
        }

        keybindingsRegistered = true;
    }

    public static SecretRoutesModule getInstance() {
        return INSTANCE;
    }

    public float[] getColorForCategory(SecretWaypoint.Category category) {
        if (category == null) {
            return new float[]{1.0f, 1.0f, 1.0f, 1.0f};
        }
        return categoryColors.computeIfAbsent(category, cat -> createColorArray(cat.colorComponents, 1.0f));
    }

    private int getCategoryColorArgb(SecretWaypoint.Category category) {
        float[] color = getColorForCategory(category);
        int a = channel(color.length > 3 ? color[3] : 1.0f);
        int r = channel(color.length > 0 ? color[0] : 1.0f);
        int g = channel(color.length > 1 ? color[1] : 1.0f);
        int b = channel(color.length > 2 ? color[2] : 1.0f);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private void setCategoryColor(SecretWaypoint.Category category, int argb) {
        float a = ((argb >> 24) & 0xFF) / 255f;
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;

        float[] stored = categoryColors.computeIfAbsent(category, cat -> new float[4]);
        stored[0] = clamp01(r);
        stored[1] = clamp01(g);
        stored[2] = clamp01(b);
        stored[3] = clamp01(a);

        float[] base = category.colorComponents;
        if (base.length >= 3) {
            base[0] = stored[0];
            base[1] = stored[1];
            base[2] = stored[2];
        }
    }

    private static float[] createColorArray(float[] rgb, float alpha) {
        float r = rgb.length > 0 ? rgb[0] : 1.0f;
        float g = rgb.length > 1 ? rgb[1] : 1.0f;
        float b = rgb.length > 2 ? rgb[2] : 1.0f;
        return new float[]{clamp01(r), clamp01(g), clamp01(b), clamp01(alpha)};
    }

    private static int channel(float value) {
        return Math.min(255, Math.max(0, Math.round(clamp01(value) * 255f)));
    }

    private static float clamp01(float value) {
        if (value < 0.0f) return 0.0f;
        if (value > 1.0f) return 1.0f;
        return value;
    }

    private static String formatCategoryName(SecretWaypoint.Category category) {
        String raw = category.toString();
        if (raw.isEmpty()) {
            return "Default";
        }
        String[] parts = raw.split("[_-]");
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty()) continue;
            if (builder.length() > 0) builder.append(' ');
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.toString();
    }

    private void onClientTick(Minecraft client) {
        if (!isEnabled() || client.player == null) return;

        Room currentRoom = DungeonManager.getCurrentRoom();
        if (currentRoom == null || !currentRoom.isMatched()) {
            lastAutoStartRoom = null;
            autoStartTriggered = false;
            return;
        }

        SecretRouteManager routeManager = currentRoom.getSecretRouteManager();
        if (routeManager == null) return;

        applyRouteSettings(routeManager);
        handleAutoStart(currentRoom, routeManager);

        if (startStopKey.consumeClick()) {
            handleStartStop(routeManager);
        }

        if (nextWaypointKey.consumeClick()) {
            handleNextWaypoint(routeManager);
        }

        if (previousWaypointKey.consumeClick()) {
            handlePreviousWaypoint(routeManager);
        }
    }

    private void handleStartStop(SecretRouteManager routeManager) {
        if (routeManager.isRouteActive()) {
            routeManager.stopRoute(true);
            autoStartTriggered = true;
            notifyRouteStopped(true);
            return;
        }

        if (routeManager.getTotalWaypoints() == 0) {
            notifyNoRouteAvailable();
            return;
        }

        routeManager.startRoute();
        autoStartTriggered = true;
        notifyRouteStarted(routeManager, false);
    }

    private void handleNextWaypoint(SecretRouteManager routeManager) {
        if (!routeManager.isRouteActive()) {
            notifyNoActiveRoute();
            return;
        }

        routeManager.nextWaypoint();

        if (routeManager.isRouteActive()) {
            notifyWaypointStatus(routeManager);
        } else if (routeManager.wasRouteCompleted()) {
            notifyRouteCompleted();
        } else {
            notifyRouteStopped(false);
        }
    }

    private void handlePreviousWaypoint(SecretRouteManager routeManager) {
        if (!routeManager.isRouteActive()) {
            notifyNoActiveRoute();
            return;
        }

        routeManager.previousWaypoint();
        notifyWaypointStatus(routeManager);
    }

    private void applyRouteSettings(SecretRouteManager routeManager) {
        routeManager.setAutoAdvanceEnabled(autoAdvanceEnabled);
        routeManager.setAutoAdvanceRange(autoAdvanceRadius);
        routeManager.setRenderWaypointBox(showWaypointBox);
        routeManager.setRenderWaypointLabel(showWaypointLabel);
        routeManager.setRenderPathLines(showPathLines);
        routeManager.setRenderPathNodes(showPathNodes);
        routeManager.setRenderEtherwarps(showEtherwarpMarkers);

        // New pathfinding & rendering settings
        routeManager.setUsePathfinding(usePathfinding);
        routeManager.setUseParticles(useParticles);
        routeManager.setParticleMode(particleMode);
        routeManager.setLineThickness(lineThickness);
    }

    private void handleAutoStart(Room room, SecretRouteManager routeManager) {
        if (!autoStartRoutes) {
            return;
        }

        if (room != lastAutoStartRoom) {
            lastAutoStartRoom = room;
            autoStartTriggered = false;
        }

        if (routeManager.isRouteActive()) {
            autoStartTriggered = true;
            return;
        }

        if (routeManager.wasManuallyStopped()) {
            return;
        }

        if (routeManager.wasRouteCompleted()) {
            if (!autoRestartAfterCompletion) {
                return;
            }
            autoStartTriggered = false;
        }

        if (!autoStartTriggered && routeManager.getTotalWaypoints() > 0) {
            routeManager.startRoute();
            autoStartTriggered = true;
            notifyRouteStarted(routeManager, true);
        }
    }

    private void notifyRouteStarted(SecretRouteManager routeManager, boolean autoTriggered) {
        String prefix = autoTriggered ? "§a[SecretRoutes] Auto route started" : "§a[SecretRoutes] Route started";
        sendMessage(prefix + " - " + routeManager.getTotalWaypoints() + " secrets");
    }

    private void notifyRouteStopped(boolean manualStop) {
        sendMessage((manualStop ? "§c" : "§e") + "[SecretRoutes] Route stopped");
    }

    private void notifyRouteCompleted() {
        sendMessage("§a[SecretRoutes] §6Route completed!");
    }

    private void notifyWaypointStatus(SecretRouteManager routeManager) {
        sendMessage(String.format("§a[SecretRoutes] Waypoint %d/%d",
            routeManager.getCurrentIndex() + 1,
            routeManager.getTotalWaypoints()));
    }

    private void notifyNoActiveRoute() {
        sendMessage("§c[SecretRoutes] No active route - press R to start");
    }

    private void notifyNoRouteAvailable() {
        sendMessage("§c[SecretRoutes] No route available for this room");
    }

    private void sendMessage(String message) {
        if (!showChatMessages) return;
        if (MC.player != null) {
            MC.player.displayClientMessage(Component.literal(message), false);
        }
    }

    private void setAutoAdvanceRadius(float radius) {
        autoAdvanceRadius = Math.max(1.0f, Math.min(6.0f, radius));
    }

    @Override
    public JsonObject saveConfig() {
        JsonObject data = new JsonObject();
        data.addProperty("autoStartRoutes", autoStartRoutes);
        data.addProperty("autoRestartAfterCompletion", autoRestartAfterCompletion);
        data.addProperty("autoAdvanceEnabled", autoAdvanceEnabled);
        data.addProperty("autoAdvanceRadius", autoAdvanceRadius);
        data.addProperty("showWaypointBox", showWaypointBox);
        data.addProperty("showWaypointLabel", showWaypointLabel);
        data.addProperty("showPathLines", showPathLines);
        data.addProperty("showPathNodes", showPathNodes);
        data.addProperty("showEtherwarpMarkers", showEtherwarpMarkers);
        data.addProperty("showChatMessages", showChatMessages);
        data.addProperty("rainbowMode", rainbowMode);

        // New pathfinding & rendering settings
        data.addProperty("usePathfinding", usePathfinding);
        data.addProperty("useParticles", useParticles);
        data.addProperty("particleMode", particleMode.ordinal());
        data.addProperty("lineThickness", lineThickness);

        // ESP Mode settings
        data.addProperty("waypointESPMode", waypointESPMode.ordinal());
        data.addProperty("markerESPMode", markerESPMode.ordinal());

        JsonObject colors = new JsonObject();
        for (SecretWaypoint.Category category : SecretWaypoint.Category.values()) {
            colors.addProperty(category.name(), getCategoryColorArgb(category));
        }
        data.add("categoryColors", colors);
        return data;
    }

    @Override
    public void loadConfig(JsonObject data) {
        if (data == null) return;
        if (data.has("autoStartRoutes")) autoStartRoutes = data.get("autoStartRoutes").getAsBoolean();
        if (data.has("autoRestartAfterCompletion")) autoRestartAfterCompletion = data.get("autoRestartAfterCompletion").getAsBoolean();
        if (data.has("autoAdvanceEnabled")) autoAdvanceEnabled = data.get("autoAdvanceEnabled").getAsBoolean();
        if (data.has("autoAdvanceRadius")) setAutoAdvanceRadius(data.get("autoAdvanceRadius").getAsFloat());
        if (data.has("showWaypointBox")) showWaypointBox = data.get("showWaypointBox").getAsBoolean();
        if (data.has("showWaypointLabel")) showWaypointLabel = data.get("showWaypointLabel").getAsBoolean();
        if (data.has("showPathLines")) showPathLines = data.get("showPathLines").getAsBoolean();
        if (data.has("showPathNodes")) showPathNodes = data.get("showPathNodes").getAsBoolean();
        if (data.has("showEtherwarpMarkers")) showEtherwarpMarkers = data.get("showEtherwarpMarkers").getAsBoolean();
        if (data.has("showChatMessages")) showChatMessages = data.get("showChatMessages").getAsBoolean();
        if (data.has("rainbowMode")) rainbowMode = data.get("rainbowMode").getAsBoolean();

        // New pathfinding & rendering settings
        if (data.has("usePathfinding")) usePathfinding = data.get("usePathfinding").getAsBoolean();
        if (data.has("useParticles")) useParticles = data.get("useParticles").getAsBoolean();
        if (data.has("particleMode")) {
            int modeIndex = data.get("particleMode").getAsInt();
            PathParticleRenderer.ParticleRenderMode[] modes = PathParticleRenderer.ParticleRenderMode.values();
            if (modeIndex >= 0 && modeIndex < modes.length) {
                particleMode = modes[modeIndex];
            }
        }
        if (data.has("lineThickness")) lineThickness = data.get("lineThickness").getAsFloat();

        // ESP Mode settings
        if (data.has("waypointESPMode")) {
            int modeIndex = data.get("waypointESPMode").getAsInt();
            ESPMode[] modes = ESPMode.values();
            if (modeIndex >= 0 && modeIndex < modes.length) {
                waypointESPMode = modes[modeIndex];
            }
        }
        if (data.has("markerESPMode")) {
            int modeIndex = data.get("markerESPMode").getAsInt();
            ESPMode[] modes = ESPMode.values();
            if (modeIndex >= 0 && modeIndex < modes.length) {
                markerESPMode = modes[modeIndex];
            }
        }

        if (data.has("categoryColors") && data.get("categoryColors").isJsonObject()) {
            JsonObject colors = data.getAsJsonObject("categoryColors");
            for (SecretWaypoint.Category category : SecretWaypoint.Category.values()) {
                if (colors.has(category.name())) {
                    setCategoryColor(category, colors.get(category.name()).getAsInt());
                }
            }
        }
    }

    @Override
    public List<ModuleSetting> getSettings() {
        List<ModuleSetting> settings = new ArrayList<>();

        settings.add(new CheckboxSetting(
            "Auto Start Routes",
            "Automatically start the route when entering a matched room",
            "secretroutes_auto_start",
            () -> autoStartRoutes,
            val -> autoStartRoutes = val
        ));

        settings.add(new CheckboxSetting(
            "Restart After Completion",
            "Restart the route automatically after it finishes",
            "secretroutes_auto_restart",
            () -> autoRestartAfterCompletion,
            val -> autoRestartAfterCompletion = val
        ));

        settings.add(new CheckboxSetting(
            "Auto Advance",
            "Advance automatically when you reach the secret",
            "secretroutes_auto_advance",
            () -> autoAdvanceEnabled,
            val -> autoAdvanceEnabled = val
        ));

        settings.add(new SliderSetting(
            "Auto-Advance Range",
            "Distance from the secret to trigger auto-advance",
            "secretroutes_auto_advance_range",
            1.0f, 6.0f,
            () -> autoAdvanceRadius,
            val -> setAutoAdvanceRadius(val.floatValue())
        ).withDecimals(1).withSuffix(" blocks"));

        settings.add(new CheckboxSetting(
            "Secret Box",
            "Show the box around the current secret",
            "secretroutes_waypoint_box",
            () -> showWaypointBox,
            val -> showWaypointBox = val
        ));

        settings.add(new CheckboxSetting(
            "Secret Label",
            "Show the label above the secret waypoint",
            "secretroutes_waypoint_label",
            () -> showWaypointLabel,
            val -> showWaypointLabel = val
        ));

        settings.add(new CheckboxSetting(
            "Path Lines",
            "Show path lines between waypoints",
            "secretroutes_path_lines",
            () -> showPathLines,
            val -> showPathLines = val
        ));

        settings.add(new CheckboxSetting(
            "Path Nodes",
            "Show small markers at each path node",
            "secretroutes_path_nodes",
            () -> showPathNodes,
            val -> showPathNodes = val
        ));

        settings.add(new CheckboxSetting(
            "Etherwarp Markers",
            "Render etherwarp locations for the current waypoint",
            "secretroutes_etherwarp_markers",
            () -> showEtherwarpMarkers,
            val -> showEtherwarpMarkers = val
        ));

        settings.add(new CheckboxSetting(
            "Chat Messages",
            "Show chat feedback for route actions",
            "secretroutes_chat_messages",
            () -> showChatMessages,
            val -> showChatMessages = val
        ));

        // === PATHFINDING & RENDERING SETTINGS ===
        settings.add(new CheckboxSetting(
            "Use Pathfinding",
            "Use A* pathfinding to navigate around walls (GPS-style)",
            "secretroutes_use_pathfinding",
            () -> usePathfinding,
            val -> usePathfinding = val
        ));

        settings.add(new CheckboxSetting(
            "Use Particles",
            "Render path as 3D particles instead of lines",
            "secretroutes_use_particles",
            () -> useParticles,
            val -> useParticles = val
        ));

        settings.add(new DropdownSetting(
            "Particle Type",
            "Type of particles to use for the path",
            "secretroutes_particle_type",
            new String[]{"Glow", "Flame", "Portal", "Enchant", "End Rod"},
            () -> particleMode.ordinal(),
            val -> particleMode = PathParticleRenderer.ParticleRenderMode.values()[val]
        ));

        settings.add(new SliderSetting(
            "Line Thickness",
            "Thickness of the path lines (3D lines only)",
            "secretroutes_line_thickness",
            1.0f, 10.0f,
            () -> lineThickness,
            val -> lineThickness = val.floatValue()
        ).withDecimals(1).withSuffix(" px"));

        // === ESP MODE SETTINGS ===
        settings.add(new DropdownSetting(
            "Waypoint ESP Mode",
            "Rendering style for the main waypoint box",
            "secretroutes_waypoint_esp_mode",
            ESPMode.getDisplayNames(),
            () -> waypointESPMode.ordinal(),
            val -> waypointESPMode = ESPMode.values()[val]
        ));

        settings.add(new DropdownSetting(
            "Marker ESP Mode",
            "Rendering style for markers (AOTV, Stonk, Superboom, etc.)",
            "secretroutes_marker_esp_mode",
            ESPMode.getDisplayNames(),
            () -> markerESPMode.ordinal(),
            val -> markerESPMode = ESPMode.values()[val]
        ));

        settings.add(new CheckboxSetting(
            "Rainbow Mode",
            "Enable rainbow/chroma colors like Skyhanni (animated)",
            "secretroutes_rainbow_mode",
            () -> rainbowMode,
            val -> rainbowMode = val
        ));
        for (SecretWaypoint.Category category : SecretWaypoint.Category.values()) {
            final SecretWaypoint.Category cat = category;
            settings.add(new ColorPickerSetting(
                formatCategoryName(cat) + " Color",
                "Waypoint color for " + formatCategoryName(cat) + " secrets",
                "secretroutes_color_" + cat.name().toLowerCase(Locale.ROOT),
                () -> getCategoryColorArgb(cat),
                value -> setCategoryColor(cat, value)
            ));
        }

        return settings;
    }

    /**
     * Render callback using PrimitiveCollector
     * Called during END_EXTRACTION phase - no camera transformation needed
     */
    private void render(PrimitiveCollector collector) {
        if (!isEnabled() || MC.player == null || MC.level == null) {
            return;
        }

        if (!dev.hunchclient.util.DungeonUtils.isInDungeon()) {
            return;
        }

        Room currentRoom = DungeonManager.getCurrentRoom();
        if (currentRoom == null || !currentRoom.isMatched()) {
            return;
        }

        SecretRouteManager routeManager = currentRoom.getSecretRouteManager();
        if (routeManager == null || !routeManager.isRouteActive()) {
            return;
        }

        SecretRouteWaypoint waypoint = routeManager.getCurrentWaypoint();
        if (waypoint == null) {
            return;
        }

        Vec3 playerPos = new Vec3(MC.player.getX(), MC.player.getY(), MC.player.getZ());

        float[] colour = getColorForCategory(waypoint.category);
        float[] colorComponents = new float[]{
            safeColorComponent(colour, 0),
            safeColorComponent(colour, 1),
            safeColorComponent(colour, 2),
            safeColorComponent(colour, 3)
        };

        Vec3 secretCenter = Vec3.atCenterOf(waypoint.secretPos);

        // Render waypoint box with configurable ESP mode
        if (showWaypointBox) {
            AABB box = new AABB(waypoint.secretPos).deflate(0.02);
            renderBoxWithESP(collector, box, colorComponents, waypointESPMode, 3.0f);
        }

        // Render path lines (dashed style like 1.21.8) - ALWAYS render if enabled
        if (showPathLines) {
            List<Vec3> points = buildPathPoints(playerPos, waypoint, secretCenter);

            // Render dashed lines (like 1.21.8 version)
            if (points != null && points.size() >= 2) {
                renderDashedPath(collector, points, colorComponents, lineThickness);
            }
        }

        // Render path node markers
        if (showPathNodes && waypoint.pathNodeBoxes != null && !waypoint.pathNodeBoxes.isEmpty()) {
            float[] nodeColor = new float[]{
                clamp01(colorComponents[0] * 0.6f),
                clamp01(colorComponents[1] * 0.6f),
                clamp01(colorComponents[2] * 0.6f),
                colorComponents[3]
            };
            int startIndex = 0;
            if (waypoint.pathLabelPositions != null && !waypoint.pathLabelPositions.isEmpty()) {
                double radiusSq = autoAdvanceRadius * autoAdvanceRadius;
                for (int i = 0; i < waypoint.pathLabelPositions.size(); i++) {
                    Vec3 nodePos = waypoint.pathLabelPositions.get(i);
                    if (playerPos.distanceToSqr(nodePos) > radiusSq) {
                        startIndex = i;
                        break;
                    }
                    startIndex = Math.min(i + 1, waypoint.pathNodeBoxes.size() - 1);
                }
            }

            final int maxMarkers = 2;
            for (int offset = 0; offset < maxMarkers; offset++) {
                int idx = startIndex + offset;
                if (idx >= waypoint.pathNodeBoxes.size()) {
                    break;
                }
                AABB nodeBox = waypoint.pathNodeBoxes.get(idx);
                collector.submitFilledBox(nodeBox, nodeColor, 0.2f, true);
                collector.submitOutlinedBox(nodeBox, nodeColor, 1.5f, true);
            }
        }

        // Render superboom markers (always shown) - with configurable ESP mode
        float[] superboomColour = getColorForCategory(SecretWaypoint.Category.SUPERBOOM);
        if (waypoint.superboomBoxes != null && !waypoint.superboomBoxes.isEmpty()) {
            for (AABB boomBox : waypoint.superboomBoxes) {
                renderBoxWithESP(collector, boomBox, superboomColour, markerESPMode, 2.0f);
            }
        } else {
            currentRoom.getSuperboomSecrets().stream()
                .filter(secret -> secret.category == SecretWaypoint.Category.SUPERBOOM)
                .forEach(secret -> {
                    AABB boomBox = new AABB(secret.pos);
                    renderBoxWithESP(collector, boomBox, superboomColour, markerESPMode, 2.0f);
                });
        }

        // Render stonk markers (always shown, independent of path nodes) - with configurable ESP mode
        float[] stonkColour = getColorForCategory(SecretWaypoint.Category.STONK);
        if (waypoint.stonkBoxes != null && !waypoint.stonkBoxes.isEmpty()) {
            for (AABB stonkBox : waypoint.stonkBoxes) {
                renderBoxWithESP(collector, stonkBox, stonkColour, markerESPMode, 1.8f);
            }
        } else {
            currentRoom.getStonkSecrets().stream()
                .filter(secret -> secret.category == SecretWaypoint.Category.STONK)
                .forEach(secret -> {
                    AABB stonkBox = new AABB(secret.pos);
                    renderBoxWithESP(collector, stonkBox, stonkColour, markerESPMode, 1.8f);
                });
        }

        // Render etherwarp markers (AOTV category) - shown based on showEtherwarpMarkers toggle - with configurable ESP mode
        if (showEtherwarpMarkers && waypoint.etherwarpBoxes != null && !waypoint.etherwarpBoxes.isEmpty()) {
            float[] etherwarpColor = getColorForCategory(SecretWaypoint.Category.AOTV);
            for (AABB etherwarpBox : waypoint.etherwarpBoxes) {
                renderBoxWithESP(collector, etherwarpBox, etherwarpColor, markerESPMode, 2.5f);
            }
        }

        // Render interact markers (WITHER, CHEST, ITEM, BAT categories) - use waypoint's actual category color - with configurable ESP mode
        if (showPathNodes && waypoint.interactBoxes != null && !waypoint.interactBoxes.isEmpty()) {
            // Use the waypoint's actual category color instead of hardcoded WITHER
            float[] interactColour = getColorForCategory(waypoint.category);
            for (AABB interactBox : waypoint.interactBoxes) {
                renderBoxWithESP(collector, interactBox, interactColour, markerESPMode, 1.5f);
            }
        }
    }

    private float safeColorComponent(float[] components, int index) {
        if (components == null || components.length <= index) {
            return 1.0f;
        }
        return clamp01(components[index]);
    }

    /**
     * Render a box with the specified ESP mode
     */
    private void renderBoxWithESP(PrimitiveCollector collector, AABB box, float[] colorComponents, ESPMode mode, float lineWidth) {
        switch (mode) {
            case OUTLINE:
                collector.submitOutlinedBox(box, colorComponents, lineWidth, true);
                break;
            case FILLED:
                collector.submitFilledBox(box, colorComponents, 0.35f, true);
                break;
            case FILLED_OUTLINE:
                collector.submitFilledBox(box, colorComponents, 0.35f, true);
                collector.submitOutlinedBox(box, colorComponents, lineWidth, true);
                break;
            case GLOW:
                // GPU-based post-processing glow with Gaussian blur
                PoseStack matrices = new PoseStack();
                GlowESPRenderer.queueGlowBox(matrices, box, colorComponents, 1.0f, 15.0f);
                break;
            case CHAMS:
                // Chams: Filled box with higher opacity through walls
                collector.submitFilledBox(box, colorComponents, 0.6f, true);
                break;
            case WIREFRAME:
                // Wireframe: Multiple thin outlines for mesh effect
                collector.submitOutlinedBox(box, colorComponents, 1.0f, true);
                collector.submitOutlinedBox(box.deflate(0.05), colorComponents, 0.5f, true);
                break;
            case NONE:
                // Don't render anything
                break;
        }
    }

    /**
     * Build path points for rendering - uses predefined paths or pathfinding
     */
    private List<Vec3> buildPathPoints(Vec3 playerPos, SecretRouteWaypoint waypoint, Vec3 secretCenter) {
        List<Vec3> points = new ArrayList<>();

        // Use predefined path locations if available
        if (waypoint.pathLocations != null && !waypoint.pathLocations.isEmpty()) {
            int nextUnreachedIndex = -1;
            for (int i = 0; i < waypoint.pathLocations.size(); i++) {
                Vec3 nodePos = Vec3.atCenterOf(waypoint.pathLocations.get(i));
                if (playerPos.distanceTo(nodePos) > autoAdvanceRadius) {
                    nextUnreachedIndex = i;
                    break;
                }
            }

            points.add(playerPos);

            if (nextUnreachedIndex >= 0) {
                for (int i = nextUnreachedIndex; i < waypoint.pathLocations.size(); i++) {
                    points.add(Vec3.atCenterOf(waypoint.pathLocations.get(i)));
                }
            }

            points.add(secretCenter);
            return points;
        }

        // Try pathfinding if enabled and we have a world
        if (usePathfinding && MC.level != null) {
            List<Vec3> pathfindingPath = dev.hunchclient.module.impl.dungeons.secrets.DungeonPathfinder.findPath(
                playerPos,
                secretCenter,
                MC.level
            );

            if (pathfindingPath != null && pathfindingPath.size() >= 2) {
                // Skip player position and already-reached nodes
                int startIndex = 1;
                while (startIndex < pathfindingPath.size() &&
                       playerPos.distanceToSqr(pathfindingPath.get(startIndex)) <= autoAdvanceRadius * autoAdvanceRadius) {
                    startIndex++;
                }
                startIndex = Math.max(1, startIndex - 1);

                for (int i = startIndex; i < pathfindingPath.size(); i++) {
                    points.add(pathfindingPath.get(i));
                }
                return points;
            }
        }

        // Fallback: Direct line from player to secret (ALWAYS render something)
        points.add(playerPos);
        points.add(secretCenter);
        return points;
    }

    /**
     * Render dashed path lines (like 1.21.8 version)
     * Progressively renders only nearby segments to give a "following the route" feeling
     */
    private void renderDashedPath(PrimitiveCollector collector, List<Vec3> points, float[] colorComponents, float lineWidth) {
        final double dashLength = 0.5; // Length of each dash
        final double gapLength = 0.3;  // Length of each gap
        final double totalPattern = dashLength + gapLength;

        for (int i = 0; i < points.size() - 1; i++) {
            Vec3 start = points.get(i);
            Vec3 end = points.get(i + 1);

            double segmentLength = start.distanceTo(end);
            if (segmentLength < 0.01) {
                continue; // Skip very short segments
            }

            Vec3 direction = end.subtract(start).normalize();

            // Render dashed segments along this line
            double traveled = 0.0;
            while (traveled < segmentLength) {
                double dashStart = traveled;
                double dashEnd = Math.min(traveled + dashLength, segmentLength);

                if (dashEnd > dashStart) {
                    Vec3 segmentStart = start.add(direction.scale(dashStart));
                    Vec3 segmentEnd = start.add(direction.scale(dashEnd));

                    // Create new array each time - BufferBuilder stores reference, not values!
                    Vec3[] dashPoints = new Vec3[]{segmentStart, segmentEnd};
                    collector.submitLinesFromPoints(dashPoints, colorComponents, 1.0f, lineWidth, true);
                }

                traveled += totalPattern;
            }
        }
    }

    // PERFORMANCE: Cache for rainbow colors to avoid per-frame calculations
    private static final float[][] RAINBOW_COLOR_CACHE = new float[360][3]; // Pre-calculated rainbow colors
    private static boolean rainbowCacheInitialized = false;

    /**
     * Initialize rainbow color cache (called once on first use)
     */
    private static void initRainbowCache() {
        if (rainbowCacheInitialized) return;

        // Pre-calculate all 360 hue values (S=0.7, V=1.0)
        for (int hue = 0; hue < 360; hue++) {
            // Fast HSV->RGB conversion (no java.awt.Color!)
            float h = hue / 360f;
            float s = 0.7f;
            float v = 1.0f;

            int hi = (int)(h * 6);
            float f = h * 6 - hi;
            float p = v * (1 - s);
            float q = v * (1 - f * s);
            float t = v * (1 - (1 - f) * s);

            switch (hi) {
                case 0: RAINBOW_COLOR_CACHE[hue] = new float[]{v, t, p}; break;
                case 1: RAINBOW_COLOR_CACHE[hue] = new float[]{q, v, p}; break;
                case 2: RAINBOW_COLOR_CACHE[hue] = new float[]{p, v, t}; break;
                case 3: RAINBOW_COLOR_CACHE[hue] = new float[]{p, q, v}; break;
                case 4: RAINBOW_COLOR_CACHE[hue] = new float[]{t, p, v}; break;
                default: RAINBOW_COLOR_CACHE[hue] = new float[]{v, p, q}; break;
            }
        }

        rainbowCacheInitialized = true;
    }

    /**
     * Get chroma/rainbow color based on index (OPTIMIZED with cache)
     */
    private float[] getChromaColor(int index) {
        if (!rainbowMode) {
            return null; // Use default color
        }

        // Initialize cache on first use
        if (!rainbowCacheInitialized) {
            initRainbowCache();
        }

        // Calculate hue and look up in pre-calculated table
        long time = System.currentTimeMillis();
        int hue = (int)((time / 50f + index * 30f) % 360f);

        // Return cached color (no allocation, no AWT call!)
        return RAINBOW_COLOR_CACHE[hue];
    }

    /**
     * Render text labels for waypoints and markers
     * Called during AFTER_ENTITIES event for 3D text rendering
     */
    private void renderLabels(net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext context) {
        if (!isEnabled() || !showWaypointLabel || MC.player == null || MC.level == null) {
            return;
        }

        if (!dev.hunchclient.util.DungeonUtils.isInDungeon()) {
            return;
        }

        Room currentRoom = DungeonManager.getCurrentRoom();
        if (currentRoom == null || !currentRoom.isMatched()) {
            return;
        }

        SecretRouteManager routeManager = currentRoom.getSecretRouteManager();
        if (routeManager == null || !routeManager.isRouteActive()) {
            return;
        }

        SecretRouteWaypoint waypoint = routeManager.getCurrentWaypoint();
        if (waypoint == null) {
            return;
        }

        com.mojang.blaze3d.vertex.PoseStack matrices = new com.mojang.blaze3d.vertex.PoseStack();
        net.minecraft.client.Camera camera = MC.gameRenderer.getMainCamera();

        // Render main secret label
        Vec3 secretPos = Vec3.atCenterOf(waypoint.secretPos).add(0, 1.0, 0);
        String secretLabel = formatCategoryName(waypoint.category) + " §7(§f" + (waypoint.index + 1) + "§7/§f" + routeManager.getTotalWaypoints() + "§7)";
        float[] color = getColorForCategory(waypoint.category);
        renderWorldLabel(matrices, camera, secretPos, secretLabel, color);

        // Render etherwarp labels if enabled
        if (showEtherwarpMarkers && waypoint.etherwarpLabelPositions != null) {
            for (int i = 0; i < waypoint.etherwarpLabelPositions.size(); i++) {
                Vec3 labelPos = waypoint.etherwarpLabelPositions.get(i);
                String label = "§6AOTV §7#" + (i + 1);
                float[] aotvColor = getColorForCategory(SecretWaypoint.Category.AOTV);
                renderWorldLabel(matrices, camera, labelPos, label, aotvColor);
            }
        }

        // Render superboom labels
        if (waypoint.superboomLabelPositions != null) {
            for (int i = 0; i < waypoint.superboomLabelPositions.size(); i++) {
                Vec3 labelPos = waypoint.superboomLabelPositions.get(i);
                String label = "§cSUPERBOOM §7#" + (i + 1);
                float[] boomColor = getColorForCategory(SecretWaypoint.Category.SUPERBOOM);
                renderWorldLabel(matrices, camera, labelPos, label, boomColor);
            }
        }

        // Render stonk labels
        if (waypoint.stonkLabelPositions != null) {
            for (int i = 0; i < waypoint.stonkLabelPositions.size(); i++) {
                Vec3 labelPos = waypoint.stonkLabelPositions.get(i);
                String label = "§5STONK §7#" + (i + 1);
                float[] stonkColor = getColorForCategory(SecretWaypoint.Category.STONK);
                renderWorldLabel(matrices, camera, labelPos, label, stonkColor);
            }
        }

        // Render interact labels (CHEST, WITHER, ITEM, BAT)
        if (showPathNodes && waypoint.interactLabelPositions != null) {
            for (int i = 0; i < waypoint.interactLabelPositions.size(); i++) {
                Vec3 labelPos = waypoint.interactLabelPositions.get(i);
                String label = formatCategoryName(waypoint.category) + " §7#" + (i + 1);
                renderWorldLabel(matrices, camera, labelPos, label, color);
            }
        }
    }

    /**
     * Render a single world label at the specified position
     */
    private void renderWorldLabel(com.mojang.blaze3d.vertex.PoseStack matrices,
                                   net.minecraft.client.Camera camera,
                                   Vec3 worldPos,
                                   String text,
                                   float[] color) {
        Vec3 cameraPos = camera.getPosition();
        double dx = worldPos.x - cameraPos.x;
        double dy = worldPos.y - cameraPos.y;
        double dz = worldPos.z - cameraPos.z;

        matrices.pushPose();
        matrices.translate(dx, dy, dz);

        // Billboard rotation (face camera)
        matrices.mulPose(new org.joml.Quaternionf().rotationY((float) Math.toRadians(-camera.getYRot())));
        matrices.mulPose(new org.joml.Quaternionf().rotationX((float) Math.toRadians(camera.getXRot())));

        float scale = 0.025f;
        matrices.scale(-scale, -scale, scale);

        var textRenderer = MC.font;
        int textWidth = textRenderer.width(text);
        float x = -textWidth / 2f;

        // Draw text (keep geometry through walls but avoid the black background)
        var immediate = MC.renderBuffers().bufferSource();
        int textColor = 0xFFFFFFFF;
        if (color != null && color.length > 0) {
            int alpha = color.length > 3 ? channel(color[3]) : 255;
            int red = channel(color[0]);
            int green = color.length > 1 ? channel(color[1]) : 255;
            int blue = color.length > 2 ? channel(color[2]) : 255;
            textColor = (alpha << 24) | (red << 16) | (green << 8) | blue;
        }
        textRenderer.drawInBatch(text, x, 0, textColor, false, matrices.last().pose(), immediate,
                         net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
        immediate.endBatch();

        matrices.popPose();
    }

    /**
     * ESP Rendering Modes - Different visual styles for waypoint rendering
     */
    public enum ESPMode {
        OUTLINE("Outline", "Simple outline box"),
        FILLED("Filled", "Filled box with transparency"),
        FILLED_OUTLINE("Filled + Outline", "Filled box with outline (classic)"),
        GLOW("Glow", "Glowing outline effect"),
        CHAMS("Chams", "See-through solid rendering"),
        WIREFRAME("Wireframe", "Wireframe mesh style"),
        NONE("None", "No ESP rendering");

        private final String displayName;
        private final String description;

        ESPMode(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }

        public static String[] getDisplayNames() {
            ESPMode[] modes = values();
            String[] names = new String[modes.length];
            for (int i = 0; i < modes.length; i++) {
                names[i] = modes[i].displayName;
            }
            return names;
        }
    }
}
