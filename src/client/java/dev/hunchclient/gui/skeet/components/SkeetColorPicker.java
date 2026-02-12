package dev.hunchclient.gui.skeet.components;

import dev.hunchclient.gui.ColorPickerScreen;
import dev.hunchclient.gui.skeet.SkeetTheme;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Skeet-styled color picker with RGBA sliders and hex input
 */
public class SkeetColorPicker extends SkeetComponent {

    private final String label;
    private int color; // ARGB format
    private final Consumer<Integer> onChange;
    private final Font textRenderer;

    // Sub-components
    private final List<SkeetSlider> sliders = new ArrayList<>();
    private SkeetTextField hexField;

    // Layout
    private static final int PREVIEW_SIZE = 30;
    private static final int SLIDER_HEIGHT = 24;
    private static final int SPACING = 5;
    private static final int HEX_FIELD_HEIGHT = 20;

    private boolean expanded = false;
    private float expandAmount = 0.0f;
    private final float animSpeed = 0.15f;

    public SkeetColorPicker(int x, int y, int width, String label, int initialColor, Consumer<Integer> onChange) {
        super(x, y, width, PREVIEW_SIZE + SPACING);
        this.label = label;
        this.color = initialColor;
        this.onChange = onChange;
        this.textRenderer = Minecraft.getInstance().font;

        createSliders();
        createHexField();
    }

    private void createSliders() {
        // Alpha slider
        sliders.add(new SkeetSlider(0, 0, 250, "Alpha",
            getAlpha() / 255f, 0f, 1f,
            val -> {
                setAlpha((int) (val * 255));
                updateHexField();
            }
        ).withDecimals(0).asPercentage());

        // Red slider
        sliders.add(new SkeetSlider(0, 0, 250, "Red",
            getRed() / 255f, 0f, 1f,
            val -> {
                setRed((int) (val * 255));
                updateHexField();
            }
        ).withDecimals(0).asPercentage());

        // Green slider
        sliders.add(new SkeetSlider(0, 0, 250, "Green",
            getGreen() / 255f, 0f, 1f,
            val -> {
                setGreen((int) (val * 255));
                updateHexField();
            }
        ).withDecimals(0).asPercentage());

        // Blue slider
        sliders.add(new SkeetSlider(0, 0, 250, "Blue",
            getBlue() / 255f, 0f, 1f,
            val -> {
                setBlue((int) (val * 255));
                updateHexField();
            }
        ).withDecimals(0).asPercentage());
    }

    private void createHexField() {
        hexField = new SkeetTextField(0, 0, 250, "Hex (#AARRGGBB)", getHexString(),
            hex -> setFromHex(hex));
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        if (!visible) return;

        // Update expand animation
        float target = expanded ? 1.0f : 0.0f;
        if (expandAmount < target) {
            expandAmount = Math.min(target, expandAmount + animSpeed);
        } else if (expandAmount > target) {
            expandAmount = Math.max(target, expandAmount - animSpeed);
        }

        // Label
        context.drawString(textRenderer, label + ":", x, y + 8, SkeetTheme.TEXT_PRIMARY(), false);

        // Preview box (clickable)
        int previewX = x + width - PREVIEW_SIZE - 50;
        int previewY = y;

        // Preview background (checkerboard for alpha)
        drawCheckerboard(context, previewX, previewY, PREVIEW_SIZE, PREVIEW_SIZE);

        // Preview color
        context.fill(previewX, previewY, previewX + PREVIEW_SIZE, previewY + PREVIEW_SIZE, color);

        // Preview border
        int borderColor = hovered ? SkeetTheme.ACCENT_PRIMARY() : SkeetTheme.BORDER_DEFAULT();
        drawBorder(context, previewX, previewY, PREVIEW_SIZE, PREVIEW_SIZE, borderColor);

        // Expand button
        String expandText = expanded ? "[-]" : "[+]";
        int expandX = x + width - 40;
        context.drawString(textRenderer, expandText, expandX, y + 10, SkeetTheme.TEXT_SECONDARY(), false);

        // Hex preview
        String hexPreview = "#" + getHexString();
        int hexX = previewX - textRenderer.width(hexPreview) - 10;
        context.drawString(textRenderer, hexPreview, hexX, y + 10, SkeetTheme.TEXT_DIM(), false);

        // Expanded sliders
        if (expandAmount > 0.01f) {
            int slidersHeight = (SLIDER_HEIGHT * 4) + HEX_FIELD_HEIGHT + (SPACING * 5);
            int currentHeight = (int) (slidersHeight * expandAmount);

            if (expandAmount > 0.1f) {
                context.enableScissor(x, y + PREVIEW_SIZE + SPACING, x + width, y + PREVIEW_SIZE + SPACING + currentHeight);

                int sliderY = y + PREVIEW_SIZE + SPACING;
                for (SkeetSlider slider : sliders) {
                    slider.setPosition(x + SPACING, sliderY);
                    slider.render(context, mouseX, mouseY, delta);
                    sliderY += SLIDER_HEIGHT;
                }

                // Hex field
                hexField.setPosition(x + SPACING, sliderY + SPACING);
                hexField.setSize(250, HEX_FIELD_HEIGHT);
                hexField.render(context, mouseX, mouseY, delta);

                context.disableScissor();
            }
        }

        // Update height based on expand
        int totalHeight = PREVIEW_SIZE + SPACING;
        if (expanded) {
            totalHeight += (SLIDER_HEIGHT * 4) + HEX_FIELD_HEIGHT + (SPACING * 5);
        }
        this.height = (int) (PREVIEW_SIZE + SPACING + ((totalHeight - (PREVIEW_SIZE + SPACING)) * expandAmount));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible || !enabled || button != 0) return false;

        // Check preview/expand button
        int previewX = x + width - PREVIEW_SIZE - 50;
        int previewY = y;
        int expandX = x + width - 40;

        // Click on preview box = Open HSV Color Picker Screen!
        if (mouseX >= previewX && mouseX <= previewX + PREVIEW_SIZE &&
            mouseY >= previewY && mouseY <= previewY + PREVIEW_SIZE) {
            openColorPickerScreen();
            return true;
        }

        // Click on expand button = Toggle inline sliders (for advanced users)
        if (mouseX >= expandX && mouseX <= expandX + 30 &&
            mouseY >= y && mouseY <= y + PREVIEW_SIZE) {
            expanded = !expanded;
            return true;
        }

        // Forward to sliders/hex if expanded
        if (expanded) {
            for (SkeetSlider slider : sliders) {
                if (slider.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            }
            if (hexField.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Open the beautiful HSV Color Picker Screen with wheel and favorites
     */
    private void openColorPickerScreen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) return;

        // Convert ARGB int to float[] RGBA for ColorPickerScreen
        float[] currentColor = new float[]{
            getRed() / 255f,
            getGreen() / 255f,
            getBlue() / 255f,
            getAlpha() / 255f
        };

        // Open ColorPickerScreen with callback
        mc.setScreen(new ColorPickerScreen(mc.screen, currentColor, newColor -> {
            // Convert float[] RGBA back to ARGB int
            int alpha = (int) (newColor[3] * 255);
            int red = (int) (newColor[0] * 255);
            int green = (int) (newColor[1] * 255);
            int blue = (int) (newColor[2] * 255);

            int newColorInt = (alpha << 24) | (red << 16) | (green << 8) | blue;
            setColor(newColorInt);

            if (onChange != null) {
                onChange.accept(newColorInt);
            }
        }));
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (expanded) {
            for (SkeetSlider slider : sliders) {
                slider.mouseReleased(mouseX, mouseY, button);
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (expanded) {
            for (SkeetSlider slider : sliders) {
                if (slider.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean charTyped(char chr) {
        if (expanded && hexField.isFocused()) {
            return hexField.charTyped(chr);
        }
        return false;
    }

    public boolean handleBackspace() {
        if (expanded && hexField.isFocused()) {
            return hexField.handleBackspace();
        }
        return false;
    }

    public boolean handleEnter() {
        if (expanded && hexField.isFocused()) {
            return hexField.handleEnter();
        }
        return false;
    }

    // Color manipulation
    private int getAlpha() {
        return (color >> 24) & 0xFF;
    }

    private int getRed() {
        return (color >> 16) & 0xFF;
    }

    private int getGreen() {
        return (color >> 8) & 0xFF;
    }

    private int getBlue() {
        return color & 0xFF;
    }

    private void setAlpha(int alpha) {
        color = (color & 0x00FFFFFF) | (alpha << 24);
        if (onChange != null) onChange.accept(color);
    }

    private void setRed(int red) {
        color = (color & 0xFF00FFFF) | (red << 16);
        if (onChange != null) onChange.accept(color);
    }

    private void setGreen(int green) {
        color = (color & 0xFFFF00FF) | (green << 8);
        if (onChange != null) onChange.accept(color);
    }

    private void setBlue(int blue) {
        color = (color & 0xFFFFFF00) | blue;
        if (onChange != null) onChange.accept(color);
    }

    private void setFromHex(String hex) {
        try {
            hex = hex.replace("#", "").replace("0x", "");
            if (hex.length() == 6) {
                int rgb = Integer.parseInt(hex, 16);
                int alpha = getAlpha();
                color = (alpha << 24) | rgb;
            } else if (hex.length() == 8) {
                color = (int) Long.parseLong(hex, 16);
            }
            updateSliders();
            if (onChange != null) onChange.accept(color);
        } catch (NumberFormatException ignored) {
        }
    }

    private String getHexString() {
        return String.format("%08X", color);
    }

    private void updateHexField() {
        if (hexField != null) {
            hexField.setText(getHexString());
        }
    }

    private void updateSliders() {
        if (!sliders.isEmpty()) {
            sliders.get(0).setValue(getAlpha() / 255f);
            sliders.get(1).setValue(getRed() / 255f);
            sliders.get(2).setValue(getGreen() / 255f);
            sliders.get(3).setValue(getBlue() / 255f);
        }
    }

    /**
     * OPTIMIZED: Draw checkerboard with larger squares and fewer draw calls
     */
    private void drawCheckerboard(GuiGraphics context, int x, int y, int width, int height) {
        int squareSize = 10; // Increased from 5 to 10 (4x fewer calls!)
        int squaresX = (width + squareSize - 1) / squareSize;
        int squaresY = (height + squareSize - 1) / squareSize;

        // Only draw ~9 squares instead of 36!
        for (int px = 0; px < squaresX; px++) {
            for (int py = 0; py < squaresY; py++) {
                boolean isLight = (px + py) % 2 == 0;
                int checkColor = isLight ? 0xFFCCCCCC : 0xFF999999;

                int x1 = x + px * squareSize;
                int y1 = y + py * squareSize;
                int x2 = Math.min(x1 + squareSize, x + width);
                int y2 = Math.min(y1 + squareSize, y + height);

                context.fill(x1, y1, x2, y2, checkColor);
            }
        }
    }

    private void drawBorder(GuiGraphics context, int x, int y, int width, int height, int color) {
        context.fill(x, y, x + width, y + 1, color);           // Top
        context.fill(x, y + height - 1, x + width, y + height, color); // Bottom
        context.fill(x, y, x + 1, y + height, color);          // Left
        context.fill(x + width - 1, y, x + width, y + height, color);  // Right
    }

    public int getColor() {
        return color;
    }

    public void setColor(int color) {
        this.color = color;
        updateSliders();
        updateHexField();
    }
}
