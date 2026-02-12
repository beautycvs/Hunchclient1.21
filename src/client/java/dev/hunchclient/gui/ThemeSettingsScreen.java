package dev.hunchclient.gui;

import dev.hunchclient.gui.skeet.SkeetTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Theme settings GUI with proper click alignment
 */
public class ThemeSettingsScreen extends Screen {

    private final GuiSettings guiSettings = GuiSettings.getInstance();
    private final Minecraft client = Minecraft.getInstance();
    private final Screen parent;

    // Window dimensions
    private int windowX;
    private int windowY;
    private final int windowWidth = 450;
    private final int windowHeight = 550;

    // Theme data
    private final String[] themes = {"dark", "light", "purple", "blue", "red", "green", "sunset", "midnight", "neon", "ocean"};
    private final String[] themeDescriptions = {
        "Classic dark theme",
        "Clean light theme",
        "Royal purple theme",
        "Ocean blue theme",
        "Crimson red theme",
        "Forest green theme",
        "Warm sunset colors",
        "Deep midnight blue",
        "Bright neon colors",
        "Deep ocean theme"
    };

    // Scroll state
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private boolean scrolling = false;

    // GUI Scale slider
    private boolean draggingScale = false;
    private float tempGuiScale;
    private int scaleSliderX, scaleSliderY, scaleSliderWidth;

    // Font Size slider
    private boolean draggingFontSize = false;
    private float tempFontSize;
    private int fontSliderX, fontSliderY, fontSliderWidth;

    // Theme buttons
    private int[] themeButtonY;
    private boolean[] themeHovered;

    // Animations
    private long openTime;
    private float fadeAnimation = 0f;

    public ThemeSettingsScreen(Screen parent) {
        super(Component.literal("Theme Settings"));
        this.parent = parent;
        this.font = Minecraft.getInstance().font;
        this.themeButtonY = new int[themes.length];
        this.themeHovered = new boolean[themes.length];
    }

    @Override
    protected void init() {
        super.init();

        // Center the window
        windowX = (width - windowWidth) / 2;
        windowY = (height - windowHeight) / 2;

        tempGuiScale = guiSettings.getGuiScale();
        tempFontSize = guiSettings.getFontSize();

        openTime = System.currentTimeMillis();

        // Calculate slider positions
        scaleSliderX = windowX + 20;
        scaleSliderWidth = windowWidth - 40;

        fontSliderX = windowX + 20;
        fontSliderWidth = windowWidth - 40;

        // Calculate max scroll
        int contentHeight = themes.length * 45 + 300; // Approximate content height
        maxScroll = Math.max(0, contentHeight - windowHeight + 60);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        // Update fade animation
        fadeAnimation = Math.min(1.0f, (System.currentTimeMillis() - openTime) / 200f);
        int alpha = (int)(fadeAnimation * 255);

        // Dark overlay background
        context.fill(0, 0, width, height, (alpha / 3) << 24 | 0x000000);

        // Main window background
        context.fill(windowX, windowY, windowX + windowWidth, windowY + windowHeight,
                    applyAlpha(SkeetTheme.BG_PRIMARY(), alpha));

        // Window border
        drawBorder(context, windowX, windowY, windowWidth, windowHeight,
                  applyAlpha(SkeetTheme.BORDER_DEFAULT(), alpha), 2);

        // Title bar
        context.fill(windowX + 2, windowY + 2, windowX + windowWidth - 2, windowY + 40,
                    applyAlpha(SkeetTheme.BG_SECONDARY(), alpha));

        // Title text
        String title = "THEME SETTINGS";
        int titleWidth = font.width(title);
        int titleX = windowX + (windowWidth - titleWidth) / 2;
        context.drawString(font, title, titleX, windowY + 15,
                         applyAlpha(SkeetTheme.ACCENT_PRIMARY(), alpha), false);

        // Close button (X)
        int closeX = windowX + windowWidth - 30;
        int closeY = windowY + 12;
        boolean closeHovered = isInBounds(mouseX, mouseY, closeX, closeY, 20, 20);

        String closeText = "X";
        int closeTextColor = applyAlpha(closeHovered ? SkeetTheme.STATUS_ERROR() : SkeetTheme.TEXT_DIM(), alpha);
        context.drawString(font, closeText, closeX + 5, closeY + 3, closeTextColor, false);

        // Content area with scissor for scrolling
        int contentY = windowY + 45;
        int contentHeight = windowHeight - 50;

        // Ensure scissor bounds are within window
        int scissorLeft = Math.max(0, windowX + 5);
        int scissorTop = Math.max(0, contentY);
        int scissorRight = Math.min(width, windowX + windowWidth - 5);
        int scissorBottom = Math.min(height, windowY + windowHeight - 5);

        context.enableScissor(scissorLeft, scissorTop, scissorRight, scissorBottom);

        int yOffset = contentY - scrollOffset;

        // Current theme header
        yOffset += 10;
        context.drawString(font, "CURRENT THEME", windowX + 20, yOffset,
                        applyAlpha(SkeetTheme.TEXT_PRIMARY(), alpha), false);
        yOffset += 15;

        context.drawString(font, guiSettings.getSelectedTheme().toUpperCase(),
                        windowX + 20, yOffset, applyAlpha(SkeetTheme.ACCENT_PRIMARY(), alpha), false);
        yOffset += 25;

        // Color preview boxes
        drawColorPreview(context, windowX + 20, yOffset, alpha);
        yOffset += 45;

        // Separator line
        drawSeparator(context, windowX + 20, yOffset, windowWidth - 40, alpha);
        yOffset += 15;

        // Available themes header
        context.drawString(font, "AVAILABLE THEMES", windowX + 20, yOffset,
                        applyAlpha(SkeetTheme.TEXT_PRIMARY(), alpha), false);
        yOffset += 25;

        // Theme list with proper click zones
        for (int i = 0; i < themes.length; i++) {
            boolean isSelected = guiSettings.getSelectedTheme().equals(themes[i]);

            // Store button Y position for click detection
            themeButtonY[i] = yOffset;

            // Check hover state with proper bounds
            themeHovered[i] = isInBounds(mouseX, mouseY, windowX + 20, yOffset, windowWidth - 40, 40);

            // Theme box background
            int bgColor = isSelected ? SkeetTheme.BG_ACTIVE() :
                         (themeHovered[i] ? SkeetTheme.BG_HOVER() : SkeetTheme.BG_SECONDARY());
            context.fill(windowX + 20, yOffset, windowX + windowWidth - 20, yOffset + 40,
                        applyAlpha(bgColor, alpha));

            // Selection indicator bar
            if (isSelected) {
                context.fill(windowX + 20, yOffset, windowX + 24, yOffset + 40,
                            applyAlpha(SkeetTheme.ACCENT_PRIMARY(), alpha));
            }

            // Theme name
            int nameColor = isSelected ? SkeetTheme.ACCENT_PRIMARY() : SkeetTheme.TEXT_PRIMARY();
            context.drawString(font, themes[i].toUpperCase(), windowX + 35, yOffset + 8,
                            applyAlpha(nameColor, alpha), false);

            // Theme description
            context.drawString(font, themeDescriptions[i], windowX + 35, yOffset + 22,
                            applyAlpha(SkeetTheme.TEXT_SECONDARY(), alpha), false);

            yOffset += 45;
        }

        // Settings section separator
        yOffset += 10;
        drawSeparator(context, windowX + 20, yOffset, windowWidth - 40, alpha);
        yOffset += 15;

        context.drawString(font, "SETTINGS", windowX + 20, yOffset,
                        applyAlpha(SkeetTheme.TEXT_PRIMARY(), alpha), false);
        yOffset += 25;

        // GUI Scale slider with proper click zone
        context.drawString(font, "GUI Scale: " + String.format("%.1f", tempGuiScale),
                        windowX + 20, yOffset, applyAlpha(SkeetTheme.TEXT_PRIMARY(), alpha), false);
        yOffset += 20;

        scaleSliderY = yOffset;
        drawSlider(context, scaleSliderX, scaleSliderY, scaleSliderWidth,
                  tempGuiScale, 0.5f, 2.0f, draggingScale, alpha);
        yOffset += 25;

        // Font Size slider with proper click zone
        context.drawString(font, "Font Size: " + String.format("%.1f", tempFontSize),
                        windowX + 20, yOffset, applyAlpha(SkeetTheme.TEXT_PRIMARY(), alpha), false);
        yOffset += 20;

        fontSliderY = yOffset;
        drawSlider(context, fontSliderX, fontSliderY, fontSliderWidth,
                  tempFontSize, 0.5f, 2.0f, draggingFontSize, alpha);
        yOffset += 25;

        // Custom colors toggle
        String customStatus = guiSettings.isCustomColorsEnabled() ? "ENABLED" : "DISABLED";
        int customColor = guiSettings.isCustomColorsEnabled() ? SkeetTheme.STATUS_SUCCESS() : SkeetTheme.TEXT_DIM();
        context.drawString(font, "Custom Colors: " + customStatus,
                        windowX + 20, yOffset, applyAlpha(customColor, alpha), false);
        context.drawString(font, "(Right-click a theme to toggle)",
                        windowX + 20, yOffset + 14, applyAlpha(SkeetTheme.TEXT_SECONDARY(), alpha), false);
        yOffset += 35;

        // Font selection
        context.drawString(font, "Font: " + guiSettings.getSelectedFont(),
                        windowX + 20, yOffset, applyAlpha(SkeetTheme.TEXT_PRIMARY(), alpha), false);
        context.drawString(font, "(Click to cycle fonts)",
                        windowX + 20, yOffset + 14, applyAlpha(SkeetTheme.TEXT_SECONDARY(), alpha), false);

        context.disableScissor();

        // Draw scrollbar if needed
        if (maxScroll > 0) {
            int scrollbarX = windowX + windowWidth - 10;
            int scrollbarY = contentY;
            int scrollbarHeight = contentHeight;

            // Scrollbar background
            context.fill(scrollbarX, scrollbarY, scrollbarX + 6, scrollbarY + scrollbarHeight,
                        applyAlpha(SkeetTheme.BG_SECONDARY(), alpha));

            // Scrollbar thumb
            float scrollPercent = (float)scrollOffset / maxScroll;
            int thumbHeight = Math.max(30, scrollbarHeight / 5);
            int thumbY = scrollbarY + (int)(scrollPercent * (scrollbarHeight - thumbHeight));

            context.fill(scrollbarX, thumbY, scrollbarX + 6, thumbY + thumbHeight,
                        applyAlpha(SkeetTheme.ACCENT_PRIMARY(), alpha));
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void drawColorPreview(GuiGraphics context, int x, int y, int alpha) {
        int boxSize = 25;
        int spacing = 8;

        // Background color
        context.fill(x, y, x + boxSize, y + boxSize, applyAlpha(SkeetTheme.BG_PRIMARY(), alpha));
        drawBorder(context, x, y, boxSize, boxSize, applyAlpha(SkeetTheme.BORDER_DEFAULT(), alpha), 1);

        // Accent color
        x += boxSize + spacing;
        context.fill(x, y, x + boxSize, y + boxSize, applyAlpha(SkeetTheme.ACCENT_PRIMARY(), alpha));
        drawBorder(context, x, y, boxSize, boxSize, applyAlpha(SkeetTheme.BORDER_DEFAULT(), alpha), 1);

        // Secondary background
        x += boxSize + spacing;
        context.fill(x, y, x + boxSize, y + boxSize, applyAlpha(SkeetTheme.BG_SECONDARY(), alpha));
        drawBorder(context, x, y, boxSize, boxSize, applyAlpha(SkeetTheme.BORDER_DEFAULT(), alpha), 1);

        // Active background
        x += boxSize + spacing;
        context.fill(x, y, x + boxSize, y + boxSize, applyAlpha(SkeetTheme.BG_ACTIVE(), alpha));
        drawBorder(context, x, y, boxSize, boxSize, applyAlpha(SkeetTheme.BORDER_DEFAULT(), alpha), 1);

        // Text primary
        x += boxSize + spacing;
        context.fill(x, y, x + boxSize, y + boxSize, applyAlpha(0xFF1a1a1a, alpha));
        context.drawString(font, "A", x + 8, y + 8, applyAlpha(SkeetTheme.TEXT_PRIMARY(), alpha), false);

        // Text secondary
        x += boxSize + spacing;
        context.fill(x, y, x + boxSize, y + boxSize, applyAlpha(0xFF1a1a1a, alpha));
        context.drawString(font, "a", x + 9, y + 8, applyAlpha(SkeetTheme.TEXT_SECONDARY(), alpha), false);
    }

    private void drawSlider(GuiGraphics context, int x, int y, int width, float value,
                           float min, float max, boolean dragging, int alpha) {
        int sliderHeight = 6;

        // Background track
        context.fill(x, y, x + width, y + sliderHeight, applyAlpha(SkeetTheme.BG_FIELD(), alpha));

        // Filled portion
        float normalized = (value - min) / (max - min);
        int filledWidth = (int)(normalized * width);
        context.fill(x, y, x + filledWidth, y + sliderHeight, applyAlpha(SkeetTheme.ACCENT_PRIMARY(), alpha));

        // Thumb handle
        int thumbX = x + filledWidth - 5;
        int thumbY = y - 3;
        int thumbSize = 12;

        int thumbColor = dragging ? SkeetTheme.lighter(SkeetTheme.ACCENT_PRIMARY(), 0.3f) : SkeetTheme.ACCENT_PRIMARY();
        context.fill(thumbX, thumbY, thumbX + thumbSize, thumbY + thumbSize, applyAlpha(thumbColor, alpha));
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        // Close button with proper bounds
        int closeX = windowX + windowWidth - 30;
        int closeY = windowY + 12;
        if (isInBounds(mouseX, mouseY, closeX, closeY, 20, 20)) {
            onClose();
            return true;
        }

        // Theme selection with proper bounds
        for (int i = 0; i < themes.length; i++) {
            if (themeButtonY[i] > 0 && isInBounds(mouseX, mouseY, windowX + 20, themeButtonY[i], windowWidth - 40, 40)) {
                if (button == 0) { // Left click - select theme
                    guiSettings.setSelectedTheme(themes[i]);
                } else if (button == 1) { // Right click - toggle custom colors
                    guiSettings.setCustomColorsEnabled(!guiSettings.isCustomColorsEnabled());
                }
                return true;
            }
        }

        // GUI Scale slider with proper bounds
        if (isInBounds(mouseX, mouseY, scaleSliderX, scaleSliderY - 5, scaleSliderWidth, 16)) {
            draggingScale = true;
            updateSliderValue(mouseX, true);
            return true;
        }

        // Font Size slider with proper bounds
        if (isInBounds(mouseX, mouseY, fontSliderX, fontSliderY - 5, fontSliderWidth, 16)) {
            draggingFontSize = true;
            updateSliderValue(mouseX, false);
            return true;
        }

        // Font selection (last section)
        int fontY = windowY + windowHeight - 100; // Approximate position
        if (isInBounds(mouseX, mouseY, windowX + 20, fontY, 200, 30)) {
            cycleFont();
            return true;
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent click, double deltaX, double deltaY) {
        double mouseX = click.x();

        if (draggingScale || draggingFontSize) {
            updateSliderValue(mouseX, draggingScale);
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent click) {
        if (draggingScale) {
            draggingScale = false;
            guiSettings.setGuiScale(tempGuiScale);
            return true;
        }
        if (draggingFontSize) {
            draggingFontSize = false;
            guiSettings.setFontSize(tempFontSize);
            return true;
        }
        scrolling = false;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (isInBounds(mouseX, mouseY, windowX, windowY, windowWidth, windowHeight)) {
            scrollOffset -= (int)(verticalAmount * 25);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private boolean isInBounds(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width &&
               mouseY >= y && mouseY <= y + height;
    }

    private void updateSliderValue(double mouseX, boolean isScale) {
        float normalized = (float)((mouseX - (isScale ? scaleSliderX : fontSliderX)) /
                                   (isScale ? scaleSliderWidth : fontSliderWidth));
        normalized = Math.max(0, Math.min(1, normalized));

        if (isScale) {
            tempGuiScale = 0.5f + normalized * 1.5f; // 0.5 to 2.0
        } else {
            tempFontSize = 0.5f + normalized * 1.5f; // 0.5 to 2.0
        }
    }

    private void cycleFont() {
        // Get all available fonts from CustomFontLoader
        CustomFontLoader loader = CustomFontLoader.getInstance();
        String[] fonts = loader.getAvailableFonts();

        if (fonts.length == 0) {
            // Fallback if no fonts are loaded
            fonts = new String[]{"default"};
        }

        String current = guiSettings.getSelectedFont();
        int currentIndex = 0;
        for (int i = 0; i < fonts.length; i++) {
            if (fonts[i].equals(current)) {
                currentIndex = i;
                break;
            }
        }
        int nextIndex = (currentIndex + 1) % fonts.length;
        guiSettings.setSelectedFont(fonts[nextIndex]);
    }

    @Override
    public void onClose() {
        // Save settings when closing
        guiSettings.setGuiScale(tempGuiScale);
        guiSettings.setFontSize(tempFontSize);

        if (parent != null) {
            minecraft.setScreen(parent);
        } else {
            super.onClose();
        }
    }

    private void drawBorder(GuiGraphics context, int x, int y, int width, int height, int color, int thickness) {
        // Top
        context.fill(x, y, x + width, y + thickness, color);
        // Bottom
        context.fill(x, y + height - thickness, x + width, y + height, color);
        // Left
        context.fill(x, y, x + thickness, y + height, color);
        // Right
        context.fill(x + width - thickness, y, x + width, y + height, color);
    }

    private void drawSeparator(GuiGraphics context, int x, int y, int width, int alpha) {
        context.fill(x, y, x + width, y + 1, applyAlpha(SkeetTheme.BORDER_DEFAULT(), alpha));
    }

    private int applyAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }
}