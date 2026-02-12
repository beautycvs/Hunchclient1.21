package dev.hunchclient.mixin.client;

import dev.hunchclient.bridge.ModuleBridge;
import dev.hunchclient.bridge.module.IF7Sim;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Handles instant-shot bow behavior for Terminator mode.
 * When F7Sim + Terminator mode is enabled, bow shoots instantly without charge.
 */
@Mixin(BowItem.class)
public class BowItemUseMixin {

    /**
     * Prevent bow from being "used" (charging animation).
     * Instead, shoot instantly.
     */
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void hunchclient$instantShot(Level world, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (!world.isClientSide()) {
            return;
        }

        IF7Sim f7sim = ModuleBridge.f7sim();

        if (f7sim != null && f7sim.isEnabled() && f7sim.isTerminatorMode()) {
            // Shoot instantly instead of charging
            f7sim.onBowUseAttempt(player, hand);

            // Return PASS to prevent bow charging animation
            cir.setReturnValue(InteractionResult.PASS);
        }
    }

    /**
     * Override getMaxUseTime to return 0 in Terminator mode.
     * This prevents the bow from being held/charged.
     */
    @Inject(method = "getUseDuration", at = @At("HEAD"), cancellable = true)
    private void hunchclient$noChargeTime(ItemStack stack, LivingEntity user, CallbackInfoReturnable<Integer> cir) {
        if (user instanceof Player player) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.player == player) {
                IF7Sim f7sim = ModuleBridge.f7sim();
                if (f7sim != null && f7sim.isEnabled() && f7sim.isTerminatorMode()) {
                    // No charge time = instant shot
                    cir.setReturnValue(0);
                }
            }
        }
    }
}
