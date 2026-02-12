package dev.hunchclient.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.hunchclient.bridge.ModuleBridge;
import dev.hunchclient.bridge.module.IRenderOptimize;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to hide player head rendering on players (worn as helmet).
 */
@Mixin(CustomHeadLayer.class)
public class HeadFeatureRendererMixin {

    @Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;FF)V",
            at = @At("HEAD"), cancellable = true)
    private void hunchclient$hideHeadFeature(PoseStack matrixStack, SubmitNodeCollector submitNodeCollector, int light, LivingEntityRenderState state, float limbAngle, float limbDistance, CallbackInfo ci) {
        IRenderOptimize ro = ModuleBridge.renderOpt();
        if (ro == null) return;
        int mode = ro.getRemoveArmorMode();
        if (mode == 0) return;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        // Mode 2: All Players - hide all player head features
        if (mode == 2) {
            ci.cancel();
            return;
        }

        // Mode 1: Self Only
        if (mode == 1 && state != null) {
            float playerBodyYaw = client.player.yBodyRot;
            if (Math.abs(state.bodyRot - playerBodyYaw) < 0.1f) {
                ci.cancel();
            }
        }
    }
}
