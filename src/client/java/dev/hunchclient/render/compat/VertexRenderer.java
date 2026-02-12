package dev.hunchclient.render.compat;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

/**
 * Simple VertexRenderer for 1.21.10
 * Provides basic box rendering without complex abstractions
 */
public class VertexRenderer {

    /**
     * Draw an outlined box
     */
    public static void drawBox(PoseStack matrices, VertexConsumer consumer,
                               double x1, double y1, double z1,
                               double x2, double y2, double z2,
                               float red, float green, float blue, float alpha) {
        Matrix4f matrix = matrices.last().pose();

        float minX = (float) x1;
        float minY = (float) y1;
        float minZ = (float) z1;
        float maxX = (float) x2;
        float maxY = (float) y2;
        float maxZ = (float) z2;

        // Draw 12 lines for a box outline
        // Bottom face
        drawLine(consumer, matrix, minX, minY, minZ, maxX, minY, minZ, red, green, blue, alpha);
        drawLine(consumer, matrix, maxX, minY, minZ, maxX, minY, maxZ, red, green, blue, alpha);
        drawLine(consumer, matrix, maxX, minY, maxZ, minX, minY, maxZ, red, green, blue, alpha);
        drawLine(consumer, matrix, minX, minY, maxZ, minX, minY, minZ, red, green, blue, alpha);

        // Top face
        drawLine(consumer, matrix, minX, maxY, minZ, maxX, maxY, minZ, red, green, blue, alpha);
        drawLine(consumer, matrix, maxX, maxY, minZ, maxX, maxY, maxZ, red, green, blue, alpha);
        drawLine(consumer, matrix, maxX, maxY, maxZ, minX, maxY, maxZ, red, green, blue, alpha);
        drawLine(consumer, matrix, minX, maxY, maxZ, minX, maxY, minZ, red, green, blue, alpha);

        // Vertical edges
        drawLine(consumer, matrix, minX, minY, minZ, minX, maxY, minZ, red, green, blue, alpha);
        drawLine(consumer, matrix, maxX, minY, minZ, maxX, maxY, minZ, red, green, blue, alpha);
        drawLine(consumer, matrix, maxX, minY, maxZ, maxX, maxY, maxZ, red, green, blue, alpha);
        drawLine(consumer, matrix, minX, minY, maxZ, minX, maxY, maxZ, red, green, blue, alpha);
    }

    /**
     * Draw an outlined box using Box object
     */
    public static void drawBox(PoseStack matrices, VertexConsumer consumer, AABB box,
                               float red, float green, float blue, float alpha) {
        drawBox(matrices, consumer, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, red, green, blue, alpha);
    }

    /**
     * Draw a filled box (Sodium compatible)
     * Delegates to vanilla VertexRendering to guarantee proper topology on 1.21+
     */
    public static void drawFilledBox(PoseStack matrices, VertexConsumer consumer,
                                     double x1, double y1, double z1,
                                     double x2, double y2, double z2,
                                     float red, float green, float blue, float alpha) {
        // Use the MatrixStack-based helper so the POSITION_COLOR debug pipeline (no normals)
        // receives the exact triangle strip ordering vanilla uses.
        ShapeRenderer.addChainedFilledBoxVertices(
            matrices,
            consumer,
            x1, y1, z1,
            x2, y2, z2,
            red, green, blue, alpha
        );
    }

    /**
     * Draw a filled box using Box object
     */
    public static void drawFilledBox(PoseStack matrices, VertexConsumer consumer, AABB box,
                                     float red, float green, float blue, float alpha) {
        drawFilledBox(matrices, consumer, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, red, green, blue, alpha);
    }

    /**
     * Draw a line between two points using the current matrix stack
     */
    public static void drawLine(PoseStack matrices, VertexConsumer consumer,
                                double x1, double y1, double z1,
                                double x2, double y2, double z2,
                                float red, float green, float blue, float alpha) {
        Matrix4f matrix = matrices.last().pose();
        drawLine(consumer, matrix, (float) x1, (float) y1, (float) z1, (float) x2, (float) y2, (float) z2, red, green, blue, alpha);
    }

    /**
     * Draw a line between two points
     */
    private static void drawLine(VertexConsumer consumer, Matrix4f matrix,
                                 float x1, float y1, float z1,
                                 float x2, float y2, float z2,
                                 float red, float green, float blue, float alpha) {
        // Calculate normal for the line
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float nx = length > 0 ? dx / length : 0;
        float ny = length > 0 ? dy / length : 1;
        float nz = length > 0 ? dz / length : 0;

        consumer.addVertex(matrix, x1, y1, z1).setColor(red, green, blue, alpha).setNormal(nx, ny, nz);
        consumer.addVertex(matrix, x2, y2, z2).setColor(red, green, blue, alpha).setNormal(nx, ny, nz);
    }

    /**
     * Draw a quad (4 vertices) - Sodium compatible
     */
    private static void quad(VertexConsumer consumer, Matrix4f matrix,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             float x4, float y4, float z4,
                             float red, float green, float blue, float alpha,
                             float nx, float ny, float nz) {
        consumer.addVertex(matrix, x1, y1, z1).setColor(red, green, blue, alpha).setNormal(nx, ny, nz);
        consumer.addVertex(matrix, x2, y2, z2).setColor(red, green, blue, alpha).setNormal(nx, ny, nz);
        consumer.addVertex(matrix, x3, y3, z3).setColor(red, green, blue, alpha).setNormal(nx, ny, nz);
        consumer.addVertex(matrix, x4, y4, z4).setColor(red, green, blue, alpha).setNormal(nx, ny, nz);
    }

    /**
     * Submit a single vertex (for custom rendering)
     */
    public static void vertex(BufferBuilder buffer, PoseStack matrices,
                             float x, float y, float z,
                             float r, float g, float b, float a,
                             float nx, float ny, float nz) {
        buffer.addVertex(matrices.last().pose(), x, y, z)
              .setColor(r, g, b, a)
              .setNormal(nx, ny, nz);
    }
}
