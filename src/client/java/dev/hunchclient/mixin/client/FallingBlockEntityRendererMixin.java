package dev.hunchclient.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.hunchclient.bridge.ModuleBridge;
import dev.hunchclient.bridge.module.IRenderOptimize;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.FallingBlockRenderer;
import net.minecraft.client.renderer.entity.state.FallingBlockRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FallingBlockRenderer.class)
public class FallingBlockEntityRendererMixin {

    @Inject(
        method = "submit(Lnet/minecraft/client/renderer/entity/state/FallingBlockRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void hideFallingBlock(FallingBlockRenderState renderState, PoseStack matrices, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraState, CallbackInfo ci) {
        IRenderOptimize ro = ModuleBridge.renderOpt();
        if (ro != null && ro.shouldHideFallingBlockEntities()) {
            ci.cancel();
        }
    }
}
