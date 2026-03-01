package dev.hunchclient.gui.skeet.tabs;

import dev.hunchclient.gui.skeet.SkeetTheme;
import dev.hunchclient.gui.skeet.components.SkeetModuleCard;
import dev.hunchclient.module.Module;
import dev.hunchclient.module.ModuleManager;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Tab for displaying modules by category
 */
public class ModulesTab extends SkeetTab {

    private final Module.Category category;
    private final ModuleManager moduleManager;
    private final Font textRenderer;

    private final List<SkeetModuleCard> moduleCards = new ArrayList<>();
    private int scrollOffset = 0;
    private int maxScroll = 0;

    // Scrollbar dragging state
    private boolean draggingScrollbar = false;
    private int scrollbarDragStartY = 0;
    private int scrollbarDragStartOffset = 0;
    private int lastContentHeight = 0;
    private int lastScrollbarHeight = 0;

    public ModulesTab(String name, String icon, Module.Category category) {
        super(name, icon);
        this.category = category;
        this.moduleManager = ModuleManager.getInstance();
        this.textRenderer = Minecraft.getInstance().font;
    }

    @Override
    public void onSelected() {
        // Rebuild module cards
        moduleCards.clear();
        List<Module> modules = moduleManager.getModulesByCategory(category);

        int cardY = y + SkeetTheme.SPACING_MD;
        for (Module module : modules) {
            SkeetModuleCard card = new SkeetModuleCard(
                x + SkeetTheme.SPACING_MD,
                cardY,
                width - SkeetTheme.SPACING_MD * 2,
                module
            );
            moduleCards.add(card);
            cardY += 35 + SkeetTheme.SPACING_SM; // Card height + spacing
        }

        // Restore saved scroll position
        scrollOffset = dev.hunchclient.util.GuiConfig.getInstance().getScrollPosition(category.name());
    }

    @Override
    public void onDeselected() {
        // Save scroll position when tab is deselected
        dev.hunchclient.util.GuiConfig.getInstance().setScrollPosition(category.name(), scrollOffset);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
// Title
context.drawString(textRenderer, name.toUpperCase(), x + SkeetTheme.SPACING_MD, y + 5, SkeetTheme.ACCENT_PRIMARY(), false);

// Separator
context.fill(x + SkeetTheme.SPACING_MD, y + 28, x + width - SkeetTheme.SPACING_MD, y + 29, SkeetTheme.BORDER_DEFAULT());

// Scrollable content area
int contentY = y + 38;
int contentHeight = height - 48;

        // Enable scissor for clipping
        context.enableScissor(x, contentY, x + width, contentY + contentHeight);

        // Render module cards
        int currentY = contentY - scrollOffset;
        int totalContentHeight = 0;
        int cardWidth = width - SkeetTheme.SPACING_MD * 2;
        for (SkeetModuleCard card : moduleCards) {
            // IMPORTANT: Update position AND size (fixes clickability bug)
            card.setPosition(x + SkeetTheme.SPACING_MD, currentY);
            card.setSize(cardWidth, card.getHeight());
            card.render(context, mouseX, mouseY, delta);

            int cardHeight = card.getTotalHeight();
            currentY += cardHeight + SkeetTheme.SPACING_SM;
            totalContentHeight += cardHeight + SkeetTheme.SPACING_SM;
        }

        // Calculate max scroll (use actual card heights)
        maxScroll = Math.max(0, totalContentHeight - contentHeight);

        context.disableScissor();

        // Scrollbar
        if (maxScroll > 0) {
            lastContentHeight = totalContentHeight;
            lastScrollbarHeight = contentHeight;
            drawScrollbar(context, x + width - 6, contentY, contentHeight, totalContentHeight);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Check if click is within content bounds
        if (mouseX < x || mouseX > x + width || mouseY < y || mouseY > y + height) {
            return false;
        }

        // Check if clicking scrollbar area (only left click)
        if (button == 0 && maxScroll > 0) {
            int scrollbarX = x + width - 6;
            int contentY = y + 30;
            int contentHeight = height - 40;

            if (mouseX >= scrollbarX && mouseX <= scrollbarX + SkeetTheme.SCROLLBAR_WIDTH) {
                // Calculate thumb position and size
                float thumbHeight = Math.max(20, (float) contentHeight * contentHeight / lastContentHeight);
                float scrollPercentage = maxScroll > 0 ? (float) scrollOffset / maxScroll : 0;
                int thumbY = contentY + (int) (scrollPercentage * (contentHeight - thumbHeight));

                // Check if clicking on thumb
                if (mouseY >= thumbY && mouseY <= thumbY + thumbHeight) {
                    // Start dragging from thumb
                    draggingScrollbar = true;
                    scrollbarDragStartY = (int) mouseY;
                    scrollbarDragStartOffset = scrollOffset;
                    return true;
                } else if (mouseY >= contentY && mouseY <= contentY + contentHeight) {
                    // Click on track - jump to position
                    float clickPercentage = (float) (mouseY - contentY) / contentHeight;
                    scrollOffset = (int) (clickPercentage * maxScroll);
                    scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
                    dev.hunchclient.util.GuiConfig.getInstance().setScrollPosition(category.name(), scrollOffset);
                    return true;
                }
            }
        }

        // Forward to module cards
        for (SkeetModuleCard card : moduleCards) {
            if (card.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // Stop scrollbar dragging
        if (draggingScrollbar && button == 0) {
            draggingScrollbar = false;
            return true;
        }

        for (SkeetModuleCard card : moduleCards) {
            if (card.mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        // Handle scrollbar dragging
        if (draggingScrollbar && button == 0) {
            int contentHeight = height - 40;
            float thumbHeight = Math.max(20, (float) contentHeight * contentHeight / lastContentHeight);
            float scrollableArea = contentHeight - thumbHeight;

            // Calculate new scroll position based on mouse movement
            int deltaMouseY = (int) mouseY - scrollbarDragStartY;
            float scrollDelta = (deltaMouseY / scrollableArea) * maxScroll;
            scrollOffset = (int) (scrollbarDragStartOffset + scrollDelta);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));

            // Save scroll position
            dev.hunchclient.util.GuiConfig.getInstance().setScrollPosition(category.name(), scrollOffset);
            return true;
        }

        for (SkeetModuleCard card : moduleCards) {
            if (card.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height) {
            scrollOffset = (int) Math.max(0, Math.min(maxScroll, scrollOffset - verticalAmount * 30));

            // Save scroll position
            dev.hunchclient.util.GuiConfig.getInstance().setScrollPosition(category.name(), scrollOffset);

            return true;
        }
        return false;
    }

    /**
     * Forward character typing to module cards (for text fields)
     */
    public boolean charTyped(char chr, int modifiers) {
        for (SkeetModuleCard card : moduleCards) {
            if (card.charTyped(chr, modifiers)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Forward key press to module cards (for text fields)
     */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (SkeetModuleCard card : moduleCards) {
            if (card.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return false;
    }

    private void drawScrollbar(GuiGraphics context, int scrollbarX, int scrollbarY, int scrollbarHeight, int totalContentHeight) {
        // Background
        context.fill(scrollbarX, scrollbarY, scrollbarX + SkeetTheme.SCROLLBAR_WIDTH, scrollbarY + scrollbarHeight, SkeetTheme.BG_FIELD());

        // Thumb - use actual total content height (not fixed card height!)
        float scrollPercentage = (float) scrollOffset / maxScroll;
        float thumbHeight = Math.max(20, (float) scrollbarHeight * scrollbarHeight / totalContentHeight);
        int thumbY = scrollbarY + (int) (scrollPercentage * (scrollbarHeight - thumbHeight));

        // Clamp thumb to scrollbar bounds
        thumbHeight = Math.min(thumbHeight, scrollbarHeight);
        thumbY = Math.max(scrollbarY, Math.min(thumbY, scrollbarY + scrollbarHeight - (int) thumbHeight));

        context.fill(scrollbarX, thumbY, scrollbarX + SkeetTheme.SCROLLBAR_WIDTH, thumbY + (int) thumbHeight, SkeetTheme.ACCENT_PRIMARY());
    }
}
