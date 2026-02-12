package dev.hunchclient.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.hunchclient.bridge.ModuleBridge;
import dev.hunchclient.bridge.module.IDarkModeShader;
import dev.hunchclient.bridge.module.IStretch;
import dev.hunchclient.bridge.module.IViewmodelOverlay;
import dev.hunchclient.bridge.module.IRenderOptimize;
import dev.hunchclient.render.CustomFontRenderQueue;
import dev.hunchclient.render.CustomShaderManager;
import dev.hunchclient.render.DarkModeRenderer;
import dev.hunchclient.util.FFmpegManager;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.server.packs.resources.ResourceProvider;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "getProjectionMatrix", at = @At("RETURN"), cancellable = true)
    private void hunchclient$adjustProjectionMatrix(float fov, CallbackInfoReturnable<Matrix4f> cir) {
        IStretch stretch = ModuleBridge.stretch();
        if (stretch == null || !stretch.shouldApplyStretch()) {
            return;
        }

        float scaleX = stretch.calculateHorizontalScale();
        if (Math.abs(scaleX - 1.0f) <= 0.001f) {
            return;
        }

        Matrix4f adjusted = new Matrix4f(cir.getReturnValue());
        adjusted.scale(scaleX, 1.0f, 1.0f);
        cir.setReturnValue(adjusted);
    }

    @Inject(method = "preloadUiShader(Lnet/minecraft/server/packs/resources/ResourceProvider;)V", at = @At("TAIL"))
    private void hunchclient$loadCustomShaders(ResourceProvider factory, CallbackInfo ci) {
        CustomShaderManager.loadShaders(factory);
    }

 
    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void hunchclient$disableHurtCamera(PoseStack matrices, float tickDelta, CallbackInfo ci) {
        IRenderOptimize ro = ModuleBridge.renderOpt();
        if (ro != null && ro.shouldDisableHurtCamera()) {
            ci.cancel();
        }
    }

    /**
     * Start collecting custom font text at the beginning of the frame
     */
    @Inject(method = "render", at = @At("HEAD"))
    private void hunchclient$startCustomFontCollecting(DeltaTracker tickCounter, boolean tick, CallbackInfo ci) {
        CustomFontRenderQueue.startCollecting();
    }

    /**
     * Render all queued custom font text at the end of the frame (after all GUI)
     */
    @Inject(method = "render", at = @At("TAIL"))
    private void hunchclient$renderQueuedCustomFont(DeltaTracker tickCounter, boolean tick, CallbackInfo ci) {
        if (minecraft != null && minecraft.getWindow() != null) {
            CustomFontRenderQueue.renderAll(
                minecraft.getWindow().getWidth(),
                minecraft.getWindow().getHeight()
            );

            // Native frame capture for Replay Buffer
            FFmpegManager ffmpeg = FFmpegManager.getInstance();
            if (ffmpeg.isRecording()) {
                ffmpeg.captureFrame(
                    minecraft.getWindow().getWidth(),
                    minecraft.getWindow().getHeight()
                );
            }
        }
    }

    /**
     * DarkMode rendering hook - injected AFTER LevelRenderer.renderLevel() completes.
     *
     * This is the CORRECT hook point for Fabulous graphics compatibility:
     * - LevelRenderer.renderLevel() calls FrameGraphBuilder.execute() which processes
     *   ALL scheduled passes including the Fabulous TransparencyChain
     * - After this method returns, the framebuffer contains the COMPLETE composited scene
     * - This runs BEFORE renderItemInHand(), so the viewmodel is not affected by DarkMode
     *
     * Previous approach using WorldRenderEvents.END_MAIN fired BEFORE FrameGraphBuilder.execute(),
     * which meant the transparency chain hadn't been composited yet, breaking Fabulous graphics.
     */
    @Inject(
        method = "renderLevel",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V",
            shift = At.Shift.AFTER
        )
    )
    private void hunchclient$renderDarkModeAfterLevel(DeltaTracker deltaTracker, CallbackInfo ci) {
        // Only render in first person and when module is enabled
        if (minecraft != null && minecraft.level != null && minecraft.options.getCameraType().isFirstPerson()) {
            IDarkModeShader darkModeModule = ModuleBridge.darkModeShader();
            if (darkModeModule != null && darkModeModule.isEnabled()) {
                DarkModeRenderer.renderDarkModeOverlay();
            }

            // Capture world depth BEFORE hands render (for depth-based overlay detection)
            IViewmodelOverlay overlayModule = ModuleBridge.viewmodelOverlay();
            if (overlayModule != null && overlayModule.isEnabled()) {
                dev.hunchclient.render.ViewmodelOverlayRenderer.captureWorldSnapshot();
            }
        }
    }

    /**
     * Viewmodel overlay - DISABLED at renderLevel RETURN
     * This point is TOO EARLY - hands haven't been GPU-rendered yet in deferred mode
     * Moving to render() method instead
     */
    // DISABLED - hands render AFTER this point in deferred rendering

    /**
     * Viewmodel overlay - injected in render() AFTER FeatureRenderDispatcher.endFrame()
     * In 1.21.10, ItemFeatureRenderer.render() is called during FeatureRenderDispatcher processing.
     * The stencil buffer is written by ItemFeatureRendererMixin during that call.
     * After endFrame(), we apply the overlay using the stencil mask.
     *
     * Hook point: After featureRenderDispatcher.endFrame() in render() method
     */
    @Inject(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;endFrame()V",
            shift = At.Shift.AFTER
        )
    )
    private void hunchclient$applyViewmodelOverlayAfterFeatures(DeltaTracker tickCounter, boolean tick, CallbackInfo ci) {
        if (minecraft != null && minecraft.level != null && minecraft.options.getCameraType().isFirstPerson()) {
            IViewmodelOverlay overlayModule2 = ModuleBridge.viewmodelOverlay();
            if (overlayModule2 != null && overlayModule2.isEnabled()) {
                // Stencil was written by ItemFeatureRendererMixin
                // Now apply overlay using the stencil mask
                dev.hunchclient.render.ViewmodelOverlayRenderer.applyOverlayPostProcess(
                    overlayModule2.getOverlayOpacity(),
                    overlayModule2.getParallaxIntensity()
                );
            }
        }
    }

}
