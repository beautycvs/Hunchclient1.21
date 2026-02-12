package dev.hunchclient.render;

import dev.hunchclient.render.primitive.PrimitiveCollectorImpl;
import dev.hunchclient.render.state.CameraRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.joml.Quaternionf;

/**
 * RenderHelper - manages the rendering pipeline
 * Based on Skyblocker 1.21.9 using Fabric API WorldRenderEvents
 */
public class RenderHelper {
    private static PrimitiveCollectorImpl collector = null;

    /**
     * Initialize the rendering system
     * Must be called during client initialization
     */
    public static void init() {
        // Register Fabric API WorldRenderEvents
        WorldRenderEvents.END_EXTRACTION.register(RenderHelper::startExtraction);

        // CRITICAL: Render boxes in AFTER_ENTITIES (early in render pipeline)
        // This ensures boxes are rendered BEFORE DarkMode post-processing
        WorldRenderEvents.AFTER_ENTITIES.register(RenderHelper::executeDraws);

        // NOTE: DarkMode rendering is now handled via GameRendererMixin
        // The mixin injects AFTER LevelRenderer.renderLevel() returns, which is
        // AFTER FrameGraphBuilder.execute() completes - this ensures Fabulous
        // graphics TransparencyChain has been fully composited before DarkMode runs.
        // The old END_MAIN event fired BEFORE execute(), breaking Fabulous graphics.
    }

    /**
     * START EXTRACTION PHASE
     * Called during END_EXTRACTION event
     * Modules register their primitives via WorldRenderExtractionCallback
     */
    private static void startExtraction(WorldExtractionContext context) {
        // Create new collector for this frame
        collector = new PrimitiveCollectorImpl();

        // Fire our custom extraction callback for modules to use
        WorldRenderExtractionCallback.EVENT.invoker().onExtract(collector);

        // End collection - no more primitives can be added
        collector.endCollection();
    }

    /**
     * EXECUTE DRAWS PHASE
     * Called during AFTER_ENTITIES event
     * Dispatches primitives to renderers and executes all draws
     */
    private static void executeDraws(WorldRenderContext context) {
        if (collector == null) return;

        try {
            // Get camera from game renderer
            Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
            if (camera == null) {
                collector = null;
                return;
            }

            // Build camera render state
            CameraRenderState cameraState = new CameraRenderState();
            cameraState.pos = camera.getPosition();
            cameraState.rotation = new Quaternionf(camera.rotation());
            cameraState.pitch = camera.getXRot();
            cameraState.yaw = camera.getYRot();

            // Dispatch primitives to individual renderers (they submit to batches)
            collector.dispatchPrimitivesToRenderers(cameraState);
            collector = null;

            // Execute all batched draws using the Renderer system
            Renderer.executeDraws();

            // Execute GPU-based post-processing glow pass
            GlowESPRenderer.renderGlowPass();

        } catch (Exception e) {
            System.err.println("[HunchClient] Error executing draws:");
            e.printStackTrace();
            collector = null;
        }
    }

    /**
     * Get the current primitive collector
     * Used by modules that need direct access
     */
    public static PrimitiveCollectorImpl getCollector() {
        return collector;
    }

    /**
     * Convert RGB 0-255 to 0-1 float
     */
    public static float toFloat(int colorComponent) {
        return colorComponent / 255.0f;
    }

    /**
     * Pack RGBA color into int
     */
    public static int packColor(int red, int green, int blue, int alpha) {
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }
}
