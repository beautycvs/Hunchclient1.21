package dev.hunchclient.render;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Simple render context - adapted from Skyblocker's PrimitiveCollector
 * Collects rendering primitives and submits them for rendering
 */
public class RenderContext {
    private final PoseStack matrices;
    private final List<RenderPrimitive> primitives = new ArrayList<>();

    public RenderContext(PoseStack matrices) {
        this.matrices = matrices;
    }

    // Box rendering
    public void submitFilledBox(AABB box, float[] colorComponents, float alpha, boolean throughWalls) {
        primitives.add(new FilledBox(box, colorComponents, alpha, throughWalls));
    }

    public void submitOutlinedBox(AABB box, float[] colorComponents, float lineWidth, boolean throughWalls) {
        primitives.add(new OutlinedBox(box, colorComponents, lineWidth, throughWalls));
    }

    // Line rendering
    public void submitLine(Vec3 from, Vec3 to, float[] colorComponents, float alpha, float lineWidth, boolean throughWalls) {
        primitives.add(new Line(from, to, colorComponents, alpha, lineWidth, throughWalls));
    }

    // Text rendering
    public void submitText(String text, Vec3 pos, boolean throughWalls, boolean background) {
        primitives.add(new TextLabel(text, pos, throughWalls, background));
    }

    public PoseStack getMatrices() {
        return matrices;
    }

    public List<RenderPrimitive> getPrimitives() {
        return primitives;
    }

    public void clear() {
        primitives.clear();
    }

    // Primitive interfaces and classes
    public interface RenderPrimitive {
        void render(PoseStack matrices);
    }

    public record FilledBox(AABB box, float[] colorComponents, float alpha, boolean throughWalls) implements RenderPrimitive {
        @Override
        public void render(PoseStack matrices) {
            // TODO: Implement actual rendering
        }
    }

    public record OutlinedBox(AABB box, float[] colorComponents, float lineWidth, boolean throughWalls) implements RenderPrimitive {
        @Override
        public void render(PoseStack matrices) {
            // TODO: Implement actual rendering
        }
    }

    public record Line(Vec3 from, Vec3 to, float[] colorComponents, float alpha, float lineWidth, boolean throughWalls) implements RenderPrimitive {
        @Override
        public void render(PoseStack matrices) {
            // TODO: Implement actual rendering
        }
    }

    public record TextLabel(String text, Vec3 pos, boolean throughWalls, boolean background) implements RenderPrimitive {
        @Override
        public void render(PoseStack matrices) {
            // TODO: Implement actual rendering
        }
    }
}
