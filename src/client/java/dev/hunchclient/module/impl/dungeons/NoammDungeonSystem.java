package dev.hunchclient.module.impl.dungeons;

import dev.hunchclient.module.impl.dungeons.map.*;
import dev.hunchclient.module.impl.dungeons.parser.MapColorParser;
import dev.hunchclient.module.impl.dungeons.scanner.PhysicalDungeonScanner;
import dev.hunchclient.util.DungeonUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main coordinator for the noamm-style dungeon map system
 * This class ties together:
 * - Physical World Scanner (finds rooms by scanning chunks)
 * - Map Color Parser (reads map colors for state updates)
 * - Map Updater (syncs physical and map data)
 * - Global State (11x11 dungeon grid)
 *
 * Call update() every tick (or every 250ms) while in dungeon
 */
public class NoammDungeonSystem {
    private static final Logger LOGGER = LoggerFactory.getLogger(NoammDungeonSystem.class);

    // Components
    private final PhysicalDungeonScanner physicalScanner;
    private final MapColorParser mapColorParser;
    private final DungeonMapUpdater mapUpdater;
    private final DungeonState dungeonState;

    // Initialization state
    private boolean initialized = false;
    private boolean roomDataLoaded = false;

    public NoammDungeonSystem() {
        this.physicalScanner = new PhysicalDungeonScanner();
        this.mapColorParser = new MapColorParser();
        this.mapUpdater = new DungeonMapUpdater(mapColorParser);
        this.dungeonState = new DungeonState();
    }

    /**
     * Initialize the system (call once per dungeon)
     * Loads room database and calibrates map parser
     */
    public void initialize(int mapRoomSize) {
        if (initialized) {
            return;
        }

        LOGGER.info("[NoammDungeonSystem] Initializing (mapRoomSize: {})...", mapRoomSize);

        // Load room database if not already loaded
        if (!roomDataLoaded) {
            LOGGER.info("[NoammDungeonSystem] Starting room database load...");
            RoomDataLoader.loadRoomDataAsync().thenRun(() -> {
                roomDataLoaded = true;
                int roomCount = dev.hunchclient.module.impl.dungeons.scanner.ScanUtils.getRoomList().size();
                LOGGER.info("[NoammDungeonSystem] ✓ Room database loaded! {} rooms available", roomCount);
            });
        }

        // Calibrate map parser
        mapColorParser.calibrate(mapRoomSize);

        initialized = true;
        LOGGER.info("[NoammDungeonSystem] Initialization complete!");
    }

    /**
     * Update the dungeon state (call every tick or every 250ms)
     * @param mapState Current map state from hotbar
     */
    public void update(@Nullable MapItemSavedData mapState) {
        if (!initialized) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }

        // Don't update in boss
        if (DungeonUtils.isInBossFight()) {
            return;
        }

        // IMPORTANT: Only scan if room data is loaded!
        // Otherwise we can't match rooms to their data
        if (!roomDataLoaded) {
            return;
        }

        // Step 1: Physical world scan (only if needed)
        // DISABLED: Scanner doesn't work and causes FPS drops
        /*
        if (physicalScanner.shouldScan()) {
            Tile[] dungeonList = dungeonState.getDungeonList();
            physicalScanner.scan(dungeonList);
        }
        */

        // Step 2: Map color parsing and state sync
        if (mapState != null) {
            mapUpdater.updateRooms(mapState, dungeonState.getDungeonList());
        }

        // Step 3: Count rooms
        updateRoomCount();
    }

    /**
     * Update room count
     */
    private void updateRoomCount() {
        int roomCount = 0;
        for (Tile tile : dungeonState.getDungeonList()) {
            if (tile instanceof Room) {
                Room room = (Room) tile;
                if (!room.isSeparator() && !room.getData().getName().equals("Unknown")) {
                    roomCount++;
                }
            }
        }
        dungeonState.setRoomCount(roomCount);
    }

    /**
     * Get a room at grid position (0-10, 0-10)
     */
    @Nullable
    public Room getRoomAt(int gridX, int gridZ) {
        Tile tile = dungeonState.getTile(gridX, gridZ);
        return (tile instanceof Room) ? (Room) tile : null;
    }

    /**
     * Get current dungeon state
     */
    public DungeonState getDungeonState() {
        return dungeonState;
    }

    /**
     * Get physical scanner
     */
    public PhysicalDungeonScanner getPhysicalScanner() {
        return physicalScanner;
    }

    /**
     * Get map color parser
     */
    public MapColorParser getMapColorParser() {
        return mapColorParser;
    }

    /**
     * Check if system is initialized
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Check if room data is loaded
     */
    public boolean isRoomDataLoaded() {
        return roomDataLoaded;
    }

    /**
     * Reset the system (call on dungeon exit)
     */
    public void reset() {
        LOGGER.info("[NoammDungeonSystem] Resetting...");
        initialized = false;
        dungeonState.reset();
        physicalScanner.reset();
        mapColorParser.reset();
    }

    /**
     * Print debug information
     */
    public void printDebug() {
        dungeonState.printDebug();
    }
}
