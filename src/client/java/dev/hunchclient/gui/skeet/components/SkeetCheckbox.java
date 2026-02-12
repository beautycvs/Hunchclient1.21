package dev.hunchclient.gui.skeet.components;

import dev.hunchclient.gui.skeet.SkeetTheme;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Skeet-styled checkbox component
 */
public class SkeetCheckbox {

    private final String label;
    private boolean checked;
    private Consumer<Boolean> onChange;
    private final Font textRenderer;

    // Position and size
    private int x, y, width, height;

    // Visual state
    private float hoverAmount = 0.0f;
    private final float hoverSpeed = 0.15f;
    private boolean hovered = false;

    public SkeetCheckbox(String label, boolean initialValue) {
        this.label = label;
        this.checked = initialValue;
        this.textRenderer = Minecraft.getInstance().font;
    }

    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void setOnChange(Consumer<Boolean> onChange) {
        this.onChange = onChange;
    }

    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        // Update hover state
        hovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;

        // Update hover animation
        if (hovered) {
            hoverAmount = Math.min(1.0f, hoverAmount + hoverSpeed);
        } else {
            hoverAmount = Math.max(0.0f, hoverAmount - hoverSpeed);
        }

        // Draw checkbox box
        int boxSize = 12;
        int boxY = y + (height - boxSize) / 2;

        // Box background
        int bgColor = checked ? SkeetTheme.ACCENT_PRIMARY : SkeetTheme.BG_SECONDARY;
        if (hoverAmount > 0) {
            bgColor = SkeetTheme.blend(bgColor, SkeetTheme.BG_HOVER, hoverAmount * 0.5f);
        }
        context.fill(x, boxY, x + boxSize, boxY + boxSize, bgColor);

        // Box border
        int borderColor = hovered ? SkeetTheme.ACCENT_PRIMARY : SkeetTheme.BORDER_DEFAULT;
        // Top
        context.fill(x, boxY, x + boxSize, boxY + 1, borderColor);
        // Bottom
        context.fill(x, boxY + boxSize - 1, x + boxSize, boxY + boxSize, borderColor);
        // Left
        context.fill(x, boxY, x + 1, boxY + boxSize, borderColor);
        // Right
        context.fill(x + boxSize - 1, boxY, x + boxSize, boxY + boxSize, borderColor);

        // Checkmark
        if (checked) {
            // Draw a simple checkmark using theme accent color
            int checkColor = SkeetTheme.ACCENT_PRIMARY;
            // Draw two lines to form checkmark
            for (int i = 0; i < 2; i++) {
                context.fill(x + 3 + i, boxY + 6, x + 4 + i, boxY + 9, checkColor);
                context.fill(x + 5 + i, boxY + 4 - i, x + 6 + i, boxY + 8, checkColor);
            }
        }

        // Label
        int textColor = hovered ? SkeetTheme.TEXT_PRIMARY : SkeetTheme.TEXT_SECONDARY;
        context.drawString(textRenderer, label, x + boxSize + 8, y + (height - textRenderer.lineHeight) / 2, textColor, false);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && hovered) {
            checked = !checked;
            if (onChange != null) {
                onChange.accept(checked);
            }
            return true;
        }
        return false;
    }

    public boolean isChecked() {
        return checked;
    }

    public void setChecked(boolean checked) {
        this.checked = checked;
    }
}