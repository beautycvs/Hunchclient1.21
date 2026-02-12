package dev.hunchclient.render.primitive;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Interface for collecting rendering primitives
 * Based on Skyblocker's PrimitiveCollector
 */
public interface PrimitiveCollector {

    void submitFilledBox(BlockPos pos, float[] colourComponents, float alpha, boolean throughWalls);

    void submitFilledBox(Vec3 pos, Vec3 dimensions, float[] colourComponents, float alpha, boolean throughWalls);

    void submitFilledBox(AABB box, float[] colourComponents, float alpha, boolean throughWalls);

    void submitOutlinedBox(BlockPos pos, float[] colourComponents, float lineWidth, boolean throughWalls);

    void submitOutlinedBox(AABB box, float[] colourComponents, float lineWidth, boolean throughWalls);

    void submitOutlinedBox(AABB box, float[] colourComponents, float alpha, float lineWidth, boolean throughWalls);

    void submitLinesFromPoints(Vec3[] points, float[] colourComponents, float alpha, float lineWidth, boolean throughWalls);

    // Legacy API compatibility - for modules that still use the old method names
    default void filledBox(AABB box, float red, float green, float blue, float alpha, boolean throughWalls) {
        submitFilledBox(box, new float[]{red, green, blue}, alpha, throughWalls);
    }

    default void outlinedBox(AABB box, float red, float green, float blue, float alpha, float lineWidth, boolean throughWalls) {
        submitOutlinedBox(box, new float[]{red, green, blue, alpha}, lineWidth, throughWalls);
    }

    default void line(double x1, double y1, double z1, double x2, double y2, double z2,
                     float red, float green, float blue, float alpha, float lineWidth, boolean throughWalls) {
        // Convert to Vec3d array
        Vec3[] points = new Vec3[]{new Vec3(x1, y1, z1), new Vec3(x2, y2, z2)};
        submitLinesFromPoints(points, new float[]{red, green, blue}, alpha, lineWidth, throughWalls);
    }
}
