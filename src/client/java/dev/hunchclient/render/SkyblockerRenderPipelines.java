package dev.hunchclient.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.ResourceLocation;

import static dev.hunchclient.HunchClient.MOD_ID;

/**
 * Custom render pipelines for through-walls rendering
 * Based on Skyblocker 1.21.9 implementation
 */
public final class SkyblockerRenderPipelines {

    public static final RenderPipeline FILLED_THROUGH_WALLS = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(ResourceLocation.fromNamespaceAndPath(MOD_ID, "pipeline/filled_through_walls"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLE_STRIP)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .build()
    );

    public static final RenderPipeline LINES_THROUGH_WALLS = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withLocation(ResourceLocation.fromNamespaceAndPath(MOD_ID, "pipeline/lines_through_walls"))
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .build()
    );

    private SkyblockerRenderPipelines() {
    }
}
