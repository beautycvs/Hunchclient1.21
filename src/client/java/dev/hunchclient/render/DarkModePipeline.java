package dev.hunchclient.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.ResourceLocation;

/**
 * DarkMode RenderPipeline - Fullscreen overlay effect using new GPU abstraction
 * Replaces old OpenGL approach with RenderPass-compatible rendering
 */
public class DarkModePipeline {

    private static RenderPipeline darkModePipeline = null;
    private static boolean initAttempted = false;

    /**
     * Initialize and register the DarkMode pipeline
     * Uses lazy initialization since shader resources aren't available at class init time
     */
    public static void init() {
        if (initAttempted) return;
        initAttempted = true;

        try {
            // Register the fullscreen overlay pipeline
            darkModePipeline = RenderPipelines.register(
                RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)  // Use POSITION_COLOR_SNIPPET (no POSITION-only exists)
                    .withLocation(ResourceLocation.fromNamespaceAndPath("hunchclient", "pipeline/darkmode"))
                    .withVertexShader(ResourceLocation.fromNamespaceAndPath("hunchclient", "core/darkmode"))  // assets/hunchclient/shaders/core/darkmode.vsh
                    .withFragmentShader(ResourceLocation.fromNamespaceAndPath("hunchclient", "core/darkmode"))  // assets/hunchclient/shaders/core/darkmode.fsh
                    .withSampler("Sampler0")  // Main color texture
                    .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES)  // Position + Color (ignore color)
                    .withBlend(BlendFunction.TRANSLUCENT)  // Use TRANSLUCENT blend
                    .build()
            );

            System.out.println("[DarkModePipeline] Pipeline registered successfully!");

        } catch (Exception e) {
            System.err.println("[DarkModePipeline] Failed to register pipeline: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Get the DarkMode pipeline (auto-initializes if needed)
     */
    public static RenderPipeline getPipeline() {
        if (!initAttempted) {
            init();
        }
        return darkModePipeline;
    }

    /**
     * Check if pipeline is ready
     */
    public static boolean isReady() {
        return darkModePipeline != null;
    }

    /**
     * Cleanup (pipeline is registered globally, don't actually destroy it)
     */
    public static void cleanup() {
        initAttempted = false;
        darkModePipeline = null;
    }
}
