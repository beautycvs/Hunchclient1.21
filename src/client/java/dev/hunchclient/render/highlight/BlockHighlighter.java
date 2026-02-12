package dev.hunchclient.render.highlight;

import dev.hunchclient.render.WorldRenderExtractionCallback;
import dev.hunchclient.render.primitive.PrimitiveCollector;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/**
 * Drop-in helper for Skyblocker-style block highlights.
 *
 * Usage:
 * <pre>
 * BlockHighlighter.HighlightHandle handle = BlockHighlighter.highlightBlock(
 *     blockPos,
 *     BlockHighlighter.options()
 *         .color(0.2f, 0.8f, 1.0f)
 *         .fillAlpha(0.25f)
 *         .outlineAlpha(1.0f)
 *         .outlinedThroughWalls(true)
 *         .lineWidth(4.0f)
 *         .build()
 * );
 *
 * // later
 * handle.remove();
 * </pre>
 */
public final class BlockHighlighter {
    private static final List<Highlight> ACTIVE = new ArrayList<>();
    private static boolean initialized;

    private BlockHighlighter() {}

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        WorldRenderExtractionCallback.EVENT.register(BlockHighlighter::extract);
    }

    public static HighlightHandle highlightBlock(BlockPos pos, HighlightOptions options) {
        return highlightBox(new AABB(pos), options);
    }

    public static HighlightHandle highlightBox(AABB box, HighlightOptions options) {
        Highlight highlight = new Highlight(box, options);
        synchronized (ACTIVE) {
            ACTIVE.add(highlight);
        }
        return new HighlightHandleImpl(highlight);
    }

    /**
     * Creates a highlight that renders exactly once (useful for transient cues).
     */
    public static void highlightOnce(AABB box, HighlightOptions options) {
        Highlight highlight = new Highlight(box, options);
        highlight.setLifetime(1);
        synchronized (ACTIVE) {
            ACTIVE.add(highlight);
        }
    }

    public static Builder options() {
        return new Builder();
    }

    private static void extract(PrimitiveCollector collector) {
        synchronized (ACTIVE) {
            Iterator<Highlight> iterator = ACTIVE.iterator();
            while (iterator.hasNext()) {
                Highlight highlight = iterator.next();

                if (highlight.isRemoved()) {
                    iterator.remove();
                    continue;
                }

                if (highlight.isVisible()) {
                    highlight.submit(collector);
                }

                if (highlight.finishFrame()) {
                    iterator.remove();
                }
            }
        }
    }

    public interface HighlightHandle {
        void remove();

        void updateBox(AABB box);

        default void updateBlock(BlockPos pos) {
            updateBox(new AABB(pos));
        }

        void setVisible(boolean visible);

        void setThroughWalls(boolean throughWalls);

        void setLineWidth(float lineWidth);

        void mutate(Consumer<MutableHighlightState> mutator);
    }

    public static final class Builder {
        private float[] fillColor = new float[]{1f, 1f, 1f};
        private float fillAlpha = 0.35f;
        private float[] outlineColor = new float[]{1f, 1f, 1f};
        private float outlineAlpha = 1f;
        private float lineWidth = 2f;
        private boolean throughWalls = false;
        private double expand = 0.0;
        private int lifetime = -1;

        public Builder color(float r, float g, float b) {
            this.fillColor = new float[]{clamp(r), clamp(g), clamp(b)};
            this.outlineColor = this.fillColor.clone();
            return this;
        }

        public Builder fillColor(float r, float g, float b) {
            this.fillColor = new float[]{clamp(r), clamp(g), clamp(b)};
            return this;
        }

        public Builder outlineColor(float r, float g, float b) {
            this.outlineColor = new float[]{clamp(r), clamp(g), clamp(b)};
            return this;
        }

        public Builder fillAlpha(float alpha) {
            this.fillAlpha = clamp(alpha);
            return this;
        }

        public Builder outlineAlpha(float alpha) {
            this.outlineAlpha = clamp(alpha);
            return this;
        }

        public Builder lineWidth(float width) {
            this.lineWidth = Math.max(0f, width);
            return this;
        }

        public Builder throughWalls(boolean throughWalls) {
            this.throughWalls = throughWalls;
            return this;
        }

        public Builder expand(double amount) {
            this.expand = amount;
            return this;
        }

        /**
         * Sets how many render extractions the highlight should live for.
         * A negative value keeps it alive until {@link HighlightHandle#remove()} is called.
         */
        public Builder lifetime(int frames) {
            this.lifetime = frames;
            return this;
        }

        public HighlightOptions build() {
            return new HighlightOptions(
                fillColor.clone(),
                fillAlpha,
                outlineColor.clone(),
                outlineAlpha,
                lineWidth,
                throughWalls,
                expand,
                lifetime
            );
        }
    }

    public static final class HighlightOptions {
        private final float[] fillColor;
        private final float fillAlpha;
        private final float[] outlineColor;
        private final float outlineAlpha;
        private final float lineWidth;
        private final boolean throughWalls;
        private final double expand;
        private final int lifetime;

        private HighlightOptions(float[] fillColor, float fillAlpha, float[] outlineColor, float outlineAlpha,
                                 float lineWidth, boolean throughWalls, double expand, int lifetime) {
            this.fillColor = fillColor;
            this.fillAlpha = fillAlpha;
            this.outlineColor = outlineColor;
            this.outlineAlpha = outlineAlpha;
            this.lineWidth = lineWidth;
            this.throughWalls = throughWalls;
            this.expand = expand;
            this.lifetime = lifetime;
        }
    }

    public interface MutableHighlightState {
        void setFillAlpha(float alpha);

        void setOutlineAlpha(float alpha);

        void setOutlineColor(float r, float g, float b);

        void setFillColor(float r, float g, float b);

        void setExpand(double expand);
    }

    private static final class Highlight implements MutableHighlightState {
        private AABB box;
        private float[] fillColor;
        private float fillAlpha;
        private float[] outlineColor;
        private float outlineAlpha;
        private float lineWidth;
        private boolean throughWalls;
        private double expand;
        private int lifetime;
        private boolean removed;
        private boolean visible = true;

        Highlight(AABB box, HighlightOptions options) {
            this.box = Objects.requireNonNull(box, "box");
            this.fillColor = options.fillColor.clone();
            this.fillAlpha = options.fillAlpha;
            this.outlineColor = options.outlineColor.clone();
            this.outlineAlpha = options.outlineAlpha;
            this.lineWidth = options.lineWidth;
            this.throughWalls = options.throughWalls;
            this.expand = options.expand;
            this.lifetime = options.lifetime;
        }

        void submit(PrimitiveCollector collector) {
            AABB renderBox = expand == 0.0 ? this.box : this.box.inflate(expand);
            if (fillAlpha > 0f) {
                collector.submitFilledBox(renderBox, fillColor, fillAlpha, throughWalls);
            }
            if (outlineAlpha > 0f && lineWidth > 0f) {
                collector.submitOutlinedBox(renderBox, outlineColor, outlineAlpha, lineWidth, throughWalls);
            }
        }

        boolean finishFrame() {
            if (removed) {
                return true;
            }
            if (lifetime < 0) {
                return false;
            }
            lifetime--;
            return lifetime <= 0;
        }

        boolean isRemoved() {
            return removed;
        }

        void remove() {
            this.removed = true;
        }

        boolean isVisible() {
            return visible;
        }

        void setVisible(boolean visible) {
            this.visible = visible;
        }

        void updateBox(AABB box) {
            this.box = Objects.requireNonNull(box, "box");
        }

        void setThroughWallsInternal(boolean throughWalls) {
            this.throughWalls = throughWalls;
        }

        void setLineWidthInternal(float lineWidth) {
            this.lineWidth = Math.max(0f, lineWidth);
        }

        void setLifetime(int frames) {
            this.lifetime = frames;
        }

        @Override
        public void setFillAlpha(float alpha) {
            this.fillAlpha = clamp(alpha);
        }

        @Override
        public void setOutlineAlpha(float alpha) {
            this.outlineAlpha = clamp(alpha);
        }

        @Override
        public void setOutlineColor(float r, float g, float b) {
            this.outlineColor = new float[]{clamp(r), clamp(g), clamp(b)};
        }

        @Override
        public void setFillColor(float r, float g, float b) {
            this.fillColor = new float[]{clamp(r), clamp(g), clamp(b)};
        }

        @Override
        public void setExpand(double expand) {
            this.expand = expand;
        }
    }

    private static final class HighlightHandleImpl implements HighlightHandle {
        private final Highlight highlight;

        private HighlightHandleImpl(Highlight highlight) {
            this.highlight = highlight;
        }

        @Override
        public void remove() {
            highlight.remove();
        }

        @Override
        public void updateBox(AABB box) {
            highlight.updateBox(box);
        }

        @Override
        public void setVisible(boolean visible) {
            highlight.setVisible(visible);
        }

        @Override
        public void setThroughWalls(boolean throughWalls) {
            highlight.setThroughWallsInternal(throughWalls);
        }

        @Override
        public void setLineWidth(float lineWidth) {
            highlight.setLineWidthInternal(lineWidth);
        }

        @Override
        public void mutate(Consumer<MutableHighlightState> mutator) {
            mutator.accept(highlight);
        }
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
