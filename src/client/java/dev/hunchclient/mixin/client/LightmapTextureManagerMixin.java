package dev.hunchclient.mixin.client;

import dev.hunchclient.bridge.ModuleBridge;
import dev.hunchclient.bridge.module.IFullBright;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LightTexture.class)
public class LightmapTextureManagerMixin {

    @Inject(method = "getBrightness(FI)F", at = @At("HEAD"), cancellable = true)
    private static void onGetBrightness(float ambientLight, int lightLevel, CallbackInfoReturnable<Float> cir) {
        // Check if FullBright is enabled
        IFullBright fb = ModuleBridge.fullBright();
        if (fb != null && fb.isEnabled()) {
            cir.setReturnValue(1.0F); // Maximum brightness
        }
    }
}
