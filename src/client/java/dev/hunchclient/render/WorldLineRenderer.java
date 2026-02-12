package dev.hunchclient.render;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * 3D Line Renderer using Minecraft's built-in rendering system
 * Based on Krypt's Render3D approach
 *
 * PERFORMANCE: Use beginBatch/addLine/endBatch for multiple lines!
 */
public class WorldLineRenderer {

    private static final Minecraft MC = Minecraft.getInstance();

    // Current render context (set per frame)
    private static PoseStack currentMatrixStack = null;
    private static MultiBufferSource currentConsumers = null;
    private static Vec3 currentCameraPos = Vec3.ZERO;

    // Batching state
    private static boolean isBatching = false;
    private static VertexConsumer batchBuffer = null;
    private static PoseStack.Pose batchEntry = null;
    private static boolean batchThroughWalls = false;

    /**
     * Set the current render context. Call this once per frame before drawing lines.
     */
    public static void setContext(PoseStack matrixStack, MultiBufferSource consumers, Vec3 cameraPos) {
        currentMatrixStack = matrixStack;
        currentConsumers = consumers;
        currentCameraPos = cameraPos;
    }

    /**
     * Begin batched line rendering - much faster for many lines!
     * Call addLine() for each line, then endBatch() when done.
     *
     * @param throughWalls Whether to render through walls
     * @param thickness Line thickness
     */
    public static void beginBatch(boolean throughWalls, float thickness) {
        if (currentMatrixStack == null || currentConsumers == null) return;
        if (MC.level == null || MC.player == null) return;

        isBatching = true;
        batchThroughWalls = throughWalls;

        if (throughWalls) {
            GlStateManager._disableDepthTest();
        }

        currentMatrixStack.pushPose();
        currentMatrixStack.translate(-currentCameraPos.x, -currentCameraPos.y, -currentCameraPos.z);

        batchEntry = currentMatrixStack.last();

        MultiBufferSource.BufferSource immediate = (MultiBufferSource.BufferSource) currentConsumers;
        batchBuffer = immediate.getBuffer(RenderType.lines());

        RenderSystem.lineWidth(thickness);
    }

    /**
     * Add a line to the current batch. Must call beginBatch() first!
     */
    public static void addLine(Vec3 start, Vec3 end, float r, float g, float b, float a) {
        if (!isBatching || batchBuffer == null || batchEntry == null) return;

        Vector3f direction = end.subtract(start).normalize().toVector3f();

        batchBuffer.addVertex(batchEntry, (float) start.x, (float) start.y, (float) start.z)
            .setColor(r, g, b, a)
            .setNormal(batchEntry, direction);

        batchBuffer.addVertex(batchEntry, (float) end.x, (float) end.y, (float) end.z)
            .setColor(r, g, b, a)
            .setNormal(batchEntry, direction);
    }

    /**
     * End batched rendering and flush all lines at once.
     */
    public static void endBatch() {
        if (!isBatching) return;

        MultiBufferSource.BufferSource immediate = (MultiBufferSource.BufferSource) currentConsumers;
        immediate.endBatch(RenderType.lines());

        currentMatrixStack.popPose();

        if (batchThroughWalls) {
            GlStateManager._enableDepthTest();
        }

        isBatching = false;
        batchBuffer = null;
        batchEntry = null;
    }

    /**
     * Draw a single 3D line (non-batched, slower for many lines)
     */
    public static void drawLine(Vec3 start, Vec3 end, float thickness, float r, float g, float b, float a) {
        if (currentMatrixStack == null || currentConsumers == null) return;
        if (MC.level == null || MC.player == null) return;

        PoseStack matrices = currentMatrixStack;

        matrices.pushPose();
        matrices.translate(-currentCameraPos.x, -currentCameraPos.y, -currentCameraPos.z);

        PoseStack.Pose entry = matrices.last();

        MultiBufferSource.BufferSource immediate = (MultiBufferSource.BufferSource) currentConsumers;
        VertexConsumer buffer = immediate.getBuffer(RenderType.lines());

        RenderSystem.lineWidth(thickness);

        Vector3f direction = end.subtract(start).normalize().toVector3f();

        buffer.addVertex(entry, (float) start.x, (float) start.y, (float) start.z)
            .setColor(r, g, b, a)
            .setNormal(entry, direction);

        buffer.addVertex(entry, (float) end.x, (float) end.y, (float) end.z)
            .setColor(r, g, b, a)
            .setNormal(entry, direction);

        immediate.endBatch(RenderType.lines());

        matrices.popPose();
    }

    /**
     * Draw a 3D line with depth testing disabled (renders through walls)
     */
    public static void drawLineThroughWalls(Vec3 start, Vec3 end, float thickness, float r, float g, float b, float a) {
        GlStateManager._disableDepthTest();
        drawLine(start, end, thickness, r, g, b, a);
        GlStateManager._enableDepthTest();
    }

    /**
     * Draw a line with int color (ARGB format)
     */
    public static void drawLine(Vec3 start, Vec3 end, float thickness, int color) {
        float a = ((color >> 24) & 0xFF) / 255f;
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        drawLine(start, end, thickness, r, g, b, a);
    }
}
