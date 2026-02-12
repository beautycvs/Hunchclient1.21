package dev.hunchclient.hud.elements;

import com.google.gson.JsonObject;
import dev.hunchclient.hud.BaseHudElement;
import dev.hunchclient.render.NVGRenderer;
import dev.hunchclient.util.Color;
import dev.hunchclient.util.Colors;
import dev.hunchclient.util.HumanMouseEmulator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * HUD Element for Mouse Emulator Visualization
 * Shows the path taken by the auto-clicker with trail lines and click markers
 */
public class MouseVisualizerHudElement extends BaseHudElement {

    private static final Minecraft mc = Minecraft.getInstance();

    // Scale factor for converting window coords to element coords
    private float scale = 1.0f;

    public MouseVisualizerHudElement(float x, float y, int width, int height) {
        super("Mouse Visualizer", x, y, width, height);
    }

    @Override
    public void render(GuiGraphics context, int x, int y, int screenWidth, int screenHeight, boolean editMode) {
        if (!enabled) {
            return;
        }

        HumanMouseEmulator emulator = HumanMouseEmulator.get();

        // Show placeholder in edit mode or when no visualization is active
        boolean hasData = emulator.isVisualizationActive();

        if (editMode || !hasData) {
            // Render placeholder background
            context.fill(x, y, x + width, y + height, 0x80000000);

            // Draw border
            drawBorder(context, x, y, width, height, 0xFF55FFFF);

            // Draw label
            String label = hasData ? "Mouse Visualizer (Active)" : "Mouse Visualizer";
            int labelWidth = mc.font.width(label);
            context.drawString(mc.font, label, x + width / 2 - labelWidth / 2, y + 10, 0xFFFFFFFF, true);

            if (!hasData && !editMode) {
                String hint = "Trail appears after terminal closes";
                int hintWidth = mc.font.width(hint);
                context.drawString(mc.font, hint, x + width / 2 - hintWidth / 2, y + height / 2 - 4, 0x80FFFFFF, false);
            }

            return;
        }

        // Calculate scale factor to fit visualization in the element
        int windowWidth = mc.getWindow().getScreenWidth();
        int windowHeight = mc.getWindow().getScreenHeight();
        float scaleX = (float) width / windowWidth;
        float scaleY = (float) height / windowHeight;
        scale = Math.min(scaleX, scaleY);

        // Get visualization data
        List<HumanMouseEmulator.TrailPoint> trailPoints = emulator.getVisualizationTrail();
        List<HumanMouseEmulator.ClickPoint> clickPoints = emulator.getVisualizationClicks();

        // Get fade progress
        float progress = emulator.getVisualizationProgress();
        float alpha = Math.min(1.0f, progress * 1.5f);

        // Render background
        context.fill(x, y, x + width, y + height, new Color(20, 20, 20, (int)(alpha * 220)).getRgba());

        // Draw border
        int borderColor = Colors.MINECRAFT_AQUA.withAlpha(alpha * 0.8f).getRgba();
        drawBorder(context, x, y, width, height, borderColor);

        // Render using NVG for smooth lines
        try {
            NVGRenderer.beginFrame(mc.getWindow().getScreenWidth(), mc.getWindow().getScreenHeight());

            // Render trail
            renderTrail(trailPoints, x, y, alpha);

            // Render clicks
            renderClicks(clickPoints, x, y, alpha);

            // Render info
            renderInfo(x, y, progress, trailPoints.size(), clickPoints.size(), alpha);

            NVGRenderer.endFrame();
        } catch (Exception e) {
            System.err.println("[MouseVisualizerHud] Render error: " + e.getMessage());
        }
    }

    private void renderTrail(List<HumanMouseEmulator.TrailPoint> points, int baseX, int baseY, float alpha) {
        if (points.size() < 2) return;

        Color trailColor = Colors.MINECRAFT_AQUA.brighter(1.2f);

        for (int i = 1; i < points.size(); i++) {
            HumanMouseEmulator.TrailPoint prev = points.get(i - 1);
            HumanMouseEmulator.TrailPoint curr = points.get(i);

            float positionFade = (float) i / points.size();
            float lineAlpha = alpha * positionFade * 0.9f;

            if (lineAlpha < 0.01f) continue;

            float lineWidth = 1.5f + (positionFade * 1.5f);

            // Scale and offset to element position
            float x1 = baseX + (float) prev.x * scale;
            float y1 = baseY + (float) prev.y * scale;
            float x2 = baseX + (float) curr.x * scale;
            float y2 = baseY + (float) curr.y * scale;

            Color lineColor = trailColor.withAlpha(lineAlpha);
            NVGRenderer.line(x1, y1, x2, y2, lineWidth, lineColor.getRgba());
        }

        // Start marker
        if (!points.isEmpty()) {
            HumanMouseEmulator.TrailPoint start = points.get(0);
            float sx = baseX + (float) start.x * scale;
            float sy = baseY + (float) start.y * scale;
            Color startColor = new Color(100, 255, 100).withAlpha(alpha * 0.8f);
            NVGRenderer.circle(sx, sy, 6f * scale, startColor.getRgba());
        }

        // End marker
        if (points.size() > 1) {
            HumanMouseEmulator.TrailPoint end = points.get(points.size() - 1);
            float ex = baseX + (float) end.x * scale;
            float ey = baseY + (float) end.y * scale;
            Color endColor = trailColor.withAlpha(alpha * 0.9f);
            NVGRenderer.circle(ex, ey, 5f * scale, endColor.getRgba());
        }
    }

    private void renderClicks(List<HumanMouseEmulator.ClickPoint> clicks, int baseX, int baseY, float alpha) {
        for (int i = 0; i < clicks.size(); i++) {
            HumanMouseEmulator.ClickPoint click = clicks.get(i);

            // Green = left, Red = right
            Color clickColor = click.rightClick
                ? new Color(255, 80, 80)
                : new Color(80, 255, 80);

            float cx = baseX + (float) click.x * scale;
            float cy = baseY + (float) click.y * scale;

            // Glow
            NVGRenderer.circle(cx, cy, 12f * scale, clickColor.withAlpha(alpha * 0.3f).getRgba());

            // Main square
            float size = 10f * scale;
            NVGRenderer.rect(cx - size/2, cy - size/2, size, size,
                clickColor.withAlpha(alpha * 0.7f).getRgba(), 2f);

            // Border
            NVGRenderer.hollowRect(cx - size/2, cy - size/2, size, size, 1f,
                Colors.WHITE.withAlpha(alpha * 0.8f).getRgba(), 2f);

            // Number inside square
            String numStr = String.valueOf(i + 1);
            float numSize = 8f * scale;
            float numWidth = NVGRenderer.textWidth(numStr, numSize, NVGRenderer.defaultFont);
            NVGRenderer.text(numStr, cx - numWidth/2, cy + numSize/3, numSize,
                Colors.WHITE.withAlpha(alpha).getRgba(), NVGRenderer.defaultFont);

            // Timing label below the click marker (in milliseconds)
            String timeStr = click.relativeTimeMs + "ms";
            float timeSize = 7f * scale;
            float timeWidth = NVGRenderer.textWidth(timeStr, timeSize, NVGRenderer.defaultFont);

            // Background for timing label
            float labelPadding = 2f * scale;
            float labelX = cx - timeWidth/2 - labelPadding;
            float labelY = cy + size/2 + 3f * scale;
            NVGRenderer.rect(labelX, labelY, timeWidth + labelPadding * 2, timeSize + labelPadding * 2,
                new Color(0, 0, 0).withAlpha(alpha * 0.7f).getRgba(), 2f);

            // Timing text
            NVGRenderer.text(timeStr, cx - timeWidth/2, labelY + timeSize, timeSize,
                Colors.MINECRAFT_YELLOW.withAlpha(alpha).getRgba(), NVGRenderer.defaultFont);
        }
    }

    private void renderInfo(int baseX, int baseY, float progress, int trailCount, int clickCount, float alpha) {
        float infoX = baseX + 8;
        float infoY = baseY + height - 30;

        // Stats line
        String stats = String.format("Points: %d | Clicks: %d", trailCount, clickCount);
        NVGRenderer.text(stats, infoX, infoY, 10f,
            Colors.WHITE.withAlpha(alpha * 0.8f).getRgba(), NVGRenderer.defaultFont);

        // Time remaining
        float timeLeft = progress * 5.0f; // 5 seconds total
        String timeStr = String.format("%.1fs", timeLeft);
        NVGRenderer.text(timeStr, infoX, infoY + 14, 9f,
            Colors.WHITE.withAlpha(alpha * 0.6f).getRgba(), NVGRenderer.defaultFont);

        // Progress bar
        float barX = baseX + width - 60;
        float barY = infoY + 4;
        float barWidth = 50f;
        float barHeight = 6f;

        NVGRenderer.rect(barX, barY, barWidth, barHeight,
            new Color(50, 50, 50).withAlpha(alpha).getRgba(), 3f);
        NVGRenderer.rect(barX, barY, barWidth * progress, barHeight,
            Colors.MINECRAFT_AQUA.withAlpha(alpha).getRgba(), 3f);
    }

    private void drawBorder(GuiGraphics context, int x, int y, int width, int height, int color) {
        int thickness = 2;
        context.fill(x, y, x + width, y + thickness, color);
        context.fill(x, y + height - thickness, x + width, y + height, color);
        context.fill(x, y, x + thickness, y + height, color);
        context.fill(x + width - thickness, y, x + width, y + height, color);
    }

    @Override
    public int getMinWidth() {
        return 150;
    }

    @Override
    public int getMinHeight() {
        return 100;
    }

    @Override
    public int getMaxWidth() {
        return 800;
    }

    @Override
    public int getMaxHeight() {
        return 600;
    }

    @Override
    public JsonObject saveToJson() {
        JsonObject json = super.saveToJson();
        json.addProperty("type", "mouse_visualizer");
        return json;
    }

    @Override
    public void loadFromJson(JsonObject json) {
        super.loadFromJson(json);
    }
}
