package dev.hunchclient.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.hunchclient.bridge.ModuleBridge;
import dev.hunchclient.bridge.module.IRenderOptimize;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LightningBoltRenderer;
import net.minecraft.client.renderer.entity.state.LightningBoltRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(LightningBoltRenderer.class)
public class LightningEntityRendererMixin {

    /**
     * MC 1.21: submit(LightningBoltRenderState, PoseStack, SubmitNodeCollector, CameraRenderState)
     */
    @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
    private void hunchclient$disableLightning(LightningBoltRenderState state, PoseStack matrices, SubmitNodeCollector submitNodeCollector, CameraRenderState camera, CallbackInfo ci) {
        IRenderOptimize ro = ModuleBridge.renderOpt();
        if (ro != null && ro.shouldRemoveLightning()) {
            ci.cancel();
        }
    }
}
