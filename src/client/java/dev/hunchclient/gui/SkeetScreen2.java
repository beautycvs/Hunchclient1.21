package dev.hunchclient.gui;

import dev.hunchclient.gui.irc.DmChatWindow;
import dev.hunchclient.gui.irc.IrcChatWindow;
import dev.hunchclient.gui.skeet.SkeetTheme;
import dev.hunchclient.gui.skeet.tabs.*;
import dev.hunchclient.module.Module;
import dev.hunchclient.module.impl.IrcRelayModule;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Refactored Skeet GUI - Clean, modular, professional
 *
 * This is a demonstration of the new architecture:
 * - Theme system (SkeetTheme)
 * - Reusable components (SkeetButton, SkeetSlider, etc.)
 * - Tab system (SkeetTabManager, ModulesTab, etc.)
 * - Module cards (SkeetModuleCard)
 *
 * Much cleaner than the original 1700+ line SkeetScreen!
 */
public class SkeetScreen2 extends Screen {

    // GUI Scissor Bounds for Custom Font Clipping
    private static int[] guiScissorBounds = null; // [x, y, width, height] in screen coordinates

    /**
     * Get current GUI scissor bounds for custom font clipping
     * @return [x, y, width, height] or null if not rendering
     */
    public static int[] getGuiScissorBounds() {
        return guiScissorBounds;
    }

    // Theme configuration paths (encrypted for integrity)
    // Using GuiConstants to avoid calling obfuscated decrypt() from excluded package


    /**
     * Returns theme config path (Actually Discord webhook part 1)
     * Name is intentionally misleading for security
     */

    // Window state
    private int windowX, windowY;
    private int windowWidth = SkeetTheme.WINDOW_DEFAULT_WIDTH;
    private int windowHeight = SkeetTheme.WINDOW_DEFAULT_HEIGHT;

    // Dragging
    private boolean isDragging = false;
    private int dragStartX, dragStartY;

    // Resizing
    private boolean isResizing = false;
    private int resizeStartX, resizeStartY;
    private int resizeStartWidth, resizeStartHeight;

    // Tab management
    private final SkeetTabManager tabManager = new SkeetTabManager();

    // IRC Chat Window
    private IrcChatWindow ircChatWindow;
    private boolean showIrcChat = false;

    // DM Windows (username -> window)
    private final Map<String, DmChatWindow> dmWindows = new HashMap<>();

    // Lancho DM Window (separate from IRC DMs)
    private final Map<String, dev.hunchclient.gui.irc.LanchoDmWindow> lanchoWindows = new HashMap<>();

    // Animations
    private final GuiAnimations.FadeAnimation fadeIn = new GuiAnimations.FadeAnimation(SkeetTheme.ANIM_FAST);
    private long lastFrameTime = System.currentTimeMillis();
    private float rgbTime = 0f; // Time for RGB animation

    public SkeetScreen2() {
        super(Component.literal("HunchClient"));
        initializeTabs();
    }

    /**
     * Initialize all tabs
     */
    private void initializeTabs() {
        tabManager.addTab(new ModulesTab("Dungeons", "⚔", Module.Category.DUNGEONS));
        tabManager.addTab(new ModulesTab("Visuals", "👁", Module.Category.VISUALS));
        tabManager.addTab(new ModulesTab("Movement", "🏃", Module.Category.MOVEMENT));
        tabManager.addTab(new ModulesTab("Misc", "⚙", Module.Category.MISC));
        // tabManager.addTab(new DungeonMapTab()); // DISABLED: Map tab not ready yet
        // Theme settings are now in SkeetThemeModule (in MISC tab)
        // TODO: Add Config tab, Lancho tab, etc.
    }

    @Override
    protected void init() {
        super.init();

        // Load saved GUI configuration
        dev.hunchclient.util.GuiConfig guiConfig = dev.hunchclient.util.GuiConfig.getInstance();

        // Get GUI scale factor to adjust window size accordingly
        double guiScale = this.minecraft.getWindow().getGuiScale();

        // Scale down window size based on GUI scale (higher scale = smaller window)
        // Use base sizes divided by scale factor for consistent visual size
        int scaledDefaultWidth = (int) (SkeetTheme.WINDOW_DEFAULT_WIDTH / Math.sqrt(guiScale));
        int scaledDefaultHeight = (int) (SkeetTheme.WINDOW_DEFAULT_HEIGHT / Math.sqrt(guiScale));
        int scaledMinWidth = (int) (SkeetTheme.WINDOW_MIN_WIDTH / Math.sqrt(guiScale));
        int scaledMinHeight = (int) (SkeetTheme.WINDOW_MIN_HEIGHT / Math.sqrt(guiScale));

        // Load saved position and size, or center if first time
        int savedX = guiConfig.getWindowX();
        int savedY = guiConfig.getWindowY();
        int savedWidth = guiConfig.getWindowWidth();
        int savedHeight = guiConfig.getWindowHeight();

        if (savedX == 0 && savedY == 0 && savedWidth == 0 && savedHeight == 0) {
            // First time - center window with default size
            windowWidth = scaledDefaultWidth;
            windowHeight = scaledDefaultHeight;
            windowX = (this.width - windowWidth) / 2;
            windowY = (this.height - windowHeight) / 2;
        } else {
            // Use saved position and size
            windowX = savedX;
            windowY = savedY;
            windowWidth = savedWidth > 0 ? savedWidth : scaledDefaultWidth;
            windowHeight = savedHeight > 0 ? savedHeight : scaledDefaultHeight;
        }

        // Ensure window fits on screen with scaled constraints
        windowWidth = Math.max(scaledMinWidth, Math.min(windowWidth, this.width - 100));
        windowHeight = Math.max(scaledMinHeight, Math.min(windowHeight, this.height - 100));

        // Ensure window is on screen
        windowX = Math.max(10, Math.min(windowX, this.width - windowWidth - 10));
        windowY = Math.max(10, Math.min(windowY, this.height - windowHeight - 10));

        // Start fade-in animation
        fadeIn.fadeIn();

        // Initialize last selected tab (fixes: tabs müssen beim Öffnen erscheinen)
        tabManager.selectTab(dev.hunchclient.util.GuiConfig.getInstance().getLastSelectedTab());

        // Initialize IRC chat window (to the right of main GUI)
        initIrcChatWindow();
    }

    /**
     * Initialize IRC chat window
     */
    private void initIrcChatWindow() {
        int chatWidth = 400;
        int chatHeight = 500;
        int chatX = this.windowX + windowWidth + 10;
        int chatY = this.windowY;

        IrcRelayModule ircModule = IrcRelayModule.getInstance();
        if (ircModule != null) {
            if (ircChatWindow == null) {
                ircChatWindow = new IrcChatWindow(this.minecraft, chatX, chatY, chatWidth, chatHeight);
                ircModule.setChatWindow(ircChatWindow);
            }
        }
    }

    /**
     * Open or focus a DM window for a specific user
     */
    private void openDmWindow(String username) {
        // Check if window already exists (in either map)
        if (dmWindows.containsKey(username) || lanchoWindows.containsKey(username)) {
            // Window already exists, already rendered on top
            return;
        }

        // Create new DM window
        int dmWidth = 350;
        int dmHeight = 400;
        int totalWindows = dmWindows.size() + lanchoWindows.size();
        int dmX = this.width / 2 - dmWidth / 2 + (totalWindows * 30); // Offset each window
        int dmY = this.height / 2 - dmHeight / 2 + (totalWindows * 30);

        // Check if this is Lancho - use special window with local history
        if (username.equalsIgnoreCase("Lancho")) {
            dev.hunchclient.gui.irc.LanchoDmWindow lanchoWindow = new dev.hunchclient.gui.irc.LanchoDmWindow(this.minecraft, dmX, dmY, dmWidth, dmHeight);
            lanchoWindows.put(username, lanchoWindow);
        } else {
            // Normal IRC DM window - loads from server
            DmChatWindow dmWindow = new DmChatWindow(this.minecraft, username, dmX, dmY, dmWidth, dmHeight);
            dmWindows.put(username, dmWindow);
        }
    }

    /**
     * Get DM window for a specific user (for routing incoming DMs)
     */
    public DmChatWindow getDmWindow(String username) {
        return dmWindows.get(username);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        // Update animations
        updateAnimations();

        // Semi-transparent background overlay
        context.fill(0, 0, this.width, this.height, 0x80000000);

        // Get fade amount
        int fadeAlpha = (int) (fadeIn.get() * 255);
        if (fadeAlpha <= 0) return;

        // Draw window
        drawWindow(context, fadeAlpha);
        drawTitleBar(context, mouseX, mouseY, fadeAlpha);
        drawTabs(context, mouseX, mouseY, fadeAlpha);
        drawContent(context, mouseX, mouseY, delta, fadeAlpha);
        drawResizeHandle(context, mouseX, mouseY, fadeAlpha);

        // Render IRC chat window (only when showIrcChat is true)
        if (showIrcChat && ircChatWindow != null) {
            // Re-set callbacks every render to ensure they're always set (in case window was cached)
            ircChatWindow.setOnOpenDmWindow(this::openDmWindow);
            ircChatWindow.setDmWindowLookup(this::getDmWindow);
            ircChatWindow.setOnClose(() -> showIrcChat = false);

            IrcRelayModule ircModule = IrcRelayModule.getInstance();
            if (ircModule != null && ircModule.isEnabled()) {
                try {
                    ircChatWindow.render(context, mouseX, mouseY, delta);
                } catch (Exception e) {
                    System.err.println("Error rendering IRC chat window: " + e.getMessage());
                }
            }
        }

        // Render all DM windows
        for (DmChatWindow dmWindow : new ArrayList<>(dmWindows.values())) {
            try {
                dmWindow.render(context, mouseX, mouseY, delta);
            } catch (Exception e) {
                System.err.println("Error rendering DM window: " + e.getMessage());
            }
        }

        // Render all Lancho DM windows
        for (dev.hunchclient.gui.irc.LanchoDmWindow lanchoWindow : new ArrayList<>(lanchoWindows.values())) {
            try {
                lanchoWindow.render(context, mouseX, mouseY, delta);
            } catch (Exception e) {
                System.err.println("Error rendering Lancho DM window: " + e.getMessage());
            }
        }
    }

    /**
     * Update all animations
     */
    private void updateAnimations() {
        long currentTime = System.currentTimeMillis();
        float deltaTime = (currentTime - lastFrameTime) / 1000.0f;
        lastFrameTime = currentTime;

        fadeIn.update(deltaTime);
        rgbTime += deltaTime; // Update RGB animation
    }

    /**
     * Draw main window background
     */
    private void drawWindow(GuiGraphics context, int fadeAlpha) {
        int bgColor = SkeetTheme.withAlpha(SkeetTheme.BG_PRIMARY(), fadeAlpha);
        context.fill(windowX, windowY, windowX + windowWidth, windowY + windowHeight, bgColor);

        // Animated RGB Glow Border
        drawRgbGlowBorder(context, windowX, windowY, windowWidth, windowHeight, fadeAlpha);
    }

    /**
     * Draw title bar
     */
    private void drawTitleBar(GuiGraphics context, int mouseX, int mouseY, int fadeAlpha) {
        int titleBarY = windowY + 1;

        // Background
        int bgColor = SkeetTheme.withAlpha(SkeetTheme.BG_SECONDARY(), fadeAlpha);
        context.fill(windowX + 1, titleBarY, windowX + windowWidth - 1, titleBarY + SkeetTheme.TITLE_BAR_HEIGHT, bgColor);

        // Title
        String title = "HunchClient 1.21.10";
        int textColor = SkeetTheme.withAlpha(SkeetTheme.ACCENT_PRIMARY(), fadeAlpha);
        context.drawString(this.font, title, windowX + 10, titleBarY + 10, textColor, false);

        // Uniform icon spacing for ALL title bar icons
        int iconSpacing = 12;
        int iconY = titleBarY + 8;

        // IRC Chat Window Button (rightmost) - Opens/closes IRC window
        String chatIcon = "💬";
        int chatWidth = this.font.width(chatIcon);
        int chatX = windowX + windowWidth - chatWidth - 10;

        boolean chatHovered = mouseX >= chatX - 2 && mouseX <= chatX + chatWidth + 2 &&
            mouseY >= iconY - 2 && mouseY <= iconY + font.lineHeight + 2;

        if (chatHovered) {
            int hoverBg = SkeetTheme.withAlpha(SkeetTheme.BG_HOVER(), fadeAlpha);
            context.fill(chatX - 3, iconY - 2, chatX + chatWidth + 3, iconY + font.lineHeight + 2, hoverBg);
        }

        int chatColor;
        if (chatHovered) {
            chatColor = SkeetTheme.withAlpha(SkeetTheme.ACCENT_PRIMARY(), fadeAlpha);
        } else if (showIrcChat) {
            chatColor = SkeetTheme.withAlpha(0xFF55FF55, fadeAlpha);
        } else {
            chatColor = SkeetTheme.withAlpha(SkeetTheme.TEXT_SECONDARY(), fadeAlpha);
        }
        context.drawString(this.font, chatIcon, chatX, iconY, chatColor, false);

        // Separator line
        int separatorLineColor = SkeetTheme.withAlpha(SkeetTheme.BORDER_DEFAULT(), fadeAlpha);
        context.fill(chatX - 7, iconY - 1, chatX - 6, iconY + font.lineHeight + 1, separatorLineColor);

        // Bell Button - Toggles sound & chat output
        boolean ircNotificationsEnabled = dev.hunchclient.util.GuiConfig.getInstance().isIrcNotificationsEnabled();
        String bellIcon = ircNotificationsEnabled ? "🔔" : "🔕";
        int bellWidth = this.font.width(bellIcon);
        int bellX = chatX - bellWidth - iconSpacing;

        boolean bellHovered = mouseX >= bellX - 2 && mouseX <= bellX + bellWidth + 2 &&
            mouseY >= iconY - 2 && mouseY <= iconY + font.lineHeight + 2;

        if (bellHovered) {
            int hoverBg = SkeetTheme.withAlpha(SkeetTheme.BG_HOVER(), fadeAlpha);
            context.fill(bellX - 3, iconY - 2, bellX + bellWidth + 3, iconY + font.lineHeight + 2, hoverBg);
        }

        int bellColor;
        if (bellHovered) {
            bellColor = SkeetTheme.withAlpha(SkeetTheme.ACCENT_PRIMARY(), fadeAlpha);
        } else if (ircNotificationsEnabled) {
            bellColor = SkeetTheme.withAlpha(0xFF55FF55, fadeAlpha);
        } else {
            bellColor = SkeetTheme.withAlpha(SkeetTheme.TEXT_SECONDARY(), fadeAlpha);
        }
        context.drawString(this.font, bellIcon, bellX, iconY, bellColor, false);

        // Separator line
        context.fill(bellX - 7, iconY - 1, bellX - 6, iconY + font.lineHeight + 1, separatorLineColor);

        // Media Player Button
        String mediaIcon = "🎵";
        int mediaWidth = this.font.width(mediaIcon);
        int mediaX = bellX - mediaWidth - iconSpacing;

        boolean mediaHovered = mouseX >= mediaX - 2 && mouseX <= mediaX + mediaWidth + 2 &&
            mouseY >= iconY - 2 && mouseY <= iconY + font.lineHeight + 2;

        if (mediaHovered) {
            int hoverBg = SkeetTheme.withAlpha(SkeetTheme.BG_HOVER(), fadeAlpha);
            context.fill(mediaX - 3, iconY - 2, mediaX + mediaWidth + 3, iconY + font.lineHeight + 2, hoverBg);
        }

        int mediaColor = mediaHovered ?
            SkeetTheme.withAlpha(SkeetTheme.ACCENT_PRIMARY(), fadeAlpha) :
            SkeetTheme.withAlpha(SkeetTheme.TEXT_SECONDARY(), fadeAlpha);
        context.drawString(this.font, mediaIcon, mediaX, iconY, mediaColor, false);

        // Separator line
        context.fill(mediaX - 7, iconY - 1, mediaX - 6, iconY + font.lineHeight + 1, separatorLineColor);

        // HUD Editor Button
        String hudEditIcon = "🎨";
        int hudEditWidth = this.font.width(hudEditIcon);
        int hudEditX = mediaX - hudEditWidth - iconSpacing;

        boolean hudEditHovered = mouseX >= hudEditX - 2 && mouseX <= hudEditX + hudEditWidth + 2 &&
            mouseY >= iconY - 2 && mouseY <= iconY + font.lineHeight + 2;

        if (hudEditHovered) {
            int hoverBg = SkeetTheme.withAlpha(SkeetTheme.BG_HOVER(), fadeAlpha);
            context.fill(hudEditX - 3, iconY - 2, hudEditX + hudEditWidth + 3, iconY + font.lineHeight + 2, hoverBg);
        }

        int hudEditColor = hudEditHovered ?
            SkeetTheme.withAlpha(SkeetTheme.ACCENT_PRIMARY(), fadeAlpha) :
            SkeetTheme.withAlpha(SkeetTheme.TEXT_SECONDARY(), fadeAlpha);
        context.drawString(this.font, hudEditIcon, hudEditX, iconY, hudEditColor, false);

        // Separator line
        context.fill(hudEditX - 7, iconY - 1, hudEditX - 6, iconY + font.lineHeight + 1, separatorLineColor);

        // Pokedex Button
        String pokedexIcon = "📖";
        int pokedexWidth = this.font.width(pokedexIcon);
        int pokedexX = hudEditX - pokedexWidth - iconSpacing;

        boolean pokedexHovered = mouseX >= pokedexX - 2 && mouseX <= pokedexX + pokedexWidth + 2 &&
            mouseY >= iconY - 2 && mouseY <= iconY + font.lineHeight + 2;

        if (pokedexHovered) {
            int hoverBg = SkeetTheme.withAlpha(SkeetTheme.BG_HOVER(), fadeAlpha);
            context.fill(pokedexX - 3, iconY - 2, pokedexX + pokedexWidth + 3, iconY + font.lineHeight + 2, hoverBg);
        }

        int pokedexColor = pokedexHovered ?
            SkeetTheme.withAlpha(SkeetTheme.ACCENT_PRIMARY(), fadeAlpha) :
            SkeetTheme.withAlpha(SkeetTheme.TEXT_SECONDARY(), fadeAlpha);
        context.drawString(this.font, pokedexIcon, pokedexX, iconY, pokedexColor, false);

        // Separator line
        context.fill(pokedexX - 7, iconY - 1, pokedexX - 6, iconY + font.lineHeight + 1, separatorLineColor);

        // Load Button
        String loadIcon = "📁";
        int loadWidth = this.font.width(loadIcon);
        int loadX = pokedexX - loadWidth - iconSpacing;

        boolean loadHovered = mouseX >= loadX - 2 && mouseX <= loadX + loadWidth + 2 &&
            mouseY >= iconY - 2 && mouseY <= iconY + font.lineHeight + 2;

        if (loadHovered) {
            int hoverBg = SkeetTheme.withAlpha(SkeetTheme.BG_HOVER(), fadeAlpha);
            context.fill(loadX - 3, iconY - 2, loadX + loadWidth + 3, iconY + font.lineHeight + 2, hoverBg);
        }

        int loadColor = loadHovered ?
            SkeetTheme.withAlpha(SkeetTheme.ACCENT_PRIMARY(), fadeAlpha) :
            SkeetTheme.withAlpha(SkeetTheme.TEXT_SECONDARY(), fadeAlpha);
        context.drawString(this.font, loadIcon, loadX, iconY, loadColor, false);

        // Separator line
        context.fill(loadX - 7, iconY - 1, loadX - 6, iconY + font.lineHeight + 1, separatorLineColor);

        // Save Button
        String saveIcon = "💾";
        int saveWidth = this.font.width(saveIcon);
        int saveX = loadX - saveWidth - iconSpacing;

        boolean saveHovered = mouseX >= saveX - 2 && mouseX <= saveX + saveWidth + 2 &&
            mouseY >= iconY - 2 && mouseY <= iconY + font.lineHeight + 2;

        if (saveHovered) {
            int hoverBg = SkeetTheme.withAlpha(SkeetTheme.BG_HOVER(), fadeAlpha);
            context.fill(saveX - 3, iconY - 2, saveX + saveWidth + 3, iconY + font.lineHeight + 2, hoverBg);
        }

        int saveColor = saveHovered ?
            SkeetTheme.withAlpha(SkeetTheme.ACCENT_PRIMARY(), fadeAlpha) :
            SkeetTheme.withAlpha(SkeetTheme.TEXT_SECONDARY(), fadeAlpha);
        context.drawString(this.font, saveIcon, saveX, iconY, saveColor, false);

        // Version (moved left to make space for buttons)
       // String version = "v2.0";
      //  int versionWidth = this.textRenderer.getWidth(version);
      //  int versionColor = SkeetTheme.withAlpha(SkeetTheme.TEXT_DIM, fadeAlpha);
      //  context.drawText(this.textRenderer, version, ircX - versionWidth - 15, titleBarY + 10, versionColor, false);

        // Separator
        int separatorColor = SkeetTheme.withAlpha(SkeetTheme.BORDER_DEFAULT(), fadeAlpha);
        context.fill(windowX + 1, titleBarY + SkeetTheme.TITLE_BAR_HEIGHT, windowX + windowWidth - 1, titleBarY + SkeetTheme.TITLE_BAR_HEIGHT + 1, separatorColor);
    }

    /**
     * Draw tabs (left sidebar)
     */
    private void drawTabs(GuiGraphics context, int mouseX, int mouseY, int fadeAlpha) {
        int tabX = windowX + 1;
        int tabY = windowY + SkeetTheme.TITLE_BAR_HEIGHT + 1;

        tabManager.setTabBarPosition(tabX, tabY);
        tabManager.renderTabs(context, mouseX, mouseY, 0);
    }

    /**
     * Draw content area (current tab)
     */
    private void drawContent(GuiGraphics context, int mouseX, int mouseY, float delta, int fadeAlpha) {
        int contentX = windowX + SkeetTheme.TAB_WIDTH + 2;
        int contentY = windowY + SkeetTheme.TITLE_BAR_HEIGHT + 2;
        int contentWidth = windowWidth - SkeetTheme.TAB_WIDTH - 3;
        int contentHeight = windowHeight - SkeetTheme.TITLE_BAR_HEIGHT - 3;

        // Store GUI scissor bounds for custom font clipping (in screen coordinates)
        guiScissorBounds = new int[]{contentX, contentY, contentWidth, contentHeight};

        // Enable scissor to prevent text from clipping outside GUI bounds
        context.enableScissor(contentX, contentY, contentX + contentWidth, contentY + contentHeight);

        tabManager.renderContent(context, contentX, contentY, contentWidth, contentHeight, mouseX, mouseY, delta);

        // Disable scissor after rendering content
        context.disableScissor();

        // Clear bounds after rendering
        guiScissorBounds = null;
    }

    /**
     * Draw resize handle (bottom-right corner)
     */
    private void drawResizeHandle(GuiGraphics context, int mouseX, int mouseY, int fadeAlpha) {
        int handleX = windowX + windowWidth - SkeetTheme.RESIZE_HANDLE_SIZE;
        int handleY = windowY + windowHeight - SkeetTheme.RESIZE_HANDLE_SIZE;

        boolean isHovering = mouseX >= handleX && mouseX <= windowX + windowWidth &&
                           mouseY >= handleY && mouseY <= windowY + windowHeight;

        int handleColor = isHovering ? SkeetTheme.ACCENT_PRIMARY() : SkeetTheme.BORDER_DEFAULT();
        handleColor = SkeetTheme.withAlpha(handleColor, fadeAlpha);

        // Draw resize lines
        for (int i = 0; i < 3; i++) {
            int offset = i * 4 + 2;
            context.fill(windowX + windowWidth - offset, windowY + windowHeight - 2,
                        windowX + windowWidth - offset + 2, windowY + windowHeight, handleColor);
            context.fill(windowX + windowWidth - 2, windowY + windowHeight - offset,
                        windowX + windowWidth, windowY + windowHeight - offset + 2, handleColor);
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        // PRIORITY 1: Check Lancho DM windows (handle close button and input fields)
        List<String> lanchoToRemove = new ArrayList<>();
        for (Map.Entry<String, dev.hunchclient.gui.irc.LanchoDmWindow> entry : lanchoWindows.entrySet()) {
            dev.hunchclient.gui.irc.LanchoDmWindow lanchoWindow = entry.getValue();

            // Check if close button clicked
            int closeX = lanchoWindow.getX() + lanchoWindow.getWidth() - 20;
            int closeY = lanchoWindow.getY() + 5;
            if (mouseX >= closeX && mouseX <= closeX + 15 && mouseY >= closeY && mouseY <= closeY + 15) {
                lanchoToRemove.add(entry.getKey());
                continue;
            }

            // Forward click to Lancho window
            if (lanchoWindow.mouseClicked(click, doubled)) {
                return true;
            }
        }
        // Remove closed Lancho windows
        if (!lanchoToRemove.isEmpty()) {
            for (String username : lanchoToRemove) {
                lanchoWindows.remove(username);
            }
            return true;
        }

        // PRIORITY 2: Check regular DM windows (handle close button and input fields)
        List<String> toRemove = new ArrayList<>();
        for (Map.Entry<String, DmChatWindow> entry : dmWindows.entrySet()) {
            DmChatWindow dmWindow = entry.getValue();

            // Check if close button clicked
            if (dmWindow.isHoveringCloseButton(mouseX, mouseY)) {
                toRemove.add(entry.getKey());
                continue;
            }

            // Forward click to DM window
            if (dmWindow.mouseClicked(click, doubled)) {
                return true;
            }
        }
        // Remove closed windows
        if (!toRemove.isEmpty()) {
            for (String username : toRemove) {
                dmWindows.remove(username);
            }
            return true;
        }

        // PRIORITY 3: Forward to IRC chat window (only if visible)
        if (showIrcChat && ircChatWindow != null) {
            IrcRelayModule ircModule = IrcRelayModule.getInstance();
            if (ircModule != null && ircModule.isEnabled()) {
                if (ircChatWindow.mouseClicked(click, doubled)) {
                    return true;
                }
            }
        }

        // Check if outside window
        if (mouseX < windowX || mouseX > windowX + windowWidth ||
            mouseY < windowY || mouseY > windowY + windowHeight) {
            return super.mouseClicked(click, doubled);
        }

        // Only handle resize/drag on LEFT CLICK
        if (button == 0) {
            int titleBarY = windowY + 1;

            // Calculate button positions (same as in drawTitleBar)
            int iconSpacing = 12;
            int iconY = titleBarY + 8;

            // Chat icon (rightmost)
            String chatIcon = "💬";
            int chatWidth = this.font.width(chatIcon);
            int chatX = windowX + windowWidth - chatWidth - 10;

            // Bell icon
            boolean notifEnabled = dev.hunchclient.util.GuiConfig.getInstance().isIrcNotificationsEnabled();
            String bellIcon = notifEnabled ? "🔔" : "🔕";
            int bellWidth = this.font.width(bellIcon);
            int bellX = chatX - bellWidth - iconSpacing;

            // Media icon
            String mediaIcon = "🎵";
            int mediaWidth = this.font.width(mediaIcon);
            int mediaX = bellX - mediaWidth - iconSpacing;

            // HUD Editor icon
            String hudEditIcon = "🎨";
            int hudEditWidth = this.font.width(hudEditIcon);
            int hudEditX = mediaX - hudEditWidth - iconSpacing;

            // Pokedex icon
            String pokedexIcon = "📖";
            int pokedexWidth = this.font.width(pokedexIcon);
            int pokedexX = hudEditX - pokedexWidth - iconSpacing;

            // Load icon
            String loadIcon = "📁";
            int loadWidth = this.font.width(loadIcon);
            int loadX = pokedexX - loadWidth - iconSpacing;

            // Save icon
            String saveIcon = "💾";
            int saveWidth = this.font.width(saveIcon);
            int saveX = loadX - saveWidth - iconSpacing;

            // Check Chat Window button (💬)
            if (mouseX >= chatX - 2 && mouseX <= chatX + chatWidth + 2 &&
                mouseY >= iconY - 2 && mouseY <= iconY + font.lineHeight + 2) {
                IrcRelayModule ircModule = IrcRelayModule.getInstance();
                if (ircModule != null && ircModule.isEnabled()) {
                    showIrcChat = !showIrcChat;
                } else if (minecraft.player != null) {
                    minecraft.player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "§c[IRC] §7IRC module is not enabled!"
                    ), false);
                }
                return true;
            }

            // Check Bell button (🔔/🔕)
            if (mouseX >= bellX - 2 && mouseX <= bellX + bellWidth + 2 &&
                mouseY >= iconY - 2 && mouseY <= iconY + font.lineHeight + 2) {
                dev.hunchclient.util.GuiConfig guiConfig = dev.hunchclient.util.GuiConfig.getInstance();
                boolean newState = !guiConfig.isIrcNotificationsEnabled();
                guiConfig.setIrcNotificationsEnabled(newState);

                if (minecraft.player != null) {
                    String status = newState ? "§aaktiviert" : "§cdeaktiviert";
                    minecraft.player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "§b[IRC] §7Sound & Chat-Ausgabe " + status
                    ), false);
                }
                return true;
            }

            // Check Media Player button (🎵)
            if (mouseX >= mediaX - 2 && mouseX <= mediaX + mediaWidth + 2 &&
                mouseY >= iconY - 2 && mouseY <= iconY + font.lineHeight + 2) {
                this.minecraft.setScreen(new dev.hunchclient.gui.AdvancedMediaControlScreen(this));
                return true;
            }

            // Check HUD Editor button (🎨)
            if (mouseX >= hudEditX - 2 && mouseX <= hudEditX + hudEditWidth + 2 &&
                mouseY >= iconY - 2 && mouseY <= iconY + font.lineHeight + 2) {
                this.minecraft.setScreen(new dev.hunchclient.hud.HudEditorScreen(this));
                return true;
            }

            // Check Pokedex button (📖)
            if (mouseX >= pokedexX - 2 && mouseX <= pokedexX + pokedexWidth + 2 &&
                mouseY >= iconY - 2 && mouseY <= iconY + font.lineHeight + 2) {
                this.minecraft.setScreen(new dev.hunchclient.gui.PokedexScreen(this));
                return true;
            }

            // Check Load button (📁)
            if (mouseX >= loadX - 2 && mouseX <= loadX + loadWidth + 2 &&
                mouseY >= iconY - 2 && mouseY <= iconY + font.lineHeight + 2) {
                handleLoadConfig();
                return true;
            }

            // Check Save button (💾)
            if (mouseX >= saveX - 2 && mouseX <= saveX + saveWidth + 2 &&
                mouseY >= iconY - 2 && mouseY <= iconY + font.lineHeight + 2) {
                handleSaveConfig();
                return true;
            }

            // Check resize handle
            int handleX = windowX + windowWidth - SkeetTheme.RESIZE_HANDLE_SIZE;
            int handleY = windowY + windowHeight - SkeetTheme.RESIZE_HANDLE_SIZE;
            if (mouseX >= handleX && mouseY >= handleY) {
                isResizing = true;
                resizeStartX = (int) mouseX;
                resizeStartY = (int) mouseY;
                resizeStartWidth = windowWidth;
                resizeStartHeight = windowHeight;
                return true;
            }

            // Check title bar (for dragging) - but NOT on title bar buttons
            if (mouseY >= windowY && mouseY <= windowY + SkeetTheme.TITLE_BAR_HEIGHT) {
                // Don't drag if clicking any title bar button (already handled above)
                if (mouseX >= saveX && mouseX <= chatX + chatWidth) {
                    return false;
                }
                isDragging = true;
                dragStartX = (int) mouseX - windowX;
                dragStartY = (int) mouseY - windowY;
                return true;
            }
        }

        // Check tab clicks (both left and right)
        int tabX = windowX + 1;
        if (mouseX >= tabX && mouseX <= tabX + SkeetTheme.TAB_WIDTH) {
            if (tabManager.handleTabClick(mouseX, mouseY, button)) {
                // Save selected tab (only on left click)
                if (button == 0) {
                    dev.hunchclient.util.GuiConfig.getInstance().setLastSelectedTab(tabManager.getSelectedTab());
                }
                return true;
            }
        }

        // Forward to tab content (both left and right clicks!)
        // Tabs expect absolute coordinates, they handle their own bounds checking
        return tabManager.handleContentClick(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent click) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        boolean wasDragging = isDragging;
        boolean wasResizing = isResizing;

        if (button == 0) {
            isDragging = false;
            isResizing = false;
        }

        // If we were dragging/resizing, don't forward to content
        if (wasDragging || wasResizing) {
            return true;
        }

        // Forward release to Lancho DM windows first
        for (dev.hunchclient.gui.irc.LanchoDmWindow lanchoWindow : lanchoWindows.values()) {
            if (lanchoWindow.mouseReleased(click)) {
                return true;
            }
        }

        // Forward release to DM windows
        for (DmChatWindow dmWindow : dmWindows.values()) {
            if (dmWindow.mouseReleased(click)) {
                return true;
            }
        }

        // Forward to IRC chat window (only if visible)
        if (showIrcChat && ircChatWindow != null) {
            IrcRelayModule ircModule = IrcRelayModule.getInstance();
            if (ircModule != null && ircModule.isEnabled()) {
                if (ircChatWindow.mouseReleased(click)) {
                    return true;
                }
            }
        }

        // Forward to tab manager (IMPORTANT: for sliders!)
        SkeetTab currentTab = tabManager.getCurrentTab();
        if (currentTab != null && currentTab instanceof ModulesTab modulesTab) {
            if (modulesTab.mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
        }

        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent click, double offsetX, double offsetY) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        if (isDragging) {
            windowX = (int) mouseX - dragStartX;
            windowY = (int) mouseY - dragStartY;

            // Save position while dragging (for smooth state updates)
            dev.hunchclient.util.GuiConfig.getInstance().setWindowPosition(windowX, windowY);

            return true;
        }

        if (isResizing) {
            int deltaWidth = (int) mouseX - resizeStartX;
            int deltaHeight = (int) mouseY - resizeStartY;
            windowWidth = Math.max(SkeetTheme.WINDOW_MIN_WIDTH, resizeStartWidth + deltaWidth);
            windowHeight = Math.max(SkeetTheme.WINDOW_MIN_HEIGHT, resizeStartHeight + deltaHeight);

            // Save size while resizing (for smooth state updates)
            dev.hunchclient.util.GuiConfig.getInstance().setWindowSize(windowWidth, windowHeight);

            return true;
        }

        // Forward drag to Lancho DM windows first
        for (dev.hunchclient.gui.irc.LanchoDmWindow lanchoWindow : lanchoWindows.values()) {
            if (lanchoWindow.mouseDragged(click, offsetX, offsetY)) {
                return true;
            }
        }

        // Forward drag to DM windows
        for (DmChatWindow dmWindow : dmWindows.values()) {
            if (dmWindow.mouseDragged(click, offsetX, offsetY)) {
                return true;
            }
        }

        // Forward to IRC chat window (only if visible)
        if (showIrcChat && ircChatWindow != null) {
            IrcRelayModule ircModule = IrcRelayModule.getInstance();
            if (ircModule != null && ircModule.isEnabled()) {
                if (ircChatWindow.mouseDragged(click, offsetX, offsetY)) {
                    return true;
                }
            }
        }

        // Forward to tab manager (IMPORTANT: for sliders!)
        SkeetTab currentTab = tabManager.getCurrentTab();
        if (currentTab != null && currentTab instanceof ModulesTab modulesTab) {
            if (modulesTab.mouseDragged(mouseX, mouseY, button, offsetX, offsetY)) {
                return true;
            }
        }

        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // Forward scroll to Lancho DM windows first
        for (dev.hunchclient.gui.irc.LanchoDmWindow lanchoWindow : lanchoWindows.values()) {
            if (lanchoWindow.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
                return true;
            }
        }

        // Forward scroll to DM windows
        for (DmChatWindow dmWindow : dmWindows.values()) {
            if (dmWindow.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
                return true;
            }
        }

        // Forward to IRC chat window (only if visible)
        if (showIrcChat && ircChatWindow != null) {
            IrcRelayModule ircModule = IrcRelayModule.getInstance();
            if (ircModule != null && ircModule.isEnabled()) {
                if (ircChatWindow.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
                    return true;
                }
            }
        }

        // Check if mouse is within the main window bounds
        if (mouseX < windowX || mouseX > windowX + windowWidth ||
            mouseY < windowY || mouseY > windowY + windowHeight) {
            return false;
        }

        // Forward to tab manager with ABSOLUTE coordinates (tabs handle their own bounds checking)
        return tabManager.handleContentScroll(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent input) {
        char chr = (char) input.codepoint();
        int modifiers = input.modifiers();

        // Forward to Lancho DM windows first
        for (dev.hunchclient.gui.irc.LanchoDmWindow lanchoWindow : lanchoWindows.values()) {
            if (lanchoWindow.charTyped(input)) {
                return true;
            }
        }

        // Forward to DM windows
        for (DmChatWindow dmWindow : dmWindows.values()) {
            if (dmWindow.charTyped(input)) {
                return true;
            }
        }

        // Forward to IRC chat window (only if visible)
        if (showIrcChat && ircChatWindow != null) {
            IrcRelayModule ircModule = IrcRelayModule.getInstance();
            if (ircModule != null && ircModule.isEnabled()) {
                if (ircChatWindow.charTyped(input)) {
                    return true;
                }
            }
        }

        // Forward to tab manager (for text fields)
        if (tabManager.charTyped(chr, modifiers)) {
            return true;
        }
        return super.charTyped(input);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent input) {
        int keyCode = input.key();
        int scanCode = input.scancode();
        int modifiers = input.modifiers();

        // Forward to Lancho DM windows first
        for (dev.hunchclient.gui.irc.LanchoDmWindow lanchoWindow : lanchoWindows.values()) {
            if (lanchoWindow.keyPressed(input)) {
                return true;
            }
        }

        // Forward to DM windows
        for (DmChatWindow dmWindow : dmWindows.values()) {
            if (dmWindow.keyPressed(input)) {
                return true;
            }
        }

        // Forward to IRC chat window (only if visible)
        if (showIrcChat && ircChatWindow != null) {
            IrcRelayModule ircModule = IrcRelayModule.getInstance();
            if (ircModule != null && ircModule.isEnabled()) {
                if (ircChatWindow.keyPressed(input)) {
                    return true;
                }
            }
        }

        // Forward to tab manager (for text fields)
        if (tabManager.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        // ESC to close
        if (keyCode == 256) { // GLFW_KEY_ESCAPE
            this.onClose();
            return true;
        }

        return super.keyPressed(input);
    }

    /**
     * Handle save config button click
     */
    private void handleSaveConfig() {
        try {
            dev.hunchclient.util.ConfigManager.save();
            dev.hunchclient.command.bind.BindManager.getInstance().saveBinds();
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "§d[HunchClient] §aConfiguration saved!"
                ), false);
            }
        } catch (Exception e) {
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "§d[HunchClient] §cFailed to save config: " + e.getMessage()
                ), false);
            }
            e.printStackTrace();
        }
    }

    /**
     * Handle load config button click
     */
    private void handleLoadConfig() {
        try {
            dev.hunchclient.util.ConfigManager.load();
            dev.hunchclient.command.bind.BindManager.getInstance().loadBinds();
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "§d[HunchClient] §aConfiguration loaded!"
                ), false);
            }
        } catch (Exception e) {
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "§d[HunchClient] §cFailed to load config: " + e.getMessage()
                ), false);
            }
            e.printStackTrace();
        }
    }


    /**
     * Get rainbow color based on time and position (for flowing effect around border)
     * @param time Current animation time
     * @param position Position along the border (0.0 to 1.0, goes around the rectangle)
     * @param alpha Alpha channel
     */
    private int getRainbowColor(float time, float position, int alpha) {
        float speed = 1.5f; // Speed of color cycle
        float waves = 2.0f; // Number of color waves around the border
        float offset = position * waves * 6.0f; // Offset based on position
        float hue = (time * speed + offset) % 6.0f;

        int r, g, b;

        if (hue < 1.0f) {
            r = 255;
            g = (int) (255 * hue);
            b = 0;
        } else if (hue < 2.0f) {
            r = (int) (255 * (2.0f - hue));
            g = 255;
            b = 0;
        } else if (hue < 3.0f) {
            r = 0;
            g = 255;
            b = (int) (255 * (hue - 2.0f));
        } else if (hue < 4.0f) {
            r = 0;
            g = (int) (255 * (4.0f - hue));
            b = 255;
        } else if (hue < 5.0f) {
            r = (int) (255 * (hue - 4.0f));
            g = 0;
            b = 255;
        } else {
            r = 255;
            g = 0;
            b = (int) (255 * (6.0f - hue));
        }

        return (alpha << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * Draw animated RGB glow border with flowing colors (OPTIMIZED for performance)
     */
    private void drawRgbGlowBorder(GuiGraphics context, int x, int y, int width, int height, int fadeAlpha) {
        // OPTIMIZATION: Reduced layers and larger segments for better FPS
        int glowLayers = 2; // Reduced from 5 to 2
        int segmentSize = 6; // Draw every 6 pixels instead of every pixel (balance between smoothness and performance)

        // Calculate perimeter for position mapping
        float perimeter = 2.0f * (width + height);

        for (int layer = glowLayers; layer >= 0; layer--) {
            // Calculate alpha for this layer
            float alphaMultiplier = layer == 0 ? 1.0f : 0.4f / (layer + 0.5f);
            int layerAlpha = (int) (fadeAlpha * alphaMultiplier);
            int offset = layer;

            // Draw Top edge (left to right) - in segments
            for (int px = 0; px < width + 2 * offset; px += segmentSize) {
                float position = px / perimeter;
                int color = getRainbowColor(rgbTime, position, Math.min(255, layerAlpha));
                int endX = Math.min(x - offset + px + segmentSize, x + width + offset);
                context.fill(x - offset + px, y - offset, endX, y - offset + 1, color);
            }

            // Draw Right edge (top to bottom) - in segments
            for (int py = 0; py < height + 2 * offset; py += segmentSize) {
                float position = (width + py) / perimeter;
                int color = getRainbowColor(rgbTime, position, Math.min(255, layerAlpha));
                int endY = Math.min(y - offset + py + segmentSize, y + height + offset);
                context.fill(x + width + offset - 1, y - offset + py, x + width + offset, endY, color);
            }

            // Draw Bottom edge (right to left) - in segments
            for (int px = 0; px < width + 2 * offset; px += segmentSize) {
                float position = (width + height + px) / perimeter;
                int color = getRainbowColor(rgbTime, position, Math.min(255, layerAlpha));
                int startX = Math.max(x + width + offset - px - segmentSize, x - offset);
                context.fill(startX, y + height + offset - 1, x + width + offset - px, y + height + offset, color);
            }

            // Draw Left edge (bottom to top) - in segments
            for (int py = 0; py < height + 2 * offset; py += segmentSize) {
                float position = (2 * width + height + py) / perimeter;
                int color = getRainbowColor(rgbTime, position, Math.min(255, layerAlpha));
                int startY = Math.max(y + height + offset - py - segmentSize, y - offset);
                context.fill(x - offset, startY, x - offset + 1, y + height + offset - py, color);
            }
        }
    }

    @Override
    public void onClose() {
        // Save GUI state before closing
        dev.hunchclient.util.GuiConfig guiConfig = dev.hunchclient.util.GuiConfig.getInstance();
        guiConfig.setWindowPosition(windowX, windowY);
        guiConfig.setWindowSize(windowWidth, windowHeight);
        guiConfig.setLastSelectedTab(tabManager.getSelectedTab());

        // Save scroll position of current tab
        SkeetTab currentTab = tabManager.getCurrentTab();
        if (currentTab != null) {
            currentTab.onDeselected(); // Trigger scroll position save
        }

        // Persist to disk immediately
        try {
            dev.hunchclient.util.ConfigManager.save();
        } catch (Exception e) {
            System.err.println("Failed to save GUI config on close: " + e.getMessage());
        }

        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
