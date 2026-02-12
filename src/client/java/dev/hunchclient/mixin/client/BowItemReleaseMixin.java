package dev.hunchclient.mixin.client;

import dev.hunchclient.bridge.ModuleBridge;
import dev.hunchclient.bridge.module.IF7Sim;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BowItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BowItem.class)
public class BowItemReleaseMixin {

    private static int currentArrowIndex = 0;

    /**
     * Insta-shot: When F7 Sim is enabled, bow is instantly fully charged.
     * This simulates the Terminator bow in F7 dungeons.
     */
    @Inject(method = "getPowerForTime", at = @At("RETURN"), cancellable = true)
    private static void hunchclient$instaShot(int useTicks, CallbackInfoReturnable<Float> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null) {
            IF7Sim f7sim = ModuleBridge.f7sim();
            if (f7sim != null && f7sim.isEnabled()) {
                // F7 Sim active = instant full charge (insta-shot)
                cir.setReturnValue(1.0F);
            }
        }
    }

    /**
     * Terminal Mode: Store the arrow index for spread calculation.
     * Note: shootProjectile is called for each projectile with its index.
     */
    @Inject(method = "shootProjectile", at = @At("HEAD"))
    protected void hunchclient$terminalModeSpread(
            LivingEntity shooter,
            Projectile projectile,
            int index,
            float speed,
            float divergence,
            float yaw,
            LivingEntity target,
            CallbackInfo ci) {

        if (shooter instanceof Player) {
            IF7Sim f7sim = ModuleBridge.f7sim();
            if (f7sim != null && f7sim.isEnabled() && f7sim.isTerminatorMode()) {
                // Store the current index for use in setVelocity injection
                currentArrowIndex = index;
            }
        }
    }

    /**
     * Terminal Mode: Modify the yaw parameter when calling setVelocity.
     * This applies the spread: left (-5°), center (0°), right (+5°).
     * NOTE: This mixin is optional (require = 0) as the setVelocity call location may have changed.
     */
    @ModifyArg(
        method = "shootProjectile",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/projectile/Projectile;shootFromRotation(Lnet/minecraft/world/entity/Entity;FFFFF)V"
        ),
        index = 2,
        require = 0
    )
    private float hunchclient$modifyArrowYaw(float originalYaw) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null) {
            IF7Sim f7sim = ModuleBridge.f7sim();
            if (f7sim != null && f7sim.isEnabled() && f7sim.isTerminatorMode()) {
                // Apply yaw offset based on arrow index
                // index 0 = left (-15°), index 1 = center (0°), index 2 = right (+15°)
                float yawOffset = (currentArrowIndex - 1) * 15.0f;
                return originalYaw + yawOffset;
            }
        }
        return originalYaw;
    }
}
