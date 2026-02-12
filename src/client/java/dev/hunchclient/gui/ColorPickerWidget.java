package dev.hunchclient.gui;

import dev.hunchclient.render.NVGRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import org.lwjgl.nanovg.NVGColor;
import org.lwjgl.nanovg.NVGPaint;

import static org.lwjgl.nanovg.NanoVG.*;

/**
 * Visual HSV Color Picker Widget (like Pickr.js)
 * Optimized with NanoVG for smooth performance
 */
public class ColorPickerWidget {

    private static final int SKEET_BORDER = 0xFF2A2A2A;
    private static final long vg = getVG(); // Get NVG context

    private int x, y;
    private int width, height;
    private float hue = 0.0f;        // 0-360
    private float saturation = 1.0f; // 0-1
    private float value = 1.0f;      // 0-1

    private boolean draggingSV = false;
    private boolean draggingHue = false;

    // NVG paint objects (reusable)
    private static final NVGPaint nvgPaint = NVGPaint.malloc();
    private static final NVGColor nvgColor = NVGColor.malloc();
    private static final NVGColor nvgColor2 = NVGColor.malloc();

    public ColorPickerWidget(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void render(GuiGraphics context) {
        int svHeight = height - 20; // Leave 20px for hue bar
        int hueBarY = y + svHeight + 4;

        // Start NVG frame
        NVGRenderer.beginFrame(context.guiWidth(), context.guiHeight());

        // Draw SV gradient box
        drawSVGradientNVG(x, y, width, svHeight);

        // Draw hue bar
        drawHueBarNVG(x, hueBarY, width, 12);

        // Draw SV picker circle
        int svX = x + (int) (saturation * width);
        int svY = y + (int) ((1.0f - value) * svHeight);
        drawPickerCircleNVG(svX, svY);

        // Draw hue picker line
        int hueX = x + (int) ((hue / 360.0f) * width);
        drawHueLineNVG(hueX, hueBarY, 12);

        // End NVG frame
        NVGRenderer.endFrame();
    }

    private void drawSVGradientNVG(int x, int y, int width, int height) {
        // Get current hue color in RGB
        int hueColor = 0xFF000000 | hsvToRgb(hue, 1.0f, 1.0f);

        // Draw border
        setColor(SKEET_BORDER);
        nvgBeginPath(vg);
        nvgRect(vg, x - 1, y - 1, width + 2, height + 2);
        nvgFillColor(vg, nvgColor);
        nvgFill(vg);

        // Draw the SV gradient box
        // First, fill with the hue color
        setColor(hueColor);
        nvgBeginPath(vg);
        nvgRect(vg, x, y, width, height);
        nvgFillColor(vg, nvgColor);
        nvgFill(vg);

        // Apply horizontal white-to-transparent gradient (for saturation)
        setColor(0xFFFFFFFF, 0x00FFFFFF);
        nvgLinearGradient(vg, x, y, x + width, y, nvgColor, nvgColor2, nvgPaint);
        nvgBeginPath(vg);
        nvgRect(vg, x, y, width, height);
        nvgFillPaint(vg, nvgPaint);
        nvgFill(vg);

        // Apply vertical transparent-to-black gradient (for value)
        setColor(0x00000000, 0xFF000000);
        nvgLinearGradient(vg, x, y, x, y + height, nvgColor, nvgColor2, nvgPaint);
        nvgBeginPath(vg);
        nvgRect(vg, x, y, width, height);
        nvgFillPaint(vg, nvgPaint);
        nvgFill(vg);
    }

    private void drawHueBarNVG(int x, int y, int width, int height) {
        // Draw border
        setColor(SKEET_BORDER);
        nvgBeginPath(vg);
        nvgRect(vg, x - 1, y - 1, width + 2, height + 2);
        nvgFillColor(vg, nvgColor);
        nvgFill(vg);

        // Draw hue gradient in segments for smooth rainbow
        float segmentWidth = width / 6.0f;
        int[] hueColors = new int[]{
                0xFFFF0000, // Red
                0xFFFFFF00, // Yellow
                0xFF00FF00, // Green
                0xFF00FFFF, // Cyan
                0xFF0000FF, // Blue
                0xFFFF00FF, // Magenta
                0xFFFF0000  // Red again
        };

        for (int i = 0; i < 6; i++) {
            float x1 = x + i * segmentWidth;
            float x2 = x + (i + 1) * segmentWidth;

            setColor(hueColors[i], hueColors[i + 1]);
            nvgLinearGradient(vg, x1, y, x2, y, nvgColor, nvgColor2, nvgPaint);
            nvgBeginPath(vg);
            nvgRect(vg, x1, y, segmentWidth, height);
            nvgFillPaint(vg, nvgPaint);
            nvgFill(vg);
        }
    }

    private void drawPickerCircleNVG(int cx, int cy) {
        // Outer white circle
        nvgBeginPath(vg);
        nvgCircle(vg, cx, cy, 5);
        setColor(0xFFFFFFFF);
        nvgFillColor(vg, nvgColor);
        nvgFill(vg);

        // Inner black circle
        nvgBeginPath(vg);
        nvgCircle(vg, cx, cy, 3);
        setColor(0xFF000000);
        nvgFillColor(vg, nvgColor);
        nvgFill(vg);
    }

    private void drawHueLineNVG(int x, int y, int height) {
        // White line
        nvgBeginPath(vg);
        nvgRect(vg, x - 1, y, 3, height);
        setColor(0xFFFFFFFF);
        nvgFillColor(vg, nvgColor);
        nvgFill(vg);

        // Black borders
        nvgBeginPath(vg);
        nvgRect(vg, x - 2, y, 1, height);
        nvgRect(vg, x + 2, y, 1, height);
        setColor(0xFF000000);
        nvgFillColor(vg, nvgColor);
        nvgFill(vg);
    }

    private static void setColor(int color) {
        nvgRGBA(
                (byte) ((color >> 16) & 0xFF),
                (byte) ((color >> 8) & 0xFF),
                (byte) (color & 0xFF),
                (byte) ((color >> 24) & 0xFF),
                nvgColor
        );
    }

    private static void setColor(int color1, int color2) {
        nvgRGBA(
                (byte) ((color1 >> 16) & 0xFF),
                (byte) ((color1 >> 8) & 0xFF),
                (byte) (color1 & 0xFF),
                (byte) ((color1 >> 24) & 0xFF),
                nvgColor
        );
        nvgRGBA(
                (byte) ((color2 >> 16) & 0xFF),
                (byte) ((color2 >> 8) & 0xFF),
                (byte) (color2 & 0xFF),
                (byte) ((color2 >> 24) & 0xFF),
                nvgColor2
        );
    }

    // Get the NVG context via reflection
    private static long getVG() {
        try {
            var field = NVGRenderer.class.getDeclaredField("vg");
            field.setAccessible(true);
            return (long) field.get(null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get NVG context", e);
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY) {
        int svHeight = height - 20;
        int hueBarY = y + svHeight + 4;

        // Check SV area
        if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + svHeight) {
            draggingSV = true;
            updateSV(mouseX, mouseY, svHeight);
            return true;
        }

        // Check hue bar
        if (mouseX >= x && mouseX <= x + width && mouseY >= hueBarY && mouseY <= hueBarY + 12) {
            draggingHue = true;
            updateHue(mouseX);
            return true;
        }

        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY) {
        if (draggingSV) {
            int svHeight = height - 20;
            updateSV(mouseX, mouseY, svHeight);
            return true;
        }

        if (draggingHue) {
            updateHue(mouseX);
            return true;
        }

        return false;
    }

    public void mouseReleased() {
        draggingSV = false;
        draggingHue = false;
    }

    private void updateSV(double mouseX, double mouseY, int svHeight) {
        saturation = Mth.clamp((float) (mouseX - x) / width, 0.0f, 1.0f);
        value = 1.0f - Mth.clamp((float) (mouseY - y) / svHeight, 0.0f, 1.0f);
    }

    private void updateHue(double mouseX) {
        hue = Mth.clamp((float) (mouseX - x) / width, 0.0f, 1.0f) * 360.0f;
    }

    public void setHSV(float h, float s, float v) {
        this.hue = Mth.clamp(h, 0.0f, 360.0f);
        this.saturation = Mth.clamp(s, 0.0f, 1.0f);
        this.value = Mth.clamp(v, 0.0f, 1.0f);
    }

    public void setRGB(float r, float g, float b) {
        float[] hsv = rgbToHsv(r, g, b);
        this.hue = hsv[0];
        this.saturation = hsv[1];
        this.value = hsv[2];
    }

    public float[] getRGB() {
        int rgb = hsvToRgb(hue, saturation, value);
        float r = ((rgb >> 16) & 0xFF) / 255.0f;
        float g = ((rgb >> 8) & 0xFF) / 255.0f;
        float b = (rgb & 0xFF) / 255.0f;
        return new float[]{r, g, b};
    }

    public int getRGBInt() {
        return hsvToRgb(hue, saturation, value);
    }

    // HSV to RGB conversion
    private static int hsvToRgb(float h, float s, float v) {
        float c = v * s;
        float x = c * (1.0f - Math.abs((h / 60.0f) % 2.0f - 1.0f));
        float m = v - c;

        float r = 0, g = 0, b = 0;

        if (h >= 0 && h < 60) {
            r = c; g = x; b = 0;
        } else if (h >= 60 && h < 120) {
            r = x; g = c; b = 0;
        } else if (h >= 120 && h < 180) {
            r = 0; g = c; b = x;
        } else if (h >= 180 && h < 240) {
            r = 0; g = x; b = c;
        } else if (h >= 240 && h < 300) {
            r = x; g = 0; b = c;
        } else {
            r = c; g = 0; b = x;
        }

        int ri = (int) ((r + m) * 255);
        int gi = (int) ((g + m) * 255);
        int bi = (int) ((b + m) * 255);

        return (ri << 16) | (gi << 8) | bi;
    }

    // RGB to HSV conversion
    private static float[] rgbToHsv(float r, float g, float b) {
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;

        float h = 0;
        if (delta > 0) {
            if (max == r) {
                h = 60 * (((g - b) / delta) % 6);
            } else if (max == g) {
                h = 60 * (((b - r) / delta) + 2);
            } else {
                h = 60 * (((r - g) / delta) + 4);
            }
        }
        if (h < 0) h += 360;

        float s = max == 0 ? 0 : delta / max;
        float v = max;

        return new float[]{h, s, v};
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
}
