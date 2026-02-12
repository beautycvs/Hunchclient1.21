package dev.hunchclient.mixin.client;

import dev.hunchclient.HunchModClient;
import dev.hunchclient.event.GuiEvent;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Emits GuiEvent.DrawTooltip so modules can cancel vanilla tooltips.
 * NOTE: The previous INVOKE target "GuiGraphics.renderTooltip()V" does not exist in Mojang mappings.
 * Using TAIL injection instead to fire event after render.
 */
@Mixin(Screen.class)
public class ScreenTooltipMixin {

    @Inject(
        method = "renderWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
        at = @At("TAIL")
    )
    private void hunchclient$fireTooltipEvent(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        if (screen instanceof AbstractContainerScreen<?> handledScreen) {
            GuiEvent.DrawTooltip event = GuiEvent.DrawTooltip.of(handledScreen, context, mouseX, mouseY);
            HunchModClient.EVENT_BUS.post(event);
            // Note: Cannot cancel at TAIL, event is for notification only
        }
    }
}
