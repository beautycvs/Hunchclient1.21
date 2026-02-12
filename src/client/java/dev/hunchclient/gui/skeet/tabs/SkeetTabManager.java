package dev.hunchclient.gui.skeet.tabs;

import dev.hunchclient.gui.skeet.SkeetTheme;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Manages tabs in the Skeet GUI
 */
public class SkeetTabManager {

    private final List<SkeetTab> tabs = new ArrayList<>();
    private int selectedTab = -1; // -1 = none selected (fixes init bug)
    private final Font textRenderer;

    // Tab bar layout
    private int tabBarX, tabBarY;
    private final int tabWidth = SkeetTheme.TAB_WIDTH;
    private final int tabHeight = SkeetTheme.TAB_HEIGHT;

    // Animation
    private int hoveredTab = -1;
    private float hoverAmount = 0.0f;
    private final float hoverSpeed = 0.15f;

    public SkeetTabManager() {
        this.textRenderer = Minecraft.getInstance().font;
    }

    /**
     * Add a tab
     */
    public void addTab(SkeetTab tab) {
        tabs.add(tab);
    }

    /**
     * Set tab bar position
     */
    public void setTabBarPosition(int x, int y) {
        this.tabBarX = x;
        this.tabBarY = y;
    }

    /**
     * Render tabs
     */
    public void renderTabs(GuiGraphics context, int mouseX, int mouseY, float delta) {
        // Update hover
        int newHovered = -1;
        for (int i = 0; i < tabs.size(); i++) {
            int tabY = tabBarY + (i * tabHeight);
            if (mouseX >= tabBarX && mouseX <= tabBarX + tabWidth &&
                mouseY >= tabY && mouseY <= tabY + tabHeight) {
                newHovered = i;
                break;
            }
        }

        if (newHovered != hoveredTab) {
            hoveredTab = newHovered;
        }

        // Update hover animation
        if (hoveredTab >= 0) {
            hoverAmount = Math.min(1.0f, hoverAmount + hoverSpeed);
        } else {
            hoverAmount = Math.max(0.0f, hoverAmount - hoverSpeed);
        }

        // Render each tab
        for (int i = 0; i < tabs.size(); i++) {
            SkeetTab tab = tabs.get(i);
            int tabY = tabBarY + (i * tabHeight);

            boolean isSelected = (i == selectedTab);
            boolean isHovered = (i == hoveredTab);

            renderTab(context, tab, tabBarX, tabY, isSelected, isHovered);
        }

        // Vertical separator (right of tabs)
        context.fill(tabBarX + tabWidth, tabBarY, tabBarX + tabWidth + 1, tabBarY + tabs.size() * tabHeight, SkeetTheme.BORDER_DEFAULT());
    }

    /**
     * Render a single tab
     */
    private void renderTab(GuiGraphics context, SkeetTab tab, int x, int y, boolean selected, boolean hovered) {
        // Background
        int bgColor;
        if (selected) {
            bgColor = SkeetTheme.BG_ACTIVE();
        } else if (hovered) {
            bgColor = SkeetTheme.blend(SkeetTheme.BG_SECONDARY(), SkeetTheme.BG_HOVER(), 0.3f);
        } else {
            bgColor = SkeetTheme.BG_SECONDARY();
        }

        context.fill(x, y, x + tabWidth, y + tabHeight, bgColor);

        // Accent bar (left side) for selected tab
        if (selected) {
            context.fill(x, y, x + 3, y + tabHeight, SkeetTheme.ACCENT_PRIMARY());
        }

        // Icon and text
        int textColor = selected ? SkeetTheme.TEXT_PRIMARY() : SkeetTheme.TEXT_SECONDARY();
        String text = tab.getIcon() + " " + tab.getName();
        context.drawString(textRenderer, text, x + 15, y + 10, textColor, false);
    }

    /**
     * Render current tab content
     */
    public void renderContent(GuiGraphics context, int contentX, int contentY, int contentWidth, int contentHeight, int mouseX, int mouseY, float delta) {
        if (selectedTab >= 0 && selectedTab < tabs.size()) {
            SkeetTab tab = tabs.get(selectedTab);
            tab.setBounds(contentX, contentY, contentWidth, contentHeight);
            tab.render(context, mouseX, mouseY, delta);
        }
    }

    /**
     * Handle tab click
     */
    public boolean handleTabClick(double mouseX, double mouseY, int button) {
        for (int i = 0; i < tabs.size(); i++) {
            int tabY = tabBarY + (i * tabHeight);

            if (mouseX >= tabBarX && mouseX <= tabBarX + tabWidth &&
                mouseY >= tabY && mouseY <= tabY + tabHeight) {
                selectTab(i);
                return true;
            }
        }
        return false;
    }

    /**
     * Handle content click (forward to active tab)
     */
    public boolean handleContentClick(double mouseX, double mouseY, int button) {
        if (selectedTab >= 0 && selectedTab < tabs.size()) {
            return tabs.get(selectedTab).mouseClicked(mouseX, mouseY, button);
        }
        return false;
    }

    /**
     * Handle content scroll (forward to active tab)
     */
    public boolean handleContentScroll(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (selectedTab >= 0 && selectedTab < tabs.size()) {
            return tabs.get(selectedTab).mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        return false;
    }

    /**
     * Select a tab by index
     */
    public void selectTab(int index) {
        if (index >= 0 && index < tabs.size() && index != selectedTab) {
            if (selectedTab >= 0 && selectedTab < tabs.size()) {
                tabs.get(selectedTab).onDeselected();
            }

            selectedTab = index;
            tabs.get(selectedTab).onSelected();
        }
    }

    /**
     * Get current tab index
     */
    public int getSelectedTab() {
        return selectedTab;
    }

    /**
     * Get current tab instance
     */
    public SkeetTab getCurrentTab() {
        if (selectedTab >= 0 && selectedTab < tabs.size()) {
            return tabs.get(selectedTab);
        }
        return null;
    }

    /**
     * Get tab count
     */
    public int getTabCount() {
        return tabs.size();
    }

    /**
     * Forward character typing to current tab
     */
    public boolean charTyped(char chr, int modifiers) {
        SkeetTab currentTab = getCurrentTab();
        if (currentTab instanceof ModulesTab modulesTab) {
            return modulesTab.charTyped(chr, modifiers);
        }
        return false;
    }

    /**
     * Forward key press to current tab
     */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        SkeetTab currentTab = getCurrentTab();
        if (currentTab instanceof ModulesTab modulesTab) {
            return modulesTab.keyPressed(keyCode, scanCode, modifiers);
        }
        return false;
    }
}
