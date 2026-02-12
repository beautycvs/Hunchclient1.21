package dev.hunchclient.module.impl.terminalsolver;

import meteordevelopment.orbit.IEventBus;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Handler for the Rubix terminal.
 * Determines how many times to click each pane so every pane cycles to the target color order.
 */
public class RubixHandler extends TerminalHandler {
    private static final List<DyeColor> RUBIX_COLOR_ORDER = Arrays.asList(
            DyeColor.ORANGE, DyeColor.YELLOW, DyeColor.GREEN, DyeColor.BLUE, DyeColor.RED
    );

    public RubixHandler(IEventBus eventBus) {
        super(TerminalTypes.RUBIX, eventBus);
    }

    @Override
    public void onNewAttempt(int windowId) {
        super.onNewAttempt(windowId);
        // Clear cached solution when terminal is reopened (new attempt)
        lastRubixSolution = null;
    }

    @Override
    public boolean handleSlotUpdate(ClientboundContainerSetSlotPacket packet) {
        // null packet means full resync from SetContent - always recalculate
        if (items[items.length - 1] == null) return false;
        if (packet != null && packet.getSlot() != type.windowSize - 1) return false;

        // Solve synchronously
        List<Integer> result = solveRubix(items);
        solution.clear();
        solution.addAll(result);
        return true;
    }

    @Override
    protected void simulateClickImpl(int slotIndex, int clickType) {
        // Remove ONE occurrence from solution (Rubix has duplicate entries for multi-click slots)
        // E.g., solution=[14,14,14,22,22] means slot 14 needs 3 clicks, slot 22 needs 2
        // Remove by value (not by index position) - atomic and thread-safe
        solution.remove(Integer.valueOf(slotIndex));
    }

    private DyeColor lastRubixSolution = null;

    private List<Integer> solveRubix(ItemStack[] items) {
        // Collect all non-black panes for evaluation
        List<ItemStack> panes = new ArrayList<>();
        for (ItemStack item : items) {
            if (item != null) {
                DyeColor color = getPaneColor(item);
                if (color != null && color != DyeColor.BLACK) {
                    panes.add(item);
                }
            }
        }

        // Start with a large dummy solution
        List<Integer> temp = new ArrayList<>();
        for (int i = 0; i < 100; i++) temp.add(i);

        if (lastRubixSolution != null) {
            // Use cached solution
            int lastIndex = RUBIX_COLOR_ORDER.indexOf(lastRubixSolution);
            temp = calculateClicksFromPanes(panes, items, lastIndex);
        } else {
            // Try each color and find the best solution
            for (DyeColor color : RUBIX_COLOR_ORDER) {
                int goalIndex = RUBIX_COLOR_ORDER.indexOf(color);
                List<Integer> temp2 = calculateClicksFromPanes(panes, items, goalIndex);

                if (getRealSize(temp2) < getRealSize(temp)) {
                    temp = temp2;
                    lastRubixSolution = color;
                }
            }
        }

        return temp;
    }

    private List<Integer> calculateClicksFromPanes(List<ItemStack> panes, ItemStack[] items, int goalIndex) {
        // Expand each pane into the number of clicks needed to reach the goal color
        List<ItemStack> clickList = new ArrayList<>();

        for (ItemStack pane : panes) {
            DyeColor paneDye = getPaneColor(pane);
            if (paneDye == null) continue;

            int paneIdx = RUBIX_COLOR_ORDER.indexOf(paneDye);
            if (paneIdx != goalIndex) {
                int distance = dist(paneIdx, goalIndex);
                // Add this pane 'distance' times to the list
                for (int i = 0; i < distance; i++) {
                    clickList.add(pane);
                }
            }
        }

        // Map ItemStacks back to their indices using Array.indexOf()
        List<Integer> result = new ArrayList<>();
        for (ItemStack pane : clickList) {
            result.add(findItemIndex(items, pane));
        }

        return result;
    }

    // Find the index of an ItemStack in the array (by reference)
    private int findItemIndex(ItemStack[] items, ItemStack target) {
        for (int i = 0; i < items.length; i++) {
            if (items[i] == target) {
                return i;
            }
        }
        return -1; // Should never happen
    }

    private int getRealSize(List<Integer> list) {
        int size = 0;
        List<Integer> distinct = new ArrayList<>();
        for (Integer item : list) {
            if (!distinct.contains(item)) {
                distinct.add(item);
            }
        }

        for (int pane : distinct) {
            int count = 0;
            for (int item : list) {
                if (item == pane) count++;
            }
            size += (count >= 3) ? (5 - count) : count;
        }
        return size;
    }

    private int dist(int pane, int most) {
        if (pane > most) {
            return (most + RUBIX_COLOR_ORDER.size()) - pane;
        }
        return most - pane;
    }

    private DyeColor getPaneColor(ItemStack item) {
        if (item.getItem() instanceof BlockItem blockItem) {
            Block block = blockItem.getBlock();
            if (block instanceof StainedGlassPaneBlock paneBlock) {
                return paneBlock.getColor();
            }
        }
        return null;
    }
}
