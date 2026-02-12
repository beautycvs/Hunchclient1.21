package dev.hunchclient.render;

import java.util.OptionalDouble;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

/**
 * Custom RenderLayers for glow effects (simplified - no custom shaders)
 */
public class CustomGlowRenderLayer {

    /**
     * Glow render layer - just uses standard filled box
     */
    public static RenderType getGlowFilled() {
        return RenderType.debugFilledBox();
    }

    /**
     * Glow render layer for lines with thick width
     */
    public static RenderType getGlowLines(double lineWidth) {
        return RenderType.create(
            "hunchclient_glow_lines",
            1536,
            RenderPipelines.LINES,
            RenderType.CompositeState.builder()
                .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.of(lineWidth)))
                .setLayeringState(RenderType.VIEW_OFFSET_Z_LAYERING)
                .setOutputState(RenderType.ITEM_ENTITY_TARGET)
                .createCompositeState(false)
        );
    }
}
