package dev.hunchclient.mixin.client;

import dev.hunchclient.HunchModClient;
import dev.hunchclient.event.GuiEvent;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to fire GuiEvent for Terminal Solver custom rendering
 */
@Mixin(AbstractContainerScreen.class)
public class GuiEventMixin {

    /**
     * Fire GuiEvent.Close when screen is closed (ESC pressed or server closes it)
     * CRITICAL: This is needed for proper cleanup of CustomGui/TermGui state
     */
    @Inject(method = "onClose", at = @At("HEAD"))
    private void hunchclient$onClose(CallbackInfo ci) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        GuiEvent.Close event = GuiEvent.Close.of(screen);
        HunchModClient.EVENT_BUS.post(event);
    }

    /**
     * Fire GuiEvent.Removed when screen is removed from display
     * This is called when the server forcefully closes the inventory
     */
    @Inject(method = "removed", at = @At("HEAD"))
    private void hunchclient$onRemoved(CallbackInfo ci) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        GuiEvent.Removed event = GuiEvent.Removed.of(screen);
        HunchModClient.EVENT_BUS.post(event);
    }

    /**
     * Fire GuiEvent.Draw for custom terminal rendering
     */
    @Inject(method = "render", at = @At("TAIL"))
    private void hunchclient$onGuiDraw(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        GuiEvent.Draw event = GuiEvent.Draw.of(screen, context, mouseX, mouseY);
        HunchModClient.EVENT_BUS.post(event);
    }

    /**
     * Fire GuiEvent.DrawBackground before vanilla background rendering
     */
    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void hunchclient$onRenderBackground(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        GuiEvent.DrawBackground event = GuiEvent.DrawBackground.of(screen, context);
        HunchModClient.EVENT_BUS.post(event);
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    /**
     * Fire GuiEvent.DrawForeground for title text rendering
     */
    @Inject(method = "renderLabels", at = @At("HEAD"), cancellable = true)
    private void hunchclient$onDrawForeground(GuiGraphics context, int mouseX, int mouseY, CallbackInfo ci) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        GuiEvent.DrawForeground event = GuiEvent.DrawForeground.of(screen, context, mouseX, mouseY);
        HunchModClient.EVENT_BUS.post(event);
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    /**
     * Fire GuiEvent.DrawSlot when drawing slots (for terminal overlays)
     */
    @Inject(method = "renderSlot", at = @At("HEAD"), cancellable = true)
    private void hunchclient$onDrawSlot(GuiGraphics context, Slot slot, CallbackInfo ci) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        GuiEvent.DrawSlot event = GuiEvent.DrawSlot.of(screen, context, slot);
        HunchModClient.EVENT_BUS.post(event);
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    /**
     * Fire GuiEvent.MouseClick for terminal clicks
     * Note: mouseClicked returns boolean, so we need CallbackInfoReturnable
     * Updated for 1.21.10: mouseClicked now uses Click object
     */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void hunchclient$onMouseClick(net.minecraft.client.input.MouseButtonEvent click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        GuiEvent.MouseClick event = GuiEvent.MouseClick.of(screen, (int) click.x(), (int) click.y(), click.button());
        HunchModClient.EVENT_BUS.post(event);
        if (event.isCancelled()) {
            cir.setReturnValue(true);
            cir.cancel();
        }
    }
}
