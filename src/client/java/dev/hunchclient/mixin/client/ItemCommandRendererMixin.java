package dev.hunchclient.mixin.client;

import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Mixin for ItemCommandRenderer - currently unused.
 * Item texture replacement is now handled in BatchingRenderCommandQueueMixin.
 */
@Mixin(ItemFeatureRenderer.class)
public class ItemCommandRendererMixin {
    // Empty - all logic moved to BatchingRenderCommandQueueMixin
}
