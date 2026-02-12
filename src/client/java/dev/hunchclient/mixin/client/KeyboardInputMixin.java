package dev.hunchclient.mixin.client;

import dev.hunchclient.bridge.ModuleBridge;
import dev.hunchclient.bridge.module.IBonzoStaffHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(KeyboardInput.class)
public class KeyboardInputMixin {

    @Redirect(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/KeyMapping;isDown()Z",
            ordinal = 1
        )
    )
    private boolean hunchclient$forceBackwardForBonzoStaff(KeyMapping keyBinding) {
        // Get original backward state
        boolean originalBackward = keyBinding.isDown();

        // If BonzoStaffHelper wants to press S, return true
        IBonzoStaffHelper bsh = ModuleBridge.bonzoStaffHelper();
        if (bsh != null && bsh.shouldPressBackward()) {
            return true;
        }

        return originalBackward;
    }
}
