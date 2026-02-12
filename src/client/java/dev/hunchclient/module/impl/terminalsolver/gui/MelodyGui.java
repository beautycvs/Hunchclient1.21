package dev.hunchclient.module.impl.terminalsolver.gui;

import dev.hunchclient.util.Color;
import dev.hunchclient.util.Colors;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * GUI for the Melody terminal.
 * Rendering is independent from the autoclick solution: the solution tracks only clickable terracotta slots, while the GUI draws the full pane state.
 */
public class MelodyGui extends TermGui {
    public static final MelodyGui INSTANCE = new MelodyGui();

    public static Color melodyColumnColor = Colors.MINECRAFT_DARK_PURPLE;
    public static Color melodyRowColor = Colors.MINECRAFT_RED;
    public static Color melodyPointerColor = Colors.MINECRAFT_GREEN;
    public static Color melodyBackgroundColor = Colors.gray26;

    @Override
    public void renderTerminal(int slotCount) {
        renderBackground(slotCount, 7); // 7 columns width

        if (currentTerm == null || currentTerm.items == null) return;

        ItemStack[] items = currentTerm.items;

        // First find the lime pane row - check column 7 (row indicator on right side)
        int limePaneRow = -1;
        for (int r = 1; r <= 4; r++) {
            int idx = r * 9 + 7; // column 7
            if (idx < items.length && items[idx] != null &&
                items[idx].getItem() == Items.LIME_STAINED_GLASS_PANE) {
                limePaneRow = r;
                break;
            }
        }

        // Also check for lime pane in the grid area (columns 1-5) - the moving pointer
        if (limePaneRow == -1) {
            for (int r = 1; r <= 4; r++) {
                for (int c = 1; c <= 5; c++) {
                    int idx = r * 9 + c;
                    if (idx < items.length && items[idx] != null &&
                        items[idx].getItem() == Items.LIME_STAINED_GLASS_PANE) {
                        limePaneRow = r;
                        break;
                    }
                }
                if (limePaneRow != -1) break;
            }
        }

        // Iterate through the visible 0-43 slot grid
        for (int index = 0; index < 44; index++) {
            if (index >= items.length) continue;

            int column = index % 9;
            int row = index / 9;
            ItemStack item = items[index];

            Color color = null;

            // Row 0: Magenta panes (column indicator)
            if (row == 0) {
                if (item != null && item.getItem() == Items.MAGENTA_STAINED_GLASS_PANE) {
                    color = melodyColumnColor;
                } else {
                    continue;
                }
            }
            // Column 7, rows 1-4: Lime pane (row pointer)
            else if (column == 7 && row >= 1 && row <= 4) {
                if (item != null && item.getItem() == Items.LIME_STAINED_GLASS_PANE) {
                    color = melodyPointerColor;
                } else {
                    color = melodyBackgroundColor;
                }
            }
            // Columns 1-5: Main grid area
            else if (column >= 1 && column <= 5) {
                if (item != null) {
                    // Lime pane = current position (green)
                    if (item.getItem() == Items.LIME_STAINED_GLASS_PANE) {
                        color = melodyPointerColor;
                    }
                    // Lime terracotta = clickable button (green)
                    else if (item.getItem() == Items.LIME_TERRACOTTA) {
                        color = melodyPointerColor;
                    }
                    // Same row as lime pane = red (active row)
                    else if (row == limePaneRow) {
                        color = melodyRowColor;
                    }
                    // Otherwise background
                    else {
                        color = melodyBackgroundColor;
                    }
                } else {
                    // Empty slot - check if in active row
                    if (row == limePaneRow) {
                        color = melodyRowColor;
                    } else {
                        color = melodyBackgroundColor;
                    }
                }
            }
            else {
                continue;
            }

            renderSlot(index, color, color);
        }
    }

    @Override
    protected int determineSlotCount() {
        return currentTerm != null && currentTerm.type != null
            ? currentTerm.type.windowSize
            : 0;
    }

    @Override
    protected boolean supportsCompactMode() {
        return false;
    }

    @Override
    protected Color getAccentColor() {
        return melodyPointerColor;
    }
}
