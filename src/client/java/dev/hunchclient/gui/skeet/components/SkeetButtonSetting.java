package dev.hunchclient.gui.skeet.components;

import dev.hunchclient.gui.skeet.SkeetTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Skeet-styled button component for settings
 */
public class SkeetButtonSetting extends SkeetComponent {

    private final String label;
    private final Runnable onClick;
    private final Font textRenderer;

    // Visual state
    private float hoverAmount = 0.0f;
    private final float hoverSpeed = 0.15f;
    private boolean pressing = false;

    // Style
    private static final int BUTTON_HEIGHT = 30;

    public SkeetButtonSetting(int x, int y, int width, String label, Runnable onClick) {
        super(x, y, width, BUTTON_HEIGHT);
        this.label = label;
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

        // Background with hover effect
        int bgColor;
        if (!enabled) {
            bgColor = SkeetTheme.darker(SkeetTheme.BG_FIELD(), 0.3f);
        } else if (pressing) {
            bgColor = SkeetTheme.darker(SkeetTheme.BG_FIELD(), 0.2f);
        } else {
            bgColor = SkeetTheme.blend(SkeetTheme.BG_FIELD(), SkeetTheme.BG_HOVER(), hoverAmount);
        }
        context.fill(x, y, x + width, y + height, bgColor);

        // Border with accent on hover
        int borderColor = hoverAmount > 0.5f ? SkeetTheme.ACCENT_PRIMARY() : SkeetTheme.BORDER_DEFAULT();
        if (!enabled) borderColor = SkeetTheme.darker(borderColor, 0.5f);
        context.fill(x, y, x + width, y + 1, borderColor); // Top
        context.fill(x, y + height - 1, x + width, y + height, borderColor); // Bottom
        context.fill(x, y, x + 1, y + height, borderColor); // Left
        context.fill(x + width - 1, y, x + width, y + height, borderColor); // Right

        // Text (centered)
        int textColor = enabled ? SkeetTheme.TEXT_PRIMARY() : SkeetTheme.TEXT_DISABLED();
        if (hoverAmount > 0.5f && enabled) {
            textColor = SkeetTheme.ACCENT_PRIMARY();
        }
        int textWidth = textRenderer.width(label);
        int textX = x + (width - textWidth) / 2;
        int textY = y + (height - textRenderer.lineHeight) / 2;
        context.drawString(textRenderer, label, textX, textY, textColor, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible || !enabled || button != 0) return false;

        if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height) {
            pressing = true;
            if (onClick != null) {
                onClick.run();
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (pressing && button == 0) {
            pressing = false;
            return true;
        }
        return false;
    }
}
