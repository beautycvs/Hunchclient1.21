package dev.hunchclient.render;

import net.minecraft.client.Minecraft;

/**
 * Item Glow Renderer using Minecraft's Entity Outline System
 *
 * Instead of custom framebuffers, we leverage Minecraft's existing
 * entity_outline post-processing which already uses our Smoother Glowing shaders.
 *
 * This approach:
 * - Uses the same high-quality blur as entity ESP
 * - No GL compatibility issues
 * - Better performance (reuses existing framebuffers)
 * - Consistent with vanilla outline rendering
 */
public class ItemGlowRenderer {

    private static final Minecraft MC = Minecraft.getInstance();

    // Glow settings
    private static float glowRadius = 5.0f;
    private static float glowIntensity = 1.0f;
    private static float[] glowColor = {1.0f, 1.0f, 1.0f}; // RGB

    private static boolean shouldRenderGlow = false;

    /**
     * Initialize the item glow rendering system
     * (Minimal init - we use Minecraft's existing outline framebuffer)
     */
    public static void init() {
        // No custom framebuffers needed!
        // We piggyback on Minecraft's entity_outline system
        System.out.println("[ItemGlowRenderer] Using Minecraft's entity outline system");
    }

    /**
     * Enable item glow rendering
     * Call this before rendering the held item
     */
    public static void beginCapture() {
        shouldRenderGlow = true;
    }

    /**
     * End item glow rendering
     * Call this after rendering the held item
     */
    public static void endCaptureAndRender() {
        shouldRenderGlow = false;
    }

    /**
     * Check if item glow should be rendered
     */
    public static boolean isCapturing() {
        return shouldRenderGlow;
    }

    /**
     * Check if item glow is active
     */
    public static boolean shouldRenderGlow() {
        return shouldRenderGlow;
    }

    /**
     * Set glow parameters
     */
    public static void setGlowRadius(float radius) {
        glowRadius = radius;
    }

    public static void setGlowIntensity(float intensity) {
        glowIntensity = intensity;
    }

    public static void setGlowColor(float r, float g, float b) {
        glowColor[0] = r;
        glowColor[1] = g;
        glowColor[2] = b;
    }

    /**
     * Cleanup resources (minimal - we don't own any resources)
     */
    public static void cleanup() {
        shouldRenderGlow = false;
    }

    /**
     * Get the glow color in RGB format suitable for OutlineVertexConsumerProvider
     */
    public static int getGlowColorRGB() {
        // Convert float RGB to packed int RGB (0xRRGGBB)
        int r = (int)(glowColor[0] * 255.0f) & 0xFF;
        int g = (int)(glowColor[1] * 255.0f) & 0xFF;
        int b = (int)(glowColor[2] * 255.0f) & 0xFF;
        return (r << 16) | (g << 8) | b;
    }
}
