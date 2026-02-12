package dev.hunchclient.mixin.client;

import dev.hunchclient.HunchModClient;
import dev.hunchclient.event.GuiEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to fire GuiEvent.Open when a screen is opened
 * Needed for F7Sim and other client-side screens
 */
@Mixin(Minecraft.class)
public class ScreenOpenEventMixin {

    /**
     * Fire GuiEvent.Open when setScreen is called
     */
    @Inject(method = "setScreen", at = @At("HEAD"))
    private void hunchclient$onScreenOpen(Screen screen, CallbackInfo ci) {
        if (screen != null) {
            GuiEvent.Open event = GuiEvent.Open.of(screen);
            HunchModClient.EVENT_BUS.post(event);
        } else {
            // Screen closed - fire Close event
            @SuppressWarnings("resource")
            Minecraft mc = (Minecraft) (Object) this;
            if (mc.screen != null) {
                GuiEvent.Close event = GuiEvent.Close.of(mc.screen);
                HunchModClient.EVENT_BUS.post(event);
            }
        }
    }
}
