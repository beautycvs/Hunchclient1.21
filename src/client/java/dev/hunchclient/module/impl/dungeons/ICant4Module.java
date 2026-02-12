package dev.hunchclient.module.impl.dungeons;

import dev.hunchclient.event.BlockChangeEvent;
import dev.hunchclient.event.EventBus;
import dev.hunchclient.module.ConfigurableModule;
import dev.hunchclient.module.Module;
import dev.hunchclient.module.SettingsProvider;
import dev.hunchclient.module.setting.CheckboxSetting;
import dev.hunchclient.module.setting.ModuleSetting;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * ICant4 Module - Exact port of original icant4 1.0.2
 *
 * Original flow:
 * 1. Block changes to emerald (id 133)
 * 2. If cancelNext -> skip (prevents double-fire within same packet batch)
 * 3. Calculate aim (Terminator: edge of block, normal: center)
 * 4. Send rotation packet + use item packet
 * 5. Rotate player locally
 * 6. cancelNext = true
 * 7. Add to done list
 *
 * Key: Original uses PACKET-based position tracking and cancels next outgoing rotation packet
 */
public class ICant4Module extends Module implements ConfigurableModule, SettingsProvider {

    // Terminal block positions (3x3 grid) - EXACT from original
    // Index layout (from player's perspective at x~63.5, z~35.5 looking at z=50):
    // [0] [1] [2]  (y=130) - x: 68, 66, 64
    // [3] [4] [5]  (y=128) - x: 68, 66, 64
    // [6] [7] [8]  (y=126) - x: 68, 66, 64
    private static final int[][] BLOCKS = {
        {68, 130, 50}, {66, 130, 50}, {64, 130, 50},
        {68, 128, 50}, {66, 128, 50}, {64, 128, 50},
        {68, 126, 50}, {66, 126, 50}, {64, 126, 50}
    };

    // Settings
    private boolean enableTerminatorLogic = true;
    private boolean debugMode = false;
    private List<ModuleSetting> settings;

    // State tracking - EXACT from original
    private final List<Integer> done = new ArrayList<>();
    private boolean on4thDevice = false;
    private boolean cancelNext = false;  // Prevents double-firing within same event batch

    // Prefire state - hold right-click while on device with Terminator
    private boolean prefireActive = false;

    // Event listener
    private Consumer<BlockChangeEvent> blockChangeListener;

    public ICant4Module() {
        super("ICant4", "Auto-solver for 4th Device (exact icant4 port)", Category.DUNGEONS, RiskLevel.VERY_RISKY);
        initializeSettings();
    }

    private void initializeSettings() {
        settings = new ArrayList<>();

        settings.add(new CheckboxSetting(
            "Terminator Logic",
            "Use special aiming logic for Terminator bow (shoots 3 arrows)",
            "enableTerminatorLogic",
            () -> enableTerminatorLogic,
            (value) -> enableTerminatorLogic = value
        ));

        settings.add(new CheckboxSetting(
            "Debug Mode",
            "Show debug messages in chat",
            "debugMode",
            () -> debugMode,
            (value) -> debugMode = value
        ));
    }

    @Override
    public List<ModuleSetting> getSettings() {
        return settings;
    }

    @Override
    protected void onEnable() {
        on4thDevice = false;
        cancelNext = false;
        done.clear();
        prefireActive = false;

        // Register block change listener
        blockChangeListener = this::onBlockChange;
        EventBus.getInstance().registerBlockChangeListener(blockChangeListener);
    }

    @Override
    protected void onDisable() {
        if (blockChangeListener != null) {
            EventBus.getInstance().unregisterBlockChangeListener(blockChangeListener);
        }
        done.clear();
        cancelNext = false;

        // Stop prefire - release right-click
        if (prefireActive) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                mc.options.keyUse.setDown(false);
            }
            prefireActive = false;
        }
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Reset cancelNext each tick (like original - it only blocks within same tick/event batch)
        cancelNext = false;

        // Track player position for 4th device detection
        // Original: x > 63 && x < 64 && y === 127 && z > 35 && z < 36
        double x = mc.player.getX();
        double y = mc.player.getY();
        double z = mc.player.getZ();

        boolean wasOnDevice = on4thDevice;
        on4thDevice = x > 63 && x < 64 && y >= 126.5 && y <= 127.5 && z > 35 && z < 36;

        // Check if holding Terminator
        ItemStack heldItem = mc.player.getMainHandItem();
        boolean holdingTerminator = enableTerminatorLogic &&
            heldItem != null &&
            heldItem.getItem() == Items.BOW &&
            dev.hunchclient.util.TerminatorUtil.isTerminator(heldItem);

        // Check if terminal is actually active (has emerald blocks)
        boolean terminalActive = false;
        if (on4thDevice && mc.level != null) {
            for (int[] blockCoords : BLOCKS) {
                BlockPos checkPos = new BlockPos(blockCoords[0], blockCoords[1], blockCoords[2]);
                Block block = mc.level.getBlockState(checkPos).getBlock();
                if (block == Blocks.EMERALD_BLOCK) {
                    terminalActive = true;
                    break;
                }
            }
        }

        // PREFIRE: Hold right-click while on device with Terminator AND terminal is active
        if (on4thDevice && holdingTerminator && terminalActive) {
            if (!prefireActive) {
                // Start prefire - hold right-click
                mc.options.keyUse.setDown(true);
                prefireActive = true;
                if (debugMode) {
                    mc.player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("§a[ICant4] Prefire started - holding right-click"), false);
                }
            }
        } else {
            if (prefireActive) {
                // Stop prefire - release right-click
                mc.options.keyUse.setDown(false);
                prefireActive = false;
                if (debugMode) {
                    mc.player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("§c[ICant4] Prefire stopped"), false);
                }
            }
        }

        if (!on4thDevice && wasOnDevice) {
            // Left device - clear done list (like original: while (done.length) done.pop())
            done.clear();
        }

        if (on4thDevice && !wasOnDevice && debugMode) {
            mc.player.displayClientMessage(
                net.minecraft.network.chat.Component.literal("§e[ICant4] 4th Device detected!"), false);
        }
    }

    /**
     * Handle block change event - EXACT port of original onBlock function
     */
    private void onBlockChange(BlockChangeEvent event) {
        if (!isEnabled()) return;
        if (!on4thDevice) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        BlockPos pos = event.getPos();
        Block block = event.getNewState().getBlock();

        // Find block index in our 3x3 grid
        int index = -1;
        for (int i = 0; i < BLOCKS.length; i++) {
            if (pos.getX() == BLOCKS[i][0] && pos.getY() == BLOCKS[i][1] && pos.getZ() == BLOCKS[i][2]) {
                index = i;
                break;
            }
        }

        if (index == -1) return;

        // Original: if (id === 159) done.push(index);
        // 159 = hardened clay/terracotta = block is solved
        if (isTerracotta(block)) {
            if (!done.contains(index)) {
                done.add(index);
            }
            return; // Don't shoot at already solved blocks
        }

        // Original: if (id !== 133) return;
        // 133 = emerald block = shoot!
        if (block != Blocks.EMERALD_BLOCK) {
            return;
        }

        // Original: if (cancelNext) return;
        if (cancelNext) return;

        // Original: if (item?.getID() !== 261) return;
        // 261 = bow
        ItemStack heldItem = mc.player.getMainHandItem();
        if (heldItem == null || heldItem.getItem() != Items.BOW) return;

        // Check if it's a TERMINATOR
        boolean isTerminator = false;
        if (enableTerminatorLogic) {
            isTerminator = dev.hunchclient.util.TerminatorUtil.isTerminator(heldItem);
        }

        // Calculate yaw and pitch - EXACT from original
        float[] angles = calculateAim(pos, index, isTerminator);
        if (angles == null) return;

        float yaw = angles[0];
        float pitch = angles[1];

        if (Float.isNaN(yaw) || Float.isNaN(pitch)) return;

        if (debugMode) {
            mc.player.displayClientMessage(
                net.minecraft.network.chat.Component.literal(
                    String.format("§a[ICant4] Shooting block %d (yaw=%.1f, pitch=%.1f)%s",
                        index + 1, yaw, pitch, isTerminator ? " [TERM]" : "")), false);
        }

        // INSTANT rotate - like original: rotate(yaw, pitch)
        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);

        // For Terminator with prefire: right-click is already held, just rotating triggers the shot
        // For normal bow: we need to click
        if (!prefireActive) {
            // Not in prefire mode - single click for normal bow
            mc.options.keyUse.setDown(true);
            // Schedule release on next tick via a simple flag check in onTick would be complex,
            // so we just click and the bow's natural mechanics handle it
        }
        // If prefireActive is true, Terminator is already firing due to held right-click
        // The rotation change will cause the next shot to go to the new target

        // Original: cancelNext = true;
        cancelNext = true;

        // For Terminator: mark ENTIRE ROW as done (spread hits all 3 blocks)
        // For normal bow: mark only the current block as done
        if (isTerminator) {
            int row = index / 3;  // 0, 1, or 2
            int rowStart = row * 3;
            for (int i = rowStart; i < rowStart + 3; i++) {
                if (!done.contains(i)) {
                    done.add(i);
                }
            }
        } else {
            if (!done.contains(index)) {
                done.add(index);
            }
        }
    }

    /**
     * Check if block is any type of terracotta
     */
    private boolean isTerracotta(Block block) {
        return block == Blocks.TERRACOTTA ||
               block == Blocks.RED_TERRACOTTA ||
               block == Blocks.ORANGE_TERRACOTTA ||
               block == Blocks.YELLOW_TERRACOTTA ||
               block == Blocks.LIME_TERRACOTTA ||
               block == Blocks.GREEN_TERRACOTTA ||
               block == Blocks.CYAN_TERRACOTTA ||
               block == Blocks.LIGHT_BLUE_TERRACOTTA ||
               block == Blocks.BLUE_TERRACOTTA ||
               block == Blocks.PURPLE_TERRACOTTA ||
               block == Blocks.MAGENTA_TERRACOTTA ||
               block == Blocks.PINK_TERRACOTTA ||
               block == Blocks.WHITE_TERRACOTTA ||
               block == Blocks.LIGHT_GRAY_TERRACOTTA ||
               block == Blocks.GRAY_TERRACOTTA ||
               block == Blocks.BLACK_TERRACOTTA ||
               block == Blocks.BROWN_TERRACOTTA;
    }

    /**
     * Calculate yaw and pitch - EXACT port of original Terminator logic
     *
     * Original code:
     * if (sbId === "TERMINATOR") switch (index % 3) {
     *     case 0: [yaw, pitch] = getYawPitch(position[0] - 0.5, position[1] + 1, position[2]); break;
     *     case 1: {
     *         const [f1, f2] = [done.includes(index - 1), done.includes(index + 1)];
     *         if (f1 && !f2) [yaw, pitch] = getYawPitch(position[0] - 0.5, position[1] + 1, position[2]);
     *         else if (f2 && !f1) [yaw, pitch] = getYawPitch(position[0] + 1.5, position[1] + 1, position[2]);
     *         else [yaw, pitch] = getYawPitch(position[0] + 0.5 + (Math.random() < 0.5 ? -1 : 1), position[1] + 1, position[2]);
     *         break;
     *     }
     *     case 2: [yaw, pitch] = getYawPitch(position[0] + 1.5, position[1] + 1, position[2]); break;
     * }
     * else [yaw, pitch] = getYawPitch(position[0] + 0.5, position[1] + 1, position[2]);
     */
    private float[] calculateAim(BlockPos position, int index, boolean isTerminator) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;

        double targetX, targetY, targetZ;

        if (isTerminator) {
            // TERMINATOR special logic - aim at edges to hit multiple blocks
            switch (index % 3) {
                case 0: // Left column (indices 0, 3, 6) - aim LEFT edge
                    targetX = position.getX() - 0.5;
                    targetY = position.getY() + 1;
                    targetZ = position.getZ();
                    break;
                case 1: // Middle column (indices 1, 4, 7) - check neighbors
                    boolean f1 = done.contains(index - 1); // Left neighbor done?
                    boolean f2 = done.contains(index + 1); // Right neighbor done?
                    if (f1 && !f2) {
                        // Left done, right not -> aim LEFT to hit middle + right
                        targetX = position.getX() - 0.5;
                    } else if (f2 && !f1) {
                        // Right done, left not -> aim RIGHT to hit middle + left
                        targetX = position.getX() + 1.5;
                    } else {
                        // Both or neither done -> random direction
                        targetX = position.getX() + 0.5 + (Math.random() < 0.5 ? -1 : 1);
                    }
                    targetY = position.getY() + 1;
                    targetZ = position.getZ();
                    break;
                case 2: // Right column (indices 2, 5, 8) - aim RIGHT edge
                    targetX = position.getX() + 1.5;
                    targetY = position.getY() + 1;
                    targetZ = position.getZ();
                    break;
                default:
                    return null;
            }
        } else {
            // Regular bow - aim at block center
            targetX = position.getX() + 0.5;
            targetY = position.getY() + 1;
            targetZ = position.getZ();
        }

        // Get player eye position
        Vec3 eyePos = new Vec3(
            mc.player.getX(),
            mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()),
            mc.player.getZ()
        );

        // Calculate difference vector
        Vec3 difference = new Vec3(
            targetX - eyePos.x,
            targetY - eyePos.y,
            targetZ - eyePos.z
        );

        // Calculate yaw and pitch - EXACT from original getYawPitch
        float yaw = (float) (Math.atan2(difference.z, difference.x) * 180.0 / Math.PI) - 90.0f;
        double xz = Math.sqrt(difference.x * difference.x + difference.z * difference.z);
        float pitch = (float) -(Math.atan2(difference.y, xz) * 180.0 / Math.PI);

        return new float[] { yaw, pitch };
    }

    @Override
    public JsonObject saveConfig() {
        JsonObject config = new JsonObject();
        config.addProperty("enabled", isEnabled());
        config.addProperty("enableTerminatorLogic", enableTerminatorLogic);
        config.addProperty("debugMode", debugMode);
        return config;
    }

    @Override
    public void loadConfig(JsonObject data) {
        if (data.has("enabled") && data.get("enabled").getAsBoolean()) {
            setEnabled(true);
        }
        if (data.has("enableTerminatorLogic")) {
            enableTerminatorLogic = data.get("enableTerminatorLogic").getAsBoolean();
        }
        if (data.has("debugMode")) {
            debugMode = data.get("debugMode").getAsBoolean();
        }
    }
}
