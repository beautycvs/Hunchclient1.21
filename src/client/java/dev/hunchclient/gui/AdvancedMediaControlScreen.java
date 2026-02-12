package dev.hunchclient.gui;

import dev.hunchclient.gui.skeet.SkeetTheme;
import dev.hunchclient.module.impl.misc.AlbumArtLoader;
import dev.hunchclient.module.impl.misc.MediaPlayerData;
import dev.hunchclient.module.impl.misc.MediaSearchManager;
import dev.hunchclient.module.impl.misc.MediaStatisticsManager;
import dev.hunchclient.module.impl.misc.MediaStatisticsManager.*;
import dev.hunchclient.module.impl.misc.NowPlayingModule;
import dev.hunchclient.module.impl.misc.WindowsMediaControl;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Advanced Media Player Control Screen with Search
 *
 * Features:
 * - Full media control (play/pause, next, prev, volume, shuffle, repeat)
 * - Search functionality for songs/artists/albums
 * - Recent songs history
 * - Direct control of Apple Music, Spotify, etc
 */
public class AdvancedMediaControlScreen extends Screen {

    private final Screen parent;
    private MediaSearchManager searchManager;
    private MediaStatisticsManager statsManager;

    // Window state
    private int windowX, windowY;
    private int windowWidth = 480;
    private int windowHeight = 600;

    // Dragging
    private boolean isDragging = false;
    private int dragStartX, dragStartY;

    // UI Components
    private int hoveredHistoryIndex = -1;

    // Timeline slider state
    private boolean isDraggingTimeline = false;
    private float timelineSeekPosition = 0;

    // Button positions
    private int prevButtonX, prevButtonY, prevButtonWidth = 40, prevButtonHeight = 40;
    private int playButtonX, playButtonY, playButtonWidth = 50, playButtonHeight = 40;
    private int nextButtonX, nextButtonY, nextButtonWidth = 40, nextButtonHeight = 40;
    private int volumeSliderX, volumeSliderY, volumeSliderWidth = 150, volumeSliderHeight = 20;

    // Media state
    private float volume = 0.5f; // 0.0 to 1.0
    private boolean isDraggingVolume = false;
    private List<MediaPlayerData> recentSongs = new ArrayList<>();

    // Notification message
    private String notificationMessage = "";
    private long notificationTime = 0;

    // Tab state
    private enum Tab {
        NOW_PLAYING("Now Playing"),
        STATISTICS("Statistics"),
        HISTORY("History");

        final String name;
        Tab(String name) { this.name = name; }
    }
    private Tab currentTab = Tab.NOW_PLAYING;

    public AdvancedMediaControlScreen(Screen parent) {
        super(Component.literal("Advanced Media Player"));
        this.parent = parent;
        this.searchManager = new MediaSearchManager();
        this.statsManager = MediaStatisticsManager.getInstance();
    }

    @Override
    protected void init() {
        super.init();

        // Center window
        windowX = (this.width - windowWidth) / 2;
        windowY = (this.height - windowHeight) / 2;

        // Calculate control positions
        calculateButtonPositions();
    }

    private void calculateButtonPositions() {
        int controlsY = windowY + windowHeight - 120;

        // Media buttons row
        int buttonSpacing = 10;
        int totalWidth = prevButtonWidth + playButtonWidth + nextButtonWidth + buttonSpacing * 2;
        int startX = windowX + (windowWidth - totalWidth) / 2;

        prevButtonX = startX;
        prevButtonY = controlsY;

        playButtonX = startX + prevButtonWidth + buttonSpacing;
        playButtonY = controlsY;

        nextButtonX = playButtonX + playButtonWidth + buttonSpacing;
        nextButtonY = controlsY;

        // Volume slider (centered below media buttons)
        int secondaryY = controlsY + 50;
        volumeSliderX = windowX + (windowWidth - volumeSliderWidth) / 2;
        volumeSliderY = secondaryY + 5;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        // Dark overlay
        context.fill(0, 0, this.width, this.height, 0x80000000);

        // Window background
        context.fill(windowX, windowY, windowX + windowWidth, windowY + windowHeight, SkeetTheme.BG_PRIMARY);

        // Window border
        drawBorderHelper(context, windowX, windowY, windowWidth, windowHeight, SkeetTheme.ACCENT_PRIMARY);

        // Title bar
        drawTitleBar(context, mouseX, mouseY);

        // Tabs
        drawTabs(context, mouseX, mouseY);

        // Content based on current tab
        switch (currentTab) {
            case NOW_PLAYING:
                renderNowPlayingTab(context, mouseX, mouseY);
                break;
            case STATISTICS:
                renderStatisticsTab(context, mouseX, mouseY);
                break;
            case HISTORY:
                renderHistoryTab(context, mouseX, mouseY);
                break;
        }

        // Always show media controls at bottom
        renderMediaControls(context, mouseX, mouseY);

        // Render notification
        renderNotification(context);
    }

    private void drawTitleBar(GuiGraphics context, int mouseX, int mouseY) {
        int titleBarHeight = 35;

        // Title bar background
        context.fill(windowX, windowY, windowX + windowWidth, windowY + titleBarHeight, SkeetTheme.BG_SECONDARY);

        // Title
        String title = "🎵 Advanced Media Player";
        context.drawString(this.font, title, windowX + 15, windowY + 12, SkeetTheme.TEXT_PRIMARY, false);

        // Close button
        String closeIcon = "✕";
        int closeWidth = this.font.width(closeIcon);
        int closeX = windowX + windowWidth - closeWidth - 15;
        int closeY = windowY + 12;

        boolean closeHovered = isMouseOver(mouseX, mouseY, closeX - 2, closeY - 2,
                                         closeWidth + 4, this.font.lineHeight + 4);

        int closeColor = closeHovered ? SkeetTheme.STATUS_ERROR : SkeetTheme.TEXT_SECONDARY;
        context.drawString(this.font, closeIcon, closeX, closeY, closeColor, false);
    }

    private void drawTabs(GuiGraphics context, int mouseX, int mouseY) {
        int tabY = windowY + 35;
        int tabHeight = 30;
        int tabX = windowX;
        int tabWidth = windowWidth / 3;

        for (Tab tab : Tab.values()) {
            boolean isActive = tab == currentTab;
            boolean isHovered = mouseY >= tabY && mouseY < tabY + tabHeight &&
                               mouseX >= tabX && mouseX < tabX + tabWidth;

            // Tab background
            int bgColor = isActive ? SkeetTheme.BG_PRIMARY :
                         (isHovered ? SkeetTheme.BG_HOVER : SkeetTheme.BG_SECONDARY);
            context.fill(tabX, tabY, tabX + tabWidth, tabY + tabHeight, bgColor);

            // Tab border
            if (isActive) {
                context.fill(tabX, tabY + tabHeight - 2, tabX + tabWidth, tabY + tabHeight, SkeetTheme.ACCENT_PRIMARY);
            }

            // Tab text
            int textColor = isActive ? SkeetTheme.TEXT_PRIMARY : SkeetTheme.TEXT_SECONDARY;
            int textWidth = this.font.width(tab.name);
            context.drawString(this.font, tab.name,
                           tabX + (tabWidth - textWidth) / 2,
                           tabY + 10, textColor, false);

            tabX += tabWidth;
        }
    }

    private void renderNowPlayingTab(GuiGraphics context, int mouseX, int mouseY) {
        NowPlayingModule module = NowPlayingModule.getInstance();
        if (module == null) return;

        MediaPlayerData data = module.getCurrentData();
        int contentY = windowY + 90;
        int centerX = windowX + windowWidth / 2;

        // Album art
        int artSize = 200;
        int artX = centerX - artSize / 2;
        int artY = contentY + 20;

        // Album art background
        context.fill(artX, artY, artX + artSize, artY + artSize, 0xFF222222);

        // Render actual album art if available
        AlbumArtLoader albumArtLoader = AlbumArtLoader.getInstance();
        if (albumArtLoader.hasAlbumArt()) {
            albumArtLoader.render(context, artX, artY, artSize);
        } else {
            // Placeholder if no album art
            String artPlaceholder = "🎵";
            int artTextWidth = this.font.width(artPlaceholder);
            context.drawString(this.font, artPlaceholder,
                            centerX - artTextWidth / 2,
                            artY + artSize / 2 - 4,
                            SkeetTheme.TEXT_DIM, false);
        }

        contentY = artY + artSize + 30;

        if (!data.isActive()) {
            String noMedia = "No media playing";
            int width = this.font.width(noMedia);
            context.drawString(this.font, noMedia, centerX - width / 2, contentY, SkeetTheme.TEXT_DIM, false);
            return;
        }

        // Song info
        String title = data.getTitle();
        if (!title.isEmpty()) {
            title = truncateText(title, windowWidth - 60);
            int titleWidth = this.font.width(title);
            context.drawString(this.font, title, centerX - titleWidth / 2, contentY, SkeetTheme.TEXT_PRIMARY, false);
            contentY += 15;
        }

        String artist = data.getArtist();
        if (!artist.isEmpty()) {
            artist = truncateText(artist, windowWidth - 60);
            int artistWidth = this.font.width(artist);
            context.drawString(this.font, artist, centerX - artistWidth / 2, contentY, SkeetTheme.TEXT_SECONDARY, false);
            contentY += 12;
        }

        String album = data.getAlbum();
        if (!album.isEmpty()) {
            album = truncateText(album, windowWidth - 60);
            int albumWidth = this.font.width(album);
            context.drawString(this.font, album, centerX - albumWidth / 2, contentY, SkeetTheme.TEXT_DIM, false);
            contentY += 20;
        }

        // Progress bar (interactive timeline slider)
        if (data.hasTimeline()) {
            int barWidth = windowWidth - 80;
            int barX = windowX + 40;
            int barY = contentY;
            int barHeight = 8;

            // Check if mouse is over timeline (use local mouse coordinates)
            boolean isHoveringTimeline = false;
            if (minecraft != null && minecraft.mouseHandler != null) {
                double scaleFactor = minecraft.getWindow().getGuiScale();
                int localMouseX = (int)(minecraft.mouseHandler.xpos() / scaleFactor);
                int localMouseY = (int)(minecraft.mouseHandler.ypos() / scaleFactor);
                isHoveringTimeline = localMouseX >= barX && localMouseX <= barX + barWidth &&
                                    localMouseY >= barY - 4 && localMouseY <= barY + barHeight + 4;
            }

            // Background
            context.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF333333);

            // Progress
            float progress = isDraggingTimeline ? timelineSeekPosition : data.getProgress();
            int progressWidth = (int) (barWidth * progress);
            context.fill(barX, barY, barX + progressWidth, barY + barHeight, SkeetTheme.ACCENT_PRIMARY);

            // Progress handle (bigger when hovering or dragging)
            int handleX = barX + progressWidth;
            int handleSize = (isHoveringTimeline || isDraggingTimeline) ? 4 : 2;
            int handleColor = isDraggingTimeline ? SkeetTheme.ACCENT_PRIMARY :
                             (isHoveringTimeline ? SkeetTheme.TEXT_PRIMARY : SkeetTheme.TEXT_SECONDARY);
            context.fill(handleX - handleSize, barY - handleSize,
                        handleX + handleSize, barY + barHeight + handleSize, handleColor);

            // Hover preview time (show where the user would seek to)
            if (isHoveringTimeline && !isDraggingTimeline && minecraft.mouseHandler != null) {
                float hoverProgress = (float)(minecraft.mouseHandler.xpos() - barX) / barWidth;
                hoverProgress = Math.max(0, Math.min(1, hoverProgress));
                long hoverTimeMs = (long)(data.getDurationMs() * hoverProgress);
                String hoverTime = formatTime(hoverTimeMs);
                int hoverX = (int)minecraft.mouseHandler.xpos();
                context.drawString(this.font, hoverTime, hoverX - 15, barY - 15, SkeetTheme.TEXT_SECONDARY, false);
            }

            // Time labels
            String currentTime = isDraggingTimeline ?
                formatTime((long)(data.getDurationMs() * timelineSeekPosition)) :
                data.getFormattedPosition();
            String timeText = currentTime + " / " + data.getFormattedDuration();
            int timeWidth = this.font.width(timeText);
            context.drawString(this.font, timeText, centerX - timeWidth / 2, barY + barHeight + 5, SkeetTheme.TEXT_DIM, false);
        }
    }

    private void renderStatisticsTab(GuiGraphics context, int mouseX, int mouseY) {
        int statsY = windowY + 90;

        // Track current song for statistics
        NowPlayingModule module = NowPlayingModule.getInstance();
        if (module != null && module.getCurrentData().isActive()) {
            statsManager.trackSong(module.getCurrentData());
        }

        // Get statistics
        TotalStatistics totalStats = statsManager.getTotalStatistics();
        DailyStatistics todayStats = statsManager.getTodayStatistics();
        List<SongStatistics> topSongs = statsManager.getTopSongs(5);
        List<ArtistStatistics> topArtists = statsManager.getTopArtists(5);

        int y = statsY;

        // Title
        String title = "📊 Listening Statistics";
        int titleWidth = this.font.width(title);
        context.drawString(this.font, title, windowX + windowWidth / 2 - titleWidth / 2, y, SkeetTheme.TEXT_PRIMARY, false);
        y += 20;

        // Overall statistics
        context.fill(windowX + 20, y, windowX + windowWidth - 20, y + 1, SkeetTheme.ACCENT_PRIMARY);
        y += 5;

        context.drawString(this.font, "Overall Statistics:", windowX + 25, y, SkeetTheme.TEXT_PRIMARY, false);
        y += 15;

        String totalSongs = String.format("Total Songs: %d", totalStats.totalSongs);
        context.drawString(this.font, totalSongs, windowX + 35, y, SkeetTheme.TEXT_SECONDARY, false);
        y += 12;

        String totalArtists = String.format("Total Artists: %d", totalStats.totalArtists);
        context.drawString(this.font, totalArtists, windowX + 35, y, SkeetTheme.TEXT_SECONDARY, false);
        y += 12;

        String totalTime = String.format("Total Listen Time: %s", totalStats.getFormattedTotalTime());
        context.drawString(this.font, totalTime, windowX + 35, y, SkeetTheme.TEXT_SECONDARY, false);
        y += 12;

        String avgSession = String.format("Average Session: %s", totalStats.getFormattedAverageSession());
        context.drawString(this.font, avgSession, windowX + 35, y, SkeetTheme.TEXT_SECONDARY, false);
        y += 20;

        // Today's statistics
        context.drawString(this.font, "Today:", windowX + 25, y, SkeetTheme.TEXT_PRIMARY, false);
        y += 15;

        String todayTime = String.format("Listen Time: %s", todayStats.getFormattedListenTime());
        context.drawString(this.font, todayTime, windowX + 35, y, SkeetTheme.TEXT_SECONDARY, false);
        y += 12;

        String todaySongs = String.format("Songs Played: %d unique", todayStats.uniqueSongs);
        context.drawString(this.font, todaySongs, windowX + 35, y, SkeetTheme.TEXT_SECONDARY, false);
        y += 20;

        // Top songs
        if (y < windowY + windowHeight - 200) {
            context.fill(windowX + 20, y, windowX + windowWidth - 20, y + 1, SkeetTheme.ACCENT_PRIMARY);
            y += 5;

            context.drawString(this.font, "Top Songs:", windowX + 25, y, SkeetTheme.TEXT_PRIMARY, false);
            y += 15;

            for (int i = 0; i < Math.min(topSongs.size(), 3); i++) {
                SongStatistics song = topSongs.get(i);
                boolean isHovered = mouseY >= y && mouseY < y + 12 &&
                                   mouseX >= windowX + 35 && mouseX < windowX + windowWidth - 20;

                String songText = String.format("%d. %s - %s (%d plays)",
                    i + 1, truncateText(song.title, 200), truncateText(song.artist, 100), song.playCount);
                int textColor = isHovered ? SkeetTheme.TEXT_PRIMARY : SkeetTheme.TEXT_SECONDARY;
                context.drawString(this.font, songText, windowX + 35, y, textColor, false);

                // Play icon on hover
                if (isHovered) {
                    context.drawString(this.font, "▶", windowX + windowWidth - 30, y, SkeetTheme.ACCENT_PRIMARY, false);
                }
                y += 12;
            }
            y += 10;
        }

        // Top artists
        if (y < windowY + windowHeight - 160) {
            context.drawString(this.font, "Top Artists:", windowX + 25, y, SkeetTheme.TEXT_PRIMARY, false);
            y += 15;

            for (int i = 0; i < Math.min(topArtists.size(), 3); i++) {
                ArtistStatistics artist = topArtists.get(i);
                String artistText = String.format("%d. %s (%d plays, %s)",
                    i + 1, artist.artist, artist.playCount, artist.getFormattedListenTime());
                context.drawString(this.font, artistText, windowX + 35, y, SkeetTheme.TEXT_SECONDARY, false);
                y += 12;
            }
        }
    }

    private void renderHistoryTab(GuiGraphics context, int mouseX, int mouseY) {
        int historyY = windowY + 90;
        int itemHeight = 50;

        if (recentSongs.isEmpty()) {
            String noHistory = "No recently played songs";
            int width = this.font.width(noHistory);
            context.drawString(this.font, noHistory, windowX + windowWidth / 2 - width / 2, historyY + 20, SkeetTheme.TEXT_DIM, false);
            return;
        }

        // Update hoveredHistoryIndex
        hoveredHistoryIndex = -1;
        for (int i = 0; i < Math.min(recentSongs.size(), 8); i++) {
            int y = historyY + i * itemHeight;
            if (mouseY >= y && mouseY < y + itemHeight &&
                mouseX >= windowX + 15 && mouseX < windowX + windowWidth - 15) {
                hoveredHistoryIndex = i;
                break;
            }
        }

        for (int i = 0; i < Math.min(recentSongs.size(), 8); i++) {
            MediaPlayerData song = recentSongs.get(i);
            int y = historyY + i * itemHeight;

            boolean isHovered = (i == hoveredHistoryIndex);

            // Item background
            int bgColor = isHovered ? SkeetTheme.BG_HOVER : (i % 2 == 0 ? SkeetTheme.BG_PRIMARY : SkeetTheme.BG_SECONDARY);
            context.fill(windowX + 15, y, windowX + windowWidth - 15, y + itemHeight - 2, bgColor);

            // Song info
            String title = truncateText(song.getTitle(), windowWidth - 100);
            String artist = truncateText(song.getArtist(), windowWidth - 100);
            String album = song.getAlbum();
            if (album != null && !album.isEmpty()) {
                album = truncateText(album, windowWidth - 100);
            }

            context.drawString(this.font, title, windowX + 25, y + 10, SkeetTheme.TEXT_PRIMARY, false);
            context.drawString(this.font, artist, windowX + 25, y + 25, SkeetTheme.TEXT_SECONDARY, false);
            if (album != null && !album.isEmpty()) {
                context.drawString(this.font, album, windowX + 25, y + 37, SkeetTheme.TEXT_DIM, false);
            }

            // Play icon on hover with tooltip
            if (isHovered) {
                context.drawString(this.font, "▶", windowX + windowWidth - 30, y + 18, SkeetTheme.ACCENT_PRIMARY, false);

                // Tooltip
                String tooltip = "Click to play";
                int tooltipWidth = this.font.width(tooltip);
                context.drawString(this.font, tooltip, windowX + windowWidth - 30 - tooltipWidth - 10, y + 18, SkeetTheme.TEXT_DIM, false);
            }
        }
    }

    private void renderMediaControls(GuiGraphics context, int mouseX, int mouseY) {
        // Controls background
        int controlsHeight = 130;
        int controlsY = windowY + windowHeight - controlsHeight;
        context.fill(windowX, controlsY, windowX + windowWidth, windowY + windowHeight, SkeetTheme.BG_SECONDARY);

        // Separator
        context.fill(windowX, controlsY, windowX + windowWidth, controlsY + 1, SkeetTheme.ACCENT_PRIMARY);

        // Main playback buttons
        boolean prevHovered = isMouseOver(mouseX, mouseY, prevButtonX, prevButtonY, prevButtonWidth, prevButtonHeight);
        boolean playHovered = isMouseOver(mouseX, mouseY, playButtonX, playButtonY, playButtonWidth, playButtonHeight);
        boolean nextHovered = isMouseOver(mouseX, mouseY, nextButtonX, nextButtonY, nextButtonWidth, nextButtonHeight);

        drawMediaButton(context, prevButtonX, prevButtonY, prevButtonWidth, prevButtonHeight, "⏮", prevHovered);
        drawMediaButton(context, playButtonX, playButtonY, playButtonWidth, playButtonHeight, getPlayPauseIcon(), playHovered);
        drawMediaButton(context, nextButtonX, nextButtonY, nextButtonWidth, nextButtonHeight, "⏭", nextHovered);

        // Volume slider
        drawVolumeSlider(context, mouseX, mouseY);
    }

    private void drawMediaButton(GuiGraphics context, int x, int y, int width, int height, String icon, boolean hovered) {
        // Button background
        int bgColor = hovered ? SkeetTheme.BG_HOVER : SkeetTheme.BG_SECONDARY;
        context.fill(x, y, x + width, y + height, bgColor);

        // Button border
        int borderColor = hovered ? SkeetTheme.ACCENT_PRIMARY : 0xFF444444;
        drawBorderHelper(context, x, y, width, height, borderColor);

        // Icon
        int iconWidth = this.font.width(icon);
        int iconX = x + (width - iconWidth) / 2;
        int iconY = y + (height - this.font.lineHeight) / 2;

        int iconColor = hovered ? SkeetTheme.TEXT_PRIMARY : SkeetTheme.TEXT_SECONDARY;
        context.drawString(this.font, icon, iconX, iconY, iconColor, false);
    }

    private void drawVolumeSlider(GuiGraphics context, int mouseX, int mouseY) {
        // Volume label
        context.drawString(this.font, "🔊", volumeSliderX - 20, volumeSliderY + 2, SkeetTheme.TEXT_SECONDARY, false);

        // Slider background
        context.fill(volumeSliderX, volumeSliderY + 8, volumeSliderX + volumeSliderWidth, volumeSliderY + 12, 0xFF333333);

        // Slider fill
        int fillWidth = (int) (volumeSliderWidth * volume);
        context.fill(volumeSliderX, volumeSliderY + 8, volumeSliderX + fillWidth, volumeSliderY + 12, SkeetTheme.ACCENT_PRIMARY);

        // Slider handle
        int handleX = volumeSliderX + fillWidth;
        boolean handleHovered = mouseX >= handleX - 5 && mouseX <= handleX + 5 &&
                                mouseY >= volumeSliderY + 5 && mouseY <= volumeSliderY + 15;
        int handleColor = handleHovered ? SkeetTheme.TEXT_PRIMARY : SkeetTheme.TEXT_SECONDARY;
        context.fill(handleX - 3, volumeSliderY + 5, handleX + 3, volumeSliderY + 15, handleColor);

        // Volume percentage
        String volumeText = (int)(volume * 100) + "%";
        context.drawString(this.font, volumeText, volumeSliderX + volumeSliderWidth + 5, volumeSliderY + 2, SkeetTheme.TEXT_DIM, false);
    }

    private void drawBorderHelper(GuiGraphics context, int x, int y, int width, int height, int color) {
        context.fill(x, y, x + width, y + 1, color);           // Top
        context.fill(x, y + height - 1, x + width, y + height, color); // Bottom
        context.fill(x, y, x + 1, y + height, color);          // Left
        context.fill(x + width - 1, y, x + width, y + height, color);  // Right
    }

    private String getPlayPauseIcon() {
        NowPlayingModule module = NowPlayingModule.getInstance();
        if (module == null) return "▶";

        MediaPlayerData data = module.getCurrentData();
        if (data.getStatus() == MediaPlayerData.PlaybackStatus.PLAYING) {
            return "⏸";
        }
        return "▶";
    }

    private void renderNotification(GuiGraphics context) {
        if (notificationMessage.isEmpty()) return;

        long timeSinceNotification = System.currentTimeMillis() - notificationTime;
        if (timeSinceNotification > 3000) { // Show for 3 seconds
            notificationMessage = "";
            return;
        }

        // Fade out effect
        float alpha = 1.0f;
        if (timeSinceNotification > 2000) {
            alpha = 1.0f - ((timeSinceNotification - 2000) / 1000.0f);
        }
        int alphaInt = (int)(alpha * 255);

        // Position above the media controls, below the content area
        int controlsHeight = 130;
        int notificationY = windowY + windowHeight - controlsHeight - 35; // Above controls with some padding
        int textWidth = this.font.width(notificationMessage);
        int notificationX = windowX + (windowWidth - textWidth) / 2;

        // Background with rounded appearance
        int bgColor = (alphaInt << 24) | 0x2a2a2a;
        int padding = 10;
        int boxX = notificationX - padding;
        int boxY = notificationY - padding;
        int boxWidth = textWidth + padding * 2;
        int boxHeight = this.font.lineHeight + padding * 2;

        // Draw background
        context.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, bgColor);

        // Draw border for better visibility
        int borderColor = (alphaInt << 24) | 0x4a4a4a;
        context.fill(boxX, boxY, boxX + boxWidth, boxY + 1, borderColor); // Top
        context.fill(boxX, boxY + boxHeight - 1, boxX + boxWidth, boxY + boxHeight, borderColor); // Bottom
        context.fill(boxX, boxY, boxX + 1, boxY + boxHeight, borderColor); // Left
        context.fill(boxX + boxWidth - 1, boxY, boxX + boxWidth, boxY + boxHeight, borderColor); // Right

        // Text
        int textColor = (alphaInt << 24) | 0xffffff;
        context.drawString(this.font, notificationMessage, notificationX, notificationY, textColor, false);
    }

    private void showNotification(String message) {
        notificationMessage = message;
        notificationTime = System.currentTimeMillis();
    }

    private void copyToClipboard(String text) {
        try {
            StringSelection stringSelection = new StringSelection(text);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(stringSelection, null);
        } catch (Exception e) {
            // Clipboard operation failed
        }
    }

    private String truncateText(String text, int maxWidth) {
        if (this.font.width(text) <= maxWidth) {
            return text;
        }

        String ellipsis = "...";
        int low = 0;
        int high = text.length();

        while (low < high) {
            int mid = (low + high + 1) / 2;
            String truncated = text.substring(0, mid) + ellipsis;
            if (this.font.width(truncated) <= maxWidth) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }

        return text.substring(0, low) + ellipsis;
    }

    private boolean isMouseOver(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private String formatTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    private void setVolume(float newVolume) {
        float oldVolume = this.volume;
        this.volume = newVolume;

        // Apply volume change via Windows Media Control
        WindowsMediaControl.setVolume(newVolume);
    }


    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        if (button != 0) {
            return super.mouseClicked(click, doubled);
        }

        int mx = (int) mouseX;
        int my = (int) mouseY;

        // Close button
        String closeIcon = "✕";
        int closeWidth = this.font.width(closeIcon);
        int closeX = windowX + windowWidth - closeWidth - 15;
        int closeY = windowY + 12;

        if (mx >= closeX - 2 && mx <= closeX + closeWidth + 2 &&
            my >= closeY - 2 && my <= closeY + this.font.lineHeight + 2) {
            this.onClose();
            return true;
        }

        // Tab switching
        int tabY = windowY + 35;
        int tabHeight = 30;
        if (my >= tabY && my < tabY + tabHeight) {
            int tabWidth = windowWidth / 3;
            int tabIndex = (mx - windowX) / tabWidth;
            if (tabIndex >= 0 && tabIndex < Tab.values().length) {
                currentTab = Tab.values()[tabIndex];
                init(); // Reinitialize to update focus
                return true;
            }
        }

        // Statistics tab - click on top songs to copy to clipboard
        if (currentTab == Tab.STATISTICS) {
            int statsY = windowY + 90;
            int y = statsY;

            // Skip to top songs section (approximate position calculation)
            y += 20; // Title
            y += 5;  // Separator
            y += 15; // Overall Statistics label
            y += 12 * 4; // 4 stat lines
            y += 20; // Space
            y += 15; // Today label
            y += 12 * 2; // 2 today lines
            y += 20; // Space

            if (y < windowY + windowHeight - 200) {
                y += 5;  // Separator
                y += 15; // Top Songs label

                List<SongStatistics> topSongs = statsManager.getTopSongs(5);
                for (int i = 0; i < Math.min(topSongs.size(), 3); i++) {
                    if (my >= y && my < y + 12 && mx >= windowX + 35 && mx < windowX + windowWidth - 20) {
                        // Copy song info to clipboard
                        SongStatistics song = topSongs.get(i);
                        String clipboardText = song.title + " - " + song.artist;
                        copyToClipboard(clipboardText);
                        showNotification("Copied: " + clipboardText);

                        // Try to play anyway (might work with iTunes)
                        MediaPlayerData data = new MediaPlayerData();
                        data.setTitle(song.title);
                        data.setArtist(song.artist);
                        data.setAlbum(song.album);
                        searchManager.playFromHistory(data);
                        return true;
                    }
                    y += 12;
                }
            }
        }

        // History items - copy to clipboard and attempt to play
        if (currentTab == Tab.HISTORY && hoveredHistoryIndex >= 0 && hoveredHistoryIndex < recentSongs.size()) {
            MediaPlayerData song = recentSongs.get(hoveredHistoryIndex);

            // Copy song info to clipboard
            String clipboardText = song.getTitle() + " - " + song.getArtist();
            copyToClipboard(clipboardText);
            showNotification("Copied: " + clipboardText);

            // Try to play the selected history item (might work with iTunes)
            searchManager.playFromHistory(song);
            return true;
        }

        // Media controls
        if (isMouseOver(mx, my, prevButtonX, prevButtonY, prevButtonWidth, prevButtonHeight)) {
            WindowsMediaControl.previousTrack();
            return true;
        }

        if (isMouseOver(mx, my, playButtonX, playButtonY, playButtonWidth, playButtonHeight)) {
            WindowsMediaControl.togglePlayPause();
            return true;
        }

        if (isMouseOver(mx, my, nextButtonX, nextButtonY, nextButtonWidth, nextButtonHeight)) {
            WindowsMediaControl.nextTrack();
            return true;
        }

        // Timeline slider interaction (for seeking in the song)
        if (currentTab == Tab.NOW_PLAYING) {
            NowPlayingModule module = NowPlayingModule.getInstance();
            if (module != null) {
                MediaPlayerData data = module.getCurrentData();
                if (data.hasTimeline()) {
                    int barWidth = windowWidth - 80;
                    int barX = windowX + 40;

                    // Calculate actual position based on content
                    int contentY = windowY + 90; // Start after tabs
                    contentY += 220; // Skip album art
                    contentY += 30; // Title space
                    if (!data.getArtist().isEmpty()) contentY += 15;
                    if (!data.getAlbum().isEmpty()) contentY += 12 + 20;

                    int barY = contentY;
                    int barHeight = 8;

                    if (mx >= barX && mx <= barX + barWidth &&
                        my >= barY - 4 && my <= barY + barHeight + 4) {
                        isDraggingTimeline = true;
                        timelineSeekPosition = (float)(mx - barX) / barWidth;
                        timelineSeekPosition = Math.max(0, Math.min(1, timelineSeekPosition));
                        return true;
                    }
                }
            }
        }

        // Volume slider
        if (my >= volumeSliderY && my <= volumeSliderY + 20 &&
            mx >= volumeSliderX && mx <= volumeSliderX + volumeSliderWidth) {
            isDraggingVolume = true;
            float newVolume = (float)(mx - volumeSliderX) / volumeSliderWidth;
            newVolume = Math.max(0, Math.min(1, newVolume));
            setVolume(newVolume);
            return true;
        }

        // Title bar dragging
        if (my >= windowY && my <= windowY + 35) {
            isDragging = true;
            dragStartX = mx - windowX;
            dragStartY = my - windowY;
            return true;
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent click) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        // Handle timeline seek on release
        if (isDraggingTimeline) {
            isDraggingTimeline = false;

            // Perform the seek operation
            NowPlayingModule module = NowPlayingModule.getInstance();
            if (module != null) {
                MediaPlayerData data = module.getCurrentData();
                if (data.hasTimeline()) {
                    long seekPositionMs = (long)(data.getDurationMs() * timelineSeekPosition);
                    searchManager.seekToPosition(seekPositionMs);
                }
            }
            return true;
        }

        // Stop volume dragging
        if (isDraggingVolume) {
            isDraggingVolume = false;
            return true;
        }

        isDragging = false;
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

            windowX = Math.max(0, Math.min(windowX, this.width - windowWidth));
            windowY = Math.max(0, Math.min(windowY, this.height - windowHeight));

            init();
            return true;
        }

        // Volume slider dragging
        int mx = (int) mouseX;
        int my = (int) mouseY;
        if (isDraggingVolume && button == 0) {
            float newVolume = (float)(mx - volumeSliderX) / volumeSliderWidth;
            newVolume = Math.max(0, Math.min(1, newVolume));
            setVolume(newVolume);
            return true;
        }

        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // No scrolling needed for current tabs
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        // Hovering is handled directly in render methods
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent input) {
        int keyCode = input.key();
        int scanCode = input.scancode();
        int modifiers = input.modifiers();

        // Media hotkeys
        if (keyCode == GLFW.GLFW_KEY_SPACE) {
            WindowsMediaControl.togglePlayPause();
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_LEFT) {
            WindowsMediaControl.previousTrack();
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            WindowsMediaControl.nextTrack();
            return true;
        }

        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent input) {
        return super.charTyped(input);
    }

    @Override
    public void tick() {
        // TextFieldWidget doesn't have tick() in newer versions
        super.tick();

        // Update recent songs periodically
        NowPlayingModule module = NowPlayingModule.getInstance();
        if (module != null) {
            MediaPlayerData currentSong = module.getCurrentData();
            if (currentSong.isActive()) {
                // Add to history if not already present
                boolean exists = recentSongs.stream()
                    .anyMatch(s -> s.getTitle().equals(currentSong.getTitle()) &&
                                  s.getArtist().equals(currentSong.getArtist()));
                if (!exists) {
                    recentSongs.add(0, currentSong);
                    if (recentSongs.size() > 20) {
                        recentSongs.remove(recentSongs.size() - 1);
                    }
                }
            }
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
