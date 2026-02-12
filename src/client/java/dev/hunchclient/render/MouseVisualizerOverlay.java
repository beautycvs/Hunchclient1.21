package dev.hunchclient.render;

import dev.hunchclient.util.Color;
import dev.hunchclient.util.Colors;
import dev.hunchclient.util.HumanMouseEmulator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Direct overlay renderer for Mouse Emulator Visualization.
 * Shows trail and clicks at their ACTUAL screen positions.
 */
public class MouseVisualizerOverlay {

    private static final Minecraft mc = Minecraft.getInstance();

    /**
     * Render the visualization overlay directly at real mouse positions.
     */
    public static void render(GuiGraphics context) {
        HumanMouseEmulator emulator = HumanMouseEmulator.get();

        if (!emulator.isVisualizationActive()) {
            return;
        }

        List<HumanMouseEmulator.TrailPoint> trailPoints = emulator.getVisualizationTrail();
        List<HumanMouseEmulator.ClickPoint> clickPoints = emulator.getVisualizationClicks();

        if (trailPoints.isEmpty() && clickPoints.isEmpty()) {
            return;
        }

        float progress = emulator.getVisualizationProgress();
        float alpha = Math.min(1.0f, progress * 1.5f);

        // GUI scale for coordinate conversion
        float guiScale = (float) mc.getWindow().getGuiScale();

        try {
            NVGRenderer.beginFrame(mc.getWindow().getScreenWidth(), mc.getWindow().getScreenHeight());

            // Render trail at REAL positions (already in window coords from HumanMouseEmulator)
            renderTrail(trailPoints, alpha);

            // Render clicks at REAL positions
            renderClicks(clickPoints, alpha);

            // Render small info box in corner
            renderInfoBox(progress, trailPoints.size(), clickPoints.size(), alpha, guiScale);

            NVGRenderer.endFrame();
        } catch (Exception e) {
            System.err.println("[MouseVisualizerOverlay] Render error: " + e.getMessage());
        }
    }

    private static void renderTrail(List<HumanMouseEmulator.TrailPoint> points, float alpha) {
        if (points.size() < 2) return;

        Color trailColor = Colors.MINECRAFT_AQUA.brighter(1.2f);

        for (int i = 1; i < points.size(); i++) {
            HumanMouseEmulator.TrailPoint prev = points.get(i - 1);
            HumanMouseEmulator.TrailPoint curr = points.get(i);

            float positionFade = (float) i / points.size();
            float lineAlpha = alpha * positionFade * 0.8f;

            if (lineAlpha < 0.01f) continue;

            float lineWidth = 2f + (positionFade * 2f);

            // Coordinates are already in window space from HumanMouseEmulator
            float x1 = (float) prev.x;
            float y1 = (float) prev.y;
            float x2 = (float) curr.x;
            float y2 = (float) curr.y;

            Color lineColor = trailColor.withAlpha(lineAlpha);
            NVGRenderer.line(x1, y1, x2, y2, lineWidth, lineColor.getRgba());
        }

        // Start marker (green circle)
        if (!points.isEmpty()) {
            HumanMouseEmulator.TrailPoint start = points.get(0);
            Color startColor = new Color(100, 255, 100).withAlpha(alpha * 0.7f);
            NVGRenderer.circle((float) start.x, (float) start.y, 8f, startColor.getRgba());
        }

        // End marker (aqua circle)
        if (points.size() > 1) {
            HumanMouseEmulator.TrailPoint end = points.get(points.size() - 1);
            Color endColor = trailColor.withAlpha(alpha * 0.8f);
            NVGRenderer.circle((float) end.x, (float) end.y, 6f, endColor.getRgba());
        }
    }

    private static void renderClicks(List<HumanMouseEmulator.ClickPoint> clicks, float alpha) {
        for (int i = 0; i < clicks.size(); i++) {
            HumanMouseEmulator.ClickPoint click = clicks.get(i);

            // Green = left click, Red = right click
            Color clickColor = click.rightClick
                ? new Color(255, 80, 80)
                : new Color(80, 255, 80);

            float cx = (float) click.x;
            float cy = (float) click.y;

            // Simple circle (like the start marker)
            NVGRenderer.circle(cx, cy, 8f, clickColor.withAlpha(alpha * 0.7f).getRgba());
        }
    }

    private static void renderInfoBox(float progress, int trailCount, int clickCount, float alpha, float guiScale) {
        // Small info box bottom-left
        float boxX = 10 * guiScale;
        float boxY = mc.getWindow().getScreenHeight() - 40 * guiScale;
        float boxW = 120 * guiScale;
        float boxH = 30 * guiScale;

        // Background
        NVGRenderer.rect(boxX, boxY, boxW, boxH,
            new Color(20, 20, 20).withAlpha(alpha * 0.8f).getRgba(), 4f);

        // Border
        NVGRenderer.hollowRect(boxX, boxY, boxW, boxH, 1f,
            Colors.MINECRAFT_AQUA.withAlpha(alpha * 0.6f).getRgba(), 4f);

        // Stats text
        String stats = String.format("%d clicks", clickCount);
        NVGRenderer.text(stats, boxX + 8, boxY + 14, 10f,
            Colors.WHITE.withAlpha(alpha * 0.9f).getRgba(), NVGRenderer.defaultFont);

        // Time remaining
        float timeLeft = progress * 5.0f;
        String timeStr = String.format("%.1fs", timeLeft);
        NVGRenderer.text(timeStr, boxX + 8, boxY + 26, 9f,
            Colors.WHITE.withAlpha(alpha * 0.6f).getRgba(), NVGRenderer.defaultFont);

        // Progress bar
        float barX = boxX + boxW - 55;
        float barY = boxY + 10;
        float barW = 45f;
        float barH = 10f;

        NVGRenderer.rect(barX, barY, barW, barH,
            new Color(50, 50, 50).withAlpha(alpha).getRgba(), 3f);
        NVGRenderer.rect(barX, barY, barW * progress, barH,
            Colors.MINECRAFT_AQUA.withAlpha(alpha).getRgba(), 3f);
    }
}
