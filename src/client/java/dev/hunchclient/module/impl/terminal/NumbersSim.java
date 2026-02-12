package dev.hunchclient.module.impl.terminal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Numbers/Order terminal simulator - click numbers in ascending order. */
public class NumbersSim extends TermSimGUI {
    private final List<Integer> clickOrder = new ArrayList<>();
    private int currentClickIndex = 0;

    public NumbersSim(Inventory playerInventory) {
        super(TerminalType.NUMBERS, playerInventory);
    }

    @Override
    protected void initializeTerminal() {
        clickOrder.clear();
        currentClickIndex = 0;

        // Create list of slots in the terminal area (rows 1-2, columns 1-7)
        List<Integer> slots = new ArrayList<>();
        for (int row = 1; row <= 2; row++) {
            for (int col = 1; col <= 7; col++) {
                slots.add(row * 9 + col);
            }
        }

        // Shuffle the slots for random placement
        Collections.shuffle(slots);

        // Place red panes with stack counts 1-14 in random order
        for (int i = 0; i < Math.min(14, slots.size()); i++) {
            int slot = slots.get(i);
            int count = i + 1;
            setStack(slot, createNumberPane(count));
            clickOrder.add(slot);
        }
    }

    @Override
    protected Boolean handleTerminalClickWithResult(int slot, int button) {
        // Check if this is the correct next number to click
        if (currentClickIndex >= clickOrder.size()) {
            return null;
        }

        int expectedSlot = clickOrder.get(currentClickIndex);
        if (slot == expectedSlot) {
            // Turn clicked pane to LIME after a correct click
            ItemStack stack = getStack(slot);
            int count = stack.getCount();
            ItemStack limePane = new ItemStack(Items.LIME_STAINED_GLASS_PANE, count);
            limePane.set(DataComponents.CUSTOM_NAME, Component.literal(""));
            setStack(slot, limePane);

            currentClickIndex++;
            playClickSound(true);
            triggerVisualFeedback(true);
            return true;
        }

        if (clickOrder.contains(slot)) {
            playClickSound(false);
            triggerVisualFeedback(false);
        }

        return null;
    }

    @Override
    protected boolean checkCompletion() {
        return currentClickIndex >= clickOrder.size();
    }

    private ItemStack createNumberPane(int count) {
        ItemStack pane = new ItemStack(Items.RED_STAINED_GLASS_PANE, count);
        pane.set(DataComponents.CUSTOM_NAME, Component.literal(""));
        return pane;
    }
}
