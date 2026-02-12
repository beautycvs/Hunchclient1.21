package dev.hunchclient.render;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * Custom render layer that replaces the enchantment glint with a galaxy overlay.
 * This uses the same GLINT pipeline but with our custom texture instead of
 * enchanted_glint_item.png.
 *
 * Only active during first-person viewmodel rendering.
 */
public class GalaxyGlintRenderLayer {

    // Flag to track if we're currently rendering first-person items
    private static final ThreadLocal<Boolean> isRenderingFirstPerson = ThreadLocal.withInitial(() -> false);

    // Cached render layer - recreated when texture changes
    private static RenderType galaxyGlintLayer = null;
    private static RenderType galaxyGlintTranslucentLayer = null;
    private static RenderType galaxyEntityGlintLayer = null;
    private static ResourceLocation lastTextureId = null;

    /**
     * Set whether we're currently rendering first-person items.
     * Called from HeldItemRendererMixin.
     */
    public static void setRenderingFirstPerson(boolean firstPerson) {
        isRenderingFirstPerson.set(firstPerson);
    }

    /**
     * Check if we're currently rendering first-person items.
     */
    public static boolean isRenderingFirstPerson() {
        return isRenderingFirstPerson.get();
    }

    /**
     * Should we use the galaxy glint instead of normal glint?
     */
    public static boolean shouldUseGalaxyGlint() {
        // Only for first-person AND when overlay module is enabled AND texture is loaded
        if (!isRenderingFirstPerson.get()) {
            return false;
        }
        return OverlayTextureManager.hasTexture();
    }

    /**
     * Get the galaxy glint layer (replacement for RenderType.getGlint())
     */
    public static RenderType getGalaxyGlint() {
        ensureLayersCreated();
        return galaxyGlintLayer;
    }

    /**
     * Get the galaxy glint translucent layer (replacement for RenderType.getGlintTranslucent())
     */
    public static RenderType getGalaxyGlintTranslucent() {
        ensureLayersCreated();
        return galaxyGlintTranslucentLayer;
    }

    /**
     * Get the galaxy entity glint layer (replacement for RenderType.getEntityGlint())
     */
    public static RenderType getGalaxyEntityGlint() {
        ensureLayersCreated();
        return galaxyEntityGlintLayer;
    }

    /**
     * Ensure render layers are created with current texture
     */
    private static void ensureLayersCreated() {
        ResourceLocation currentTextureId = OverlayTextureManager.getTextureId();

        // Recreate layers if texture changed
        if (currentTextureId != null && !currentTextureId.equals(lastTextureId)) {
            createLayers(currentTextureId);
            lastTextureId = currentTextureId;
        }
    }

    /**
     * Create the galaxy glint render layers with the given texture
     */
    private static void createLayers(ResourceLocation textureId) {
        System.out.println("[GalaxyGlintRenderLayer] Creating layers with texture: " + textureId);

        // Create GLINT equivalent with our texture
        // Uses same pipeline (GLINT) but different texture
        galaxyGlintLayer = RenderType.create(
            "hunchclient_galaxy_glint",
            1536,
            RenderPipelines.GLINT,
            RenderType.CompositeState.builder()
                .setTextureState(new RenderStateShard.TextureStateShard(textureId, false))
                .setTexturingState(RenderType.GLINT_TEXTURING)
                .createCompositeState(false)
        );

        // Create GLINT_TRANSLUCENT equivalent
        galaxyGlintTranslucentLayer = RenderType.create(
            "hunchclient_galaxy_glint_translucent",
            1536,
            RenderPipelines.GLINT,
            RenderType.CompositeState.builder()
                .setTextureState(new RenderStateShard.TextureStateShard(textureId, false))
                .setTexturingState(RenderType.GLINT_TEXTURING)
                .setOutputState(RenderType.ITEM_ENTITY_TARGET)
                .createCompositeState(false)
        );

        // Create ENTITY_GLINT equivalent
        galaxyEntityGlintLayer = RenderType.create(
            "hunchclient_galaxy_entity_glint",
            1536,
            RenderPipelines.GLINT,
            RenderType.CompositeState.builder()
                .setTextureState(new RenderStateShard.TextureStateShard(textureId, false))
                .setTexturingState(RenderType.ENTITY_GLINT_TEXTURING)
                .createCompositeState(false)
        );

        System.out.println("[GalaxyGlintRenderLayer] Layers created successfully");
    }

    /**
     * Cleanup cached layers (call when texture changes or module disabled)
     */
    public static void cleanup() {
        galaxyGlintLayer = null;
        galaxyGlintTranslucentLayer = null;
        galaxyEntityGlintLayer = null;
        lastTextureId = null;
    }
}
