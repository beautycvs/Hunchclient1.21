package dev.hunchclient.render;

/**
 * Global scissor state tracker for NVG custom font rendering.
 * Tracks scissor bounds that are set via GuiGraphics.enableScissor()
 * so they can be captured and applied to NVG text rendering.
 *
 * This solves the issue where GL scissor state isn't reliably captured
 * because of deferred/batched rendering or state management differences.
 */
public class ScissorStateTracker {

    // Current scissor bounds [x1, y1, x2, y2] in screen coordinates (same as enableScissor params)
    private static int[] currentBounds = null;

    // Scissor stack depth (for nested scissor calls)
    private static int depth = 0;

    /**
     * Called when GuiGraphics.enableScissor is invoked
     * @param x1 Left edge
     * @param y1 Top edge
     * @param x2 Right edge
     * @param y2 Bottom edge
     */
    public static void pushScissor(int x1, int y1, int x2, int y2) {
        depth++;
        // Only track outermost scissor for simplicity (NVG handles one scissor at a time)
        if (currentBounds == null) {
            currentBounds = new int[]{x1, y1, x2, y2};
        }
    }

    /**
     * Called when GuiGraphics.disableScissor is invoked
     */
    public static void popScissor() {
        depth--;
        if (depth <= 0) {
            currentBounds = null;
            depth = 0;
        }
    }

    /**
     * Get current scissor bounds if any are active
     * @return [x1, y1, x2, y2] in screen coordinates, or null if no scissor active
     */
    public static int[] getCurrentBounds() {
        return currentBounds;
    }

    /**
     * Check if scissor is currently active
     */
    public static boolean isActive() {
        return currentBounds != null;
    }

    /**
     * Reset state (e.g., at frame start)
     */
    public static void reset() {
        currentBounds = null;
        depth = 0;
    }
}
