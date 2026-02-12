package dev.hunchclient.mixin.client;

import dev.hunchclient.freecam.FreeCamera;
import dev.hunchclient.module.ModuleManager;
import dev.hunchclient.module.impl.FreecamModule;
import dev.hunchclient.render.MobGlow;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes entities with custom glow appear as "glowing" to EntityCulling/MoreCulling mods
 * This prevents them from culling our starred mobs
 */
@Mixin(Entity.class)
public class EntityMixin {

    /**
     * Override hasGlowingTag() for vanilla glow checks
     */
    @Inject(method = "hasGlowingTag", at = @At("HEAD"), cancellable = true)
    private void hunchclient$customGlowHasGlowingTag(CallbackInfoReturnable<Boolean> cir) {
        Entity entity = (Entity) (Object) this;
        if (MobGlow.hasOrComputeMobGlow(entity)) {
            cir.setReturnValue(true);
        }
    }

    /**
     * Override isCurrentlyGlowing() - this is what EntityCulling mod actually checks!
     * In Mojang mappings: isCurrentlyGlowing() combines hasGlowingTag() + spectral arrow effect
     */
    @Inject(method = "isCurrentlyGlowing", at = @At("HEAD"), cancellable = true)
    private void hunchclient$customGlowIsCurrentlyGlowing(CallbackInfoReturnable<Boolean> cir) {
        Entity entity = (Entity) (Object) this;
        if (MobGlow.hasOrComputeMobGlow(entity)) {
            cir.setReturnValue(true);
        }
    }

    /**
     * Override shouldRenderAtSqrDistance() to force starred mobs to always render
     * regardless of distance (within reasonable limits)
     */
    @Inject(method = "shouldRenderAtSqrDistance", at = @At("HEAD"), cancellable = true)
    private void hunchclient$forceRenderDistance(double distanceSq, CallbackInfoReturnable<Boolean> cir) {
        Entity entity = (Entity) (Object) this;
        if (MobGlow.hasOrComputeMobGlow(entity)) {
            // Force render up to ~100 blocks (10000 = 100^2)
            if (distanceSq < 10000) {
                cir.setReturnValue(true);
            }
        }
    }

    /**
     * Redirects mouse rotation to FreeCamera when freecam is enabled.
     * This is the key to making mouse look work in freecam mode!
     */
    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void hunchclient$redirectFreecamTurn(double yaw, double pitch, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        FreecamModule freecam = ModuleManager.getInstance().getModule(FreecamModule.class);

        if (freecam != null && freecam.isFreecamEnabled() && !freecam.isPlayerControlEnabled()) {
            // Only redirect if this is the player entity
            if (this.equals(mc.player)) {
                FreeCamera camera = freecam.getFreeCamera();
                if (camera != null) {
                    // Redirect turn() to the FreeCamera instead of the player
                    camera.turn(yaw, pitch);
                    ci.cancel();
                }
            }
        }
    }
}
