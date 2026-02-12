package dev.hunchclient.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * StarredMobRenderer - Renders ESP boxes around starred mobs
 *
 * Simple box-based ESP that works through walls using 1.21 rendering API
 */
public class StarredMobRenderer {

    /**
     * Renders a box around an entity with the specified color
     */
    public static void renderEntityBox(Entity entity, PoseStack matrices, MultiBufferSource.BufferSource vertexConsumers, Camera camera, float r, float g, float b, float a) {
        if (entity == null) return;

        Vec3 cameraPos = camera.getPosition();
        double x = entity.getX() - cameraPos.x;
        double y = entity.getY() - cameraPos.y;
        double z = entity.getZ() - cameraPos.z;

        AABB box = entity.getBoundingBox().move(-entity.getX(), -entity.getY(), -entity.getZ());

        matrices.pushPose();
        matrices.translate(x, y, z);

        // Draw box outline using RenderLayer
        drawBoxOutline(matrices, vertexConsumers, box, r, g, b, a);

        matrices.popPose();
    }

    /**
     * Draws a box outline using VertexConsumer
     */
    private static void drawBoxOutline(PoseStack matrices, MultiBufferSource.BufferSource vertexConsumers, AABB box, float r, float g, float b, float a) {
        Matrix4f matrix = matrices.last().pose();

        // Use Lines render layer (works through walls)
        VertexConsumer buffer = vertexConsumers.getBuffer(RenderType.lines());

        // Convert to ARGB color
        int alpha = (int)(a * 255);
        int red = (int)(r * 255);
        int green = (int)(g * 255);
        int blue = (int)(b * 255);
        int colorInt = (alpha << 24) | (red << 16) | (green << 8) | blue;

        float minX = (float) box.minX;
        float minY = (float) box.minY;
        float minZ = (float) box.minZ;
        float maxX = (float) box.maxX;
        float maxY = (float) box.maxY;
        float maxZ = (float) box.maxZ;

        // Bottom face (4 lines)
        buffer.addVertex(matrix, minX, minY, minZ).setColor(colorInt).setNormal(0, 0, 0);
        buffer.addVertex(matrix, maxX, minY, minZ).setColor(colorInt).setNormal(0, 0, 0);

        buffer.addVertex(matrix, maxX, minY, minZ).setColor(colorInt).setNormal(0, 0, 0);
        buffer.addVertex(matrix, maxX, minY, maxZ).setColor(colorInt).setNormal(0, 0, 0);

        buffer.addVertex(matrix, maxX, minY, maxZ).setColor(colorInt).setNormal(0, 0, 0);
        buffer.addVertex(matrix, minX, minY, maxZ).setColor(colorInt).setNormal(0, 0, 0);

        buffer.addVertex(matrix, minX, minY, maxZ).setColor(colorInt).setNormal(0, 0, 0);
        buffer.addVertex(matrix, minX, minY, minZ).setColor(colorInt).setNormal(0, 0, 0);

        // Top face (4 lines)
        buffer.addVertex(matrix, minX, maxY, minZ).setColor(colorInt).setNormal(0, 0, 0);
        buffer.addVertex(matrix, maxX, maxY, minZ).setColor(colorInt).setNormal(0, 0, 0);

        buffer.addVertex(matrix, maxX, maxY, minZ).setColor(colorInt).setNormal(0, 0, 0);
        buffer.addVertex(matrix, maxX, maxY, maxZ).setColor(colorInt).setNormal(0, 0, 0);

        buffer.addVertex(matrix, maxX, maxY, maxZ).setColor(colorInt).setNormal(0, 0, 0);
        buffer.addVertex(matrix, minX, maxY, maxZ).setColor(colorInt).setNormal(0, 0, 0);

        buffer.addVertex(matrix, minX, maxY, maxZ).setColor(colorInt).setNormal(0, 0, 0);
        buffer.addVertex(matrix, minX, maxY, minZ).setColor(colorInt).setNormal(0, 0, 0);

        // Vertical lines (4 lines)
        buffer.addVertex(matrix, minX, minY, minZ).setColor(colorInt).setNormal(0, 0, 0);
        buffer.addVertex(matrix, minX, maxY, minZ).setColor(colorInt).setNormal(0, 0, 0);

        buffer.addVertex(matrix, maxX, minY, minZ).setColor(colorInt).setNormal(0, 0, 0);
        buffer.addVertex(matrix, maxX, maxY, minZ).setColor(colorInt).setNormal(0, 0, 0);

        buffer.addVertex(matrix, maxX, minY, maxZ).setColor(colorInt).setNormal(0, 0, 0);
        buffer.addVertex(matrix, maxX, maxY, maxZ).setColor(colorInt).setNormal(0, 0, 0);

        buffer.addVertex(matrix, minX, minY, maxZ).setColor(colorInt).setNormal(0, 0, 0);
        buffer.addVertex(matrix, minX, maxY, maxZ).setColor(colorInt).setNormal(0, 0, 0);

        // Draw the buffer
        vertexConsumers.endLastBatch();
    }
}
