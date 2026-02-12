package dev.hunchclient.mixin.client;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor mixin to access package-private fields in ItemRenderState
 */
@Mixin(ItemStackRenderState.class)
public interface ItemRenderStateAccessor {
    @Accessor("displayContext")
    ItemDisplayContext getDisplayContext();
}

