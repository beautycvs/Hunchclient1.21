package dev.hunchclient.mixin.client;

import dev.hunchclient.bridge.ModuleBridge;
import dev.hunchclient.bridge.module.IRenderOptimize;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow private Entity entity;
    @Shadow private float eyeHeight;
    @Shadow private float eyeHeightOld;

    @Unique
    private static final float STANDING_EYE_HEIGHT = 1.62f;
    @Unique
    private static final float SNEAK_EYE_HEIGHT = 1.27f;

    /**
     * Adjust camera height when sneaking based on the sneak camera amount setting
     * 0% = normal sneak camera (goes down)
     * 100% = 1.8.9 style (stays at standing height)
     *
     * Hook tick() because that's where eyeHeight is actually calculated:
     * eyeHeightOld = eyeHeight;
     * eyeHeight = eyeHeight + (entity.getEyeHeight() - eyeHeight) * 0.5f;
     */
    @Inject(method = "tick", at = @At("RETURN"))
    private void hunchclient$adjustSneakCameraHeight(CallbackInfo ci) {
        if (!(this.entity instanceof Player player)) {
            return;
        }

        IRenderOptimize ro = ModuleBridge.renderOpt();
        if (ro == null) return;
        float sneakAmount = ro.getSneakCameraAmount();

        // If disabled (0), let vanilla behavior happen
        if (sneakAmount <= 0.0f) {
            return;
        }

        // Only apply when player is sneaking
        if (player.isShiftKeyDown()) {
            // Calculate the target eye height based on sneak amount
            // sneakAmount = 0.0: use normal sneak height (1.27)
            // sneakAmount = 1.0: use standing height (1.62)
            float targetHeight = SNEAK_EYE_HEIGHT + (STANDING_EYE_HEIGHT - SNEAK_EYE_HEIGHT) * sneakAmount;

            // Override vanilla's interpolated eye height
            // Set both to same value to prevent interpolation jitter
            this.eyeHeightOld = targetHeight;
            this.eyeHeight = targetHeight;
        }
    }
}
