package dev.hunchclient.hud;

/**
 * Anchor points for HUD elements
 * Determines the origin point for positioning
 */
public enum HudAnchor {
    // Top row
    TOP_LEFT("Top Left", 0.0f, 0.0f),
    TOP_CENTER("Top Center", 0.5f, 0.0f),
    TOP_RIGHT("Top Right", 1.0f, 0.0f),

    // Middle row
    MIDDLE_LEFT("Middle Left", 0.0f, 0.5f),
    MIDDLE_CENTER("Center", 0.5f, 0.5f),
    MIDDLE_RIGHT("Middle Right", 1.0f, 0.5f),

    // Bottom row
    BOTTOM_LEFT("Bottom Left", 0.0f, 1.0f),
    BOTTOM_CENTER("Bottom Center", 0.5f, 1.0f),
    BOTTOM_RIGHT("Bottom Right", 1.0f, 1.0f);

    private final String displayName;
    private final float screenXFactor; // 0.0 = left, 0.5 = center, 1.0 = right
    private final float screenYFactor; // 0.0 = top, 0.5 = middle, 1.0 = bottom

    HudAnchor(String displayName, float screenXFactor, float screenYFactor) {
        this.displayName = displayName;
        this.screenXFactor = screenXFactor;
        this.screenYFactor = screenYFactor;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Calculate absolute X position on screen
     * @param relativeX Relative X offset (percentage 0-100)
     * @param screenWidth Screen width
     * @param elementWidth Element width
     * @return Absolute X position in pixels
     */
    public int calculateX(float relativeX, int screenWidth, int elementWidth) {
        // Start from anchor point
        int anchorX = (int)(screenWidth * screenXFactor);

        // Apply relative offset (convert percentage to pixels)
        int offsetX = (int)(screenWidth * (relativeX / 100.0f));

        // Adjust for element width based on anchor
        if (screenXFactor == 0.5f) {
            // Center anchor - offset from center
            return anchorX + offsetX - elementWidth / 2;
        } else if (screenXFactor == 1.0f) {
            // Right anchor - offset from right edge
            return anchorX + offsetX - elementWidth;
        } else {
            // Left anchor - offset from left edge
            return anchorX + offsetX;
        }
    }

    /**
     * Calculate absolute Y position on screen
     */
    public int calculateY(float relativeY, int screenHeight, int elementHeight) {
        // Start from anchor point
        int anchorY = (int)(screenHeight * screenYFactor);

        // Apply relative offset (convert percentage to pixels)
        int offsetY = (int)(screenHeight * (relativeY / 100.0f));

        // Adjust for element height based on anchor
        if (screenYFactor == 0.5f) {
            // Middle anchor - offset from middle
            return anchorY + offsetY - elementHeight / 2;
        } else if (screenYFactor == 1.0f) {
            // Bottom anchor - offset from bottom edge
            return anchorY + offsetY - elementHeight;
        } else {
            // Top anchor - offset from top edge
            return anchorY + offsetY;
        }
    }

    /**
     * Calculate relative position from absolute pixel position
     * @param absoluteX Absolute X in pixels
     * @param screenWidth Screen width
     * @param elementWidth Element width
     * @return Relative X (percentage 0-100)
     */
    public float calculateRelativeX(int absoluteX, int screenWidth, int elementWidth) {
        int anchorX = (int)(screenWidth * screenXFactor);

        // Adjust for element width based on anchor
        int adjustedX;
        if (screenXFactor == 0.5f) {
            adjustedX = absoluteX + elementWidth / 2;
        } else if (screenXFactor == 1.0f) {
            adjustedX = absoluteX + elementWidth;
        } else {
            adjustedX = absoluteX;
        }

        int offsetX = adjustedX - anchorX;
        return (offsetX / (float)screenWidth) * 100.0f;
    }

    /**
     * Calculate relative position from absolute pixel position
     */
    public float calculateRelativeY(int absoluteY, int screenHeight, int elementHeight) {
        int anchorY = (int)(screenHeight * screenYFactor);

        // Adjust for element height based on anchor
        int adjustedY;
        if (screenYFactor == 0.5f) {
            adjustedY = absoluteY + elementHeight / 2;
        } else if (screenYFactor == 1.0f) {
            adjustedY = absoluteY + elementHeight;
        } else {
            adjustedY = absoluteY;
        }

        int offsetY = adjustedY - anchorY;
        return (offsetY / (float)screenHeight) * 100.0f;
    }
}
