package dev.hunchclient.module.impl.terminal;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Rubix terminal simulator.
 * Centers a 3x3 grid of colored panes; left/right clicks rotate colors forward/backward until all match.
 * Color order: orange, yellow, green, blue, red.
 */
public class RubixSim extends TermSimGUI {

    // Pane color order: ORANGE, YELLOW, GREEN, BLUE, RED
    private static final Item[] PANES = new Item[] {
        Items.ORANGE_STAINED_GLASS_PANE,  // 0
        Items.YELLOW_STAINED_GLASS_PANE,  // 1
        Items.GREEN_STAINED_GLASS_PANE,   // 2 - true GREEN pane
        Items.BLUE_STAINED_GLASS_PANE,    // 3 - BLUE (not LIGHT_BLUE!)
        Items.RED_STAINED_GLASS_PANE      // 4
    };

    // Core 3x3 area (rows 1-3, cols 3-5)
    private static final int[] INDICES = new int[] {
        12, 13, 14,  // Row 1 (1*9 + 3,4,5)
        21, 22, 23,  // Row 2 (2*9 + 3,4,5)
        30, 31, 32   // Row 3 (3*9 + 3,4,5)
    };

    private final Map<Integer, Integer> slotColors = new HashMap<>();

    public RubixSim(Inventory playerInventory) {
        super(TerminalType.RUBIX, playerInventory);
    }

    @Override
    protected void initializeTerminal() {
        slotColors.clear();

        // Randomly assign colors to the 3x3 grid
        for (int slot : INDICES) {
            int colorIndex = getRandomColorIndex();
            slotColors.put(slot, colorIndex);
            setStack(slot, genStack(colorIndex));
        }
        // Rest is black background from initializeBackground()
    }

    // Even 20% chance for each color
    private int getRandomColorIndex() {
        double rand = Math.random();
        if (rand < 0.2) return 0;
        else if (rand < 0.4) return 1;
        else if (rand < 0.6) return 2;
        else if (rand < 0.8) return 3;
        else return 4;
    }

    @Override
    protected Boolean handleTerminalClickWithResult(int slot, int button) {
        // Check if this is an active slot
        if (!slotColors.containsKey(slot)) {
            return null;
        }

        int currentIndex = slotColors.get(slot);
        int nextIndex;

        if (button == 1) {
            // RIGHT CLICK: backwards
            nextIndex = (currentIndex - 1 + PANES.length) % PANES.length;
        } else {
            // LEFT CLICK: forwards
            nextIndex = (currentIndex + 1) % PANES.length;
        }

        slotColors.put(slot, nextIndex);
        setStack(slot, genStack(nextIndex));

        playClickSound(true);
        triggerVisualFeedback(true);

        return true;
    }

    @Override
    protected boolean checkCompletion() {
        if (slotColors.isEmpty()) return false;

        int referenceColor = slotColors.get(INDICES[0]);
        for (int slot : INDICES) {
            if (slotColors.get(slot) != referenceColor) {
                return false;
            }
        }
        return true;
    }

    private ItemStack genStack(int colorIndex) {
        // Bounds check in case of bad input
        int safeIndex = (colorIndex >= 0 && colorIndex < PANES.length) ? colorIndex : PANES.length - 1;
        ItemStack stack = new ItemStack(PANES[safeIndex]);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(""));
        return stack;
    }
}
