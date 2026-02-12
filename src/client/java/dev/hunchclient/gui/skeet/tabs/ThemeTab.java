package dev.hunchclient.gui.skeet.tabs;

import dev.hunchclient.gui.skeet.SkeetTheme;
import dev.hunchclient.gui.skeet.components.SkeetSettingsRenderer;
import dev.hunchclient.module.ModuleManager;
import dev.hunchclient.module.impl.SkeetThemeModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Theme customization tab for SkeetScreen GUI
 * Allows customization of all GUI colors through SkeetThemeModule
 */
public class ThemeTab extends SkeetTab {

    private final Font textRenderer;
    private final SkeetSettingsRenderer settingsRenderer;
    private int scrollOffset = 0;
    private boolean isDragging = false;

    public ThemeTab() {
        super("Theme", "🎨");
        this.textRenderer = Minecraft.getInstance().font;

        // Get the SkeetThemeModule
        SkeetThemeModule themeModule = ModuleManager.getInstance().getModule(SkeetThemeModule.class);
        if (themeModule == null) {
            throw new IllegalStateException("SkeetThemeModule not found! Make sure it's registered in HunchModClient.");
        }

        // Create settings renderer for the theme module
        this.settingsRenderer = new SkeetSettingsRenderer(themeModule);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        if (x == 0 || y == 0 || width == 0 || height == 0) {
            return; // Not initialized yet
        }

        // Draw title
        context.drawString(textRenderer, "GUI Theme Colors", x + 10, y + 10, SkeetTheme.TEXT_PRIMARY(), false);
        context.drawString(textRenderer, "Customize the appearance of the GUI", x + 10, y + 25, SkeetTheme.TEXT_SECONDARY(), false);

        // Draw separator
        context.fill(x, y + 40, x + width, y + 41, SkeetTheme.BORDER_DEFAULT());

        // Render settings with scroll offset
        int settingsY = y + 50 - scrollOffset;
        int settingsHeight = settingsRenderer.getContentHeight();

        // Enable scissor for scrolling area
        enableScissor(context, x, y + 45, width, height - 50);
        settingsRenderer.render(context, x, settingsY, width, mouseX, mouseY, delta);
        disableScissor(context);

        // Draw scrollbar if needed
        if (settingsHeight > height - 50) {
            drawScrollbar(context, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Check if clicked on scrollbar
        int scrollbarX = x + width - 6;
        int scrollbarHeight = height - 50;
        int contentHeight = settingsRenderer.getContentHeight();

        if (contentHeight > scrollbarHeight && mouseX >= scrollbarX && mouseX <= scrollbarX + 4) {
            isDragging = true;
            return true;
        }

        // Forward to settings renderer with ABSOLUTE screen coordinates
        // The renderer handles coordinate checking internally
        return settingsRenderer.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        isDragging = false;

        // Forward to settings renderer with ABSOLUTE screen coordinates
        return settingsRenderer.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (isDragging) {
            // Update scroll based on drag
            int scrollbarHeight = height - 50;
            int contentHeight = settingsRenderer.getContentHeight();
            int maxScroll = Math.max(0, contentHeight - scrollbarHeight);

            // Calculate scroll from mouse position
            double relativeY = mouseY - (y + 45);
            double scrollRatio = relativeY / scrollbarHeight;
            scrollOffset = (int) (scrollRatio * maxScroll);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
            return true;
        }

        // Forward to settings renderer with correct relative coordinates
        double relativeMouseY = (mouseY - (y + 50)) + scrollOffset;
        return settingsRenderer.mouseDragged(mouseX, relativeMouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // Check if mouse is within tab bounds
        if (mouseX < x || mouseX > x + width || mouseY < y || mouseY > y + height) {
            return false;
        }

        int scrollbarHeight = height - 50;
        int contentHeight = settingsRenderer.getContentHeight();
        int maxScroll = Math.max(0, contentHeight - scrollbarHeight);

        if (maxScroll > 0) {
            scrollOffset -= (int) (verticalAmount * 20);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
            return true;
        }

        return false;
    }

    private void drawScrollbar(GuiGraphics context, int mouseX, int mouseY) {
        int scrollbarX = x + width - 6;
        int scrollbarY = y + 45;
        int scrollbarHeight = height - 50;
        int contentHeight = settingsRenderer.getContentHeight();

        // Calculate scrollbar thumb size and position
        float thumbRatio = (float) scrollbarHeight / contentHeight;
        int thumbHeight = Math.max(20, (int) (scrollbarHeight * thumbRatio));
        int maxScroll = Math.max(0, contentHeight - scrollbarHeight);
        float scrollRatio = maxScroll > 0 ? (float) scrollOffset / maxScroll : 0;
        int thumbY = scrollbarY + (int) ((scrollbarHeight - thumbHeight) * scrollRatio);

        // Draw scrollbar track
        context.fill(scrollbarX, scrollbarY, scrollbarX + 4, scrollbarY + scrollbarHeight, SkeetTheme.BG_FIELD());

        // Draw scrollbar thumb
        boolean hovered = mouseX >= scrollbarX && mouseX <= scrollbarX + 4 && mouseY >= thumbY && mouseY <= thumbY + thumbHeight;
        int thumbColor = hovered || isDragging ? SkeetTheme.ACCENT_PRIMARY() : SkeetTheme.TEXT_DIM();
        context.fill(scrollbarX, thumbY, scrollbarX + 4, thumbY + thumbHeight, thumbColor);
    }

    private void enableScissor(GuiGraphics context, int x, int y, int width, int height) {
        // Ensure scissor bounds are within screen bounds
        Minecraft client = Minecraft.getInstance();
        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();

        int scissorLeft = Math.max(0, x);
        int scissorTop = Math.max(0, y);
        int scissorRight = Math.min(screenWidth, x + width);
        int scissorBottom = Math.min(screenHeight, y + height);

        // Enable scissor with bounds clamping
        context.enableScissor(scissorLeft, scissorTop, scissorRight, scissorBottom);
    }

    private void disableScissor(GuiGraphics context) {
        context.disableScissor();
    }

    /**
     * Forward char typed events to settings renderer (for text fields)
     */
    public boolean charTyped(char chr, int modifiers) {
        return settingsRenderer.charTyped(chr, modifiers);
    }

    /**
     * Forward key press events to settings renderer (for text fields)
     */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return settingsRenderer.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onDeselected() {
        // Unfocus any text fields when leaving tab
        settingsRenderer.unfocusAll();
    }
}
