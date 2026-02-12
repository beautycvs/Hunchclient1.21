package dev.hunchclient.render;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.FormattedCharSink;

/**
 * Queue system for custom font rendering
 * Collects text draw calls during GUI rendering and renders them all at the end
 * to ensure they're ALWAYS on top
 */
public class CustomFontRenderQueue {
    private static final List<QueuedText> textQueue = new ArrayList<>();
    private static boolean collecting = false;

    // ===== PERFORMANCE OPTIMIZATION: Reusable buffers to avoid allocations =====
    private static final List<ColoredTextSegment> SEGMENT_BUFFER = new ArrayList<>();
    private static final List<ColoredTextSegment> FINAL_SEGMENTS_BUFFER = new ArrayList<>();
    private static final StringBuilder REUSABLE_STRING_BUILDER = new StringBuilder(256);
    private static final int[] REUSABLE_COLOR_HOLDER = new int[1];

    // Reusable visitor to avoid lambda allocation every call
    private static final ReusableCharSinkVisitor REUSABLE_VISITOR = new ReusableCharSinkVisitor();

    private static class ReusableCharSinkVisitor implements FormattedCharSink {
        int defaultColor;

        void reset(int defaultColor) {
            this.defaultColor = defaultColor;
            REUSABLE_STRING_BUILDER.setLength(0);
            REUSABLE_COLOR_HOLDER[0] = defaultColor & 0xFFFFFF;
        }

        @Override
        public boolean accept(int index, net.minecraft.network.chat.Style style, int codePoint) {
            // Get color from style
            TextColor styleColor = style.getColor();
            int color = styleColor != null ? styleColor.getValue() : (defaultColor & 0xFFFFFF);

            // If color changed, flush current segment
            if (color != REUSABLE_COLOR_HOLDER[0] && REUSABLE_STRING_BUILDER.length() > 0) {
                SEGMENT_BUFFER.add(new ColoredTextSegment(REUSABLE_STRING_BUILDER.toString(), REUSABLE_COLOR_HOLDER[0]));
                REUSABLE_STRING_BUILDER.setLength(0);
            }

            // Append character
            REUSABLE_STRING_BUILDER.appendCodePoint(codePoint);
            REUSABLE_COLOR_HOLDER[0] = color;

            return true; // Continue visiting
        }
    }

    public static class QueuedText {
        public final List<ColoredTextSegment> segments; // Color-coded segments
        public final float x;
        public final float y;
        public final float size;
        public final boolean shadow;
        public final Font font;
        public final ScissorBox scissor;

        public QueuedText(String text, float x, float y, float size, int color, boolean shadow, Font font, ScissorBox scissor) {
            // Parse § color codes in the text (Hypixel scoreboard sends raw § codes)
            int rgb = ColoredTextSegment.toRGB(color);
            this.segments = CustomFontColorParser.parse(text, rgb);

            this.x = x;
            this.y = y;
            this.size = size;
            this.shadow = shadow;
            this.font = font;
            this.scissor = scissor;
        }

        /**
         * Constructor for styled text (from Text/OrderedText)
         */
        public QueuedText(List<ColoredTextSegment> segments, float x, float y, float size, boolean shadow, Font font, ScissorBox scissor) {
            this.segments = segments;
            this.x = x;
            this.y = y;
            this.size = size;
            this.shadow = shadow;
            this.font = font;
            this.scissor = scissor;
        }
    }

    public static class ScissorBox {
        public final int x, y, width, height;
        public final boolean enabled;

        public ScissorBox(int x, int y, int width, int height, boolean enabled) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.enabled = enabled;
        }

        public static ScissorBox capture() {
            // Use ScissorStateTracker instead of GL state - the tracker captures bounds
            // from GuiGraphics.enableScissor() calls which is more reliable for deferred NVG rendering
            int[] bounds = ScissorStateTracker.getCurrentBounds();
            if (bounds != null) {
                // Tracker stores [x1, y1, x2, y2] in screen coords, convert to [x, y, width, height]
                int x1 = bounds[0];
                int y1 = bounds[1];
                int x2 = bounds[2];
                int y2 = bounds[3];

                // Get window info for coordinate conversion
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.getWindow() != null) {
                    double scale = mc.getWindow().getGuiScale();
                    int fbHeight = mc.getWindow().getHeight();

                    // Convert GUI coords to framebuffer coords
                    int fbX = (int)(x1 * scale);
                    int fbY = (int)(y1 * scale);
                    int fbWidth = (int)((x2 - x1) * scale);
                    int fbHeight2 = (int)((y2 - y1) * scale);

                    // Convert to GL coords (Y flipped from top)
                    int glY = fbHeight - fbY - fbHeight2;

                    return new ScissorBox(fbX, glY, fbWidth, fbHeight2, true);
                }
            }

            // Fallback: check GL state directly
            boolean enabled = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
            if (!enabled) {
                return new ScissorBox(0, 0, 0, 0, false);
            }
            java.nio.IntBuffer scissorBox = org.lwjgl.BufferUtils.createIntBuffer(16);
            org.lwjgl.opengl.GL11.glGetIntegerv(org.lwjgl.opengl.GL11.GL_SCISSOR_BOX, scissorBox);
            return new ScissorBox(scissorBox.get(0), scissorBox.get(1), scissorBox.get(2), scissorBox.get(3), true);
        }

        public void applyNVG(long vg, int framebufferHeight) {
            if (enabled) {
                // Flip Y coordinate for NVG (GL uses bottom-left origin, NVG uses top-left)
                float nvgY = framebufferHeight - y - height;
                // Use NanoVG scissor instead of OpenGL scissor
                // Debug: Print scissor bounds to check if they're correct
                // System.out.println("[CustomFont] Applying scissor: x=" + x + " nvgY=" + nvgY + " width=" + width + " height=" + height + " (fbHeight=" + framebufferHeight + ", glY=" + y + ")");
                org.lwjgl.nanovg.NanoVG.nvgScissor(vg, x, nvgY, width, height);
            } else {
                // Reset scissor (use full screen)
                // System.out.println("[CustomFont] Scissor not enabled, resetting");
                org.lwjgl.nanovg.NanoVG.nvgResetScissor(vg);
            }
        }
    }

    public static void startCollecting() {
        collecting = true;
        textQueue.clear();
    }

    public static void queueText(String text, float x, float y, float size, int color, boolean shadow, Font font, ScissorBox scissor) {
        if (!collecting) return;
        textQueue.add(new QueuedText(text, x, y, size, color, shadow, font, scissor));
    }

    /**
     * Queue styled text from Text object (extracts colors from Style)
     */
    public static void queueStyledText(Component text, float x, float y, float size, int defaultColor, boolean shadow, Font font, ScissorBox scissor) {
        if (!collecting) return;
        List<ColoredTextSegment> segments = extractStyledSegments(text.getVisualOrderText(), defaultColor);
        if (!segments.isEmpty()) {
            textQueue.add(new QueuedText(segments, x, y, size, shadow, font, scissor));
        }
    }

    /**
     * Queue styled text from OrderedText (extracts colors from Style)
     */
    public static void queueStyledText(FormattedCharSequence text, float x, float y, float size, int defaultColor, boolean shadow, Font font, ScissorBox scissor) {
        if (!collecting) return;
        List<ColoredTextSegment> segments = extractStyledSegments(text, defaultColor);
        if (!segments.isEmpty()) {
            textQueue.add(new QueuedText(segments, x, y, size, shadow, font, scissor));
        }
    }

    /**
     * Extract styled text segments from OrderedText
     * This visits each character and extracts colors from the Style objects
     * OPTIMIZED: Uses reusable visitor and buffers to avoid allocations
     */
    private static List<ColoredTextSegment> extractStyledSegments(FormattedCharSequence orderedText, int defaultColor) {
        // OPTIMIZED: Reuse buffers instead of allocating new objects
        SEGMENT_BUFFER.clear();
        REUSABLE_VISITOR.reset(defaultColor);

        // Use pooled visitor instead of lambda
        orderedText.accept(REUSABLE_VISITOR);

        // Flush remaining text from visitor
        if (REUSABLE_STRING_BUILDER.length() > 0) {
            SEGMENT_BUFFER.add(new ColoredTextSegment(REUSABLE_STRING_BUILDER.toString(), REUSABLE_COLOR_HOLDER[0]));
        }

        // Parse § color codes in segment text (Hypixel sends raw § codes in scoreboard)
        // OPTIMIZED: Reuse final segments buffer
        FINAL_SEGMENTS_BUFFER.clear();
        for (ColoredTextSegment seg : SEGMENT_BUFFER) {
            // Use CustomFontColorParser to handle § codes
            List<ColoredTextSegment> parsed = CustomFontColorParser.parse(seg.text, seg.color);
            FINAL_SEGMENTS_BUFFER.addAll(parsed);
        }

        // Return a copy since we reuse the buffer (QueuedText stores the list)
        return new ArrayList<>(FINAL_SEGMENTS_BUFFER);
    }

    public static void renderAll(int framebufferWidth, int framebufferHeight) {
        if (textQueue.isEmpty()) {
            collecting = false;
            return;
        }

        try {
            // Start NVG frame once for all text
            NVGRenderer.beginFrame(framebufferWidth, framebufferHeight);

            // Save GL state
            boolean depthTest = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
            boolean stencilTest = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_STENCIL_TEST);
            boolean blend = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_BLEND);

            // Set GL state for ALWAYS-ON-TOP rendering
            org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
            org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_STENCIL_TEST);
            org.lwjgl.opengl.GL11.glDepthMask(false);
            org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_BLEND);
            org.lwjgl.opengl.GL11.glBlendFunc(org.lwjgl.opengl.GL11.GL_SRC_ALPHA, org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA);

            // Get NVG context for scissor
            long vg = NVGRenderer.getVg();

            // Render all queued text with NVG scissor clipping
            for (QueuedText qt : textQueue) {
                // Apply NVG scissor clipping for this text (with Y flip for NVG coordinate system)
                if (qt.scissor != null) {
                    qt.scissor.applyNVG(vg, framebufferHeight);
                }

                // Render each colored segment
                float currentX = qt.x;
                for (ColoredTextSegment segment : qt.segments) {
                    if (segment.text.isEmpty()) continue;

                    // Convert RGB color to ARGB (add full alpha)
                    int argbColor = 0xFF000000 | segment.color;

                    if (qt.shadow) {
                        NVGRenderer.textShadow(segment.text, currentX, qt.y, qt.size, argbColor, qt.font);
                    } else {
                        NVGRenderer.text(segment.text, currentX, qt.y, qt.size, argbColor, qt.font);
                    }

                    // Advance X position by the width of this segment
                    float segmentWidth = NVGRenderer.textWidth(segment.text, qt.size, qt.font);
                    currentX += segmentWidth;
                }
            }

            // Reset NVG scissor after rendering
            org.lwjgl.nanovg.NanoVG.nvgResetScissor(vg);

            // Restore GL state
            if (depthTest) org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
            if (stencilTest) org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_STENCIL_TEST);
            if (!blend) org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_BLEND);
            org.lwjgl.opengl.GL11.glDepthMask(true);

            // End NVG frame
            NVGRenderer.endFrame();
        } catch (Exception e) {
            System.err.println("[CustomFontRenderQueue] Error rendering queued text: " + e.getMessage());
            e.printStackTrace();
        } finally {
            textQueue.clear();
            collecting = false;
        }
    }

    public static boolean isCollecting() {
        return collecting;
    }

    public static void clear() {
        textQueue.clear();
        collecting = false;
    }
}
