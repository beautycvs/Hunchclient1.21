package dev.hunchclient.gui.skeet.components;

import dev.hunchclient.gui.skeet.SkeetTheme;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Skeet-styled text input field
 */
public class SkeetTextField extends SkeetComponent {

    private String text;
    private final String placeholder;
    private final Consumer<String> onChange;
    private final Font textRenderer;

    private boolean focused = false;
    private float focusAmount = 0.0f;
    private final float focusSpeed = 0.15f;
    private long lastCursorBlink = System.currentTimeMillis();

    private static final int FIELD_HEIGHT = 18;
    private static final int PADDING = 4;

    public SkeetTextField(int x, int y, int width, String placeholder, String initialText, Consumer<String> onChange) {
        super(x, y, width, FIELD_HEIGHT);
        this.placeholder = placeholder;
        this.text = initialText != null ? initialText : "";
        this.onChange = onChange;
        this.textRenderer = Minecraft.getInstance().font;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        if (!visible) return;

        // Update focus animation
        updateHover(mouseX, mouseY);
        if (focused) {
            focusAmount = Math.min(1.0f, focusAmount + focusSpeed);
        } else {
            focusAmount = Math.max(0.0f, focusAmount - focusSpeed);
        }

        // Background
        int bgColor = SkeetTheme.blend(SkeetTheme.BG_FIELD(), SkeetTheme.BG_ACTIVE(), focusAmount);
        if (!enabled) {
            bgColor = SkeetTheme.darker(bgColor, 0.5f);
        }
        context.fill(x, y, x + width, y + height, bgColor);

        // Border with focus effect
        int borderColor = SkeetTheme.blend(SkeetTheme.BORDER_DEFAULT(), SkeetTheme.ACCENT_PRIMARY(), focusAmount);
        if (!enabled) {
            borderColor = SkeetTheme.darker(borderColor, 0.5f);
        }
        drawBorder(context, borderColor);

        // Text content
        String displayText = text;
        boolean showCursor = focused && (System.currentTimeMillis() - lastCursorBlink) % 1000 < 500;

        if (displayText.isEmpty() && !focused) {
            // Show placeholder
            displayText = placeholder;
            int placeholderColor = enabled ? SkeetTheme.TEXT_DIM() : SkeetTheme.TEXT_DISABLED();
            context.drawString(textRenderer, displayText, x + PADDING, y + (height - textRenderer.lineHeight) / 2, placeholderColor, false);
        } else {
            // Show actual text with cursor
            if (showCursor && enabled) {
                displayText += "_";
            }

            // Truncate if too long
            int maxWidth = width - PADDING * 2;
            while (textRenderer.width(displayText) > maxWidth && !displayText.isEmpty()) {
                displayText = displayText.substring(1); // Remove from start
            }

            int textColor = enabled ? SkeetTheme.TEXT_PRIMARY() : SkeetTheme.TEXT_DISABLED();
            context.drawString(textRenderer, displayText, x + PADDING, y + (height - textRenderer.lineHeight) / 2, textColor, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible || !enabled || button != 0) return false;

        boolean wasClicked = isHovered(mouseX, mouseY);
        setFocused(wasClicked);
        return wasClicked;
    }

    /**
     * Handle character input
     */
    public boolean charTyped(char chr) {
        if (!focused || !enabled) return false;

        // Only accept printable ASCII
        if (chr >= 32 && chr <= 126) {
            text += chr;
            if (onChange != null) {
                onChange.accept(text);
            }
            return true;
        }
        return false;
    }

    /**
     * Handle backspace
     */
    public boolean handleBackspace() {
        if (!focused || !enabled) return false;

        if (!text.isEmpty()) {
            text = text.substring(0, text.length() - 1);
            if (onChange != null) {
                onChange.accept(text);
            }
            return true;
        }
        return false;
    }

    /**
     * Handle enter key
     */
    public boolean handleEnter() {
        if (focused) {
            setFocused(false);
            return true;
        }
        return false;
    }

    public void setText(String text) {
        this.text = text != null ? text : "";
        if (onChange != null) {
            onChange.accept(this.text);
        }
    }

    public String getText() {
        return text;
    }

    public void setFocused(boolean focused) {
        if (this.focused != focused) {
            this.focused = focused;
            lastCursorBlink = System.currentTimeMillis();
        }
    }

    public boolean isFocused() {
        return focused;
    }

    private void drawBorder(GuiGraphics context, int color) {
        context.fill(x, y, x + width, y + 1, color);           // Top
        context.fill(x, y + height - 1, x + width, y + height, color); // Bottom
        context.fill(x, y, x + 1, y + height, color);          // Left
        context.fill(x + width - 1, y, x + width, y + height, color);  // Right
    }
}
