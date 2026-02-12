package dev.hunchclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;


// @author Hunch

public class SwapManager {

    private static final Minecraft MC = Minecraft.getInstance();

    // State tracking
    private boolean recentlySwapped = false;
    private int lastSwapTick = -1;
    private int currentTick = 0;
    private int originalSlot = -1;

    /**
     * Update tick counter (call this in module's onTick())
     */
    public void tick() {
        currentTick++;

        // Reset recentlySwapped flag after tick passes
        if (lastSwapTick < currentTick) {
            recentlySwapped = false;
        }
    }

    /**
     * Check if swapping is safe 
     * @return true if swap is allowed this tick
     */
    public boolean canSwap() {
        return !recentlySwapped;
    }

    /**
     * Swap to a specific hotbar slot
     * @param targetSlot Hotbar slot to swap to (0-8)
     * @return true if swap was successful
     */
    public boolean swapToSlot(int targetSlot) {
        if (MC.player == null) return false;
        if (recentlySwapped) return false; // Already swapped this tick
        if (targetSlot < 0 || targetSlot > 8) return false;

        Inventory inventory = MC.player.getInventory();

        // Save original slot (only if not already swapped)
        if (originalSlot == -1) {
            originalSlot = inventory.getSelectedSlot();
        }

        // No-op if already holding target item
        if (inventory.getSelectedSlot() == targetSlot) {
            originalSlot = -1; // No need to swap back
            return false;
        }

        // Perform swap
        inventory.setSelectedSlot(targetSlot);

        // Mark as swapped
        recentlySwapped = true;
        lastSwapTick = currentTick;

        return true;
    }

    /**
     * Restore original hotbar slot
     * @return true if restore was successful
     */
    public boolean restoreOriginalSlot() {
        if (MC.player == null) return false;
        if (originalSlot == -1) return false; // Nothing to restore
        if (originalSlot < 0 || originalSlot > 8) return false;
        if (recentlySwapped) return false; // Already swapped this tick

        // Perform restore
        MC.player.getInventory().setSelectedSlot(originalSlot);

        // Mark as swapped and clear originalSlot
        recentlySwapped = true;
        lastSwapTick = currentTick;
        originalSlot = -1;

        return true;
    }

    /**
     * Get current original slot (for checking if swap is active)
     * @return original slot index, or -1 if no swap is active
     */
    public int getOriginalSlot() {
        return originalSlot;
    }

    /**
     * Check if currently swapped to a different slot
     * @return true if a swap is active
     */
    public boolean isSwapped() {
        return originalSlot != -1;
    }

    /**
     * Reset swap state (call in onDisable())
     */
    public void reset() {
        recentlySwapped = false;
        lastSwapTick = -1;
        currentTick = 0;
        originalSlot = -1;
    }

    /**
     * Force immediate restore without swap guard (use with caution)
     * This bypasses the anti-cheat safety check
     */
    public void forceRestore() {
        if (MC.player != null && originalSlot >= 0 && originalSlot <= 8) {
            MC.player.getInventory().setSelectedSlot(originalSlot);
            originalSlot = -1;
        }
    }
}
