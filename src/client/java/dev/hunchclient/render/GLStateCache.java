package dev.hunchclient.render;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * Caches OpenGL state queries to avoid expensive GPU-CPU synchronization.
 * Call invalidate() at the start of each frame, then ensureValid() before using cached values.
 * This eliminates 6+ glGetInteger() calls per frame that cause GPU pipeline stalls.
 */
public class GLStateCache {
    // Cached OpenGL state
    private static int currentFBO = 0;
    private static int currentShader = 0;
    private static int currentVAO = 0;
    private static int currentActiveTexture = GL13.GL_TEXTURE0;
    private static boolean blendEnabled = false;
    private static boolean depthTestEnabled = true;
    private static boolean depthMaskEnabled = true;

    // Track if cache is valid for current frame
    private static boolean cacheValid = false;

    /**
     * Invalidate the cache at the start of each frame.
     * Should be called from GameRendererMixin at frame start.
     */
    public static void invalidate() {
        cacheValid = false;
    }

    /**
     * Ensure cache is valid by querying OpenGL state if needed.
     * Only performs actual queries once per frame after invalidation.
     */
    public static void ensureValid() {
        if (!cacheValid) {
            currentFBO = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
            currentShader = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            currentVAO = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
            currentActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            blendEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
            depthTestEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            depthMaskEnabled = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
            cacheValid = true;
        }
    }

    /**
     * Update cached FBO value when we change it ourselves.
     * This avoids needing to query after our own state changes.
     */
    public static void setCurrentFBO(int fbo) {
        currentFBO = fbo;
    }

    /**
     * Update cached shader value when we change it ourselves.
     */
    public static void setCurrentShader(int shader) {
        currentShader = shader;
    }

    /**
     * Update cached VAO value when we change it ourselves.
     */
    public static void setCurrentVAO(int vao) {
        currentVAO = vao;
    }

    /**
     * Update cached active texture when we change it ourselves.
     */
    public static void setCurrentActiveTexture(int texture) {
        currentActiveTexture = texture;
    }

    /**
     * Update cached blend state when we change it ourselves.
     */
    public static void setBlendEnabled(boolean enabled) {
        blendEnabled = enabled;
    }

    /**
     * Update cached depth test state when we change it ourselves.
     */
    public static void setDepthTestEnabled(boolean enabled) {
        depthTestEnabled = enabled;
    }

    /**
     * Update cached depth mask state when we change it ourselves.
     */
    public static void setDepthMaskEnabled(boolean enabled) {
        depthMaskEnabled = enabled;
    }

    // Getters for cached values
    public static int getCurrentFBO() {
        return currentFBO;
    }

    public static int getCurrentShader() {
        return currentShader;
    }

    public static int getCurrentVAO() {
        return currentVAO;
    }

    public static int getCurrentActiveTexture() {
        return currentActiveTexture;
    }

    public static boolean isBlendEnabled() {
        return blendEnabled;
    }

    public static boolean isDepthTestEnabled() {
        return depthTestEnabled;
    }

    public static boolean isDepthMaskEnabled() {
        return depthMaskEnabled;
    }
}
