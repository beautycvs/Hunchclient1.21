package dev.hunchclient.mixin.client;

import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.resources.SplashManager;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SplashManager.class)
public class SplashTextResourceSupplierMixin {
    @Inject(method = "getSplash", at = @At("HEAD"), cancellable = true)
    private void overrideSplashText(CallbackInfoReturnable<@Nullable SplashRenderer> cir) {
        // Always return "hunchclient" as splash text
        cir.setReturnValue(new SplashRenderer("hunchclient"));
    }
}
