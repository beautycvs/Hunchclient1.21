package dev.hunchclient.util.customgui;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.inventory.Slot;

/**
 * Custom GUI interface that can override HandledScreen rendering
 * Based on Firmament's CustomGui system
 */
public abstract class CustomGui {

    /**
     * Get the bounding rectangles for this GUI (for click detection)
     */
    public abstract List<int[]> getBounds();

    /**
     * Called when the screen is initialized
     */
    public void onInit() {}

    /**
     * Render the custom GUI
     */
    public void render(GuiGraphics context, float delta, int mouseX, int mouseY) {}

    /**
     * Move a slot's visual position
     */
    public void moveSlot(Slot slot) {}

    /**
     * Called before rendering a slot
     */
    public void beforeSlotRender(GuiGraphics context, Slot slot) {}

    /**
     * Called after rendering a slot
     */
    public void afterSlotRender(GuiGraphics context, Slot slot) {}

    /**
     * Handle mouse scroll
     */
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return false;
    }

    /**
     * Handle mouse click
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    /**
     * Handle mouse release
     */
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    /**
     * Handle mouse drag
     */
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return false;
    }

    /**
     * Handle key press
     */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    /**
     * Handle character typed
     */
    public boolean charTyped(char chr, int modifiers) {
        return false;
    }

    /**
     * Check if a click is outside the GUI bounds
     */
    public boolean isClickOutsideBounds(double mouseX, double mouseY) {
        for (int[] rect : getBounds()) {
            if (mouseX >= rect[0] && mouseX < rect[0] + rect[2] &&
                mouseY >= rect[1] && mouseY < rect[1] + rect[3]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if a point is within bounds
     */
    public boolean isPointWithinBounds(int x, int y, int width, int height, double pointX, double pointY) {
        for (int[] rect : getBounds()) {
            if (pointX >= rect[0] && pointX < rect[0] + rect[2] &&
                pointY >= rect[1] && pointY < rect[1] + rect[3]) {
                return pointX >= x && pointX < x + width && pointY >= y && pointY < y + height;
            }
        }
        return false;
    }

    /**
     * Check if point is over a slot
     */
    public boolean isPointOverSlot(Slot slot, int xOffset, int yOffset, double pointX, double pointY) {
        return isPointWithinBounds(slot.x + xOffset, slot.y + yOffset, 16, 16, pointX, pointY);
    }

    /**
     * Whether to draw the foreground labels
     */
    public boolean shouldDrawForeground() {
        return true;
    }

    /**
     * Called when user tries to close the screen
     * Return false to prevent closing
     */
    public boolean onVoluntaryExit() {
        return true;
    }
}
