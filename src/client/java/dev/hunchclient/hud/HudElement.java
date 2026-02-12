package dev.hunchclient.hud;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Base interface for all HUD elements
 * Provides unified positioning, sizing, and rendering
 */
public interface HudElement {

    /**
     * Unique identifier for this element
     */
    String getId();

    /**
     * Display name shown in HUD editor
     */
    String getDisplayName();

    /**
     * Render this HUD element
     * @param context Draw context
     * @param x Calculated X position (pixels)
     * @param y Calculated Y position (pixels)
     * @param screenWidth Current screen width
     * @param screenHeight Current screen height
     * @param editMode Whether HUD editor is active
     */
    void render(GuiGraphics context, int x, int y, int screenWidth, int screenHeight, boolean editMode);

    /**
     * Get current width in pixels
     */
    int getWidth();

    /**
     * Get current height in pixels
     */
    int getHeight();

    /**
     * Get X position (0-100, percentage of screen width from anchor)
     */
    float getX();

    /**
     * Get Y position (0-100, percentage of screen height from anchor)
     */
    float getY();

    /**
     * Set X position
     */
    void setX(float x);

    /**
     * Set Y position
     */
    void setY(float y);

    /**
     * Set size
     */
    void setSize(int width, int height);

    /**
     * Get anchor point (determines origin for positioning)
     */
    HudAnchor getAnchor();

    /**
     * Set anchor point
     */
    void setAnchor(HudAnchor anchor);

    /**
     * Whether this element is currently enabled/visible
     */
    boolean isEnabled();

    /**
     * Set enabled state
     */
    void setEnabled(boolean enabled);

    /**
     * Whether this element is locked (can't be moved in editor)
     */
    boolean isLocked();

    /**
     * Set locked state
     */
    void setLocked(boolean locked);

    /**
     * Z-order (higher = rendered on top)
     */
    int getZOrder();

    /**
     * Set Z-order
     */
    void setZOrder(int zOrder);

    /**
     * Whether this element can be resized
     */
    default boolean isResizable() {
        return true;
    }

    /**
     * Minimum width (for resizing)
     */
    default int getMinWidth() {
        return 50;
    }

    /**
     * Minimum height (for resizing)
     */
    default int getMinHeight() {
        return 20;
    }

    /**
     * Maximum width (for resizing, -1 = no limit)
     */
    default int getMaxWidth() {
        return -1;
    }

    /**
     * Maximum height (for resizing, -1 = no limit)
     */
    default int getMaxHeight() {
        return -1;
    }

    /**
     * Save to JSON
     */
    JsonObject saveToJson();

    /**
     * Load from JSON
     */
    void loadFromJson(JsonObject json);
}
