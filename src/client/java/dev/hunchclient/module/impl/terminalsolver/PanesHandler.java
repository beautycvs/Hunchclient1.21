package dev.hunchclient.module.impl.terminalsolver;

import meteordevelopment.orbit.IEventBus;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.ArrayList;
import java.util.List;

/** Handler for the Panes terminal (click red panes that are not already glinting). */
public class PanesHandler extends TerminalHandler {

    public PanesHandler(IEventBus eventBus) {
        super(TerminalTypes.PANES, eventBus);
    }

    @Override
    public boolean handleSlotUpdate(ClientboundContainerSetSlotPacket packet) {
        // null packet means full resync from SetContent - always recalculate
        if (packet != null && packet.getSlot() != type.windowSize - 1) return false;

        // Wait until all items are loaded (no nulls)
        for (int i = 0; i < items.length; i++) {
            if (items[i] == null) {
                return false; // Not ready yet
            }
        }

        // Solve synchronously - MUST be sync for queue validation to work!
        // (hasGlint filter will exclude clicked items automatically)
        List<Integer> result = solvePanes(items);
        solution.clear();
        solution.addAll(result);
        return true;
    }

    @Override
    protected void simulateClickImpl(int slotIndex, int clickType) {
        // Remove by value (not by index position) - atomic and thread-safe
        solution.remove(Integer.valueOf(slotIndex));
    }

    private List<Integer> solvePanes(ItemStack[] items) {
        List<Integer> result = new ArrayList<>();
        for (int index = 0; index < items.length; index++) {
            ItemStack item = items[index];
            // CRITICAL: Exclude items with glint (already clicked)
            if (item != null && item.getItem() == Items.RED_STAINED_GLASS_PANE && !item.hasFoil()) {
                result.add(index);
            }
        }
        return result;
    }
}
