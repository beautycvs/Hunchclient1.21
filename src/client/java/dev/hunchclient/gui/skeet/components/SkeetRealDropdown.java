package dev.hunchclient.gui.skeet.components;

import dev.hunchclient.gui.skeet.SkeetTheme;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Real expandable dropdown with option list
 * Skeet-styled with smooth animations
 */
public class SkeetRealDropdown extends SkeetComponent {

    private final String label;
    private final List<String> options;
    private int selectedIndex;
    private final Consumer<String> onChange;
    private final Font textRenderer;

    // Dropdown state
    private boolean expanded = false;
    private float expandAnimation = 0.0f;
    private int hoveredOptionIndex = -1;

    // Styling
    private static final int HEADER_HEIGHT = 20;
    private static final int OPTION_HEIGHT = 18;
    private static final int DROPDOWN_PADDING = 4;
    private static final float ANIMATION_SPEED = 0.2f;

    // Global tracking for closing when clicking outside
    private static SkeetRealDropdown currentlyOpen = null;

    public SkeetRealDropdown(int x, int y, int width, String label, List<String> options, int initialIndex, Consumer<String> onChange) {
        super(x, y, width, HEADER_HEIGHT);
        this.label = label;
        this.options = options;
        this.selectedIndex = Math.max(0, Math.min(initialIndex, options.size() - 1));
        this.onChange = onChange;
        this.textRenderer = Minecraft.getInstance().font;
    }

    @Override
    public int getHeight() {
        // Return height including expanded dropdown if open
        if (expanded || expandAnimation > 0.01f) {
            int dropdownHeight = (int) ((options.size() * OPTION_HEIGHT + DROPDOWN_PADDING * 2) * expandAnimation);
            return HEADER_HEIGHT + dropdownHeight;
        }
        return HEADER_HEIGHT;
    }

    /**
     * Get base height (without expansion)
     */
    public int getBaseHeight() {
        return HEADER_HEIGHT;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        if (!visible || options.isEmpty()) return;

        updateHover(mouseX, mouseY);
        updateAnimation();
        updateHoveredOption(mouseX, mouseY);

        // Calculate label width for positioning
        int labelWidth = textRenderer.width(label + ": ");

        // Draw label
        int textColor = enabled ? SkeetTheme.TEXT_PRIMARY() : SkeetTheme.TEXT_DISABLED();
        context.drawString(textRenderer, label + ":", x, y + (HEADER_HEIGHT - textRenderer.lineHeight) / 2, textColor, false);

        // Selection box area
        int boxX = x + labelWidth + 4;
        int boxWidth = width - labelWidth - 8;
        int boxHeight = HEADER_HEIGHT - 4;
        int boxY = y + 2;

        // Draw selection box background
        int bgColor = expanded ? SkeetTheme.BG_ACTIVE() : (hovered ? SkeetTheme.BG_HOVER() : SkeetTheme.BG_FIELD());
        context.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, bgColor);

        // Draw border
        int borderColor = expanded ? SkeetTheme.ACCENT_PRIMARY() : SkeetTheme.BORDER_DEFAULT();
        drawBorder(context, boxX, boxY, boxWidth, boxHeight, borderColor);

        // Draw selected value
        String selected = options.get(selectedIndex);
        int valueColor = enabled ? SkeetTheme.TEXT_PRIMARY() : SkeetTheme.TEXT_DISABLED();
        int textY = boxY + (boxHeight - textRenderer.lineHeight) / 2;

        // Truncate if too long
        String displayText = truncateText(selected, boxWidth - 20);
        context.drawString(textRenderer, displayText, boxX + 4, textY, valueColor, false);

        // Draw arrow indicator
        String arrow = expanded ? "▲" : "▼";
        int arrowColor = expanded ? SkeetTheme.ACCENT_PRIMARY() : SkeetTheme.TEXT_DIM();
        int arrowX = boxX + boxWidth - 12;
        context.drawString(textRenderer, arrow, arrowX, textY, arrowColor, false);

        // Draw expanded dropdown
        if (expandAnimation > 0.01f) {
            renderDropdownList(context, boxX, boxY + boxHeight, boxWidth, mouseX, mouseY);
        }
    }

    private void renderDropdownList(GuiGraphics context, int listX, int listY, int listWidth, int mouseX, int mouseY) {
        int totalHeight = options.size() * OPTION_HEIGHT + DROPDOWN_PADDING * 2;
        int visibleHeight = (int) (totalHeight * expandAnimation);

        if (visibleHeight < 1) return;

        // Draw dropdown background with shadow
        int shadowOffset = 2;
        context.fill(listX + shadowOffset, listY + shadowOffset,
                    listX + listWidth + shadowOffset, listY + visibleHeight + shadowOffset,
                    0x40000000); // Shadow

        // Main background
        context.fill(listX, listY, listX + listWidth, listY + visibleHeight, SkeetTheme.BG_SECONDARY());

        // Border
        drawBorder(context, listX, listY, listWidth, visibleHeight, SkeetTheme.BORDER_ACCENT());

        // Draw options (only if animation is far enough)
        if (expandAnimation > 0.3f) {
            int optionY = listY + DROPDOWN_PADDING;
            for (int i = 0; i < options.size(); i++) {
                if (optionY + OPTION_HEIGHT > listY + visibleHeight) break;

                boolean isHovered = (i == hoveredOptionIndex);
                boolean isSelected = (i == selectedIndex);

                // Option background
                if (isHovered) {
                    context.fill(listX + 2, optionY, listX + listWidth - 2, optionY + OPTION_HEIGHT,
                                SkeetTheme.BG_HOVER());
                }

                // Option text
                int optionColor;
                if (isSelected) {
                    optionColor = SkeetTheme.ACCENT_PRIMARY();
                } else if (isHovered) {
                    optionColor = SkeetTheme.TEXT_PRIMARY();
                } else {
                    optionColor = SkeetTheme.TEXT_SECONDARY();
                }

                String optionText = truncateText(options.get(i), listWidth - 12);
                int textY = optionY + (OPTION_HEIGHT - textRenderer.lineHeight) / 2;
                context.drawString(textRenderer, optionText, listX + 6, textY, optionColor, false);

                // Selection indicator (checkmark or dot)
                if (isSelected) {
                    context.drawString(textRenderer, "●", listX + listWidth - 14, textY, SkeetTheme.ACCENT_PRIMARY(), false);
                }

                optionY += OPTION_HEIGHT;
            }
        }
    }

    private void updateAnimation() {
        float target = expanded ? 1.0f : 0.0f;
        if (expandAnimation < target) {
            expandAnimation = Math.min(target, expandAnimation + ANIMATION_SPEED);
        } else if (expandAnimation > target) {
            expandAnimation = Math.max(target, expandAnimation - ANIMATION_SPEED);
        }
    }

    private void updateHoveredOption(int mouseX, int mouseY) {
        if (!expanded || expandAnimation < 0.5f) {
            hoveredOptionIndex = -1;
            return;
        }

        int labelWidth = textRenderer.width(label + ": ");
        int boxX = x + labelWidth + 4;
        int boxWidth = width - labelWidth - 8;
        int listY = y + HEADER_HEIGHT;

        if (mouseX >= boxX && mouseX <= boxX + boxWidth &&
            mouseY >= listY && mouseY <= listY + options.size() * OPTION_HEIGHT + DROPDOWN_PADDING * 2) {

            int relativeY = mouseY - listY - DROPDOWN_PADDING;
            int index = relativeY / OPTION_HEIGHT;

            if (index >= 0 && index < options.size()) {
                hoveredOptionIndex = index;
            } else {
                hoveredOptionIndex = -1;
            }
        } else {
            hoveredOptionIndex = -1;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible || !enabled || button != 0) return false;

        int labelWidth = textRenderer.width(label + ": ");
        int boxX = x + labelWidth + 4;
        int boxWidth = width - labelWidth - 8;
        int boxY = y + 2;
        int boxHeight = HEADER_HEIGHT - 4;

        // Check header click (toggle dropdown)
        if (mouseX >= boxX && mouseX <= boxX + boxWidth &&
            mouseY >= boxY && mouseY <= boxY + boxHeight) {

            if (expanded) {
                close();
            } else {
                open();
            }
            return true;
        }

        // Check option click (if expanded)
        if (expanded && hoveredOptionIndex >= 0 && hoveredOptionIndex < options.size()) {
            selectedIndex = hoveredOptionIndex;
            if (onChange != null) {
                onChange.accept(options.get(selectedIndex));
            }
            close();
            return true;
        }

        // Click outside - close dropdown
        if (expanded) {
            close();
            return true;
        }

        return false;
    }

    private void open() {
        // Close any other open dropdown
        if (currentlyOpen != null && currentlyOpen != this) {
            currentlyOpen.close();
        }
        expanded = true;
        currentlyOpen = this;
    }

    private void close() {
        expanded = false;
        if (currentlyOpen == this) {
            currentlyOpen = null;
        }
    }

    /**
     * Close dropdown if click was outside
     * Call this from the parent container's click handler
     */
    public static void closeIfClickedOutside(double mouseX, double mouseY) {
        if (currentlyOpen != null) {
            // The actual closing is handled in mouseClicked
        }
    }

    /**
     * Force close any open dropdown
     */
    public static void forceCloseAll() {
        if (currentlyOpen != null) {
            currentlyOpen.close();
        }
    }

    public boolean isExpanded() {
        return expanded;
    }

    private void drawBorder(GuiGraphics context, int x, int y, int w, int h, int color) {
        context.fill(x, y, x + w, y + 1, color); // Top
        context.fill(x, y + h - 1, x + w, y + h, color); // Bottom
        context.fill(x, y, x + 1, y + h, color); // Left
        context.fill(x + w - 1, y, x + w, y + h, color); // Right
    }

    private String truncateText(String text, int maxWidth) {
        if (textRenderer.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int ellipsisWidth = textRenderer.width(ellipsis);
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (textRenderer.width(sb.toString()) + textRenderer.width(String.valueOf(c)) + ellipsisWidth > maxWidth) {
                break;
            }
            sb.append(c);
        }
        return sb + ellipsis;
    }

    public void setSelectedIndex(int index) {
        if (index >= 0 && index < options.size()) {
            selectedIndex = index;
        }
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    public String getSelectedOption() {
        return options.get(selectedIndex);
    }
}
