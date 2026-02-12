package dev.hunchclient.mixin.client;

import dev.hunchclient.bridge.module.IStretch;
import net.minecraft.client.renderer.CachedPerspectiveProjectionMatrixBuffer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// TODO: API completely changed in 1.21.10 Mojang mappings
// Old: getMatrix(int,int,float) returns Matrix4f
// New: getBuffer(int,int,float) returns GpuBufferSlice (GPU-side buffer, not Matrix4f)
// The Stretch module's projection matrix modification needs a new approach via GameRenderer.getProjectionMatrix
@Mixin(CachedPerspectiveProjectionMatrixBuffer.class)
public class ProjectionMatrix3Mixin {
    // Disabled - see GameRendererMixin for Stretch implementation
    // @Inject(method = "getMatrix(IIF)Lorg/joml/Matrix4f;", at = @At("RETURN"), cancellable = true)
    // private void hunchclient$scaleHudMatrix(int width, int height, float fov, CallbackInfoReturnable<Matrix4f> cir) {
    //     ...
    // }
}
