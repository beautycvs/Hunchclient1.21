package dev.hunchclient.mixin.client;

import dev.hunchclient.mixininterface.IHiddenEntityMarker;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin into EntityRenderManager to hide non-starred entities
 */
@Mixin(EntityRenderDispatcher.class)
public class EntityRenderManagerMixin {

    /**
     * Skip rendering hidden entities at submit() method
     * Uses marker interface to check if entity should be hidden
     * Note: In 1.21.10 render() was renamed to submit()
     */
    @Inject(
        method = "submit",
        at = @At("HEAD"),
        cancellable = true
    )
    private void hunchclient$skipHiddenEntitiesRender(EntityRenderState state, net.minecraft.client.renderer.state.CameraRenderState cameraState, double x, double y, double z, com.mojang.blaze3d.vertex.PoseStack matrices, net.minecraft.client.renderer.SubmitNodeCollector collector, CallbackInfo ci) {
        if (state instanceof IHiddenEntityMarker && ((IHiddenEntityMarker) state).hunchclient$isHidden()) {
            ci.cancel(); // Skip rendering this entity
        }
    }
}
