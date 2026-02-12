package dev.hunchclient.render;

import dev.hunchclient.util.Color;
import com.mojang.blaze3d.opengl.GlDevice;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.nanovg.*;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.Minecraft;

import static org.lwjgl.nanovg.NanoSVG.*;
import static org.lwjgl.nanovg.NanoVG.*;
import static org.lwjgl.nanovg.NanoVGGL3.*;
import static org.lwjgl.stb.STBImage.*;

/** NanoVG renderer utilities with font management and texture support. */
public class NVGRenderer {
    private static final Minecraft mc = Minecraft.getInstance();

    private static final NVGPaint nvgPaint = NVGPaint.malloc();
    private static final NVGColor nvgColor = NVGColor.malloc();
    private static final NVGColor nvgColor2 = NVGColor.malloc();

    public static Font defaultFont;

    private static final Map<Font, NVGFont> fontMap = new HashMap<>();
    private static final int MAX_FONT_CACHE_SIZE = 64;
    // PERFORMANCE: Cache the last used font to avoid map lookups
    private static Font lastUsedFont = null;
    private static int lastUsedFontId = -1;
    private static final float[] fontBounds = new float[4];

    private static final Map<Image, NVGImage> images = new HashMap<>();
    private static final int MAX_IMAGE_CACHE_SIZE = 256;

    private static Scissor scissor = null;
    private static int previousTexture = -1;
    private static long vg = -1L;
    private static boolean drawing = false;
    private static boolean fontsInitialized = false;
    private static boolean nvgInitialized = false;
    private static boolean nvgAvailable = false; // Track if NanoVG loaded successfully

    /**
     * Lazy initialization of NanoVG - called when first needed
     * MUST be called after OpenGL context is ready!
     */
    private static void ensureNVGInitialized() {
        if (nvgInitialized) return;
        nvgInitialized = true;

        try {
            vg = nvgCreate(NVG_ANTIALIAS | NVG_STENCIL_STROKES);
            if (vg == -1L) {
                throw new RuntimeException("Failed to initialize NanoVG");
            }
        } catch (Exception e) {
            System.err.println("[NVGRenderer] Failed to initialize NanoVG: " + e.getMessage());
            throw new RuntimeException("NanoVG initialization failed", e);
        }
    }

    /**
     * Lazy initialization of fonts - called when first text is rendered
     */
    private static void ensureFontsLoaded() {
        if (fontsInitialized) return;
        fontsInitialized = true;

        try {
            // Initialize FontManager (loads all fonts)
            FontManager.ensureLoaded();

            // Get default font from FontConfig
            defaultFont = FontConfig.getSelectedFont();
            if (defaultFont == null) {
                System.err.println("[NVGRenderer] WARNING: No font available! Text rendering will not work.");
            }
        } catch (Exception e) {
            System.err.println("[NVGRenderer] Error loading fonts: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Clear the font cache - used when reloading fonts
     */
    public static void clearFontCache() {
        // Clear cache
        fontMap.clear();
        lastUsedFont = null;
        lastUsedFontId = -1;

        // Reload default font
        defaultFont = FontConfig.getSelectedFont();
    }

    /**
     * Check if currently in a drawing frame
     */
    public static boolean isDrawing() {
        return drawing;
    }

    /**
     * Get the NanoVG context handle
     */
    public static long getVg() {
        ensureNVGInitialized();
        return vg;
    }

    public static void beginFrame(float width, float height) {
        ensureNVGInitialized(); // Ensure NanoVG is initialized first

        if (drawing) {
            throw new IllegalStateException("[NVGRenderer] Already drawing, but called beginFrame");
        }

        previousTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        var framebuffer = mc.getMainRenderTarget();
        int glFramebuffer = ((GlTexture) framebuffer.getColorTexture())
                .getFbo(((GlDevice) RenderSystem.getDevice()).directStateAccess(), null);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, glFramebuffer);
        GL11.glViewport(0, 0, framebuffer.width, framebuffer.height);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);

        nvgBeginFrame(vg, width, height, 1f);
        nvgTextAlign(vg, NVG_ALIGN_LEFT | NVG_ALIGN_TOP);
        drawing = true;
    }

    public static void endFrame() {
        if (!drawing) {
            throw new IllegalStateException("[NVGRenderer] Not drawing, but called endFrame");
        }
        nvgEndFrame(vg);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);

        GL20.glUseProgram(0);

        if (previousTexture != -1) {
            GL13.glActiveTexture(previousTexture);
            if (TextureTracker.previousBoundTexture != -1) {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, TextureTracker.previousBoundTexture);
            }
        }

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        drawing = false;
    }

    public static void push() {
        nvgSave(vg);
    }

    public static void pop() {
        nvgRestore(vg);
    }

    public static void scale(float x, float y) {
        nvgScale(vg, x, y);
    }

    public static void translate(float x, float y) {
        nvgTranslate(vg, x, y);
    }

    public static void rotate(float amount) {
        nvgRotate(vg, amount);
    }

    public static void globalAlpha(float amount) {
        nvgGlobalAlpha(vg, Math.max(0f, Math.min(1f, amount)));
    }

    public static void pushScissor(float x, float y, float w, float h) {
        scissor = new Scissor(scissor, x, y, w + x, h + y);
        scissor.applyScissor();
    }

    public static void popScissor() {
        nvgResetScissor(vg);
        scissor = scissor != null ? scissor.previous : null;
        if (scissor != null) {
            scissor.applyScissor();
        }
    }

    public static void line(float x1, float y1, float x2, float y2, float thickness, int color) {
        nvgBeginPath(vg);
        nvgMoveTo(vg, x1, y1);
        nvgLineTo(vg, x2, y2);
        nvgStrokeWidth(vg, thickness);
        setColor(color);
        nvgStrokeColor(vg, nvgColor);
        nvgStroke(vg);
    }

    public static void drawHalfRoundedRect(float x, float y, float w, float h, int color, float radius, boolean roundTop) {
        nvgBeginPath(vg);

        if (roundTop) {
            nvgMoveTo(vg, x, y + h);
            nvgLineTo(vg, x + w, y + h);
            nvgLineTo(vg, x + w, y + radius);
            nvgArcTo(vg, x + w, y, x + w - radius, y, radius);
            nvgLineTo(vg, x + radius, y);
            nvgArcTo(vg, x, y, x, y + radius, radius);
            nvgLineTo(vg, x, y + h);
        } else {
            nvgMoveTo(vg, x, y);
            nvgLineTo(vg, x + w, y);
            nvgLineTo(vg, x + w, y + h - radius);
            nvgArcTo(vg, x + w, y + h, x + w - radius, y + h, radius);
            nvgLineTo(vg, x + radius, y + h);
            nvgArcTo(vg, x, y + h, x, y + h - radius, radius);
            nvgLineTo(vg, x, y);
        }

        nvgClosePath(vg);
        setColor(color);
        nvgFillColor(vg, nvgColor);
        nvgFill(vg);
    }

    public static void rect(float x, float y, float w, float h, int color, float radius) {
        nvgBeginPath(vg);
        nvgRoundedRect(vg, x, y, w, h + 0.5f, radius);
        setColor(color);
        nvgFillColor(vg, nvgColor);
        nvgFill(vg);
    }

    public static void rect(float x, float y, float w, float h, int color) {
        nvgBeginPath(vg);
        nvgRect(vg, x, y, w, h + 0.5f);
        setColor(color);
        nvgFillColor(vg, nvgColor);
        nvgFill(vg);
    }

    public static void hollowRect(float x, float y, float w, float h, float thickness, int color, float radius) {
        nvgBeginPath(vg);
        nvgRoundedRect(vg, x, y, w, h, radius);
        nvgStrokeWidth(vg, thickness);
        nvgPathWinding(vg, NVG_HOLE);
        setColor(color);
        nvgStrokeColor(vg, nvgColor);
        nvgStroke(vg);
    }

    public static void gradientRect(float x, float y, float w, float h, int color1, int color2, Gradient gradient, float radius) {
        nvgBeginPath(vg);
        nvgRoundedRect(vg, x, y, w, h, radius);
        setGradient(color1, color2, x, y, w, h, gradient);
        nvgFillPaint(vg, nvgPaint);
        nvgFill(vg);
    }

    public static void dropShadow(float x, float y, float width, float height, float blur, float spread, float radius) {
        nvgRGBA((byte) 0, (byte) 0, (byte) 0, (byte) 125, nvgColor);
        nvgRGBA((byte) 0, (byte) 0, (byte) 0, (byte) 0, nvgColor2);

        nvgBoxGradient(vg, x - spread, y - spread, width + 2 * spread, height + 2 * spread,
                radius + spread, blur, nvgColor, nvgColor2, nvgPaint);
        nvgBeginPath(vg);
        nvgRoundedRect(vg, x - spread - blur, y - spread - blur,
                width + 2 * spread + 2 * blur, height + 2 * spread + 2 * blur, radius + spread);
        nvgRoundedRect(vg, x, y, width, height, radius);
        nvgPathWinding(vg, NVG_HOLE);
        nvgFillPaint(vg, nvgPaint);
        nvgFill(vg);
    }

    public static void circle(float x, float y, float radius, int color) {
        nvgBeginPath(vg);
        nvgCircle(vg, x, y, radius);
        setColor(color);
        nvgFillColor(vg, nvgColor);
        nvgFill(vg);
    }

    public static void text(String text, float x, float y, float size, int color, Font font) {
        ensureFontsLoaded();
        if (text == null || text.isEmpty()) return;

        // CRITICAL: Prevent native crashes from invalid font state
        if (!drawing) {
            System.err.println("[NVGRenderer] Attempted to render text outside of beginFrame/endFrame!");
            return;
        }

        int fontId = getFontID(font);
        if (fontId == -1) {
            System.err.println("[NVGRenderer] Invalid font ID, skipping text: " + text);
            return;
        }

        try {
            nvgFontSize(vg, size);
            nvgFontFaceId(vg, fontId);
            setColor(color);
            nvgFillColor(vg, nvgColor);
            nvgText(vg, x, y, text);
        } catch (Exception e) {
            System.err.println("[NVGRenderer] Exception rendering text '" + text + "': " + e.getMessage());
        }
    }

    public static void textShadow(String text, float x, float y, float size, int color, Font font) {
        ensureFontsLoaded();
        if (text == null || text.isEmpty()) return;

        // CRITICAL: Prevent native crashes from invalid font state
        if (!drawing) {
            System.err.println("[NVGRenderer] Attempted to render text outside of beginFrame/endFrame!");
            return;
        }

        int fontId = getFontID(font);
        if (fontId == -1) {
            System.err.println("[NVGRenderer] Invalid font ID, skipping text: " + text);
            return;
        }

        try {
            nvgFontFaceId(vg, fontId);
            nvgFontSize(vg, size);
            setColor(-16777216); // Black
            nvgFillColor(vg, nvgColor);
            nvgText(vg, Math.round(x + 3f), Math.round(y + 3f), text);

            setColor(color);
            nvgFillColor(vg, nvgColor);
            nvgText(vg, Math.round(x), Math.round(y), text);
        } catch (Exception e) {
            System.err.println("[NVGRenderer] Exception rendering text '" + text + "': " + e.getMessage());
        }
    }

    public static float textWidth(String text, float size, Font font) {
        ensureNVGInitialized();
        ensureFontsLoaded();
        if (text == null || text.isEmpty()) return 0f;

        int fontId = getFontID(font);
        if (fontId == -1) return 0f; // Font loading failed

        try {
            nvgFontSize(vg, size);
            nvgFontFaceId(vg, fontId);
            return nvgTextBounds(vg, 0f, 0f, text, fontBounds);
        } catch (Exception e) {
            System.err.println("[NVGRenderer] Exception measuring text '" + text + "': " + e.getMessage());
            return 0f;
        }
    }

    public static void drawWrappedString(String text, float x, float y, float w, float size, int color, Font font, float lineHeight) {
        ensureFontsLoaded();
        if (text == null || text.isEmpty()) return;

        if (!drawing) {
            System.err.println("[NVGRenderer] Attempted to render wrapped text outside of beginFrame/endFrame!");
            return;
        }

        int fontId = getFontID(font);
        if (fontId == -1) return; // Font loading failed

        try {
            nvgFontSize(vg, size);
            nvgFontFaceId(vg, fontId);
            nvgTextLineHeight(vg, lineHeight);
            setColor(color);
            nvgFillColor(vg, nvgColor);
            nvgTextBox(vg, x, y, w, text);
        } catch (Exception e) {
            System.err.println("[NVGRenderer] Exception rendering wrapped text: " + e.getMessage());
        }
    }

    public static void drawWrappedString(String text, float x, float y, float w, float size, int color, Font font) {
        drawWrappedString(text, x, y, w, size, color, font, 1f);
    }

    public static float[] wrappedTextBounds(String text, float w, float size, Font font, float lineHeight) {
        ensureFontsLoaded();
        float[] bounds = new float[4];
        if (text == null || text.isEmpty()) return bounds;
        int fontId = getFontID(font);
        if (fontId == -1) return bounds; // Font loading failed

        try {
            nvgFontSize(vg, size);
            nvgFontFaceId(vg, fontId);
            nvgTextLineHeight(vg, lineHeight);
            nvgTextBoxBounds(vg, 0f, 0f, w, text, bounds);
            return bounds;
        } catch (Exception e) {
            System.err.println("[NVGRenderer] Exception measuring wrapped text: " + e.getMessage());
            return bounds;
        }
    }

    public static float[] wrappedTextBounds(String text, float w, float size, Font font) {
        return wrappedTextBounds(text, w, size, font, 1f);
    }

    public static int createNVGImage(int textureId, int textureWidth, int textureHeight) {
        return nvglCreateImageFromHandle(vg, textureId, textureWidth, textureHeight, NVG_IMAGE_NEAREST | NVG_IMAGE_NODELETE);
    }

    public static void image(int image, int textureWidth, int textureHeight, int subX, int subY, int subW, int subH,
                             float x, float y, float w, float h, float radius) {
        if (image == -1) return;

        float sx = (float) subX / textureWidth;
        float sy = (float) subY / textureHeight;
        float sw = (float) subW / textureWidth;
        float sh = (float) subH / textureHeight;

        float iw = w / sw;
        float ih = h / sh;
        float ix = x - iw * sx;
        float iy = y - ih * sy;

        nvgImagePattern(vg, ix, iy, iw, ih, 0f, image, 1f, nvgPaint);
        nvgBeginPath(vg);
        nvgRoundedRect(vg, x, y, w, h + 0.5f, radius);
        nvgFillPaint(vg, nvgPaint);
        nvgFill(vg);
    }

    public static void image(Image image, float x, float y, float w, float h, float radius) {
        nvgImagePattern(vg, x, y, w, h, 0f, getImage(image), 1f, nvgPaint);
        nvgBeginPath(vg);
        nvgRoundedRect(vg, x, y, w, h + 0.5f, radius);
        nvgFillPaint(vg, nvgPaint);
        nvgFill(vg);
    }

    public static void image(Image image, float x, float y, float w, float h) {
        nvgImagePattern(vg, x, y, w, h, 0f, getImage(image), 1f, nvgPaint);
        nvgBeginPath(vg);
        nvgRect(vg, x, y, w, h + 0.5f);
        nvgFillPaint(vg, nvgPaint);
        nvgFill(vg);
    }

    public static Image createImage(String resourcePath) {
        Image image = null;
        for (Image img : images.keySet()) {
            if (img.identifier.equals(resourcePath)) {
                image = img;
                break;
            }
        }
        if (image == null) {
            image = new Image(resourcePath);
        }

        NVGImage nvgImage;
        if (image.isSVG) {
            nvgImage = images.computeIfAbsent(image, img -> new NVGImage(0, loadSVG(img)));
        } else {
            nvgImage = images.computeIfAbsent(image, img -> new NVGImage(0, loadImage(img)));
        }
        nvgImage.count++;
        pruneImageCache();
        return image;
    }

    public static void deleteImage(Image image) {
        NVGImage nvgImage = images.get(image);
        if (nvgImage == null) return;
        nvgImage.count--;
        if (nvgImage.count == 0) {
            nvgDeleteImage(vg, nvgImage.nvg);
            images.remove(image);
        }
        pruneImageCache();
    }

    private static int getImage(Image image) {
        NVGImage nvgImage = images.get(image);
        if (nvgImage == null) {
            throw new IllegalStateException("Image (" + image.identifier + ") doesn't exist");
        }
        return nvgImage.nvg;
    }

    private static int loadImage(Image image) {
        int[] w = new int[1];
        int[] h = new int[1];
        int[] channels = new int[1];
        ByteBuffer buffer = stbi_load_from_memory(image.buffer(), w, h, channels, 4);
        image.releaseBuffer();
        if (buffer == null) {
            throw new NullPointerException("Failed to load image: " + image.identifier);
        }
        try {
            return nvgCreateImageRGBA(vg, w[0], h[0], 0, buffer);
        } finally {
            stbi_image_free(buffer);
        }
    }

    private static int loadSVG(Image image) {
        try {
            String vec = new String(image.stream.readAllBytes());
            NSVGImage svgImage = nsvgParse(vec, "px", 96f);
            if (svgImage == null) {
                throw new IllegalStateException("Failed to parse " + image.identifier);
            }

            int width = (int) svgImage.width();
            int height = (int) svgImage.height();
            ByteBuffer buffer = MemoryUtil.memAlloc(width * height * 4);

            try {
                long rasterizer = nsvgCreateRasterizer();
                nsvgRasterize(rasterizer, svgImage, 0f, 0f, 1f, buffer, width, height, width * 4);
                int nvgImage = nvgCreateImageRGBA(vg, width, height, 0, buffer);
                nsvgDeleteRasterizer(rasterizer);
                return nvgImage;
            } finally {
                nsvgDelete(svgImage);
                MemoryUtil.memFree(buffer);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load SVG: " + image.identifier, e);
        }
    }

    private static void setColor(int color) {
        // Use Color utility to extract ARGB components
        int r = Color.getRed(color);
        int g = Color.getGreen(color);
        int b = Color.getBlue(color);
        int a = Color.getAlpha(color);

        nvgRGBA((byte) r, (byte) g, (byte) b, (byte) a, nvgColor);
    }

    private static void setColor(int color1, int color2) {
        // Use Color utility to extract ARGB components for both colors
        nvgRGBA((byte) Color.getRed(color1), (byte) Color.getGreen(color1),
                (byte) Color.getBlue(color1), (byte) Color.getAlpha(color1), nvgColor);
        nvgRGBA((byte) Color.getRed(color2), (byte) Color.getGreen(color2),
                (byte) Color.getBlue(color2), (byte) Color.getAlpha(color2), nvgColor2);
    }

    private static void setGradient(int color1, int color2, float x, float y, float w, float h, Gradient direction) {
        setColor(color1, color2);
        switch (direction) {
            case LeftToRight:
                nvgLinearGradient(vg, x, y, x + w, y, nvgColor, nvgColor2, nvgPaint);
                break;
            case TopToBottom:
                nvgLinearGradient(vg, x, y, x, y + h, nvgColor, nvgColor2, nvgPaint);
                break;
        }
    }

    private static int getFontID(Font font) {
        // Safety check: if font is null, use default font
        if (font == null) {
            if (defaultFont == null) {
                System.err.println("[NVGRenderer] Both font and defaultFont are null! Cannot render text.");
                return -1;
            }
            font = defaultFont;
        }

        // PERFORMANCE: Check if this is the same font as last time
        if (font == lastUsedFont && lastUsedFontId != -1) {
            return lastUsedFontId;
        }

        try {
            NVGFont cachedFont = fontMap.get(font);
            if (cachedFont != null) {
                lastUsedFont = font;
                lastUsedFontId = cachedFont.id;
                return cachedFont.id;
            }

            if (fontMap.size() >= MAX_FONT_CACHE_SIZE) {
                Font fallback = defaultFont != null ? defaultFont : font;
                NVGFont fallbackFont = fontMap.get(fallback);
                if (fallbackFont != null) {
                    lastUsedFont = fallback;
                    lastUsedFontId = fallbackFont.id;
                    return fallbackFont.id;
                }
            }

            int fontId = fontMap.computeIfAbsent(font, f -> {
                try {
                    ByteBuffer buffer = f.buffer();
                    int id = nvgCreateFontMem(vg, f.name, buffer, false);
                    if (id == -1) {
                        System.err.println("[NVGRenderer] Failed to create font in NanoVG: " + f.name);
                        // Return dummy font rather than crashing
                        return new NVGFont(-1, null);
                    }
                    return new NVGFont(id, buffer);
                } catch (Exception e) {
                    System.err.println("[NVGRenderer] Exception creating font: " + f.name + " - " + e.getMessage());
                    e.printStackTrace();
                    // Return a dummy font ID rather than crashing
                    return new NVGFont(-1, null);
                }
            }).id;

            // PERFORMANCE: Update cache for next time
            lastUsedFont = font;
            lastUsedFontId = fontId;

            return fontId;
        } catch (Exception e) {
            System.err.println("[NVGRenderer] Critical font loading error: " + e.getMessage());
            return -1;
        }
    }

    private static class Scissor {
        final Scissor previous;
        final float x, y, maxX, maxY;

        Scissor(Scissor previous, float x, float y, float maxX, float maxY) {
            this.previous = previous;
            this.x = x;
            this.y = y;
            this.maxX = maxX;
            this.maxY = maxY;
        }

        void applyScissor() {
            if (previous == null) {
                nvgScissor(vg, x, y, maxX - x, maxY - y);
            } else {
                float x = Math.max(this.x, previous.x);
                float y = Math.max(this.y, previous.y);
                float width = Math.max(0f, Math.min(maxX, previous.maxX) - x);
                float height = Math.max(0f, Math.min(maxY, previous.maxY) - y);
                nvgScissor(vg, x, y, width, height);
            }
        }
    }

    private static class NVGImage {
        int count;
        final int nvg;

        NVGImage(int count, int nvg) {
            this.count = count;
            this.nvg = nvg;
        }
    }

    private static class NVGFont {
        final int id;
        final ByteBuffer buffer; // Store buffer reference for cleanup

        NVGFont(int id, ByteBuffer buffer) {
            this.id = id;
            this.buffer = buffer;
        }

        void free() {
            if (buffer != null && !buffer.isDirect()) {
                // Only free if it's a direct buffer we allocated
                // MemoryUtil.memFree(buffer); // Commented out - NanoVG manages font memory
            }
        }
    }

    /**
     * Cleanup all native NanoVG resources.
     * CRITICAL: Call this on client shutdown to prevent native memory leaks!
     */
    public static void cleanup() {
        // Free native NanoVG structs
        if (nvgPaint != null) {
            try {
                nvgPaint.free();
            } catch (Exception e) {
                System.err.println("[NVGRenderer] Error freeing nvgPaint: " + e.getMessage());
            }
        }

        if (nvgColor != null) {
            try {
                nvgColor.free();
            } catch (Exception e) {
                System.err.println("[NVGRenderer] Error freeing nvgColor: " + e.getMessage());
            }
        }

        if (nvgColor2 != null) {
            try {
                nvgColor2.free();
            } catch (Exception e) {
                System.err.println("[NVGRenderer] Error freeing nvgColor2: " + e.getMessage());
            }
        }

        // Clean up fonts
        for (NVGFont font : fontMap.values()) {
            try {
                font.free();
            } catch (Exception e) {
                System.err.println("[NVGRenderer] Error freeing font: " + e.getMessage());
            }
        }
        fontMap.clear();

        // Delete all images
        for (NVGImage img : images.values()) {
            try {
                nvgDeleteImage(vg, img.nvg);
            } catch (Exception e) {
                System.err.println("[NVGRenderer] Error deleting image: " + e.getMessage());
            }
        }
        images.clear();

        // Delete NanoVG context
        if (vg != -1L) {
            try {
                nvgDelete(vg);
                vg = -1L;
            } catch (Exception e) {
                System.err.println("[NVGRenderer] Error deleting NanoVG context: " + e.getMessage());
            }
        }
    }

    private static void pruneImageCache() {
        if (images.size() <= MAX_IMAGE_CACHE_SIZE) {
            return;
        }
        Iterator<Map.Entry<Image, NVGImage>> iterator = images.entrySet().iterator();
        while (iterator.hasNext() && images.size() > MAX_IMAGE_CACHE_SIZE) {
            Map.Entry<Image, NVGImage> entry = iterator.next();
            NVGImage nvgImage = entry.getValue();
            if (nvgImage.count <= 0) {
                try {
                    nvgDeleteImage(vg, nvgImage.nvg);
                } catch (Exception e) {
                    System.err.println("[NVGRenderer] Error deleting image: " + e.getMessage());
                }
                iterator.remove();
            }
        }
    }
}
