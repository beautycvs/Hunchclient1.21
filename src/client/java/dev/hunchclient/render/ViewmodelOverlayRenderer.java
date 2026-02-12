package dev.hunchclient.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.opengl.GlDevice;
import com.mojang.blaze3d.systems.RenderSystem;

/**
 * Viewmodel Overlay Renderer - 1.21+ Compatible Architecture
 *
 * Uses a pre/post snapshot comparison approach:
 * 1. BEFORE hands render: Capture "worldOnlyTexture" (world without hands)
 * 2. Hands render normally to main framebuffer
 * 3. AFTER hands render: Compare current framebuffer with worldOnlyTexture
 * 4. Where pixels differ = hand pixels → apply overlay texture
 *
 * This approach works because:
 * - No framebuffer switching during RenderPass (which crashes in 1.21+)
 * - Uses simple glCopyTexSubImage2D which doesn't interfere with RenderPass
 * - Runs post-processing AFTER all hand rendering is complete
 */
public class ViewmodelOverlayRenderer {

    private static final Minecraft MC = Minecraft.getInstance();

    // Textures for snapshot comparison
    private static int worldOnlyTexture = 0;  // World rendered, no hands yet
    private static int currentTexture = 0;    // Current framebuffer (with hands)
    private static int worldDepthTexture = 0; // Depth buffer before hands
    private static int textureWidth = 0;
    private static int textureHeight = 0;

    // Shader for overlay compositing
    private static int compositeShader = -1;

    // Fullscreen quad VAO
    private static int quadVAO = -1;
    private static int quadVBO = -1;

    // State
    private static boolean initialized = false;
    private static boolean worldSnapshotCaptured = false;
    private static int lastWidth = -1;
    private static int lastHeight = -1;

    // Settings (set from module)
    private static float overlayOpacity = 1.0f;
    private static float parallaxIntensity = 0.2f;

    /**
     * Initialize the overlay rendering system
     */
    public static void init() {
        if (initialized) return;

        try {
            int width = MC.getWindow().getWidth();
            int height = MC.getWindow().getHeight();
            if (width <= 0 || height <= 0) {
                System.err.println("[ViewmodelOverlayRenderer] Skipping init: invalid framebuffer size");
                return;
            }

            createShader();
            createFullscreenQuad();
            ensureTextures(width, height);

            lastWidth = width;
            lastHeight = height;
            initialized = true;

            System.out.println("[ViewmodelOverlayRenderer] Initialized with snapshot comparison approach");
        } catch (Exception e) {
            System.err.println("[ViewmodelOverlayRenderer] Failed to initialize: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Create the composite shader for overlay blending
     */
    private static void createShader() {
        String vertexSource = """
            #version 150

            in vec2 Position;
            in vec2 TexCoord;

            out vec2 texCoord;

            void main() {
                gl_Position = vec4(Position, 0.0, 1.0);
                texCoord = TexCoord;
            }
            """;

        String fragmentSource = """
            #version 150

            uniform sampler2D DepthTexture;      // Current depth buffer
            uniform sampler2D OverlayTexture;    // User's overlay image
            uniform float Time;
            uniform vec2 ParallaxOffset;
            uniform float OverlayOpacity;
            uniform float NearDepthThreshold;    // Max depth for hand detection
            uniform float FallbackMode;          // 1.0 = no depth available, draw red

            in vec2 texCoord;
            out vec4 fragColor;

            void main() {
                if (FallbackMode > 0.5) {
                    // FALLBACK: No depth buffer available - draw red everywhere as test
                    fragColor = vec4(1.0, 0.0, 0.0, 0.5);
                    return;
                }

                // DEBUG: Visualize depth buffer
                float depth = texture(DepthTexture, texCoord).r;

                // Show depth as grayscale (black = near, white = far)
                fragColor = vec4(depth, depth, depth, 1.0);
            }
            """;

        // Compile vertex shader
        int vertexShader = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
        GL20.glShaderSource(vertexShader, vertexSource);
        GL20.glCompileShader(vertexShader);

        if (GL20.glGetShaderi(vertexShader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            System.err.println("[ViewmodelOverlayRenderer] Vertex shader error: " +
                               GL20.glGetShaderInfoLog(vertexShader, 1024));
        }

        // Compile fragment shader
        int fragmentShader = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
        GL20.glShaderSource(fragmentShader, fragmentSource);
        GL20.glCompileShader(fragmentShader);

        if (GL20.glGetShaderi(fragmentShader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            System.err.println("[ViewmodelOverlayRenderer] Fragment shader error: " +
                               GL20.glGetShaderInfoLog(fragmentShader, 1024));
        }

        // Link program
        compositeShader = GL20.glCreateProgram();
        GL20.glAttachShader(compositeShader, vertexShader);
        GL20.glAttachShader(compositeShader, fragmentShader);

        GL20.glBindAttribLocation(compositeShader, 0, "Position");
        GL20.glBindAttribLocation(compositeShader, 1, "TexCoord");

        GL20.glLinkProgram(compositeShader);

        if (GL20.glGetProgrami(compositeShader, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            System.err.println("[ViewmodelOverlayRenderer] Shader link error: " +
                               GL20.glGetProgramInfoLog(compositeShader, 1024));
        }

        // Cleanup
        GL20.glDeleteShader(vertexShader);
        GL20.glDeleteShader(fragmentShader);

        System.out.println("[ViewmodelOverlayRenderer] Shader compiled successfully");
    }

    /**
     * Create fullscreen quad for rendering
     */
    private static void createFullscreenQuad() {
        float[] vertices = {
            // Position    // TexCoord
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
     * Ensure textures exist and match framebuffer size
     */
    private static void ensureTextures(int width, int height) {
        if (width <= 0 || height <= 0) return;

        if (textureWidth != width || textureHeight != height) {
            // Cleanup old textures
            if (worldOnlyTexture != 0) GL11.glDeleteTextures(worldOnlyTexture);
            if (currentTexture != 0) GL11.glDeleteTextures(currentTexture);
            if (worldDepthTexture != 0) GL11.glDeleteTextures(worldDepthTexture);

            // Create worldOnlyTexture (color)
            worldOnlyTexture = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, worldOnlyTexture);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, width, height, 0,
                              GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

            // Create currentTexture (color)
            currentTexture = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, currentTexture);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, width, height, 0,
                              GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

            // Create worldDepthTexture (depth buffer snapshot)
            worldDepthTexture = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, worldDepthTexture);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL14.GL_DEPTH_COMPONENT24, width, height, 0,
                              GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, 0);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

            textureWidth = width;
            textureHeight = height;

            System.out.println("[ViewmodelOverlayRenderer] Created textures (incl. depth): " + width + "x" + height);
        }
    }

    /**
     * STEP 1: Capture world depth snapshot (called BEFORE hands render)
     * This is called from GameRendererMixin after LevelRenderer.renderLevel()
     */
    public static void captureWorldSnapshot() {
        if (!initialized) {
            init();
        }
        if (!initialized) return;

        try {
            RenderTarget mainFramebuffer = MC.getMainRenderTarget();
            if (mainFramebuffer == null) return;

            int width = mainFramebuffer.width;
            int height = mainFramebuffer.height;

            if (width <= 0 || height <= 0) return;

            // Ensure textures match size
            ensureTextures(width, height);

            // Copy depth buffer to worldDepthTexture
            // glCopyTexSubImage2D copies from the current READ_FRAMEBUFFER's depth attachment
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, worldDepthTexture);
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

            worldSnapshotCaptured = true;
            System.out.println("[ViewmodelOverlayRenderer] World depth snapshot captured " + width + "x" + height);

        } catch (Exception e) {
            System.err.println("[ViewmodelOverlayRenderer] Error capturing world snapshot: " + e.getMessage());
            worldSnapshotCaptured = false;
        }
    }

    /**
     * DEPTH-THRESHOLD: Apply overlay to pixels with depth close to camera (hands)
     * Called AFTER hands have been rendered
     */
    public static void applyOverlayPostProcess(float opacity, float parallax) {
        if (!initialized) {
            init();
        }
        if (!initialized || compositeShader == -1) return;

        // Check if overlay texture is available
        if (!OverlayTextureManager.hasTexture()) return;

        RenderTarget mainFramebuffer = MC.getMainRenderTarget();
        if (mainFramebuffer == null) return;

        int width = mainFramebuffer.width;
        int height = mainFramebuffer.height;
        if (width <= 0 || height <= 0) return;

        ensureTextures(width, height);

        overlayOpacity = opacity;
        parallaxIntensity = parallax;

        try {
            // Get Minecraft's FBO and query its depth attachment
            int mcFBO = ((GlTexture) mainFramebuffer.getColorTexture())
                    .getFbo(((GlDevice) RenderSystem.getDevice()).directStateAccess(), null);

            // Save current FBO and bind Minecraft's FBO (using cache to avoid GPU stall)
            GLStateCache.ensureValid();
            int prevFBO = GLStateCache.getCurrentFBO();
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, mcFBO);

            // Check what type of depth attachment is used
            int[] attachType = new int[1];
            GL30.glGetFramebufferAttachmentParameteriv(
                GL30.GL_FRAMEBUFFER,
                GL30.GL_DEPTH_ATTACHMENT,
                GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE,
                attachType
            );

            int mcDepthTexture = 0;
            if (attachType[0] == GL11.GL_TEXTURE) {
                int[] depthTexId = new int[1];
                GL30.glGetFramebufferAttachmentParameteriv(
                    GL30.GL_FRAMEBUFFER,
                    GL30.GL_DEPTH_ATTACHMENT,
                    GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME,
                    depthTexId
                );
                mcDepthTexture = depthTexId[0];
            } else if (attachType[0] == GL30.GL_RENDERBUFFER) {
                // Depth is a renderbuffer, not a texture - can't sample directly
                // Try GL_DEPTH_STENCIL_ATTACHMENT instead
                GL30.glGetFramebufferAttachmentParameteriv(
                    GL30.GL_FRAMEBUFFER,
                    GL30.GL_DEPTH_STENCIL_ATTACHMENT,
                    GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE,
                    attachType
                );
                if (attachType[0] == GL11.GL_TEXTURE) {
                    int[] depthTexId = new int[1];
                    GL30.glGetFramebufferAttachmentParameteriv(
                        GL30.GL_FRAMEBUFFER,
                        GL30.GL_DEPTH_STENCIL_ATTACHMENT,
                        GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME,
                        depthTexId
                    );
                    mcDepthTexture = depthTexId[0];
                }
            }

            // Restore previous FBO
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFBO);

            if (mcDepthTexture == 0) {
                // Depth is renderbuffer - fall back to simpler approach
                // Just draw overlay on everything for now as debug
                System.out.println("[ViewmodelOverlayRenderer] Depth is renderbuffer, using fallback");
                mcDepthTexture = -1; // Signal to use fallback
            }

            // Get parallax offset from player camera
            float parallaxX = 0.0f;
            float parallaxY = 0.0f;
            if (MC.player != null) {
                parallaxX = (MC.player.getYRot() % 360.0f) / 360.0f * 0.2f * parallaxIntensity;
                parallaxY = (MC.player.getXRot() + 90.0f) / 180.0f * 0.1f * parallaxIntensity;
            }

            // Save OpenGL state (using cache to avoid GPU stalls)
            GLStateCache.ensureValid();
            int prevShaderProgram = GLStateCache.getCurrentShader();
            int prevVAO = GLStateCache.getCurrentVAO();
            int prevActiveTexture = GLStateCache.getCurrentActiveTexture();
            boolean blendEnabled = GLStateCache.isBlendEnabled();
            boolean depthTestEnabled = GLStateCache.isDepthTestEnabled();
            boolean depthMaskEnabled = GLStateCache.isDepthMaskEnabled();

            // Setup rendering state
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_BLEND);
            GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                                      GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);

            // Use shader
            GL20.glUseProgram(compositeShader);

            // Bind depth texture (or skip if fallback mode)
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            if (mcDepthTexture > 0) {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, mcDepthTexture);
            } else {
                // Fallback: bind a dummy texture or skip depth test
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            }
            GL20.glUniform1i(GL20.glGetUniformLocation(compositeShader, "DepthTexture"), 0);
            // Tell shader if we're in fallback mode (no depth available)
            GL20.glUniform1f(GL20.glGetUniformLocation(compositeShader, "FallbackMode"), mcDepthTexture > 0 ? 0.0f : 1.0f);

            // Bind overlay texture
            GL13.glActiveTexture(GL13.GL_TEXTURE1);
            OverlayTextureManager.bindToUnit(1);
            GL20.glUniform1i(GL20.glGetUniformLocation(compositeShader, "OverlayTexture"), 1);

            // Set uniforms
            float time = (System.currentTimeMillis() % 100000) / 1000.0f;
            GL20.glUniform1f(GL20.glGetUniformLocation(compositeShader, "Time"), time);
            GL20.glUniform2f(GL20.glGetUniformLocation(compositeShader, "ParallaxOffset"), parallaxX, parallaxY);
            GL20.glUniform1f(GL20.glGetUniformLocation(compositeShader, "OverlayOpacity"), overlayOpacity);
            // Depth threshold: hands typically render at depth 0.0-0.2 range
            // Adjust this value if needed - higher = more pixels detected as hand
            GL20.glUniform1f(GL20.glGetUniformLocation(compositeShader, "NearDepthThreshold"), 0.15f);

            // Render fullscreen quad
            GL30.glBindVertexArray(quadVAO);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
            GL30.glBindVertexArray(0);

            // Debug removed for performance

            // Restore OpenGL state
            GL20.glUseProgram(prevShaderProgram);
            GL30.glBindVertexArray(prevVAO);

            GL13.glActiveTexture(GL13.GL_TEXTURE1);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GL13.glActiveTexture(prevActiveTexture);

            if (!blendEnabled) GL11.glDisable(GL11.GL_BLEND);
            if (depthTestEnabled) GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(depthMaskEnabled);

        } catch (Exception e) {
            System.err.println("[ViewmodelOverlayRenderer] Error applying overlay: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Check if world snapshot was captured this frame
     */
    public static boolean hasWorldSnapshot() {
        return worldSnapshotCaptured;
    }

    /**
     * Check if initialized
     */
    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * Cleanup resources
     */
    public static void cleanup() {
        if (compositeShader != -1) {
            GL20.glDeleteProgram(compositeShader);
            compositeShader = -1;
        }
        if (quadVAO != -1) {
            GL30.glDeleteVertexArrays(quadVAO);
            quadVAO = -1;
        }
        if (quadVBO != -1) {
            GL20.glDeleteBuffers(quadVBO);
            quadVBO = -1;
        }
        if (worldOnlyTexture != 0) {
            GL11.glDeleteTextures(worldOnlyTexture);
            worldOnlyTexture = 0;
        }
        if (currentTexture != 0) {
            GL11.glDeleteTextures(currentTexture);
            currentTexture = 0;
        }
        if (worldDepthTexture != 0) {
            GL11.glDeleteTextures(worldDepthTexture);
            worldDepthTexture = 0;
        }

        textureWidth = 0;
        textureHeight = 0;
        initialized = false;
        worldSnapshotCaptured = false;

        System.out.println("[ViewmodelOverlayRenderer] Cleaned up");
    }

    // ============ Legacy compatibility methods (kept for HeldItemRendererMixin) ============

    /**
     * @deprecated Use captureWorldSnapshot() + applyOverlayPostProcess() instead
     */
    @Deprecated
    public static void beginCapture(float opacity, float parallaxIntensityParam) {
        // Legacy method - no longer does anything
        // The new architecture uses captureWorldSnapshot() before hands
        // and applyOverlayPostProcess() after hands
    }

    /**
     * @deprecated Use applyOverlayPostProcess() instead
     */
    @Deprecated
    public static void endCaptureAndComposite() {
        // Legacy method - no longer does anything
    }

    /**
     * @deprecated No longer needed
     */
    @Deprecated
    public static void cancelCapture() {
        // Legacy method - no longer needed
    }

    /**
     * @deprecated Always returns false now
     */
    @Deprecated
    public static boolean isCapturing() {
        return false;
    }
}
