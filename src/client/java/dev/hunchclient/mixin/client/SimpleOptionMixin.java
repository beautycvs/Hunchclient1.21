package dev.hunchclient.mixin.client;

import dev.hunchclient.bridge.ModuleBridge;
import dev.hunchclient.bridge.module.IFullBright;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import net.minecraft.client.OptionInstance;

@Mixin(OptionInstance.UnitDouble.class)
public class SimpleOptionMixin {

    @Inject(method = "validateValue(Ljava/lang/Double;)Ljava/util/Optional;", at = @At("RETURN"), cancellable = true)
    public void removeGammaValidation(Double value, CallbackInfoReturnable<Optional<Double>> cir) {
        // Check if FullBright is enabled
        IFullBright fb = ModuleBridge.fullBright();
        if (fb != null && fb.isEnabled()) {
            if (value == 100.0) {
                cir.setReturnValue(Optional.of(100.0));
            }
        }
    }
}
