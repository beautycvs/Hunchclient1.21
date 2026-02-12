package dev.hunchclient.module.impl.terminalsolver;

import meteordevelopment.orbit.IEventBus;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.ArrayList;
import java.util.List;

/** Handler for the Melody terminal (timing-based pane matching). */
public class MelodyHandler extends TerminalHandler {

    public MelodyHandler(IEventBus eventBus) {
        super(TerminalTypes.MELODY, eventBus);
    }

    @Override
    public boolean isHighPingModeEnabled() {
        // Disable high ping mode for Melody terminal - timing is critical
        return false;
    }

    @Override
    protected void simulateClickImpl(int slotIndex, int clickType) {
        // MELODY: Do NOT remove clicked slots from solution locally!
        // We need to wait for server confirmation (SetSlot packets) to update.
        // Removing slots locally causes GUI to freeze when clicks fail.
        // Override with empty implementation - no local prediction for Melody!
    }

    @Override
    public boolean handleSlotUpdate(ClientboundContainerSetSlotPacket packet) {
        System.out.println("[MelodyHandler] handleSlotUpdate called: packet=" + (packet != null) + 
            ", item=" + (packet != null && packet.getItem() != null));
        
        // Only update when the packet contains an item
        if (packet == null || packet.getItem() == null) {
            System.out.println("[MelodyHandler] Ignoring packet (null packet or null item)");
            return false;
        }

        List<Integer> newSolution = solveMelody(items);
        System.out.println("[MelodyHandler] solveMelody returned " + newSolution.size() + " slots");

        // CRITICAL: Always reset isClicked for Melody!
        // Melody doesn't send OpenScreen, so we need to reset on every slot update
        isClicked = false;

        // Get current lime pane row for tracking
        int currentLimePaneRow = -1;
        for (int r = 1; r <= 4; r++) {
            for (int c = 1; c <= 5; c++) {
                int idx = r * 9 + c;
                if (idx < items.length && items[idx] != null &&
                    items[idx].getItem() == Items.LIME_STAINED_GLASS_PANE) {
                    currentLimePaneRow = r;
                    break;
                }
            }
            if (currentLimePaneRow != -1) break;
        }

        // Solution update logic:
        // 1. If the new solution is not empty -> update and track row
        // 2. If the row changed -> clear old solution (no retry for different row)
        // 3. If the same row and new is empty -> keep old solution (allow retry)
        if (!newSolution.isEmpty()) {
            solution.clear();
            solution.addAll(newSolution);
            lastClickRow = currentLimePaneRow;
        } else if (currentLimePaneRow != lastClickRow && lastClickRow != -1) {
            // Row changed - clear solution, no retry
            solution.clear();
            lastClickRow = -1;
        }
        // else: same row, keep old solution for retry
        
        System.out.println("[MelodyHandler] Current solution: " + solution + " (new was: " + newSolution + ")");
        
        // CRITICAL: ALWAYS force GUI refresh for Melody on every packet!
        // Even if solution is empty or unchanged, GUI needs to update
        if (type != null && type.getGUI() != null) {
            type.getGUI().onSolutionUpdate();
            System.out.println("[MelodyHandler] onSolutionUpdate() called successfully");
        } else {
            System.out.println("[MelodyHandler] WARNING: type or GUI is null!");
        }
        
        // ALWAYS return true for Melody - we need GUI to update on every packet
        return true;
    }

    // Pre-position target for mouse (not clickable yet, just move there)
    private int prePositionSlot = -1;
    // Track the row we last clicked for - to clear solution when row changes
    private int lastClickRow = -1;

    public int getPrePositionSlot() {
        return prePositionSlot;
    }

    /**
     * Position-based solveMelody - clicks Column 7 based on Lime Pane row
     * Item data for terracotta can be bugged (shows as "air"), so we use position instead.
     */
    private List<Integer> solveMelody(ItemStack[] items) {
        List<Integer> result = new ArrayList<>();
        prePositionSlot = -1;

        // Find the lime pane (current position) and magenta pane (target column)
        int greenPane = -1;  // indexOfLast for LIME_STAINED_GLASS_PANE
        int magentaPane = -1; // indexOfFirst for MAGENTA_STAINED_GLASS_PANE

        for (int i = 0; i < items.length; i++) {
            if (items[i] != null) {
                if (items[i].getItem() == Items.LIME_STAINED_GLASS_PANE) {
                    greenPane = i; // last one wins
                }
                if (items[i].getItem() == Items.MAGENTA_STAINED_GLASS_PANE && magentaPane == -1) {
                    magentaPane = i; // first one wins
                }
            }
        }

        // Calculate the Column 7 slot for the current row (same row as greenPane)
        // This is the clickable button position, regardless of what item type it shows
        int clickSlot = -1;
        if (greenPane != -1) {
            int row = greenPane / 9;
            if (row >= 1 && row <= 4) {
                clickSlot = row * 9 + 7; // Column 7 of current row
                prePositionSlot = clickSlot; // Always pre-position to the button
            }
        }

        // Add to solution when columns match (greenPane column == magentaPane column)
        // Don't check item type - the terracotta item data can be bugged!
        if (greenPane != -1 && magentaPane != -1 && greenPane % 9 == magentaPane % 9 && clickSlot != -1) {
            result.add(clickSlot);
        }

        System.out.println("[MelodyHandler] greenPane=" + greenPane +
            ", magentaPane=" + magentaPane +
            ", clickSlot=" + clickSlot +
            ", columnsMatch=" + (greenPane != -1 && magentaPane != -1 && greenPane % 9 == magentaPane % 9) +
            ", solution=" + result);
        return result;
    }
}
