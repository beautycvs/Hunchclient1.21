package dev.hunchclient.gui;

import dev.hunchclient.gui.skeet.SkeetTheme;
import dev.hunchclient.module.impl.misc.MediaPlayerData;
import dev.hunchclient.module.impl.misc.NowPlayingModule;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Edit screen for NowPlaying HUD
 * Allows dragging and resizing the HUD
 */
public class NowPlayingEditScreen extends Screen {

    private final Screen parent;
    private final NowPlayingModule module;

    // Dragging
    private boolean isDragging = false;
    private int dragStartX, dragStartY;
    private float dragStartHudX, dragStartHudY;

    // Resizing
    private boolean isResizing = false;
    private int resizeStartX, resizeStartY;
    private int resizeStartWidth, resizeStartHeight;

    // Preview data (fake data for preview)
    private final MediaPlayerData previewData;

    public NowPlayingEditScreen(Screen parent, NowPlayingModule module) {
        super(Component.literal("Edit Now Playing HUD"));
        this.parent = parent;
        this.module = module;

        // Create fake preview data
        previewData = new MediaPlayerData();
        previewData.setActive(true);
        previewData.setTitle("Example Song Title - Very Long Name That Might Scroll");
        previewData.setArtist("Example Artist");
        previewData.setAlbum("Example Album");
        previewData.setStatus(MediaPlayerData.PlaybackStatus.PLAYING);
        previewData.setPositionMs(125000); // 2:05
        previewData.setDurationMs(240000); // 4:00
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        // Semi-transparent background
        context.fill(0, 0, this.width, this.height, 0x80000000);

        // Render the HUD with preview data
        renderHudPreview(context, mouseX, mouseY);

        // Instructions
        drawInstructions(context, mouseX, mouseY);
    }

    private void renderHudPreview(GuiGraphics context, int mouseX, int mouseY) {
        int screenWidth = this.width;
        int screenHeight = this.height;

        // Get HUD position from module
        int x = (int) (screenWidth * module.getHudX() / 100.0f) - module.getHudWidth();
        int y = (int) (screenHeight * module.getHudY() / 100.0f);
        int width = module.getHudWidth();
        int height = module.getHudHeight();

        // Check if mouse is over HUD
        boolean isHovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;

        // Check if mouse is over resize handle
        boolean isResizeHovered = mouseX >= x + width - 15 && mouseX <= x + width &&
                                  mouseY >= y + height - 15 && mouseY <= y + height;

        // Background
        int bgColor = isHovered ? SkeetTheme.BG_HOVER : module.getBackgroundColor();
        context.fill(x, y, x + width, y + height, bgColor);

        // Border (highlight when hovered)
        int borderColor = isHovered ? SkeetTheme.ACCENT_PRIMARY : module.getAccentColor();
        drawBorder(context, x, y, width, height, borderColor, 2);

        // Render preview content (similar to actual HUD)
        int textY = y + 5;

        // Title
        String title = truncateText(previewData.getTitle(), width - 30);
        context.drawString(this.font, title, x + 5, textY, module.getTextColor(), false);
        textY += 10;

        // Artist
        String artist = truncateText(previewData.getArtist(), width - 10);
        context.drawString(this.font, artist, x + 5, textY, 0xFFAAAAAA, false);
        textY += 9;

        // Album
        if (module.isShowAlbum()) {
            String album = truncateText(previewData.getAlbum(), width - 10);
            context.drawString(this.font, album, x + 5, textY, 0xFF888888, false);
            textY += 8;
        }

        // Progress bar
        if (module.isShowProgressBar()) {
            textY += 3;
            int barWidth = width - 10;
            int barHeight = 4;
            int barX = x + 5;
            int barY = textY;

            // Background
            context.fill(barX, barY, barX + barWidth, barY + barHeight, module.getProgressBarBgColor());

            // Progress
            float progress = previewData.getProgress();
            int progressWidth = (int) (barWidth * progress);
            context.fill(barX, barY, barX + progressWidth, barY + barHeight, module.getProgressBarColor());

            textY += barHeight + 3;

            // Time labels
            String timeText = previewData.getFormattedPosition() + " / " + previewData.getFormattedDuration();
            context.drawString(this.font, timeText, x + 5, textY, 0xFFAAAAAA, false);
        }

        // Playback status indicator
        String statusIcon = "▶";
        context.drawString(this.font, statusIcon, x + width - 15, y + 5, module.getAccentColor(), false);

        // Resize handle
        if (isResizeHovered || isResizing) {
            drawResizeHandle(context, x + width - 15, y + height - 15, SkeetTheme.ACCENT_PRIMARY);
        } else {
            drawResizeHandle(context, x + width - 15, y + height - 15, 0xFF666666);
        }

        // Show dimensions
        String dimText = width + "x" + height;
        int dimWidth = this.font.width(dimText);
        context.fill(x + width / 2 - dimWidth / 2 - 3, y - 20, x + width / 2 + dimWidth / 2 + 3, y - 8, 0xE0000000);
        context.drawString(this.font, dimText, x + width / 2 - dimWidth / 2, y - 18, 0xFFFFFFFF, false);
    }

    private void drawInstructions(GuiGraphics context, int mouseX, int mouseY) {
        String[] instructions = {
            "§e§lNow Playing HUD Editor",
            "§7Drag to move, resize handle to resize",
            "§7Press ESC or click Done to save"
        };

        int y = 20;
        for (String line : instructions) {
            context.drawString(this.font, Component.literal(line), 10, y, 0xFFFFFFFF, false);
            y += 12;
        }

        // Done button
        String doneText = "Done";
        int doneWidth = this.font.width(doneText) + 20;
        int doneX = this.width / 2 - doneWidth / 2;
        int doneY = this.height - 40;

        boolean doneHovered = mouseX >= doneX && mouseX <= doneX + doneWidth &&
                              mouseY >= doneY && mouseY <= doneY + 20;

        int doneColor = doneHovered ? SkeetTheme.ACCENT_PRIMARY : SkeetTheme.BG_SECONDARY;
        context.fill(doneX, doneY, doneX + doneWidth, doneY + 20, doneColor);
        drawBorder(context, doneX, doneY, doneWidth, 20, SkeetTheme.ACCENT_PRIMARY, 1);

        int textColor = doneHovered ? SkeetTheme.TEXT_PRIMARY : SkeetTheme.TEXT_SECONDARY;
        context.drawString(this.font, doneText,
            doneX + doneWidth / 2 - this.font.width(doneText) / 2,
            doneY + 6, textColor, false);
    }

    private void drawBorder(GuiGraphics context, int x, int y, int width, int height, int color, int thickness) {
        context.fill(x, y, x + width, y + thickness, color);           // Top
        context.fill(x, y + height - thickness, x + width, y + height, color); // Bottom
        context.fill(x, y, x + thickness, y + height, color);          // Left
        context.fill(x + width - thickness, y, x + width, y + height, color);  // Right
    }

    private void drawResizeHandle(GuiGraphics context, int x, int y, int color) {
        for (int i = 0; i < 3; i++) {
            int offset = i * 4 + 2;
            context.fill(x + 15 - offset, y + 13, x + 15 - offset + 2, y + 15, color);
            context.fill(x + 13, y + 15 - offset, x + 15, y + 15 - offset + 2, color);
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

        int screenWidth = this.width;
        int screenHeight = this.height;

        int x = (int) (screenWidth * module.getHudX() / 100.0f) - module.getHudWidth();
        int y = (int) (screenHeight * module.getHudY() / 100.0f);
        int width = module.getHudWidth();
        int height = module.getHudHeight();

        // Check Done button
        String doneText = "Done";
        int doneWidth = this.font.width(doneText) + 20;
        int doneX = this.width / 2 - doneWidth / 2;
        int doneY = this.height - 40;

        if (mx >= doneX && mx <= doneX + doneWidth && my >= doneY && my <= doneY + 20) {
            this.onClose();
            return true;
        }

        // Check resize handle
        if (mx >= x + width - 15 && mx <= x + width && my >= y + height - 15 && my <= y + height) {
            isResizing = true;
            resizeStartX = mx;
            resizeStartY = my;
            resizeStartWidth = width;
            resizeStartHeight = height;
            return true;
        }

        // Check HUD area for dragging
        if (mx >= x && mx <= x + width && my >= y && my <= y + height) {
            isDragging = true;
            dragStartX = mx;
            dragStartY = my;
            dragStartHudX = module.getHudX();
            dragStartHudY = module.getHudY();
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
        isResizing = false;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent click, double offsetX, double offsetY) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        int mx = (int) mouseX;
        int my = (int) mouseY;

        if (isDragging) {
            int deltaMouseX = mx - dragStartX;
            int deltaMouseY = my - dragStartY;

            // Calculate new position (in percentage)
            float newX = dragStartHudX + (deltaMouseX * 100.0f / this.width);
            float newY = dragStartHudY + (deltaMouseY * 100.0f / this.height);

            // Clamp to screen
            newX = Math.max(0, Math.min(100, newX));
            newY = Math.max(0, Math.min(100, newY));

            module.setHudPosition(newX, newY);
            return true;
        }

        if (isResizing) {
            int deltaWidth = mx - resizeStartX;
            int deltaHeight = my - resizeStartY;

            int newWidth = Math.max(150, Math.min(600, resizeStartWidth + deltaWidth));
            int newHeight = Math.max(50, Math.min(200, resizeStartHeight + deltaHeight));

            module.setHudSize(newWidth, newHeight);
            return true;
        }

        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public void onClose() {
        // Save config when closing
        module.saveConfig();
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
