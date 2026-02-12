package dev.hunchclient.mixininterface;

import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;

/**
 * Accessor for HandledScreen to get the focused (hovered) slot
 */
public interface IHandledScreen {
    @Nullable
    Slot hunchclient$getFocusedSlot();
}
