package dev.hunchclient.render;

import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * New Viewmodel Framebuffer Rendering System - Raw OpenGL Implementation
 *
 * STRATEGY:
 * 1. Render viewmodel to separate framebuffer (viewmodelFBO)
 * 2. Apply overlay texture via shader → composite result
 * 3. Render composited result back to main framebuffer
 * 4. Cancel original viewmodel rendering
 *
 * Uses raw OpenGL (not Minecraft's RenderTarget API) to avoid compatibility issues
 */
public class ViewmodelFramebufferRenderer {

    private static final Minecraft MC = Minecraft.getInstance();

    // Separate framebuffer for viewmodel rendering (raw OpenGL)
    private static int viewmodelFBO = -1;
    private static int viewmodelTexture = -1;
    private static int viewmodelDepthBuffer = -1;
    private static int lastCreatedTexture = -1;

    // Shader for compositing viewmodel + overlay
    private static int compositeShader = -1;

    // Fullscreen quad for rendering composite back to main FB
    private static int quadVAO = -1;
    private static int quadVBO = -1;

    // State tracking
    private static boolean initialized = false;
    private static boolean isCapturing = false;
    private static int previousFBO = 0;
    private static int viewmodelWidth = 0;
    private static int viewmodelHeight = 0;

    // Settings
    private static float overlayOpacity = 1.0f;
    private static float parallaxIntensity = 0.2f;

    /**
     * Initialize the framebuffer rendering system
     */
    public static void init() {
        if (initialized) return;

        try {
            int width = MC.getWindow().getWidth();
            int height = MC.getWindow().getHeight();

            if (width <= 0 || height <= 0) {
                System.err.println("[ViewmodelFramebufferRenderer] Invalid window size");
                return;
            }

            // Create separate framebuffer for viewmodel
            createViewmodelFramebuffer(width, height);

            // Create composite shader
            createCompositeShader();

            // Create fullscreen quad
            createFullscreenQuad();

            initialized = true;
            System.out.println("[ViewmodelFramebufferRenderer] Initialized successfully");

        } catch (Exception e) {
            System.err.println("[ViewmodelFramebufferRenderer] Init failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Create separate framebuffer for viewmodel rendering using raw OpenGL
     */
    private static void createViewmodelFramebuffer(int width, int height) {
        // Delete old framebuffer if exists
        if (viewmodelFBO != -1) {
            GL30.glDeleteFramebuffers(viewmodelFBO);
        }
        if (viewmodelTexture != -1) {
            GL11.glDeleteTextures(viewmodelTexture);
        }
        if (viewmodelDepthBuffer != -1) {
            GL30.glDeleteRenderbuffers(viewmodelDepthBuffer);
        }

        // Create FBO
        viewmodelFBO = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, viewmodelFBO);

        // Create color texture
        viewmodelTexture = GL11.glGenTextures();
        lastCreatedTexture = viewmodelTexture;
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, viewmodelTexture);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, viewmodelTexture, 0);

        // Create depth buffer (necessary for viewmodel rendering)
        viewmodelDepthBuffer = GL30.glGenRenderbuffers();
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, viewmodelDepthBuffer);
        GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_DEPTH_COMPONENT, width, height);
        GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, viewmodelDepthBuffer);

        // Check framebuffer status
        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            System.err.println("[ViewmodelFramebufferRenderer] FBO not complete! Status: 0x" + Integer.toHexString(status));
        }

        // Unbind
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, 0);

        viewmodelWidth = width;
        viewmodelHeight = height;

        System.out.println("[ViewmodelFramebufferRenderer] Created viewmodel framebuffer: " + width + "x" + height);
    }

    /**
     * Create composite shader that blends viewmodel + overlay
     */
    private static void createCompositeShader() {
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

            uniform sampler2D ViewmodelTexture;  // Rendered viewmodel
            uniform sampler2D OverlayTexture;    // User's overlay image
            uniform float Time;
            uniform vec2 ParallaxOffset;
            uniform float OverlayOpacity;

            in vec2 texCoord;
            out vec4 fragColor;

            void main() {
                // Sample viewmodel
                vec4 viewmodel = texture(ViewmodelTexture, texCoord);

                // If no viewmodel pixel (alpha = 0), output transparent
                if (viewmodel.a < 0.01) {
                    discard;  // Don't render anything where viewmodel isn't
                }

                // Calculate overlay UV with parallax
                vec2 overlayUV = texCoord + ParallaxOffset;

                // Add subtle animation
                overlayUV.x += sin(Time * 0.5 + texCoord.y * 3.0) * 0.02;
                overlayUV.y += cos(Time * 0.3 + texCoord.x * 2.0) * 0.02;

                // Sample overlay texture
                vec4 overlayColor = texture(OverlayTexture, overlayUV);

                // Blend overlay with viewmodel using multiply/overlay blend
                float blendStrength = overlayColor.a * OverlayOpacity;

                // Use overlay color but preserve viewmodel's lighting/shading
                vec3 finalColor = mix(viewmodel.rgb, viewmodel.rgb * overlayColor.rgb, blendStrength);

                fragColor = vec4(finalColor, viewmodel.a);
            }
            """;

        // Compile vertex shader
        int vertexShader = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
        GL20.glShaderSource(vertexShader, vertexSource);
        GL20.glCompileShader(vertexShader);

        if (GL20.glGetShaderi(vertexShader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            System.err.println("[ViewmodelFramebufferRenderer] Vertex shader error: " +
                GL20.glGetShaderInfoLog(vertexShader, 1024));
        }

        // Compile fragment shader
        int fragmentShader = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
        GL20.glShaderSource(fragmentShader, fragmentSource);
        GL20.glCompileShader(fragmentShader);

        if (GL20.glGetShaderi(fragmentShader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            System.err.println("[ViewmodelFramebufferRenderer] Fragment shader error: " +
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
            System.err.println("[ViewmodelFramebufferRenderer] Shader link error: " +
                GL20.glGetProgramInfoLog(compositeShader, 1024));
        }

        // Cleanup
        GL20.glDeleteShader(vertexShader);
        GL20.glDeleteShader(fragmentShader);

        System.out.println("[ViewmodelFramebufferRenderer] Composite shader compiled successfully");
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
     * STEP 1: Begin capturing viewmodel to separate framebuffer
     * Call this BEFORE viewmodel renders
     */
    public static void beginCapture() {
        if (!initialized) {
            init();
        }
        if (!initialized) return;

        try {
            // Check framebuffer size matches window
            int width = MC.getWindow().getWidth();
            int height = MC.getWindow().getHeight();

            if (viewmodelWidth != width || viewmodelHeight != height) {
                createViewmodelFramebuffer(width, height);
            }

            // Save current framebuffer
            previousFBO = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);

            // Switch to viewmodel framebuffer
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, viewmodelFBO);

            // Clear viewmodel framebuffer (transparent)
            GL11.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

            // Set viewport
            GL11.glViewport(0, 0, width, height);

            isCapturing = true;

        } catch (Exception e) {
            System.err.println("[ViewmodelFramebufferRenderer] Error beginning capture: " + e.getMessage());
            e.printStackTrace();
            isCapturing = false;
        }
    }

    /**
     * STEP 2: Finish capturing, composite with overlay, render to main framebuffer
     * Call this AFTER viewmodel renders
     */
    public static void endCaptureAndComposite(float opacity, float parallax) {
        if (!isCapturing) return;
        isCapturing = false;

        if (!initialized || compositeShader == -1) return;

        overlayOpacity = opacity;
        parallaxIntensity = parallax;

        try {
            // Switch back to main framebuffer
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFBO);

            // Restore viewport
            int width = MC.getWindow().getWidth();
            int height = MC.getWindow().getHeight();
            GL11.glViewport(0, 0, width, height);

            // Check if overlay texture is available
            if (!OverlayTextureManager.hasTexture()) {
                // No overlay - just render viewmodel as-is to main FB
                renderViewmodelToMain();
                return;
            }

            // Composite viewmodel + overlay and render to main FB
            renderCompositeToMain();

        } catch (Exception e) {
            System.err.println("[ViewmodelFramebufferRenderer] Error compositing: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Render composited viewmodel+overlay to main framebuffer
     */
    private static void renderCompositeToMain() {
        // Calculate parallax offset from player camera
        float parallaxX = 0.0f;
        float parallaxY = 0.0f;
        if (MC.player != null) {
            parallaxX = (MC.player.getYRot() % 360.0f) / 360.0f * 0.2f * parallaxIntensity;
            parallaxY = (MC.player.getXRot() + 90.0f) / 180.0f * 0.1f * parallaxIntensity;
        }

        // Save OpenGL state
        boolean blendEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean depthTestEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean cullFaceEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        int prevShaderProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int prevVAO = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int prevActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);

        // Setup rendering state
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDepthMask(false);

        // Use composite shader
        GL20.glUseProgram(compositeShader);

        // Bind viewmodel texture (Sampler0)
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, viewmodelTexture);
        GL20.glUniform1i(GL20.glGetUniformLocation(compositeShader, "ViewmodelTexture"), 0);

        // Bind overlay texture (Sampler1)
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        int overlayTextureId = OverlayTextureManager.getGlTextureId();
        if (overlayTextureId != -1) {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, overlayTextureId);
        }
        GL20.glUniform1i(GL20.glGetUniformLocation(compositeShader, "OverlayTexture"), 1);

        // Set uniforms
        float time = (System.currentTimeMillis() % 100000) / 1000.0f;
        GL20.glUniform1f(GL20.glGetUniformLocation(compositeShader, "Time"), time);
        GL20.glUniform2f(GL20.glGetUniformLocation(compositeShader, "ParallaxOffset"), parallaxX, parallaxY);
        GL20.glUniform1f(GL20.glGetUniformLocation(compositeShader, "OverlayOpacity"), overlayOpacity);

        // Render fullscreen quad
        GL30.glBindVertexArray(quadVAO);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
        GL30.glBindVertexArray(0);

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
        if (cullFaceEnabled) GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glDepthMask(true);
    }

    /**
     * Render viewmodel without overlay to main framebuffer (simple blit)
     */
    private static void renderViewmodelToMain() {
        // Save OpenGL state
        boolean blendEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean depthTestEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean cullFaceEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);

        // Setup state for blitting
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);

        // Blit viewmodel FBO to main FBO using glBlitFramebuffer
        int width = MC.getWindow().getWidth();
        int height = MC.getWindow().getHeight();

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, viewmodelFBO);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousFBO);

        GL30.glBlitFramebuffer(
            0, 0, width, height,  // src
            0, 0, width, height,  // dst
            GL11.GL_COLOR_BUFFER_BIT,
            GL11.GL_NEAREST
        );

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFBO);

        // Restore state
        if (!blendEnabled) GL11.glDisable(GL11.GL_BLEND);
        if (depthTestEnabled) GL11.glEnable(GL11.GL_DEPTH_TEST);
        if (cullFaceEnabled) GL11.glEnable(GL11.GL_CULL_FACE);
    }

    /**
     * Cancel capture if something goes wrong
     */
    public static void cancelCapture() {
        if (isCapturing) {
            try {
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFBO);
                int width = MC.getWindow().getWidth();
                int height = MC.getWindow().getHeight();
                GL11.glViewport(0, 0, width, height);
            } catch (Exception e) {
                System.err.println("[ViewmodelFramebufferRenderer] Error canceling capture: " + e.getMessage());
            }
            isCapturing = false;
        }
    }

    /**
     * Check if currently capturing
     */
    public static boolean isCapturing() {
        return isCapturing;
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
        if (viewmodelFBO != -1) {
            GL30.glDeleteFramebuffers(viewmodelFBO);
            viewmodelFBO = -1;
        }
        if (viewmodelTexture != -1) {
            GL11.glDeleteTextures(viewmodelTexture);
            viewmodelTexture = -1;
        }
        if (viewmodelDepthBuffer != -1) {
            GL30.glDeleteRenderbuffers(viewmodelDepthBuffer);
            viewmodelDepthBuffer = -1;
        }

        initialized = false;
        isCapturing = false;

        System.out.println("[ViewmodelFramebufferRenderer] Cleaned up");
    }
}
