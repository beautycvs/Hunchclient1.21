package dev.hunchclient.module.impl.dungeons.devmap;

import dev.hunchclient.module.impl.dungeons.devmap.Coordinates.*;
import dev.hunchclient.module.impl.dungeons.devmap.MapEnums.*;
import dev.hunchclient.module.impl.dungeons.devmap.DevPlayer.DevClass;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
// scoreboard imports already present below
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class DevMapState {

    private static final Minecraft mc = Minecraft.getInstance();
    private static DevMapState INSTANCE;

    // Players
    private final Map<String, DevPlayer> players = new LinkedHashMap<>();
    private final Map<String, DevClass> playerClasses = new ConcurrentHashMap<>();

    // Floor info
    private FloorType floor = FloorType.NONE;

    // State flags
    private boolean inBoss = false;
    private boolean bloodCleared = false;
    private boolean dungeonStarted = false;
    private boolean mimicKilled = false;

    // Statistics
    private int clearedPercent = 0;
    private int timeElapsed = 0;
    private int deaths = 0;
    private int crypts = 0;
    private int secretsFound = 0;
    private double secretsFoundPercent = 0.0;

    // Map rendering
    private boolean needsRedraw = true;

    // Soft reset timer - like auto-toggling the module to discover new rooms
    private int tickCounter = 0;
    private static final int SOFT_RESET_INTERVAL_TICKS = 20; // Soft reset every 1 second (20 ticks)

    // Area-based reset tracking (like Devonian)
    private boolean wasInDungeon = false;

    // Map scanning parameters (like Devonian's DungeonMapScanner)
    private static final int MAP_SIZE = 128;
    private static final int ROOM_SPACING = 4;
    private int roomSize = -1;
    private int roomGap = -1;
    private int mapOffsetX = -1;
    private int mapOffsetZ = -1;

    private DevMapState() {
    }

    public static DevMapState getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new DevMapState();
        }
        return INSTANCE;
    }

    /**
     * Initialize the map state
     */
    public void init() {
        DevScanner.init();
    }

    /**
     * Reset all state
     */
    public void reset() {
        players.clear();
        playerClasses.clear();
        floor = FloorType.NONE;
        inBoss = false;
        bloodCleared = false;
        dungeonStarted = false;
        mimicKilled = false;
        clearedPercent = 0;
        timeElapsed = 0;
        deaths = 0;
        crypts = 0;
        secretsFound = 0;
        secretsFoundPercent = 0.0;
        needsRedraw = true;
        tickCounter = 0;

        // Reset map scanning parameters
        roomSize = -1;
        roomGap = -1;
        mapOffsetX = -1;
        mapOffsetZ = -1;

        DevScanner.reset();

        // Clear player head cache for new dungeon party
        dev.hunchclient.module.impl.dungeons.map.PlayerHeadCache.clear();
    }

    // Devonian regex patterns
    private static final Pattern DUNGEON_FLOOR_REGEX = Pattern.compile("^ *⏣ The Catacombs \\((\\w+)\\)$");

    /**
     * Called each tick
     */
    public void tick() {
        if (mc.player == null || mc.level == null) return;

        // Area-based reset (like Devonian) - detect dungeon entry/exit
        boolean currentlyInDungeon = dev.hunchclient.util.DungeonUtils.isInDungeon();

        if (currentlyInDungeon) {
            // Entered dungeon
            wasInDungeon = true;

            // Detect floor from scoreboard
            FloorType detectedFloor = detectFloorFromScoreboard();
            if (detectedFloor != null && detectedFloor != FloorType.NONE) {
                if (floor != detectedFloor) {
                    setFloor(detectedFloor);
                }
            }
        } else {
            // Not in dungeon - reset if we were previously in dungeon
            if (wasInDungeon) {
                System.out.println("[DevMapState] Left dungeon area, resetting...");
                reset();
                wasInDungeon = false;
                return; // Skip rest of tick after reset
            }
        }

        // Skip processing if not in dungeon
        if (!currentlyInDungeon || floor == FloorType.NONE) {
            return;
        }

        if (inBoss) return;

        // Auto-detect and update all players in the dungeon area
        // Filter out NPCs by checking UUID version (real players = version 4, NPCs = version 2)
        for (Player entity : mc.level.players()) {
            if (entity == null || entity.isRemoved()) continue;

            // Skip NPCs - they have version 2 UUIDs, real players have version 4
            if (entity.getUUID().version() != 4) continue;

            String name = entity.getName().getString();
            double x = entity.getX();
            double z = entity.getZ();

            // Check if player is in dungeon area (-200 to -10 for both X and Z)
            if (x < -200 || x > -10 || z < -200 || z > -10) continue;

            // Get or create player
            DevPlayer player = players.get(name);
            if (player == null) {
                player = new DevPlayer(name, DevPlayer.DevClass.UNKNOWN, 0, false);
                players.put(name, player);
            }

            // Update entity reference and tick
            player.setEntity(entity);
            player.tick();
        }

        // Scan dungeon - continuous scanning for new rooms
        DevScanner.scan();
        DevScanner.checkRoomRotations();
        DevScanner.checkDoorStates();

        // Periodically soft-reset to discover new rooms (like auto-toggling module)
        tickCounter++;
        if (tickCounter >= SOFT_RESET_INTERVAL_TICKS) {
            tickCounter = 0;
            DevScanner.softReset();
        }

        // Update current room
        double px = mc.player.getX();
        double pz = mc.player.getZ();
        ComponentPosition comp = new WorldPosition((int) px, (int) pz).toComponent();
        int idx = comp.getRoomIdx();

        if (idx >= 0 && idx < 36) {
            DevRoom room = DevScanner.rooms[idx];
            if (room != null) {
                room.setExplored(true);
            }

            if (DevScanner.lastIdx != idx) {
                DevScanner.lastIdx = idx;
                DevScanner.currentRoom = room;
            }
        }

        // Update from vanilla dungeon map (checkmarks AND player positions)
        updateFromVanillaMap();
    }

    /**
     * Scan map dimensions from the entrance room (like Devonian)
     * Returns true if successful
     */
    private boolean scanMapDimensions(byte[] colors) {
        if (floor == FloorType.NONE) return false;

        // Find entrance room (color 30 = green/entrance)
        int entranceIdx = 0;
        int i = 0;
        while (entranceIdx < colors.length && colors[entranceIdx] != MapColor.ROOM_ENTRANCE.color) {
            i++;
            entranceIdx = ((i & 7) << 4) + ((i >> 3) << 11);
        }

        if (entranceIdx >= colors.length) return false;

        // Find boundaries of entrance room
        int l = entranceIdx;
        int r = entranceIdx;
        while (l > 0 && colors[l - 1] == MapColor.ROOM_ENTRANCE.color) l--;
        while (r < colors.length - 1 && colors[r + 1] == MapColor.ROOM_ENTRANCE.color) r++;

        int t = entranceIdx;
        int b = entranceIdx;
        while (t >= MAP_SIZE && colors[t - MAP_SIZE] == MapColor.ROOM_ENTRANCE.color) t -= MAP_SIZE;
        while (b < colors.length - MAP_SIZE && colors[b + MAP_SIZE] == MapColor.ROOM_ENTRANCE.color) b += MAP_SIZE;

        l = l & 127;
        r = r & 127;
        t = t >> 7;
        b = b >> 7;

        roomSize = r - l + 1;
        roomGap = roomSize + ROOM_SPACING;

        mapOffsetX = l % roomGap;
        mapOffsetZ = t % roomGap;

        int mapWidth = roomGap * (floor.roomsW - 1) + roomSize;
        int mapHeight = roomGap * (floor.roomsH - 1) + roomSize;

        if (MAP_SIZE - mapWidth >= roomGap * 2) mapOffsetX += roomGap;
        if (MAP_SIZE - mapHeight >= roomGap * 2) mapOffsetZ += roomGap;

        return true;
    }

    /**
     * Update player positions from map decorations (icons on the vanilla map)
     * This works for ALL players in the dungeon, even if they're far away!
     */
    private void updatePlayerIconsFromMap(MapItemSavedData mapState) {
        if (players.isEmpty()) return;
        if (floor == FloorType.NONE) return;

        // Get map decorations (player icons)
        Iterable<MapDecoration> decorations = mapState.getDecorations();
        if (decorations == null) return;

        // Count living players
        long livingPlayers = players.values().stream().filter(p -> !p.isDead()).count();

        // Convert to list for iteration
        List<MapDecoration> decList = new ArrayList<>();
        for (MapDecoration dec : decorations) {
            // Skip frame decorations (item frames etc)
            if (dec.type().value() == MapDecorationTypes.FRAME.value()) continue;
            decList.add(dec);
        }

        // If decoration count doesn't match living players, skip (data not ready)
        if (decList.size() != livingPlayers) return;

        // Iterate through players and decorations together
        Iterator<DevPlayer> playerIter = players.values().iterator();
        if (!playerIter.hasNext()) return;
        playerIter.next(); // Skip first player (self - we track via entity)

        for (MapDecoration dec : decList) {
            if (!playerIter.hasNext()) break;
            DevPlayer player = playerIter.next();

            // Convert map coordinates to component coordinates
            // Map coords range from -128 to 127, we rescale to component space
            // Note: In Yarn mappings, MapDecoration uses x() and z() (not y()!)
            double x = rescale(
                (dec.x() + 128.0) * 0.5,
                mapOffsetX, mapOffsetX + roomGap * floor.roomsW,
                0.0, floor.roomsW * 2.0
            );
            double z = rescale(
                (dec.y() + 128.0) * 0.5,
                mapOffsetZ, mapOffsetZ + roomGap * floor.roomsH,
                0.0, floor.roomsH * 2.0
            );
            double r = -(dec.rot() / 16.0 * 360.0 + 90.0) / 180.0 * Math.PI;

            player.updatePosition(new PlayerComponentPosition(x, z, r));
        }
    }

    /**
     * Rescale a value from one range to another
     */
    private double rescale(double value, double inMin, double inMax, double outMin, double outMax) {
        return outMin + (value - inMin) * (outMax - outMin) / (inMax - inMin);
    }

    /**
     * Update rooms and checkmarks from vanilla map (like Devonian)
     * Key fix: Compare roomCol with centerCol to determine if checkmark is visible
     */
    private void updateRoomsFromMap(byte[] colors) {
        if (colors.length < MAP_SIZE * MAP_SIZE) return;

        Set<DevRoom> visited = new HashSet<>();

        for (int idx = 0; idx < DevScanner.rooms.length; idx++) {
            DevRoom room = DevScanner.rooms[idx];
            if (room != null && !visited.add(room)) continue;

            int x = idx % 6;
            int z = idx / 6;

            // Calculate map coordinates for room corner
            int mrx = mapOffsetX + x * roomGap;
            int mrz = mapOffsetZ + z * roomGap;

            // Calculate map coordinates for room center (with +2 offset like Devonian)
            int mcx = mrx + roomSize / 2 - 1;
            int mcz = mrz + roomSize / 2 - 1 + 2; // +2 is crucial for checkmark detection!

            int mridx = mrx + mrz * MAP_SIZE;
            int mcidx = mcx + mcz * MAP_SIZE;

            if (mridx < 0 || mridx >= colors.length) continue;
            if (mcidx < 0 || mcidx >= colors.length) continue;

            byte roomCol = colors[mridx];
            byte centerCol = colors[mcidx];

            if (roomCol == MapColor.EMPTY.color) continue;

            // Create room if not exists
            if (room == null) {
                ComponentPosition comp = new ComponentPosition(x * 2, z * 2);
                room = new DevRoom(new ArrayList<>(List.of(comp.withWorld())), 0);
                DevScanner.addRoom(comp, room);
            }

            // Update room type from map color if unknown
            if (room.getType() == RoomType.UNKNOWN) {
                room.setType(getRoomTypeFromColor(roomCol));
            }

            // Mark as explored if not unopened
            if (!room.isExplored() && roomCol != MapColor.ROOM_UNOPENED.color) {
                room.setExplored(true);
            }

            // KEY FIX: Only update checkmark if not already GREEN
            // Compare roomCol with centerCol - if same, no checkmark visible!
            if (room.getCheckmark() != CheckmarkType.GREEN) {
                CheckmarkType newCheckmark;
                if (roomCol == centerCol) {
                    // Same color = no checkmark visible
                    newCheckmark = CheckmarkType.NONE;
                } else {
                    // Different color = checkmark is the center color
                    newCheckmark = switch (centerCol) {
                        case 34 -> CheckmarkType.WHITE;   // CHECK_WHITE
                        case 30 -> CheckmarkType.GREEN;   // CHECK_GREEN
                        case 18 -> CheckmarkType.FAILED;  // CHECK_FAIL
                        case 119 -> CheckmarkType.UNEXPLORED; // CHECK_UNKNOWN
                        default -> CheckmarkType.NONE;
                    };
                }

                if (newCheckmark != room.getCheckmark()) {
                    room.setCheckmark(newCheckmark);
                    invalidate();
                }
            }
        }
    }

    /**
     * Get room type from map color
     */
    private RoomType getRoomTypeFromColor(byte color) {
        return switch (color) {
            case 30 -> RoomType.ENTRANCE;  // ROOM_ENTRANCE
            case 18 -> RoomType.BLOOD;     // ROOM_BLOOD
            case 85 -> RoomType.NORMAL;    // ROOM_UNOPENED
            case 74 -> RoomType.YELLOW;    // ROOM_BOSS
            case 82 -> RoomType.FAIRY;     // ROOM_FAIRY
            case 63 -> RoomType.NORMAL;    // ROOM_NORMAL
            case 66 -> RoomType.PUZZLE;    // ROOM_PUZZLE
            case 62 -> RoomType.TRAP;      // ROOM_TRAP
            default -> RoomType.UNKNOWN;
        };
    }

    /**
     * Main method to update everything from vanilla dungeon map
     */
    private void updateFromVanillaMap() {
        if (mc.player == null || mc.level == null) return;

        // Get map from hotbar slot 8
        ItemStack mapStack = mc.player.getInventory().getItem(8);
        if (mapStack.isEmpty() || !mapStack.is(net.minecraft.world.item.Items.FILLED_MAP)) {
            return;
        }

        MapId mapId = mapStack.get(DataComponents.MAP_ID);
        if (mapId == null) return;

        MapItemSavedData mapState = MapItem.getSavedData(mapId, mc.level);
        if (mapState == null || mapState.colors == null) return;

        byte[] colors = mapState.colors;

        // Scan map dimensions if not done yet
        if (roomSize == -1 && !scanMapDimensions(colors)) {
            return;
        }

        // Update player positions from map decorations (teammates!)
        updatePlayerIconsFromMap(mapState);

        // Update rooms and checkmarks
        updateRoomsFromMap(colors);
    }

    /**
     * Detect floor from scoreboard like Devonian does
     */
    @Nullable
    private FloorType detectFloorFromScoreboard() {
        if (mc.level == null) return null;

        Scoreboard scoreboard = mc.level.getScoreboard();
        if (scoreboard == null) return null;

        Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (sidebar == null) return null;

        // Get all scores for the sidebar
        for (ScoreHolder holder : scoreboard.getTrackedPlayers()) {
            ReadOnlyScoreInfo score = scoreboard.getPlayerScoreInfo(holder, sidebar);
            if (score == null) continue;

            // Get the display name/team prefix
            PlayerTeam team = scoreboard.getPlayersTeam(holder.getScoreboardName());
            if (team == null) continue;

            String line = team.getPlayerPrefix().getString() + team.getPlayerSuffix().getString();
            line = cleanScoreboardLine(line);

            Matcher matcher = DUNGEON_FLOOR_REGEX.matcher(line);
            if (matcher.find()) {
                String floorName = matcher.group(1);
                return FloorType.fromName(floorName);
            }
        }

        return null;
    }

    /**
     * Clean scoreboard line (remove color codes and special chars)
     */
    private String cleanScoreboardLine(String line) {
        // Remove color codes (§x)
        return line.replaceAll("§[0-9a-fk-or]", "");
    }

    /**
     * Update player from tab list
     */
    public void updatePlayer(String name, DevClass role, boolean dead) {
        DevPlayer player = players.get(name);
        if (player != null) {
            player.setRole(role);
            player.setDead(dead);
        } else {
            player = new DevPlayer(name, role, 0, dead);
            players.put(name, player);
        }
        playerClasses.put(name, role);
    }

    /**
     * Update player position from map decorations
     */
    public void updatePlayerPosition(String name, double x, double z, double rotation) {
        DevPlayer player = players.get(name);
        if (player != null) {
            player.updatePosition(new PlayerComponentPosition(x, z, rotation));
        }
    }

    /**
     * Mark the map for redraw
     */
    public void invalidate() {
        needsRedraw = true;
    }

    /**
     * Check if redraw is needed and reset flag
     */
    public boolean checkAndResetRedraw() {
        boolean result = needsRedraw;
        needsRedraw = false;
        return result;
    }

    // Getters and setters

    public Map<String, DevPlayer> getPlayers() {
        return players;
    }

    public FloorType getFloor() {
        return floor;
    }

    public void setFloor(FloorType newFloor) {
        // Reset scanner when entering a new dungeon (from NONE or different floor)
        if (newFloor != FloorType.NONE && newFloor != this.floor) {
            DevScanner.reset();
            players.clear();
            playerClasses.clear();
            inBoss = false;
            bloodCleared = false;
            dungeonStarted = false;
            mimicKilled = false;
            clearedPercent = 0;
            timeElapsed = 0;
            deaths = 0;
            crypts = 0;
            secretsFound = 0;
            secretsFoundPercent = 0.0;
        }
        this.floor = newFloor;
        invalidate();
    }

    public boolean isInBoss() {
        return inBoss;
    }

    public void setInBoss(boolean inBoss) {
        this.inBoss = inBoss;
    }

    public boolean isBloodCleared() {
        return bloodCleared;
    }

    public void setBloodCleared(boolean bloodCleared) {
        this.bloodCleared = bloodCleared;
    }

    public boolean isDungeonStarted() {
        return dungeonStarted;
    }

    public void setDungeonStarted(boolean dungeonStarted) {
        this.dungeonStarted = dungeonStarted;
        invalidate();
    }

    public boolean isMimicKilled() {
        return mimicKilled;
    }

    public void setMimicKilled(boolean mimicKilled) {
        this.mimicKilled = mimicKilled;
    }

    public int getClearedPercent() {
        return clearedPercent;
    }

    public void setClearedPercent(int clearedPercent) {
        this.clearedPercent = clearedPercent;
    }

    public int getTimeElapsed() {
        return timeElapsed;
    }

    public void setTimeElapsed(int timeElapsed) {
        this.timeElapsed = timeElapsed;
    }

    public int getDeaths() {
        return deaths;
    }

    public void setDeaths(int deaths) {
        this.deaths = deaths;
    }

    public int getCrypts() {
        return crypts;
    }

    public void setCrypts(int crypts) {
        this.crypts = crypts;
    }

    public int getSecretsFound() {
        return secretsFound;
    }

    public void setSecretsFound(int secretsFound) {
        this.secretsFound = secretsFound;
    }

    public double getSecretsFoundPercent() {
        return secretsFoundPercent;
    }

    public void setSecretsFoundPercent(double secretsFoundPercent) {
        this.secretsFoundPercent = secretsFoundPercent;
    }

    @Nullable
    public DevRoom getCurrentRoom() {
        return DevScanner.currentRoom;
    }

    public DevRoom[] getRooms() {
        return DevScanner.rooms;
    }

    public DevDoor[] getDoors() {
        return DevScanner.doors;
    }
}
