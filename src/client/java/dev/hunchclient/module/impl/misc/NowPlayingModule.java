package dev.hunchclient.module.impl.misc;

import dev.hunchclient.module.ConfigurableModule;
import dev.hunchclient.module.Module;
import dev.hunchclient.module.SettingsProvider;
import dev.hunchclient.module.setting.*;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Now Playing HUD Module
 *
 * Displays currently playing media from Windows SMTC (System Media Transport Controls)
 * Shows song info, artist, album, and progress bar (read-only)
 *
 * For media controls (play/pause/next/prev), use the Media Control GUI
 *
 * WATCHDOG SAFE: ✅ YES
 * - Client-side only rendering
 * - No packets sent
 * - Visual only
 */
public class NowPlayingModule extends Module implements ConfigurableModule, SettingsProvider {

    private static NowPlayingModule INSTANCE;

    // Current media data
    private MediaPlayerData currentData = new MediaPlayerData();
    private CompletableFuture<Void> updateFuture = null;

    // Album art loader
    private final AlbumArtLoader albumArtLoader = AlbumArtLoader.getInstance();

    // Update settings
    private int updateIntervalMs = 1000; // 1 second default
    private long lastUpdateTime = 0;

    // Position settings (percentage of screen) - kept for backwards compatibility
    private float hudX = 1.0f; // Right side
    private float hudY = 90.0f; // Bottom
    private int hudWidth = 300;
    private int hudHeight = 80;

    // HUD Element for unified editor
    private dev.hunchclient.hud.elements.NowPlayingHudElement hudElement;

    // Visual settings
    private boolean showProgressBar = true;
    private boolean showAlbum = true;
    private boolean showAlbumArt = true; // Show album cover
    private boolean enableTextScroll = true;
    private float scrollSpeed = 1.0f;
    private int backgroundColor = 0x80000000; // Semi-transparent black
    private int textColor = 0xFFFFFFFF; // White
    private int accentColor = 0xFF1DB954; // Spotify green
    private int progressBarColor = 0xFF1DB954; // Spotify green
    private int progressBarBgColor = 0xFF333333; // Dark gray

    // Text scrolling state
    private float titleScrollOffset = 0;
    private float artistScrollOffset = 0;
    private float albumScrollOffset = 0;
    private long lastScrollUpdate = System.currentTimeMillis();

    public NowPlayingModule() {
        super("NowPlaying", "Display currently playing media on HUD", Category.VISUALS, true);
        INSTANCE = this;

        // Create HUD element for unified editor (positioned center by default)
        hudElement = new dev.hunchclient.hud.elements.NowPlayingHudElement(0, 0, 300, 80);
        hudElement.setAnchor(dev.hunchclient.hud.HudAnchor.MIDDLE_CENTER);
        hudElement.setX(0);
        hudElement.setY(30); // Slightly below center
    }

    public static NowPlayingModule getInstance() {
        return INSTANCE;
    }

    /**
     * Sync positions/sizes back from HUD editor to module config
     * Call this when HUD editor makes changes
     */
    public void syncFromHudEditor() {
        if (hudElement != null) {
            // Sync position, size, and anchor from HUD element back to module fields
            this.hudX = hudElement.getX();
            this.hudY = hudElement.getY();
            this.hudWidth = hudElement.getWidth();
            this.hudHeight = hudElement.getHeight();
            this.hudAnchor = hudElement.getAnchor().name();
        }
    }

    @Override
    protected void onEnable() {
        // Start polling media info
        currentData = new MediaPlayerData();
        lastUpdateTime = 0;

        // Register HUD element with unified editor
        dev.hunchclient.hud.HudEditorManager.getInstance().registerElement(hudElement);
    }

    @Override
    protected void onDisable() {
        // Cancel pending updates
        if (updateFuture != null && !updateFuture.isDone()) {
            updateFuture.cancel(true);
        }
        currentData = new MediaPlayerData();

        // Clear album art
        albumArtLoader.clearAlbumArt();

        // Stop external helper
        WindowsMediaControl.shutdown();

        // Unregister HUD element
        dev.hunchclient.hud.HudEditorManager.getInstance().unregisterElement(hudElement);
    }

    /**
     * Update media data (called periodically)
     */
    public void update() {
        if (!isEnabled()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastUpdateTime < updateIntervalMs) {
            return;
        }

        // Don't spam updates if previous one is still pending
        if (updateFuture != null && !updateFuture.isDone()) {
            return;
        }

        lastUpdateTime = now;

        // Fetch new data asynchronously
        updateFuture = WindowsMediaControl.getMediaInfoAsync()
            .thenAccept(data -> {
                if (data != null) {
                    currentData = data;

                    // Track statistics
                    MediaStatisticsManager statsManager = MediaStatisticsManager.getInstance();
                    statsManager.trackSong(data);

                    // Load album art if enabled, otherwise clear out stale textures
                    if (showAlbumArt) {
                        String artPath = data.getThumbnailPath();
                        if (data.hasThumbnail() && artPath != null && !artPath.isEmpty()) {
                            albumArtLoader.loadAlbumArt(artPath);
                        } else {
                            albumArtLoader.clearAlbumArt();
                        }
                    } else {
                        albumArtLoader.clearAlbumArt();
                    }
                } else {
                    currentData = new MediaPlayerData();
                    albumArtLoader.clearAlbumArt();

                    // End tracking session when no media is playing
                    MediaStatisticsManager statsManager = MediaStatisticsManager.getInstance();
                    statsManager.trackSong(currentData);
                }
            })
            .exceptionally(throwable -> {
                // Silently fail - media info is optional
                return null;
            });
    }

    /**
     * Render the HUD (called from InGameHudMixin or similar)
     * @deprecated Use HudEditorManager instead - this is kept for backwards compatibility
     */
    @Deprecated
    public void render(GuiGraphics context, float tickDelta) {
        if (!isEnabled() || !currentData.isActive()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) {
            return;
        }

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        // Calculate position from old percentage-based system
        int x = (int) (screenWidth * hudX / 100.0f) - hudWidth;
        int y = (int) (screenHeight * hudY / 100.0f);

        // Delegate to renderAt
        renderAt(context, x, y, hudWidth, hudHeight, tickDelta);
    }

    /**
     * Render the HUD at specific coordinates (called from HudElement)
     */
    public void renderAt(GuiGraphics context, int x, int y, int width, int height, float tickDelta) {
        if (!isEnabled() || !currentData.isActive()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }

        // Background
        context.fill(x, y, x + width, y + height, backgroundColor);

        // Border
        drawBorder(context, x, y, width, height, accentColor);

        // Album Art (left side if enabled and loaded)
        int albumArtSize = 0;
        int textStartX = x + 5;
        if (showAlbumArt && albumArtLoader.hasAlbumArt()) {
            albumArtSize = Math.min(height - 10, 60); // Max 60px, respect HUD height
            albumArtLoader.render(context, x + 5, y + 5, albumArtSize);
            textStartX = x + albumArtSize + 10; // Text starts after album art
        }

        Font textRenderer = mc.font;
        int textY = y + 5;
        int textWidth = width - (textStartX - x) - 5; // Available width for text

        // Update scroll animation
        if (enableTextScroll) {
            long now = System.currentTimeMillis();
            float deltaTime = (now - lastScrollUpdate) / 1000.0f;
            deltaTime = Math.min(deltaTime, 0.5f);
            lastScrollUpdate = now;

            String title = currentData.getTitle();
            if (!title.isEmpty()) {
                int availableWidth = Math.max(textWidth - 10, 0);
                int titleWidth = textRenderer.width(title);
                if (availableWidth > 0 && titleWidth > availableWidth) {
                    titleScrollOffset += scrollSpeed * deltaTime * 20; // pixels per second
                    if (titleScrollOffset > titleWidth + 50) {
                        titleScrollOffset = -availableWidth;
                    }
                } else {
                    titleScrollOffset = 0;
                }
            } else {
                titleScrollOffset = 0;
            }

            String artist = currentData.getArtist();
            if (!artist.isEmpty()) {
                int availableWidth = Math.max(textWidth - 5, 0);
                int artistWidth = textRenderer.width(artist);
                if (availableWidth > 0 && artistWidth > availableWidth) {
                    artistScrollOffset += scrollSpeed * deltaTime * 15;
                    if (artistScrollOffset > artistWidth + 50) {
                        artistScrollOffset = -availableWidth;
                    }
                } else {
                    artistScrollOffset = 0;
                }
            } else {
                artistScrollOffset = 0;
            }

            if (showAlbum) {
                String album = currentData.getAlbum();
                if (!album.isEmpty()) {
                    int availableWidth = Math.max(textWidth - 5, 0);
                    int albumWidth = textRenderer.width(album);
                    if (availableWidth > 0 && albumWidth > availableWidth) {
                        albumScrollOffset += scrollSpeed * deltaTime * 15;
                        if (albumScrollOffset > albumWidth + 50) {
                            albumScrollOffset = -availableWidth;
                        }
                    } else {
                        albumScrollOffset = 0;
                    }
                } else {
                    albumScrollOffset = 0;
                }
            } else {
                albumScrollOffset = 0;
            }
        } else {
            titleScrollOffset = 0;
            artistScrollOffset = 0;
            albumScrollOffset = 0;
            lastScrollUpdate = System.currentTimeMillis();
        }

        // Title (with scrolling or truncate)
        String title = currentData.getTitle();
        if (!title.isEmpty()) {
            int maxWidth = Math.max(textWidth - 10, 0);
            if (enableTextScroll && maxWidth > 0 && textRenderer.width(title) > maxWidth) {
                // Enable scissor for clipping
                context.enableScissor(textStartX, textY, textStartX + maxWidth, textY + 10);
                context.drawString(textRenderer, title, (int)(textStartX - titleScrollOffset), textY, textColor, false);
                context.disableScissor();
            } else if (maxWidth > 0) {
                title = truncateText(textRenderer, title, maxWidth);
                context.drawString(textRenderer, title, textStartX, textY, textColor, false);
            }
            textY += 10;
        }

        // Artist (with scrolling or truncate)
        String artist = currentData.getArtist();
        if (!artist.isEmpty()) {
            int maxWidth = Math.max(textWidth - 5, 0);
            int artistColor = 0xFFAAAAAA; // Light gray

            if (enableTextScroll && maxWidth > 0 && textRenderer.width(artist) > maxWidth) {
                context.enableScissor(textStartX, textY, textStartX + maxWidth, textY + 9);
                context.drawString(textRenderer, artist, (int)(textStartX - artistScrollOffset), textY, artistColor, false);
                context.disableScissor();
            } else if (maxWidth > 0) {
                artist = truncateText(textRenderer, artist, maxWidth);
                context.drawString(textRenderer, artist, textStartX, textY, artistColor, false);
            }
            textY += 9;
        }

        // Album (optional)
        if (showAlbum) {
            String album = currentData.getAlbum();
            if (!album.isEmpty()) {
                int albumColor = 0xFF888888; // Gray
                int maxWidth = Math.max(textWidth - 5, 0);

                if (enableTextScroll && maxWidth > 0 && textRenderer.width(album) > maxWidth) {
                    context.enableScissor(textStartX, textY, textStartX + maxWidth, textY + 8);
                    context.drawString(textRenderer, album, (int)(textStartX - albumScrollOffset), textY, albumColor, false);
                    context.disableScissor();
                } else if (maxWidth > 0) {
                    album = truncateText(textRenderer, album, maxWidth);
                    context.drawString(textRenderer, album, textStartX, textY, albumColor, false);
                }
                textY += 8;
            }
        }

        // Progress bar
        if (showProgressBar && currentData.hasTimeline()) {
            textY += 3;
            int barWidth = textWidth - 5;
            int barHeight = 4;
            int barX = textStartX;
            int barY = textY;

            // Background
            context.fill(barX, barY, barX + barWidth, barY + barHeight, progressBarBgColor);

            // Progress
            float progress = currentData.getProgress();
            int progressWidth = (int) (barWidth * progress);
            context.fill(barX, barY, barX + progressWidth, barY + barHeight, progressBarColor);

            textY += barHeight + 3;

            // Time labels
            String timeText = currentData.getFormattedPosition() + " / " + currentData.getFormattedDuration();
            int timeColor = 0xFFAAAAAA;
            context.drawString(textRenderer, timeText, textStartX, textY, timeColor, false);
        }

        // Playback status indicator
        String statusIcon = getStatusIcon(currentData.getStatus());
        if (!statusIcon.isEmpty()) {
            int statusX = x + width - 15;
            int statusY = y + 5;
            context.drawString(textRenderer, statusIcon, statusX, statusY, accentColor, false);
        }
    }

    /**
     * Truncate text to fit width
     */
    private String truncateText(Font textRenderer, String text, int maxWidth) {
        if (textRenderer.width(text) <= maxWidth) {
            return text;
        }

        String ellipsis = "...";

        // Binary search for the right length
        int low = 0;
        int high = text.length();
        while (low < high) {
            int mid = (low + high + 1) / 2;
            String truncated = text.substring(0, mid) + ellipsis;
            if (textRenderer.width(truncated) <= maxWidth) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }

        return text.substring(0, low) + ellipsis;
    }

    /**
     * Draw border around HUD
     */
    private void drawBorder(GuiGraphics context, int x, int y, int width, int height, int color) {
        int borderWidth = 1;
        // Top
        context.fill(x, y, x + width, y + borderWidth, color);
        // Bottom
        context.fill(x, y + height - borderWidth, x + width, y + height, color);
        // Left
        context.fill(x, y, x + borderWidth, y + height, color);
        // Right
        context.fill(x + width - borderWidth, y, x + width, y + height, color);
    }

    /**
     * Get status icon
     */
    private String getStatusIcon(MediaPlayerData.PlaybackStatus status) {
        switch (status) {
            case PLAYING:
                return "▶";
            case PAUSED:
                return "⏸";
            case STOPPED:
                return "⏹";
            default:
                return "";
        }
    }

    // Getters for settings
    public MediaPlayerData getCurrentData() {
        return currentData;
    }

    public int getUpdateIntervalMs() {
        return updateIntervalMs;
    }

    public void setUpdateIntervalMs(int updateIntervalMs) {
        this.updateIntervalMs = Math.max(100, Math.min(5000, updateIntervalMs));
    }

    // Getters for Edit Mode
    public float getHudX() { return hudX; }
    public float getHudY() { return hudY; }
    public int getHudWidth() { return hudWidth; }
    public int getHudHeight() { return hudHeight; }
    public int getBackgroundColor() { return backgroundColor; }
    public int getTextColor() { return textColor; }
    public int getAccentColor() { return accentColor; }
    public int getProgressBarColor() { return progressBarColor; }
    public int getProgressBarBgColor() { return progressBarBgColor; }
    public boolean isShowProgressBar() { return showProgressBar; }
    public boolean isShowAlbum() { return showAlbum; }

    // Setters for Edit Mode
    public void setHudPosition(float x, float y) {
        this.hudX = Math.max(0, Math.min(100, x));
        this.hudY = Math.max(0, Math.min(100, y));
    }

    public void setHudSize(int width, int height) {
        this.hudWidth = Math.max(150, Math.min(600, width));
        this.hudHeight = Math.max(50, Math.min(200, height));
    }

    // Settings Provider implementation
    @Override
    public List<ModuleSetting> getSettings() {
        List<ModuleSetting> settings = new ArrayList<>();

        // Reset Position Button
        settings.add(new ButtonSetting(
            "Reset HUD Position",
            "Reset HUD position to center of screen",
            "nowplaying_reset",
            () -> {
                if (hudElement != null) {
                    hudElement.setAnchor(dev.hunchclient.hud.HudAnchor.MIDDLE_CENTER);
                    hudElement.setX(0);
                    hudElement.setY(30);
                    hudElement.setSize(300, 80);
                    // Also update module fields
                    hudX = 0;
                    hudY = 30;
                    hudWidth = 300;
                    hudHeight = 80;
                    hudAnchor = "MIDDLE_CENTER";
                    saveConfig();
                }
            }
        ));

        // Advanced Control Button with Search
        settings.add(new ButtonSetting(
            "Advanced Controls",
            "Open advanced media player with search functionality",
            "nowplaying_advanced",
            () -> {
                Minecraft mc = Minecraft.getInstance();
                mc.execute(() -> {
                    mc.setScreen(new dev.hunchclient.gui.AdvancedMediaControlScreen(mc.screen));
                });
            }
        ));

        // Position settings
        settings.add(new SliderSetting(
            "X Position (%)",
            "Horizontal position (0-100%)",
            "nowplaying_x",
            0.0f, 100.0f,
            () -> hudX,
            (val) -> {
                hudX = val;
                saveConfig();
            }
        ).withDecimals(1));

        settings.add(new SliderSetting(
            "Y Position (%)",
            "Vertical position (0-100%)",
            "nowplaying_y",
            0.0f, 100.0f,
            () -> hudY,
            (val) -> {
                hudY = val;
                saveConfig();
            }
        ).withDecimals(1));

        // Size settings
        settings.add(new SliderSetting(
            "Width",
            "HUD width in pixels",
            "nowplaying_width",
            150, 600,
            () -> (float) hudWidth,
            (val) -> {
                hudWidth = val.intValue();
                saveConfig();
            }
        ).withDecimals(0));

        settings.add(new SliderSetting(
            "Height",
            "HUD height in pixels",
            "nowplaying_height",
            50, 200,
            () -> (float) hudHeight,
            (val) -> {
                hudHeight = val.intValue();
                saveConfig();
            }
        ).withDecimals(0));

        // Update rate
        settings.add(new SliderSetting(
            "Update Rate (ms)",
            "How often to check for media updates",
            "nowplaying_update_rate",
            100, 5000,
            () -> (float) updateIntervalMs,
            (val) -> {
                setUpdateIntervalMs(val.intValue());
                saveConfig();
            }
        ).withDecimals(0));

        // Visual toggles
        settings.add(new CheckboxSetting(
            "Show Progress Bar",
            "Display playback progress bar",
            "nowplaying_show_progress",
            () -> showProgressBar,
            (val) -> {
                showProgressBar = val;
                saveConfig();
            }
        ));

        settings.add(new CheckboxSetting(
            "Show Album",
            "Display album name",
            "nowplaying_show_album",
            () -> showAlbum,
            (val) -> {
                showAlbum = val;
                saveConfig();
            }
        ));

        settings.add(new CheckboxSetting(
            "Show Album Art",
            "Display album cover artwork",
            "nowplaying_show_album_art",
            () -> showAlbumArt,
            (val) -> {
                showAlbumArt = val;
                if (!val) {
                    albumArtLoader.clearAlbumArt();
                } else if (currentData != null && currentData.hasThumbnail()) {
                    String artPath = currentData.getThumbnailPath();
                    if (artPath != null && !artPath.isEmpty()) {
                        albumArtLoader.loadAlbumArt(artPath);
                    }
                }
                saveConfig();
            }
        ));

        settings.add(new CheckboxSetting(
            "Enable Text Scrolling",
            "Scroll text that's too long instead of truncating",
            "nowplaying_scroll",
            () -> enableTextScroll,
            (val) -> {
                enableTextScroll = val;
                if (!val) {
                    titleScrollOffset = 0;
                    artistScrollOffset = 0;
                    albumScrollOffset = 0;
                }
                saveConfig();
            }
        ));

        settings.add(new SliderSetting(
            "Scroll Speed",
            "How fast the text scrolls",
            "nowplaying_scroll_speed",
            0.1f, 3.0f,
            () -> scrollSpeed,
            (val) -> {
                scrollSpeed = val;
                saveConfig();
            }
        ).withDecimals(1));

        // Color settings
        settings.add(new ColorPickerSetting(
            "Background Color",
            "HUD background color",
            "nowplaying_bg_color",
            () -> backgroundColor,
            (color) -> {
                backgroundColor = color;
                saveConfig();
            }
        ));

        settings.add(new ColorPickerSetting(
            "Text Color",
            "Main text color",
            "nowplaying_text_color",
            () -> textColor,
            (color) -> {
                textColor = color;
                saveConfig();
            }
        ));

        settings.add(new ColorPickerSetting(
            "Accent Color",
            "Border and icon color",
            "nowplaying_accent_color",
            () -> accentColor,
            (color) -> {
                accentColor = color;
                saveConfig();
            }
        ));

        settings.add(new ColorPickerSetting(
            "Progress Bar Color",
            "Progress bar fill color",
            "nowplaying_progress_color",
            () -> progressBarColor,
            (color) -> {
                progressBarColor = color;
                saveConfig();
            }
        ));

        return settings;
    }

    // Store anchor name for config persistence
    private String hudAnchor = "MIDDLE_CENTER";

    @Override
    public JsonObject saveConfig() {
        JsonObject config = new JsonObject();
        config.addProperty("hudX", hudX);
        config.addProperty("hudY", hudY);
        config.addProperty("hudWidth", hudWidth);
        config.addProperty("hudHeight", hudHeight);
        config.addProperty("hudAnchor", hudAnchor);
        config.addProperty("updateIntervalMs", updateIntervalMs);
        config.addProperty("showProgressBar", showProgressBar);
        config.addProperty("showAlbum", showAlbum);
        config.addProperty("showAlbumArt", showAlbumArt);
        config.addProperty("enableTextScroll", enableTextScroll);
        config.addProperty("scrollSpeed", scrollSpeed);
        config.addProperty("backgroundColor", backgroundColor);
        config.addProperty("textColor", textColor);
        config.addProperty("accentColor", accentColor);
        config.addProperty("progressBarColor", progressBarColor);
        config.addProperty("progressBarBgColor", progressBarBgColor);
        return config;
    }

    @Override
    public void loadConfig(JsonObject data) {
        if (data == null) return;

        // Check if this is an old config (missing hudAnchor means old percentage-based system)
        // Old system used hudX=1.0 (100%) for right side, new system uses pixel offsets from anchor
        boolean isOldConfig = data.has("hudX") && !data.has("hudAnchor");

        if (isOldConfig) {
            // Reset to center defaults for old configs
            hudX = 0;
            hudY = 30;
            hudWidth = 300;
            hudHeight = 80;
            hudAnchor = "MIDDLE_CENTER";
        } else {
            if (data.has("hudX")) hudX = data.get("hudX").getAsFloat();
            if (data.has("hudY")) hudY = data.get("hudY").getAsFloat();
            if (data.has("hudWidth")) hudWidth = data.get("hudWidth").getAsInt();
            if (data.has("hudHeight")) hudHeight = data.get("hudHeight").getAsInt();
            if (data.has("hudAnchor")) hudAnchor = data.get("hudAnchor").getAsString();
        }

        if (data.has("updateIntervalMs")) updateIntervalMs = data.get("updateIntervalMs").getAsInt();
        if (data.has("showProgressBar")) showProgressBar = data.get("showProgressBar").getAsBoolean();
        if (data.has("showAlbum")) showAlbum = data.get("showAlbum").getAsBoolean();
        if (data.has("showAlbumArt")) showAlbumArt = data.get("showAlbumArt").getAsBoolean();
        if (data.has("enableTextScroll")) enableTextScroll = data.get("enableTextScroll").getAsBoolean();
        if (data.has("scrollSpeed")) scrollSpeed = data.get("scrollSpeed").getAsFloat();
        if (data.has("backgroundColor")) backgroundColor = data.get("backgroundColor").getAsInt();
        if (data.has("textColor")) textColor = data.get("textColor").getAsInt();
        if (data.has("accentColor")) accentColor = data.get("accentColor").getAsInt();
        if (data.has("progressBarColor")) progressBarColor = data.get("progressBarColor").getAsInt();
        if (data.has("progressBarBgColor")) progressBarBgColor = data.get("progressBarBgColor").getAsInt();

        // CRITICAL: Apply loaded positions to HUD element
        // This ensures positions persist after restart
        if (hudElement != null) {
            try {
                hudElement.setAnchor(dev.hunchclient.hud.HudAnchor.valueOf(hudAnchor));
            } catch (IllegalArgumentException e) {
                hudElement.setAnchor(dev.hunchclient.hud.HudAnchor.MIDDLE_CENTER);
            }
            hudElement.setX(hudX);
            hudElement.setY(hudY);
            hudElement.setSize(hudWidth, hudHeight);
        }
    }
}
