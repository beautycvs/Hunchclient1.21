package dev.hunchclient.module.impl.dungeons;

import dev.hunchclient.module.ConfigurableModule;
import dev.hunchclient.module.Module;
import dev.hunchclient.module.SettingsProvider;
import dev.hunchclient.module.setting.*;
import dev.hunchclient.render.MobGlow;
import dev.hunchclient.render.MobGlowAdder;
import dev.hunchclient.util.DungeonUtils;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;

/**
 * StarredMobsESP - Highlights starred mobs using custom glow system
 *
 * Detects mobs with a star (✯) in their name and applies a custom glow effect.
 * Based on Skyblocker's MobGlow system for better performance and render distance.
 *
 * WATCHDOG SAFE: Client-side rendering only, no packets sent
 */
public class StarredMobsESPModule extends Module implements MobGlowAdder, ConfigurableModule, SettingsProvider {

    // Star symbol for starred mobs
    private static final String STAR = "✯";

    // Glow color for starred mobs (ARGB: Yellow/Gold with full opacity)
    private int glowColor = 0xFFFFD700;  // Alpha=FF (opaque), R=FF, G=D7, B=00

    // Fel (sleeping Enderman) detection
    private static final int FEL_COLOR = 0xFFcc00fa; // Purple color for Fel heads (Alpha + RGB)
    private static final String FEL_HEAD_TEXTURE = "ewogICJ0aW1lc3RhbXAiIDogMTcyMDAyNTQ4Njg2MywKICAicHJvZmlsZUlkIiA6ICIzZDIxZTYyMTk2NzQ0Y2QwYjM3NjNkNTU3MWNlNGJlZSIsCiAgInByb2ZpbGVOYW1lIiA6ICJTcl83MUJsYWNrYmlyZCIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9jMjg2ZGFjYjBmMjE0NGQ3YTQxODdiZTM2YmJhYmU4YTk4ODI4ZjdjNzlkZmY1Y2UwMTM2OGI2MzAwMTU1NjYzIiwKICAgICAgIm1ldGFkYXRhIiA6IHsKICAgICAgICAibW9kZWwiIDogInNsaW0iCiAgICAgIH0KICAgIH0KICB9Cn0=";
    private boolean detectFels = true; // Enable/disable Fel detection

    // ESP rendering options
    private float fillOpacity = 0.3f;     // Fill opacity (0.0 = transparent, 1.0 = opaque)
    private float outlineOpacity = 1.0f;  // Outline opacity (0.0 = transparent, 1.0 = opaque)
    private float lineWidth = 2.0f;       // Line width for outline
    private boolean showFill = true;      // Show filled box
    private boolean showOutline = true;   // Show outline

    // Maximum render distance for starred mobs (configurable)
    private double maxDistance = 50.0;
    private int glowRefreshDelayMs = 0;
    private int cacheRefreshDelayMs = 2000;

    // Debug mode
    private boolean debugMode = false;

    // Hide non-starred entities (render optimization)
    private boolean hideNonStarred = false;

    // PERFORMANCE: Cache for starred status to avoid repeated entity searches
    private final ObjectSet<Entity> starredCache = new ObjectOpenHashSet<>();
    private final Object cacheLock = new Object();
    private long lastCacheClear = 0;

    // Singleton reference for mixin access
    private static StarredMobsESPModule instance;

    public StarredMobsESPModule() {
        super("StarredMobsESP", "Highlights starred dungeon mobs with custom glow", Category.DUNGEONS, true);
        instance = this;
    }

    public static StarredMobsESPModule getInstance() {
        return instance;
    }

    /**
     * Checks if an entity should be hidden (render optimization)
     * Called from EntityRendererMixin
     */
    public boolean shouldHideEntity(Entity entity) {
        if (!hideNonStarred || !isEnabled()) {
            return false;
        }

        // Only hide entities in dungeons
        if (!DungeonUtils.isInDungeon()) {
            return false;
        }

        // Never hide real players (but hide fake players/NPCs)
        if (entity instanceof net.minecraft.world.entity.player.Player) {
            net.minecraft.world.entity.player.Player player = (net.minecraft.world.entity.player.Player) entity;
            // Real players are in the player list, fake players are not
            if (net.minecraft.client.Minecraft.getInstance().getConnection() != null &&
                net.minecraft.client.Minecraft.getInstance().getConnection().getOnlinePlayers().stream()
                    .anyMatch(entry -> entry.getProfile().id().equals(player.getUUID()))) {
                return false; // Real player - don't hide
            }
            // Fake player/NPC - continue with normal hiding logic
        }

        // Only hide LivingEntities and ArmorStands (not arrows, items, etc.)
        if (!(entity instanceof net.minecraft.world.entity.LivingEntity) &&
            !(entity instanceof net.minecraft.world.entity.decoration.ArmorStand)) {
            return false; // Don't hide projectiles, items, etc.
        }

        // Never hide bats (important for dungeon puzzles)
        if (entity instanceof net.minecraft.world.entity.ambient.Bat) {
            return false;
        }

        // Don't hide starred mobs
        synchronized (cacheLock) {
            if (starredCache.contains(entity)) {
                return false;
            }
        }

        // Check if entity is starred (and cache result)
        if (isStarredEntity(entity)) {
            synchronized (cacheLock) {
                starredCache.add(entity);
            }
            return false;
        }

        // Hide everything else (only LivingEntities/ArmorStands at this point)
        return true;
    }

    @Override
    protected void onEnable() {
        // Register this module as a MobGlowAdder
        MobGlow.registerGlowAdder(this);
        MobGlow.setRefreshDelayMs(glowRefreshDelayMs);
        lastCacheClear = 0;
        debugLog("§a[StarredMobsESP] Module enabled and registered with MobGlow system!");
    }

    @Override
    protected void onDisable() {
        // The MobGlow system will automatically stop using this adder when isEnabled() returns false
        MobGlow.clearCache();
        MobGlow.setRefreshDelayMs(0);
        synchronized (cacheLock) {
            starredCache.clear();
        }
        debugLog("§c[StarredMobsESP] Module disabled");
    }

    // ==================== MobGlowAdder Implementation ====================

    @Override
    public boolean isEnabled() {
        Minecraft mc = Minecraft.getInstance();

        // Module can be toggled anytime, but will only scan in dungeons
        // (Dungeon check moved to computeColour() for better UX)
        return super.isEnabled() && mc.level != null && mc.player != null;
    }

    @Override
    public int computeColour(Entity entity) {
        if (entity == null) {
            return MobGlow.NO_GLOW;
        }

        // Only scan in dungeons (but module can be toggled anywhere)
        if (!DungeonUtils.isInDungeon()) {
            return MobGlow.NO_GLOW;
        }

        // PERFORMANCE: Periodically clear cache to avoid memory leaks
        long currentTime = System.currentTimeMillis();
        if (cacheRefreshDelayMs <= 0 || currentTime - lastCacheClear > cacheRefreshDelayMs) {
            cleanupCache();
            lastCacheClear = currentTime;
        }

        // Check for Fel heads (sleeping Endermen) BEFORE ignoring all armor stands
        if (entity instanceof ArmorStand) {
            ArmorStand as = (ArmorStand) entity;

            if (!detectFels) {
                return MobGlow.NO_GLOW; // Fel detection disabled
            }

            debugLog("§7[Fel Check] ArmorStand found - isMarker=" + as.isMarker() + ", hasHead=" + as.hasItemInSlot(net.minecraft.world.entity.EquipmentSlot.HEAD));

            if (as.isMarker() && as.hasItemInSlot(net.minecraft.world.entity.EquipmentSlot.HEAD)) {
                String headTexture = getHeadTexture(as.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD));
                if (headTexture != null) {
                    debugLog("§7[Fel Check] Head texture found: " + headTexture.substring(0, Math.min(50, headTexture.length())) + "...");
                    debugLog("§7[Fel Check] Match=" + FEL_HEAD_TEXTURE.equals(headTexture));
                } else {
                    debugLog("§7[Fel Check] Head texture is NULL");
                }

                if (FEL_HEAD_TEXTURE.equals(headTexture)) {
                    debugLog("§d§l[StarredMobsESP] ★★★ FEL DETECTED ★★★");
                    synchronized (cacheLock) {
                        starredCache.add(entity); // Cache it so it doesn't get culled
                    }
                    return FEL_COLOR; // Purple glow
                }
            }
            // Not a Fel, ignore other armor stands
            return MobGlow.NO_GLOW;
        }

        // Check distance to player (configurable range)
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            double distance = entity.distanceTo(mc.player);
            if (distance > maxDistance) {
                return MobGlow.NO_GLOW;
            }
        }

        // Check cache first (starred status never changes)
        synchronized (cacheLock) {
            if (starredCache.contains(entity)) {
                return glowColor;
            }
        }

        // Check if this entity has a star
        boolean isStarred = isStarredEntity(entity);

        // If starred, add to cache and highlight
        if (isStarred) {
            debugLog("§a[StarredMobsESP] ★ Starred mob detected: " + entity.getType());
            synchronized (cacheLock) {
                starredCache.add(entity);
            }
            return glowColor;
        }

        return MobGlow.NO_GLOW;
    }

    // ==================== Detection Logic ====================

    /**
     * Checks if the entity has a starred armor stand above it OR is a Fel head
     */
    private boolean isStarredEntity(Entity entity) {
        // Check for Fel heads (sleeping Endermen as player heads)
        if (detectFels && entity instanceof ArmorStand) {
            ArmorStand as = (ArmorStand) entity;
            if (as.isMarker() && as.hasItemInSlot(net.minecraft.world.entity.EquipmentSlot.HEAD)) {
                String headTexture = getHeadTexture(as.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD));
                if (FEL_HEAD_TEXTURE.equals(headTexture)) {
                    debugLog("§d[StarredMobsESP] ★ Fel (sleeping Enderman) detected!");
                    return true;
                }
            }
        }

        // Check for starred mobs (with star in name)
        List<ArmorStand> armorStands = MobGlow.getArmorStands(entity);
        return !armorStands.isEmpty() && isStarredName(armorStands.get(0).getName().getString());
    }


    /**
     * Checks if a name contains the star symbol
     */
    private boolean isStarredName(String name) {
        return name != null && name.contains(STAR);
    }

    /**
     * Extracts the Base64-encoded texture from a player head ItemStack
     * Returns null if not a skull or no texture found
     * Based on Skyblocker's ItemUtils.getHeadTexture()
     */
    private String getHeadTexture(net.minecraft.world.item.ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }

        // Check if it's a player head
        if (!stack.is(net.minecraft.world.item.Items.PLAYER_HEAD)) {
            return null;
        }

        // Get the profile component
        if (!stack.has(net.minecraft.core.component.DataComponents.PROFILE)) {
            return null;
        }

        net.minecraft.world.item.component.ResolvableProfile profile = stack.get(net.minecraft.core.component.DataComponents.PROFILE);
        if (profile == null) {
            return null;
        }

        // Extract texture from game profile properties
        return profile.partialProfile().properties().get("textures").stream()
            .map(com.mojang.authlib.properties.Property::value)
            .findFirst()
            .orElse(null);
    }

    // ==================== Utility Methods ====================

    /**
     * PERFORMANCE: Clean up cache entries for dead/removed entities
     */
    private void cleanupCache() {
        synchronized (cacheLock) {
            Iterator<Entity> iterator = starredCache.iterator();
            while (iterator.hasNext()) {
                Entity cached = iterator.next();
                if (cached == null || cached.isRemoved() || !cached.isAlive()) {
                    iterator.remove();
                }
            }
        }
    }

    /**
     * Called when world is unloaded
     */
    public void onWorldUnload() {
        MobGlow.clearCache();
        synchronized (cacheLock) {
            starredCache.clear();
        }
    }

    /**
     * Sets the debug mode
     */
    public void setDebugMode(boolean enabled) {
        this.debugMode = enabled;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                enabled ? "§a[StarredMobsESP] Debug mode enabled" : "§c[StarredMobsESP] Debug mode disabled"
            ), false);
        }
    }

    /**
     * Toggles debug mode
     */
    public void toggleDebug() {
        setDebugMode(!debugMode);
    }

    /**
     * Debug logging
     */
    private void debugLog(String message) {
        if (!debugMode) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(message), false);
        }
    }

    // ConfigurableModule implementation
    @Override
    public JsonObject saveConfig() {
        JsonObject config = new JsonObject();
        config.addProperty("glowColor", glowColor);
        config.addProperty("maxDistance", maxDistance);
        config.addProperty("debugMode", debugMode);
        config.addProperty("hideNonStarred", hideNonStarred);
        config.addProperty("detectFels", detectFels);
        config.addProperty("glowRefreshDelayMs", glowRefreshDelayMs);
        config.addProperty("cacheRefreshDelayMs", cacheRefreshDelayMs);
        return config;
    }

    @Override
    public void loadConfig(JsonObject data) {
        if (data == null) return;
        if (data.has("glowColor")) glowColor = data.get("glowColor").getAsInt();
        if (data.has("maxDistance")) maxDistance = data.get("maxDistance").getAsDouble();
        if (data.has("debugMode")) debugMode = data.get("debugMode").getAsBoolean();
        if (data.has("hideNonStarred")) hideNonStarred = data.get("hideNonStarred").getAsBoolean();
        if (data.has("detectFels")) detectFels = data.get("detectFels").getAsBoolean();
        if (data.has("glowRefreshDelayMs")) {
            glowRefreshDelayMs = data.get("glowRefreshDelayMs").getAsInt();
            MobGlow.setRefreshDelayMs(glowRefreshDelayMs);
        }
        if (data.has("cacheRefreshDelayMs")) {
            cacheRefreshDelayMs = data.get("cacheRefreshDelayMs").getAsInt();
            lastCacheClear = 0;
        }
    }

    // SettingsProvider implementation
    @Override
    public java.util.List<ModuleSetting> getSettings() {
        java.util.List<ModuleSetting> settings = new java.util.ArrayList<>();

        settings.add(new ColorPickerSetting(
            "Glow Color",
            "Color of starred mob glow",
            "starredmobs_glow_color",
            () -> glowColor,
            color -> glowColor = color
        ));

        settings.add(new SliderSetting(
            "Max Distance",
            "Maximum distance to highlight starred mobs (in blocks)",
            "starredmobs_max_distance",
            10f, 100f,
            () -> (float) maxDistance,
            val -> maxDistance = val.doubleValue()
        ).withDecimals(0).withSuffix(" blocks"));

        settings.add(new SliderSetting(
            "Glow Refresh Delay",
            "Delay before glow cache refreshes (0 = every tick)",
            "starredmobs_glow_refresh_delay",
            0f, 1000f,
            () -> (float) glowRefreshDelayMs,
            val -> {
                glowRefreshDelayMs = val.intValue();
                MobGlow.setRefreshDelayMs(glowRefreshDelayMs);
            }
        ).withDecimals(0).withSuffix(" ms"));

        settings.add(new SliderSetting(
            "Scan Refresh Delay",
            "Interval between armor stand scans (0 = every tick)",
            "starredmobs_scan_refresh_delay",
            0f, 5000f,
            () -> (float) cacheRefreshDelayMs,
            val -> {
                cacheRefreshDelayMs = Math.max(0, val.intValue());
                lastCacheClear = 0; // force immediate rescan with new interval
            }
        ).withDecimals(0).withSuffix(" ms"));

        settings.add(new CheckboxSetting(
            "Detect Fels",
            "Detect sleeping Fels (Endermen as player heads on ground) with purple glow",
            "starredmobs_detect_fels",
            () -> detectFels,
            val -> {
                detectFels = val;
                MobGlow.clearCache(); // Clear cache to re-scan entities
                synchronized (cacheLock) {
                    starredCache.clear();
                }
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        val ? "§d[StarredMobsESP] Fel detection enabled!"
                            : "§c[StarredMobsESP] Fel detection disabled"
                    ), false);
                }
            }
        ));

        settings.add(new CheckboxSetting(
            "Hide Non-Starred",
            "Hide all entities except players and starred mobs (render optimization)",
            "starredmobs_hide_nonstarred",
            () -> hideNonStarred,
            val -> {
                hideNonStarred = val;
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        val ? "§a[StarredMobsESP] Hide Non-Starred enabled - only players and starred mobs visible!"
                            : "§c[StarredMobsESP] Hide Non-Starred disabled"
                    ), false);
                }
            }
        ));

        settings.add(new CheckboxSetting(
            "Debug Mode",
            "Show debug messages in chat",
            "starredmobs_debug",
            () -> debugMode,
            val -> debugMode = val
        ));

        return settings;
    }
}
