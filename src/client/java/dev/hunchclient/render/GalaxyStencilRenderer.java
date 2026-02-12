package dev.hunchclient.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * Stencil-based galaxy renderer for items
 *
 * Flow:
 * 1. beginStencilWrite() - Enable stencil, disable color
 * 2. [Item renders normally] - Writes to stencil buffer
 * 3. endStencilWriteBeginGalaxyRender() - Enable color, set stencil test
 * 4. renderGalaxyQuad() - Render galaxy only where stencil == 1
 * 5. cleanup() - Disable stencil
 */
public class GalaxyStencilRenderer {

    private static final Minecraft client = Minecraft.getInstance();

    /**
     * Step 1: Enable stencil buffer writing while KEEPING color writing enabled
     * This allows the item to render normally AND write to stencil simultaneously
     * Call BEFORE item renders
     */
    public static void beginStencilWrite() {
        RenderSystem.assertOnRenderThread();

        // Enable stencil testing
        GL11.glEnable(GL11.GL_STENCIL_TEST);

        // Clear stencil buffer to 0
        GL11.glClearStencil(0);
        GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);

        // Stencil function: always pass (render everything, but write to stencil)
        GL11.glStencilFunc(GL11.GL_ALWAYS, 1, 0xFF);

        // Stencil operation: write 1 where item renders (depth test passes)
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);

        // Enable writing to stencil buffer
        GL11.glStencilMask(0xFF);

        // KEEP color writing ENABLED - item renders normally AND writes to stencil!
        // GL11.glColorMask(false, false, false, false); // DISABLED - we want the item visible!

        System.out.println("[GalaxyStencilRenderer] Stencil write mode ENABLED (color still active)");
    }

    /**
     * Step 2: Configure stencil test for overlay rendering
     * Call AFTER item renders to stencil
     */
    public static void endStencilWriteBeginGalaxyRender() {
        RenderSystem.assertOnRenderThread();

        // Color writing is already enabled - no change needed

        // Stencil function: only render where stencil == 1 (where item was rendered)
        GL11.glStencilFunc(GL11.GL_EQUAL, 1, 0xFF);

        // Stencil operation: don't modify stencil anymore
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);

        // Disable stencil writing
        GL11.glStencilMask(0x00);

        System.out.println("[GalaxyStencilRenderer] Overlay render mode ENABLED (only where stencil == 1)");
    }

    /**
     * Step 3a: Render overlay quad using a MatrixStack.Entry (from ItemCommand)
     * This renders in screen space where stencil == 1
     */
    public static void renderGalaxyQuadScreenSpace(PoseStack.Pose positionMatrix) {
        RenderSystem.assertOnRenderThread();

        ResourceLocation overlayTexture = OverlayTextureManager.getTextureId();
        if (overlayTexture == null || !OverlayTextureManager.hasTexture()) {
            System.err.println("[GalaxyStencilRenderer] No overlay texture!");
            return;
        }

        try {
            // Get parallax offset from player camera
            float uvOffsetX = 0.0f;
            float uvOffsetY = 0.0f;
            if (client.player != null) {
                uvOffsetX = (client.player.getYRot() % 360.0f) / 360.0f * 0.2f;
                uvOffsetY = (client.player.getXRot() + 90.0f) / 180.0f * 0.1f;
            }

            // Use RenderLayer to render the overlay quad
            RenderType overlayLayer = RenderType.entityCutout(overlayTexture);

            // Get vertex consumer
            net.minecraft.client.renderer.MultiBufferSource.BufferSource immediate = client.renderBuffers().bufferSource();
            com.mojang.blaze3d.vertex.VertexConsumer vertexConsumer = immediate.getBuffer(overlayLayer);

            // Large quad that covers the entire view (will be clipped by stencil)
            float x1 = -2.0f;
            float y1 = -2.0f;
            float x2 = 2.0f;
            float y2 = 2.0f;
            float z = 0.0f;

            // Normal pointing towards camera
            float nx = 0.0f, ny = 0.0f, nz = 1.0f;

            // Add vertices with parallax-offset UVs using the provided position matrix
            // Note: Entity vertex format requires: position, color, texture (UV0), overlay (UV1), light (UV2), normal
            vertexConsumer.addVertex(positionMatrix, x1, y2, z)
                .setColor(255, 255, 255, 255)
                .setUv(0.0f + uvOffsetX, 1.0f + uvOffsetY)
                .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .setLight(0xF000F0)
                .setNormal(positionMatrix, nx, ny, nz);

            vertexConsumer.addVertex(positionMatrix, x2, y2, z)
                .setColor(255, 255, 255, 255)
                .setUv(1.0f + uvOffsetX, 1.0f + uvOffsetY)
                .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .setLight(0xF000F0)
                .setNormal(positionMatrix, nx, ny, nz);

            vertexConsumer.addVertex(positionMatrix, x2, y1, z)
                .setColor(255, 255, 255, 255)
                .setUv(1.0f + uvOffsetX, 0.0f + uvOffsetY)
                .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .setLight(0xF000F0)
                .setNormal(positionMatrix, nx, ny, nz);

            vertexConsumer.addVertex(positionMatrix, x1, y1, z)
                .setColor(255, 255, 255, 255)
                .setUv(0.0f + uvOffsetX, 0.0f + uvOffsetY)
                .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .setLight(0xF000F0)
                .setNormal(positionMatrix, nx, ny, nz);

            // Draw it!
            immediate.endBatch();

            System.out.println("[GalaxyStencilRenderer] ✅ Overlay quad (screen space) rendered!");
        } catch (Exception e) {
            System.err.println("[GalaxyStencilRenderer] Render error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Step 3b: Render overlay fullscreen quad (only draws where stencil == 1)
     */
    public static void renderGalaxyQuad(PoseStack matrices) {
        RenderSystem.assertOnRenderThread();

        // Use OverlayTextureManager instead of GalaxyTextureReplacer
        ResourceLocation overlayTexture = OverlayTextureManager.getTextureId();
        if (overlayTexture == null || !OverlayTextureManager.hasTexture()) {
            System.err.println("[GalaxyStencilRenderer] No overlay texture!");
            return;
        }

        try {
            // Get parallax offset from player camera
            float uvOffsetX = 0.0f;
            float uvOffsetY = 0.0f;
            if (client.player != null) {
                uvOffsetX = (client.player.getYRot() % 360.0f) / 360.0f * 0.2f;
                uvOffsetY = (client.player.getXRot() + 90.0f) / 180.0f * 0.1f;
            }

            // Use RenderLayer to render the overlay quad
            RenderType overlayLayer = RenderType.entityCutout(overlayTexture);

            // Get vertex consumer
            net.minecraft.client.renderer.MultiBufferSource.BufferSource immediate = client.renderBuffers().bufferSource();
            com.mojang.blaze3d.vertex.VertexConsumer vertexConsumer = immediate.getBuffer(overlayLayer);

            PoseStack.Pose entry = matrices.last();

            // Fullscreen quad (covers entire viewport)
            float x1 = -2.0f;
            float y1 = -2.0f;
            float x2 = 2.0f;
            float y2 = 2.0f;
            float z = 0.0f;

            // Normal pointing towards camera
            float nx = 0.0f, ny = 0.0f, nz = 1.0f;

            // Add vertices with parallax-offset UVs
            // Note: Entity vertex format requires: position, color, texture (UV0), overlay (UV1), light (UV2), normal
            vertexConsumer.addVertex(entry, x1, y2, z)
                .setColor(255, 255, 255, 255)
                .setUv(0.0f + uvOffsetX, 1.0f + uvOffsetY)
                .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .setLight(0xF000F0)
                .setNormal(entry, nx, ny, nz);

            vertexConsumer.addVertex(entry, x2, y2, z)
                .setColor(255, 255, 255, 255)
                .setUv(1.0f + uvOffsetX, 1.0f + uvOffsetY)
                .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .setLight(0xF000F0)
                .setNormal(entry, nx, ny, nz);

            vertexConsumer.addVertex(entry, x2, y1, z)
                .setColor(255, 255, 255, 255)
                .setUv(1.0f + uvOffsetX, 0.0f + uvOffsetY)
                .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .setLight(0xF000F0)
                .setNormal(entry, nx, ny, nz);

            vertexConsumer.addVertex(entry, x1, y1, z)
                .setColor(255, 255, 255, 255)
                .setUv(0.0f + uvOffsetX, 0.0f + uvOffsetY)
                .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .setLight(0xF000F0)
                .setNormal(entry, nx, ny, nz);

            // Draw it!
            immediate.endBatch();

            System.out.println("[GalaxyStencilRenderer] ✅ Overlay quad rendered!");
        } catch (Exception e) {
            System.err.println("[GalaxyStencilRenderer] Render error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Step 4: Cleanup stencil state
     * Call AFTER galaxy rendering is complete
     */
    public static void cleanup() {
        RenderSystem.assertOnRenderThread();

        // Disable stencil test
        GL11.glDisable(GL11.GL_STENCIL_TEST);

        // Reset stencil mask
        GL11.glStencilMask(0xFF);

        // Ensure color mask is enabled
        GL11.glColorMask(true, true, true, true);

        System.out.println("[GalaxyStencilRenderer] Stencil cleanup done");
    }
}
