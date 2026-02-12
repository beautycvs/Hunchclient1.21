package dev.hunchclient.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * Custom RenderPipeline for rendering items with an overlay texture
 * that uses the item's shape as a mask.
 *
 * Uses lazy loading because shader resources aren't available at class init time
 */
public class ItemOverlayRenderLayer {

    private static final ResourceLocation SHADER_LOCATION = ResourceLocation.fromNamespaceAndPath("hunchclient", "item_overlay");

    /**
     * Lazily loaded overlay pipeline - created on first use after resources are loaded
     * Can't be static final because shader files aren't available at class init time!
     */
    private static RenderPipeline overlayPipeline = null;
    private static RenderType overlayRenderLayer = null;
    private static ResourceLocation lastTextureId = null;
    private static boolean initAttempted = false;

    /**
     * Initialize the custom render layer
     * This should be called when the overlay module is enabled
     */
    public static void init() {
        try {
            initAttempted = true;

            // Build and register the pipeline (lazily)
            if (overlayPipeline == null) {
                try {
                    overlayPipeline = RenderPipelines.register(
                        RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)  // Use ENTITY_SNIPPET for lighting/transforms
                            .withLocation(ResourceLocation.fromNamespaceAndPath("hunchclient", "pipeline/item_overlay"))  // Pipeline location
                            .withVertexShader(ResourceLocation.fromNamespaceAndPath("hunchclient", "core/item_overlay"))  // Vertex shader: assets/hunchclient/shaders/core/item_overlay.vsh
                            .withFragmentShader(ResourceLocation.fromNamespaceAndPath("hunchclient", "core/item_overlay"))  // Fragment shader: assets/hunchclient/shaders/core/item_overlay.fsh
                            .withSampler("Sampler0")  // Item texture (slot 0)
                            .withSampler("Sampler1")  // Overlay texture (slot 1)
                            .withSampler("Sampler2")  // Light map (slot 2)
                            .withVertexFormat(DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS)
                            .withBlend(BlendFunction.TRANSLUCENT)
                            .build()
                    );
                    System.out.println("[ItemOverlayRenderLayer] Pipeline registered successfully");
                } catch (Exception e) {
                    System.err.println("[ItemOverlayRenderLayer] Failed to register pipeline: " + e.getMessage());
                    e.printStackTrace();
                    return;
                }
            }

            // Get current overlay texture ID
            ResourceLocation overlayTextureId = OverlayTextureManager.getTextureId();
            lastTextureId = overlayTextureId;

            // Get the item atlas texture ID - items use the block atlas
            ResourceLocation itemAtlasId = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/atlas/blocks.png");

            // Create RenderLayer using the registered pipeline
            // Use custom OverlayTexturePhase that binds BOTH textures (item atlas + overlay)
            overlayRenderLayer = RenderType.create(
                "hunchclient_item_overlay",
                1536,  // Buffer size
                overlayPipeline,
                RenderType.CompositeState.builder()
                    .setTextureState(new OverlayTexturePhase(overlayTextureId, itemAtlasId))  // Bind both textures
                    .setLightmapState(RenderType.LIGHTMAP)  // Enable lightmap (Sampler2)
                    .setOverlayState(RenderType.OVERLAY)  // Enable overlay color
                    .setOutputState(RenderType.ITEM_ENTITY_TARGET)  // Use item entity framebuffer target!
                    .createCompositeState(true)  // Translucent
            );

            System.out.println("[ItemOverlayRenderLayer] Initialized successfully with texture: " + overlayTextureId);

        } catch (Exception e) {
            System.err.println("[ItemOverlayRenderLayer] Failed to initialize: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Get the custom render layer for item overlay
     * Automatically recreates the layer if the texture has changed
     */
    public static RenderType getItemOverlay() {
        // Auto-init if not done yet
        if (!initAttempted) {
            init();
        }

        // Check if texture has changed and reinit if needed
        ResourceLocation currentTextureId = OverlayTextureManager.getTextureId();
        if (currentTextureId != null && !currentTextureId.equals(lastTextureId)) {
            System.out.println("[ItemOverlayRenderLayer] Texture changed, reinitializing...");
            cleanup();
            init();
        }

        return overlayRenderLayer;
    }

    /**
     * Get the overlay texture identifier for binding
     */
    public static ResourceLocation getOverlayTextureId() {
        return OverlayTextureManager.getTextureId();
    }

    /**
     * Check if pipeline is loaded and ready
     */
    public static boolean isReady() {
        return overlayPipeline != null && overlayRenderLayer != null;
    }

    /**
     * Get the shader ID for resource loading
     */
    public static ResourceLocation getShaderId() {
        return SHADER_LOCATION;
    }

    /**
     * Cleanup pipeline resources
     */
    public static void cleanup() {
        overlayRenderLayer = null;
        lastTextureId = null;
        initAttempted = false;
        // Don't cleanup the pipeline itself - it's registered globally
    }
}
