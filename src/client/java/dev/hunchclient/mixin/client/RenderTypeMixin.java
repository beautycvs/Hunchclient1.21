package dev.hunchclient.mixin.client;

import dev.hunchclient.render.GalaxyGlintRenderLayer;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to intercept glint render layer getters.
 * When rendering first-person items with galaxy overlay enabled,
 * returns our custom galaxy texture layer instead of the normal glint.
 */
@Mixin(RenderType.class)
public abstract class RenderTypeMixin {

    // DISABLED - Galaxy glint approach causes black screen
    // All injection methods disabled until fixed
}
