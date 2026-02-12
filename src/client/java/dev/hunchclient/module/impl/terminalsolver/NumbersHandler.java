package dev.hunchclient.module.impl.terminalsolver;

import meteordevelopment.orbit.IEventBus;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Handler for the Numbers terminal (click numbered panes in ascending order). */
public class NumbersHandler extends TerminalHandler {

    public NumbersHandler(IEventBus eventBus) {
        super(TerminalTypes.NUMBERS, eventBus);
    }

    // Numbers terminal allowed slots (where red panes can appear) - 2 rows only
    private static final int[] ALLOWED_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25
    };

    @Override
    public boolean handleSlotUpdate(ClientboundContainerSetSlotPacket packet) {
        // Wait until all ALLOWED items are loaded (not all slots, just the ones we care about!)
        List<Integer> nullSlots = new ArrayList<>();
        for (int slot : ALLOWED_SLOTS) {
            if (items[slot] == null) {
                nullSlots.add(slot);
            }
        }

        if (!nullSlots.isEmpty()) {
            return false;
        }

        List<Integer> result = solveNumbers(items);
        boolean changed = !solution.equals(result);
        solution.clear();
        solution.addAll(result);
        return changed;
    }

    @Override
    protected void simulateClickImpl(int slotIndex, int clickType) {
        // For Numbers terminal, we only remove if it's the first element (must click in order)
        // Use synchronized block to make the check-then-remove atomic
        synchronized (solution) {
            if (!solution.isEmpty() && solution.get(0).equals(slotIndex)) {
                solution.remove(0);
            }
        }
    }

    private List<Integer> solveNumbers(ItemStack[] items) {
        List<NumberSlot> redPanes = new ArrayList<>();

        for (int index = 0; index < items.length; index++) {
            ItemStack item = items[index];
            // CRITICAL: Exclude items with glint (already clicked) - like SA filters meta === 14
            if (item != null && item.getItem() == Items.RED_STAINED_GLASS_PANE && !item.hasFoil()) {
                redPanes.add(new NumberSlot(index, item.getCount()));
            }
        }

        // Sort by count
        redPanes.sort(Comparator.comparingInt(ns -> ns.count));

        List<Integer> result = new ArrayList<>();
        for (NumberSlot ns : redPanes) {
            result.add(ns.index);
        }
        return result;
    }

    private static class NumberSlot {
        int index;
        int count;

        NumberSlot(int index, int count) {
            this.index = index;
            this.count = count;
        }
    }
}
