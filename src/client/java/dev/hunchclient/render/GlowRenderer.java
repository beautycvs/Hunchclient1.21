package dev.hunchclient.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OutlineBufferSource;

/**
 * Manages custom glow rendering for entities.
 * Based on Skyblocker's GlowRenderer system.
 */
public class GlowRenderer {
    private static GlowRenderer instance;

    private final Minecraft client;
    private RenderTarget glowFramebuffer;
    private OutlineBufferSource glowVertexConsumers;

    private GlowRenderer() {
        this.client = Minecraft.getInstance();
    }

    /**
     * Get the singleton instance
     * @return The GlowRenderer instance
     */
    public static GlowRenderer getInstance() {
        if (instance == null) {
            instance = new GlowRenderer();
        }
        return instance;
    }

    /**
     * Get or create the glow framebuffer
     * @return The glow framebuffer
     */
    public RenderTarget getGlowFramebuffer() {
        if (glowFramebuffer == null || glowFramebuffer.width != client.getWindow().getWidth()
                || glowFramebuffer.height != client.getWindow().getHeight()) {
            if (glowFramebuffer != null) {
                glowFramebuffer.destroyBuffers();
            }
            glowFramebuffer = new com.mojang.blaze3d.pipeline.TextureTarget(
                "Custom Glow",
                client.getWindow().getWidth(),
                client.getWindow().getHeight(),
                true
            );
        }
        return glowFramebuffer;
    }

    /**
     * Get the glow vertex consumers
     * @return The OutlineVertexConsumerProvider for custom glow
     */
    public OutlineBufferSource getGlowVertexConsumers() {
        if (glowVertexConsumers == null) {
            glowVertexConsumers = client.renderBuffers().outlineBufferSource();
        }
        return glowVertexConsumers;
    }

    /**
     * Update the glow depth texture depth
     * This is called during rendering to ensure proper depth handling
     */
    public void updateGlowDepthTexDepth() {
        // This method would handle depth buffer updates if needed
        // For now, we'll use the default depth handling
    }

    /**
     * Clean up resources
     */
    public void cleanup() {
        if (glowFramebuffer != null) {
            glowFramebuffer.destroyBuffers();
            glowFramebuffer = null;
        }
        glowVertexConsumers = null;
    }
}
