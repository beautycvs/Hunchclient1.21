package dev.hunchclient.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.Scoreboard;

/**
 * Utility helpers for reading the Hypixel sidebar scoreboard and detecting dungeon state.
 */
public final class DungeonUtils {

    private static final Pattern STRIP_FORMAT_PATTERN = Pattern.compile("\u00a7[0-9a-fklmnor]", Pattern.CASE_INSENSITIVE);

    // Match specific dungeon indicators, NOT "Dungeon Hub"
    // - "The Catacombs" with optional floor indicator
    // - "Time Elapsed:" (only appears in active dungeons)
    // - "Cleared: X%" (only appears in active dungeons)
    // - Master Mode indicator
    private static final Pattern DUNGEON_KEYWORDS = Pattern.compile(
        "(the catacombs|time elapsed:|cleared:|master mode|\\b[mf][1-7](?!\\d))",
        Pattern.CASE_INSENSITIVE
    );

    private static final Comparator<PlayerScoreEntry> SCOREBOARD_ORDER =
        Comparator.comparingInt(PlayerScoreEntry::value).reversed();

    // PERFORMANCE: Cache dungeon status to avoid checking scoreboard every tick
    private static boolean cachedInDungeon = false;
    private static boolean cachedInBossFight = false;
    private static long lastCheckTime = 0;
    private static final long CHECK_INTERVAL_MS = 100; // Re-check every 100ms (10 times per second) for fast boss detection

    private static final Pattern FLOOR_SHORT_PATTERN = Pattern.compile("\\b([MF])([1-7])\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern FLOOR_LONG_PATTERN = Pattern.compile("\\bFLOOR\\s+(VII|VI|IV|V|III|II|I|1|2|3|4|5|6|7)\\b",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern MASTER_MODE_PATTERN = Pattern.compile("MASTER\\s+MODE", Pattern.CASE_INSENSITIVE);

    private DungeonUtils() {
    }

    /**
     * Returns true if player is on their Private Island.
     * Uses LocationManager (/locraw) as primary source, scoreboard as fallback.
     */
    public static boolean isOnPrivateIsland() {
        // Primary: Use LocationManager (from /locraw command)
        LocationManager locationManager = LocationManager.getInstance();
        if (locationManager.hasLocationData()) {
            return locationManager.isOnPrivateIsland();
        }

        // Fallback: Check scoreboard for "Your Island" indicator
        SidebarSnapshot snapshot = readSidebar();

        if (snapshot == SidebarSnapshot.EMPTY) {
            return false;
        }

        // Check title and lines for "Your Island"
        String titleLower = snapshot.title().toLowerCase(Locale.ROOT);
        if (titleLower.contains("your isla") || titleLower.contains("private isla")) {
            return true;
        }

        for (String line : snapshot.lines()) {
            String lineLower = line.toLowerCase(Locale.ROOT);
            if (lineLower.contains("your isla") || lineLower.contains("private isla")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns true if the current scoreboard title or lines indicate the player is in a dungeon.
     * CACHED: Only checks scoreboard every 2 seconds to reduce performance impact.
     */
    public static boolean isInDungeon() {
        long now = System.currentTimeMillis();

        // Return cached value if we checked recently
        if (now - lastCheckTime < CHECK_INTERVAL_MS) {
            return cachedInDungeon;
        }

        // Update cache
        lastCheckTime = now;
        cachedInDungeon = checkDungeonStatus();
        cachedInBossFight = checkBossFightStatus();
        return cachedInDungeon;
    }


    /**
     * STRICT dungeon check - only returns true if player is in an ACTIVE dungeon run.
     * Unlike isInDungeon(), this does NOT match the Dungeon Hub.
     * Uses only "Time Elapsed:" and "Cleared:" which ONLY appear during active runs.
     * 
     * Use this for features that must NEVER trigger outside an active dungeon.
     */
    public static boolean isInActiveDungeon() {
        SidebarSnapshot snapshot = readSidebar();
        
        if (snapshot == SidebarSnapshot.EMPTY) {
            return false;
        }
        
        // Only check for indicators that EXCLUSIVELY appear in active dungeons
        for (String line : snapshot.lines()) {
            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.contains("time elapsed:") || lower.contains("cleared:")) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Returns true if currently in a boss fight.
     * CACHED: Only checks every 2 seconds.
     *
     * Detection method: Checks if dungeon map is missing (map disappears in boss)
     */
    public static boolean isInBossFight() {
        long now = System.currentTimeMillis();

        // Return cached value if we checked recently
        if (now - lastCheckTime < CHECK_INTERVAL_MS) {
            return cachedInBossFight;
        }

        // Update cache (will be updated by isInDungeon())
        isInDungeon();
        return cachedInBossFight;
    }

    /**
     * Force update the dungeon status cache.
     * Call this when changing worlds to immediately update the cache.
     */
    public static void updateCache() {
        lastCheckTime = 0; // Force next isInDungeon() call to re-check
    }

    /**
     * Internal method that actually checks the scoreboard (no caching).
     */
    private static boolean checkDungeonStatus() {
        SidebarSnapshot snapshot = readSidebar();

        if (snapshot == SidebarSnapshot.EMPTY) {
            return false;
        }

        if (textContainsDungeonKeywords(snapshot.title())) {
            return true;
        }

        for (String line : snapshot.lines()) {
            if (textContainsDungeonKeywords(line)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Internal method that checks if player is in a boss fight (no caching).
     *
     * Detection: Checks if dungeon map is missing from slot 8
     * In dungeons, players have a map in slot 8. When entering boss, the map disappears.
     */
    private static boolean checkBossFightStatus() {
        Minecraft client = Minecraft.getInstance();

        // Must be in dungeon first
        if (!cachedInDungeon) {
            return false;
        }

        if (client == null || client.player == null) {
            return false;
        }

        // Check if map is missing from slot 8 (where dungeon map normally is)
        net.minecraft.world.item.ItemStack mapStack = client.player.getInventory().getItem(8);

        // If slot 8 is empty or not a filled map, likely in boss
        if (mapStack.isEmpty()) {
            return true;
        }

        // Check if it's actually a filled map
        if (!mapStack.is(net.minecraft.world.item.Items.FILLED_MAP)) {
            return true; // Something else in slot 8 = likely boss
        }

        return false; // Map is present = not in boss
    }

    /**
     * Reads the current sidebar scoreboard (title + visible lines).
     * Returns {@link SidebarSnapshot#EMPTY} when no sidebar is active.
     */
    public static SidebarSnapshot readSidebar() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null) {
            return SidebarSnapshot.EMPTY;
        }

        Scoreboard scoreboard = client.level.getScoreboard();
        if (scoreboard == null) {
            return SidebarSnapshot.EMPTY;
        }

        Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (objective == null) {
            return SidebarSnapshot.EMPTY;
        }

        String title = stripFormatting(objective.getDisplayName());

        List<PlayerScoreEntry> entries = new ArrayList<>(scoreboard.listPlayerScores(objective));

        if (entries.isEmpty()) {
            return new SidebarSnapshot(title, List.of());
        }

        entries.sort(SCOREBOARD_ORDER);

        List<String> lines = new ArrayList<>(entries.size());
        for (PlayerScoreEntry entry : entries) {
            String owner = entry.owner();

            // Hypixel uses Teams to format scoreboard text
            // The actual text is in: Team.prefix + owner + Team.suffix
            var team = scoreboard.getPlayersTeam(owner);

            String line;
            if (team != null) {
                // Build the full line from team formatting
                String prefix = team.getPlayerPrefix() != null ? team.getPlayerPrefix().getString() : "";
                String suffix = team.getPlayerSuffix() != null ? team.getPlayerSuffix().getString() : "";
                String fullText = prefix + owner + suffix;
                line = stripFormatting(fullText);
            } else {
                // Fallback: use display text if available
                Component displayField = entry.display();
                if (displayField != null) {
                    line = stripFormatting(displayField);
                } else {
                    line = stripFormatting(entry.ownerName());
                }
            }

            if (!line.isEmpty()) {
                lines.add(line);
            }
            if (lines.size() >= 15) {
                break; // scoreboard renders max 15 lines
            }
        }

        if (lines.isEmpty()) {
            return new SidebarSnapshot(title, List.of());
        }

        return new SidebarSnapshot(title, List.copyOf(lines));
    }

    /**
     * Removes Minecraft formatting codes from the provided text.
     */
    public static String stripFormatting(Component text) {
        if (text == null) {
            return "";
        }
        return stripFormatting(text.getString());
    }

    /**
     * Removes Minecraft formatting codes from the provided string.
     */
    public static String stripFormatting(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        return STRIP_FORMAT_PATTERN.matcher(input).replaceAll("").trim();
    }

    private static boolean textContainsDungeonKeywords(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return DUNGEON_KEYWORDS.matcher(lower).find();
    }

    /**
     * Extracts the Room ID from the scoreboard.
     * In Hypixel dungeons, the Room ID appears as component coordinates "x,y" on the first line.
     * Example: "102,66" means room at grid position (102, 66)
     *
     * Returns null if not in dungeon or room ID not found.
     */
    public static String getCurrentRoomID() {
        SidebarSnapshot snapshot = readSidebar();

        if (snapshot == SidebarSnapshot.EMPTY) {
            return null;
        }

        // DEBUG: Log all scoreboard lines
        boolean debugMode = false; // Set to false to disable debug logging
        if (debugMode && !snapshot.lines().isEmpty()) {
            System.out.println("[DungeonUtils DEBUG] Scoreboard lines:");
            for (int i = 0; i < Math.min(5, snapshot.lines().size()); i++) {
                String line = snapshot.lines().get(i);
                System.out.println("  Line " + i + ": [" + line + "] (length: " + line.length() + ")");
                // Show character codes for debugging
                if (i == 0) {
                    StringBuilder hexDump = new StringBuilder("    Hex: ");
                    for (char c : line.toCharArray()) {
                        hexDump.append(String.format("%04x ", (int)c));
                    }
                    System.out.println(hexDump);
                }
            }
        }

        // PRIORITY 1: Check line 0 for component format "x,y" (most common in dungeons)
        // Extract "x,y" pattern from the line (may be at the end after date and server ID)
        if (!snapshot.lines().isEmpty()) {
            String firstLine = snapshot.lines().get(0);
            String cleaned = firstLine.trim();

            if (debugMode) {
                System.out.println("[DungeonUtils DEBUG] Testing line 0: [" + cleaned + "]");
            }

            // Use regex to EXTRACT "x,y" pattern from anywhere in the line (supports negative coordinates)
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(-?\\d+)\\s*,\\s*(-?\\d+)");
            java.util.regex.Matcher matcher = pattern.matcher(cleaned);

            if (matcher.find()) {
                // Extract and clean the room ID (remove spaces)
                String x = matcher.group(1);
                String y = matcher.group(2);
                String result = x + "," + y;

                if (debugMode) {
                    System.out.println("[DungeonUtils DEBUG] ✓ EXTRACTED Room ID: " + result);
                }
                return result;
            }

            if (debugMode) {
                System.out.println("[DungeonUtils DEBUG] ✗ No x,y pattern found in line 0");
            }
        }

        // PRIORITY 2: Search all lines for component format "x,y"
        for (String line : snapshot.lines()) {
            String trimmed = line.trim();

            // Skip empty lines
            if (trimmed.isEmpty()) continue;

            // Component format: "x,y" or "102,66" (allow spaces around comma)
            if (trimmed.matches("^\\d+\\s*,\\s*\\d+$")) {
                // Remove spaces for consistency
                return trimmed.replaceAll("\\s+", "");
            }
        }

        // PRIORITY 3: Look for prefixed format "Room: XYZ"
        for (String line : snapshot.lines()) {
            String trimmed = line.trim();
            String lower = trimmed.toLowerCase();

            if (lower.startsWith("room") && trimmed.contains(":")) {
                String[] parts = trimmed.split(":", 2);
                if (parts.length == 2) {
                    String id = parts[1].trim();
                    if (!id.isEmpty()) {
                        return id;
                    }
                }
            }
        }

        // PRIORITY 4: Alphanumeric room codes (fallback)
        for (String line : snapshot.lines()) {
            String trimmed = line.trim();
            String lower = trimmed.toLowerCase();

            // Skip common non-room-id lines
            if (lower.contains("cleared") ||
                lower.contains("time") ||
                lower.contains("deaths") ||
                lower.contains("secrets") ||
                lower.contains("crypts") ||
                lower.contains("puzzle") ||
                lower.contains("catacombs") ||
                lower.contains("floor") ||
                lower.contains("master")) {
                continue;
            }

            // Skip server ID line (contains date)
            if (trimmed.matches(".*\\d{1,2}/\\d{1,2}/\\d{2,4}.*")) {
                continue;
            }

            // Alphanumeric codes: "AB123", "XYZ", etc.
            if (trimmed.matches("^[A-Za-z0-9]{2,8}$")) {
                // Not pure numbers (those are usually scores)
                if (!trimmed.matches("^\\d+$")) {
                    return trimmed;
                }
            }
        }

        return null;
    }

    /**
     * Extracts numeric Room ID from RoomData name format.
     * Example: "AB123" -> 123 (if RoomData stores it this way)
     * Returns -1 if not parseable.
     */
    public static int parseRoomIDNumber(String roomName) {
        if (roomName == null || roomName.isEmpty()) {
            return -1;
        }

        // Try to extract trailing number from room name
        // Example: "Lava Room 23" -> 23
        String[] parts = roomName.split("\\s+");
        for (int i = parts.length - 1; i >= 0; i--) {
            try {
                return Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                // Continue searching
            }
        }

        return -1;
    }

    /**
     * Returns information about the currently detected dungeon floor.
     * If no floor could be resolved, {@link FloorInfo#UNKNOWN} is returned.
     */
    public static FloorInfo getCurrentFloorInfo() {
        SidebarSnapshot snapshot = readSidebar();
        if (snapshot == SidebarSnapshot.EMPTY) {
            return FloorInfo.UNKNOWN;
        }
        return extractFloorInfo(snapshot);
    }

    /**
     * Returns {@code true} if the player is on one of the provided floors.
     * Mode (normal vs master) is ignored. To specifically check for master mode, call {@link #isMasterMode()}.
     */
    public static boolean isFloor(int... floors) {
        if (floors == null || floors.length == 0) {
            return false;
        }

        FloorInfo info = getCurrentFloorInfo();
        if (!info.isValid()) {
            return false;
        }

        int current = info.number();
        for (int floor : floors) {
            if (Math.abs(floor) == current) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns {@code true} when the current dungeon is a Master Mode floor.
     */
    public static boolean isMasterMode() {
        return getCurrentFloorInfo().master();
    }

    /**
     * Detects the current Floor 7 boss phase based on player height.
     * Returns {@link F7Phase#UNKNOWN} when not in an F7 boss encounter.
     */
    public static F7Phase getF7Phase() {
        Minecraft client = Minecraft.getInstance();
        Player player = client != null ? client.player : null;
        return getF7Phase(player);
    }

    /**
     * Detects the current Floor 7 boss phase for the supplied player.
     */
    public static F7Phase getF7Phase(Player player) {
        FloorInfo info = getCurrentFloorInfo();
        if (!info.isValid() || info.number() != 7) {
            return F7Phase.UNKNOWN;
        }
        if (!isInBossFight()) {
            return F7Phase.UNKNOWN;
        }
        if (player == null) {
            return F7Phase.UNKNOWN;
        }

        double y = player.getY();
        if (y > 210) {
            return F7Phase.P1;
        }
        if (y > 155) {
            return F7Phase.P2;
        }
        if (y > 100) {
            return F7Phase.P3;
        }
        if (y > 45) {
            return F7Phase.P4;
        }
        return F7Phase.P5;
    }

    /**
     * Returns the player's current section during F7 P3 (Maxor chase).
     * If the player is not in Phase 3, {@link F7P3Section#UNKNOWN} is returned.
     */
    public static F7P3Section getF7P3Section() {
        Minecraft client = Minecraft.getInstance();
        Player player = client != null ? client.player : null;
        return getF7P3Section(player);
    }

    /**
     * Returns the supplied player's current section during F7 P3 (Maxor chase).
     */
    public static F7P3Section getF7P3Section(Player player) {
        if (player == null) {
            return F7P3Section.UNKNOWN;
        }

        if (getF7Phase(player) != F7Phase.P3) {
            return F7P3Section.UNKNOWN;
        }

        double x = player.getX();
        double z = player.getZ();

        if (x >= 89.0 && x <= 113.0 && z >= 30.0 && z <= 122.0) {
            return F7P3Section.S1;
        }
        if (x >= 19.0 && x <= 111.0 && z >= 121.0 && z <= 145.0) {
            return F7P3Section.S2;
        }
        if (x >= -6.0 && x <= 19.0 && z >= 51.0 && z <= 143.0) {
            return F7P3Section.S3;
        }
        if (x >= -2.0 && x <= 90.0 && z >= 27.0 && z <= 51.0) {
            return F7P3Section.S4;
        }

        return F7P3Section.UNKNOWN;
    }

    private static FloorInfo extractFloorInfo(SidebarSnapshot snapshot) {
        boolean master = false;
        int floorNumber = 0;

        List<String> candidates = new ArrayList<>(1 + snapshot.lines().size());
        if (!snapshot.title().isEmpty()) {
            candidates.add(snapshot.title());
        }
        candidates.addAll(snapshot.lines());

        for (String raw : candidates) {
            if (raw == null || raw.isEmpty()) {
                continue;
            }

            String normalized = raw.toUpperCase(Locale.ROOT);

            if (MASTER_MODE_PATTERN.matcher(normalized).find()) {
                master = true;
            }

            var shortMatcher = FLOOR_SHORT_PATTERN.matcher(normalized);
            if (shortMatcher.find()) {
                String prefix = shortMatcher.group(1).toUpperCase(Locale.ROOT);
                if (prefix.startsWith("M")) {
                    master = true;
                }
                floorNumber = parseFloorToken(shortMatcher.group(2));
                if (floorNumber > 0) {
                    break;
                }
            }

            var longMatcher = FLOOR_LONG_PATTERN.matcher(normalized);
            if (longMatcher.find()) {
                floorNumber = parseFloorToken(longMatcher.group(1));
                if (floorNumber > 0) {
                    break;
                }
            }
        }

        if (floorNumber <= 0) {
            return master ? new FloorInfo(0, true) : FloorInfo.UNKNOWN;
        }

        return new FloorInfo(floorNumber, master);
    }

    private static int parseFloorToken(String token) {
        if (token == null) {
            return 0;
        }

        String upper = token.toUpperCase(Locale.ROOT).trim();
        return switch (upper) {
            case "1", "I" -> 1;
            case "2", "II" -> 2;
            case "3", "III" -> 3;
            case "4", "IV" -> 4;
            case "5", "V" -> 5;
            case "6", "VI" -> 6;
            case "7", "VII" -> 7;
            default -> {
                try {
                    yield Integer.parseInt(upper);
                } catch (NumberFormatException ignored) {
                    yield 0;
                }
            }
        };
    }

    public enum F7Phase {
        UNKNOWN,
        P1,
        P2,
        P3,
        P4,
        P5
    }

    public enum F7P3Section {
        UNKNOWN,
        S1,
        S2,
        S3,
        S4
    }

    public record FloorInfo(int number, boolean master) {
        public static final FloorInfo UNKNOWN = new FloorInfo(0, false);

        public boolean isValid() {
            return number > 0;
        }
    }

    public record SidebarSnapshot(String title, List<String> lines) {
        public static final SidebarSnapshot EMPTY = new SidebarSnapshot("", List.of());
    }
}
