package dev.hunchclient.gui.skeet.components;

import dev.hunchclient.gui.skeet.SkeetTheme;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/**
 * Skeet-styled slider component with smooth animations
 * Supports clicking on value to enter directly
 */
public class SkeetSlider extends SkeetComponent {

    private final String label;
    private float value;
    private final float min;
    private final float max;
    private final Consumer<Float> onChange;
    private final Font textRenderer;

    // Visual state
    private boolean dragging = false;
    private float hoverAmount = 0.0f;
    private final float hoverSpeed = 0.15f;

    // Direct value input
    private boolean editing = false;
    private StringBuilder inputBuffer = new StringBuilder();
    private int cursorBlink = 0;

    // Display settings
    private int decimals = 1;
    private String suffix = "";
    private boolean percentage = false;

    // Style
    private static final int SLIDER_HEIGHT = 4;
    private static final int THUMB_SIZE = 8;
    private static final int LABEL_HEIGHT = 12;

    public SkeetSlider(int x, int y, int width, String label, float value, float min, float max, Consumer<Float> onChange) {
        super(x, y, width, LABEL_HEIGHT + SLIDER_HEIGHT + 8);
        this.label = label;
        this.value = Mth.clamp(value, min, max);
        this.min = min;
        this.max = max;
        this.onChange = onChange;
        this.textRenderer = Minecraft.getInstance().font;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        if (!visible) return;

        cursorBlink++;

        // Update hover animation
        updateHover(mouseX, mouseY);
        if ((hovered || dragging) && enabled) {
            hoverAmount = Math.min(1.0f, hoverAmount + hoverSpeed);
        } else {
            hoverAmount = Math.max(0.0f, hoverAmount - hoverSpeed);
        }

        // Label with value
        String displayValue = editing ? inputBuffer.toString() + ((cursorBlink / 10) % 2 == 0 ? "|" : "") : formatValue();
        String labelPart = label + ": ";
        int textColor = enabled ? SkeetTheme.TEXT_PRIMARY() : SkeetTheme.TEXT_DISABLED();
        int valueColor = editing ? SkeetTheme.ACCENT_PRIMARY() : textColor;

        context.drawString(textRenderer, labelPart, x, y, textColor, false);
        int valueX = x + textRenderer.width(labelPart);

        // Draw value with highlight if editing
        if (editing) {
            int valueWidth = textRenderer.width(displayValue) + 4;
            context.fill(valueX - 2, y - 1, valueX + valueWidth, y + 10, SkeetTheme.BG_FIELD());
        }
        context.drawString(textRenderer, displayValue, valueX, y, valueColor, false);

        // Slider bar position
        int sliderY = y + LABEL_HEIGHT + 4;

        // Background bar
        int bgColor = enabled ? SkeetTheme.BG_FIELD() : SkeetTheme.darker(SkeetTheme.BG_FIELD(), 0.3f);
        context.fill(x, sliderY, x + width, sliderY + SLIDER_HEIGHT, bgColor);

        // Filled bar (value)
        float normalized = (value - min) / (max - min);
        int filledWidth = (int) (normalized * width);

        int fillColor = SkeetTheme.ACCENT_PRIMARY();
        if (!enabled) {
            fillColor = SkeetTheme.darker(fillColor, 0.5f);
        } else if (hoverAmount > 0) {
            // Add glow effect on hover
            fillColor = SkeetTheme.blend(SkeetTheme.ACCENT_PRIMARY(), SkeetTheme.lighter(SkeetTheme.ACCENT_PRIMARY(), 0.3f), hoverAmount);
        }
        context.fill(x, sliderY, x + filledWidth, sliderY + SLIDER_HEIGHT, fillColor);

        // Thumb (handle)
        if (enabled) {
            int thumbX = x + filledWidth - THUMB_SIZE / 2;
            int thumbY = sliderY - (THUMB_SIZE - SLIDER_HEIGHT) / 2;

            // Thumb glow on hover/drag
            if (hoverAmount > 0) {
                int glowSize = (int) (hoverAmount * 4);
                int glowColor = SkeetTheme.withAlpha(SkeetTheme.ACCENT_GLOW(), hoverAmount * 0.5f);
                context.fill(thumbX - glowSize, thumbY - glowSize,
                           thumbX + THUMB_SIZE + glowSize, thumbY + THUMB_SIZE + glowSize, glowColor);
            }

            // Thumb
            int thumbColor = dragging ? SkeetTheme.lighter(SkeetTheme.ACCENT_PRIMARY(), 0.2f) : SkeetTheme.ACCENT_PRIMARY();
            context.fill(thumbX, thumbY, thumbX + THUMB_SIZE, thumbY + THUMB_SIZE, thumbColor);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible || !enabled) return false;

        // Check if clicking on value text (to edit directly)
        String labelPart = label + ": ";
        int valueX = x + textRenderer.width(labelPart);
        String displayValue = formatValue();
        int valueWidth = textRenderer.width(displayValue) + 4;

        boolean onValue = mouseX >= valueX - 2 && mouseX <= valueX + valueWidth &&
                         mouseY >= y - 1 && mouseY <= y + 10;

        if (onValue && button == 0) {
            // Start editing
            editing = true;
            inputBuffer = new StringBuilder();
            cursorBlink = 0;
            return true;
        }

        // If editing and clicking elsewhere, stop editing
        if (editing) {
            finishEditing();
        }

        if (button != 0) return false;

        int sliderY = y + LABEL_HEIGHT + 4;
        boolean onSlider = mouseX >= x && mouseX <= x + width &&
                          mouseY >= sliderY - 4 && mouseY <= sliderY + SLIDER_HEIGHT + 4;

        if (onSlider) {
            dragging = true;
            updateValueFromMouse(mouseX);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!editing) return false;

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            // Cancel editing
            editing = false;
            inputBuffer = new StringBuilder();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            finishEditing();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_BACKSPACE && inputBuffer.length() > 0) {
            inputBuffer.deleteCharAt(inputBuffer.length() - 1);
            return true;
        }

        return true; // Consume all keys while editing
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!editing) return false;

        // Allow digits, minus, and decimal point
        if (Character.isDigit(chr) || chr == '-' || chr == '.') {
            inputBuffer.append(chr);
            return true;
        }
        return false;
    }

    private void finishEditing() {
        editing = false;
        try {
            float newValue = Float.parseFloat(inputBuffer.toString());
            setValue(newValue);
        } catch (NumberFormatException e) {
            // Invalid input, keep old value
        }
        inputBuffer = new StringBuilder();
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging && button == 0) {
            dragging = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (dragging && enabled) {
            updateValueFromMouse(mouseX);
            return true;
        }
        return false;
    }

    private void updateValueFromMouse(double mouseX) {
        float normalized = (float) ((mouseX - x) / width);
        normalized = Mth.clamp(normalized, 0.0f, 1.0f);
        float newValue = min + (max - min) * normalized;
        setValue(newValue);
    }

    private String formatValue() {
        if (percentage) {
            return String.format("%.0f%%", value * 100);
        }
        String format = "%." + decimals + "f";
        return String.format(format, value) + suffix;
    }

    public void setValue(float value) {
        float oldValue = this.value;
        this.value = Mth.clamp(value, min, max);
        if (oldValue != this.value && onChange != null) {
            onChange.accept(this.value);
        }
    }

    public float getValue() {
        return value;
    }

    // Configuration methods
    public SkeetSlider withDecimals(int decimals) {
        this.decimals = decimals;
        return this;
    }

    public SkeetSlider withSuffix(String suffix) {
        this.suffix = suffix;
        return this;
    }

    public SkeetSlider asPercentage() {
        this.percentage = true;
        return this;
    }
}
