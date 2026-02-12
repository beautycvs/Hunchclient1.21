package dev.hunchclient.mixin.client;

import dev.hunchclient.render.ScissorStateTracker;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to track scissor state for NVG custom font rendering.
 * Hooks into GuiGraphics.enableScissor/disableScissor to capture bounds
 * that can be applied to NVG text rendering.
 */
@Mixin(GuiGraphics.class)
public abstract class GuiScissorMixin {

    /**
     * Hook into enableScissor to track the bounds
     */
    @Inject(method = "enableScissor", at = @At("HEAD"))
    private void onEnableScissor(int x1, int y1, int x2, int y2, CallbackInfo ci) {
        ScissorStateTracker.pushScissor(x1, y1, x2, y2);
    }

    /**
     * Hook into disableScissor to clear the tracked bounds
     */
    @Inject(method = "disableScissor", at = @At("HEAD"))
    private void onDisableScissor(CallbackInfo ci) {
        ScissorStateTracker.popScissor();
    }
}
