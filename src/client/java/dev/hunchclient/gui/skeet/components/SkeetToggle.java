package dev.hunchclient.gui.skeet.components;

import dev.hunchclient.gui.skeet.SkeetTheme;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Skeet-styled toggle/checkbox component
 */
public class SkeetToggle extends SkeetComponent {

    private final String label;
    private boolean value;
    private final Consumer<Boolean> onChange;
    private final Font textRenderer;

    // Visual state
    private float hoverAmount = 0.0f;
    private float toggleAmount = 0.0f;
    private final float animSpeed = 0.15f;

    // Style
    private static final int TOGGLE_WIDTH = 40;
    private static final int TOGGLE_HEIGHT = 16;
    private static final int THUMB_SIZE = 12;

    public SkeetToggle(int x, int y, int width, String label, boolean value, Consumer<Boolean> onChange) {
        super(x, y, width, TOGGLE_HEIGHT);
        this.label = label;
        this.value = value;
        this.toggleAmount = value ? 1.0f : 0.0f;
        this.onChange = onChange;
        this.textRenderer = Minecraft.getInstance().font;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        if (!visible) return;

        // Update hover animation
        updateHover(mouseX, mouseY);
        if (hovered && enabled) {
            hoverAmount = Math.min(1.0f, hoverAmount + animSpeed);
        } else {
            hoverAmount = Math.max(0.0f, hoverAmount - animSpeed);
        }

        // Update toggle animation
        float target = value ? 1.0f : 0.0f;
        if (toggleAmount < target) {
            toggleAmount = Math.min(target, toggleAmount + animSpeed);
        } else if (toggleAmount > target) {
            toggleAmount = Math.max(target, toggleAmount - animSpeed);
        }

        // Label
        int textColor = enabled ? SkeetTheme.TEXT_PRIMARY() : SkeetTheme.TEXT_DISABLED();
        context.drawString(textRenderer, label + ":", x, y + (height - textRenderer.lineHeight) / 2, textColor, false);

        // Toggle switch position (right-aligned)
        int toggleX = x + width - TOGGLE_WIDTH;
        int toggleY = y;

        // Background track
        int trackColor = value ?
            SkeetTheme.blend(SkeetTheme.BG_FIELD(), SkeetTheme.ACCENT_DIM(), toggleAmount) :
            SkeetTheme.BG_FIELD();

        if (!enabled) {
            trackColor = SkeetTheme.darker(trackColor, 0.5f);
        } else if (hoverAmount > 0) {
            trackColor = SkeetTheme.lighter(trackColor, hoverAmount * 0.1f);
        }

        // Rounded track
        context.fill(toggleX, toggleY, toggleX + TOGGLE_WIDTH, toggleY + TOGGLE_HEIGHT, trackColor);

        // Border
        int borderColor = value ?
            SkeetTheme.blend(SkeetTheme.BORDER_DEFAULT(), SkeetTheme.ACCENT_PRIMARY(), toggleAmount) :
            SkeetTheme.BORDER_DEFAULT();

        if (!enabled) {
            borderColor = SkeetTheme.darker(borderColor, 0.5f);
        }

        drawBorder(context, toggleX, toggleY, TOGGLE_WIDTH, TOGGLE_HEIGHT, borderColor);

        // Thumb (sliding button)
        int thumbX = toggleX + 2 + (int) ((TOGGLE_WIDTH - THUMB_SIZE - 4) * toggleAmount);
        int thumbY = toggleY + (TOGGLE_HEIGHT - THUMB_SIZE) / 2;

        int thumbColor = value ? SkeetTheme.ACCENT_PRIMARY() : SkeetTheme.TEXT_SECONDARY();
        if (!enabled) {
            thumbColor = SkeetTheme.darker(thumbColor, 0.5f);
        } else if (hoverAmount > 0) {
            thumbColor = SkeetTheme.lighter(thumbColor, hoverAmount * 0.2f);
        }

        // Thumb glow when enabled
        if (value && enabled && hoverAmount > 0) {
            int glowColor = SkeetTheme.withAlpha(SkeetTheme.ACCENT_GLOW(), hoverAmount * 0.4f);
            context.fill(thumbX - 2, thumbY - 2, thumbX + THUMB_SIZE + 2, thumbY + THUMB_SIZE + 2, glowColor);
        }

        context.fill(thumbX, thumbY, thumbX + THUMB_SIZE, thumbY + THUMB_SIZE, thumbColor);

        // Status text
        String status = value ? "ON" : "OFF";
        int statusColor = value ? SkeetTheme.STATUS_SUCCESS() : SkeetTheme.STATUS_ERROR();
        if (!enabled) {
            statusColor = SkeetTheme.TEXT_DISABLED();
        }
        int statusX = toggleX - textRenderer.width(status) - 8;
        context.drawString(textRenderer, status, statusX, y + (height - textRenderer.lineHeight) / 2, statusColor, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible || !enabled || button != 0) return false;

        if (isHovered(mouseX, mouseY)) {
            toggle();
            return true;
        }
        return false;
    }

    public void toggle() {
        setValue(!value);
    }

    public void setValue(boolean value) {
        boolean oldValue = this.value;
        this.value = value;
        if (oldValue != value && onChange != null) {
            onChange.accept(value);
        }
    }

    public boolean getValue() {
        return value;
    }

    private void drawBorder(GuiGraphics context, int x, int y, int width, int height, int color) {
        context.fill(x, y, x + width, y + 1, color);           // Top
        context.fill(x, y + height - 1, x + width, y + height, color); // Bottom
        context.fill(x, y, x + 1, y + height, color);          // Left
        context.fill(x + width - 1, y, x + width, y + height, color);  // Right
    }
}
