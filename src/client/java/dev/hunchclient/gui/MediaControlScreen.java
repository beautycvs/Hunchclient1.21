package dev.hunchclient.gui;

import dev.hunchclient.gui.skeet.SkeetTheme;
import dev.hunchclient.module.impl.misc.MediaPlayerData;
import dev.hunchclient.module.impl.misc.NowPlayingModule;
import dev.hunchclient.module.impl.misc.WindowsMediaControl;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Media Player Control Screen
 *
 * Popup window for controlling media playback
 * Buttons: Play/Pause, Previous, Next
 * Shows current song info
 */
public class MediaControlScreen extends Screen {

    private final Screen parent;

    // Window state
    private int windowX, windowY;
    private int windowWidth = 320;
    private int windowHeight = 220;

    // Dragging
    private boolean isDragging = false;
    private int dragStartX, dragStartY;

    // Button positions (calculated in init)
    private int prevButtonX, prevButtonY, prevButtonWidth, prevButtonHeight;
    private int playButtonX, playButtonY, playButtonWidth, playButtonHeight;
    private int nextButtonX, nextButtonY, nextButtonWidth, nextButtonHeight;

    // Button state
    private boolean prevHovered = false;
    private boolean playHovered = false;
    private boolean nextHovered = false;

    public MediaControlScreen(Screen parent) {
        super(Component.literal("Media Player"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        // Center window
        windowX = (this.width - windowWidth) / 2;
        windowY = (this.height - windowHeight) / 2;

        // Calculate button positions
        int buttonSize = 50;
        int buttonSpacing = 15;
        int buttonY = windowY + 140;

        int totalWidth = buttonSize * 3 + buttonSpacing * 2;
        int startX = windowX + (windowWidth - totalWidth) / 2;

        // Previous button
        prevButtonX = startX;
        prevButtonY = buttonY;
        prevButtonWidth = buttonSize;
        prevButtonHeight = buttonSize;

        // Play/Pause button
        playButtonX = startX + buttonSize + buttonSpacing;
        playButtonY = buttonY;
        playButtonWidth = buttonSize;
        playButtonHeight = buttonSize;

        // Next button
        nextButtonX = startX + (buttonSize + buttonSpacing) * 2;
        nextButtonY = buttonY;
        nextButtonWidth = buttonSize;
        nextButtonHeight = buttonSize;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        // Dark overlay
        context.fill(0, 0, this.width, this.height, 0x80000000);

        // Window background
        context.fill(windowX, windowY, windowX + windowWidth, windowY + windowHeight, SkeetTheme.BG_PRIMARY);

        // Window border
        drawBorder(context, windowX, windowY, windowWidth, windowHeight, SkeetTheme.ACCENT_PRIMARY);

        // Title bar
        drawTitleBar(context, mouseX, mouseY);

        // Song info
        drawSongInfo(context);

        // Media buttons
        prevHovered = isMouseOver(mouseX, mouseY, prevButtonX, prevButtonY, prevButtonWidth, prevButtonHeight);
        playHovered = isMouseOver(mouseX, mouseY, playButtonX, playButtonY, playButtonWidth, playButtonHeight);
        nextHovered = isMouseOver(mouseX, mouseY, nextButtonX, nextButtonY, nextButtonWidth, nextButtonHeight);

        drawMediaButton(context, prevButtonX, prevButtonY, prevButtonWidth, prevButtonHeight, "⏮", prevHovered);
        drawMediaButton(context, playButtonX, playButtonY, playButtonWidth, playButtonHeight, getPlayPauseIcon(), playHovered);
        drawMediaButton(context, nextButtonX, nextButtonY, nextButtonWidth, nextButtonHeight, "⏭", nextHovered);

        // Help text
        String help = "Click buttons to control media playback";
        int helpWidth = this.font.width(help);
        context.drawString(this.font, help, windowX + (windowWidth - helpWidth) / 2, windowY + windowHeight - 20, SkeetTheme.TEXT_DIM, false);
    }

    private void drawTitleBar(GuiGraphics context, int mouseX, int mouseY) {
        int titleBarHeight = 30;

        // Title bar background (slightly lighter)
        context.fill(windowX, windowY, windowX + windowWidth, windowY + titleBarHeight, SkeetTheme.BG_SECONDARY);

        // Title
        String title = "🎵 Media Player";
        context.drawString(this.font, title, windowX + 10, windowY + 10, SkeetTheme.TEXT_PRIMARY, false);

        // Close button
        String closeIcon = "✕";
        int closeWidth = this.font.width(closeIcon);
        int closeX = windowX + windowWidth - closeWidth - 10;
        int closeY = windowY + 10;

        boolean closeHovered = mouseX >= closeX - 2 && mouseX <= closeX + closeWidth + 2 &&
            mouseY >= closeY - 2 && mouseY <= closeY + this.font.lineHeight + 2;

        int closeColor = closeHovered ? SkeetTheme.STATUS_ERROR : SkeetTheme.TEXT_SECONDARY;
        context.drawString(this.font, closeIcon, closeX, closeY, closeColor, false);
    }

    private void drawSongInfo(GuiGraphics context) {
        NowPlayingModule module = NowPlayingModule.getInstance();
        if (module == null) {
            return;
        }

        MediaPlayerData data = module.getCurrentData();
        int infoY = windowY + 50;
        int centerX = windowX + windowWidth / 2;

        if (!data.isActive()) {
            String noMedia = "No media playing";
            int width = this.font.width(noMedia);
            context.drawString(this.font, noMedia, centerX - width / 2, infoY, SkeetTheme.TEXT_DIM, false);
            return;
        }

        // Title
        String title = data.getTitle();
        if (!title.isEmpty()) {
            title = truncateText(title, windowWidth - 40);
            int titleWidth = this.font.width(title);
            context.drawString(this.font, title, centerX - titleWidth / 2, infoY, SkeetTheme.TEXT_PRIMARY, false);
            infoY += 12;
        }

        // Artist
        String artist = data.getArtist();
        if (!artist.isEmpty()) {
            artist = truncateText(artist, windowWidth - 40);
            int artistWidth = this.font.width(artist);
            context.drawString(this.font, artist, centerX - artistWidth / 2, infoY, SkeetTheme.TEXT_SECONDARY, false);
            infoY += 12;
        }

        // Album
        String album = data.getAlbum();
        if (!album.isEmpty()) {
            album = truncateText(album, windowWidth - 40);
            int albumWidth = this.font.width(album);
            context.drawString(this.font, album, centerX - albumWidth / 2, infoY, SkeetTheme.TEXT_DIM, false);
            infoY += 15;
        }

        // Progress bar (if available)
        if (data.hasTimeline()) {
            int barWidth = windowWidth - 60;
            int barX = windowX + 30;
            int barY = infoY;
            int barHeight = 6;

            // Background
            context.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF333333);

            // Progress
            float progress = data.getProgress();
            int progressWidth = (int) (barWidth * progress);
            context.fill(barX, barY, barX + progressWidth, barY + barHeight, SkeetTheme.ACCENT_PRIMARY);

            // Time labels
            String timeText = data.getFormattedPosition() + " / " + data.getFormattedDuration();
            int timeWidth = this.font.width(timeText);
            context.drawString(this.font, timeText, centerX - timeWidth / 2, barY + barHeight + 5, SkeetTheme.TEXT_DIM, false);
        }
    }

    private void drawMediaButton(GuiGraphics context, int x, int y, int width, int height, String icon, boolean hovered) {
        // Button background
        int bgColor = hovered ? SkeetTheme.BG_HOVER : SkeetTheme.BG_SECONDARY;
        context.fill(x, y, x + width, y + height, bgColor);

        // Button border
        int borderColor = hovered ? SkeetTheme.ACCENT_PRIMARY : 0xFF444444;
        drawBorder(context, x, y, width, height, borderColor);

        // Icon (centered)
        int iconWidth = this.font.width(icon);
        int iconX = x + (width - iconWidth) / 2;
        int iconY = y + (height - this.font.lineHeight) / 2;

        int iconColor = hovered ? SkeetTheme.TEXT_PRIMARY : SkeetTheme.TEXT_SECONDARY;
        context.drawString(this.font, icon, iconX, iconY, iconColor, false);
    }

    private void drawBorder(GuiGraphics context, int x, int y, int width, int height, int color) {
        context.fill(x, y, x + width, y + 1, color);           // Top
        context.fill(x, y + height - 1, x + width, y + height, color); // Bottom
        context.fill(x, y, x + 1, y + height, color);          // Left
        context.fill(x + width - 1, y, x + width, y + height, color);  // Right
    }

    private String getPlayPauseIcon() {
        NowPlayingModule module = NowPlayingModule.getInstance();
        if (module == null) {
            return "▶";
        }

        MediaPlayerData data = module.getCurrentData();
        if (data.getStatus() == MediaPlayerData.PlaybackStatus.PLAYING) {
            return "⏸";
        }
        return "▶";
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

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        if (button != 0) {
            return super.mouseClicked(click, doubled); // Only left click
        }

        int mx = (int) mouseX;
        int my = (int) mouseY;

        // Close button
        String closeIcon = "✕";
        int closeWidth = this.font.width(closeIcon);
        int closeX = windowX + windowWidth - closeWidth - 10;
        int closeY = windowY + 10;

        if (mx >= closeX - 2 && mx <= closeX + closeWidth + 2 &&
            my >= closeY - 2 && my <= closeY + this.font.lineHeight + 2) {
            this.onClose();
            return true;
        }

        // Previous button
        if (isMouseOver(mx, my, prevButtonX, prevButtonY, prevButtonWidth, prevButtonHeight)) {
            WindowsMediaControl.previousTrack();
            return true;
        }

        // Play/Pause button
        if (isMouseOver(mx, my, playButtonX, playButtonY, playButtonWidth, playButtonHeight)) {
            WindowsMediaControl.togglePlayPause();
            return true;
        }

        // Next button
        if (isMouseOver(mx, my, nextButtonX, nextButtonY, nextButtonWidth, nextButtonHeight)) {
            WindowsMediaControl.nextTrack();
            return true;
        }

        // Title bar dragging
        if (my >= windowY && my <= windowY + 30) {
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

            // Clamp to screen bounds
            windowX = Math.max(0, Math.min(windowX, this.width - windowWidth));
            windowY = Math.max(0, Math.min(windowY, this.height - windowHeight));

            // Recalculate button positions
            init();
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false; // Don't pause game
    }
}
