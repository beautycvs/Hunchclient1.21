package dev.hunchclient.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * Dark Mode Shader Renderer
 * Applies a color tint overlay with advanced effects
 */
public class DarkModeRenderer {

    private static DarkTintShader tintShader;
    private static boolean initialized = false;

    // Full-screen quad VAO for rendering overlay
    private static int quadVAO;
    private static int quadVBO;

    // Temporary texture to avoid feedback loop
    private static int tempTexture = 0;
    private static int tempWidth = 0;
    private static int tempHeight = 0;

    // Validation tracking (don't validate every frame, too expensive)
    private static long lastValidationTime = 0;
    private static final long VALIDATION_INTERVAL_MS = 5000; // Validate every 5 seconds

    // Resize handling - skip frames during resize to avoid garbage
    private static int lastFbWidth = 0;
    private static int lastFbHeight = 0;
    private static int resizeSkipFrames = 0;

    // Settings
    private static float tintR = 0.2f;
    private static float tintG = 0.1f;
    private static float tintB = 0.3f;
    private static float tintA = 1.0f;
    private static float intensity = 0.6f;
    private static int blendMode = 0; // 0=Multiply, 1=Overlay, 2=Additive, 3=Screen
    private static float vignetteStrength = 0.0f;
    private static float saturation = 1.0f;
    private static float contrast = 1.0f;
    private static float chromaticAberration = 0.0f;
    private static float brightness = 1.5f; // Default 1.5x brightness to compensate for tint darkening

    // Depth-based viewmodel exclusion settings
    private static boolean excludeViewmodel = true; // Enable by default
    private static float depthThreshold = 0.15f; // Viewmodel depth threshold (0.0-1.0, typical viewmodel is 0.0-0.15)

    /**
     * Initialize the dark mode renderer
     */
    public static void init() {
        if (initialized) return;

        try {
            // Load shader
            tintShader = new DarkTintShader();

            // Create fullscreen quad
            createFullscreenQuad();

            initialized = true;
            // Successfully initialized
        } catch (Exception e) {
            System.err.println("[DarkModeRenderer] Failed to initialize: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Create a full-screen quad VAO for rendering overlay
     */
    private static void createFullscreenQuad() {
        float[] vertices = {
            // positions   // texCoords
            -1.0f,  1.0f,  0.0f, 1.0f,
            -1.0f, -1.0f,  0.0f, 0.0f,
             1.0f, -1.0f,  1.0f, 0.0f,

            -1.0f,  1.0f,  0.0f, 1.0f,
             1.0f, -1.0f,  1.0f, 0.0f,
             1.0f,  1.0f,  1.0f, 1.0f
        };

        quadVAO = GL30.glGenVertexArrays();
        quadVBO = GL20.glGenBuffers();

        GL30.glBindVertexArray(quadVAO);
        GL20.glBindBuffer(GL20.GL_ARRAY_BUFFER, quadVBO);
        GL20.glBufferData(GL20.GL_ARRAY_BUFFER, vertices, GL20.GL_STATIC_DRAW);

        // Position attribute
        GL30.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 4 * Float.BYTES, 0);

        // TexCoord attribute
        GL30.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 4 * Float.BYTES, 2 * Float.BYTES);

        GL30.glBindVertexArray(0);
    }

    /**
     * Create or resize temporary texture to match framebuffer size
     */
    private static void ensureTempTexture(int width, int height) {
        // Create texture if needed
        if (tempTexture == 0) {
            tempTexture = GL11.glGenTextures();
        }

        // Resize if needed
        if (tempWidth != width || tempHeight != height) {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, tempTexture);
            // Use RGBA to match Minecraft's framebuffer format exactly
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            // Use GL_CLAMP_TO_EDGE (GL_CLAMP is deprecated and can cause edge artifacts)
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

            tempWidth = width;
            tempHeight = height;
        }
    }

    /**
     * Render the dark mode overlay
     * Works in first-person only
     */
    public static void renderDarkModeOverlay() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }

        // Lazy initialization
        if (!initialized) {
            init();
        }

        // Re-check initialization after init() call
        if (!initialized || tintShader == null || !tintShader.isValid()) {
            return;
        }

        // Periodic validation (every 5 seconds) to catch context loss
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastValidationTime > VALIDATION_INTERVAL_MS) {
            lastValidationTime = currentTime;

            // Validate VAO/VBO are still valid
            if (quadVAO == 0 || quadVBO == 0) {
                initialized = false;
                init();
                if (!initialized) return;
            }

            // Validate temp texture is still valid
            if (tempTexture != 0 && !GL11.glIsTexture(tempTexture)) {
                tempTexture = 0;
                tempWidth = 0;
                tempHeight = 0;
            }
        }

        try {
            // Get main framebuffer
            RenderTarget mainFramebuffer = mc.getMainRenderTarget();
            if (mainFramebuffer == null) {
                return;
            }

            int fbWidth = mainFramebuffer.width;
            int fbHeight = mainFramebuffer.height;

            // Validate framebuffer size
            if (fbWidth <= 0 || fbHeight <= 0) {
                return;
            }

            // RESIZE DETECTION: Skip frames during resize to avoid rendering garbage
            // During resize, framebuffer content can be undefined/corrupted
            if (fbWidth != lastFbWidth || fbHeight != lastFbHeight) {
                lastFbWidth = fbWidth;
                lastFbHeight = fbHeight;
                resizeSkipFrames = 3; // Skip 3 frames after resize
                // Force temp texture recreation
                tempWidth = 0;
                tempHeight = 0;
            }

            if (resizeSkipFrames > 0) {
                resizeSkipFrames--;
                return; // Skip rendering this frame
            }

            // Ensure temp texture exists and matches size (always check size changes)
            ensureTempTexture(fbWidth, fbHeight);

            // === SAVE OPENGL STATE (must be done every frame - Minecraft changes state constantly) ===
            int prevShaderProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            int prevVAO = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
            int prevActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            int prevBoundTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            int prevBlendSrcRGB = GL11.glGetInteger(GL30.GL_BLEND_SRC_RGB);
            int prevBlendDstRGB = GL11.glGetInteger(GL30.GL_BLEND_DST_RGB);
            int prevBlendSrcAlpha = GL11.glGetInteger(GL30.GL_BLEND_SRC_ALPHA);
            int prevBlendDstAlpha = GL11.glGetInteger(GL30.GL_BLEND_DST_ALPHA);
            int prevBlendEquationRGB = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB);
            int prevBlendEquationAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
            boolean blendEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
            boolean depthTestEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            boolean cullFaceEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
            boolean scissorTestEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
            boolean depthMaskEnabled = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);

            // Save viewport
            int[] viewport = new int[4];
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);

            // Set viewport to framebuffer size
            GL11.glViewport(0, 0, fbWidth, fbHeight);

            // Copy current framebuffer COLOR content to temp texture (avoids feedback loop!)
            // Don't change framebuffer bindings - use whatever Minecraft has bound
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, tempTexture);
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, fbWidth, fbHeight);

            // Disable scissor test to ensure full-screen rendering
            if (scissorTestEnabled) {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            }

            // Setup rendering state
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);  // CRITICAL: Disable depth writing to not corrupt viewmodel!
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL20.glBlendEquationSeparate(GL20.GL_FUNC_ADD, GL20.GL_FUNC_ADD);

            // Use shader and set uniforms
            tintShader.use();

            // Bind color texture to unit 0
            tintShader.setTexture(0);

            // DEPTH-BASED EXCLUSION: DISABLED
            // Cannot access GpuTextureView.id() - method not public/doesn't exist in this Minecraft version
            // Relying on render pipeline timing: DarkMode in END_MAIN, viewmodel renders AFTER
            tintShader.setExcludeViewmodel(false);
            tintShader.setDepthThreshold(depthThreshold);

            // Set other shader uniforms
            tintShader.setTintColor(tintR, tintG, tintB, tintA);
            tintShader.setIntensity(intensity);
            tintShader.setBlendMode(blendMode);
            tintShader.setVignetteStrength(vignetteStrength);
            tintShader.setSaturation(saturation);
            tintShader.setContrast(contrast);
            tintShader.setChromaticAberration(chromaticAberration);
            tintShader.setBrightness(brightness);

            // Render fullscreen quad
            GL30.glBindVertexArray(quadVAO);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
            GL30.glBindVertexArray(0);

            // === RESTORE ALL OPENGL STATE ===
            GL20.glUseProgram(prevShaderProgram);
            GL30.glBindVertexArray(prevVAO);
            GL13.glActiveTexture(prevActiveTexture);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevBoundTexture);
            GL30.glBlendFuncSeparate(prevBlendSrcRGB, prevBlendDstRGB, prevBlendSrcAlpha, prevBlendDstAlpha);
            GL20.glBlendEquationSeparate(prevBlendEquationRGB, prevBlendEquationAlpha);

            // Restore enabled/disabled states
            if (!blendEnabled) {
                GL11.glDisable(GL11.GL_BLEND);
            }
            if (depthTestEnabled) {
                GL11.glEnable(GL11.GL_DEPTH_TEST);
            }
            if (cullFaceEnabled) {
                GL11.glEnable(GL11.GL_CULL_FACE);
            }
            if (scissorTestEnabled) {
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
            }

            // Restore depth mask state (CRITICAL for viewmodel!)
            GL11.glDepthMask(depthMaskEnabled);

            // Restore viewport
            GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);

            // NOTE: Don't touch framebuffer bindings - Minecraft manages them

        } catch (Exception e) {
            System.err.println("[DarkModeRenderer] Error during rendering: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void setTintColor(float r, float g, float b) {
        tintR = r;
        tintG = g;
        tintB = b;
    }

    public static void setIntensity(float value) {
        intensity = Math.max(0.0f, Math.min(1.0f, value));
    }

    public static void setBlendMode(int mode) {
        blendMode = Math.max(0, Math.min(3, mode));
    }

    public static float getTintR() {
        return tintR;
    }

    public static float getTintG() {
        return tintG;
    }

    public static float getTintB() {
        return tintB;
    }

    public static float getIntensity() {
        return intensity;
    }

    public static int getBlendMode() {
        return blendMode;
    }

    public static void setVignetteStrength(float strength) {
        vignetteStrength = Math.max(0.0f, Math.min(1.0f, strength));
    }

    public static float getVignetteStrength() {
        return vignetteStrength;
    }

    public static void setSaturation(float sat) {
        saturation = Math.max(0.0f, Math.min(2.0f, sat));
    }

    public static float getSaturation() {
        return saturation;
    }

    public static void setContrast(float con) {
        contrast = Math.max(0.0f, Math.min(2.0f, con));
    }

    public static float getContrast() {
        return contrast;
    }

    public static void setChromaticAberration(float amount) {
        chromaticAberration = Math.max(0.0f, Math.min(0.01f, amount));
    }

    public static float getChromaticAberration() {
        return chromaticAberration;
    }

    public static void setBrightness(float bright) {
        brightness = Math.max(0.1f, Math.min(5.0f, bright));
    }

    public static float getBrightness() {
        return brightness;
    }

    public static void setExcludeViewmodel(boolean exclude) {
        excludeViewmodel = exclude;
    }

    public static boolean isExcludeViewmodel() {
        return excludeViewmodel;
    }

    public static void setDepthThreshold(float threshold) {
        depthThreshold = Math.max(0.0f, Math.min(1.0f, threshold));
    }

    public static float getDepthThreshold() {
        return depthThreshold;
    }

    /**
     * Cleanup resources
     */
    public static void cleanup() {
        if (tintShader != null) {
            tintShader.cleanup();
            tintShader = null;
        }
        if (quadVAO != 0) GL30.glDeleteVertexArrays(quadVAO);
        if (quadVBO != 0) GL20.glDeleteBuffers(quadVBO);
        if (tempTexture != 0) {
            GL11.glDeleteTextures(tempTexture);
            tempTexture = 0;
        }
        tempWidth = 0;
        tempHeight = 0;
        initialized = false;
    }
}
