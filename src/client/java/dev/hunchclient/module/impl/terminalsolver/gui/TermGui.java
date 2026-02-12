package dev.hunchclient.module.impl.terminalsolver.gui;

import dev.hunchclient.module.impl.terminal.TermSimGUI;
import dev.hunchclient.module.impl.terminalsolver.TerminalHandler;
import dev.hunchclient.module.impl.terminalsolver.TerminalTypes;
import dev.hunchclient.render.Gradient;
import dev.hunchclient.render.NVGRenderer;
import dev.hunchclient.util.Color;
import dev.hunchclient.util.Colors;
import dev.hunchclient.util.HumanMouseEmulator;
import dev.hunchclient.util.MouseUtils;
import dev.hunchclient.util.animation.ColorAnimation;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;

/**
 * Base class for all custom terminal GUIs.
 * Provides common chrome, hover handling and the optional single-click layout.
 */
public abstract class TermGui {
    protected static final Minecraft mc = Minecraft.getInstance();
    private static final int LEFT_BUTTON = 0;
    private static final int RIGHT_BUTTON = 1;
    private static final long CLICK_COOLDOWN = 350L;

    private static TermGui currentGui = null;

    protected final Map<Integer, Box> itemIndexMap = new HashMap<>();
    protected final Map<Integer, ColorAnimation> colorAnimations = new HashMap<>();

    // Access to terminal solver settings (set by TerminalSolverModule)
    public static TerminalHandler currentTerm = null;
    public static float customTermSize = 1.0f;
    public static float gap = 5.0f;
    public static float roundness = 0.0f;
    public static Color backgroundColor = new Color(26, 26, 26);
    public static boolean hideClicked = false;
    public static boolean customAnimations = true;
    public static boolean compactClickMode = true;
    public static boolean showQueuePreview = true;
    public static int queuePreviewLimit = 6;
    public static boolean useCustomFont = false;

    protected LayoutMetrics layout = LayoutMetrics.EMPTY;
    protected Color currentAccent = Colors.MINECRAFT_AQUA;
    private boolean compactActive = true;
    protected net.minecraft.client.gui.GuiGraphics currentDrawContext = null;

    protected List<Integer> getCurrentSolution() {
        try {
            if (currentTerm != null && currentTerm.solution != null) {
                // Return a defensive copy to avoid concurrent modification issues
                return List.copyOf(currentTerm.solution);
            }
        } catch (Exception e) {
            // If copy fails, return empty list
        }
        return List.of();
    }

    /**
     * Called when the solution updates to force GUI refresh
     * Clears color animations so they restart even if solution values are the same
     */
    public void onSolutionUpdate() {
        colorAnimations.clear();
    }

    /**
     * Get the font to use for terminal GUI
     * Returns custom font if enabled, otherwise default NVG font
     */
    protected static dev.hunchclient.render.Font getTerminalFont() {
        if (useCustomFont) {
            dev.hunchclient.module.impl.CustomFontModule customFont =
                dev.hunchclient.module.impl.CustomFontModule.getInstance();
            if (customFont != null && customFont.isEnabled()) {
                return customFont.getSelectedFont();
            }
        }
        return NVGRenderer.defaultFont;
    }

    public abstract void renderTerminal(int slotCount);

    protected LayoutMetrics renderBackground(int slotCount, int slotWidth) {
        float slotSize = 55f * customTermSize;
        float totalSlotSpace = slotSize + gap * customTermSize;

        float backgroundStartX = mc.getWindow().getScreenWidth() / 2f - (slotWidth / 2f) * totalSlotSpace - 7.5f * customTermSize;
        float backgroundStartY = mc.getWindow().getScreenHeight() / 2f + ((-getRowOffset(slotCount) + 0.5f) * totalSlotSpace) - 7.5f * customTermSize;
        float backgroundWidth = slotWidth * totalSlotSpace + 15f * customTermSize;
        float baseHeight = ((slotCount) / 9) * totalSlotSpace + 15f * customTermSize;
        float backgroundHeight = Math.max(baseHeight, slotSize + 12f * customTermSize);

        // Minimal padding - clean, tight design
        float sidePadding = 12f * customTermSize;
        float footerPadding = 12f * customTermSize;

        // No header - content is the entire panel
        float panelX = backgroundStartX - sidePadding;
        float panelY = backgroundStartY - sidePadding;
        float panelWidth = backgroundWidth + sidePadding * 2f;
        float panelHeight = backgroundHeight + sidePadding * 2f;

        currentAccent = getAccentColor();

        // Clean dark panel with subtle gradient
        Color panelBase = backgroundColor.withAlpha(0.95f);
        Color panelDark = backgroundColor.darker(0.85f).withAlpha(0.98f);
        Color accentGlow = currentAccent.withAlpha(0.15f);
        Color borderColor = currentAccent.withAlpha(0.35f);

        // Panel radius - respects user setting (0 = perfect squares)
        float panelRadius = roundness * customTermSize;

        // Soft shadow for depth
        NVGRenderer.dropShadow(panelX, panelY, panelWidth, panelHeight,
            20f * customTermSize, 10f * customTermSize, panelRadius);

        // Main panel - clean solid background
        NVGRenderer.gradientRect(panelX, panelY, panelWidth, panelHeight,
            panelBase.getRgba(), panelDark.getRgba(), Gradient.TopToBottom, panelRadius);

        // Subtle inner glow at top for depth
        NVGRenderer.gradientRect(panelX + 2f, panelY + 2f, panelWidth - 4f, 8f * customTermSize,
            accentGlow.getRgba(), Colors.TRANSPARENT.getRgba(), Gradient.TopToBottom, Math.max(0, panelRadius - 2f));

        // Clean accent border
        NVGRenderer.hollowRect(panelX, panelY, panelWidth, panelHeight,
            Math.max(1.5f * customTermSize, 1.2f), borderColor.getRgba(), panelRadius);

        // Content area is the same as panel (no separate region)
        layout = new LayoutMetrics(panelX, panelY, panelWidth, panelHeight, 0f,
            panelX + sidePadding, panelY + sidePadding, backgroundWidth, backgroundHeight, sidePadding, footerPadding);

        return layout;
    }

    protected float[] renderSlot(int index, Color startColor, Color endColor) {
        float slotSize = 55f * customTermSize;
        float totalSlotSpace = slotSize + gap * customTermSize;

        float x = (index % 9 - 4) * totalSlotSpace + mc.getWindow().getScreenWidth() / 2f - slotSize / 2;
        float y = (index / 9 - 2) * totalSlotSpace + mc.getWindow().getScreenHeight() / 2f - slotSize / 2;

        itemIndexMap.put(index, new Box(x, y, slotSize, slotSize));

        ColorAnimation colorAnim = colorAnimations.computeIfAbsent(index, k -> new ColorAnimation(200));
        Color color = colorAnim.get(startColor, endColor, true);

        // Slot radius - respects user setting (0 = perfect squares)
        float slotRadius = roundness * customTermSize;

        // Only draw shadow if there's gap or roundness (otherwise it overlaps neighboring slots)
        if (gap > 0 || roundness > 0) {
            NVGRenderer.dropShadow(x, y, slotSize, slotSize,
                6f * customTermSize, 3f * customTermSize, slotRadius);
        }

        // Main slot fill
        NVGRenderer.rect(x, y, slotSize, slotSize, color.getRgba(), slotRadius);

        // Only draw highlight if there's roundness (looks better on flat squares without it)
        if (roundness > 0) {
            Color highlight = color.brighter(1.2f).withAlpha(0.25f);
            NVGRenderer.gradientRect(x + 1f, y + 1f, slotSize - 2f, slotSize * 0.3f,
                highlight.getRgba(), Colors.TRANSPARENT.getRgba(), Gradient.TopToBottom, Math.max(0, slotRadius - 1f));
        }

        return new float[]{x, y};
    }

    public Integer mouseClicked(Screen screen, net.minecraft.client.input.MouseButtonEvent click) {
        int button = click.button();
        int mouseX = (int) click.x();
        int mouseY = (int) click.y();

        TerminalHandler term = currentTerm;

        // CRITICAL FIX for compact click mode:
        // In compact mode, ALWAYS use the CURRENT first solution slot instead of itemIndexMap
        // This prevents race conditions where itemIndexMap contains a stale slot from previous frame
        Integer slot;
        if (compactActive && term != null && !term.solution.isEmpty()) {
            // Get the CURRENT first slot from solution (not from itemIndexMap!)
            Integer currentNext = term.solution.get(0);

            // Validate that user clicked somewhere in the button area
            Box buttonBox = itemIndexMap.get(currentNext);
            if (buttonBox != null &&
                mouseX >= buttonBox.x && mouseX <= buttonBox.x + buttonBox.w &&
                mouseY >= buttonBox.y && mouseY <= buttonBox.y + buttonBox.h) {
                slot = currentNext;
            } else {
                slot = null;  // User didn't click in button area
            }
        } else {
            // Normal mode: use hover detection
            slot = getHoveredItem(mouseX, mouseY);
        }

        if (slot != null && term != null) {
            // Require a short delay after the terminal opens to avoid early misclicks
            long elapsed = System.currentTimeMillis() - term.timeOpened;
            int resolvedButton = resolveButton(term, slot, button);

            if (elapsed >= CLICK_COOLDOWN && term.canClick(slot, resolvedButton)) {
                // IMPORTANT: Always call simulateClick for terminals that need click tracking!
                // StartsWith and SelectAll MUST call simulateClick() on EVERY click to update ItemState
                boolean needsTracking = term.type == TerminalTypes.STARTS_WITH ||
                                       term.type == TerminalTypes.SELECT;
                // shouldSimulateClick is true if:
                // 1. Terminal needs tracking (StartsWith/SelectAll) - ALWAYS
                // 2. OR hideClicked is enabled and this is the first click
                boolean shouldSimulateClick = needsTracking || (hideClicked && !term.isClicked);
                boolean handled = false;

                try {
                    if (screen instanceof TermSimGUI sim) {
                        handled = sim.handleCustomClick(slot, resolvedButton);
                        if (handled) {
                            term.isClicked = true;
                            // NOTE: TermSimGUI already calls simulateClick() BEFORE the refresh!
                            // No need to call it again here - that would be a duplicate
                        }
                    } else {
                        // HIGH PING MODE: Queue clicks if already waiting or queue has items
                        // RULE: Once we start queuing, ALL subsequent clicks must queue
                        // This prevents race conditions where a fast click bypasses the queue
                        // and arrives at server before queued clicks (breaks Numbers terminal order)
                        if (term.shouldQueue()) {
                            // CRITICAL: Predict FIRST (like NoamAddons line 79 + SA line 44)
                            // This is for local GUI rendering - won't break validation because
                            // solve() filters by hasGlint() which is set by SERVER, not by predict()
                            term.simulateClick(slot, resolvedButton);
                            // Then queue for server processing
                            term.queueClick(slot, resolvedButton);
                            System.out.println("[GUI->QUEUE] Queued slot=" + slot + ", queue size=" + term.getClickQueue().size());
                            handled = true;
                        } else {
                            // Normal mode or first click: send directly
                            // CRITICAL FIX: For non-simulator screens, ALWAYS call simulateClick for tracking terminals
                            // The old logic only called it on the first click (when !isClicked), which broke ItemState tracking
                            term.click(slot, resolvedButton, shouldSimulateClick);
                            handled = true;
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if (handled && customAnimations) {
                    ColorAnimation anim = colorAnimations.get(slot);
                    if (anim != null) anim.start();
                }

                if (handled) {
                    return slot;
                }
            }
        }
        return null;
    }

    public void closeGui() {
        colorAnimations.clear();
    }

    /**
     * Called from within NVGRenderer frame to render shapes/backgrounds
     */
    public void render(net.minecraft.client.gui.GuiGraphics context) {
        try {
            setCurrentGui(this);
            itemIndexMap.clear();
            layout = LayoutMetrics.EMPTY;
            this.currentDrawContext = context; // Store for text rendering AFTER NVG frame

            // Render mouse trail FIRST (behind everything else)
            renderMouseTrail();

            int slotCount = determineSlotCount();
            TerminalHandler term = currentTerm;

            // Safety check: don't render if terminal is invalid
            if (term == null || term.type == null) {
                return;
            }

            compactActive = compactClickMode && supportsCompactMode() && term != null;

            // Render shapes first (within NVG frame)
            if (compactActive) {
                renderCompactTerminal(slotCount);
            } else {
                renderTerminal(slotCount);
            }

            if (!compactActive) {
                renderNextIndicator();
            }
            renderHoverIndicator();
        } catch (Exception e) {
            // Don't crash if rendering fails - just skip this frame
            System.err.println("[TermGui] Render error: " + e.getMessage());
        }
    }

    /**
     * Called AFTER NVGRenderer.endFrame() to render text with Minecraft's DrawContext
     * IMPORTANT: Coordinates must be converted from window space to GUI-scaled space!
     */
    public void renderText() {
        if (mc == null || mc.font == null || currentDrawContext == null) return;
        if (layout == null || layout == LayoutMetrics.EMPTY) return;

        try {
            // Get scale factor to convert window coordinates to GUI coordinates
            double guiScale = mc.getWindow().getGuiScale();

            // Render text based on current mode (compact mode only)
            if (compactActive) {
                List<Integer> queue = getCurrentSolution();
                if (!queue.isEmpty()) {
                    renderCompactButtonText(queue, guiScale);
                    if (showQueuePreview && queue.size() > 1) {
                        float buttonHeight = Math.max(54f * customTermSize, 50f * customTermSize);
                        float padding = 14f * customTermSize;
                        float buttonBottom = layout.contentY + padding + buttonHeight;
                        renderCompactQueueText(queue, buttonBottom + 10f * customTermSize, guiScale);
                    }
                }
            }
            // No header text - clean minimal design
        } catch (Exception e) {
            System.err.println("[TermGui] Text render error: " + e.getMessage());
        }
    }

    protected int getDefaultSlotWidth() {
        return 7;
    }

    protected int determineSlotCount() {
        TerminalHandler term = currentTerm;
        return term != null && term.type != null
            ? Math.max(0, term.type.windowSize - 10)
            : 0;
    }

    protected boolean supportsCompactMode() {
        return true;
    }

    protected void renderCompactTerminal(int slotCount) {
        LayoutMetrics metrics = renderBackground(slotCount, getDefaultSlotWidth());

        TerminalHandler term = currentTerm;
        if (term == null) {
            renderEmptyState(metrics, "Waiting for terminal...");
            return;
        }

        List<Integer> queue = getCurrentSolution();
        if (queue.isEmpty()) {
            renderEmptyState(metrics, "Terminal solved");
            return;
        }

        float buttonBottom = renderCompactButton(metrics, queue);
        if (showQueuePreview) {
            renderCompactQueue(metrics, queue, buttonBottom + 14f * customTermSize);
        }
    }

    /**
     * Render the mouse movement trail from HumanMouseEmulator.
     * Ultra-thin fading lines showing the WindMouse path.
     */
    private void renderMouseTrail() {
        HumanMouseEmulator emulator = HumanMouseEmulator.get();
        if (!emulator.isTrailEnabled()) return;

        List<HumanMouseEmulator.TrailPoint> points = emulator.getTrailPoints();
        if (points.size() < 2) return;

        // Trail colors - cyan/aqua gradient that matches the terminal accent
        Color trailColor = currentAccent.brighter(1.3f);

        // Draw connected line segments with fading opacity
        for (int i = 1; i < points.size(); i++) {
            HumanMouseEmulator.TrailPoint prev = points.get(i - 1);
            HumanMouseEmulator.TrailPoint curr = points.get(i);

            // Get opacity based on age (older = more transparent)
            float opacity = curr.getOpacity();
            if (opacity <= 0.01f) continue;

            // Fade based on position in trail (newer points = brighter)
            float positionFade = (float) i / points.size();
            float finalOpacity = opacity * positionFade * 0.85f;

            if (finalOpacity <= 0.01f) continue;

            // Ultra-thin line (1.0 - 1.5 pixels)
            float lineWidth = 1.0f + (positionFade * 0.5f);

            Color lineColor = trailColor.withAlpha(finalOpacity);

            // Draw line segment using NVGRenderer
            NVGRenderer.line(
                (float) prev.x, (float) prev.y,
                (float) curr.x, (float) curr.y,
                lineWidth, lineColor.getRgba()
            );
        }

        // Draw a small glowing dot at the newest point (current position)
        if (!points.isEmpty()) {
            HumanMouseEmulator.TrailPoint newest = points.get(points.size() - 1);
            float newestOpacity = newest.getOpacity();
            if (newestOpacity > 0.1f) {
                // Small glowing circle at cursor tip
                Color dotColor = trailColor.brighter(1.2f).withAlpha(newestOpacity * 0.9f);
                Color glowColor = trailColor.withAlpha(newestOpacity * 0.3f);

                // Outer glow
                NVGRenderer.circle((float) newest.x, (float) newest.y, 6f, glowColor.getRgba());
                // Inner dot
                NVGRenderer.circle((float) newest.x, (float) newest.y, 2.5f, dotColor.getRgba());
            }
        }
    }

    private void renderHoverIndicator() {
        Integer hovered = getHoveredItem();
        if (hovered == null) return;
        Box box = itemIndexMap.get(hovered);
        if (box == null) return;

        // Subtle pulse animation
        float hoverTime = System.currentTimeMillis() % 1500 / 1500f;
        float hoverPulse = (float)(Math.sin(hoverTime * Math.PI * 2) * 0.15f + 0.85f);

        float slotRadius = roundness * customTermSize;
        Color outline = currentAccent.brighter(1.1f).withAlpha(0.6f * hoverPulse);

        // Single clean border - no excessive glow
        NVGRenderer.hollowRect(box.x - 2f, box.y - 2f,
            box.w + 4f, box.h + 4f,
            Math.max(2f * customTermSize, 1.5f), outline.getRgba(), slotRadius + 2f);
    }

    private void renderNextIndicator() {
        // Use getCurrentSolution() to avoid race conditions
        List<Integer> solution = getCurrentSolution();
        if (solution.isEmpty()) return;

        for (int slot : solution) {
            Box box = itemIndexMap.get(slot);
            if (box != null) {
                float slotRadius = roundness * customTermSize;
                Color outline = currentAccent.withAlpha(0.7f);

                // Clean border for next slot
                NVGRenderer.hollowRect(box.x - 2f, box.y - 2f,
                    box.w + 4f, box.h + 4f,
                    Math.max(2f * customTermSize, 1.5f), outline.getRgba(), slotRadius + 2f);
                break;
            }
        }
    }

    private float renderCompactButton(LayoutMetrics metrics, List<Integer> queue) {
        if (queue == null || queue.isEmpty()) return metrics.contentY;

        try {
            int nextSlot = queue.get(0);
            ClickInfo clickInfo = computeClickInfo(nextSlot);

            // Tighter padding for minimal design
            float padding = 14f * customTermSize;
            float buttonWidth = Math.max(metrics.contentWidth - padding * 2f, 180f * customTermSize);
            float buttonX = metrics.contentX + (metrics.contentWidth - buttonWidth) / 2f;
            float buttonY = metrics.contentY + padding;
            float buttonHeight = Math.max(54f * customTermSize, 50f * customTermSize);

            // Subtle pulse animation
            float pulseTime = System.currentTimeMillis() % 2500 / 2500f;
            float pulse = (float)(Math.sin(pulseTime * Math.PI * 2) * 0.08f + 0.92f);

            // COLOR BASED ON CLICK TYPE: RED = Right Click, GREEN = Left Click
            Color buttonColor;
            if (clickInfo.preferredButton == RIGHT_BUTTON) {
                buttonColor = new Color(200, 60, 60); // Softer red
            } else {
                buttonColor = new Color(60, 180, 80); // Softer green
            }

            Color top = buttonColor.brighter(1.15f * pulse).withAlpha(0.95f);
            Color bottom = buttonColor.darker(0.80f).withAlpha(0.98f);
            Color borderColor = buttonColor.brighter(1.3f).withAlpha(0.5f * pulse);

            float radius = roundness * customTermSize;

            // Subtle shadow
            NVGRenderer.dropShadow(buttonX, buttonY, buttonWidth, buttonHeight,
                12f * customTermSize * pulse, 6f * customTermSize, radius);

            // Main button gradient
            NVGRenderer.gradientRect(buttonX, buttonY, buttonWidth, buttonHeight,
                top.getRgba(), bottom.getRgba(), Gradient.TopToBottom, radius);

            // Subtle top highlight
            NVGRenderer.gradientRect(buttonX + 1f, buttonY + 1f, buttonWidth - 2f, buttonHeight * 0.3f,
                buttonColor.brighter(1.4f).withAlpha(0.2f).getRgba(),
                Colors.TRANSPARENT.getRgba(),
                Gradient.TopToBottom, radius - 1f);

            // Clean border
            NVGRenderer.hollowRect(buttonX, buttonY, buttonWidth, buttonHeight,
                Math.max(1.5f * customTermSize, 1.2f), borderColor.getRgba(), radius);

            itemIndexMap.put(nextSlot, new Box(buttonX, buttonY, buttonWidth, buttonHeight));

            return buttonY + buttonHeight;
        } catch (Exception e) {
            System.err.println("[TermGui] CompactButton render error: " + e.getMessage());
            return metrics.contentY;
        }
    }

    private void renderCompactButtonText(List<Integer> queue, double guiScale) {
        if (queue == null || queue.isEmpty()) return;
        if (mc == null || mc.font == null || currentDrawContext == null) return;

        try {
            int nextSlot = queue.get(0);
            String itemName = truncate(describeSlot(nextSlot), 26);
            if (itemName == null) itemName = "Unknown";
            ClickInfo clickInfo = computeClickInfo(nextSlot);

            float padding = 14f * customTermSize;
            float buttonWidth = Math.max(layout.contentWidth - padding * 2f, 180f * customTermSize);
            float buttonX = layout.contentX + (layout.contentWidth - buttonWidth) / 2f;
            float buttonY = layout.contentY + padding;
            float buttonHeight = Math.max(54f * customTermSize, 50f * customTermSize);

            // Convert from window coordinates to GUI-scaled coordinates
            int guiButtonX = (int)(buttonX / guiScale);
            int guiButtonY = (int)(buttonY / guiScale);
            int guiButtonWidth = (int)(buttonWidth / guiScale);
            int guiButtonHeight = (int)(buttonHeight / guiScale);

            int whiteColor = Colors.WHITE.getRgba();

            // Center text vertically in button
            int textY = guiButtonY + (guiButtonHeight - mc.font.lineHeight) / 2;

            // Action text (e.g., "Left click" or "Right click x3")
            String action = switch (clickInfo.preferredButton) {
                case RIGHT_BUTTON -> "R-Click";
                case LEFT_BUTTON -> "L-Click";
                default -> "Click";
            };
            if (clickInfo.remainingClicks > 1) {
                action += " x" + clickInfo.remainingClicks;
            }

            // Item name + action on single line, centered
            String fullText = itemName + " - " + action;
            int maxWidth = guiButtonWidth - 24;
            while (mc.font.width(fullText) > maxWidth && fullText.length() > 3) {
                fullText = fullText.substring(0, fullText.length() - 4) + "...";
            }

            int textWidth = mc.font.width(fullText);
            int textX = guiButtonX + (guiButtonWidth - textWidth) / 2;

            currentDrawContext.drawString(mc.font, fullText, textX, textY, whiteColor, true);

            // Pending count in corner if more items
            if (queue.size() > 1) {
                String upcoming = "+" + (queue.size() - 1);
                int upcomingWidth = mc.font.width(upcoming);
                currentDrawContext.drawString(mc.font, upcoming,
                    guiButtonX + guiButtonWidth - upcomingWidth - 8,
                    guiButtonY + 4,
                    Colors.WHITE.withAlpha(0.6f).getRgba(), false);
            }
        } catch (Exception e) {
            System.err.println("[TermGui] CompactButton text render error: " + e.getMessage());
        }
    }

    private void renderCompactQueue(LayoutMetrics metrics, List<Integer> queue, float startY) {
        // Empty - text is now rendered in renderCompactQueueText()
    }

    private void renderCompactQueueText(List<Integer> queue, float startY, double guiScale) {
        if (queue == null || queue.size() <= 1) return;
        if (mc == null || mc.font == null || currentDrawContext == null) return;

        float padding = 18f * customTermSize;
        int x = (int)((layout.contentX + padding) / guiScale);
        float lineHeight = 18f * customTermSize / (float)guiScale;
        int guiStartY = (int)(startY / guiScale);

        int preview = Math.min(queuePreviewLimit, queue.size() - 1);
        for (int offset = 1; offset <= preview; offset++) {
            try {
                int slot = queue.get(offset);
                String label = (offset + 1) + ". " + truncate(describeSlot(slot), 30);
                if (label == null) label = "Unknown";

                Color color = offset == 1
                    ? currentAccent.withAlpha(0.8f)
                    : Colors.WHITE.withAlpha(0.7f);

                currentDrawContext.drawString(mc.font, label, x,
                    (int)(guiStartY + (offset - 1) * lineHeight), color.getRgba(), false);
            } catch (Exception e) {
                // Skip this entry if there's an error
                continue;
            }
        }

        try {
            if (queue.size() - 1 > preview) {
                String more = "+" + (queue.size() - 1 - preview) + " more";
                currentDrawContext.drawString(mc.font, more, x,
                    (int)(guiStartY + preview * lineHeight),
                    Colors.WHITE.withAlpha(0.55f).getRgba(), false);
            }
        } catch (Exception e) {
            // Ignore errors in rendering the "more" text
        }
    }

    private void renderEmptyState(LayoutMetrics metrics, String message) {
        // Empty - text is now rendered in renderEmptyStateText()
    }

    private void renderHeader(LayoutMetrics metrics) {
        // Empty - text is now rendered in renderHeaderText()
    }

    private void renderHeaderText(double guiScale) {
        if (mc == null || mc.font == null || currentDrawContext == null) return;
        if (layout == null || layout == LayoutMetrics.EMPTY) return;

        try {
            String title = getHeaderTitle();
            String subtitle = getHeaderSubtitle();
            String queueInfo = getQueueSummary();

            // Convert window coordinates to GUI-scaled coordinates
            float titleY = layout.panelY + 12f * customTermSize;
            float subtitleY = titleY + mc.font.lineHeight * (float)guiScale + 4f * customTermSize;

            int guiTitleY = (int)(titleY / guiScale);
            int guiSubtitleY = (int)(subtitleY / guiScale);

            // Title (centered)
            int titleWidth = mc.font.width(title);
            int titleX = (int)((layout.panelX + (layout.panelWidth - titleWidth * guiScale) / 2f) / guiScale);
            currentDrawContext.drawString(mc.font, title, titleX, guiTitleY, Colors.WHITE.getRgba(), true);

            // Subtitle (centered)
            if (subtitle != null && !subtitle.isEmpty()) {
                int subtitleWidth = mc.font.width(subtitle);
                int subtitleX = (int)((layout.panelX + (layout.panelWidth - subtitleWidth * guiScale) / 2f) / guiScale);
                currentDrawContext.drawString(mc.font, subtitle, subtitleX, guiSubtitleY, Colors.WHITE.withAlpha(0.7f).getRgba(), false);
            }

            // Queue info (right side)
            if (queueInfo != null && !queueInfo.isEmpty()) {
                int queueWidth = mc.font.width(queueInfo);
                int queueX = (int)((layout.panelX + layout.panelWidth - queueWidth * guiScale - 16f * customTermSize) / guiScale);
                currentDrawContext.drawString(mc.font, queueInfo, queueX, guiTitleY, currentAccent.getRgba(), false);
            }
        } catch (Exception e) {
            System.err.println("[TermGui] Header text render error: " + e.getMessage());
        }
    }

    // ===== TEXT RENDERING RE-ENABLED =====
    // Text is now rendered after NVG frame using Minecraft's DrawContext

    protected Color getAccentColor() {
        return Colors.MINECRAFT_AQUA;
    }

    protected String getHeaderTitle() {
        TerminalHandler term = currentTerm;
        if (term == null || term.type == null) {
            return "Terminal Solver";
        }
        return formatEnumName(term.type.name()) + " Terminal";
    }

    protected String getHeaderSubtitle() {
        TerminalHandler term = currentTerm;
        if (term == null || term.type == null) {
            return "Waiting for terminal";
        }
        String base = stripFormatting(term.type.windowName);
        if (compactActive) {
            return base + " - Single-click mode";
        }
        return base;
    }

    protected String getQueueSummary() {
        TerminalHandler term = currentTerm;
        if (term == null) return "";
        // Use getCurrentSolution() to avoid race conditions
        int remaining = getCurrentSolution().size();
        if (remaining <= 0) {
            return "Ready";
        }
        return "Queue: " + remaining;
    }

    protected String describeSlot(int slotIndex) {
        try {
            TerminalHandler term = currentTerm;
            if (term != null && term.items != null && slotIndex >= 0 && slotIndex < term.items.length) {
                ItemStack stack = term.items[slotIndex];
                if (stack != null && !stack.isEmpty()) {
                    String name = stripFormatting(stack.getHoverName().getString());
                    return name != null ? name : "Slot " + (slotIndex + 1);
                }
            }
            return "Slot " + (slotIndex + 1);
        } catch (Exception e) {
            return "Slot " + (slotIndex + 1);
        }
    }

    protected ClickInfo computeClickInfo(int slotIndex) {
        ClickInfo info = new ClickInfo();
        TerminalHandler term = currentTerm;
        if (term == null || term.type == null) {
            return info;
        }

        // CRITICAL FIX: Use getCurrentSolution() to get a defensive copy
        // This prevents concurrent modification issues when solution is updated during iteration
        List<Integer> currentSolution = getCurrentSolution();

        int remaining = 0;
        for (int slot : currentSolution) {
            if (slot == slotIndex) remaining++;
        }

        info.remainingClicks = remaining;
        if (term.type == TerminalTypes.RUBIX) {
            // Rubix terminal logic:
            // - When needed < 3: use LEFT_BUTTON (removes 1 at a time)
            // - When needed >= 3: use RIGHT_BUTTON (removes 2 at once, more efficient)
            if (remaining >= 3) {
                info.preferredButton = RIGHT_BUTTON;
            } else {
                info.preferredButton = LEFT_BUTTON;
            }
        } else {
            info.preferredButton = LEFT_BUTTON;
        }
        return info;
    }

    private int resolveButton(TerminalHandler term, int slot, int requestedButton) {
        if (!compactActive) return requestedButton;

        // In compact mode, determine the correct button automatically
        ClickInfo info = computeClickInfo(slot);
        int optimalButton = info.preferredButton;

        // Try the optimal button first
        if (term.canClick(slot, optimalButton)) {
            return optimalButton;
        }

        // Fall back to the requested button if different
        if (requestedButton != optimalButton && term.canClick(slot, requestedButton)) {
            return requestedButton;
        }

        // Try the alternative button
        int alternative = (optimalButton == LEFT_BUTTON) ? RIGHT_BUTTON : LEFT_BUTTON;
        if (term.canClick(slot, alternative)) {
            return alternative;
        }

        // Default to the optimal button
        return optimalButton;
    }

    private String stripFormatting(String text) {
        if (text == null) return "";
        StringBuilder builder = new StringBuilder(text.length());
        boolean skip = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (skip) {
                skip = false;
                continue;
            }
            if (c == '§') {
                skip = true;
                continue;
            }
            builder.append(c);
        }
        return builder.toString();
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) return text;
        return text.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private String fitText(String text, float fontSize, float maxWidth) {
        if (text == null) return "";
        String current = text;
        while (!current.isEmpty() && NVGRenderer.textWidth(current, fontSize, getTerminalFont()) > maxWidth) {
            current = truncate(current, current.length() - 1);
        }
        return current;
    }

    private static String formatEnumName(String name) {
        String lower = name.toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder builder = new StringBuilder(lower.length());
        boolean capitalize = true;
        for (char c : lower.toCharArray()) {
            if (capitalize && Character.isLetter(c)) {
                builder.append(Character.toUpperCase(c));
                capitalize = false;
            } else {
                builder.append(c);
            }
            if (c == ' ') {
                capitalize = true;
            }
        }
        return builder.toString();
    }

    private float getRowOffset(int slotCount) {
        if (slotCount >= 0 && slotCount <= 9) return 0f;
        if (slotCount >= 10 && slotCount <= 18) return 1f;
        if (slotCount >= 19 && slotCount <= 27) return 2f;
        if (slotCount >= 28 && slotCount <= 36) return 2f;
        if (slotCount >= 37 && slotCount <= 45) return 2f;
        return 3f;
    }

    public static void setCurrentGui(TermGui gui) {
        currentGui = gui;
    }

    public static Integer getHoveredItem(int mouseX, int mouseY) {
        if (currentGui == null) return null;

        for (Map.Entry<Integer, Box> entry : currentGui.itemIndexMap.entrySet()) {
            Box box = entry.getValue();
            if (mouseX >= box.x && mouseX <= (box.x + box.w) && mouseY >= box.y && mouseY <= (box.y + box.h)) {
                return entry.getKey();
            }
        }
        return null;
    }

    @Deprecated
    public static Integer getHoveredItem() {
        // Legacy method for backwards compatibility - uses MouseUtils (may be incorrect due to scaling)
        if (currentGui == null) return null;
        for (Map.Entry<Integer, Box> entry : currentGui.itemIndexMap.entrySet()) {
            Box box = entry.getValue();
            if (MouseUtils.isAreaHovered(box.x, box.y, box.w, box.h)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Get the Box (position and size) for a specific slot index.
     * Used by AutoClick to get exact screen coordinates.
     * @param slotIndex The slot index
     * @return Box with x, y, w, h or null if not found
     */
    public static Box getSlotBox(int slotIndex) {
        if (currentGui == null) return null;
        return currentGui.itemIndexMap.get(slotIndex);
    }

    public static class Box {
        public final float x, y, w, h;

        public Box(float x, float y, float w, float h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }
    }

    protected static class LayoutMetrics {
        static final LayoutMetrics EMPTY = new LayoutMetrics(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

        final float panelX;
        final float panelY;
        final float panelWidth;
        final float panelHeight;
        final float headerHeight;
        final float contentX;
        final float contentY;
        final float contentWidth;
        final float contentHeight;
        final float sidePadding;
        final float footerPadding;

        LayoutMetrics(float panelX, float panelY, float panelWidth, float panelHeight,
                      float headerHeight, float contentX, float contentY,
                      float contentWidth, float contentHeight,
                      float sidePadding, float footerPadding) {
            this.panelX = panelX;
            this.panelY = panelY;
            this.panelWidth = panelWidth;
            this.panelHeight = panelHeight;
            this.headerHeight = headerHeight;
            this.contentX = contentX;
            this.contentY = contentY;
            this.contentWidth = contentWidth;
            this.contentHeight = contentHeight;
            this.sidePadding = sidePadding;
            this.footerPadding = footerPadding;
        }
    }

    protected static class ClickInfo {
        int preferredButton = -1;
        int remainingClicks = 1;
    }
}
