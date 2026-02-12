package dev.hunchclient.module.impl.dungeons;

import dev.hunchclient.util.DungeonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Manages dungeon player information for the fancy map
 * Simplified version of Skyblocker's DungeonPlayerManager
 */
public class DungeonPlayerManager {

    /**
     * Match a player entry from tab list
     * Example: "[100] [MVP+] PlayerName (Healer LXX)"
     * Groups: name, class, level
     */
    private static final Pattern PLAYER_TAB_PATTERN = Pattern.compile("\\[\\d+] (?:\\[[A-Za-z]+] )?(?<name>[A-Za-z0-9_]+) (?:.+ )?\\((?<class>\\S+) ?(?<level>[LXVI0]+)?\\)");

    /**
     * Array of 5 dungeon players (maintains order from tab list)
     */
    private static final DungeonPlayer[] players = new DungeonPlayer[5];

    /**
     * Update players from tab list
     * Call this periodically (e.g., every tick)
     */
    public static void updatePlayers() {
        if (!DungeonUtils.isInDungeon()) {
            reset();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        ClientPacketListener networkHandler = mc.player.connection;
        if (networkHandler == null) return;

        Collection<PlayerInfo> playerList = networkHandler.getOnlinePlayers();
        if (playerList.isEmpty()) return;

        // Convert to array for indexed access
        PlayerInfo[] entries = playerList.toArray(new PlayerInfo[0]);

        // Parse each of the 5 dungeon player slots
        for (int i = 0; i < 5; i++) {
            Matcher matcher = getPlayerFromTab(i + 1, entries);

            if (matcher == null) {
                players[i] = null;
                continue;
            }

            String name = matcher.group("name");
            DungeonClass dungeonClass = DungeonClass.from(matcher.group("class"));

            // Update existing player or create new one
            if (players[i] != null && players[i].getName().equals(name)) {
                players[i].update(dungeonClass);
            } else {
                players[i] = new DungeonPlayer(name, dungeonClass);
            }
        }
    }

    /**
     * Get player from tab list at index (1-5)
     */
    @Nullable
    private static Matcher getPlayerFromTab(int index, PlayerInfo[] entries) {
        // Tab list layout (typically):
        // Row 0: Server info
        // Row 1-5: Player entries (indices 1, 5, 9, 13, 17)
        int tabIndex = 1 + (index - 1) * 4;

        if (tabIndex >= entries.length) {
            return null;
        }

        PlayerInfo entry = entries[tabIndex];
        if (entry == null || entry.getTabListDisplayName() == null) {
            return null;
        }

        String text = entry.getTabListDisplayName().getString();
        Matcher matcher = PLAYER_TAB_PATTERN.matcher(text);

        return matcher.find() ? matcher : null;
    }

    /**
     * Get all dungeon players
     */
    @NotNull
    public static DungeonPlayer[] getPlayers() {
        return players;
    }

    /**
     * Reset player data (called when leaving dungeon)
     */
    public static void reset() {
        Arrays.fill(players, null);
    }

    /**
     * Represents a dungeon player
     */
    public static class DungeonPlayer {
        @Nullable
        private UUID uuid;
        @NotNull
        private final String name;
        @NotNull
        private DungeonClass dungeonClass = DungeonClass.UNKNOWN;
        private boolean alive = true;

        public DungeonPlayer(@NotNull String name, @NotNull DungeonClass dungeonClass) {
            this.name = name;
            this.uuid = findPlayerUuid(name);
            update(dungeonClass);
        }

        /**
         * Find player UUID from world entities
         */
        @Nullable
        private static UUID findPlayerUuid(@NotNull String name) {
            Minecraft mc = Minecraft.getInstance();
            Level world = mc.level;
            if (world == null) return null;

            // Use getPlayers() instead of getEntities() in 1.21+
            for (Player player : world.players()) {
                if (player.getGameProfile().name().equals(name)) {
                    return player.getUUID();
                }
            }
            return null;
        }

        /**
         * Update player class and alive status
         */
        private void update(DungeonClass dungeonClass) {
            this.dungeonClass = dungeonClass;
            this.alive = dungeonClass != DungeonClass.UNKNOWN;
        }

        @Nullable
        public UUID getUuid() {
            if (uuid == null) {
                uuid = findPlayerUuid(name); // Try to find UUID again
            }
            return uuid;
        }

        @NotNull
        public String getName() {
            return name;
        }

        @NotNull
        public DungeonClass getDungeonClass() {
            return dungeonClass;
        }

        public boolean isAlive() {
            return alive;
        }

        @Override
        public String toString() {
            return "DungeonPlayer{name='" + name + "', class=" + dungeonClass + ", alive=" + alive + ", uuid=" + uuid + "}";
        }
    }
}
