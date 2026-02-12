package dev.hunchclient.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * Block outline renderer with CUSTOM Gaussian blur using RAW OpenGL.
 * NO MINECRAFT WRAPPERS - pure LWJGL implementation as per the German guide.
 */
public final class RawGLBlockOutlineBlur {
    private static final Minecraft mc = Minecraft.getInstance();

    private boolean enabled = true;
    private float[] outlineColor = {1.0f, 1.0f, 1.0f, 1.0f};
    private float blurRadius = 4.0f;
    private float threshold = 0.3f;

    // OpenGL resources
    private GaussianBlurShader blurShader;
    private int maskFBO = -1;
    private int maskTexture = -1;
    private int blurAFBO = -1;
    private int blurATexture = -1;
    private int blurBFBO = -1;
    private int blurBTexture = -1;
    private int quadVAO = -1;
    private int quadVBO = -1;

    private int lastWidth = -1;
    private int lastHeight = -1;

    public RawGLBlockOutlineBlur() {
    }

    /**
     * Main render method following the German guide's pipeline:
     * 1. Render block to mask FBO (white)
     * 2. Horizontal blur: mask -> blurA
     * 3. Vertical blur: blurA -> blurB
     * 4. Composite: blurB + mask -> screen
     */
    public void render(PoseStack matrices, Camera camera, MultiBufferSource.BufferSource vertexConsumers, float tickDelta) {
        if (!enabled || mc.level == null || mc.player == null) return;

        // Get the block the player is looking at
        HitResult hitResult = mc.hitResult;
        if (hitResult == null || hitResult.getType() != HitResult.Type.BLOCK) {
            return;
        }

        BlockHitResult blockHit = (BlockHitResult) hitResult;
        BlockPos pos = blockHit.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir()) return;

        VoxelShape shape = state.getShape(mc.level, pos, CollisionContext.of(mc.player));
        if (shape.isEmpty()) return;

        // Initialize OpenGL resources
        initGL();

        // Save GL state - CRITICAL: We must restore to prevFBO, NOT 0!
        int prevFBO = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int prevTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);

        try {
            // STEP 1: Render mask
            renderMaskPass(matrices, camera, vertexConsumers, pos, shape);

            // STEP 2 & 3: Apply blur
            applyBlur();

            // STEP 4: Composite to Minecraft's active framebuffer (NOT screen!)
            compositeToFramebuffer(prevFBO);

        } finally {
            // Restore GL state
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFBO);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTexture);
            GL20.glUseProgram(0);
        }
    }

    /**
     * Step 1: Render block outline as white mask into maskFBO
     */
    private void renderMaskPass(PoseStack matrices, Camera camera, MultiBufferSource.BufferSource vertexConsumers,
                                  BlockPos pos, VoxelShape shape) {
        // Bind mask FBO
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, maskFBO);
        GL11.glClearColor(0, 0, 0, 0);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

        // Save GL state
        float prevLineWidth = GL11.glGetFloat(GL11.GL_LINE_WIDTH);
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);

        // Set thick lines for better blur effect
        GL11.glLineWidth(4.0f);
        GL11.glDisable(GL11.GL_DEPTH_TEST); // Prevent z-fighting flicker

        // Render block outline in white
        Vec3 cameraPos = camera.getPosition();
        matrices.pushPose();
        matrices.translate(
            pos.getX() - cameraPos.x,
            pos.getY() - cameraPos.y,
            pos.getZ() - cameraPos.z
        );

        Matrix4f matrix = matrices.last().pose();
        VertexConsumer buffer = vertexConsumers.getBuffer(RenderType.lines());

        shape.forAllEdges((minX, minY, minZ, maxX, maxY, maxZ) -> {
            buffer.addVertex(matrix, (float)minX, (float)minY, (float)minZ).setColor(255, 255, 255, 255).setNormal(0, 0, 0);
            buffer.addVertex(matrix, (float)maxX, (float)maxY, (float)maxZ).setColor(255, 255, 255, 255).setNormal(0, 0, 0);
        });

        vertexConsumers.endBatch();
        matrices.popPose();

        // Restore GL state
        GL11.glLineWidth(prevLineWidth);
        if (depthTest) GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    /**
     * Steps 2 & 3: Apply separable Gaussian blur
     */
    private void applyBlur() {
        int width = mc.getWindow().getWidth();
        int height = mc.getWindow().getHeight();

        blurShader.use();
        blurShader.setResolution(width, height);
        blurShader.setRadius(blurRadius);

        // Pass 1: Horizontal blur (mask -> blurA)
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, blurAFBO);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, maskTexture);
        blurShader.setTexture(0);
        blurShader.setDirection(1.0f, 0.0f); // Horizontal

        renderFullscreenQuad();

        // Pass 2: Vertical blur (blurA -> blurB)
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, blurBFBO);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, blurATexture);
        blurShader.setTexture(0);
        blurShader.setDirection(0.0f, 1.0f); // Vertical

        renderFullscreenQuad();
    }

    /**
     * Step 4: Composite blurred result to Minecraft's active framebuffer
     * CRITICAL: We MUST render to the current FBO, NOT FBO 0!
     * FBO 0 causes GL_INVALID_VALUE in Minecraft's FrameGraph system!
     */
    private void compositeToFramebuffer(int targetFBO) {
        // Bind Minecraft's current framebuffer (NOT 0!)
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, targetFBO);

        // Enable blending for outline
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        // Draw blurred texture
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, blurBTexture);
        GL20.glUseProgram(0); // Use fixed-function pipeline for simple blit

        renderFullscreenQuad();

        GL11.glDisable(GL11.GL_BLEND);
    }

    /**
     * Render a fullscreen quad
     */
    private void renderFullscreenQuad() {
        GL30.glBindVertexArray(quadVAO);
        GL11.glDrawArrays(GL11.GL_TRIANGLE_STRIP, 0, 4);
        GL30.glBindVertexArray(0);
    }

    /**
     * Initialize all OpenGL resources
     */
    private void initGL() {
        int width = mc.getWindow().getWidth();
        int height = mc.getWindow().getHeight();

        // Check if resize needed
        if (width != lastWidth || height != lastHeight) {
            cleanupGL();
            lastWidth = width;
            lastHeight = height;
        }

        // Create shader
        if (blurShader == null) {
            blurShader = new GaussianBlurShader();
        }

        // Create FBOs
        if (maskFBO == -1) {
            maskFBO = createFBO(width, height);
            maskTexture = getLastCreatedTexture();
        }
        if (blurAFBO == -1) {
            blurAFBO = createFBO(width, height);
            blurATexture = getLastCreatedTexture();
        }
        if (blurBFBO == -1) {
            blurBFBO = createFBO(width, height);
            blurBTexture = getLastCreatedTexture();
        }

        // Create fullscreen quad VAO
        if (quadVAO == -1) {
            createFullscreenQuad();
        }
    }

    private int lastCreatedTexture;

    private int createFBO(int width, int height) {
        int fbo = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);

        // Create color texture
        int texture = GL11.glGenTextures();
        lastCreatedTexture = texture;

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);

        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 0);

        if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
            System.err.println("[BlockOutlineBlur] FBO not complete!");
        }

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        return fbo;
    }

    private int getLastCreatedTexture() {
        return lastCreatedTexture;
    }

    private void createFullscreenQuad() {
        // Fullscreen quad vertices: position (xy) + texcoord (uv)
        float[] vertices = {
            -1, -1,  0, 0,
             1, -1,  1, 0,
            -1,  1,  0, 1,
             1,  1,  1, 1
        };

        quadVAO = GL30.glGenVertexArrays();
        quadVBO = GL20.glGenBuffers();

        GL30.glBindVertexArray(quadVAO);
        GL20.glBindBuffer(GL20.GL_ARRAY_BUFFER, quadVBO);
        GL20.glBufferData(GL20.GL_ARRAY_BUFFER, vertices, GL20.GL_STATIC_DRAW);

        // Position attribute
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 4 * Float.BYTES, 0);
        GL20.glEnableVertexAttribArray(0);

        // TexCoord attribute
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 4 * Float.BYTES, 2 * Float.BYTES);
        GL20.glEnableVertexAttribArray(1);

        GL30.glBindVertexArray(0);
    }

    private void cleanupGL() {
        if (maskFBO != -1) GL30.glDeleteFramebuffers(maskFBO);
        if (maskTexture != -1) GL11.glDeleteTextures(maskTexture);
        if (blurAFBO != -1) GL30.glDeleteFramebuffers(blurAFBO);
        if (blurATexture != -1) GL11.glDeleteTextures(blurATexture);
        if (blurBFBO != -1) GL30.glDeleteFramebuffers(blurBFBO);
        if (blurBTexture != -1) GL11.glDeleteTextures(blurBTexture);
        if (quadVAO != -1) GL30.glDeleteVertexArrays(quadVAO);
        if (quadVBO != -1) GL20.glDeleteBuffers(quadVBO);

        maskFBO = blurAFBO = blurBFBO = -1;
        maskTexture = blurATexture = blurBTexture = -1;
        quadVAO = quadVBO = -1;
    }

    // Configuration
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isEnabled() { return enabled; }
    public void setBlurRadius(float radius) { this.blurRadius = Math.max(1.0f, Math.min(16.0f, radius)); }
    public float getBlurRadius() { return blurRadius; }
    public void setThreshold(float threshold) { this.threshold = Math.max(0.0f, Math.min(1.0f, threshold)); }
    public float getThreshold() { return threshold; }
    public void setOutlineColor(float r, float g, float b, float a) {
        this.outlineColor[0] = r; this.outlineColor[1] = g;
        this.outlineColor[2] = b; this.outlineColor[3] = a;
    }
    public float[] getOutlineColor() { return outlineColor; }
}
