package dev.hunchclient.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.ResourceLocation;

import static dev.hunchclient.HunchClient.MOD_ID;

/**
 * Custom render pipelines used by the secret route renderer.
 * These pipelines disable the depth test so the overlays remain visible through blocks.
 */
public final class RouteRenderPipelines {

    public static final RenderPipeline FILLED_THROUGH_WALLS = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(ResourceLocation.fromNamespaceAndPath(MOD_ID, "pipeline/secret_route_filled"))
            // Keep geometry identical to vanilla debug boxes to avoid diagonal artefacts
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withCull(false)
            .build()
    );

    public static final RenderPipeline LINES_THROUGH_WALLS = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withLocation(ResourceLocation.fromNamespaceAndPath(MOD_ID, "pipeline/secret_route_lines"))
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withCull(false)
            .build()
    );

    private RouteRenderPipelines() {
    }
}
