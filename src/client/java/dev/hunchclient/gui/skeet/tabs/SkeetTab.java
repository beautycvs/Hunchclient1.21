package dev.hunchclient.gui.skeet.tabs;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Base class for all Skeet GUI tabs
 */
public abstract class SkeetTab {

    protected final String name;
    protected final String icon;
    protected int x, y, width, height;

    public SkeetTab(String name, String icon) {
        this.name = name;
        this.icon = icon;
    }

    /**
     * Render tab content
     */
    public abstract void render(GuiGraphics context, int mouseX, int mouseY, float delta);

    /**
     * Handle mouse click in tab content
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    /**
     * Handle mouse release in tab content
     */
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    /**
     * Handle mouse drag in tab content
     */
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return false;
    }

    /**
     * Handle mouse scroll in tab content
     */
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return false;
    }

    /**
     * Called when tab is selected
     */
    public void onSelected() {
    }

    /**
     * Called when tab is deselected
     */
    public void onDeselected() {
    }

    /**
     * Set content area bounds
     */
    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    // Getters
    public String getName() {
        return name;
    }

    public String getIcon() {
        return icon;
    }
}
