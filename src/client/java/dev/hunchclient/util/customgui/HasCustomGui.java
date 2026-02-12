package dev.hunchclient.util.customgui;

/**
 * Interface implemented via Mixin on HandledScreen
 * Allows setting a custom GUI override
 */
public interface HasCustomGui {

    /**
     * Get the current custom GUI override
     */
    CustomGui getCustomGui();

    /**
     * Set a custom GUI override (or null to remove)
     */
    void setCustomGui(CustomGui gui);
}
