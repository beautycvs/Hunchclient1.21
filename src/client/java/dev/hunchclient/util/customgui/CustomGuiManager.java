package dev.hunchclient.util.customgui;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * Manages CustomGui instances for HandledScreens
 * Uses WeakHashMap so GUIs are automatically cleaned up when screens are garbage collected
 *
 * This approach is more reliable than trying to add interfaces via Mixin
 */
public class CustomGuiManager {

    private static final Map<Screen, CustomGui> customGuis = new WeakHashMap<>();

    /**
     * Get the CustomGui for a screen (if any)
     */
    public static CustomGui getCustomGui(Screen screen) {
        return customGuis.get(screen);
    }

    /**
     * Get the CustomGui for a HandledScreen (if any)
     */
    public static CustomGui getCustomGui(AbstractContainerScreen<?> screen) {
        return customGuis.get(screen);
    }

    /**
     * Set or remove the CustomGui for a screen
     */
    public static void setCustomGui(Screen screen, CustomGui gui) {
        if (gui == null) {
            customGuis.remove(screen);
        } else {
            customGuis.put(screen, gui);
        }
    }

    /**
     * Set or remove the CustomGui for a HandledScreen
     */
    public static void setCustomGui(AbstractContainerScreen<?> screen, CustomGui gui) {
        if (gui == null) {
            customGuis.remove(screen);
        } else {
            customGuis.put(screen, gui);
        }
    }

    /**
     * Check if a screen has a CustomGui
     */
    public static boolean hasCustomGui(Screen screen) {
        return customGuis.containsKey(screen) && customGuis.get(screen) != null;
    }

    /**
     * Remove all CustomGuis (cleanup)
     */
    public static void clear() {
        customGuis.clear();
    }
}
