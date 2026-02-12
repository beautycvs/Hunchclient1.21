package dev.hunchclient.gui.skeet.components;

import dev.hunchclient.gui.skeet.SkeetTheme;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Skeet-styled dropdown selector
 */
public class SkeetDropdown extends SkeetComponent {

    private final String label;
    private final List<String> options;
    private int selectedIndex;
    private final Consumer<String> onChange;
    private final Font textRenderer;

    private float hoverAmount = 0.0f;
    private boolean hoveringPrev = false;
    private boolean hoveringNext = false;
    private final float hoverSpeed = 0.15f;

    private static final int HEIGHT = 16;
    private static final int BUTTON_WIDTH = 20;

    public SkeetDropdown(int x, int y, int width, String label, List<String> options, int initialIndex, Consumer<String> onChange) {
        super(x, y, width, HEIGHT);
        this.label = label;
        this.options = options;
        this.selectedIndex = Math.max(0, Math.min(initialIndex, options.size() - 1));
        this.onChange = onChange;
        this.textRenderer = Minecraft.getInstance().font;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        if (!visible || options.isEmpty()) return;

        // Update hover states
        updateHover(mouseX, mouseY);

        int prevX = x + width - BUTTON_WIDTH * 2 - 4;
        int nextX = x + width - BUTTON_WIDTH;

        hoveringPrev = enabled && mouseX >= prevX && mouseX <= prevX + BUTTON_WIDTH &&
                      mouseY >= y && mouseY <= y + height;
        hoveringNext = enabled && mouseX >= nextX && mouseX <= nextX + BUTTON_WIDTH &&
                      mouseY >= y && mouseY <= y + height;

        if (hoveringPrev || hoveringNext) {
            hoverAmount = Math.min(1.0f, hoverAmount + hoverSpeed);
        } else {
            hoverAmount = Math.max(0.0f, hoverAmount - hoverSpeed);
        }

        // Label
        int textColor = enabled ? SkeetTheme.TEXT_PRIMARY() : SkeetTheme.TEXT_DISABLED();
        context.drawString(textRenderer, label + ":", x, y + (height - textRenderer.lineHeight) / 2, textColor, false);

        // Selected value
        String selected = options.get(selectedIndex);
        int valueColor = enabled ? SkeetTheme.ACCENT_PRIMARY() : SkeetTheme.TEXT_DISABLED();
        int labelWidth = textRenderer.width(label + ": ");
        context.drawString(textRenderer, selected, x + labelWidth, y + (height - textRenderer.lineHeight) / 2, valueColor, false);

        // Previous button [<]
        int prevColor = hoveringPrev && enabled ? SkeetTheme.ACCENT_PRIMARY() : SkeetTheme.TEXT_SECONDARY();
        if (!enabled) prevColor = SkeetTheme.TEXT_DISABLED();
        context.drawString(textRenderer, "[<]", prevX, y + (height - textRenderer.lineHeight) / 2, prevColor, false);

        // Next button [>]
        int nextColor = hoveringNext && enabled ? SkeetTheme.ACCENT_PRIMARY() : SkeetTheme.TEXT_SECONDARY();
        if (!enabled) nextColor = SkeetTheme.TEXT_DISABLED();
        context.drawString(textRenderer, "[>]", nextX, y + (height - textRenderer.lineHeight) / 2, nextColor, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible || !enabled || button != 0) return false;

        int prevX = x + width - BUTTON_WIDTH * 2 - 4;
        int nextX = x + width - BUTTON_WIDTH;

        if (mouseX >= prevX && mouseX <= prevX + BUTTON_WIDTH && mouseY >= y && mouseY <= y + height) {
            cyclePrevious();
            return true;
        }

        if (mouseX >= nextX && mouseX <= nextX + BUTTON_WIDTH && mouseY >= y && mouseY <= y + height) {
            cycleNext();
            return true;
        }

        return false;
    }

    public void cyclePrevious() {
        selectedIndex = (selectedIndex - 1 + options.size()) % options.size();
        if (onChange != null) {
            onChange.accept(options.get(selectedIndex));
        }
    }

    public void cycleNext() {
        selectedIndex = (selectedIndex + 1) % options.size();
        if (onChange != null) {
            onChange.accept(options.get(selectedIndex));
        }
    }

    public void setSelectedIndex(int index) {
        if (index >= 0 && index < options.size()) {
            selectedIndex = index;
            if (onChange != null) {
                onChange.accept(options.get(selectedIndex));
            }
        }
    }

    public String getSelectedOption() {
        return options.get(selectedIndex);
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }
}
