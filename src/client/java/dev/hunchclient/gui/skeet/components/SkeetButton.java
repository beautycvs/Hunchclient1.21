package dev.hunchclient.gui.skeet.components;

import dev.hunchclient.gui.skeet.SkeetTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Skeet-styled button component
 */
public class SkeetButton extends SkeetComponent {

    private final String text;
    private final Runnable onClick;
    private final Font textRenderer;

    // Visual state
    private float hoverAmount = 0.0f;
    private float pressAmount = 0.0f;
    private final float hoverSpeed = 0.15f;

    // Style options
    private int bgColor = SkeetTheme.BG_SECONDARY();
    private int bgHoverColor = SkeetTheme.BG_HOVER();
    private int textColor = SkeetTheme.TEXT_SECONDARY();
    private int borderColor = SkeetTheme.BORDER_DEFAULT();
    private boolean drawBorder = true;

    public SkeetButton(int x, int y, int width, int height, String text, Runnable onClick) {
        super(x, y, width, height);
        this.text = text;
        this.onClick = onClick;
        this.textRenderer = Minecraft.getInstance().font;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        if (!visible) return;

        // Update hover animation
        updateHover(mouseX, mouseY);
        if (hovered && enabled) {
            hoverAmount = Math.min(1.0f, hoverAmount + hoverSpeed);
        } else {
            hoverAmount = Math.max(0.0f, hoverAmount - hoverSpeed);
        }

        // Update press animation
        if (pressAmount > 0) {
            pressAmount = Math.max(0.0f, pressAmount - 0.2f);
        }

        // Background with hover effect
        int currentBg = SkeetTheme.blend(bgColor, bgHoverColor, hoverAmount);
        if (!enabled) {
            currentBg = SkeetTheme.darker(currentBg, 0.5f);
        }
        context.fill(x, y, x + width, y + height, currentBg);

        // Border
        if (drawBorder) {
            int currentBorder = hoverAmount > 0.5f ? SkeetTheme.BORDER_ACCENT() : borderColor;
            if (!enabled) {
                currentBorder = SkeetTheme.darker(currentBorder, 0.5f);
            }
            drawBorder(context, currentBorder);
        }

        // Text (centered)
        int textWidth = textRenderer.width(text);
        int textX = x + (width - textWidth) / 2;
        int textY = y + (height - textRenderer.lineHeight) / 2;

        int currentTextColor = textColor;
        if (hoverAmount > 0.5f) {
            currentTextColor = SkeetTheme.ACCENT_PRIMARY();
        }
        if (!enabled) {
            currentTextColor = SkeetTheme.TEXT_DISABLED();
        }

        context.drawString(textRenderer, text, textX, textY, currentTextColor, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible || !enabled) return false;
        if (button == 0 && isHovered(mouseX, mouseY)) {
            pressAmount = 1.0f;
            if (onClick != null) {
                onClick.run();
            }
            return true;
        }
        return false;
    }

    private void drawBorder(GuiGraphics context, int color) {
        context.fill(x, y, x + width, y + SkeetTheme.BORDER_THIN, color);           // Top
        context.fill(x, y + height - SkeetTheme.BORDER_THIN, x + width, y + height, color); // Bottom
        context.fill(x, y, x + SkeetTheme.BORDER_THIN, y + height, color);          // Left
        context.fill(x + width - SkeetTheme.BORDER_THIN, y, x + width, y + height, color);  // Right
    }

    // Style setters
    public SkeetButton withBackgroundColor(int color) {
        this.bgColor = color;
        return this;
    }

    public SkeetButton withTextColor(int color) {
        this.textColor = color;
        return this;
    }

    public SkeetButton withBorder(boolean drawBorder) {
        this.drawBorder = drawBorder;
        return this;
    }

    public SkeetButton withAccentStyle() {
        this.textColor = SkeetTheme.ACCENT_PRIMARY();
        this.borderColor = SkeetTheme.BORDER_ACCENT();
        return this;
    }
}
