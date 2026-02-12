package dev.hunchclient.render;

import java.io.IOException;
import net.minecraft.server.packs.resources.ResourceProvider;

/**
 * Manages custom shader programs for HunchClient using Raw OpenGL
 */
public class CustomShaderManager {

    private static GlowShader glowShader = null;

    /**
     * Load custom shaders using Raw OpenGL (like GaussianBlurShader)
     */
    public static void loadShaders(ResourceProvider factory) {
        try {
            System.out.println("[CustomShaderManager] Loading raw OpenGL glow shader...");
            glowShader = new GlowShader();

            if (glowShader.isValid()) {
                System.out.println("[CustomShaderManager] Glow shader loaded successfully!");
            } else {
                System.err.println("[CustomShaderManager] Glow shader failed to compile!");
                glowShader = null;
            }
        } catch (Exception e) {
            System.err.println("[CustomShaderManager] Failed to load glow shader: " + e.getMessage());
            e.printStackTrace();
            glowShader = null;
        }
    }

    /**
     * Get the raw OpenGL glow shader
     */
    public static GlowShader getGlowShader() {
        return glowShader;
    }

    /**
     * Check if glow shader is loaded
     */
    public static boolean isGlowShaderLoaded() {
        return glowShader != null && glowShader.isValid();
    }

    /**
     * Use the glow shader and set uniforms
     */
    public static void useGlowShader(float intensity, org.joml.Matrix4f modelViewMat, org.joml.Matrix4f projMat) {
        if (isGlowShaderLoaded()) {
            glowShader.use();
            float time = (System.currentTimeMillis() % 100000) / 1000.0f;
            glowShader.setTime(time);
            glowShader.setGlowIntensity(intensity);
            glowShader.setModelViewMat(modelViewMat);
            glowShader.setProjMat(projMat);
        }
    }

    /**
     * Unbind the glow shader
     */
    public static void unbindGlowShader() {
        if (isGlowShaderLoaded()) {
            glowShader.unbind();
        }
    }

    /**
     * Cleanup shaders
     */
    public static void cleanup() {
        if (glowShader != null) {
            glowShader.cleanup();
            glowShader = null;
        }
    }
}
