package dev.hunchclient.render.examples;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.hunchclient.render.compat.VertexRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Example: How to highlight blocks in Minecraft using VertexRenderer
 *
 * This works on ALL Minecraft versions including 1.21.10+
 */
public class BlockHighlightExample {

    /**
     * Highlight a single block with an outlined box
     *
     * @param matrices MatrixStack from world render event
     * @param consumers VertexConsumerProvider from world render event
     * @param cameraPos Camera position (camera.getPos())
     * @param blockPos Position of the block to highlight
     * @param red Red color (0.0-1.0)
     * @param green Green color (0.0-1.0)
     * @param blue Blue color (0.0-1.0)
     * @param alpha Alpha/transparency (0.0-1.0)
     */
    public static void highlightBlock(PoseStack matrices,
                                     MultiBufferSource consumers,
                                     Vec3 cameraPos,
                                     BlockPos blockPos,
                                     float red, float green, float blue, float alpha) {
        matrices.pushPose();

        // Translate to camera-relative coordinates
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        // Create box around the block
        AABB box = new AABB(blockPos);

        // Get line buffer for rendering
        VertexConsumer buffer = consumers.getBuffer(RenderType.lines());

        // Draw the outline using VertexRenderer
        VertexRenderer.drawBox(matrices, buffer, box, red, green, blue, alpha);

        matrices.popPose();
    }

    /**
     * Highlight a block with a filled box
     */
    public static void highlightBlockFilled(PoseStack matrices,
                                           MultiBufferSource consumers,
                                           Vec3 cameraPos,
                                           BlockPos blockPos,
                                           float red, float green, float blue, float alpha) {
        matrices.pushPose();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        AABB box = new AABB(blockPos);

        // Get filled box buffer
        VertexConsumer buffer = consumers.getBuffer(RenderType.debugFilledBox());

        // Draw filled box
        VertexRenderer.drawFilledBox(matrices, buffer, box, red, green, blue, alpha);

        matrices.popPose();
    }

    /**
     * Highlight a block with both outline and fill
     */
    public static void highlightBlockBoth(PoseStack matrices,
                                         MultiBufferSource consumers,
                                         Vec3 cameraPos,
                                         BlockPos blockPos,
                                         float red, float green, float blue,
                                         float fillAlpha, float outlineAlpha) {
        // Draw filled box first
        highlightBlockFilled(matrices, consumers, cameraPos, blockPos, red, green, blue, fillAlpha);

        // Then draw outline on top
        highlightBlock(matrices, consumers, cameraPos, blockPos, red, green, blue, outlineAlpha);
    }

    /**
     * Highlight multiple blocks at once
     */
    public static void highlightBlocks(PoseStack matrices,
                                      MultiBufferSource consumers,
                                      Vec3 cameraPos,
                                      Iterable<BlockPos> blocks,
                                      float red, float green, float blue, float alpha) {
        for (BlockPos pos : blocks) {
            highlightBlock(matrices, consumers, cameraPos, pos, red, green, blue, alpha);
        }
    }

    /**
     * Highlight a custom box (not aligned to blocks)
     */
    public static void highlightCustomBox(PoseStack matrices,
                                         MultiBufferSource consumers,
                                         Vec3 cameraPos,
                                         double x1, double y1, double z1,
                                         double x2, double y2, double z2,
                                         float red, float green, float blue, float alpha) {
        matrices.pushPose();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        VertexConsumer buffer = consumers.getBuffer(RenderType.lines());
        VertexRenderer.drawBox(matrices, buffer, x1, y1, z1, x2, y2, z2, red, green, blue, alpha);

        matrices.popPose();
    }

    /**
     * Draw a line between two points
     */
    public static void drawLine(PoseStack matrices,
                               MultiBufferSource consumers,
                               Vec3 cameraPos,
                               Vec3 start, Vec3 end,
                               float red, float green, float blue, float alpha) {
        matrices.pushPose();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        VertexConsumer buffer = consumers.getBuffer(RenderType.lines());
        VertexRenderer.drawLine(matrices, buffer,
                               start.x, start.y, start.z,
                               end.x, end.y, end.z,
                               red, green, blue, alpha);

        matrices.popPose();
    }
}
