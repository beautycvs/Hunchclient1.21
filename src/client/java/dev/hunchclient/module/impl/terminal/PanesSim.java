package dev.hunchclient.module.impl.terminal;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Panes terminal simulator - click all red panes to turn them green. */
public class PanesSim extends TermSimGUI {

    public PanesSim(Inventory playerInventory) {
        super(TerminalType.PANES, playerInventory);
    }

    @Override
    protected void initializeTerminal() {
        // Fill the terminal area (rows 1-3, columns 2-6) with red/green panes
        for (int row = 1; row <= 3; row++) {
            for (int col = 2; col <= 6; col++) {
                int slot = row * 9 + col;
                // 25% chance for green pane, 75% chance for red pane
                if (Math.random() > 0.75) {
                    setStack(slot, createGreenPane());
                } else {
                    setStack(slot, createRedPane());
                }
            }
        }
    }

    @Override
    protected Boolean handleTerminalClickWithResult(int slot, int button) {
        ItemStack stack = getStack(slot);
        if (stack.isEmpty()) {
            return null;
        }

        if (stack.getItem() == Items.RED_STAINED_GLASS_PANE) {
            setStack(slot, createGreenPane());
            playClickSound(true);
            triggerVisualFeedback(true);
            return true;
        } else if (stack.getItem() == Items.LIME_STAINED_GLASS_PANE) {
            setStack(slot, createRedPane());
            playClickSound(false);
            triggerVisualFeedback(false);
            return true;
        }

        return null;
    }

    @Override
    protected boolean checkCompletion() {
        // Check if all red panes are gone
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = getStack(i);
            if (stack.getItem() == Items.RED_STAINED_GLASS_PANE) {
                return false;
            }
        }
        return true;
    }

    private ItemStack createGreenPane() {
        ItemStack pane = new ItemStack(Items.LIME_STAINED_GLASS_PANE);
        pane.set(DataComponents.CUSTOM_NAME, Component.literal(""));
        return pane;
    }

    private ItemStack createRedPane() {
        ItemStack pane = new ItemStack(Items.RED_STAINED_GLASS_PANE);
        pane.set(DataComponents.CUSTOM_NAME, Component.literal(""));
        return pane;
    }
}
