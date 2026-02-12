package dev.hunchclient.module.impl.terminalsolver;

import meteordevelopment.orbit.IEventBus;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Handler for the "Starts With" terminal.
 * Builds a solution from items whose display name starts with the target letter, while handling items that always glint.
 */
public class StartsWithHandler extends TerminalHandler {
    private static final boolean DEBUG = true; // Verbose logging for troubleshooting glint edge cases

    private final String letter;
    // Local tracking for items that naturally glint (server never updates them)
    // Examples:
    // - Nether Star: always hasFoil() = true, server never changes
    // - Bottle o' Enchanting: has a default glint as well
    private final Set<Integer> clickedNaturalGlintSlots = new HashSet<>();

    public StartsWithHandler(String letter, IEventBus eventBus) {
        super(TerminalTypes.STARTS_WITH, eventBus);
        this.letter = letter;

        if (DEBUG) {
            System.out.println("[StartsWith] === TERMINAL OPENED ===");
            System.out.println("[StartsWith] Letter: '" + letter + "'");
        }
    }

    @Override
    public String getSearchCriteria() {
        return letter;
    }

    @Override
    public boolean handleSlotUpdate(ClientboundContainerSetSlotPacket packet) {
        // Only react once the full inventory has been delivered
        if (packet != null && packet.getSlot() != type.windowSize - 1) return false;

        solution.clear();
        solution.addAll(solveStartsWith(items, letter));
        return true;
    }

    @Override
    protected void simulateClickImpl(int slotIndex, int clickType) {
        // Remove locally for visual feedback only; server glint remains authoritative
        solution.remove(Integer.valueOf(slotIndex));
    }

    /**
     * Called when a click is actually sent to the server.
     * Tracks clicks on items that have a permanent glint so they can be filtered locally.
     */
    @Override
    public void onClickSent(int slotIndex) {
        // Track natural glint items locally (server doesn't change their state)
        if (slotIndex >= 0 && slotIndex < items.length && items[slotIndex] != null) {
            if (isNaturalGlintItem(items[slotIndex])) {
                clickedNaturalGlintSlots.add(slotIndex);
                if (DEBUG) {
                    System.out.println("[StartsWith] Tracking natural glint item click at slot " + slotIndex + " (sent to server)");
                }
            }
        }
    }

    @Override
    public void onNewAttempt(int newWindowId) {
        super.onNewAttempt(newWindowId);
        // Clear local tracking on new terminal attempt
        clickedNaturalGlintSlots.clear();
        if (DEBUG) {
            System.out.println("[StartsWith] Cleared natural glint click tracking (new attempt)");
        }
    }

    // Build a list of slots where the display name starts with the target letter and the item is not already clicked/glinting.
    private List<Integer> solveStartsWith(ItemStack[] items, String letter) {
        List<Integer> result = new ArrayList<>();

        if (DEBUG) {
            System.out.println("[StartsWith] === SOLVING ===");
            System.out.println("[StartsWith] Looking for items starting with: '" + letter + "'");
            System.out.println("[StartsWith] Total slots: " + items.length);
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

            // Skip decorative glass panes so they never count as matches
            if (item.getItem() == Items.BLACK_STAINED_GLASS_PANE) {
                paneCount++;
                continue; // SKIP panes! Otherwise "Black Stained Glass Pane" matches letter 'B'
            }

            String hoverName = item.getHoverName().getString();
            if (hoverName == null) {
                if (DEBUG) {
                    System.out.println("[StartsWith] Slot " + index + ": null hoverName");
                }
                continue;
            }

            boolean startsWithLetter = hoverName.toLowerCase().startsWith(letter.toLowerCase());

            // Nether Star and Bottle o' Enchanting always glint; track clicks locally for them.
            boolean naturalGlintItem = isNaturalGlintItem(item);
            boolean hasGlint;
            if (naturalGlintItem) {
                // Use local tracking since the server never toggles their glint
                hasGlint = clickedNaturalGlintSlots.contains(index);
                if (DEBUG && startsWithLetter) {
                    System.out.println("[StartsWith] Slot " + index + ": Natural glint item, locally tracked clicked=" + hasGlint);
                }
            } else {
                // For all other items the server glint indicates click state
                hasGlint = item.hasFoil();
            }

            if (startsWithLetter && !hasGlint) {
                matchCount++;
                result.add(index);
                if (DEBUG) {
                    System.out.println("[StartsWith] Slot " + index + ": MATCH - '" + hoverName + "' starts with '" + letter + "'");
                }
            } else {
                if (hasGlint) {
                    glintCount++;
                    if (DEBUG && startsWithLetter) {
                        System.out.println("[StartsWith] Slot " + index + ": SKIP (has glint/already clicked) - '" + hoverName + "'");
                    }
                } else if (!startsWithLetter) {
                    noMatchCount++;
                    if (DEBUG && !hoverName.isEmpty() && !hoverName.equals(" ")) {
                        System.out.println("[StartsWith] Slot " + index + ": NO MATCH - '" + hoverName + "' does NOT start with '" + letter + "'");
                    }
                }
            }
        }

        if (DEBUG) {
            System.out.println("[StartsWith] === SOLVE RESULT ===");
            System.out.println("[StartsWith] null slots: " + nullCount);
            System.out.println("[StartsWith] empty slots: " + emptyCount);
            System.out.println("[StartsWith] black panes: " + paneCount);
            System.out.println("[StartsWith] glint (server-confirmed clicked): " + glintCount);
            System.out.println("[StartsWith] MATCHED: " + matchCount);
            System.out.println("[StartsWith] no match: " + noMatchCount);
            System.out.println("[StartsWith] Solution slots: " + result);
        }

        return result;
    }

    private boolean isNaturalGlintItem(ItemStack item) {
        return item.getItem() == Items.NETHER_STAR || item.getItem() == Items.EXPERIENCE_BOTTLE;
    }
}
