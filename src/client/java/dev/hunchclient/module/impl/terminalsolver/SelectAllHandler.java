package dev.hunchclient.module.impl.terminalsolver;

import meteordevelopment.orbit.IEventBus;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.*;

/**
 * Handler for the Select All terminal.
 * Matches items whose display name starts with the target color, with fallbacks for color-themed items without name prefixes.
 */
public class SelectAllHandler extends TerminalHandler {
    private static final boolean DEBUG = false; // Disabled - use keybind debug dump instead
    private static final Map<String, DyeColor> colorFromName;

    static {
        // Initialize color name mapping for parsing window title
        colorFromName = new HashMap<>();
        for (DyeColor color : DyeColor.values()) {
            // Map both underscore and space versions
            colorFromName.put(color.getName().toUpperCase(Locale.ENGLISH), color);
            colorFromName.put(color.name().replace("_", " "), color); // enum name with spaces
        }
        // Skyblock uses "SILVER" for LIGHT_GRAY
        colorFromName.put("SILVER", DyeColor.LIGHT_GRAY);
    }

    private final DyeColor targetColor;
    private final String colorNameForMatching; // "LIGHT BLUE", "RED", etc.

    public SelectAllHandler(String colorName, IEventBus eventBus) {
        super(TerminalTypes.SELECT, eventBus);

        // Parse color from window title (e.g., "LIGHT BLUE" or "light_blue")
        String upperName = colorName.toUpperCase(Locale.ENGLISH).replace("_", " ");
        this.targetColor = colorFromName.getOrDefault(upperName,
            colorFromName.getOrDefault(colorName.toUpperCase(Locale.ENGLISH), DyeColor.WHITE));

        // Use the enum name with spaces for display-name comparisons ("LIGHT BLUE", "RED", etc.)
        this.colorNameForMatching = targetColor.name().replace("_", " ");

        if (DEBUG) {
            System.out.println("[SelectAll] === TERMINAL OPENED ===");
            System.out.println("[SelectAll] Input colorName: '" + colorName + "'");
            System.out.println("[SelectAll] Parsed targetColor: " + targetColor);
            System.out.println("[SelectAll] colorNameForMatching: '" + colorNameForMatching + "'");
        }
    }

    @Override
    public String getSearchCriteria() {
        return colorNameForMatching;
    }

    @Override
    public boolean handleSlotUpdate(ClientboundContainerSetSlotPacket packet) {
        // null packet means full resync from SetContent - always recalculate
        if (packet != null && packet.getSlot() != type.windowSize - 1) {
            return false;
        }

        solution.clear();
        solution.addAll(solveSelectAll());
        return true;
    }

    @Override
    protected void simulateClickImpl(int slotIndex, int clickType) {
        // Remove by value (not by index position) - atomic and thread-safe
        solution.remove(Integer.valueOf(slotIndex));
    }

    // Build a list of slots whose items match the target color and are not already clicked/glinting.
    private List<Integer> solveSelectAll() {
        List<Integer> result = new ArrayList<>();

        if (DEBUG) {
            System.out.println("[SelectAll] === SOLVING ===");
            System.out.println("[SelectAll] Looking for items starting with: '" + colorNameForMatching + "'");
            System.out.println("[SelectAll] Total slots: " + items.length);
        }

        int nullCount = 0;
        int emptyCount = 0;
        int glintCount = 0;
        int paneCount = 0;
        int matchCount = 0;
        int noMatchCount = 0;

        for (int index = 0; index < items.length; index++) {
            ItemStack item = items[index];
            if (item == null) {
                nullCount++;
                continue;
            }
            if (item.isEmpty()) {
                emptyCount++;
                continue;
            }

            // Ignore items that are already glinting (already clicked)
            if (item.hasFoil()) {
                glintCount++;
                if (DEBUG) {
                    String itemName = item.getItem().getName(item).getString();
                    System.out.println("[SelectAll] Slot " + index + ": SKIP (has glint) - '" + itemName + "'");
                }
                continue;
            }

            // Decorative panes are never part of the solution
            if (item.getItem() == Items.BLACK_STAINED_GLASS_PANE) {
                paneCount++;
                continue;
            }

            // Use the display name as shown in-game for matching
            String itemName = item.getItem().getName(item).getString();
            String itemNameUpper = itemName != null ? itemName.toUpperCase(Locale.ENGLISH) : "";
            boolean nameMatches = itemName != null && itemNameUpper.startsWith(colorNameForMatching);

            // Some color items do not include the color in their display name
            boolean specialItemMatches = switch (targetColor) {
                case BLACK -> item.getItem() == Items.INK_SAC;
                case BLUE -> item.getItem() == Items.LAPIS_LAZULI;
                case BROWN -> item.getItem() == Items.COCOA_BEANS;
                case WHITE -> item.getItem() == Items.BONE_MEAL;
                default -> false;
            };

            if (nameMatches || specialItemMatches) {
                matchCount++;
                result.add(index);
                if (DEBUG) {
                    System.out.println("[SelectAll] Slot " + index + ": MATCH - '" + itemName + "' (upper: '" + itemNameUpper + "') startsWith '" + colorNameForMatching + "' = " + nameMatches + ", special = " + specialItemMatches);
                }
            } else {
                noMatchCount++;
                if (DEBUG) {
                    System.out.println("[SelectAll] Slot " + index + ": NO MATCH - '" + itemName + "' (upper: '" + itemNameUpper + "') does NOT start with '" + colorNameForMatching + "'");
                }
            }
        }

        if (DEBUG) {
            System.out.println("[SelectAll] === SOLVE RESULT ===");
            System.out.println("[SelectAll] null slots: " + nullCount);
            System.out.println("[SelectAll] empty slots: " + emptyCount);
            System.out.println("[SelectAll] glint (clicked): " + glintCount);
            System.out.println("[SelectAll] black panes: " + paneCount);
            System.out.println("[SelectAll] MATCHED: " + matchCount);
            System.out.println("[SelectAll] no match: " + noMatchCount);
            System.out.println("[SelectAll] Solution slots: " + result);
        }

        return result;
    }
}
