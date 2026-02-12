package dev.hunchclient.module.impl.terminal;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Starts With terminal simulator.
 * Picks a random target letter, fills the grid with items based on their display names, and tracks clicks via glint.
 */
public class StartsWithSim extends TermSimGUI {

    private static final String[] LETTERS = {"A", "B", "C", "G", "D", "M", "N", "R", "S", "T"};
    private final String letter;
    private final List<Integer> correctSlots = new ArrayList<>();
    // Track which slots we've clicked (for completion check)
    private final Set<Integer> clickedSlots = new HashSet<>();

    public StartsWithSim(Inventory playerInventory) {
        super(TerminalType.STARTS_WITH, playerInventory, "What starts with: '" + getRandomLetter() + "'?");
        this.letter = extractLetterFromTitle();
    }

    private static String getRandomLetter() {
        return LETTERS[new java.util.Random().nextInt(LETTERS.length)];
    }

    private String extractLetterFromTitle() {
        // Extract letter from title "What starts with: 'X'?"
        String title = this.getTitle().getString();
        int start = title.indexOf("'") + 1;
        int end = title.lastIndexOf("'");
        return title.substring(start, end);
    }

    @Override
    protected void initializeTerminal() {
        correctSlots.clear();
        clickedSlots.clear();

        // Always guarantee at least one matching item in rows 1-3, columns 1-7
        int guaranteedSlot = 10 + random.nextInt(7); // (10..16).random()

        for (int index = 0; index < inventory.getContainerSize(); index++) {
            int row = index / 9;
            int col = index % 9;

            ItemStack stack;

            if (row < 1 || row > 3 || col < 1 || col > 7) {
                stack = pane(net.minecraft.world.item.Items.BLACK_STAINED_GLASS_PANE);
            }
            else if (index == guaranteedSlot) {
                stack = getLetterItemStack(false);
                if (stack != null) {
                    correctSlots.add(index);
                }
            }
            else if (Math.random() > 0.7) {
                stack = getLetterItemStack(false);
                if (stack != null) {
                    correctSlots.add(index);
                }
            }
            else {
                stack = getLetterItemStack(true);
            }

            if (stack != null) {
                setStack(index, stack);
            }
        }
    }

    @Override
    protected Boolean handleTerminalClickWithResult(int slot, int button) {
        ItemStack stack = getStack(slot);

        if (stack.isEmpty()) {
            return null; // Ignore empty slots
        }

        // Check if it's a black pane (background)
        if (stack.is(net.minecraft.world.item.Items.BLACK_STAINED_GLASS_PANE)) {
            return null; // Ignore background clicks
        }

        // Check if already clicked (using our explicit tracking, not glint!)
        // Must check clickedSlots BEFORE hasGlint because we set glint to true after click
        if (clickedSlots.contains(slot)) {
            return null; // Already clicked, ignore
        }

        // Use the display name (not registry name) for matching
        String displayName = stack.getHoverName().getString();
        boolean startsWithLetter = displayName.toLowerCase().startsWith(letter.toLowerCase());

        if (!startsWithLetter) {
            // WRONG CLICK - Play error sound and show red flash
            playClickSound(false);
            triggerVisualFeedback(false); // Red flash
            return false; // WRONG ITEM - FAIL!
        }

        // Correct item! Mark as clicked with glint
        // IMPORTANT: Set to TRUE (not false) so the solver can see it's clicked
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        setStack(slot, stack);

        // Track this slot as clicked
        clickedSlots.add(slot);

        // CORRECT CLICK - Play success sound and show green flash
        playClickSound(true);
        triggerVisualFeedback(true); // Green flash

        return true; // Correct click
    }

    @Override
    protected boolean checkCompletion() {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = getStack(i);
            if (!stack.isEmpty() && !stack.is(net.minecraft.world.item.Items.BLACK_STAINED_GLASS_PANE)) {
                String displayName = stack.getHoverName().getString();
                boolean startsWithLetter = displayName.toLowerCase().startsWith(letter.toLowerCase());

                if (startsWithLetter && !stack.hasFoil()) {
                    return false; // Still items to click
                }
            }
        }
        return true;
    }

    private ItemStack getLetterItemStack(boolean filterNot) {
        List<Item> matchingItems = new ArrayList<>();

        for (Item item : BuiltInRegistries.ITEM) {
            String displayName;
            try {
                displayName = new ItemStack(item).getHoverName().getString();
            } catch (Exception e) {
                continue; // Skip items that can't be created
            }

            boolean startsWithLetter = displayName.toLowerCase().startsWith(letter.toLowerCase());
            boolean isValid = (startsWithLetter != filterNot) && !displayName.toLowerCase().contains("pane");

            if (isValid) {
                matchingItems.add(item);
            }
        }

        if (matchingItems.isEmpty()) {
            return new ItemStack(net.minecraft.world.item.Items.STONE);
        }

        Item randomItem = matchingItems.get(random.nextInt(matchingItems.size()));
        return new ItemStack(randomItem);
    }

    private ItemStack pane(net.minecraft.world.item.Item item) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(""));
        return stack;
    }
}
