package dev.hunchclient.gui.skeet.components;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Base class for all Skeet UI components
 */
public abstract class SkeetComponent {

    protected int x, y;
    protected int width, height;
    protected boolean visible = true;
    protected boolean enabled = true;
    protected boolean hovered = false;

    public SkeetComponent(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /**
     * Render the component
     */
    public abstract void render(GuiGraphics context, int mouseX, int mouseY, float delta);

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
     * Handle mouse scroll
     */
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
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
     * Check if mouse is over component
     */
    public boolean isHovered(double mouseX, double mouseY) {
        return visible && mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    /**
     * Update hover state
     */
    public void updateHover(double mouseX, double mouseY) {
        hovered = isHovered(mouseX, mouseY);
    }

    // Getters and setters
    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void setSize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public boolean isVisible() { return visible; }
    public void setVisible(boolean visible) { this.visible = visible; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean isHovered() { return hovered; }
}
