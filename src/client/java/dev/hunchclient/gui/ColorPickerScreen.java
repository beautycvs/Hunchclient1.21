package dev.hunchclient.gui;

import dev.hunchclient.util.ColorPickerConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Full-featured HSV Color Picker with:
 * - HSV color wheel and sliders
 * - Hex color code input/output
 * - Favorite colors system
 * - Real-time preview
 */
public class ColorPickerScreen extends Screen {

    private final Screen parent;
    private final Consumer<float[]> onColorSelected;
    private final List<int[]> favoriteColors = new ArrayList<>();

    // HSV values
    private float hue = 0.0f;          // 0-360
    private float saturation = 1.0f;   // 0-1
    private float value = 1.0f;        // 0-1
    private float alpha = 1.0f;        // 0-1

    // UI positions
    private int colorWheelX;
    private int colorWheelY;
    private int colorWheelSize = 150;

    private int valueSliderX;
    private int valueSliderY;
    private int sliderWidth = 20;
    private int sliderHeight = 150;

    private int alphaSliderX;

    // Input fields
    private EditBox hexInput;

    // Dragging state
    private boolean draggingWheel = false;
    private boolean draggingValueSlider = false;
    private boolean draggingAlphaSlider = false;

    // Performance optimization: Cache the color wheel pixels
    private int[][] colorWheelCache = null;
    private float lastCachedHue = -1;
    private int pixelStep = 6; // OPTIMIZED: Increased from 2 to 6 (9x fewer draw calls!)

    public ColorPickerScreen(Screen parent, float[] currentColor, Consumer<float[]> onColorSelected) {
        super(Component.literal("Color Picker"));
        this.parent = parent;
        this.onColorSelected = onColorSelected;

        // Convert RGB to HSV
        if (currentColor != null && currentColor.length >= 3) {
            float[] hsv = rgbToHsv(currentColor[0], currentColor[1], currentColor[2]);
            this.hue = hsv[0];
            this.saturation = hsv[1];
            this.value = hsv[2];
            if (currentColor.length >= 4) {
                this.alpha = currentColor[3];
            }
        }

        // Load favorite colors from config
        loadFavoriteColors();
    }

    @Override
    protected void init() {
        // Calculate positions
        colorWheelX = this.width / 2 - colorWheelSize / 2 - 100;
        colorWheelY = this.height / 2 - colorWheelSize / 2;

        valueSliderX = colorWheelX + colorWheelSize + 20;
        valueSliderY = colorWheelY;

        alphaSliderX = valueSliderX + sliderWidth + 10;

        // Hex input field
        hexInput = new EditBox(this.font, this.width / 2 + 80, this.height / 2 - 75, 100, 20, Component.literal("Hex Code"));
        hexInput.setMaxLength(7);
        hexInput.setValue(getCurrentHexColor());
        hexInput.setResponder(this::onHexInputChanged);
        this.addWidget(hexInput);

        // Buttons
        this.addRenderableWidget(Button.builder(Component.literal("Done"), button -> {
            onColorSelected.accept(getCurrentColor());
            this.onClose();
        }).bounds(this.width / 2 - 100, this.height - 50, 95, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> {
            this.onClose();
        }).bounds(this.width / 2 + 5, this.height - 50, 95, 20).build());

        // Add to favorites button
        this.addRenderableWidget(Button.builder(Component.literal("Add Favorite"), button -> {
            addCurrentColorToFavorites();
        }).bounds(this.width / 2 + 80, this.height / 2 - 45, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        // Background blur already handled by Screen.renderWithTooltip before this render call

        // Title
        context.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);

        // Draw HSV color wheel (saturation and hue)
        drawColorWheel(context, colorWheelX, colorWheelY, colorWheelSize);

        // Draw value slider
        drawValueSlider(context, valueSliderX, valueSliderY, sliderWidth, sliderHeight);

        // Draw alpha slider
        drawAlphaSlider(context, alphaSliderX, valueSliderY, sliderWidth, sliderHeight);

        // Draw color preview
        drawColorPreview(context, this.width / 2 + 80, this.height / 2 - 105, 100, 20);

        // Draw hex input
        hexInput.render(context, mouseX, mouseY, delta);

        // Draw RGB values
        float[] rgb = hsvToRgb(hue, saturation, value);
        int r = (int)(rgb[0] * 255);
        int g = (int)(rgb[1] * 255);
        int b = (int)(rgb[2] * 255);
        int a = (int)(alpha * 255);

        context.drawString(this.font, String.format("RGB: %d, %d, %d", r, g, b),
            this.width / 2 + 80, this.height / 2 - 15, 0xFFFFFF);
        context.drawString(this.font, String.format("Alpha: %d", a),
            this.width / 2 + 80, this.height / 2, 0xFFFFFF);

        // Draw HSV values
        context.drawString(this.font, String.format("H: %.0f° S: %.0f%% V: %.0f%%",
            hue, saturation * 100, value * 100),
            this.width / 2 + 80, this.height / 2 + 15, 0xFFFFFF);

        // Draw favorite colors
        drawFavoriteColors(context, this.width / 2 + 80, this.height / 2 + 40);

        // Draw labels
        context.drawString(this.font, "Color", colorWheelX, colorWheelY - 15, 0xFFFFFF);
        context.drawString(this.font, "H", valueSliderX, valueSliderY - 15, 0xFFFFFF);
        context.drawString(this.font, "A", alphaSliderX, valueSliderY - 15, 0xFFFFFF);

        super.render(context, mouseX, mouseY, delta);
    }

    private void drawColorWheel(GuiGraphics context, int x, int y, int size) {
        // Check if we need to rebuild the cache
        if (colorWheelCache == null || Math.abs(lastCachedHue - hue) > 0.5f) {
            rebuildColorWheelCache(size);
            lastCachedHue = hue;
        }

        // Draw cached color wheel with reduced resolution for performance
        for (int px = 0; px < size; px += pixelStep) {
            for (int py = 0; py < size; py += pixelStep) {
                int color = colorWheelCache[px][py];
                context.fill(x + px, y + py, x + px + pixelStep, y + py + pixelStep, color);
            }
        }

        // Draw selection indicator
        int selX = (int)(saturation * size);
        int selY = (int)((1.0f - value) * size);
        drawBorder(context, x + selX - 3, y + selY - 3, 6, 6, 0xFFFFFFFF);
        drawBorder(context, x + selX - 4, y + selY - 4, 8, 8, 0xFF000000);
    }

    private void rebuildColorWheelCache(int size) {
        if (colorWheelCache == null) {
            colorWheelCache = new int[size][size];
        }

        // Only calculate colors, don't draw yet (happens once per hue change)
        for (int px = 0; px < size; px++) {
            for (int py = 0; py < size; py++) {
                float s = (float) px / size;
                float v = 1.0f - (float) py / size;

                float[] rgb = hsvToRgb(hue, s, v);
                colorWheelCache[px][py] = 0xFF000000 |
                    ((int)(rgb[0] * 255) << 16) |
                    ((int)(rgb[1] * 255) << 8) |
                    (int)(rgb[2] * 255);
            }
        }
    }

    private void drawValueSlider(GuiGraphics context, int x, int y, int width, int height) {
        // Draw HUE gradient (rainbow from red to red) - optimized with step
        for (int py = 0; py < height; py += pixelStep) {
            float h = ((float) py / height) * 360.0f;
            float[] rgb = hsvToRgb(h, 1.0f, 1.0f);
            int color = 0xFF000000 |
                ((int)(rgb[0] * 255) << 16) |
                ((int)(rgb[1] * 255) << 8) |
                (int)(rgb[2] * 255);

            context.fill(x, y + py, x + width, y + py + pixelStep, color);
        }

        // Draw slider handle
        int handleY = (int)((hue / 360.0f) * height);
        context.fill(x - 2, y + handleY - 2, x + width + 2, y + handleY + 2, 0xFFFFFFFF);
    }

    private void drawAlphaSlider(GuiGraphics context, int x, int y, int width, int height) {
        // OPTIMIZED: Draw checkerboard with larger squares (4 -> 10)
        int checkerSize = 10;
        int checksX = (width + checkerSize - 1) / checkerSize;
        int checksY = (height + checkerSize - 1) / checkerSize;

        for (int cy = 0; cy < checksY; cy++) {
            for (int cx = 0; cx < checksX; cx++) {
                boolean light = (cx + cy) % 2 == 0;
                int checkerColor = light ? 0xFFCCCCCC : 0xFF999999;
                int x1 = x + cx * checkerSize;
                int y1 = y + cy * checkerSize;
                int x2 = Math.min(x1 + checkerSize, x + width);
                int y2 = Math.min(y1 + checkerSize, y + height);
                context.fill(x1, y1, x2, y2, checkerColor);
            }
        }

        // Draw gradient from transparent to opaque - optimized with step
        float[] rgb = hsvToRgb(hue, saturation, value);
        for (int py = 0; py < height; py += pixelStep) {
            float a = 1.0f - (float) py / height;
            int color = ((int)(a * 255) << 24) |
                ((int)(rgb[0] * 255) << 16) |
                ((int)(rgb[1] * 255) << 8) |
                (int)(rgb[2] * 255);

            context.fill(x, y + py, x + width, y + py + pixelStep, color);
        }

        // Draw slider handle
        int handleY = (int)((1.0f - alpha) * height);
        context.fill(x - 2, y + handleY - 2, x + width + 2, y + handleY + 2, 0xFFFFFFFF);
    }

    private void drawColorPreview(GuiGraphics context, int x, int y, int width, int height) {
        float[] rgb = hsvToRgb(hue, saturation, value);
        int color = ((int)(alpha * 255) << 24) |
            ((int)(rgb[0] * 255) << 16) |
            ((int)(rgb[1] * 255) << 8) |
            (int)(rgb[2] * 255);

        // OPTIMIZED: Checkerboard background with larger squares (4 -> 10)
        int checkerSize = 10;
        int checksX = (width + checkerSize - 1) / checkerSize;
        int checksY = (height + checkerSize - 1) / checkerSize;

        for (int cy = 0; cy < checksY; cy++) {
            for (int cx = 0; cx < checksX; cx++) {
                boolean light = (cx + cy) % 2 == 0;
                int checkerColor = light ? 0xFFCCCCCC : 0xFF999999;
                int x1 = x + cx * checkerSize;
                int y1 = y + cy * checkerSize;
                int x2 = Math.min(x1 + checkerSize, x + width);
                int y2 = Math.min(y1 + checkerSize, y + height);
                context.fill(x1, y1, x2, y2, checkerColor);
            }
        }

        context.fill(x, y, x + width, y + height, color);
        drawBorder(context, x, y, width, height, 0xFF000000);
    }

    private void drawFavoriteColors(GuiGraphics context, int x, int y) {
        context.drawString(this.font, "Favorites:", x, y, 0xFFFFFF);

        int swatchSize = 20;
        int spacing = 5;
        for (int i = 0; i < Math.min(5, favoriteColors.size()); i++) {
            int[] fav = favoriteColors.get(i);
            int favX = x + i * (swatchSize + spacing);
            int favY = y + 15;

            int color = 0xFF000000 | (fav[0] << 16) | (fav[1] << 8) | fav[2];
            context.fill(favX, favY, favX + swatchSize, favY + swatchSize, color);
            drawBorder(context, favX, favY, swatchSize, swatchSize, 0xFFFFFFFF);
        }
    }

    private void drawBorder(GuiGraphics context, int x, int y, int width, int height, int color) {
        context.fill(x, y, x + width, y + 1, color);           // Top
        context.fill(x, y + height - 1, x + width, y + height, color); // Bottom
        context.fill(x, y, x + 1, y + height, color);          // Left
        context.fill(x + width - 1, y, x + width, y + height, color);  // Right
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        if (button == 0) {
            // Check color wheel click
            if (mouseX >= colorWheelX && mouseX < colorWheelX + colorWheelSize &&
                mouseY >= colorWheelY && mouseY < colorWheelY + colorWheelSize) {
                draggingWheel = true;
                updateColorFromWheel(mouseX, mouseY);
                return true;
            }

            // Check hue slider click
            if (mouseX >= valueSliderX && mouseX < valueSliderX + sliderWidth &&
                mouseY >= valueSliderY && mouseY < valueSliderY + sliderHeight) {
                draggingValueSlider = true;
                updateHueFromSlider(mouseY);
                return true;
            }

            // Check alpha slider click
            if (mouseX >= alphaSliderX && mouseX < alphaSliderX + sliderWidth &&
                mouseY >= valueSliderY && mouseY < valueSliderY + sliderHeight) {
                draggingAlphaSlider = true;
                updateAlphaFromSlider(mouseY);
                return true;
            }

            // Check favorite color click
            checkFavoriteColorClick(mouseX, mouseY);
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent click, double offsetX, double offsetY) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        if (draggingWheel) {
            updateColorFromWheel(mouseX, mouseY);
            return true;
        }
        if (draggingValueSlider) {
            updateHueFromSlider(mouseY);
            return true;
        }
        if (draggingAlphaSlider) {
            updateAlphaFromSlider(mouseY);
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent click) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        draggingWheel = false;
        draggingValueSlider = false;
        draggingAlphaSlider = false;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // Adjust hue with scroll wheel
        hue = (hue + (float)verticalAmount * 5.0f + 360.0f) % 360.0f;
        updateHexInput();
        return true;
    }

    private void updateColorFromWheel(double mouseX, double mouseY) {
        saturation = Math.max(0, Math.min(1, (float)(mouseX - colorWheelX) / colorWheelSize));
        value = Math.max(0, Math.min(1, 1.0f - (float)(mouseY - colorWheelY) / colorWheelSize));
        updateHexInput();
    }

    private void updateHueFromSlider(double mouseY) {
        hue = Math.max(0, Math.min(360, ((float)(mouseY - valueSliderY) / sliderHeight) * 360.0f));
        updateHexInput();
    }

    private void updateAlphaFromSlider(double mouseY) {
        alpha = Math.max(0, Math.min(1, 1.0f - (float)(mouseY - valueSliderY) / sliderHeight));
        updateHexInput();
    }

    private void checkFavoriteColorClick(double mouseX, double mouseY) {
        int x = this.width / 2 + 80;
        int y = this.height / 2 + 40 + 15;
        int swatchSize = 20;
        int spacing = 5;

        for (int i = 0; i < Math.min(5, favoriteColors.size()); i++) {
            int favX = x + i * (swatchSize + spacing);
            if (mouseX >= favX && mouseX < favX + swatchSize &&
                mouseY >= y && mouseY < y + swatchSize) {
                // Load favorite color
                int[] fav = favoriteColors.get(i);
                float[] hsv = rgbToHsv(fav[0] / 255.0f, fav[1] / 255.0f, fav[2] / 255.0f);
                hue = hsv[0];
                saturation = hsv[1];
                value = hsv[2];
                updateHexInput();
                break;
            }
        }
    }

    private void onHexInputChanged(String hex) {
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }

        if (hex.length() == 6) {
            try {
                int rgb = Integer.parseInt(hex, 16);
                float r = ((rgb >> 16) & 0xFF) / 255.0f;
                float g = ((rgb >> 8) & 0xFF) / 255.0f;
                float b = (rgb & 0xFF) / 255.0f;

                float[] hsv = rgbToHsv(r, g, b);
                hue = hsv[0];
                saturation = hsv[1];
                value = hsv[2];
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private void updateHexInput() {
        hexInput.setValue(getCurrentHexColor());
    }

    private String getCurrentHexColor() {
        float[] rgb = hsvToRgb(hue, saturation, value);
        int r = (int)(rgb[0] * 255);
        int g = (int)(rgb[1] * 255);
        int b = (int)(rgb[2] * 255);
        return String.format("#%02X%02X%02X", r, g, b);
    }

    private float[] getCurrentColor() {
        float[] rgb = hsvToRgb(hue, saturation, value);
        return new float[]{rgb[0], rgb[1], rgb[2], alpha};
    }

    private void addCurrentColorToFavorites() {
        float[] rgb = hsvToRgb(hue, saturation, value);
        int[] color = new int[]{
            (int)(rgb[0] * 255),
            (int)(rgb[1] * 255),
            (int)(rgb[2] * 255)
        };

        if (favoriteColors.size() >= 5) {
            favoriteColors.remove(0);
        }
        favoriteColors.add(color);

        // Save to config
        ColorPickerConfig.saveFavoriteColors(favoriteColors);
    }

    private void loadFavoriteColors() {
        // Load from config
        List<int[]> savedColors = ColorPickerConfig.loadFavoriteColors();

        if (!savedColors.isEmpty()) {
            favoriteColors.addAll(savedColors);
        } else {
            // Add default favorites if no saved colors exist
            favoriteColors.add(new int[]{255, 0, 0});     // Red
            favoriteColors.add(new int[]{0, 255, 0});     // Green
            favoriteColors.add(new int[]{0, 0, 255});     // Blue
            favoriteColors.add(new int[]{255, 255, 0});   // Yellow
            favoriteColors.add(new int[]{255, 0, 255});   // Magenta
        }
    }

    // HSV <-> RGB conversion
    private static float[] hsvToRgb(float h, float s, float v) {
        float c = v * s;
        float x = c * (1 - Math.abs(((h / 60.0f) % 2) - 1));
        float m = v - c;

        float r, g, b;
        if (h < 60) {
            r = c; g = x; b = 0;
        } else if (h < 120) {
            r = x; g = c; b = 0;
        } else if (h < 180) {
            r = 0; g = c; b = x;
        } else if (h < 240) {
            r = 0; g = x; b = c;
        } else if (h < 300) {
            r = x; g = 0; b = c;
        } else {
            r = c; g = 0; b = x;
        }

        return new float[]{r + m, g + m, b + m};
    }

    private static float[] rgbToHsv(float r, float g, float b) {
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;

        float h = 0;
        if (delta > 0) {
            if (max == r) {
                h = 60 * (((g - b) / delta) % 6);
            } else if (max == g) {
                h = 60 * (((b - r) / delta) + 2);
            } else {
                h = 60 * (((r - g) / delta) + 4);
            }
        }
        if (h < 0) h += 360;

        float s = max == 0 ? 0 : delta / max;
        float v = max;

        return new float[]{h, s, v};
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return true; // Don't pause - we handle background manually
    }
}
