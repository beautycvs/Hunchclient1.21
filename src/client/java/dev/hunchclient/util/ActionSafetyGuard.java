package dev.hunchclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * ActionSafetyGuard - PREVENTS WATCHDOG BANS!
 *
 * Tracks ALL critical actions and enforces minimum delays to prevent:
 * - Sub-tick actions (< 50ms)
 * - Instant slot switches
 * - Too-fast block placements
 *
 * NEVER bypass this guard - it's your protection against bans!
 */
public class ActionSafetyGuard {

    private static final ActionSafetyGuard INSTANCE = new ActionSafetyGuard();

    // ABSOLUTE MINIMUMS - NEVER GO BELOW THESE!
    private static final long MIN_SLOT_SWITCH_DELAY_MS = 50;  // 1 tick minimum
    private static final long MIN_BLOCK_PLACE_DELAY_MS = 50;  // 1 tick minimum
    private static final long MIN_RIGHT_CLICK_DELAY_MS = 50;  // 1 tick minimum
    private static final long MIN_SWITCH_TO_ACTION_DELAY_MS = 50; // 1 tick between switch and use

    // WARNING THRESHOLDS (faster than this = warning)
    private static final long WARN_SLOT_SWITCH_DELAY_MS = 100; // 2 ticks recommended
    private static final long WARN_BLOCK_PLACE_DELAY_MS = 100; // 2 ticks recommended

    // Last action timestamps
    private long lastSlotSwitchTime = 0;
    private long lastBlockPlaceTime = 0;
    private long lastRightClickTime = 0;

    private boolean debugMode = true;
    private final Minecraft mc = Minecraft.getInstance();

    private ActionSafetyGuard() {}

    public static ActionSafetyGuard getInstance() {
        return INSTANCE;
    }

    /**
     * Check if a slot switch is SAFE to perform right now
     * Returns true if safe, false if TOO FAST (would cause ban!)
     */
    public boolean canSwitchSlot() {
        long now = System.currentTimeMillis();
        long timeSinceLastSwitch = now - lastSlotSwitchTime;

        if (timeSinceLastSwitch < MIN_SLOT_SWITCH_DELAY_MS) {
            sendWarning("§c[SAFETY] BLOCKED SLOT SWITCH! Too fast: " + timeSinceLastSwitch + "ms (min: " + MIN_SLOT_SWITCH_DELAY_MS + "ms)");
            return false;
        }

        if (debugMode && timeSinceLastSwitch < WARN_SLOT_SWITCH_DELAY_MS) {
            sendDebug("§e[SAFETY] WARNING: Fast slot switch: " + timeSinceLastSwitch + "ms (recommended: " + WARN_SLOT_SWITCH_DELAY_MS + "ms+)");
        }

        return true;
    }

    /**
     * Check if a block placement is SAFE to perform right now
     * Returns true if safe, false if TOO FAST (would cause ban!)
     */
    public boolean canPlaceBlock() {
        long now = System.currentTimeMillis();
        long timeSinceLastPlace = now - lastBlockPlaceTime;
        long timeSinceLastSwitch = now - lastSlotSwitchTime;

        // Check minimum delay since last placement
        if (timeSinceLastPlace < MIN_BLOCK_PLACE_DELAY_MS) {
            sendWarning("§c[SAFETY] BLOCKED PLACEMENT! Too fast: " + timeSinceLastPlace + "ms (min: " + MIN_BLOCK_PLACE_DELAY_MS + "ms)");
            return false;
        }

        // Check minimum delay since last slot switch (critical for server sync!)
        if (timeSinceLastSwitch < MIN_SWITCH_TO_ACTION_DELAY_MS) {
            sendWarning("§c[SAFETY] BLOCKED PLACEMENT! Too fast after slot switch: " + timeSinceLastSwitch + "ms (min: " + MIN_SWITCH_TO_ACTION_DELAY_MS + "ms)");
            return false;
        }

        if (debugMode && timeSinceLastPlace < WARN_BLOCK_PLACE_DELAY_MS) {
            sendDebug("§e[SAFETY] WARNING: Fast placement: " + timeSinceLastPlace + "ms (recommended: " + WARN_BLOCK_PLACE_DELAY_MS + "ms+)");
        }

        return true;
    }

    /**
     * Check if a right-click action is SAFE to perform right now
     * Returns true if safe, false if TOO FAST (would cause ban!)
     */
    public boolean canRightClick() {
        long now = System.currentTimeMillis();
        long timeSinceLastClick = now - lastRightClickTime;
        long timeSinceLastSwitch = now - lastSlotSwitchTime;

        if (timeSinceLastClick < MIN_RIGHT_CLICK_DELAY_MS) {
            sendWarning("§c[SAFETY] BLOCKED RIGHT-CLICK! Too fast: " + timeSinceLastClick + "ms (min: " + MIN_RIGHT_CLICK_DELAY_MS + "ms)");
            return false;
        }

        // Check minimum delay since last slot switch
        if (timeSinceLastSwitch < MIN_SWITCH_TO_ACTION_DELAY_MS) {
            sendWarning("§c[SAFETY] BLOCKED RIGHT-CLICK! Too fast after slot switch: " + timeSinceLastSwitch + "ms (min: " + MIN_SWITCH_TO_ACTION_DELAY_MS + "ms)");
            return false;
        }

        return true;
    }

    /**
     * Record that a slot switch just happened
     * ALWAYS call this AFTER performing a switch!
     */
    public void recordSlotSwitch() {
        long now = System.currentTimeMillis();
        lastSlotSwitchTime = now;

        if (debugMode) {
            sendDebug("§7[SAFETY] Slot switch recorded at T+" + now);
        }
    }

    /**
     * Record that a block placement just happened
     * ALWAYS call this AFTER performing a placement!
     */
    public void recordBlockPlace() {
        long now = System.currentTimeMillis();
        lastBlockPlaceTime = now;

        if (debugMode) {
            sendDebug("§7[SAFETY] Block placement recorded at T+" + now);
        }
    }

    /**
     * Record that a right-click just happened
     * ALWAYS call this AFTER performing a right-click!
     */
    public void recordRightClick() {
        long now = System.currentTimeMillis();
        lastRightClickTime = now;

        if (debugMode) {
            sendDebug("§7[SAFETY] Right-click recorded at T+" + now);
        }
    }

    /**
     * Reset all timers (use when disabling module or after errors)
     */
    public void reset() {
        lastSlotSwitchTime = 0;
        lastBlockPlaceTime = 0;
        lastRightClickTime = 0;

        if (debugMode) {
            sendDebug("§7[SAFETY] All timers reset");
        }
    }

    /**
     * Get time since last slot switch in milliseconds
     */
    public long getTimeSinceLastSlotSwitch() {
        return System.currentTimeMillis() - lastSlotSwitchTime;
    }

    /**
     * Get time since last block placement in milliseconds
     */
    public long getTimeSinceLastBlockPlace() {
        return System.currentTimeMillis() - lastBlockPlaceTime;
    }

    public void setDebugMode(boolean enabled) {
        this.debugMode = enabled;
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    private void sendWarning(String message) {
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(message), false);
        }
    }

    private void sendDebug(String message) {
        if (debugMode && mc.player != null) {
            mc.player.displayClientMessage(Component.literal(message), false);
        }
    }

    /**
     * Get a summary of current timing status
     */
    public String getStatusSummary() {
        long now = System.currentTimeMillis();
        return String.format(
            "§7[SAFETY] Status: SlotSwitch=%dms ago, BlockPlace=%dms ago, RightClick=%dms ago",
            now - lastSlotSwitchTime,
            now - lastBlockPlaceTime,
            now - lastRightClickTime
        );
    }
}
