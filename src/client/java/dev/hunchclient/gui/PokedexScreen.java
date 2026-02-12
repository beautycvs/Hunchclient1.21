package dev.hunchclient.gui;

import com.mojang.blaze3d.platform.NativeImage;
import dev.hunchclient.module.impl.PokedexManager;
import dev.hunchclient.module.impl.PokedexManager.CapturedEntry;
import dev.hunchclient.render.ColoredTextSegment;
import dev.hunchclient.render.CustomFontColorParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Pokedex Screen - Gen 4 Pokemon style!
 * Shows the legacy OG user list.
 * Click on captured users to see their detail view with:
 * - Screenshot at top
 * - 3D skin body from mc-heads.net below
 * - Gradient name rendering
 * - Capture info (date, time, location, skyblock island)
 */
public class PokedexScreen extends Screen {

    private final Screen parent;
    private final PokedexManager pokedex;
    private static final Minecraft mc = Minecraft.getInstance();

    // Layout constants
    private static final int ENTRY_WIDTH = 180;
    private static final int ENTRY_HEIGHT = 24;
    private static final int ENTRIES_PER_ROW = 3;
    private static final int PADDING = 8;
    private static final int HEADER_HEIGHT = 50;
    private static final int DETAIL_PANEL_WIDTH = 300;

    // Scroll
    private int scrollOffset = 0;
    private int maxScroll = 0;

    // Detail view state
    private Integer selectedUid = null;

    // Detail panel scroll (for profile data)
    private int detailScrollOffset = 0;
    private int detailMaxScroll = 0;

    // Refresh button bounds (set during render, used for click detection)
    private int refreshBtnX = 0, refreshBtnY = 0, refreshBtnW = 80, refreshBtnH = 16;
    private boolean refreshBtnVisible = false;

    // Colors (Gen 4 style - dark theme)
    private static final int BG_COLOR = 0xF0181818;
    private static final int HEADER_COLOR = 0xFF202020;
    private static final int ENTRY_BG_UNCAPTURED = 0xFF2A2A2A;
    private static final int ENTRY_BG_CAPTURED = 0xFF1A3A1A;
    private static final int ENTRY_BG_SELECTED = 0xFF2A4A2A;
    private static final int ENTRY_BORDER = 0xFF404040;
    private static final int TEXT_WHITE = 0xFFFFFFFF;
    private static final int TEXT_GRAY = 0xFF888888;
    private static final int TEXT_GREEN = 0xFF55FF55;
    private static final int TEXT_GOLD = 0xFFFFAA00;
    private static final int UID_COLOR = 0xFF55FFFF;
    private static final int DETAIL_BG = 0xFF1A1A1A;
    private static final int DETAIL_BORDER = 0xFF55FF55;

    // Texture caches
    private static final ExecutorService executor = Executors.newFixedThreadPool(2);
    private static final Map<Integer, ResourceLocation> screenshotTextures = new ConcurrentHashMap<>();
    private static final Map<Integer, int[]> screenshotSizes = new ConcurrentHashMap<>(); // [width, height]
    private static final Set<Integer> loadingScreenshots = ConcurrentHashMap.newKeySet();
    private static final AtomicLong CACHE_EPOCH = new AtomicLong(0);

    public PokedexScreen(Screen parent) {
        super(Component.literal("Hunchclient OG List"));
        this.parent = parent;
        this.pokedex = PokedexManager.getInstance();
    }

    @Override
    protected void init() {
        super.init();
        calculateMaxScroll();
    }

    private void calculateMaxScroll() {
        List<Integer> uids = pokedex.getAllUids();
        int rows = (int) Math.ceil((double) uids.size() / ENTRIES_PER_ROW);
        int contentHeight = rows * (ENTRY_HEIGHT + PADDING) + PADDING;
        int viewHeight = height - HEADER_HEIGHT - 20;
        maxScroll = Math.max(0, contentHeight - viewHeight);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        // Background
        context.fill(0, 0, width, height, BG_COLOR);

        // Header
        renderHeader(context);

        // Calculate content area (shrink if detail panel is open)
        int contentWidth = selectedUid != null ? width - DETAIL_PANEL_WIDTH - 10 : width;
        int contentY = HEADER_HEIGHT;
        int contentHeight = height - HEADER_HEIGHT - 10;

        // Content area with scissor
        context.enableScissor(0, contentY, contentWidth, contentY + contentHeight);
        renderEntries(context, mouseX, mouseY, contentY, contentWidth);
        context.disableScissor();

        // Scrollbar
        renderScrollbar(context, contentY, contentHeight, contentWidth);

        // Detail panel (if a user is selected)
        if (selectedUid != null && pokedex.isCaptured(selectedUid)) {
            renderDetailPanel(context, mouseX, mouseY);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderHeader(GuiGraphics context) {
        context.fill(0, 0, width, HEADER_HEIGHT, HEADER_COLOR);
        context.fill(0, HEADER_HEIGHT - 1, width, HEADER_HEIGHT, 0xFF303030);

        // Title
        String title = "§l§6Hunchclient OG List";
        int titleWidth = font.width(title.replace("§l", "").replace("§6", ""));
        context.drawString(font, title, (width - titleWidth) / 2, 10, TEXT_WHITE, true);

        // Stats
        int captured = pokedex.getCapturedCount();
        int total = pokedex.getTotalCount();
        float percentage = total > 0 ? (float) captured / total * 100 : 0;

        String stats = String.format("§a%d§7/§f%d §7captured (§e%.1f%%§7)", captured, total, percentage);
        int statsWidth = font.width(stats.replaceAll("§.", ""));
        context.drawString(font, stats, (width - statsWidth) / 2, 26, TEXT_WHITE, false);

        // Progress bar
        int barWidth = 200;
        int barX = (width - barWidth) / 2;
        int barY = 38;
        int barHeight = 6;

        context.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF404040);
        int fillWidth = (int) (barWidth * percentage / 100);
        if (fillWidth > 0) {
            context.fill(barX, barY, barX + fillWidth, barY + barHeight, 0xFF55FF55);
        }
        drawBorder(context, barX, barY, barWidth, barHeight, 0xFF606060);
    }

    private void renderEntries(GuiGraphics context, int mouseX, int mouseY, int contentY, int contentWidth) {
        List<Integer> uids = pokedex.getAllUids();

        if (uids.isEmpty()) {
            String msg = pokedex.isLoaded() ? "No OG users found" : "Loading...";
            int msgWidth = font.width(msg);
            context.drawString(font, msg, (contentWidth - msgWidth) / 2, contentY + 50, TEXT_GRAY, false);
            return;
        }

        int totalWidth = ENTRIES_PER_ROW * ENTRY_WIDTH + (ENTRIES_PER_ROW - 1) * PADDING;
        int startX = (contentWidth - totalWidth) / 2;
        int y = contentY + PADDING - scrollOffset;

        for (int i = 0; i < uids.size(); i++) {
            int uid = uids.get(i);
            int col = i % ENTRIES_PER_ROW;
            int row = i / ENTRIES_PER_ROW;

            int x = startX + col * (ENTRY_WIDTH + PADDING);
            int entryY = y + row * (ENTRY_HEIGHT + PADDING);

            if (entryY + ENTRY_HEIGHT < contentY || entryY > height) {
                continue;
            }

            renderEntry(context, x, entryY, uid, mouseX, mouseY);
        }
    }

    private void renderEntry(GuiGraphics context, int x, int y, int uid, int mouseX, int mouseY) {
        boolean captured = pokedex.isCaptured(uid);
        boolean selected = selectedUid != null && selectedUid == uid;
        boolean hovered = mouseX >= x && mouseX < x + ENTRY_WIDTH && mouseY >= y && mouseY < y + ENTRY_HEIGHT;

        // Background
        int bgColor = selected ? ENTRY_BG_SELECTED : (captured ? ENTRY_BG_CAPTURED : ENTRY_BG_UNCAPTURED);
        if (hovered && !selected) {
            bgColor = brighten(bgColor, 20);
        }
        context.fill(x, y, x + ENTRY_WIDTH, y + ENTRY_HEIGHT, bgColor);

        // Border
        int borderColor = selected ? TEXT_GOLD : (captured ? 0xFF55FF55 : ENTRY_BORDER);
        drawBorder(context, x, y, ENTRY_WIDTH, ENTRY_HEIGHT, borderColor);

        // UID number
        String uidText = "§b#" + uid;
        context.drawString(font, uidText, x + 6, y + (ENTRY_HEIGHT - 8) / 2, UID_COLOR, false);

        // Name or ???
        int uidWidth = font.width("#" + uid) + 10;
        int nameX = x + 6 + uidWidth;
        int nameY = y + (ENTRY_HEIGHT - 8) / 2;
        int maxNameWidth = ENTRY_WIDTH - uidWidth - 20;

        if (captured) {
            CapturedEntry entry = pokedex.getCapturedEntry(uid);
            String rawName = entry != null ? entry.name : "???";
            // Render gradient text
            renderGradientText(context, rawName, nameX, nameY, maxNameWidth, TEXT_GREEN);
        } else {
            context.drawString(font, "???", nameX, nameY, TEXT_GRAY, false);
        }

        // Captured indicator
        if (captured) {
            context.drawString(font, "★", x + ENTRY_WIDTH - 14, y + (ENTRY_HEIGHT - 8) / 2, TEXT_GOLD, false);
        }
    }

    /**
     * Render text with gradient support using CustomFontColorParser
     */
    private void renderGradientText(GuiGraphics context, String text, int x, int y, int maxWidth, int defaultColor) {
        // Check if text has gradient tags
        if (text.contains("<gradient:")) {
            List<ColoredTextSegment> segments = CustomFontColorParser.parse(text, defaultColor);
            int currentX = x;
            for (ColoredTextSegment segment : segments) {
                if (currentX - x > maxWidth) break;
                int color = segment.color | 0xFF000000; // Add alpha
                // Use drawString with proper width calculation
                String segText = segment.text;
                int segWidth = font.width(segText);
                if (segWidth == 0) segWidth = 6; // Fallback for single chars
                context.drawString(font, segText, currentX, y, color, false);
                currentX += segWidth;
            }
        } else {
            // No gradient, render normally (truncate if needed)
            String displayText = text;
            if (font.width(displayText) > maxWidth) {
                while (font.width(displayText + "...") > maxWidth && displayText.length() > 1) {
                    displayText = displayText.substring(0, displayText.length() - 1);
                }
                displayText += "...";
            }
            context.drawString(font, displayText, x, y, defaultColor, false);
        }
    }

    /**
     * Strip gradient tags and return clean text
     */
    private String stripGradientTags(String text) {
        return text.replaceAll("<gradient:[^>]+>", "").replaceAll("</gradient>", "");
    }

    private void renderDetailPanel(GuiGraphics context, int mouseX, int mouseY) {
        int panelX = width - DETAIL_PANEL_WIDTH - 5;
        int panelY = HEADER_HEIGHT + 5;
        int panelHeight = height - HEADER_HEIGHT - 15;

        // Panel background
        context.fill(panelX, panelY, panelX + DETAIL_PANEL_WIDTH, panelY + panelHeight, DETAIL_BG);
        drawBorder(context, panelX, panelY, DETAIL_PANEL_WIDTH, panelHeight, DETAIL_BORDER);

        CapturedEntry entry = pokedex.getCapturedEntry(selectedUid);
        if (entry == null) return;

        int textX = panelX + 15;
        int textY = panelY + 15;
        int contentWidth = DETAIL_PANEL_WIDTH - 30;

        // Close button
        String closeBtn = "§c✕";
        int closeBtnX = panelX + DETAIL_PANEL_WIDTH - 20;
        context.drawString(font, closeBtn, closeBtnX, textY, TEXT_WHITE, false);

        // Title with gradient
        context.drawString(font, "§l§6#" + selectedUid, textX, textY, TEXT_WHITE, true);
        textY += 12;
        renderGradientText(context, entry.name, textX, textY, contentWidth, TEXT_GREEN);
        textY += 15;

        // Divider
        context.fill(textX, textY, panelX + DETAIL_PANEL_WIDTH - 15, textY + 1, 0xFF404040);
        textY += 10;

        // Screenshot area (smaller to make room for profile data)
        int imageAreaHeight = 100;
        int imageAreaWidth = contentWidth;

        if (entry.screenshotPath != null) {
            ResourceLocation screenshotTex = getScreenshotTexture(selectedUid, entry.screenshotPath);
            int[] texSize = screenshotSizes.get(selectedUid);
            if (screenshotTex != null && texSize != null) {
                context.fill(textX, textY, textX + imageAreaWidth, textY + imageAreaHeight, 0xFF252525);
                context.blit(RenderPipelines.GUI_TEXTURED, screenshotTex, textX, textY, 0.0f, 0.0f, imageAreaWidth, imageAreaHeight, texSize[0], texSize[1], texSize[0], texSize[1]);
                drawBorder(context, textX, textY, imageAreaWidth, imageAreaHeight, 0xFF404040);
            } else {
                context.fill(textX, textY, textX + imageAreaWidth, textY + imageAreaHeight, 0xFF252525);
                String loadingText = "Loading...";
                int loadingWidth = font.width(loadingText);
                context.drawString(font, loadingText, textX + (imageAreaWidth - loadingWidth) / 2, textY + imageAreaHeight / 2 - 4, TEXT_GRAY, false);
                drawBorder(context, textX, textY, imageAreaWidth, imageAreaHeight, 0xFF404040);
            }
        } else {
            context.fill(textX, textY, textX + imageAreaWidth, textY + imageAreaHeight, 0xFF252525);
            String noScreenshot = "No screenshot";
            int noScreenshotWidth = font.width(noScreenshot);
            context.drawString(font, noScreenshot, textX + (imageAreaWidth - noScreenshotWidth) / 2, textY + imageAreaHeight / 2 - 4, TEXT_GRAY, false);
            drawBorder(context, textX, textY, imageAreaWidth, imageAreaHeight, 0xFF404040);
        }
        textY += imageAreaHeight + 10;

        // Capture Info section
        context.drawString(font, "§7Captured:", textX, textY, TEXT_WHITE, false);
        textY += 11;

        String dateTime = entry.date;
        if (entry.time != null) dateTime += " " + entry.time;
        if (entry.weekday != null) dateTime = entry.weekday.substring(0, 3) + " " + dateTime;
        context.drawString(font, "§f" + dateTime, textX + 8, textY, TEXT_WHITE, false);
        textY += 11;

        if (entry.location != null) {
            context.drawString(font, "§7Server: §f" + truncate(entry.location, 22), textX, textY, TEXT_WHITE, false);
            textY += 11;
        }

        if (entry.skyblockIsland != null) {
            context.drawString(font, "§7Island: §e" + entry.skyblockIsland, textX, textY, TEXT_WHITE, false);
            textY += 11;
        }

        // Profile Viewer Section (scrollable area)
        textY += 5;
        context.fill(textX, textY, panelX + DETAIL_PANEL_WIDTH - 15, textY + 1, 0xFF404040);
        textY += 8;

        // Check if we have profile data
        boolean hasProfileData = entry.sbLevel != null || entry.networth != null || entry.cataLevel != null;

        if (hasProfileData) {
            context.drawString(font, "§6§lProfile Viewer", textX, textY, TEXT_GOLD, true);
            textY += 14;

            // Scrollable content area
            int scrollAreaY = textY;
            int scrollAreaHeight = panelY + panelHeight - textY - 10;

            // Enable scissor for scrolling
            context.enableScissor(textX, scrollAreaY, panelX + DETAIL_PANEL_WIDTH - 15, scrollAreaY + scrollAreaHeight);

            int contentY = scrollAreaY - detailScrollOffset;

            // SB Level
            if (entry.sbLevel != null) {
                context.drawString(font, "§eSB Level: §f" + String.format("%.0f", entry.sbLevel), textX, contentY, TEXT_WHITE, false);
                contentY += 11;
            }

            // Networth
            if (entry.networth != null) {
                context.drawString(font, "§eNetworth: §6" + formatNetworth(entry.networth), textX, contentY, TEXT_WHITE, false);
                contentY += 11;
            }

            // Skill Average
            if (entry.skillAverage != null) {
                context.drawString(font, "§eSkill Avg: §a" + String.format("%.2f", entry.skillAverage), textX, contentY, TEXT_WHITE, false);
                contentY += 11;
            }

            // Catacombs Level
            if (entry.cataLevel != null) {
                context.drawString(font, "§eCata Level: §b" + String.format("%.2f", entry.cataLevel), textX, contentY, TEXT_WHITE, false);
                contentY += 14;
            }

            // Individual Skills
            if (entry.skillsData != null) {
                try {
                    JsonObject skills = JsonParser.parseString(entry.skillsData).getAsJsonObject();
                    if (skills.size() > 0) {
                        context.drawString(font, "§7Skills:", textX, contentY, TEXT_GRAY, false);
                        contentY += 11;

                        for (String skill : skills.keySet()) {
                            double lvl = skills.get(skill).getAsDouble();
                            String skillName = skill.substring(0, 1).toUpperCase() + skill.substring(1).toLowerCase();
                            int skillColor = getSkillColor(lvl);
                            context.drawString(font, "  §7" + skillName + ": ", textX, contentY, TEXT_GRAY, false);
                            context.drawString(font, String.format("%.0f", lvl), textX + 70, contentY, skillColor, false);
                            contentY += 10;
                        }
                        contentY += 4;
                    }
                } catch (Exception ignored) {}
            }

            // Dungeon PBs
            if (entry.dungeonPbs != null) {
                try {
                    JsonObject pbs = JsonParser.parseString(entry.dungeonPbs).getAsJsonObject();
                    if (pbs.size() > 0) {
                        context.drawString(font, "§7Dungeon PBs:", textX, contentY, TEXT_GRAY, false);
                        contentY += 11;

                        // Catacombs S+
                        if (pbs.has("catacombs_splus")) {
                            JsonObject cataSplus = pbs.getAsJsonObject("catacombs_splus");
                            contentY = renderDungeonPbs(context, textX, contentY, "Cata S+", cataSplus);
                        }

                        // Master S+
                        if (pbs.has("master_splus")) {
                            JsonObject masterSplus = pbs.getAsJsonObject("master_splus");
                            contentY = renderDungeonPbs(context, textX, contentY, "MM S+", masterSplus);
                        }
                    }
                } catch (Exception ignored) {}
            }

            // Calculate max scroll
            int totalContentHeight = contentY - scrollAreaY + detailScrollOffset;
            detailMaxScroll = Math.max(0, totalContentHeight - scrollAreaHeight);

            context.disableScissor();

            // Scrollbar for detail panel
            if (detailMaxScroll > 0) {
                int scrollbarX = panelX + DETAIL_PANEL_WIDTH - 10;
                int scrollbarHeight = scrollAreaHeight;
                context.fill(scrollbarX, scrollAreaY, scrollbarX + 4, scrollAreaY + scrollbarHeight, 0xFF303030);

                float scrollRatio = (float) detailScrollOffset / detailMaxScroll;
                int thumbHeight = Math.max(15, (int) ((float) scrollAreaHeight / (scrollAreaHeight + detailMaxScroll) * scrollbarHeight));
                int thumbY = scrollAreaY + (int) (scrollRatio * (scrollbarHeight - thumbHeight));
                context.fill(scrollbarX, thumbY, scrollbarX + 4, thumbY + thumbHeight, 0xFF606060);
            }
        } else {
            // No profile data - show loading or refresh button
            context.drawString(font, "§6§lProfile Viewer", textX, textY, TEXT_GOLD, true);
            textY += 14;
            context.drawString(font, "§7No profile data available", textX, textY, TEXT_GRAY, false);
            textY += 12;

            // Refresh button - store bounds for click detection
            refreshBtnX = textX;
            refreshBtnY = textY;
            refreshBtnW = 80;
            refreshBtnH = 16;
            refreshBtnVisible = true;
            boolean hovered = mouseX >= refreshBtnX && mouseX < refreshBtnX + refreshBtnW && mouseY >= refreshBtnY && mouseY < refreshBtnY + refreshBtnH;

            context.fill(refreshBtnX, refreshBtnY, refreshBtnX + refreshBtnW, refreshBtnY + refreshBtnH, hovered ? 0xFF3A5A3A : 0xFF2A4A2A);
            drawBorder(context, refreshBtnX, refreshBtnY, refreshBtnW, refreshBtnH, 0xFF55FF55);
            String btnText = "§aRefresh";
            int btnTextWidth = font.width(btnText.replace("§a", ""));
            context.drawString(font, btnText, refreshBtnX + (refreshBtnW - btnTextWidth) / 2, refreshBtnY + 4, TEXT_WHITE, false);
            textY += 22;

            context.drawString(font, "§8(Hypixel API via hysky.de)", textX, textY, 0xFF555555, false);
        }
    }

    /**
     * Render dungeon PBs for a category
     */
    private int renderDungeonPbs(GuiGraphics context, int textX, int y, String label, JsonObject floors) {
        context.drawString(font, "  §7" + label + ":", textX, y, TEXT_GRAY, false);
        y += 10;

        // Sort floors numerically: 0 (Entrance), 1, 2, 3, 4, 5, 6, 7
        java.util.List<String> sortedFloors = new java.util.ArrayList<>(floors.keySet());
        sortedFloors.sort((a, b) -> {
            try {
                return Integer.compare(Integer.parseInt(a), Integer.parseInt(b));
            } catch (NumberFormatException e) {
                return a.compareTo(b);
            }
        });

        for (String floor : sortedFloors) {
            long timeMs = floors.get(floor).getAsLong();
            String timeStr = formatDungeonTime(timeMs);
            String floorName = floor.equals("0") ? "E" : floor;
            context.drawString(font, "    §8F" + floorName + ": §f" + timeStr, textX, y, TEXT_WHITE, false);
            y += 9;
        }
        return y + 3;
    }

    /**
     * Format networth with K/M/B suffix
     */
    private String formatNetworth(double value) {
        if (value >= 1_000_000_000) {
            return String.format("%.2fB", value / 1_000_000_000);
        } else if (value >= 1_000_000) {
            return String.format("%.2fM", value / 1_000_000);
        } else if (value >= 1_000) {
            return String.format("%.1fK", value / 1_000);
        }
        return String.format("%.0f", value);
    }

    /**
     * Format dungeon time from ms to MM:SS.mmm
     */
    private String formatDungeonTime(long ms) {
        long seconds = ms / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        long millis = ms % 1000;
        return String.format("%d:%02d.%03d", minutes, seconds, millis);
    }

    /**
     * Get color based on skill level
     */
    private int getSkillColor(double level) {
        if (level >= 60) return 0xFFFFAA00; // Gold
        if (level >= 50) return 0xFF55FF55; // Green
        if (level >= 40) return 0xFF55FFFF; // Aqua
        if (level >= 25) return 0xFFFFFFFF; // White
        return 0xFFAAAAAA; // Gray
    }

    /**
     * Get or load screenshot texture from file
     */
    private ResourceLocation getScreenshotTexture(int uid, String path) {
        ResourceLocation cached = screenshotTextures.get(uid);
        if (cached != null) return cached;

        if (!loadingScreenshots.contains(uid)) {
            loadingScreenshots.add(uid);
            executor.submit(() -> loadScreenshotTexture(uid, path));
        }
        return null;
    }

    private void loadScreenshotTexture(int uid, String path) {
        long cacheEpoch = CACHE_EPOCH.get();
        try {
            File file = new File(mc.gameDirectory, path);
            if (!file.exists()) {
                loadingScreenshots.remove(uid);
                return;
            }

            BufferedImage img = ImageIO.read(file);
            if (img == null) {
                loadingScreenshots.remove(uid);
                return;
            }

            // Convert to PNG bytes
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "PNG", baos);
            byte[] pngBytes = baos.toByteArray();

            // Store size before registering
            final int imgWidth = img.getWidth();
            final int imgHeight = img.getHeight();

            // Register on render thread
            mc.execute(() -> {
                try {
                    if (CACHE_EPOCH.get() != cacheEpoch) {
                        return;
                    }
                    NativeImage nativeImage = NativeImage.read(new ByteArrayInputStream(pngBytes));
                    DynamicTexture texture = new DynamicTexture(() -> "PokedexScreenshot_" + uid, nativeImage);
                    ResourceLocation id = ResourceLocation.fromNamespaceAndPath("hunchclient", "pokedex/screenshot/" + uid);
                    mc.getTextureManager().register(id, texture);
                    screenshotTextures.put(uid, id);
                    screenshotSizes.put(uid, new int[]{imgWidth, imgHeight});
                } catch (Exception e) {
                    System.err.println("[Pokedex] Error registering screenshot texture: " + e.getMessage());
                } finally {
                    loadingScreenshots.remove(uid);
                }
            });
        } catch (Exception e) {
            System.err.println("[Pokedex] Error loading screenshot: " + e.getMessage());
            loadingScreenshots.remove(uid);
        }
    }

    private void renderScrollbar(GuiGraphics context, int contentY, int contentHeight, int contentWidth) {
        if (maxScroll <= 0) return;

        int scrollbarWidth = 6;
        int scrollbarX = contentWidth - scrollbarWidth - 4;
        int scrollbarHeight = contentHeight;

        context.fill(scrollbarX, contentY, scrollbarX + scrollbarWidth, contentY + scrollbarHeight, 0xFF303030);

        float scrollRatio = (float) scrollOffset / maxScroll;
        int thumbHeight = Math.max(20, (int) ((float) contentHeight / (contentHeight + maxScroll) * scrollbarHeight));
        int thumbY = contentY + (int) (scrollRatio * (scrollbarHeight - thumbHeight));

        context.fill(scrollbarX, thumbY, scrollbarX + scrollbarWidth, thumbY + thumbHeight, 0xFF606060);
    }

    private void drawBorder(GuiGraphics context, int x, int y, int w, int h, int color) {
        context.fill(x, y, x + w, y + 1, color);
        context.fill(x, y + h - 1, x + w, y + h, color);
        context.fill(x, y, x + 1, y + h, color);
        context.fill(x + w - 1, y, x + w, y + h, color);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        if (button == 0) {
            // Check close button on detail panel
            if (selectedUid != null) {
                int panelX = width - DETAIL_PANEL_WIDTH - 5;
                int closeBtnX = panelX + DETAIL_PANEL_WIDTH - 20;
                int closeBtnY = HEADER_HEIGHT + 20;

                if (mouseX >= closeBtnX - 5 && mouseX <= closeBtnX + 15 &&
                    mouseY >= closeBtnY - 5 && mouseY <= closeBtnY + 15) {
                    selectedUid = null;
                    return true;
                }

                // Check refresh button (uses bounds stored during render)
                if (refreshBtnVisible) {
                    if (mouseX >= refreshBtnX && mouseX < refreshBtnX + refreshBtnW &&
                        mouseY >= refreshBtnY && mouseY < refreshBtnY + refreshBtnH) {
                        CapturedEntry entry = pokedex.getCapturedEntry(selectedUid);
                        if (entry != null) {
                            System.out.println("[Pokedex] Refresh button clicked for #" + selectedUid + " " + entry.name);
                            pokedex.fetchProfileDataAsync(selectedUid, entry.name);
                            return true;
                        }
                    }
                }
            }

            // Check entry clicks
            int contentWidth = selectedUid != null ? width - DETAIL_PANEL_WIDTH - 10 : width;
            int contentY = HEADER_HEIGHT;

            List<Integer> uids = pokedex.getAllUids();
            int totalWidth = ENTRIES_PER_ROW * ENTRY_WIDTH + (ENTRIES_PER_ROW - 1) * PADDING;
            int startX = (contentWidth - totalWidth) / 2;
            int y = contentY + PADDING - scrollOffset;

            for (int i = 0; i < uids.size(); i++) {
                int uid = uids.get(i);
                int col = i % ENTRIES_PER_ROW;
                int row = i / ENTRIES_PER_ROW;

                int x = startX + col * (ENTRY_WIDTH + PADDING);
                int entryY = y + row * (ENTRY_HEIGHT + PADDING);

                if (mouseX >= x && mouseX < x + ENTRY_WIDTH &&
                    mouseY >= entryY && mouseY < entryY + ENTRY_HEIGHT) {

                    if (pokedex.isCaptured(uid)) {
                        // Toggle selection
                        if (selectedUid != null && selectedUid == uid) {
                            selectedUid = null;
                        } else {
                            selectedUid = uid;
                            detailScrollOffset = 0; // Reset scroll for new selection
                        }
                    }
                    return true;
                }
            }
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // Check if scrolling in detail panel
        if (selectedUid != null && mouseX >= width - DETAIL_PANEL_WIDTH - 5) {
            detailScrollOffset = Math.max(0, Math.min(detailMaxScroll, detailScrollOffset - (int) (verticalAmount * 15)));
            return true;
        }

        // Main list scroll
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) (verticalAmount * 20)));
        return true;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent input) {
        int keyCode = input.key();

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (selectedUid != null) {
                selectedUid = null;
                return true;
            }
            onClose();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
        clearCaches();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int brighten(int color, int amount) {
        int a = (color >> 24) & 0xFF;
        int r = Math.min(255, ((color >> 16) & 0xFF) + amount);
        int g = Math.min(255, ((color >> 8) & 0xFF) + amount);
        int b = Math.min(255, (color & 0xFF) + amount);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private String truncate(String text, int maxLen) {
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen - 3) + "...";
    }

    /**
     * Clear texture caches (call on close or world leave)
     */
    public static void clearCaches() {
        CACHE_EPOCH.incrementAndGet();
        for (ResourceLocation id : screenshotTextures.values()) {
            try {
                mc.getTextureManager().release(id);
            } catch (Exception ignored) {}
        }
        screenshotTextures.clear();
        screenshotSizes.clear();
        loadingScreenshots.clear();
    }

    public static void shutdownExecutor() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
