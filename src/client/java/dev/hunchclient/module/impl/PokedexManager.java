package dev.hunchclient.module.impl;

import com.google.gson.*;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import dev.hunchclient.util.LocationManager;
import java.time.LocalTime;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/**
 * Manages the Hunchclient Pokedex - a collection of discovered OG users.
 * Gen 4 Pokemon style: shows all UIDs, undiscovered as "???", captured with names.
 */
public class PokedexManager {

    private static final String UID_MAPPINGS_URL = "https://34.7.234.242/helper/uid_mappings.json";
    private static PokedexManager instance;

    private final Minecraft mc = Minecraft.getInstance();

    // Legacy OG user list: username -> UID
    private final Map<String, Integer> allUsers = new ConcurrentHashMap<>();
    // Reverse map: UID -> username (for display)
    private final Map<Integer, String> uidToUsername = new ConcurrentHashMap<>();

    // Captured users: UID -> CapturedEntry
    private final Map<Integer, CapturedEntry> captured = new ConcurrentHashMap<>();

    private volatile boolean loaded = false;

    /**
     * Entry for a captured user
     */
    public static class CapturedEntry {
        public final String name;
        public final String date;
        public final String time;             // Time of capture (HH:mm:ss)
        public final String screenshotPath;   // Path to capture screenshot
        public final String skinPath;         // Path to cached skin texture
        public final String skinUrl;          // Original skin URL
        public final String location;         // Where they were captured (server/world)
        public final String skyblockIsland;   // Skyblock island name if on Skyblock
        public final String gameMode;         // Game mode (e.g., dungeon, dynamic)

        // Profile Viewer data (from Hypixel API)
        public final Double sbLevel;          // Skyblock level
        public final Double skillAverage;     // Average skill level
        public final Double cataLevel;        // Catacombs level
        public final Double networth;         // Networth (purse + bank + items estimate)
        public final String dungeonPbs;       // JSON: floor -> {s: time, splus: time}
        public final String armorData;        // JSON: armor slots with NBT data
        public final String weekday;          // Day of week when captured
        public final String skillsData;       // JSON: individual skill levels
        public final Long lastLogin;          // Last login timestamp (ms)
        public final Long lastLogout;         // Last logout timestamp (ms)

        public CapturedEntry(String name, String date) {
            this(name, date, null, null, null, null, null, null, null,
                 null, null, null, null, null, null, null, null, null, null);
        }

        public CapturedEntry(String name, String date, String screenshotPath, String skinPath, String skinUrl, String location) {
            this(name, date, null, screenshotPath, skinPath, skinUrl, location, null, null,
                 null, null, null, null, null, null, null, null, null, null);
        }

        public CapturedEntry(String name, String date, String time, String screenshotPath, String skinPath,
                            String skinUrl, String location, String skyblockIsland, String gameMode) {
            this(name, date, time, screenshotPath, skinPath, skinUrl, location, skyblockIsland, gameMode,
                 null, null, null, null, null, null, null, null, null, null);
        }

        public CapturedEntry(String name, String date, String time, String screenshotPath, String skinPath,
                            String skinUrl, String location, String skyblockIsland, String gameMode,
                            Double sbLevel, Double skillAverage, Double cataLevel, Double networth,
                            String dungeonPbs, String armorData, String weekday, String skillsData,
                            Long lastLogin, Long lastLogout) {
            this.name = name;
            this.date = date;
            this.time = time;
            this.screenshotPath = screenshotPath;
            this.skinPath = skinPath;
            this.skinUrl = skinUrl;
            this.location = location;
            this.skyblockIsland = skyblockIsland;
            this.gameMode = gameMode;
            this.sbLevel = sbLevel;
            this.skillAverage = skillAverage;
            this.cataLevel = cataLevel;
            this.networth = networth;
            this.dungeonPbs = dungeonPbs;
            this.armorData = armorData;
            this.weekday = weekday;
            this.skillsData = skillsData;
            this.lastLogin = lastLogin;
            this.lastLogout = lastLogout;
        }
    }

    private PokedexManager() {
        loadCaptured();
        fetchAllUsers();
    }

    public static PokedexManager getInstance() {
        if (instance == null) {
            instance = new PokedexManager();
        }
        return instance;
    }

    private JsonObject fetchJsonFromUrl(String urlString) throws IOException {
        HttpURLConnection connection = null;
        try {
            URL url = URI.create(urlString).toURL();
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(7000);
            connection.setReadTimeout(7000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "HunchClient/1.0");

            if (connection.getResponseCode() != 200) {
                return null;
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Fetch legacy OG users from public server JSON
     */
    public void fetchAllUsers() {
        CompletableFuture.runAsync(() -> {
            try {
                JsonObject json = fetchJsonFromUrl(UID_MAPPINGS_URL);
                if (json != null) {
                    allUsers.clear();
                    uidToUsername.clear();

                    for (String username : json.keySet()) {
                        int uid = json.get(username).getAsInt();
                        allUsers.put(username.toLowerCase(Locale.ROOT), uid);
                        uidToUsername.put(uid, username);
                    }

                    loaded = true;
                    System.out.println("[Pokedex] Loaded " + allUsers.size() + " OG users");
                }
            } catch (Exception e) {
                System.err.println("[Pokedex] Failed to fetch user list: " + e.getMessage());
            }
        });
    }

    // Hypixel API proxy (no API key needed)
    private static final String HYPIXEL_PROXY = "https://hysky.de/api/hypixel/v2/";
    private static final String MOJANG_API = "https://api.minecraftservices.com/minecraft/profile/lookup/name/";

    /**
     * Fetch Skyblock profile data from Hypixel API
     * @param username The player's username
     * @return JsonObject with profile data, or null if failed
     */
    private JsonObject fetchHypixelProfile(String username) {
        try {
            // First get UUID from Mojang
            String uuid = getUuidFromMojang(username);
            if (uuid == null) {
                System.err.println("[Pokedex] Could not get UUID for " + username);
                return null;
            }
            System.out.println("[Pokedex] Got UUID: " + uuid + " for " + username);

            // Fetch profile from Hypixel via proxy (with redirect handling)
            String profileUrl = HYPIXEL_PROXY + "skyblock/profiles?uuid=" + uuid;
            String responseBody = fetchWithRedirects(profileUrl);

            if (responseBody == null) {
                System.err.println("[Pokedex] No response from Hypixel API for " + username);
                return null;
            }

            JsonObject response = JsonParser.parseString(responseBody).getAsJsonObject();
            if (response.has("profiles") && !response.get("profiles").isJsonNull()) {
                // Find selected profile
                for (var profile : response.getAsJsonArray("profiles")) {
                    JsonObject p = profile.getAsJsonObject();
                    if (p.has("selected") && p.get("selected").getAsBoolean()) {
                        // Get member data
                        if (p.has("members") && p.getAsJsonObject("members").has(uuid)) {
                            JsonObject result = new JsonObject();
                            result.add("profile", p);
                            result.add("member", p.getAsJsonObject("members").get(uuid).getAsJsonObject());
                            result.addProperty("uuid", uuid);
                            System.out.println("[Pokedex] Successfully fetched profile for " + username);
                            return result;
                        }
                    }
                }
            }
            System.err.println("[Pokedex] No selected profile found for " + username);
        } catch (Exception e) {
            System.err.println("[Pokedex] Failed to fetch Hypixel profile: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Fetch URL with automatic redirect handling (supports HTTPS→HTTPS redirects)
     * @param urlString The URL to fetch
     * @return Response body as string, or null if failed
     */
    private String fetchWithRedirects(String urlString) {
        int maxRedirects = 5;
        String currentUrl = urlString;

        for (int i = 0; i < maxRedirects; i++) {
            try {
                URL url = URI.create(currentUrl).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setInstanceFollowRedirects(false); // Handle redirects manually
                conn.setRequestProperty("User-Agent", "HunchClient/1.0");
                conn.setRequestProperty("Accept", "application/json");

                int responseCode = conn.getResponseCode();
                System.out.println("[Pokedex] HTTP " + responseCode + " from " + currentUrl);

                // Handle redirects (301, 302, 303, 307, 308)
                if (responseCode >= 300 && responseCode < 400) {
                    String location = conn.getHeaderField("Location");
                    if (location == null || location.isEmpty()) {
                        System.err.println("[Pokedex] Redirect without Location header");
                        return null;
                    }
                    // Handle relative redirects
                    if (location.startsWith("/")) {
                        URL oldUrl = URI.create(currentUrl).toURL();
                        location = oldUrl.getProtocol() + "://" + oldUrl.getHost() + location;
                    }
                    System.out.println("[Pokedex] Following redirect to: " + location);
                    currentUrl = location;
                    conn.disconnect();
                    continue;
                }

                if (responseCode == 200) {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line);
                        }
                        conn.disconnect();
                        return sb.toString();
                    }
                } else {
                    System.err.println("[Pokedex] HTTP error: " + responseCode);
                    conn.disconnect();
                    return null;
                }
            } catch (Exception e) {
                System.err.println("[Pokedex] Fetch error: " + e.getMessage());
                return null;
            }
        }
        System.err.println("[Pokedex] Too many redirects");
        return null;
    }

    /**
     * Get UUID from Mojang API
     */
    private String getUuidFromMojang(String username) {
        try {
            URL url = URI.create(MOJANG_API + username).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "HunchClient/1.0");

            if (conn.getResponseCode() == 200) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    JsonObject json = JsonParser.parseString(sb.toString()).getAsJsonObject();
                    if (json.has("id")) {
                        return json.get("id").getAsString();
                    }
                }
            }
            conn.disconnect();
        } catch (Exception e) {
            System.err.println("[Pokedex] Failed to get UUID: " + e.getMessage());
        }
        return null;
    }

    /**
     * Parse profile data and extract relevant stats
     */
    private ProfileData parseProfileData(JsonObject data, Long lastLogin, Long lastLogout) {
        if (data == null) return null;

        try {
            JsonObject member = data.getAsJsonObject("member");
            JsonObject profile = data.getAsJsonObject("profile");

            // SB Level
            double sbLevel = 0;
            if (member.has("leveling") && member.getAsJsonObject("leveling").has("experience")) {
                sbLevel = member.getAsJsonObject("leveling").get("experience").getAsDouble() / 100.0;
            }

            // Skills
            Map<String, Double> skills = new HashMap<>();
            double skillTotal = 0;
            int skillCount = 0;
            if (member.has("player_data") && member.getAsJsonObject("player_data").has("experience")) {
                JsonObject exp = member.getAsJsonObject("player_data").getAsJsonObject("experience");
                String[] skillNames = {"SKILL_COMBAT", "SKILL_MINING", "SKILL_FARMING", "SKILL_FORAGING",
                        "SKILL_FISHING", "SKILL_ENCHANTING", "SKILL_ALCHEMY", "SKILL_TAMING", "SKILL_CARPENTRY"};
                for (String skill : skillNames) {
                    if (exp.has(skill)) {
                        double xp = exp.get(skill).getAsDouble();
                        double level = xpToLevel(xp);
                        skills.put(skill.replace("SKILL_", ""), level);
                        skillTotal += level;
                        skillCount++;
                    }
                }
            }
            double skillAvg = skillCount > 0 ? skillTotal / skillCount : 0;

            // Catacombs
            double cataLevel = 0;
            JsonObject dungeonPbs = new JsonObject();
            if (member.has("dungeons")) {
                JsonObject dungeons = member.getAsJsonObject("dungeons");
                if (dungeons.has("dungeon_types") && dungeons.getAsJsonObject("dungeon_types").has("catacombs")) {
                    JsonObject cata = dungeons.getAsJsonObject("dungeon_types").getAsJsonObject("catacombs");
                    if (cata.has("experience")) {
                        cataLevel = xpToLevel(cata.get("experience").getAsDouble());
                    }
                    // PBs
                    if (cata.has("fastest_time_s_plus")) {
                        dungeonPbs.add("catacombs_splus", cata.getAsJsonObject("fastest_time_s_plus"));
                    }
                    if (cata.has("fastest_time_s")) {
                        dungeonPbs.add("catacombs_s", cata.getAsJsonObject("fastest_time_s"));
                    }
                }
                if (dungeons.has("dungeon_types") && dungeons.getAsJsonObject("dungeon_types").has("master_catacombs")) {
                    JsonObject master = dungeons.getAsJsonObject("dungeon_types").getAsJsonObject("master_catacombs");
                    if (master.has("fastest_time_s_plus")) {
                        dungeonPbs.add("master_splus", master.getAsJsonObject("fastest_time_s_plus"));
                    }
                    if (master.has("fastest_time_s")) {
                        dungeonPbs.add("master_s", master.getAsJsonObject("fastest_time_s"));
                    }
                }
            }

            // Networth (purse + bank)
            double networth = 0;
            if (member.has("currencies") && member.getAsJsonObject("currencies").has("coin_purse")) {
                networth += member.getAsJsonObject("currencies").get("coin_purse").getAsDouble();
            }
            if (profile.has("banking") && profile.getAsJsonObject("banking").has("balance")) {
                networth += profile.getAsJsonObject("banking").get("balance").getAsDouble();
            }

            return new ProfileData(sbLevel, skillAvg, cataLevel, networth,
                    new Gson().toJson(dungeonPbs), new Gson().toJson(skills), lastLogin, lastLogout);
        } catch (Exception e) {
            System.err.println("[Pokedex] Failed to parse profile: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Convert XP to level (simplified Skyblock formula)
     */
    private double xpToLevel(double xp) {
        // Simplified - real formula is more complex with varying XP per level
        double[] levelXp = {0, 50, 175, 375, 675, 1175, 1925, 2925, 4425, 6425, 9925, 14925, 22425, 32425, 47425,
                67425, 97425, 147425, 222425, 322425, 522425, 822425, 1222425, 1722425, 2322425, 3022425, 3822425,
                4722425, 5722425, 6822425, 8022425, 9322425, 10722425, 12222425, 13822425, 15522425, 17322425,
                19222425, 21222425, 23322425, 25522425, 27822425, 30222425, 32722425, 35322425, 38072425, 40972425,
                44072425, 47472425, 51172425, 55172425, 59472425, 64072425, 68972425, 74172425, 79672425, 85472425,
                91572425, 97972425, 104672425, 111672425};

        for (int i = levelXp.length - 1; i >= 0; i--) {
            if (xp >= levelXp[i]) {
                double excess = xp - levelXp[i];
                double nextRequired = (i + 1 < levelXp.length) ? levelXp[i + 1] - levelXp[i] : levelXp[i] - levelXp[i - 1];
                return i + (excess / nextRequired);
            }
        }
        return 0;
    }

    /**
     * Helper class for profile data
     */
    private static class ProfileData {
        final double sbLevel, skillAvg, cataLevel, networth;
        final String dungeonPbs, skillsData;
        final Long lastLogin, lastLogout;

        ProfileData(double sbLevel, double skillAvg, double cataLevel, double networth,
                   String dungeonPbs, String skillsData, Long lastLogin, Long lastLogout) {
            this.sbLevel = sbLevel;
            this.skillAvg = skillAvg;
            this.cataLevel = cataLevel;
            this.networth = networth;
            this.dungeonPbs = dungeonPbs;
            this.skillsData = skillsData;
            this.lastLogin = lastLogin;
            this.lastLogout = lastLogout;
        }
    }

    /**
     * Capture a user by UID (simple version)
     * @param uid The user's UID
     * @param name The user's display name
     * @return true if newly captured, false if already captured
     */
    public boolean capture(int uid, String name) {
        return capture(uid, name, null, false);
    }

    /**
     * Capture a user by UID with full data
     * @param uid The user's UID
     * @param name The user's display name
     * @param targetPlayer The player entity (for skin extraction) - use Object for obfuscation safety
     * @param takeScreenshot Whether to take a screenshot
     * @return true if newly captured, false if already captured
     */
    public boolean capture(int uid, String name, Object targetPlayer, boolean takeScreenshot) {
        if (captured.containsKey(uid)) {
            return false; // Already captured
        }

        String date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String screenshotPath = null;
        String skinPath = null;
        String skinUrl = null;
        String location = getCurrentLocation();
        String skyblockIsland = getSkyblockIsland();
        String gameMode = getGameMode();

        // Take screenshot if requested
        if (takeScreenshot) {
            screenshotPath = captureScreenshot(uid);
        }

        // Download and cache skin if player available
        // Cast from Object to Player for obfuscation safety
        if (targetPlayer instanceof Player) {
            Player player = (Player) targetPlayer;
            skinUrl = extractSkinUrl(player);
            if (skinUrl != null) {
                skinPath = downloadAndCacheSkin(uid, skinUrl);
            }
        }

        captured.put(uid, new CapturedEntry(name, date, time, screenshotPath, skinPath, skinUrl, location, skyblockIsland, gameMode));
        saveCaptured();

        System.out.println("[Pokedex] Captured #" + uid + " " + name + " at " + time + "!");

        // Fetch profile data async (Hypixel API via hysky.de proxy)
        fetchProfileDataAsync(uid, name);

        return true;
    }

    /**
     * Get current location string (server or world name)
     */
    private String getCurrentLocation() {
        if (mc.getCurrentServer() != null) {
            return mc.getCurrentServer().ip;
        } else if (mc.level != null) {
            return "Singleplayer";
        }
        return "Unknown";
    }

    /**
     * Get current Skyblock island name if on Hypixel Skyblock
     */
    private String getSkyblockIsland() {
        LocationManager loc = LocationManager.getInstance();
        if (loc.isOnSkyblock()) {
            String map = loc.getMap();
            return (map != null && !map.isEmpty()) ? map : null;
        }
        return null;
    }

    /**
     * Get current game mode (e.g., dungeon, dynamic, etc.)
     */
    private String getGameMode() {
        LocationManager loc = LocationManager.getInstance();
        if (loc.hasLocationData()) {
            String mode = loc.getMode();
            String gameType = loc.getGameType();
            if (mode != null && !mode.isEmpty()) {
                return gameType + "/" + mode;
            }
            return gameType;
        }
        return null;
    }

    /**
     * Take a screenshot and save it to pokedex folder.
     * Uses direct LWJGL/OpenGL calls for obfuscation safety.
     */
    private String captureScreenshot(int uid) {
        try {
            File screenshotDir = new File(mc.gameDirectory, "hunchclient/pokedex/screenshots");
            screenshotDir.mkdirs();

            String filename = "capture_" + uid + "_" + System.currentTimeMillis() + ".png";
            File screenshotFile = new File(screenshotDir, filename);

            // Get window dimensions from Minecraft's window
            int width = mc.getWindow().getWidth();
            int height = mc.getWindow().getHeight();

            // Use direct OpenGL calls - these won't be obfuscated
            ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 4);
            GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);

            // Convert to BufferedImage (flip vertically since OpenGL reads bottom-up)
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int i = (x + (height - 1 - y) * width) * 4;
                    int r = buffer.get(i) & 0xFF;
                    int g = buffer.get(i + 1) & 0xFF;
                    int b = buffer.get(i + 2) & 0xFF;
                    int a = buffer.get(i + 3) & 0xFF;
                    image.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
                }
            }

            // Save asynchronously to avoid lag
            final BufferedImage finalImage = image;
            CompletableFuture.runAsync(() -> {
                try {
                    ImageIO.write(finalImage, "PNG", screenshotFile);
                    System.out.println("[Pokedex] Screenshot saved: " + screenshotFile.getAbsolutePath());
                } catch (Exception e) {
                    System.err.println("[Pokedex] Failed to save screenshot: " + e.getMessage());
                }
            });

            return "hunchclient/pokedex/screenshots/" + filename;
        } catch (Throwable e) {
            // Catch Throwable to handle NoClassDefFoundError, NoSuchMethodError etc.
            System.err.println("[Pokedex] Failed to capture screenshot: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Extract skin URL from player using Mojang API
     */
    private String extractSkinUrl(Player player) {
        try {
            // Use player's UUID directly to fetch skin from Mojang API
            UUID uuid = player.getUUID();
            if (uuid != null) {
                return fetchSkinUrlFromMojangApi(uuid);
            }
        } catch (Exception e) {
            System.err.println("[Pokedex] Failed to extract skin URL: " + e.getMessage());
        }
        return null;
    }

    /**
     * Fallback: Fetch skin URL from Mojang API
     */
    private String fetchSkinUrlFromMojangApi(UUID uuid) {
        try {
            String sessionUrl = "https://sessionserver.mojang.com/session/minecraft/profile/" + uuid.toString().replace("-", "");
            URL url = URI.create(sessionUrl).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);

            if (conn.getResponseCode() == 200) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }

                    JsonObject json = JsonParser.parseString(sb.toString()).getAsJsonObject();
                    if (json.has("properties")) {
                        for (var prop : json.getAsJsonArray("properties")) {
                            JsonObject propObj = prop.getAsJsonObject();
                            if ("textures".equals(propObj.get("name").getAsString())) {
                                String value = propObj.get("value").getAsString();
                                String decoded = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
                                JsonObject texturesJson = JsonParser.parseString(decoded).getAsJsonObject();

                                if (texturesJson.has("textures")) {
                                    JsonObject texturesObj = texturesJson.getAsJsonObject("textures");
                                    if (texturesObj.has("SKIN")) {
                                        return texturesObj.getAsJsonObject("SKIN").get("url").getAsString();
                                    }
                                }
                            }
                        }
                    }
                }
            }
            conn.disconnect();
        } catch (Exception e) {
            System.err.println("[Pokedex] Mojang API fallback failed: " + e.getMessage());
        }
        return null;
    }

    /**
     * Download skin from URL and cache locally
     */
    private String downloadAndCacheSkin(int uid, String skinUrl) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                File skinDir = new File(mc.gameDirectory, "hunchclient/pokedex/skins");
                skinDir.mkdirs();

                String filename = "skin_" + uid + ".png";
                File skinFile = new File(skinDir, filename);

                // Download the skin
                URL url = URI.create(skinUrl).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                if (conn.getResponseCode() == 200) {
                    try (InputStream in = conn.getInputStream()) {
                        BufferedImage image = ImageIO.read(in);
                        ImageIO.write(image, "PNG", skinFile);
                        System.out.println("[Pokedex] Cached skin for #" + uid);
                        return "hunchclient/pokedex/skins/" + filename;
                    }
                }
                conn.disconnect();
            } catch (Exception e) {
                System.err.println("[Pokedex] Failed to download skin: " + e.getMessage());
            }
            return null;
        }).join(); // Block and wait for result
    }

    /**
     * Get the skin file for a UID
     */
    public File getSkinFile(int uid) {
        CapturedEntry entry = captured.get(uid);
        if (entry != null && entry.skinPath != null) {
            return new File(mc.gameDirectory, entry.skinPath);
        }
        return null;
    }

    /**
     * Get the screenshot file for a UID
     */
    public File getScreenshotFile(int uid) {
        CapturedEntry entry = captured.get(uid);
        if (entry != null && entry.screenshotPath != null) {
            return new File(mc.gameDirectory, entry.screenshotPath);
        }
        return null;
    }

    /**
     * Check if a UID is captured
     */
    public boolean isCaptured(int uid) {
        return captured.containsKey(uid);
    }

    /**
     * Get captured entry for a UID
     */
    public CapturedEntry getCapturedEntry(int uid) {
        return captured.get(uid);
    }

    /**
     * Get the UID for a username (case-insensitive)
     * @return UID or -1 if not found
     */
    public int getUid(String username) {
        Integer uid = allUsers.get(username.toLowerCase(Locale.ROOT));
        return uid != null ? uid : -1;
    }

    /**
     * Get username for a UID
     */
    public String getUsername(int uid) {
        return uidToUsername.get(uid);
    }

    /**
     * Get all UIDs sorted
     */
    public List<Integer> getAllUids() {
        List<Integer> uids = new ArrayList<>(uidToUsername.keySet());
        Collections.sort(uids);
        return uids;
    }

    /**
     * Get number of captured users
     */
    public int getCapturedCount() {
        return captured.size();
    }

    /**
     * Reset/clear all captured users
     */
    public void resetAllCaptured() {
        captured.clear();
        saveCaptured();
        System.out.println("[Pokedex] All captured users have been reset!");
    }

    // Cooldown tracking for online status refresh (prevent API spam)
    private final Map<Integer, Long> lastStatusRefresh = new ConcurrentHashMap<>();
    private static final long STATUS_REFRESH_COOLDOWN = 60000; // 1 minute cooldown

    /**
     * Check if online status can be refreshed for a user (respects cooldown)
     */
    public boolean canRefreshStatus(int uid) {
        Long lastRefresh = lastStatusRefresh.get(uid);
        if (lastRefresh == null) return true;
        return System.currentTimeMillis() - lastRefresh > STATUS_REFRESH_COOLDOWN;
    }

    /**
     * Refresh online status for a specific user (non-aggressive, respects cooldown)
     * @param uid The user's UID
     * @return CompletableFuture that completes when refresh is done
     */
    public CompletableFuture<Boolean> refreshOnlineStatus(int uid) {
        if (!canRefreshStatus(uid)) {
            return CompletableFuture.completedFuture(false);
        }

        CapturedEntry entry = captured.get(uid);
        if (entry == null) {
            return CompletableFuture.completedFuture(false);
        }

        lastStatusRefresh.put(uid, System.currentTimeMillis());

        return CompletableFuture.supplyAsync(() -> {
            try {
                String cleanName = extractCleanUsername(entry.name);
                String uuid = getUuidFromMojang(cleanName);
                if (uuid == null) return false;

                String playerUrl = HYPIXEL_PROXY + "player?uuid=" + uuid;
                String playerResponse = fetchWithRedirects(playerUrl);
                if (playerResponse == null) return false;

                JsonObject playerJson = JsonParser.parseString(playerResponse).getAsJsonObject();
                if (!playerJson.has("player") || playerJson.get("player").isJsonNull()) return false;

                JsonObject player = playerJson.getAsJsonObject("player");
                Long lastLogin = player.has("lastLogin") ? player.get("lastLogin").getAsLong() : null;
                Long lastLogout = player.has("lastLogout") ? player.get("lastLogout").getAsLong() : null;

                // Update entry with new status
                CapturedEntry newEntry = new CapturedEntry(
                    entry.name, entry.date, entry.time, entry.screenshotPath,
                    entry.skinPath, entry.skinUrl, entry.location,
                    entry.skyblockIsland, entry.gameMode,
                    entry.sbLevel, entry.skillAverage, entry.cataLevel, entry.networth,
                    entry.dungeonPbs, entry.armorData, entry.weekday, entry.skillsData,
                    lastLogin, lastLogout
                );

                captured.put(uid, newEntry);
                saveCaptured();
                System.out.println("[Pokedex] Refreshed online status for #" + uid);
                return true;
            } catch (Exception e) {
                System.err.println("[Pokedex] Failed to refresh status for #" + uid + ": " + e.getMessage());
                return false;
            }
        });
    }

    /**
     * Check if a user is currently online based on lastLogin/lastLogout
     * @return true if online (lastLogin > lastLogout), false otherwise
     */
    public boolean isOnline(CapturedEntry entry) {
        if (entry == null || entry.lastLogin == null) return false;
        if (entry.lastLogout == null) return true; // Never logged out = still online
        return entry.lastLogin > entry.lastLogout;
    }

    /**
     * Get "last seen" time string for display
     * @return formatted string like "2h ago", "5d ago", "Online"
     */
    public String getLastSeenText(CapturedEntry entry) {
        if (entry == null) return "Unknown";
        if (isOnline(entry)) return "Online";
        if (entry.lastLogout == null) return "Unknown";

        long now = System.currentTimeMillis();
        long diff = now - entry.lastLogout;

        long seconds = diff / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) return days + "d ago";
        if (hours > 0) return hours + "h ago";
        if (minutes > 0) return minutes + "m ago";
        return "Just now";
    }

    /**
     * Get total number of OG users
     */
    public int getTotalCount() {
        return allUsers.size();
    }

    /**
     * Check if data is loaded
     */
    public boolean isLoaded() {
        return loaded;
    }

    /**
     * Save captured users to file
     */
    private void saveCaptured() {
        File file = new File(mc.gameDirectory, "hunchclient/pokedex.json");
        file.getParentFile().mkdirs();

        JsonObject json = new JsonObject();
        JsonObject capturedJson = new JsonObject();

        for (Map.Entry<Integer, CapturedEntry> entry : captured.entrySet()) {
            JsonObject entryJson = new JsonObject();
            CapturedEntry e = entry.getValue();
            entryJson.addProperty("name", e.name);
            entryJson.addProperty("date", e.date);
            if (e.time != null) entryJson.addProperty("time", e.time);
            if (e.screenshotPath != null) entryJson.addProperty("screenshot", e.screenshotPath);
            if (e.skinPath != null) entryJson.addProperty("skin", e.skinPath);
            if (e.skinUrl != null) entryJson.addProperty("skinUrl", e.skinUrl);
            if (e.location != null) entryJson.addProperty("location", e.location);
            if (e.skyblockIsland != null) entryJson.addProperty("skyblockIsland", e.skyblockIsland);
            if (e.gameMode != null) entryJson.addProperty("gameMode", e.gameMode);
            // Profile Viewer data
            if (e.sbLevel != null) entryJson.addProperty("sbLevel", e.sbLevel);
            if (e.skillAverage != null) entryJson.addProperty("skillAverage", e.skillAverage);
            if (e.cataLevel != null) entryJson.addProperty("cataLevel", e.cataLevel);
            if (e.networth != null) entryJson.addProperty("networth", e.networth);
            if (e.dungeonPbs != null) entryJson.addProperty("dungeonPbs", e.dungeonPbs);
            if (e.armorData != null) entryJson.addProperty("armorData", e.armorData);
            if (e.weekday != null) entryJson.addProperty("weekday", e.weekday);
            if (e.skillsData != null) entryJson.addProperty("skillsData", e.skillsData);
            if (e.lastLogin != null) entryJson.addProperty("lastLogin", e.lastLogin);
            if (e.lastLogout != null) entryJson.addProperty("lastLogout", e.lastLogout);
            capturedJson.add(String.valueOf(entry.getKey()), entryJson);
        }

        json.add("captured", capturedJson);

        try (FileWriter writer = new FileWriter(file)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(json, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Load captured users from file
     */
    private void loadCaptured() {
        File file = new File(mc.gameDirectory, "hunchclient/pokedex.json");
        if (!file.exists()) {
            return;
        }

        try (FileReader reader = new FileReader(file)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();

            if (json.has("captured")) {
                JsonObject capturedJson = json.getAsJsonObject("captured");

                captured.clear();
                for (String uidStr : capturedJson.keySet()) {
                    try {
                        int uid = Integer.parseInt(uidStr);
                        JsonObject entryJson = capturedJson.getAsJsonObject(uidStr);
                        String name = entryJson.get("name").getAsString();
                        String date = entryJson.has("date") ? entryJson.get("date").getAsString() : "unknown";
                        String time = entryJson.has("time") ? entryJson.get("time").getAsString() : null;
                        String screenshot = entryJson.has("screenshot") ? entryJson.get("screenshot").getAsString() : null;
                        String skin = entryJson.has("skin") ? entryJson.get("skin").getAsString() : null;
                        String skinUrl = entryJson.has("skinUrl") ? entryJson.get("skinUrl").getAsString() : null;
                        String location = entryJson.has("location") ? entryJson.get("location").getAsString() : null;
                        String skyblockIsland = entryJson.has("skyblockIsland") ? entryJson.get("skyblockIsland").getAsString() : null;
                        String gameMode = entryJson.has("gameMode") ? entryJson.get("gameMode").getAsString() : null;
                        // Profile Viewer data
                        Double sbLevel = entryJson.has("sbLevel") ? entryJson.get("sbLevel").getAsDouble() : null;
                        Double skillAverage = entryJson.has("skillAverage") ? entryJson.get("skillAverage").getAsDouble() : null;
                        Double cataLevel = entryJson.has("cataLevel") ? entryJson.get("cataLevel").getAsDouble() : null;
                        Double networth = entryJson.has("networth") ? entryJson.get("networth").getAsDouble() : null;
                        String dungeonPbs = entryJson.has("dungeonPbs") ? entryJson.get("dungeonPbs").getAsString() : null;
                        String armorData = entryJson.has("armorData") ? entryJson.get("armorData").getAsString() : null;
                        String weekday = entryJson.has("weekday") ? entryJson.get("weekday").getAsString() : null;
                        String skillsData = entryJson.has("skillsData") ? entryJson.get("skillsData").getAsString() : null;
                        Long lastLogin = entryJson.has("lastLogin") ? entryJson.get("lastLogin").getAsLong() : null;
                        Long lastLogout = entryJson.has("lastLogout") ? entryJson.get("lastLogout").getAsLong() : null;

                        captured.put(uid, new CapturedEntry(name, date, time, screenshot, skin, skinUrl, location,
                                skyblockIsland, gameMode, sbLevel, skillAverage, cataLevel, networth,
                                dungeonPbs, armorData, weekday, skillsData, lastLogin, lastLogout));
                    } catch (NumberFormatException e) {
                        // Skip invalid entries
                    }
                }

                System.out.println("[Pokedex] Loaded " + captured.size() + " captured users");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Update an existing captured entry with profile data (called async after capture)
     */
    public void updateEntryWithProfileData(int uid, ProfileData profileData) {
        CapturedEntry oldEntry = captured.get(uid);
        if (oldEntry == null || profileData == null) return;

        // Get weekday
        String weekday = java.time.LocalDate.now().getDayOfWeek().toString();

        CapturedEntry newEntry = new CapturedEntry(
            oldEntry.name, oldEntry.date, oldEntry.time, oldEntry.screenshotPath,
            oldEntry.skinPath, oldEntry.skinUrl, oldEntry.location,
            oldEntry.skyblockIsland, oldEntry.gameMode,
            profileData.sbLevel, profileData.skillAvg, profileData.cataLevel, profileData.networth,
            profileData.dungeonPbs, null, weekday, profileData.skillsData,
            profileData.lastLogin, profileData.lastLogout
        );

        captured.put(uid, newEntry);
        saveCaptured();
        System.out.println("[Pokedex] Updated #" + uid + " with profile data");
    }

    /**
     * Fetch and store profile data for a captured user (async)
     */
    public void fetchProfileDataAsync(int uid, String username) {
        CompletableFuture.runAsync(() -> {
            try {
                // Extract clean username (remove NameProtect format like "#17 Username")
                String cleanName = extractCleanUsername(username);
                System.out.println("[Pokedex] Fetching profile data for " + cleanName + " (original: " + username + ")...");

                // Get UUID first
                String uuid = getUuidFromMojang(cleanName);

                // Fetch player status (lastLogin/lastLogout)
                Long lastLogin = null;
                Long lastLogout = null;
                if (uuid != null) {
                    try {
                        String playerUrl = HYPIXEL_PROXY + "player?uuid=" + uuid;
                        String playerResponse = fetchWithRedirects(playerUrl);
                        if (playerResponse != null) {
                            JsonObject playerJson = JsonParser.parseString(playerResponse).getAsJsonObject();
                            if (playerJson.has("player") && !playerJson.get("player").isJsonNull()) {
                                JsonObject player = playerJson.getAsJsonObject("player");
                                if (player.has("lastLogin")) {
                                    lastLogin = player.get("lastLogin").getAsLong();
                                }
                                if (player.has("lastLogout")) {
                                    lastLogout = player.get("lastLogout").getAsLong();
                                }
                                System.out.println("[Pokedex] Got player status - lastLogin: " + lastLogin + ", lastLogout: " + lastLogout);
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("[Pokedex] Failed to fetch player status: " + e.getMessage());
                    }
                }

                JsonObject data = fetchHypixelProfile(cleanName);
                if (data == null) {
                    System.err.println("[Pokedex] No profile data returned for " + cleanName);
                    return;
                }

                ProfileData profileData = parseProfileData(data, lastLogin, lastLogout);
                if (profileData != null) {
                    updateEntryWithProfileData(uid, profileData);
                    System.out.println("[Pokedex] Profile data saved for " + cleanName);
                } else {
                    System.err.println("[Pokedex] Failed to parse profile data for " + cleanName);
                }
            } catch (Exception e) {
                System.err.println("[Pokedex] Failed to fetch profile for " + username + ": " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /**
     * Extract clean username from NameProtect format
     * e.g., "#17 Username" -> "Username", "Username" -> "Username"
     */
    private String extractCleanUsername(String name) {
        if (name == null || name.isEmpty()) return name;

        // Remove color codes
        String clean = name.replaceAll("§.", "");

        // Check for NameProtect format: #UID Username
        if (clean.startsWith("#")) {
            int spaceIdx = clean.indexOf(' ');
            if (spaceIdx > 0 && spaceIdx < clean.length() - 1) {
                return clean.substring(spaceIdx + 1).trim();
            }
        }

        return clean.trim();
    }
}
