package dev.hunchclient.render;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.vertex.PoseStack;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;

/**
 * GPU-based Post-Processing Glow ESP Renderer
 * Uses Gaussian Blur + Additive Compositing for beautiful glow effects
 *
 * Uses RAW OpenGL for framebuffer management to avoid Minecraft API compatibility issues
 */
public class GlowESPRenderer {

    private static final Minecraft MC = Minecraft.getInstance();

    // Framebuffers for multi-pass rendering (raw OpenGL)
    private static int glowFBO = -1;
    private static int glowTexture = -1;
    private static int blurFBO1 = -1;
    private static int blurTexture1 = -1;
    private static int blurFBO2 = -1;
    private static int blurTexture2 = -1;

    // Custom Gaussian blur shader
    private static GaussianBlurShader blurShader;

    // Simple shader for rendering colored boxes directly
    private static SimpleBoxShader boxShader;

    // Queue of objects to render with glow
    private static final List<GlowRenderTask> glowQueue = new ArrayList<>();

    private static boolean initialized = false;

    // Full-screen quad VAO for post-processing
    private static int quadVAO;
    private static int quadVBO;

    // Box VAO/VBO for rendering
    private static int boxVAO;
    private static int boxVBO;

    // Track last dimensions for resize detection
    private static int lastWidth = -1;
    private static int lastHeight = -1;

    /**
     * Initialize the glow rendering system
     */
    public static void init() {
        if (initialized) return;

        try {
            int width = MC.getWindow().getWidth();
            int height = MC.getWindow().getHeight();

            // Create framebuffers using raw OpenGL
            glowFBO = createFramebuffer(width, height);
            glowTexture = lastCreatedTexture;

            blurFBO1 = createFramebuffer(width, height);
            blurTexture1 = lastCreatedTexture;

            blurFBO2 = createFramebuffer(width, height);
            blurTexture2 = lastCreatedTexture;

            lastWidth = width;
            lastHeight = height;

            // Load shaders
            blurShader = new GaussianBlurShader();
            boxShader = new SimpleBoxShader();

            // Create VAOs
            createFullscreenQuad();
            createBoxVAO();

            // Note: glLineWidth is not supported in Minecraft 1.21's OpenGL context

            initialized = true;
        } catch (Exception e) {
            System.err.println("[GlowESPRenderer] Failed to initialize: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static int lastCreatedTexture = -1;

    /**
     * Create a framebuffer object with color texture
     */
    private static int createFramebuffer(int width, int height) {
        int fbo = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);

        // Create color texture
        int texture = GL11.glGenTextures();
        lastCreatedTexture = texture;
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);

        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0);

        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 0);

        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            System.err.println("[GlowESPRenderer] FBO not complete! Status: 0x" + Integer.toHexString(status));
        }

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        return fbo;
    }

    /**
     * Recreate framebuffers if window size changed
     */
    private static void resizeFramebuffers(int width, int height) {
        // Delete old framebuffers
        if (glowFBO != -1) GL30.glDeleteFramebuffers(glowFBO);
        if (glowTexture != -1) GL11.glDeleteTextures(glowTexture);
        if (blurFBO1 != -1) GL30.glDeleteFramebuffers(blurFBO1);
        if (blurTexture1 != -1) GL11.glDeleteTextures(blurTexture1);
        if (blurFBO2 != -1) GL30.glDeleteFramebuffers(blurFBO2);
        if (blurTexture2 != -1) GL11.glDeleteTextures(blurTexture2);

        // Create new framebuffers
        glowFBO = createFramebuffer(width, height);
        glowTexture = lastCreatedTexture;

        blurFBO1 = createFramebuffer(width, height);
        blurTexture1 = lastCreatedTexture;

        blurFBO2 = createFramebuffer(width, height);
        blurTexture2 = lastCreatedTexture;

        lastWidth = width;
        lastHeight = height;
    }

    /**
     * Create a full-screen quad VAO for rendering post-processing effects
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
     * Create box VAO (empty, we'll upload data per-frame)
     */
    private static void createBoxVAO() {
        boxVAO = GL30.glGenVertexArrays();
        boxVBO = GL30.glGenBuffers();

        GL30.glBindVertexArray(boxVAO);
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, boxVBO);

        // Position attribute (3 floats)
        GL30.glEnableVertexAttribArray(0);
        GL30.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 7 * Float.BYTES, 0);

        // Color attribute (4 floats)
        GL30.glEnableVertexAttribArray(1);
        GL30.glVertexAttribPointer(1, 4, GL11.GL_FLOAT, false, 7 * Float.BYTES, 3 * Float.BYTES);

        GL30.glBindVertexArray(0);
    }

    /**
     * Queue a box to be rendered with glow effect
     */
    public static void queueGlowBox(PoseStack matrices, AABB box, float[] color, float glowIntensity, float glowRadius) {
        if (!initialized) {
            init();
        }

        glowQueue.add(new GlowRenderTask(
            new PoseStack(),
            box,
            color.clone(),
            glowIntensity,
            glowRadius
        ));

        // Copy matrix transformations
        GlowRenderTask task = glowQueue.get(glowQueue.size() - 1);
        task.matrices.last().pose().set(matrices.last().pose());
    }

    /**
     * Render all queued glow objects with post-processing
     * Call this at the end of your render pass
     */
    public static void renderGlowPass() {
        if (!initialized || glowQueue.isEmpty()) {
            glowQueue.clear();
            return;
        }

        // Clear any existing GL errors before we start
        clearGlErrors();

        // Save current GL state
        int prevFBO = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        clearGlErrors(); // Clear errors from glGetInteger
        boolean blendEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean depthTestEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);

        // Save current viewport
        int[] prevViewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, prevViewport);

        // Save current blend function
        int prevBlendSrcRGB = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        int prevBlendDstRGB = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int prevBlendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        int prevBlendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);

        // Save active texture unit and bound texture
        int prevActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int prevBoundTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);

        // Save current shader program
        int prevShaderProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);

        // Save current VAO
        int prevVAO = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);

        // Save polygon offset state (used for enchantment glint)
        boolean polygonOffsetFillEnabled = GL11.glIsEnabled(GL11.GL_POLYGON_OFFSET_FILL);
        clearGlErrors(); // Clear errors from glGetInteger

        try {
            // Disable enchantment glint rendering during glow pass
            if (polygonOffsetFillEnabled) {
                GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
            }
            // Resize framebuffers if window size changed
            int width = MC.getWindow().getWidth();
            int height = MC.getWindow().getHeight();

            if (width != lastWidth || height != lastHeight) {
                resizeFramebuffers(width, height);
            }

            // Step 1: Render glow objects to separate framebuffer
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, glowFBO);
            GL11.glViewport(0, 0, width, height);
            GL11.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
            GL11.glEnable(GL11.GL_BLEND);
            GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            GlStateManager._disableDepthTest();

            // Calculate maximum blur radius from all tasks
            float maxBlurRadius = 1.0f; // Use actual task values only
            for (GlowRenderTask task : glowQueue) {
                maxBlurRadius = Math.max(maxBlurRadius, task.glowRadius);
            }

            // Setup shader once for all objects
            boxShader.use();
            boxShader.setMatrices();

            // Render all glow objects
            for (GlowRenderTask task : glowQueue) {
                renderGlowObject(task);
            }

            // Step 2: Apply horizontal blur
            applyBlur(glowTexture, blurFBO1, true, width, height, maxBlurRadius);

            // Step 3: Apply vertical blur
            applyBlur(blurTexture1, blurFBO2, false, width, height, maxBlurRadius);

            // Step 4: Composite blurred glow onto main framebuffer
            compositeGlow(blurTexture2, prevFBO, width, height, 0.8f);

        } finally {
            // Restore OpenGL state - CRITICAL for not breaking Minecraft rendering

            // 1. Unbind framebuffer
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFBO);

            // 2. Restore shader program
            GL20.glUseProgram(prevShaderProgram);

            // 3. Restore VAO and unbind VBO
            GL30.glBindVertexArray(prevVAO);
            GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, 0);

            // 4. CRITICAL: Restore active texture unit and restore bound texture
            GL13.glActiveTexture(prevActiveTexture);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevBoundTexture);

            // 5. Restore blend function BEFORE blend state
            GL14.glBlendFuncSeparate(prevBlendSrcRGB, prevBlendDstRGB, prevBlendSrcAlpha, prevBlendDstAlpha);

            // 6. Restore blend state
            if (blendEnabled) {
                GL11.glEnable(GL11.GL_BLEND);
            } else {
                GL11.glDisable(GL11.GL_BLEND);
            }

            // 7. Restore depth test state
            if (depthTestEnabled) {
                GlStateManager._enableDepthTest();
            } else {
                GlStateManager._disableDepthTest();
            }

            // 8. Restore viewport
            GL11.glViewport(prevViewport[0], prevViewport[1], prevViewport[2], prevViewport[3]);

            // 9. Restore polygon offset state (enchantment glint)
            if (polygonOffsetFillEnabled) {
                GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
            }

            glowQueue.clear();
        }
    }

    /**
     * Render a single glow object using direct OpenGL
     */
    private static void renderGlowObject(GlowRenderTask task) {
        float r = task.color[0];
        float g = task.color[1];
        float b = task.color[2];

        // Add pulsing animation based on time
        long currentTime = System.currentTimeMillis();
        float pulsePhase = (float) (currentTime % 2000) / 2000.0f; // 2 second cycle
        float pulseFactor = 2.0f + 0.7f * (float) Math.sin(pulsePhase * Math.PI * 2.0);

        // Apply pulse to intensity and add color variation
        float fillAlpha = task.color[3] * task.glowIntensity * pulseFactor;
        float outlineAlpha = Math.min(1.0f, fillAlpha + 0.35f);

        // Add subtle color shift during pulse
        float colorShift = 0.1f * (float) Math.sin(pulsePhase * Math.PI * 4.0);
        r = Math.min(1.0f, r + colorShift);
        g = Math.min(1.0f, g - colorShift * 0.5f);
        b = Math.min(1.0f, b + colorShift * 0.3f);

        // Get camera position to transform world coords to camera-relative coords
        net.minecraft.client.Camera camera = MC.gameRenderer.getMainCamera();
        double camX = camera.getPosition().x;
        double camY = camera.getPosition().y;
        double camZ = camera.getPosition().z;

        // Transform box to camera-relative coordinates
        // Keep box size exactly at block boundaries (no expansion)
        float minX = (float) (task.box.minX - camX);
        float minY = (float) (task.box.minY - camY);
        float minZ = (float) (task.box.minZ - camZ);
        float maxX = (float) (task.box.maxX - camX);
        float maxY = (float) (task.box.maxY - camY);
        float maxZ = (float) (task.box.maxZ - camZ);

        // Removed rotation angle - not needed currently

        // Render multiple layers with different sizes for depth effect
        for (int layer = 0; layer < 3; layer++) {
            float layerScale = 1.0f - layer * 0.02f; // Slightly smaller each layer
            float layerAlpha = fillAlpha * (1.0f - layer * 0.3f); // Fade each layer

            // Apply layer-specific scaling
            float lMinX = minX + (maxX - minX) * (1.0f - layerScale) * 0.5f;
            float lMinY = minY + (maxY - minY) * (1.0f - layerScale) * 0.5f;
            float lMinZ = minZ + (maxZ - minZ) * (1.0f - layerScale) * 0.5f;
            float lMaxX = maxX - (maxX - minX) * (1.0f - layerScale) * 0.5f;
            float lMaxY = maxY - (maxY - minY) * (1.0f - layerScale) * 0.5f;
            float lMaxZ = maxZ - (maxZ - minZ) * (1.0f - layerScale) * 0.5f;

            // Build vertex data for this layer
            float[] fillVertices = buildBoxVertices(lMinX, lMinY, lMinZ, lMaxX, lMaxY, lMaxZ, r, g, b, layerAlpha);
            drawBoxGeometry(fillVertices, GL11.GL_TRIANGLES);
        }

        // Draw main outline with original size
        float[] outlineVertices = buildBoxOutlineVertices(minX, minY, minZ, maxX, maxY, maxZ, r, g, b, outlineAlpha);
        drawBoxGeometry(outlineVertices, GL11.GL_LINES);

        // Don't unbind shader here - will be unbound in finally block
    }

    // Pre-allocated buffer for vertex data to avoid allocations
    private static final int BOX_VERTEX_COUNT = 36 * 7; // 36 vertices * 7 floats per vertex
    private static final float[] boxVertexBuffer = new float[BOX_VERTEX_COUNT];

    /**
     * Build vertex data for a filled box
     * Each vertex: [x, y, z, r, g, b, a]
     */
    private static float[] buildBoxVertices(float minX, float minY, float minZ,
                                           float maxX, float maxY, float maxZ,
                                           float r, float g, float b, float a) {
        int i = 0;

        // Bottom face (y = minY) - 6 vertices
        boxVertexBuffer[i++] = minX; boxVertexBuffer[i++] = minY; boxVertexBuffer[i++] = minZ;
        boxVertexBuffer[i++] = r; boxVertexBuffer[i++] = g; boxVertexBuffer[i++] = b; boxVertexBuffer[i++] = a;
        boxVertexBuffer[i++] = maxX; boxVertexBuffer[i++] = minY; boxVertexBuffer[i++] = minZ;
        boxVertexBuffer[i++] = r; boxVertexBuffer[i++] = g; boxVertexBuffer[i++] = b; boxVertexBuffer[i++] = a;
        boxVertexBuffer[i++] = maxX; boxVertexBuffer[i++] = minY; boxVertexBuffer[i++] = maxZ;
        boxVertexBuffer[i++] = r; boxVertexBuffer[i++] = g; boxVertexBuffer[i++] = b; boxVertexBuffer[i++] = a;
        boxVertexBuffer[i++] = minX; boxVertexBuffer[i++] = minY; boxVertexBuffer[i++] = minZ;
        boxVertexBuffer[i++] = r; boxVertexBuffer[i++] = g; boxVertexBuffer[i++] = b; boxVertexBuffer[i++] = a;
        boxVertexBuffer[i++] = maxX; boxVertexBuffer[i++] = minY; boxVertexBuffer[i++] = maxZ;
        boxVertexBuffer[i++] = r; boxVertexBuffer[i++] = g; boxVertexBuffer[i++] = b; boxVertexBuffer[i++] = a;
        boxVertexBuffer[i++] = minX; boxVertexBuffer[i++] = minY; boxVertexBuffer[i++] = maxZ;
        boxVertexBuffer[i++] = r; boxVertexBuffer[i++] = g; boxVertexBuffer[i++] = b; boxVertexBuffer[i++] = a;

        // Top face (y = maxY) - 6 vertices
        boxVertexBuffer[i++] = minX; boxVertexBuffer[i++] = maxY; boxVertexBuffer[i++] = minZ;
        boxVertexBuffer[i++] = r; boxVertexBuffer[i++] = g; boxVertexBuffer[i++] = b; boxVertexBuffer[i++] = a;
        boxVertexBuffer[i++] = minX; boxVertexBuffer[i++] = maxY; boxVertexBuffer[i++] = maxZ;
        boxVertexBuffer[i++] = r; boxVertexBuffer[i++] = g; boxVertexBuffer[i++] = b; boxVertexBuffer[i++] = a;
        boxVertexBuffer[i++] = maxX; boxVertexBuffer[i++] = maxY; boxVertexBuffer[i++] = maxZ;
        boxVertexBuffer[i++] = r; boxVertexBuffer[i++] = g; boxVertexBuffer[i++] = b; boxVertexBuffer[i++] = a;
        boxVertexBuffer[i++] = minX; boxVertexBuffer[i++] = maxY; boxVertexBuffer[i++] = minZ;
        boxVertexBuffer[i++] = r; boxVertexBuffer[i++] = g; boxVertexBuffer[i++] = b; boxVertexBuffer[i++] = a;
        boxVertexBuffer[i++] = maxX; boxVertexBuffer[i++] = maxY; boxVertexBuffer[i++] = maxZ;
        boxVertexBuffer[i++] = r; boxVertexBuffer[i++] = g; boxVertexBuffer[i++] = b; boxVertexBuffer[i++] = a;
        boxVertexBuffer[i++] = maxX; boxVertexBuffer[i++] = maxY; boxVertexBuffer[i++] = minZ;
        boxVertexBuffer[i++] = r; boxVertexBuffer[i++] = g; boxVertexBuffer[i++] = b; boxVertexBuffer[i++] = a;

        // North face (z = minZ) - 6 vertices
        boxVertexBuffer[i++] = minX; boxVertexBuffer[i++] = minY; boxVertexBuffer[i++] = minZ;
        boxVertexBuffer[i++] = r; boxVertexBuffer[i++] = g; boxVertexBuffer[i++] = b; boxVertexBuffer[i++] = a;
        boxVertexBuffer[i++] = minX; boxVertexBuffer[i++] = maxY; boxVertexBuffer[i++] = minZ;
        boxVertexBuffer[i++] = r; boxVertexBuffer[i++] = g; boxVertexBuffer[i++] = b; boxVertexBuffer[i++] = a;
        boxVertexBuffer[i++] = maxX; boxVertexBuffer[i++] = maxY; boxVertexBuffer[i++] = minZ;
        boxVertexBuffer[i++] = r; boxVertexBuffer[i++] = g; boxVertexBuffer[i++] = b; boxVertexBuffer[i++] = a;
        boxVertexBuffer[i++] = minX; boxVertexBuffer[i++] = minY; boxVertexBuffer[i++] = minZ;
        boxVertexBuffer[i++] = r; boxVertexBuffer[i++] = g; boxVertexBuffer[i++] = b; boxVertexBuffer[i++] = a;
        boxVertexBuffer[i++] = maxX; boxVertexBuffer[i++] = maxY; boxVertexBuffer[i++] = minZ;
        boxVertexBuffer[i++] = r; boxVertexBuffer[i++] = g; boxVertexBuffer[i++] = b; boxVertexBuffer[i++] = a;
        boxVertexBuffer[i++] = maxX; boxVertexBuffer[i++] = minY; boxVertexBuffer[i++] = minZ;
        boxVertexBuffer[i++] = r; boxVertexBuffer[i++] = g; boxVertexBuffer[i++] = b; boxVertexBuffer[i++] = a;

        // South face (z = maxZ) - 6 vertices
        boxVertexBuffer[i++] = minX; boxVertexBuffer[i++] = minY; boxVertexBuffer[i++] = maxZ;
        boxVertexBuffer[i++] = r; boxVertexBuffer[i++] = g; boxVertexBuffer[i++] = b; boxVertexBuffer[i++] = a;
        boxVertexBuffer[i++] = maxX; boxVertexBuffer[i++] = minY; boxVertexBuffer[i++] = maxZ;
        boxVertexBuffer[i++] = r; boxVertexBuffer[i++] = g; boxVertexBuffer[i++] = b; boxVertexBuffer[i++] = a;
        boxVertexBuffer[i++] = maxX; boxVertexBuffer[i++] = maxY; boxVertexBuffer[i++] = maxZ;
        boxVertexBuffer[i++] = r; boxVertexBuffer[i++] = g; boxVertexBuffer[i++] = b; boxVertexBuffer[i++] = a;
        boxVertexBuffer[i++] = minX; boxVertexBuffer[i++] = minY; boxVertexBuffer[i++] = maxZ;
        boxVertexBuffer[i++] = r; boxVertexBuffer[i++] = g; boxVertexBuffer[i++] = b; boxVertexBuffer[i++] = a;
        boxVertexBuffer[i++] = maxX; boxVertexBuffer[i++] = maxY; boxVertexBuffer[i++] = maxZ;
        boxVertexBuffer[i++] = r; boxVertexBuffer[i++] = g; boxVertexBuffer[i++] = b; boxVertexBuffer[i++] = a;
        boxVertexBuffer[i++] = minX; boxVertexBuffer[i++] = maxY; boxVertexBuffer[i++] = maxZ;
        boxVertexBuffer[i++] = r; boxVertexBuffer[i++] = g; boxVertexBuffer[i++] = b; boxVertexBuffer[i++] = a;

        // West face (x = minX) - 6 vertices
        boxVertexBuffer[i++] = minX; boxVertexBuffer[i++] = minY; boxVertexBuffer[i++] = minZ;
        boxVertexBuffer[i++] = r; boxVertexBuffer[i++] = g; boxVertexBuffer[i++] = b; boxVertexBuffer[i++] = a;
        boxVertexBuffer[i++] = minX; boxVertexBuffer[i++] = minY; boxVertexBuffer[i++] = maxZ;
        boxVertexBuffer[i++] = r; boxVertexBuffer[i++] = g; boxVertexBuffer[i++] = b; boxVertexBuffer[i++] = a;
        boxVertexBuffer[i++] = minX; boxVertexBuffer[i++] = maxY; boxVertexBuffer[i++] = maxZ;
        boxVertexBuffer[i++] = r; boxVertexBuffer[i++] = g; boxVertexBuffer[i++] = b; boxVertexBuffer[i++] = a;
        boxVertexBuffer[i++] = minX; boxVertexBuffer[i++] = minY; boxVertexBuffer[i++] = minZ;
        boxVertexBuffer[i++] = r; boxVertexBuffer[i++] = g; boxVertexBuffer[i++] = b; boxVertexBuffer[i++] = a;
        boxVertexBuffer[i++] = minX; boxVertexBuffer[i++] = maxY; boxVertexBuffer[i++] = maxZ;
        boxVertexBuffer[i++] = r; boxVertexBuffer[i++] = g; boxVertexBuffer[i++] = b; boxVertexBuffer[i++] = a;
        boxVertexBuffer[i++] = minX; boxVertexBuffer[i++] = maxY; boxVertexBuffer[i++] = minZ;
        boxVertexBuffer[i++] = r; boxVertexBuffer[i++] = g; boxVertexBuffer[i++] = b; boxVertexBuffer[i++] = a;

        // East face (x = maxX) - 6 vertices
        boxVertexBuffer[i++] = maxX; boxVertexBuffer[i++] = minY; boxVertexBuffer[i++] = minZ;
        boxVertexBuffer[i++] = r; boxVertexBuffer[i++] = g; boxVertexBuffer[i++] = b; boxVertexBuffer[i++] = a;
        boxVertexBuffer[i++] = maxX; boxVertexBuffer[i++] = maxY; boxVertexBuffer[i++] = minZ;
        boxVertexBuffer[i++] = r; boxVertexBuffer[i++] = g; boxVertexBuffer[i++] = b; boxVertexBuffer[i++] = a;
        boxVertexBuffer[i++] = maxX; boxVertexBuffer[i++] = maxY; boxVertexBuffer[i++] = maxZ;
        boxVertexBuffer[i++] = r; boxVertexBuffer[i++] = g; boxVertexBuffer[i++] = b; boxVertexBuffer[i++] = a;
        boxVertexBuffer[i++] = maxX; boxVertexBuffer[i++] = minY; boxVertexBuffer[i++] = minZ;
        boxVertexBuffer[i++] = r; boxVertexBuffer[i++] = g; boxVertexBuffer[i++] = b; boxVertexBuffer[i++] = a;
        boxVertexBuffer[i++] = maxX; boxVertexBuffer[i++] = maxY; boxVertexBuffer[i++] = maxZ;
        boxVertexBuffer[i++] = r; boxVertexBuffer[i++] = g; boxVertexBuffer[i++] = b; boxVertexBuffer[i++] = a;
        boxVertexBuffer[i++] = maxX; boxVertexBuffer[i++] = minY; boxVertexBuffer[i++] = maxZ;
        boxVertexBuffer[i++] = r; boxVertexBuffer[i++] = g; boxVertexBuffer[i++] = b; boxVertexBuffer[i++] = a;

        return boxVertexBuffer; // Return shared buffer - caller must not modify!
    }

    /**
     * Build vertex data for box outlines (12 edges)
     */
    private static float[] buildBoxOutlineVertices(float minX, float minY, float minZ,
                                                   float maxX, float maxY, float maxZ,
                                                   float r, float g, float b, float a) {
        float[][] corners = new float[][]{
            {minX, minY, minZ},
            {maxX, minY, minZ},
            {maxX, minY, maxZ},
            {minX, minY, maxZ},
            {minX, maxY, minZ},
            {maxX, maxY, minZ},
            {maxX, maxY, maxZ},
            {minX, maxY, maxZ}
        };

        int[][] edges = new int[][]{
            {0, 1}, {1, 2}, {2, 3}, {3, 0}, // Bottom
            {4, 5}, {5, 6}, {6, 7}, {7, 4}, // Top
            {0, 4}, {1, 5}, {2, 6}, {3, 7}  // Vertical
        };

        float[] result = new float[edges.length * 2 * 7];
        int idx = 0;

        for (int[] edge : edges) {
            float[] start = corners[edge[0]];
            float[] end = corners[edge[1]];

            idx = appendVertex(result, idx, start, r, g, b, a);
            idx = appendVertex(result, idx, end, r, g, b, a);
        }

        return result;
    }

    private static int appendVertex(float[] data, int index, float[] pos, float r, float g, float b, float a) {
        data[index++] = pos[0];
        data[index++] = pos[1];
        data[index++] = pos[2];
        data[index++] = r;
        data[index++] = g;
        data[index++] = b;
        data[index++] = a;
        return index;
    }

    /**
     * Uploads vertex data and draws it with the provided mode.
     */
    private static void drawBoxGeometry(float[] vertices, int mode) {
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, boxVBO);
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, vertices, GL30.GL_DYNAMIC_DRAW);
        GL30.glBindVertexArray(boxVAO);
        GL11.glDrawArrays(mode, 0, vertices.length / 7);
        GL30.glBindVertexArray(0);
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, 0);
    }

    private static void clearGlErrors() {
        // Clear any pending GL errors
        int errorCount = 0;
        while (GL11.glGetError() != GL11.GL_NO_ERROR) {
            errorCount++;
            if (errorCount > 100) break; // Safety limit
        }
    }


    /**
     * Apply Gaussian blur pass using custom shader
     */
    private static void applyBlur(int inputTexture, int outputFBO, boolean horizontal, int width, int height, float blurRadius) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, outputFBO);
        GL11.glViewport(0, 0, width, height);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

        // Bind input texture
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, inputTexture);

        // Use blur shader
        blurShader.use();
        blurShader.setTexture(0);
        blurShader.setDirection(horizontal ? 1.0f : 0.0f, horizontal ? 0.0f : 1.0f);
        blurShader.setRadius(blurRadius);
        blurShader.setResolution(width, height);

        // Render full-screen quad
        GL30.glBindVertexArray(quadVAO);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
        GL30.glBindVertexArray(0);

        // Unbind everything to avoid interfering with Minecraft rendering
        GL20.glUseProgram(0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    /**
     * Composite the blurred glow onto the main framebuffer using additive blending
     */
    private static void compositeGlow(int glowTexture, int targetFBO, int width, int height, float intensity) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, targetFBO);
        GL11.glViewport(0, 0, width, height);

        // Enable additive blending for glow effect
        GL11.glEnable(GL11.GL_BLEND);
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO); // Additive blending
        GlStateManager._disableDepthTest();

        // Bind glow texture
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, glowTexture);

        // Save current texture parameters
        int prevMinFilter = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER);
        int prevMagFilter = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER);

        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);

        // Use blur shader with minimal blur (radius 1) to ensure it renders
        blurShader.use();
        blurShader.setTexture(0);
        blurShader.setDirection(0.001f, 0.001f); // Minimal direction to trigger shader
        blurShader.setRadius(1.0f); // Minimal radius
        blurShader.setResolution(width, height);

        GL30.glBindVertexArray(quadVAO);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
        GL30.glBindVertexArray(0);

        // Restore texture parameters
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, prevMinFilter);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, prevMagFilter);

        // Clean up
        GL20.glUseProgram(0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    /**
     * Clean up resources
     */
    public static void cleanup() {
        if (glowFBO != -1) GL30.glDeleteFramebuffers(glowFBO);
        if (glowTexture != -1) GL11.glDeleteTextures(glowTexture);
        if (blurFBO1 != -1) GL30.glDeleteFramebuffers(blurFBO1);
        if (blurTexture1 != -1) GL11.glDeleteTextures(blurTexture1);
        if (blurFBO2 != -1) GL30.glDeleteFramebuffers(blurFBO2);
        if (blurTexture2 != -1) GL11.glDeleteTextures(blurTexture2);

        if (blurShader != null) blurShader.cleanup();
        if (boxShader != null) boxShader.cleanup();

        if (quadVAO != 0) GL30.glDeleteVertexArrays(quadVAO);
        if (quadVBO != 0) GL20.glDeleteBuffers(quadVBO);
        if (boxVAO != 0) GL30.glDeleteVertexArrays(boxVAO);
        if (boxVBO != 0) GL30.glDeleteBuffers(boxVBO);

        initialized = false;
    }

    /**
     * Task for rendering a glow object
     */
    private static class GlowRenderTask {
        final PoseStack matrices;
        final AABB box;
        final float[] color;
        final float glowIntensity;
        final float glowRadius;

        GlowRenderTask(PoseStack matrices, AABB box, float[] color, float glowIntensity, float glowRadius) {
            this.matrices = matrices;
            this.box = box;
            this.color = color;
            this.glowIntensity = glowIntensity;
            this.glowRadius = glowRadius;
        }
    }
}
