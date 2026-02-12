package dev.hunchclient.module.impl.terminal;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Melody terminal simulator.
 * A magenta column is the target, a lime pointer sweeps across the current row, and the player clicks column 7 when they align.
 */
public class MelodySim extends TermSimGUI {

    private int magentaColumn = 1;
    private int limeColumn = 1;
    private int currentRow = 1;
    private int limeDirection = 1;
    private int tickCounter = 0;
    private boolean animationPaused = false;
    private int pauseTicksRemaining = 0;

    public MelodySim(Inventory playerInventory) {
        super(TerminalType.MELODY, playerInventory);
    }

    @Override
    protected void initializeTerminal() {
        currentRow = 1;
        magentaColumn = 1 + random.nextInt(5); // (1..5).random()
        limeColumn = 1;
        limeDirection = 1;
        tickCounter = 0;
        animationPaused = false;
        pauseTicksRemaining = 0;
        updateGui();
    }

    @Override
    protected void containerTick() {
        // Check if animation is paused
        if (animationPaused) {
            pauseTicksRemaining--;
            if (pauseTicksRemaining <= 0) {
                animationPaused = false;
            }
            return; // Don't move lime pane while paused
        }

        // Update every 20 ticks (1 second) for predictable movement speed
        if (tickCounter++ % 20 != 0) return;

        limeColumn += limeDirection;
        if (limeColumn == 1 || limeColumn == 5) {
            limeDirection *= -1;
        }

        updateGui();
        super.containerTick();
    }

    @Override
    protected Boolean handleTerminalClickWithResult(int slot, int button) {
        int row = slot / 9;
        int col = slot % 9;

        // Check if it's a black pane or not a button column
        if (col != 7) {
            return null; // Ignore non-button clicks
        }

        // Check if it's not the current row
        if (row != currentRow) {
            return null; // Ignore inactive buttons
        }

        if (limeColumn != magentaColumn) {
            // WRONG TIMING - pause animation briefly
            animationPaused = true;
            pauseTicksRemaining = 20; // Pause for 1 second (20 ticks)
            playClickSound(false);
            triggerVisualFeedback(false);
            return null; // Ignore but pause (don't fail the terminal)
        }

        magentaColumn = 1 + random.nextInt(4); // (1 until 5) = 1,2,3,4
        currentRow++;
        playClickSound(true);
        triggerVisualFeedback(true);
        updateGui();
        return true;
    }

    @Override
    protected boolean checkCompletion() {
        return currentRow >= 5;
    }

    private void updateGui() {
        for (int index = 0; index < inventory.getContainerSize(); index++) {
            int col = index % 9;
            int row = index / 9;

            ItemStack stack = generateItemStack(index, col, row);
            setStack(index, stack);
        }
    }

    private ItemStack generateItemStack(int index, int col, int row) {
        if (col == magentaColumn && (row < 1 || row > 4)) {
            return pane(Items.MAGENTA_STAINED_GLASS_PANE);
        }
        else if (col == limeColumn && row == currentRow) {
            return pane(Items.LIME_STAINED_GLASS_PANE);
        }
        else if (col >= 1 && col <= 5 && row == currentRow) {
            return pane(Items.RED_STAINED_GLASS_PANE);
        }
        else if (col == 7 && row == currentRow) {
            return pane(Items.LIME_TERRACOTTA);
        }
        else if (col == 7 && row >= 1 && row <= 4) {
            return pane(Items.RED_TERRACOTTA);
        }
        else if (col >= 1 && col <= 5 && row >= 1 && row <= 4) {
            return pane(Items.WHITE_STAINED_GLASS_PANE);
        }
        else {
            return pane(Items.BLACK_STAINED_GLASS_PANE);
        }
    }

    private ItemStack pane(net.minecraft.world.item.Item item) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(""));
        return stack;
    }
}
