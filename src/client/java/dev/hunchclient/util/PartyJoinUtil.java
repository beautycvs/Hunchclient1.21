package dev.hunchclient.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Universal utility for detecting and parsing party/dungeon join messages.
 * Used by both ShitterList and SBD AutoKick modules.
 *
 * WATCHDOG SAFE: YES
 * - Pure message parsing, no gameplay automation
 */
public class PartyJoinUtil {

    // Pattern for regular party join
    // Example: "PlayerName joined the party."
    private static final Pattern PARTY_JOIN = Pattern.compile(
        "^([\\w]+) joined the party\\.$"
    );

    // Pattern for dungeon group join (simplified version without class info)
    // Example: "Party Finder > PlayerName joined the dungeon group!"
    private static final Pattern DUNGEON_JOIN_SIMPLE = Pattern.compile(
        "^Party Finder > ([\\w]+) joined the dungeon group!$"
    );

    // Pattern for dungeon group join with class info
    // Example: "Party Finder > PlayerName joined the dungeon group! (Archer Level 50)"
    private static final Pattern DUNGEON_JOIN_DETAILED = Pattern.compile(
        "^Party Finder > ([\\w]+) joined the dungeon group! \\(([\\w]+) Level (\\d+)\\)$"
    );

    /**
     * Parse a chat message and detect if it's a party or dungeon join.
     *
     * @param message The raw chat message (should be stripped of color codes before calling)
     * @return JoinInfo if a join was detected, null otherwise
     */
    public static JoinInfo parseJoinMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            return null;
        }

        String stripped = stripColorCodes(message);

        // Check for party join
        Matcher partyMatcher = PARTY_JOIN.matcher(stripped);
        if (partyMatcher.matches()) {
            String username = partyMatcher.group(1);
            return new JoinInfo(JoinType.PARTY, username, null, null);
        }

        // Check for detailed dungeon join (with class info)
        Matcher detailedMatcher = DUNGEON_JOIN_DETAILED.matcher(stripped);
        if (detailedMatcher.matches()) {
            String username = detailedMatcher.group(1);
            String dungeonClass = detailedMatcher.group(2);
            String classLevel = detailedMatcher.group(3);
            return new JoinInfo(JoinType.DUNGEON, username, dungeonClass, classLevel);
        }

        // Check for simple dungeon join (no class info)
        Matcher simpleMatcher = DUNGEON_JOIN_SIMPLE.matcher(stripped);
        if (simpleMatcher.matches()) {
            String username = simpleMatcher.group(1);
            return new JoinInfo(JoinType.DUNGEON, username, null, null);
        }

        return null;
    }

    /**
     * Strip Minecraft color codes from a message.
     *
     * @param text The text to strip
     * @return The stripped text
     */
    public static String stripColorCodes(String text) {
        if (text == null) {
            return "";
        }
        // Remove § color codes
        return text.replaceAll("§.", "");
    }

    /**
     * Type of join detected
     */
    public enum JoinType {
        PARTY,   // Regular party join
        DUNGEON  // Party Finder dungeon group join
    }

    /**
     * Information about a detected join
     */
    public static class JoinInfo {
        private final JoinType type;
        private final String username;
        private final String dungeonClass;  // Only for DUNGEON type (may be null)
        private final String classLevel;    // Only for DUNGEON type (may be null)

        public JoinInfo(JoinType type, String username, String dungeonClass, String classLevel) {
            this.type = type;
            this.username = username;
            this.dungeonClass = dungeonClass;
            this.classLevel = classLevel;
        }

        public JoinType getType() {
            return type;
        }

        public String getUsername() {
            return username;
        }

        public String getDungeonClass() {
            return dungeonClass;
        }

        public String getClassLevel() {
            return classLevel;
        }

        public int getClassLevelAsInt() {
            if (classLevel == null) {
                return 0;
            }
            try {
                return Integer.parseInt(classLevel);
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        public boolean hasClassInfo() {
            return dungeonClass != null && classLevel != null;
        }

        @Override
        public String toString() {
            if (hasClassInfo()) {
                return String.format("%s[%s] %s (%s Lvl %s)",
                    type, type, username, dungeonClass, classLevel);
            }
            return String.format("%s[%s] %s", type, type, username);
        }
    }
}
