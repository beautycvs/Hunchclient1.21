package dev.hunchclient.render;

import java.util.OptionalDouble;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

/**
 * Custom RenderLayers for thick block outlines with blur effect.
 */
public class CustomRenderLayers {

    // Custom thick outline layer with configurable line width
    public static RenderType getThickBlockOutline(double lineWidth) {
        return RenderType.create(
            "thick_block_outline",
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
