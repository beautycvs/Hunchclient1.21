package dev.hunchclient.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.hunchclient.bridge.ModuleBridge;
import dev.hunchclient.bridge.module.IRenderOptimize;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to hide armor rendering on players.
 * New feature for hunchclient - MC 1.21 compatible.
 */
@Mixin(HumanoidArmorLayer.class)
public class ArmorFeatureRendererMixin<S extends HumanoidRenderState> {

    @Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V",
            at = @At("HEAD"), cancellable = true)
    private void hunchclient$hideArmor(PoseStack matrixStack, SubmitNodeCollector submitNodeCollector, int light, S state, float limbAngle, float limbDistance, CallbackInfo ci) {
        IRenderOptimize ro = ModuleBridge.renderOpt();
        if (ro == null) return;
        int mode = ro.getRemoveArmorMode();
        if (mode == 0) return; // Disabled

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        // Mode 2: All Players - hide all player armor
        if (mode == 2) {
            ci.cancel();
            return;
        }

        // Mode 1: Self Only - check if this is the local player
        // BipedEntityRenderState has bodyYaw which we can compare with player's bodyYaw
        if (mode == 1 && state != null) {
            // Use bodyYaw comparison as a heuristic for self-detection
            float playerBodyYaw = client.player.yBodyRot;
            if (Math.abs(state.bodyRot - playerBodyYaw) < 0.1f) {
                ci.cancel();
            }
        }
    }
}
