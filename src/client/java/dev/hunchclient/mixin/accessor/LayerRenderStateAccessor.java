package dev.hunchclient.mixin.accessor;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * Accessor mixin for ItemStackRenderState.LayerRenderState
 * Used to access the quads, tints, and layer for second-pass overlay rendering
 */
@Mixin(ItemStackRenderState.LayerRenderState.class)
public interface LayerRenderStateAccessor {

    @Accessor("quads")
    List<BakedQuad> hunchclient$getQuads();

    @Accessor("tintLayers")
    int[] hunchclient$getTintLayers();

    @Accessor("renderType")
    RenderType hunchclient$getRenderType();

    @Accessor("foilType")
    ItemStackRenderState.FoilType hunchclient$getFoilType();
}
