package dev.hunchclient.module.impl.sbd.api;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.hunchclient.HunchClient;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * HTTP Client for Hypixel API requests
 * Uses SBD API and SkyCrypt API (matching original ChatTriggers module)
 */
public class HypixelApiClient {
    private static final Gson GSON = new Gson();
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(3, runnable -> {
        Thread thread = new Thread(runnable, "SBD-API-Worker");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * Fetch UUID for a player username
     */
    public static CompletableFuture<String> fetchUUID(String username) {
        return CompletableFuture.supplyAsync(() -> {
            // Try PlayerDB first
            try {
                String url = "https://playerdb.co/api/player/minecraft/" + username;
                JsonElement response = makeRequest(url);
                if (response != null && response.isJsonObject()) {
                    JsonObject obj = response.getAsJsonObject();
                    if (obj.has("data") && obj.get("data").isJsonObject()) {
                        JsonObject data = obj.getAsJsonObject("data");
                        if (data.has("player") && data.get("player").isJsonObject()) {
                            JsonObject player = data.getAsJsonObject("player");
                            if (player.has("id")) {
                                String uuid = player.get("id").getAsString().replace("-", "");
                                return uuid;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // Silent fail, try next API
            }

            // Try Mojang API
            try {
                String url = "https://api.mojang.com/users/profiles/minecraft/" + username;
                JsonElement response = makeRequest(url);
                if (response != null && response.isJsonObject()) {
                    JsonObject obj = response.getAsJsonObject();
                    if (obj.has("id")) {
                        return obj.get("id").getAsString();
                    }
                }
            } catch (Exception e) {
                // Silent fail, try next API
            }

            // Try Ashcon API
            try {
                String url = "https://api.ashcon.app/mojang/v2/user/" + username;
                JsonElement response = makeRequest(url);
                if (response != null && response.isJsonObject()) {
                    JsonObject obj = response.getAsJsonObject();
                    if (obj.has("uuid")) {
                        return obj.get("uuid").getAsString().replace("-", "");
                    }
                }
            } catch (Exception e) {
                // Silent fail
            }

            return null;
        }, EXECUTOR);
    }

    /**
     * Fetch player stats - try SBD API first, then SkyCrypt API
     */
    public static CompletableFuture<PlayerStats> fetchStats(String uuid) {
        return CompletableFuture.supplyAsync(() -> {
            // Try SBD API first (Cloudflare Workers)
            try {
                String url = "https://sbd.evankhell.workers.dev/player/" + uuid;
                JsonElement response = makeRequest(url);
                if (response != null && response.isJsonObject()) {
                    PlayerStats stats = parseSbdApiResponse(response.getAsJsonObject());
                    if (stats != null) {
                        return stats;
                    }
                }
            } catch (Exception e) {
                // Silent fail, try next API
            }

            // Try SBD Azure fallback
            try {
                String url = "http://sbd.hs.vc/player/" + uuid;
                JsonElement response = makeRequest(url);
                if (response != null && response.isJsonObject()) {
                    PlayerStats stats = parseSbdApiResponse(response.getAsJsonObject());
                    if (stats != null) {
                        return stats;
                    }
                }
            } catch (Exception e) {
                // Silent fail, try next API
            }

            // Fallback to SkyCrypt Dungeons API
            try {
                String url = "https://sky.shiiyu.moe/api/v2/dungeons/" + uuid;
                JsonElement response = makeRequest(url);
                if (response != null && response.isJsonObject()) {
                    PlayerStats stats = parseSkyCryptDungeonsApi(response.getAsJsonObject());
                    if (stats != null) {
                        return stats;
                    }
                }
            } catch (Exception e) {
                // Silent fail
            }

            return null;
        }, EXECUTOR);
    }

    private static JsonElement makeRequest(String urlString) throws Exception {
        URL url = URI.create(urlString).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "Mozilla/5.0");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(8000);

        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) {
            connection.disconnect();
            return null;
        }

        InputStream stream = connection.getInputStream();
        String raw = readStream(stream);
        connection.disconnect();

        if (raw == null || raw.isEmpty()) {
            return null;
        }

        return GSON.fromJson(raw, JsonElement.class);
    }

    private static String readStream(InputStream stream) throws Exception {
        if (stream == null) {
            return "";
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            return builder.toString();
        }
    }

    public static void shutdownExecutor() {
        EXECUTOR.shutdown();
        try {
            if (!EXECUTOR.awaitTermination(3, TimeUnit.SECONDS)) {
                EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Parse SBD API JSON response
     */
    private static PlayerStats parseSbdApiResponse(JsonObject root) {
        try {
            if (!root.has("dungeons")) {
                return null;
            }

            JsonObject dungeons = root.getAsJsonObject("dungeons");
            PlayerStats stats = new PlayerStats();

            // Cata level - calculate from XP if not provided
            if (dungeons.has("catalevel")) {
                stats.cataLevel = dungeons.get("catalevel").getAsInt();
            } else if (dungeons.has("cataxp")) {
                double xp = dungeons.get("cataxp").getAsDouble();
                stats.cataLevel = calculateCataLevel(xp);
            }

            // Secrets
            if (dungeons.has("secrets")) {
                stats.totalSecrets = dungeons.get("secrets").getAsInt();
            }

            // Runs
            if (dungeons.has("runs")) {
                stats.totalRuns = dungeons.get("runs").getAsInt();
            }

            // PB times (stored in nested structure)
            if (dungeons.has("pb") && dungeons.get("pb").isJsonObject()) {
                JsonObject pb = dungeons.getAsJsonObject("pb");

                // F7: catacombs -> 7 -> rawS+
                stats.pbF7 = extractPbFromSbd(pb, "catacombs", "7");

                // Master modes
                stats.pbM4 = extractPbFromSbd(pb, "master_catacombs", "4");
                stats.pbM5 = extractPbFromSbd(pb, "master_catacombs", "5");
                stats.pbM6 = extractPbFromSbd(pb, "master_catacombs", "6");
                stats.pbM7 = extractPbFromSbd(pb, "master_catacombs", "7");
            }

            return stats;
        } catch (Exception e) {
            HunchClient.LOGGER.error("Error parsing SBD API response", e);
            return null;
        }
    }

    private static long extractPbFromSbd(JsonObject pb, String dungeonType, String floor) {
        try {
            if (!pb.has(dungeonType)) return -1;
            JsonObject dungeon = pb.getAsJsonObject(dungeonType);
            if (!dungeon.has(floor)) return -1;
            JsonObject floorData = dungeon.getAsJsonObject(floor);
            if (floorData.has("rawS+") && !floorData.get("rawS+").isJsonNull()) {
                return floorData.get("rawS+").getAsLong();
            }
        } catch (Exception e) {
            // Ignore
        }
        return -1;
    }

    /**
     * Calculate Catacombs level from XP
     * Uses Hypixel's dungeoneering XP formula
     */
    private static int calculateCataLevel(double xp) {
        // XP requirements for levels 1-50 (from Hypixel)
        double[] xpTable = {
            50, 125, 235, 395, 625, 955, 1425, 2095, 3045, 4385,
            6275, 8940, 12700, 17960, 25340, 35640, 50040, 70040,
            97640, 135640, 188140, 259640, 356640, 488640, 668640,
            911640, 1239640, 1684640, 2284640, 3084640, 4149640, 5559640,
            7459640, 9959640, 13259640, 17559640, 23159640, 30359640, 39559640,
            51559640, 66559640, 85559640, 109559640, 139559640, 177559640, 225559640,
            285559640, 360559640, 453559640, 569809640
        };

        if (xp < 50) return 0;

        // Find level from XP table
        for (int i = 0; i < xpTable.length; i++) {
            if (xp < xpTable[i]) {
                return i;
            }
        }

        // Level 50+: each level requires 200M XP
        double xpAfter50 = xp - xpTable[xpTable.length - 1];
        int levelsAfter50 = (int) Math.floor(xpAfter50 / 200000000.0);
        return 50 + levelsAfter50;
    }

    /**
     * Parse SkyCrypt Dungeons API response
     * URL: https://sky.shiiyu.moe/api/v2/dungeons/{uuid}
     */
    private static PlayerStats parseSkyCryptDungeonsApi(JsonObject root) {
        try {
            if (!root.has("profiles")) {
                return null;
            }

            JsonObject profiles = root.getAsJsonObject("profiles");

            // Find selected profile
            JsonObject profile = null;
            for (String key : profiles.keySet()) {
                JsonObject p = profiles.getAsJsonObject(key);
                if (p.has("selected") && p.get("selected").getAsBoolean()) {
                    profile = p;
                    break;
                }
            }

            if (profile == null && profiles.size() > 0) {
                // Use first profile
                profile = profiles.getAsJsonObject(profiles.keySet().iterator().next());
            }

            if (profile == null || !profile.has("dungeons")) {
                return null;
            }

            JsonObject dungeons = profile.getAsJsonObject("dungeons");
            PlayerStats stats = new PlayerStats();

            // Cata level
            if (dungeons.has("catacombs") && dungeons.get("catacombs").isJsonObject()) {
                JsonObject catacombs = dungeons.getAsJsonObject("catacombs");
                if (catacombs.has("level") && catacombs.get("level").isJsonObject()) {
                    JsonObject level = catacombs.getAsJsonObject("level");
                    if (level.has("uncappedLevel")) {
                        stats.cataLevel = level.get("uncappedLevel").getAsInt();
                    }
                }
            }

            // Secrets (aggregate across all profiles)
            int totalSecrets = 0;
            for (String key : profiles.keySet()) {
                JsonObject p = profiles.getAsJsonObject(key);
                if (p.has("dungeons") && p.get("dungeons").isJsonObject()) {
                    JsonObject pDungeons = p.getAsJsonObject("dungeons");
                    if (pDungeons.has("secrets_found")) {
                        totalSecrets += pDungeons.get("secrets_found").getAsInt();
                    }
                }
            }
            stats.totalSecrets = totalSecrets;

            // Runs
            if (dungeons.has("floor_completions")) {
                stats.totalRuns = dungeons.get("floor_completions").getAsInt();
            }

            // PB times
            stats.pbF7 = extractPbFromSkyCrypt(dungeons, "catacombs", "7");
            stats.pbM4 = extractPbFromSkyCrypt(dungeons, "master_catacombs", "4");
            stats.pbM5 = extractPbFromSkyCrypt(dungeons, "master_catacombs", "5");
            stats.pbM6 = extractPbFromSkyCrypt(dungeons, "master_catacombs", "6");
            stats.pbM7 = extractPbFromSkyCrypt(dungeons, "master_catacombs", "7");

            return stats;
        } catch (Exception e) {
            HunchClient.LOGGER.error("Error parsing SkyCrypt Dungeons API response", e);
            return null;
        }
    }

    private static long extractPbFromSkyCrypt(JsonObject dungeons, String dungeonType, String floor) {
        try {
            if (!dungeons.has(dungeonType)) return -1;
            JsonObject dungeon = dungeons.getAsJsonObject(dungeonType);
            if (!dungeon.has("floors")) return -1;
            JsonObject floors = dungeon.getAsJsonObject("floors");
            if (!floors.has(floor)) return -1;
            JsonObject floorData = floors.getAsJsonObject(floor);
            if (!floorData.has("stats")) return -1;
            JsonObject floorStats = floorData.getAsJsonObject("stats");
            if (floorStats.has("fastest_time_s_plus") && !floorStats.get("fastest_time_s_plus").isJsonNull()) {
                return floorStats.get("fastest_time_s_plus").getAsLong();
            }
        } catch (Exception e) {
            // Ignore
        }
        return -1;
    }

    public static class PlayerStats {
        public int cataLevel = 0;
        public int totalSecrets = 0;
        public int totalRuns = 0;
        public long pbF7 = -1;  // in milliseconds
        public long pbM4 = -1;
        public long pbM5 = -1;
        public long pbM6 = -1;
        public long pbM7 = -1;

        public double getSecretAverage() {
            if (totalRuns == 0) {
                return 0.0;
            }
            return (double) totalSecrets / totalRuns;
        }

        public String getPBString(long pbMs) {
            if (pbMs < 0) {
                return "?";
            }
            long seconds = pbMs / 1000;
            long minutes = seconds / 60;
            seconds = seconds % 60;
            return String.format("%d:%02d", minutes, seconds);
        }
    }
}
