package dev.hunchclient.mixin.client;

import dev.hunchclient.module.ModuleManager;
import dev.hunchclient.module.impl.FreecamModule;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to prevent interactions while in Freecam mode.
 * This prevents packets from being sent when clicking blocks/entities
 * that would be unreachable from the player's actual position.
 */
@Mixin(Minecraft.class)
public class FreecamMinecraftMixin {

    /**
     * Prevents attacks when in freecam (unless player control is enabled).
     */
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void hunchclient$onStartAttack(CallbackInfoReturnable<Boolean> cir) {
        if (hunchclient$shouldBlockInteraction()) {
            cir.setReturnValue(false);
        }
    }

    /**
     * Prevents item pick (middle click) when in freecam.
     */
    @Inject(method = "pickBlock", at = @At("HEAD"), cancellable = true)
    private void hunchclient$onPickBlock(CallbackInfo ci) {
        if (hunchclient$shouldBlockInteraction()) {
            ci.cancel();
        }
    }

    /**
     * Prevents block breaking when in freecam.
     */
    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void hunchclient$onContinueAttack(boolean bl, CallbackInfo ci) {
        if (hunchclient$shouldBlockInteraction()) {
            ci.cancel();
        }
    }

    /**
     * Prevents using items/blocks when in freecam.
     */
    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void hunchclient$onStartUseItem(CallbackInfo ci) {
        if (hunchclient$shouldBlockInteraction()) {
            ci.cancel();
        }
    }

    /**
     * Check if we should block interactions.
     * Returns true if freecam is enabled and player control is NOT enabled.
     */
    @Unique
    private static boolean hunchclient$shouldBlockInteraction() {
        FreecamModule freecam = ModuleManager.getInstance().getModule(FreecamModule.class);
        return freecam != null && freecam.isFreecamEnabled() && !freecam.isPlayerControlEnabled();
    }
}
