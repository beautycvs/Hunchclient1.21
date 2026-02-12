package dev.hunchclient.mixin.client;

import dev.hunchclient.bridge.ModuleBridge;
import dev.hunchclient.bridge.module.ITerminalSolver;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppress vanilla slot highlight rendering while the custom terminal GUI is active.
 */
@Mixin(AbstractContainerScreen.class)
public class HandledScreenHighlightMixin {

    @Inject(method = "renderSlotHighlightBack", at = @At("HEAD"), cancellable = true)
    private void hunchclient$hideSlotHighlightBack(GuiGraphics context, CallbackInfo ci) {
        if (shouldHideHighlights()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderSlotHighlightFront", at = @At("HEAD"), cancellable = true)
    private void hunchclient$hideSlotHighlightFront(GuiGraphics context, CallbackInfo ci) {
        if (shouldHideHighlights()) {
            ci.cancel();
        }
    }

    private boolean shouldHideHighlights() {
        ITerminalSolver solver = ModuleBridge.terminalSolver();
        return solver != null && solver.isEnabled() && solver.getRenderType();
    }
}
