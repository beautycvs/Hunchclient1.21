package dev.hunchclient.render;

import dev.hunchclient.render.compat.VertexRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * Renders etherwarp highlights using Minecraft's native outline post-processing pipeline.
 * We draw a white mask into the entity-outline framebuffer and let vanilla blur/composite it.
 */
public final class EtherwarpGlowRenderer {

    private EtherwarpGlowRenderer() {
    }

    /**
     * Queue a glowing outline around the provided box.
     *
     * @param matrices   active matrix stack
     * @param box        block-space bounding box
     * @param rgba       target color (0-1)
     * @param intensity  scales alpha for stronger glow
     * @param spread     number of extra outline shells for thicker glow
     */
    public static void render(PoseStack matrices, AABB box, float[] rgba, float intensity, float spread) {
        Minecraft client = Minecraft.getInstance();
        OutlineBufferSource outlineProvider = client.renderBuffers().outlineBufferSource();

        int r = (int) (clamp01(rgba[0]) * 255.0f);
        int g = (int) (clamp01(rgba[1]) * 255.0f);
        int b = (int) (clamp01(rgba[2]) * 255.0f);
        int a = (int) (clamp01(rgba[3] * Math.max(0.4f, intensity)) * 255.0f);
        // TODO 1.21.10: OutlineVertexConsumerProvider.setColor signature changed - comment out for now
        // outlineProvider.setColor(r, g, b, a);

        float lineWidth = 2.0f + spread * 2.0f;
        RenderSystem.lineWidth(lineWidth);
        GlStateManager._disableDepthTest();

        int shells = Math.max(1, 1 + (int) Math.floor(spread * 2.0f));
        VertexConsumer lines = outlineProvider.getBuffer(RenderType.lines());

        for (int i = shells; i >= 0; i--) {
            float factor = i / (float) shells;
            float expansion = factor * 0.015f * (1.0f + spread);
            AABB expanded = box.inflate(expansion);
            // Using VertexRenderer for 1.21.10+ compatibility
            VertexRenderer.drawBox(matrices, lines, expanded, 1.0f, 1.0f, 1.0f, 1.0f);
        }

        outlineProvider.endOutlineBatch();

        GlStateManager._enableDepthTest();
        RenderSystem.lineWidth(1.0f);
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
