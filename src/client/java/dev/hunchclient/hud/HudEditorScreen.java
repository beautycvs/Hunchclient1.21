package dev.hunchclient.hud;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Screen for editing HUD layout
 * Shows all HUD elements with edit overlay and controls
 */
public class HudEditorScreen extends Screen {

    private final HudEditorManager manager;
    private final Screen parent;

    public HudEditorScreen(Screen parent) {
        super(Component.literal("HUD Editor"));
        this.parent = parent;
        this.manager = HudEditorManager.getInstance();
    }

    @Override
    protected void init() {
        super.init();
        manager.setEditMode(true);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        // Render semi-transparent background (without blur to avoid "Can only blur once per frame" crash)
        context.fill(0, 0, width, height, 0x80000000);

        // Render all HUD elements with edit overlay
        manager.render(context, width, height);

        // Render help text
        renderHelpText(context);

        // Render element info panel if element is selected
        if (manager.getSelectedElement() != null) {
            renderElementInfo(context, manager.getSelectedElement());
        }

        super.render(context, mouseX, mouseY, delta);
    }

    /**
     * Render help text at top of screen
     */
    private void renderHelpText(GuiGraphics context) {
        String[] helpLines = {
            "§eHUD Editor",
            "§7Click and drag elements to move",
            "§7Drag corners/edges to resize",
            "§7Hold Shift: Fine adjustment",
            "§7L: Lock/Unlock | G: Toggle Grid | ESC: Save & Exit"
        };

        int y = 10;
        for (String line : helpLines) {
            context.drawString(font, line, 10, y, 0xFFFFFFFF, true);
            y += 10;
        }
    }

    /**
     * Render info panel for selected element
     */
    private void renderElementInfo(GuiGraphics context, HudElement element) {
        int panelX = width - 210;
        int panelY = 10;
        int panelWidth = 200;
        int panelHeight = 120;

        // Background
        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xD0000000);
        context.fill(panelX, panelY, panelX + panelWidth, panelY + 1, 0xFF00FF00); // Top border

        int textY = panelY + 5;
        int textX = panelX + 5;

        // Element info
        context.drawString(font, "§a" + element.getDisplayName(), textX, textY, 0xFFFFFFFF, false);
        textY += 12;

        context.drawString(font, "§7Position:", textX, textY, 0xFFFFFFFF, false);
        textY += 10;
        context.drawString(font, String.format("  X: %.1f%% Y: %.1f%%", element.getX(), element.getY()), textX, textY, 0xFFFFFFFF, false);
        textY += 10;

        context.drawString(font, "§7Size:", textX, textY, 0xFFFFFFFF, false);
        textY += 10;
        context.drawString(font, String.format("  %d x %d", element.getWidth(), element.getHeight()), textX, textY, 0xFFFFFFFF, false);
        textY += 10;

        context.drawString(font, "§7Anchor:", textX, textY, 0xFFFFFFFF, false);
        textY += 10;
        context.drawString(font, "  " + element.getAnchor().getDisplayName(), textX, textY, 0xFFFFFFFF, false);
        textY += 10;

        context.drawString(font, "§7Z-Order: " + element.getZOrder(), textX, textY, 0xFFFFFFFF, false);
        textY += 10;

        if (element.isLocked()) {
            context.drawString(font, "§c[LOCKED]", textX, textY, 0xFFFF0000, false);
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        if (manager.handleMouseClick(click.x(), click.y(), click.button())) {
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent click) {
        manager.handleMouseRelease(click.x(), click.y(), click.button());
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent click, double deltaX, double deltaY) {
        manager.handleMouseDrag(click.x(), click.y());
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent keyInput) {
        int keyCode = keyInput.key();  // Use key() field
        HudElement selected = manager.getSelectedElement();

        // L - Lock/Unlock
        if (keyCode == 76 && selected != null) { // L key
            selected.setLocked(!selected.isLocked());
            return true;
        }

        // G - Toggle grid
        if (keyCode == 71) { // G key
            manager.setShowGrid(!manager.isShowGrid());
            return true;
        }

        // Arrow keys - fine movement
        if (selected != null && !selected.isLocked()) {
            // Check if shift is pressed (modifiers bit 1 = Shift)
            boolean shiftPressed = (keyInput.modifiers() & 1) != 0;
            int moveAmount = shiftPressed ? 1 : manager.getGridSize(); // Shift = 1px, else grid size

            switch (keyCode) {
                case 262: // Right
                    selected.setX(selected.getX() + moveAmount * 0.1f);
                    return true;
                case 263: // Left
                    selected.setX(selected.getX() - moveAmount * 0.1f);
                    return true;
                case 264: // Down
                    selected.setY(selected.getY() + moveAmount * 0.1f);
                    return true;
                case 265: // Up
                    selected.setY(selected.getY() - moveAmount * 0.1f);
                    return true;
            }
        }

        // ESC - Save and exit
        if (keyCode == 256) { // ESC
            this.onClose();
            return true;
        }

        return super.keyPressed(keyInput);
    }

    @Override
    public void onClose() {
        manager.setEditMode(false);
        manager.saveConfig();
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false; // Don't pause game
    }
}
