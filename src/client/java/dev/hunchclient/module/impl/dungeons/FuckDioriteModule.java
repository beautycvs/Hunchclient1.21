package dev.hunchclient.module.impl.dungeons;

import com.google.gson.JsonObject;
import dev.hunchclient.module.ConfigurableModule;
import dev.hunchclient.module.Module;
import dev.hunchclient.module.SettingsProvider;
import dev.hunchclient.module.setting.*;
import dev.hunchclient.util.DungeonUtils;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;

/**
 * FuckDiorite - replaces diorite pillars in F7/M7 P2 with glass.
 *
 * @author Kaze.0707
 */
public class FuckDioriteModule extends Module implements ConfigurableModule, SettingsProvider {

    // Settings
    private boolean pillarBasedColor = true;
    private int colorIndex = 0; // 0 = None (white glass)
    private boolean schitzoMode = false;
    private boolean debugMode = false; // Always replace, ignore boss checks

    // Glass block states (1.21.10+ uses separate blocks for each color)
    private static final BlockState GLASS_STATE = Blocks.GLASS.defaultBlockState();
    private static final BlockState[] STAINED_GLASS_STATES = new BlockState[16];

    static {
        // Initialize stained glass states for all 16 dye colors (1.21.10+ format)
        STAINED_GLASS_STATES[0] = Blocks.WHITE_STAINED_GLASS.defaultBlockState();
        STAINED_GLASS_STATES[1] = Blocks.ORANGE_STAINED_GLASS.defaultBlockState();
        STAINED_GLASS_STATES[2] = Blocks.MAGENTA_STAINED_GLASS.defaultBlockState();
        STAINED_GLASS_STATES[3] = Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();
        STAINED_GLASS_STATES[4] = Blocks.YELLOW_STAINED_GLASS.defaultBlockState();
        STAINED_GLASS_STATES[5] = Blocks.LIME_STAINED_GLASS.defaultBlockState();
        STAINED_GLASS_STATES[6] = Blocks.PINK_STAINED_GLASS.defaultBlockState();
        STAINED_GLASS_STATES[7] = Blocks.GRAY_STAINED_GLASS.defaultBlockState();
        STAINED_GLASS_STATES[8] = Blocks.LIGHT_GRAY_STAINED_GLASS.defaultBlockState();
        STAINED_GLASS_STATES[9] = Blocks.CYAN_STAINED_GLASS.defaultBlockState();
        STAINED_GLASS_STATES[10] = Blocks.PURPLE_STAINED_GLASS.defaultBlockState();
        STAINED_GLASS_STATES[11] = Blocks.BLUE_STAINED_GLASS.defaultBlockState();
        STAINED_GLASS_STATES[12] = Blocks.BROWN_STAINED_GLASS.defaultBlockState();
        STAINED_GLASS_STATES[13] = Blocks.GREEN_STAINED_GLASS.defaultBlockState();
        STAINED_GLASS_STATES[14] = Blocks.RED_STAINED_GLASS.defaultBlockState();
        STAINED_GLASS_STATES[15] = Blocks.BLACK_STAINED_GLASS.defaultBlockState();
    }

    // F7/M7 Storm pillar positions
    private static final BlockPos[] PILLAR_CENTERS = {
        new BlockPos(46, 169, 41),   // Pillar 1 (Orange)
        new BlockPos(46, 169, 65),   // Pillar 2 (Yellow)
        new BlockPos(100, 169, 65),  // Pillar 3 (Lime)
        new BlockPos(100, 169, 41)   // Pillar 4 (Red)
    };

    // Pillar colors (matches terminal colors) - Direct indices for 1.21.10
    private static final int[] PILLAR_COLORS = {
        1,   // Orange
        4,   // Yellow
        5,   // Lime
        14   // Red
    };

    // Pre-computed block positions for each pillar (performance optimization)
    private static final Set<BlockPos>[] PILLAR_POSITIONS = new Set[4];

    static {
        // Build position sets for all 4 pillars (7x38x7 cube around center)
        for (int pillarIndex = 0; pillarIndex < 4; pillarIndex++) {
            BlockPos center = PILLAR_CENTERS[pillarIndex];
            Set<BlockPos> positions = new HashSet<>();

            for (int dx = -3; dx <= 3; dx++) {
                for (int dy = 0; dy <= 37; dy++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        positions.add(center.offset(dx, dy, dz));
                    }
                }
            }

            PILLAR_POSITIONS[pillarIndex] = positions;
        }
    }

    // State tracking
    private DungeonUtils.F7Phase lastPhase = DungeonUtils.F7Phase.UNKNOWN;
    private boolean replacedThisRun = false;
    private boolean maxorDetected = false; // Chat trigger - activates position check
    private final Random random = new Random();

    public FuckDioriteModule() {
        super("Fuck Diorite", "Replaces diorite pillars in F7/M7 P2 with glass", Category.DUNGEONS, true);
    }

    @Override
    protected void onEnable() {
        resetState();
    }

    @Override
    protected void onDisable() {
        resetState();
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        // Debug mode: Always replace, ignore all checks
        if (debugMode) {
            if (!replacedThisRun) {
                replaceDiorite(false);
                replacedThisRun = true;
            }
            return;
        }

        // Only check position if Maxor was detected (chat trigger)
        if (!maxorDetected) return;

        // P2 detection using Y-position (same logic as DungeonUtils but without scoreboard checks)
        DungeonUtils.F7Phase currentPhase = getF7PhaseByPosition(mc.player.getY());

        // Reset on phase change
        if (currentPhase != lastPhase) {
            lastPhase = currentPhase;
            if (currentPhase != DungeonUtils.F7Phase.P2) {
                replacedThisRun = false;
            }
        }

        // Only replace in Phase 2 (Storm) - position-based detection
        if (currentPhase == DungeonUtils.F7Phase.P2 && !replacedThisRun) {
            replaceDiorite(false);
            replacedThisRun = true;
        }
    }

    /**
     * Gets F7 phase based ONLY on Y position (no scoreboard checks)
     * Uses same Y-ranges as DungeonUtils.getF7Phase()
     */
    private DungeonUtils.F7Phase getF7PhaseByPosition(double y) {
        if (y > 210) {
            return DungeonUtils.F7Phase.P1;
        }
        if (y > 155) {
            return DungeonUtils.F7Phase.P2; // Storm phase (155-210)
        }
        if (y > 100) {
            return DungeonUtils.F7Phase.P3;
        }
        if (y > 45) {
            return DungeonUtils.F7Phase.P4;
        }
        if (y > 0) {
            return DungeonUtils.F7Phase.P5;
        }
        return DungeonUtils.F7Phase.UNKNOWN;
    }

    /**
     * Replaces diorite in all pillars with glass
     * @param force Force replacement (even if not stone/diorite)
     */
    private void replaceDiorite(boolean force) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        ChunkSource chunkProvider = mc.level.getChunkSource();
        if (chunkProvider == null) return;

        // Cache chunks to avoid repeated lookups (PERFORMANCE)
        Map<Long, ChunkAccess> chunkCache = new HashMap<>();

        int replacedCount = 0;

        for (int pillarIndex = 0; pillarIndex < 4; pillarIndex++) {
            Set<BlockPos> positions = PILLAR_POSITIONS[pillarIndex];

            for (BlockPos pos : positions) {
                // Get chunk (with caching)
                int chunkX = pos.getX() >> 4;
                int chunkZ = pos.getZ() >> 4;
                long chunkKey = ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);

                ChunkAccess chunk = chunkCache.get(chunkKey);
                if (chunk == null) {
                    chunk = chunkProvider.getChunkNow(chunkX, chunkZ);
                    if (chunk == null) continue;
                    chunkCache.put(chunkKey, chunk);
                }

                // Get block at position
                BlockState state = chunk.getBlockState(pos);
                Block block = state.getBlock();

                // Check if we should replace this block
                boolean shouldReplace;
                if (force) {
                    // Force mode: Replace stone, glass, and any stained glass
                    shouldReplace = (block == Blocks.STONE || block == Blocks.GLASS || isStainedGlass(block));
                } else {
                    // Normal mode: Only replace stone/diorite variants
                    shouldReplace = (block == Blocks.STONE || block == Blocks.POLISHED_DIORITE || block == Blocks.DIORITE);
                }

                if (shouldReplace) {
                    BlockState glassState = getGlassState(pillarIndex);
                    mc.level.setBlock(pos, glassState, 3);
                    replacedCount++;
                }
            }
        }

        if (replacedCount > 0) {
            sendMessage("§aReplaced §e" + replacedCount + "§a blocks with glass!");
        }
    }

    /**
     * Checks if a block is any stained glass (1.21.10+ has 16 separate blocks)
     */
    private boolean isStainedGlass(Block block) {
        return block == Blocks.WHITE_STAINED_GLASS ||
               block == Blocks.ORANGE_STAINED_GLASS ||
               block == Blocks.MAGENTA_STAINED_GLASS ||
               block == Blocks.LIGHT_BLUE_STAINED_GLASS ||
               block == Blocks.YELLOW_STAINED_GLASS ||
               block == Blocks.LIME_STAINED_GLASS ||
               block == Blocks.PINK_STAINED_GLASS ||
               block == Blocks.GRAY_STAINED_GLASS ||
               block == Blocks.LIGHT_GRAY_STAINED_GLASS ||
               block == Blocks.CYAN_STAINED_GLASS ||
               block == Blocks.PURPLE_STAINED_GLASS ||
               block == Blocks.BLUE_STAINED_GLASS ||
               block == Blocks.BROWN_STAINED_GLASS ||
               block == Blocks.GREEN_STAINED_GLASS ||
               block == Blocks.RED_STAINED_GLASS ||
               block == Blocks.BLACK_STAINED_GLASS;
    }

    /**
     * Gets the glass state for a specific pillar based on settings
     */
    private BlockState getGlassState(int pillarIndex) {
        if (schitzoMode) {
            // Random stained glass
            return STAINED_GLASS_STATES[random.nextInt(16)];
        } else if (pillarBasedColor) {
            // Pillar-specific color
            return STAINED_GLASS_STATES[PILLAR_COLORS[pillarIndex]];
        } else if (colorIndex > 0) {
            // User-selected color
            return STAINED_GLASS_STATES[colorIndex - 1];
        } else {
            // Default white glass
            return GLASS_STATE;
        }
    }

    /**
     * Force glass replacement (called by button)
     */
    private void forceGlass() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            sendMessage("§cYou must be in-game to use this!");
            return;
        }

        // Check if in F7/M7 boss or singleplayer (for testing)
        if (!DungeonUtils.isInBossFight() && !mc.isLocalServer()) {
            sendMessage("§cYou must be in F7/M7 boss room to use this!");
            return;
        }

        sendMessage("§aForcing glass replacement...");
        replaceDiorite(true);
    }

    /**
     * Called when chat message received (for P2 trigger)
     */
    public void onChatMessage(String message) {
        if (message.contains("[BOSS] Maxor: WELL WELL WELL LOOK WHO'S HERE!")) {
            // Maxor (Storm) started - activate position check
            maxorDetected = true;
            replacedThisRun = false; // Reset to allow replacement
            sendMessage("§aMaxor detected - position check activated!");
        }
    }

    private void resetState() {
        lastPhase = DungeonUtils.F7Phase.UNKNOWN;
        replacedThisRun = false;
        maxorDetected = false;
    }

    private void sendMessage(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("§6[FuckDiorite] " + message), false);
        }
    }

    // ============= Config Persistence =============

    @Override
    public JsonObject saveConfig() {
        JsonObject config = new JsonObject();
        config.addProperty("pillarBasedColor", pillarBasedColor);
        config.addProperty("colorIndex", colorIndex);
        config.addProperty("schitzoMode", schitzoMode);
        config.addProperty("debugMode", debugMode);
        return config;
    }

    @Override
    public void loadConfig(JsonObject config) {
        if (config.has("pillarBasedColor")) {
            pillarBasedColor = config.get("pillarBasedColor").getAsBoolean();
        }
        if (config.has("colorIndex")) {
            colorIndex = config.get("colorIndex").getAsInt();
        }
        if (config.has("schitzoMode")) {
            schitzoMode = config.get("schitzoMode").getAsBoolean();
        }
        if (config.has("debugMode")) {
            debugMode = config.get("debugMode").getAsBoolean();
        }
    }

    // ============= Settings Provider =============

    @Override
    public List<ModuleSetting> getSettings() {
        List<ModuleSetting> settings = new ArrayList<>();

        // Pillar Based Color
        settings.add(new CheckboxSetting(
            "Pillar Based Color",
            "Use different colors for each pillar (Orange, Yellow, Lime, Red)",
            "fuckdiorite_pillar_based",
            () -> pillarBasedColor,
            val -> pillarBasedColor = val
        ).setVisible(() -> !schitzoMode));

        // Color Selection Dropdown
        String[] colorOptions = {
            "None (White)", "White", "Orange", "Magenta", "Light Blue", "Yellow", "Lime", "Pink",
            "Gray", "Light Gray", "Cyan", "Purple", "Blue", "Brown", "Green", "Red", "Black"
        };
        settings.add(new DropdownSetting(
            "Glass Color",
            "Select glass color (None = white glass)",
            "fuckdiorite_color",
            colorOptions,
            () -> colorIndex,
            val -> colorIndex = val
        ).setVisible(() -> !pillarBasedColor && !schitzoMode));

        // Schitzo Mode
        settings.add(new CheckboxSetting(
            "Schitzo Mode",
            "Random colors for every block (seizure warning)",
            "fuckdiorite_schitzo",
            () -> schitzoMode,
            val -> schitzoMode = val
        ));

        // Debug Mode
        settings.add(new CheckboxSetting(
            "Debug Mode",
            "Always replace diorite (ignores boss/floor checks)",
            "fuckdiorite_debug",
            () -> debugMode,
            val -> {
                debugMode = val;
                if (val) {
                    sendMessage("§eDebug mode enabled - will replace diorite anywhere!");
                    replacedThisRun = false; // Force re-run
                } else {
                    sendMessage("§eDebug mode disabled");
                    resetState();
                }
            }
        ));

        // Force Glass Button
        settings.add(new ButtonSetting(
            "Force Glass",
            "Replace all pillars with glass NOW",
            "fuckdiorite_force",
            this::forceGlass
        ));

        return settings;
    }
}
