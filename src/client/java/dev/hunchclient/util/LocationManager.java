package dev.hunchclient.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;

/**
 * Manages location detection via Hypixel's /locraw command.
 * Sends /locraw on world join and parses the JSON response.
 *
 * Example response: {"server":"mini147A","gametype":"SKYBLOCK","mode":"dynamic","map":"Private Island"}
 */
public class LocationManager {

    private static final LocationManager INSTANCE = new LocationManager();
    private static final Minecraft mc = Minecraft.getInstance();

    // Cached location data from /locraw
    private String server = "";
    private String gameType = "";
    private String mode = "";
    private String map = "";
    private long lastLocrawTime = 0;
    private long lastLocrawSentTime = 0;
    private boolean awaitingLocraw = false;

    // Rate limiting - only send /locraw once per 30 seconds
    private static final long LOCRAW_COOLDOWN_MS = 30000;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "LocationManager");
        t.setDaemon(true);
        return t;
    });

    private LocationManager() {}

    public static LocationManager getInstance() {
        return INSTANCE;
    }

    /**
     * Called on world join/respawn to request location.
     * Sends /locraw with a small delay to ensure connection is ready.
     */
    public void onWorldJoin() {
        // Schedule /locraw with delay to ensure we're fully connected
        scheduler.schedule(this::sendLocraw, 500, TimeUnit.MILLISECONDS);
    }

    private void sendLocraw() {
        if (mc.player != null && mc.getConnection() != null) {
            // Rate limiting - don't spam /locraw
            long now = System.currentTimeMillis();
            if (now - lastLocrawSentTime < LOCRAW_COOLDOWN_MS) {
                return; // Still on cooldown
            }

            awaitingLocraw = true;
            lastLocrawSentTime = now;
            lastLocrawTime = now;

            // Send /locraw command
            mc.getConnection().sendCommand("locraw");
        }
    }

    /**
     * Called when a chat message is received.
     * Checks if it's a /locraw JSON response and parses it.
     *
     * @param message The chat message
     * @return true if message was consumed (was locraw response)
     */
    public boolean onChatMessage(String message) {
        // Only process if we're expecting a locraw response
        if (!awaitingLocraw) {
            // Also check for locraw responses even if not awaiting (manual /locraw)
            if (!message.startsWith("{") || !message.contains("server")) {
                return false;
            }
        }

        // Check if this looks like a locraw JSON response
        if (message.startsWith("{") && message.contains("server")) {
            try {
                JsonObject json = JsonParser.parseString(message).getAsJsonObject();

                // Parse location data
                server = json.has("server") ? json.get("server").getAsString() : "";
                gameType = json.has("gametype") ? json.get("gametype").getAsString() : "";
                mode = json.has("mode") ? json.get("mode").getAsString() : "";
                map = json.has("map") ? json.get("map").getAsString() : "";

                awaitingLocraw = false;
                return true; // Message consumed - will be suppressed in chat
            } catch (Exception ignored) {
                // Failed to parse - not a locraw response
            }
        }

        return false;
    }

    /**
     * Check if player is on Skyblock
     */
    public boolean isOnSkyblock() {
        return "SKYBLOCK".equalsIgnoreCase(gameType);
    }

    /**
     * Check if player is on their Private Island
     */
    public boolean isOnPrivateIsland() {
        return isOnSkyblock() && "Private Island".equalsIgnoreCase(map);
    }

    /**
     * Check if player is in a dungeon
     */
    public boolean isInDungeon() {
        return isOnSkyblock() && "dungeon".equalsIgnoreCase(mode);
    }

    /**
     * Check if player is in the Dungeon Hub
     */
    public boolean isInDungeonHub() {
        return isOnSkyblock() && "Dungeon Hub".equalsIgnoreCase(map);
    }

    /**
     * Check if player is in the Garden
     */
    public boolean isInGarden() {
        return isOnSkyblock() && "Garden".equalsIgnoreCase(map);
    }

    /**
     * Get the current map name
     */
    public String getMap() {
        return map;
    }

    /**
     * Get the current game type
     */
    public String getGameType() {
        return gameType;
    }

    /**
     * Get the current mode
     */
    public String getMode() {
        return mode;
    }

    /**
     * Get the current server
     */
    public String getServer() {
        return server;
    }

    /**
     * Check if location data is available (has been updated recently)
     */
    public boolean hasLocationData() {
        return !server.isEmpty() && System.currentTimeMillis() - lastLocrawTime < 300000; // 5 min cache
    }

    /**
     * Force refresh location data
     */
    public void refresh() {
        sendLocraw();
    }

    /**
     * Reset location data (on disconnect)
     */
    public void reset() {
        server = "";
        gameType = "";
        mode = "";
        map = "";
        awaitingLocraw = false;
    }

    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
