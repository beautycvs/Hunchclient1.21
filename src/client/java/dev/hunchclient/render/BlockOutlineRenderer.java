package dev.hunchclient.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;

/**
 * Renders block outlines with enhanced appearance.
 * SIMPLIFIED VERSION - just renders with custom color and thickness.
 */
public final class BlockOutlineRenderer {
    private static final Minecraft mc = Minecraft.getInstance();

    private boolean enabled = true;
    private float outlineWidth = 2.0f;
    private float[] outlineColor = {1.0f, 1.0f, 1.0f, 1.0f}; // White
    private float blurRadius = 4.0f;
    private float threshold = 0.3f;

    public BlockOutlineRenderer() {
    }

    /**
     * Main render method - renders enhanced block outline.
     */
    public void render(PoseStack matrices, Camera camera, MultiBufferSource.BufferSource vertexConsumers, float tickDelta) {
        if (!enabled || mc.level == null || mc.player == null) return;

        // Get the block the player is looking at
        HitResult hitResult = mc.hitResult;
        if (hitResult == null || hitResult.getType() != HitResult.Type.BLOCK) {
            return;
        }

        BlockHitResult blockHit = (BlockHitResult) hitResult;
        BlockPos pos = blockHit.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);

        if (state.isAir()) return;

        // Get block shape
        VoxelShape shape = state.getShape(mc.level, pos, CollisionContext.of(mc.player));
        if (shape.isEmpty()) return;

        // Render enhanced outline
        Vec3 cameraPos = camera.getPosition();
        matrices.pushPose();
        matrices.translate(
            pos.getX() - cameraPos.x,
            pos.getY() - cameraPos.y,
            pos.getZ() - cameraPos.z
        );

        renderEnhancedOutline(matrices, vertexConsumers, shape);

        matrices.popPose();
    }

    /**
     * Renders the block outline with custom appearance using SecondaryBlockOutline.
     * This layer has built-in thickness/blur support!
     */
    private void renderEnhancedOutline(PoseStack matrices, MultiBufferSource.BufferSource vertexConsumers, VoxelShape shape) {
        Matrix4f matrix = matrices.last().pose();

        // Use SecondaryBlockOutline layer - this has BUILT-IN blur/thickness!
        VertexConsumer thickBuffer = vertexConsumers.getBuffer(RenderType.secondaryBlockOutline());

        // Convert color to ARGB integer
        int alpha = (int)(outlineColor[3] * 255);
        int red = (int)(outlineColor[0] * 255);
        int green = (int)(outlineColor[1] * 255);
        int blue = (int)(outlineColor[2] * 255);
        int colorInt = (alpha << 24) | (red << 16) | (green << 8) | blue;

        // Draw all edges with the thick buffer
        shape.forAllEdges((minX, minY, minZ, maxX, maxY, maxZ) -> {
            float x1 = (float) minX;
            float y1 = (float) minY;
            float z1 = (float) minZ;
            float x2 = (float) maxX;
            float y2 = (float) maxY;
            float z2 = (float) maxZ;

            // Draw thick line
            thickBuffer.addVertex(matrix, x1, y1, z1).setColor(colorInt).setNormal(0, 0, 0);
            thickBuffer.addVertex(matrix, x2, y2, z2).setColor(colorInt).setNormal(0, 0, 0);
        });

        // Also draw the normal outline for contrast
        VertexConsumer normalBuffer = vertexConsumers.getBuffer(RenderType.lines());

        shape.forAllEdges((minX, minY, minZ, maxX, maxY, maxZ) -> {
            normalBuffer.addVertex(matrix, (float)minX, (float)minY, (float)minZ)
                .setColor(colorInt).setNormal(0, 0, 0);
            normalBuffer.addVertex(matrix, (float)maxX, (float)maxY, (float)maxZ)
                .setColor(colorInt).setNormal(0, 0, 0);
        });

        // Draw both layers
        vertexConsumers.endLastBatch();
    }

    // Configuration methods
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setOutlineWidth(float width) {
        this.outlineWidth = Math.max(0.5f, Math.min(10.0f, width));
    }

    public float getOutlineWidth() {
        return outlineWidth;
    }

    public void setOutlineColor(float r, float g, float b, float a) {
        this.outlineColor[0] = r;
        this.outlineColor[1] = g;
        this.outlineColor[2] = b;
        this.outlineColor[3] = a;
    }

    public float[] getOutlineColor() {
        return outlineColor;
    }

    public void setBlurRadius(float radius) {
        this.blurRadius = Math.max(1.0f, Math.min(16.0f, radius));
    }

    public float getBlurRadius() {
        return blurRadius;
    }

    public void setThreshold(float threshold) {
        this.threshold = Math.max(0.0f, Math.min(1.0f, threshold));
    }

    public float getThreshold() {
        return threshold;
    }

    public static void resize(int width, int height) {
        // No-op for now
    }
}
