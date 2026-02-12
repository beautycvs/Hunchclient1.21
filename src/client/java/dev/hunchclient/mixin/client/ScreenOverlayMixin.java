package dev.hunchclient.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.hunchclient.bridge.ModuleBridge;
import dev.hunchclient.bridge.module.IRenderOptimize;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(ScreenEffectRenderer.class)
public class ScreenOverlayMixin {

    /**
     * Disable fire overlay when on fire
     * MC 1.21.10: renderFire(PoseStack, MultiBufferSource, TextureAtlasSprite)
     */
    @Inject(method = "renderFire", at = @At("HEAD"), cancellable = true)
    private static void hunchclient$disableFireOverlay(PoseStack matrices, MultiBufferSource bufferSource, TextureAtlasSprite sprite, CallbackInfo ci) {
        IRenderOptimize ro = ModuleBridge.renderOpt();
        if (ro != null && ro.shouldRemoveFireOverlay()) {
            ci.cancel();
        }
    }

    /**
     * Disable water overlay when underwater
     * MC 1.21.10: renderWater(Minecraft, PoseStack, MultiBufferSource)
     */
    @Inject(method = "renderWater", at = @At("HEAD"), cancellable = true)
    private static void hunchclient$disableWaterOverlay(Minecraft client, PoseStack matrices, MultiBufferSource bufferSource, CallbackInfo ci) {
        IRenderOptimize ro = ModuleBridge.renderOpt();
        if (ro != null && ro.shouldDisableWaterOverlay()) {
            ci.cancel();
        }
    }

    /**
     * Disable suffocating block overlay
     * MC 1.21.10: renderTex(TextureAtlasSprite, PoseStack, MultiBufferSource) - covers block overlay
     */
    @Inject(method = "renderTex", at = @At("HEAD"), cancellable = true)
    private static void hunchclient$disableSuffocatingOverlay(TextureAtlasSprite sprite, PoseStack matrices, MultiBufferSource bufferSource, CallbackInfo ci) {
        IRenderOptimize ro = ModuleBridge.renderOpt();
        if (ro != null && ro.shouldDisableSuffocatingOverlay()) {
            ci.cancel();
        }
    }
}
